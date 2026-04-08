# PBJ Code Generation Architecture

This document describes how the PBJ compiler Gradle plugin transforms `.proto` schema files into Java source code.

## Overview

The PBJ compiler is a Gradle plugin (`com.hedera.pbj.pbj-compiler`) that parses Protocol Buffer 3 schema files using an ANTLR4 grammar and generates Java source code. For each protobuf message, it produces up to five Java files: a model record, a schema class, a protobuf codec, a JSON codec, and a unit test. For enums and services, it generates a single file each.

The pipeline has three phases:

1. **Global analysis** — scan all proto files (sources + classpath) to build lookup tables for packages, types, and imports
2. **Parse** — lex and parse each source proto file into an ANTLR parse tree
3. **Generate** — walk each top-level definition and emit Java source code via generators

## Gradle Plugin Integration

### Entry point: `PbjCompilerPlugin`

The plugin (`PbjCompilerPlugin implements Plugin<Project>`) performs these setup steps during the configuration phase:

1. Registers a `PbjExtension` exposing the `pbj { }` DSL block with two options:
   - `javaPackageSuffix` — optional suffix appended to derived package names (e.g., `".pbj"`)
   - `generateTestClasses` — boolean (default `true`) controlling test generation
2. Registers a `PbjProtobufExtractTransform` artifact transform that extracts `.proto` files from JAR dependencies on the compile classpath
3. For each source set, creates a virtual `PbjSourceDirectorySet` pointing to `src/<sourceSet>/proto` and registers a `PbjCompilerTask`

The main source set generates model/codec/schema into `build/generated/source/pbj-proto/main/java` and tests into `build/generated/source/pbj-proto/test/java`. The generated source directories are wired as inputs to the Java compile task, so generation happens automatically before compilation.

### Task: `PbjCompilerTask`

The task (`extends SourceTask`) defines:

- `@InputFiles` — proto source files + extracted classpath protos
- `@OutputDirectory` — main and test output directories
- `@TaskAction perform()` — clears output dirs, then delegates to `PbjCompiler.compileFilesIn()`

## Parsing Pipeline

### ANTLR Grammar

The grammar file `Protobuf3.g4` (package `com.hedera.hashgraph.protoparser.grammar`) defines the full proto3 syntax. Notable additions beyond the standard spec:

- `DOC_COMMENT` tokens preserve `/** ... */` documentation comments through to generated Javadoc
- `OPTION_LINE_COMMENT` tokens capture PBJ-specific option comments: `// <<<pbj.java_package = "...">>>`

### Two-Phase Processing

**Phase 1 — `LookupHelper` construction:**

Before any code generation, `PbjCompiler` builds a `LookupHelper` by parsing every proto file (both source files and classpath dependencies). This pre-scan builds several lookup maps:

| Map | Key | Value |
|-----|-----|-------|
| `pbjPackageMap` | Fully qualified proto name | Java package for PBJ model classes |
| `pbjCompleteClassMap` | Fully qualified proto name | Complete Java class name (including outer class for nested types) |
| `protocPackageMap` | Fully qualified proto name | Java package for protoc-generated classes |
| `enumNames` | — | Set of all fully qualified enum names |
| `comparableFieldsByMsg` | Message name | List of comparable field names |

