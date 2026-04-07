# PBJ Codec Architecture

This document describes PBJ's shared serialization architecture — the runtime interfaces, streaming data abstractions, and helper classes that all codec implementations build on.

For format-specific codec details, see:
- [codec-protobuf.md](codec-protobuf.md) — protobuf binary codec internals
- [codec-json.md](codec-json.md) — JSON codec internals

For protobuf specification coverage and type mappings, see [protobuf-and-schemas.md](protobuf-and-schemas.md).

## Overview

For every `.proto` message, PBJ generates four artifacts:

| Artifact | Example | Purpose |
|----------|---------|---------|
| **Model class** | `HelloRequest` | Immutable data object with fields, builder, equals/hashCode |
| **Schema class** | `HelloRequestSchema` | Static `FieldDefinition` constants and field-number lookup |
| **Protobuf codec** | `HelloRequestProtoCodec` | Binary protobuf serialization (`implements Codec<T>`) |
| **JSON codec** | `HelloRequestJsonCodec` | JSON serialization (`implements JsonCodec<T>`) |

Each model class exposes singleton codec instances as static fields:

```java
public static final Codec<HelloRequest> PROTOBUF = new HelloRequestProtoCodec();
public static final JsonCodec<HelloRequest> JSON = new HelloRequestJsonCodec();
```

This means any code that has a reference to the model type can serialize/deserialize without looking up codecs externally:

```java
// Parse from protobuf bytes
HelloRequest msg = HelloRequest.PROTOBUF.parse(readableData);

// Serialize to bytes
Bytes bytes = HelloRequest.PROTOBUF.toBytes(msg);

// Parse/write JSON
HelloRequest msg = HelloRequest.JSON.parse(jsonInput);
String json = HelloRequest.JSON.toJSON(msg);
```

## Runtime Interfaces

### `Codec<T>` — the core interface

**File:** `pbj-runtime/.../Codec.java`

The `Codec<T>` interface defines the full contract for binary serialization:

| Method | Purpose |
|--------|---------|
| `parse(ReadableSequentialData, strictMode, parseUnknownFields, maxDepth, maxSize)` | Deserialize from binary protobuf |
| `write(T, WritableSequentialData)` | Serialize to binary protobuf (streaming) |
| `write(T, byte[], startOffset)` | Serialize to byte array (performance path) |
| `measure(ReadableSequentialData)` | Determine encoded size by parsing |
| `measureRecord(T)` | Calculate serialized size from in-memory object |
| `fastEquals(T, ReadableSequentialData)` | Compare object with encoded bytes without full deserialization |
| `toBytes(T)` | Convenience: serialize to `Bytes` (measures first, then writes) |
| `getDefaultInstance()` | Returns `T.DEFAULT` |

**Convenience overloads** reduce boilerplate for common cases:

```java
parse(input)                        // non-strict, default depth/size
parse(bytes)                        // from Bytes, non-strict
parseStrict(input)                  // strict mode — errors on unknown fields
parse(input, strictMode, maxDepth)  // custom depth, default size
```

**Safety limits:**

- `DEFAULT_MAX_SIZE = 2 MB` — maximum size of any length-delimited field (string, bytes, message, repeated)
- `DEFAULT_MAX_DEPTH = 512` — maximum nesting depth for recursive message parsing

These prevent out-of-memory and stack-overflow attacks from malicious payloads. Applications can override them per call.

### `JsonCodec<T>` — JSON extension

**File:** `pbj-runtime/.../JsonCodec.java`

Extends `Codec<T>` with JSON-specific behavior:

| Method | Purpose |
|--------|---------|
| `parse(JSONParser.ObjContext, strictMode, maxDepth, maxSize)` | Parse from ANTLR JSON AST |
| `toJSON(T)` | Compact JSON string |
| `toJSON(T, indent, inline)` | Pretty-printed JSON |

The `JsonCodec` bridges the binary `Codec` interface to JSON by implementing `parse(ReadableSequentialData, ...)` as:

```java
default T parse(ReadableSequentialData input, ...) {
    return parse(JsonTools.parseJson(input), strictMode, maxDepth, maxSize);
}
```

This first parses the raw bytes into an ANTLR parse tree (`JSONParser.ObjContext`), then walks the tree. Similarly, `write()` delegates to `toJSON()`:

```java
default void write(T item, WritableSequentialData output) {
    output.writeUTF8(toJSON(item));
}
```

### `Schema` — field metadata

**File:** `pbj-runtime/.../Schema.java`

A marker interface. Generated schema classes provide two static methods:

```java
public static FieldDefinition getField(int fieldNumber);  // lookup by field number
public static boolean valid(FieldDefinition f);            // membership check
```

Each field is described by a `FieldDefinition` record:

```java
record FieldDefinition(
    String name,        // proto field name
    FieldType type,     // DOUBLE, FLOAT, INT32, ..., MESSAGE, MAP (18 types)
    boolean repeated,   // repeated field
    boolean optional,   // optional wrapper type
    boolean oneOf,      // part of a oneOf group
    int number          // protobuf field number
)
```

Generated schemas define `FieldDefinition` constants for each field:

```java
public static final FieldDefinition NAME =
    new FieldDefinition("name", FieldType.STRING, false, false, false, 1);
```

These constants are referenced by both the proto and JSON codecs during read/write operations.

## Streaming Data Abstractions

Codecs read from `ReadableSequentialData` and write to `WritableSequentialData`. These interfaces abstract over the underlying byte source/sink:

```
                    SequentialData
                    (position, limit, capacity)
                   /                           \
    ReadableSequentialData              WritableSequentialData
    - readByte()                        - writeByte()
    - readVarInt(zigZag)                - writeVarInt(value, zigZag)
    - readVarLong(zigZag)               - writeVarLong(value, zigZag)
    - readInt/Long/Float/Double()       - writeInt/Long/Float/Double()
    - readBytes(...)                    - writeBytes(...)
    - view(length)                      - writeUTF8(String)
```

**Concrete implementations:**

| Class | Backed by | Use case |
|-------|-----------|----------|
| `BufferedData` | `ByteBuffer` (heap or direct) | In-memory buffers, random access |
| `ReadableStreamingData` | `InputStream` | Streaming from files/network |
| `WritableStreamingData` | `OutputStream` | Streaming to files/network |
| `Bytes` | Immutable `byte[]` or `ByteBuffer` | Immutable byte sequences |

All implementations track position and enforce limits, providing a uniform interface regardless of whether you're reading from a byte array, a direct buffer, or a network stream.

## Runtime Helper Classes

### `ProtoParserTools`

Static utility methods for reading protobuf field values:

- `readInt32()`, `readUint32()`, `readSignedInt32()` — integer variants
- `readInt64()`, `readUint64()`, `readSignedInt64()` — long variants
- `readFixed32()`, `readFixed64()`, `readSignedFixed32()`, `readSignedFixed64()` — fixed-width
- `readFloat()`, `readDouble()`, `readBool()` — other scalars
- `readString(input, maxSize)`, `readBytes(input, maxSize)` — length-delimited with size validation
- `readNextFieldNumber(input)` — decode tag, return field number
- `skipField(input, wireType, maxSize)` — skip unknown fields
- `extractField(input, wireType, maxSize)` — capture unknown field bytes
- `addToList()`, `addToMap()` — lazy collection builders

### `ProtoWriterTools`

Static utility methods for writing protobuf field values:

- `writeTag(out, field)` — write field tag
- `wireType(field)` — compute wire type from `FieldDefinition`
- `writeInteger()`, `writeLong()`, `writeFloat()`, `writeDouble()`, `writeBoolean()` — scalar writes
- `writeString()`, `writeBytes()` — length-delimited writes
- `writeMessage()` — nested message write (delegates to sub-codec)
- `writeEnum()`, `writeEnumProtoOrdinal()` — enum writes
- `write*List()` — batch methods for repeated fields (with packed encoding)

### `ProtoArrayWriterTools`

Mirror of `ProtoWriterTools` that writes directly to `byte[]` instead of `WritableSequentialData`. Used by the array-based `write()` overload for maximum performance (avoids virtual dispatch on the output stream).

### `JsonTools`

Static utility methods for JSON serialization:

- `parseJson(input)` — parse raw bytes into ANTLR JSON AST
- `toJsonFieldName(name)` — convert snake_case to camelCase
- `field(name, value)` — format a JSON field (many overloads for different types)
- `arrayField(name, fieldDef, items)` — format a JSON array field
- `escape()` / `unescape()` — JSON string escaping
- `parseInteger()`, `parseLong()`, etc. — extract typed values from JSON AST nodes

## Design Principles

1. **Generated code looks hand-written.** Codec classes are readable, well-commented, and follow standard Java patterns. Comments on switch cases document the tag breakdown (wire type, proto type, field number, field name).

2. **Performance-oriented.** Two write paths (streaming and byte-array), precomputed tag values in switch cases, lazy list/map allocation, cached `protobufSize()` in model objects.

3. **Deterministic encoding.** Fields always written in ascending field number order. Maps sorted by key. This ensures identical objects produce identical bytes.

4. **Safety by default.** Size limits (`maxSize`) and depth limits (`maxDepth`) are enforced throughout. Position and limit verification for nested messages. Unknown fields validated against wire type constraints.

5. **Forward compatibility.** Non-strict mode (the default) silently skips unknown fields, allowing older parsers to read data produced by newer schemas. Unknown fields can optionally be preserved for round-trip fidelity.

6. **Separation of concerns.** Model classes are pure data. Schemas hold field metadata. Codecs handle serialization. Writer/parser tools provide reusable primitives. This keeps each generated class focused and testable.

7. **Codec-per-format.** Protobuf and JSON have separate codec classes rather than a single class with mode flags. This keeps each codec simple and avoids conditional logic in hot paths.