The `LookupHelper` resolves Java packages using a priority chain (PBJ comment option → per-definition options → standard `java_package` + suffix → proto `package` + suffix). For the full resolution rules, see [protobuf-and-schemas.md](protobuf-and-schemas.md#package-resolution).

**Phase 2 — Per-file generation:**

For each source proto file, a `ContextualLookupHelper` wraps the global `LookupHelper` with the current file context. The file is lexed and parsed:

```
FileInputStream → Protobuf3Lexer → CommonTokenStream → Protobuf3Parser → ProtoContext
```

Then each `topLevelDef` is dispatched:

- `messageDef` → create `FileSetWriter` (5 `JavaFileWriter` instances), run all `Generator` implementations, write files
- `enumDef` → `EnumGenerator.generateEnum()` with a single `JavaFileWriter`
- `serviceDef` → `ServiceGenerator.generateService()` with a single `JavaFileWriter`

## Field Model (Intermediate Representation)

Rather than building a full AST, the compiler uses lightweight field records extracted directly from ANTLR parse tree contexts. The `Field` interface defines the contract; three record implementations cover all protobuf field kinds:

### `SingleField`

Represents a regular field or a sub-field within a oneof. Constructed directly from `Protobuf3Parser.FieldContext`. Stores:

- `type` — a `FieldType` enum value (see below)
- `fieldNumber`, `name`, `repeated`, `deprecated`
- `messageType` / `completeClassName` — for message and enum references
- `parent` — the `OneOfField` this belongs to, if any
- Package references for model, codec, and test imports

Key methods: `parseCode()` (Java code to parse this field from protobuf input), `javaFieldType()`, `schemaFieldsDef()`, `parserFieldsSetMethodCase()`.

### `OneOfField`

Represents a protobuf `oneof` block. Contains a list of child `Field` objects (the variants). Generates an inner enum type (e.g., `DataOneOfType` with values like `ACCOUNT_ID`, `UNSET`) for runtime type discrimination.

### `MapField`

Represents a `map<K, V>` field. Internally decomposed into synthetic `keyField` and `valueField` `SingleField` instances. On the wire, maps are repeated length-delimited entries sorted by key for deterministic encoding.

### `FieldType` Enum

Maps every protobuf type to its Java representation and wire format:

| FieldType | Java type | Boxed type | Wire type |
|-----------|-----------|------------|-----------|
| INT32, UINT32, SINT32 | `int` | `Integer` | VARINT (0) |
| INT64, UINT64, SINT64 | `long` | `Long` | VARINT (0) |
| FLOAT, FIXED32, SFIXED32 | `float`/`int` | `Float`/`Integer` | FIXED32 (5) |
| DOUBLE, FIXED64, SFIXED64 | `double`/`long` | `Double`/`Long` | FIXED64 (1) |
| BOOL | `boolean` | `Boolean` | VARINT (0) |
| STRING | `String` | `String` | LENGTH_DELIMITED (2) |
| BYTES | `Bytes` | `Bytes` | LENGTH_DELIMITED (2) |
| MESSAGE | `Object` | `Object` | LENGTH_DELIMITED (2) |
| ENUM | `int` | `Integer` | VARINT (0) |
| MAP | `Map` | `Map` | LENGTH_DELIMITED (2) |
| ONE_OF | `OneOf` | `OneOf` | — |

For repeated fields, `FieldType.javaType(true)` returns the boxed `List<>` variant (e.g., `List<Integer>`).

## Code Generators

All message generators implement the `Generator` interface and are registered in `Generator.GENERATORS` — a map from generator class to the `JavaFileWriter` accessor on `FileSetWriter`:

```java
Map.of(
    ModelGenerator.class,     FileSetWriter::modelWriter,
    SchemaGenerator.class,    FileSetWriter::schemaWriter,
    CodecGenerator.class,     FileSetWriter::codecWriter,
    JsonCodecGenerator.class, FileSetWriter::jsonCodecWriter,
    XdrCodecGenerator.class,  FileSetWriter::xdrCodecWriter,
    TestGenerator.class,      FileSetWriter::testWriter
);
```

Each generator is instantiated via reflection and called with the `MessageDefContext`, a `JavaFileWriter`, and the `ContextualLookupHelper`. Generators build Java code as strings and append to the writer.

### ModelGenerator

**Output:** `<MessageName>.java` in the base package

Generates a Java `record` for each protobuf message containing:

- Record fields for each proto field, plus two precomputed fields: `$hashCode` and `$protobufEncodedSize`
- Multiple constructor overloads (with/without `unknownFields`, with enum types or raw `Object` storage)
- Getter methods — `foo()` returns the value (null if absent), `fooOrElse(default)` returns a default for absent fields
- `hashCode()` / `equals()` — fields with default values are excluded so adding new default-valued fields doesn't break existing hash maps
- `toString()`, `compareTo()` (when fields are marked `pbj.comparable`)
- Builder inner class with fluent API (`newBuilder()`, `toBuilder()`)
- OneOf inner enums and typed accessor methods
- Static `PROTOBUF`, `JSON`, and `XDR` codec constants

### SchemaGenerator

**Output:** `<MessageName>Schema.java` in the `.schema` sub-package

Generates static `FieldDefinition` constants for each field (field number, type, repeated/optional flags) and a `getField(int fieldNumber)` method for O(1) lookup.

### CodecGenerator (Protobuf)

**Output:** `<MessageName>ProtoCodec.java` in the `.codec` sub-package

Implements the `Codec<T>` interface for protobuf binary serialization. The generator delegates to specialized sub-generators:

| Sub-generator | Method generated | Purpose |
|---------------|-----------------|---------|
| `CodecParseMethodGenerator` | `parse(ReadableSequentialData, ...)` | Deserialize from protobuf binary |
| `CodecWriteMethodGenerator` | `write(T, WritableSequentialData)` | Serialize to protobuf binary |
| `CodecWriteByteArrayMethodGenerator` | `write(T) → byte[]` | Serialize to byte array |
| `CodecMeasureDataMethodGenerator` | `measure(T)` | Compute serialized size |
| `CodecMeasureRecordMethodGenerator` | `measureRecord(T)` | Record-based size measurement |
| `CodecFastEqualsMethodGenerator` | `fastEquals(T, T)` | Optimized equality check |
| `CodecDefaultInstanceMethodGenerator` | `getDefaultInstance()` | Singleton default instance |
| `LazyGetProtobufSizeMethodGenerator` | `getProtobufSize()` | Lazy size computation for model |

The parse method uses a switch over protobuf tags (`(fieldNumber << 3) | wireType`) to dispatch to field-specific parsing logic. Maps are sorted by key on write for deterministic encoding.

### JsonCodecGenerator

**Output:** `<MessageName>JsonCodec.java` in the `.codec` sub-package

Implements `Codec<T>` for JSON serialization/deserialization. Structured similarly to `CodecGenerator` with:

- `JsonCodecParseMethodGenerator` — JSON deserialization
- `JsonCodecWriteMethodGenerator` — JSON serialization

### XdrCodecGenerator

**Output:** `<MessageName>XdrCodec.java` in the `.codec` sub-package

Implements `XdrCodec<T>` (which extends `Codec<T>`) for XDR (RFC 4506) binary serialization. The generator delegates to specialized sub-generators:

| Sub-generator | Method generated | Purpose |
|---------------|-----------------|---------|
| `XdrCodecParseMethodGenerator` | `parse(ReadableSequentialData, ...)` | Deserialize from XDR binary |
| `XdrCodecWriteMethodGenerator` | `write(T, WritableSequentialData)` | Serialize to XDR binary |
| `XdrCodecMeasureRecordMethodGenerator` | `measureRecord(T)` | Record-based size measurement |
| `CodecFastEqualsMethodGenerator` (shared) | `fastEquals(T, T)` | Optimized equality check |
| `CodecDefaultInstanceMethodGenerator` (shared) | `getDefaultInstance()` | Singleton default instance |

The parse method reads fields in proto field number order. Each singular field is preceded by a 4-byte presence flag. Repeated fields are preceded by a 4-byte count. OneOf fields use a 4-byte discriminant. See [codec-xdr.md](codec-xdr.md) for encoding details.

### TestGenerator

**Output:** `<MessageName>Test.java` in the `.tests` sub-package (test source set)

Generates JUnit 5 parameterized tests covering:

- Round-trip serialization (model → bytes → model) for protobuf, JSON, and XDR codecs
- Equality and hash code verification
- Unknown fields handling
- Compatibility with Google protoc-generated classes

### EnumGenerator

**Output:** `<EnumName>.java` in the base package

Generates a Java `enum` with:

- A constant for each proto enum value
- `fromProtobufOrdinal(int)` — maps wire value to enum constant
- `toProtobufOrdinal()` — maps enum constant to wire value
- `@Deprecated` annotations where specified in the proto schema

### ServiceGenerator

**Output:** `<ServiceName>ServiceInterface.java` in the base package

Generates a Java interface extending `ServiceInterface` with:

- `SERVICE_NAME` and `FULL_NAME` constants
- A `Method` inner enum listing all RPC methods
- Default method implementations for each RPC (throwing `UnsupportedOperationException`)
- An `open()` routing method that dispatches by method enum to the correct handler
- Support for all four gRPC call types: unary, client-streaming, server-streaming, and bidirectional
- An inner `Client` class implementing the interface via `GrpcClient`

## File Output and Writing

### `JavaFileWriter`

A single `.java` file accumulator. Generators call `addImport()` to register imports and `append()` to build the class body as a string. When `writeFile()` is called, it assembles the final file:

```
// SPDX-License-Identifier: Apache-2.0
package <package>;

import <sorted imports>;

<accumulated class body>
```

### `FileSetWriter`

A record holding five `JavaFileWriter` instances (model, schema, codec, jsonCodec, test) for a single message. Created by `FileSetWriter.create()` which resolves output paths and packages for each file type. After all generators run, `writeAllFiles()` writes them all to disk.

### Output Package Structure

For a message with base package `com.example.proto`:

```
com/example/proto/
├── MessageName.java                    (model)
├── schema/
│   └── MessageNameSchema.java          (schema)
├── codec/
│   ├── MessageNameProtoCodec.java      (protobuf codec)
│   └── MessageNameJsonCodec.java       (JSON codec)
└── tests/                              (test source set)
    └── MessageNameTest.java            (unit tests)
```

File naming is controlled by constants in `FileAndPackageNamesConfig`:

| File type | Class suffix | Sub-package |
|-----------|-------------|-------------|
| Model | (none) | (base) |
| Schema | `Schema` | `schema` |
| Protobuf Codec | `ProtoCodec` | `codec` |
| JSON Codec | `JsonCodec` | `codec` |
| Test | `Test` | `tests` |

## Code Style of the Generator

The generators use **direct string construction** (StringBuilder via `JavaFileWriter.append()`) rather than templates or AST manipulation. Java source code is built by concatenating string literals, formatted blocks (often using text blocks with `.indent()`), and field-specific code fragments produced by `Field` method calls like `parseCode()`, `schemaFieldsDef()`, and `parserFieldsSetMethodCase()`.

This approach is simple and keeps all generation logic visible in the generator classes, but means the generators must manually manage indentation, imports, and syntax correctness.

## Nested Message Handling

Nested messages (messages defined inside other messages) are detected via `Generator.isInner()`, which walks up the ANTLR parse tree looking for a parent `MessageDefContext`. Inner messages are generated as `static` inner classes within the outer message's model file. The `JavaFileWriter` abstraction allows inner type generators to append their output to the same writer as the outer type.

## Dependency Resolution from JARs

The `PbjProtobufExtractTransform` Gradle artifact transform extracts `.proto` files from JAR dependencies. This allows proto files in one module to `import` proto definitions from another module's published JAR. The extracted protos are passed to `PbjCompiler` as classpath files — they are parsed for type resolution in the `LookupHelper` but no code is generated for them (code generation only runs for source files).
