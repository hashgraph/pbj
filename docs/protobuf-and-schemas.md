# Protobuf and Schemas in PBJ

This document describes how PBJ implements the Protocol Buffers specification. It covers which proto3 features are supported, how PBJ maps protobuf concepts to Java, and where PBJ intentionally deviates from standard protobuf behavior.

For details on the compiler pipeline, see [code-generation.md](code-generation.md). For codec internals, see [codecs.md](codecs.md).

## Introduction

PBJ is a **proto3-only** protobuf implementation. It parses `.proto` schema files and generates Java source code — model classes, serialization codecs, and tests — that is wire-compatible with Google's `protoc` compiler.

PBJ was built for the [Hiero Consensus Node](https://github.com/hiero-ledger/hiero-consensus-node), a distributed ledger system where serialized bytes are hashed and digitally signed as part of consensus. This use case drives several design decisions that differ from standard protobuf implementations:

- **Deterministic encoding** — identical objects always produce identical bytes, so hashes and signatures are reliable
- **Explicit nullability** — missing fields return `null` rather than default values, forcing developers to handle absence explicitly
- **Stable `hashCode()` and `equals()`** — adding new fields with default values does not break existing hash maps
- **Performance and minimal garbage** — lazy computation, cached sizes, immutable objects, and direct byte-array write paths

PBJ produces the same wire encoding as `protoc`. Any protobuf message serialized by PBJ can be deserialized by `protoc`-generated code, and vice versa. The differences are in the Java API surface and serialization guarantees, not in the wire format.

## Proto3 Language Support

PBJ supports the following proto3 syntax elements:

| Feature | Supported | Notes |
|---------|-----------|-------|
| Messages | Yes | Including nested messages |
| Enums | Yes | Including nested enums |
| Services and RPCs | Yes | All four gRPC call types |
| Oneof | Yes | Type-safe discriminated union |
| Map fields | Yes | Deterministic key ordering |
| Repeated fields | Yes | Packed encoding for numerics |
| Imports | Yes | Regular, weak, and public |
| Options | Partial | Standard options plus PBJ-specific extensions |
| Reserved fields/names | Yes | Parsed and enforced at schema level |
| Deprecation | Yes | Maps to `@Deprecated` annotation |
| Documentation comments | Yes | `/** */` comments become Javadoc |
| Packages | Yes | With custom PBJ package resolution |
| `optional` keyword | No | See [Nullability and Field Presence](#nullability-and-field-presence) |
| `proto2` syntax | No | Proto3 only |
| Groups | No | Deprecated proto2 feature |
| Extensions | No | Proto2 feature |

### Package Resolution

PBJ resolves Java packages for generated code using a priority chain:

1. **PBJ comment option** (highest priority): `// <<<pbj.java_package = "com.example.package">>>` in the proto file
2. **Per-definition PBJ options**: `pbj.message_java_package`, `pbj.enum_java_package`, or `pbj.service_java_package`
3. **Standard `java_package` option** + optional `javaPackageSuffix` from the Gradle plugin
4. **Proto `package` statement** + `javaPackageSuffix` (fallback)

The `javaPackageSuffix` is configured in `build.gradle.kts`:

```groovy
pbj { javaPackageSuffix = ".pbj" }
```

This allows PBJ-generated and `protoc`-generated classes to coexist in the same project under different packages.

## Scalar Types

PBJ supports all 15 proto3 scalar types. Each maps to a Java primitive (or `String`/`Bytes`) and uses the standard protobuf wire encoding:

| Proto type | Java type | Boxed type | Wire type | Encoding |
|------------|-----------|------------|-----------|----------|
| `double` | `double` | `Double` | Fixed 64-bit (1) | IEEE 754 double |
| `float` | `float` | `Float` | Fixed 32-bit (5) | IEEE 754 float |
| `int32` | `int` | `Integer` | Varint (0) | Signed varint (negative values use 10 bytes) |
| `int64` | `long` | `Long` | Varint (0) | Signed varint (negative values use 10 bytes) |
| `uint32` | `int` | `Integer` | Varint (0) | Unsigned varint |
| `uint64` | `long` | `Long` | Varint (0) | Unsigned varint |
| `sint32` | `int` | `Integer` | Varint (0) | ZigZag-encoded varint (efficient for negative values) |
| `sint64` | `long` | `Long` | Varint (0) | ZigZag-encoded varint (efficient for negative values) |
| `fixed32` | `int` | `Integer` | Fixed 32-bit (5) | Little-endian 4 bytes |
| `fixed64` | `long` | `Long` | Fixed 64-bit (1) | Little-endian 8 bytes |
| `sfixed32` | `int` | `Integer` | Fixed 32-bit (5) | Little-endian 4 bytes, signed |
| `sfixed64` | `long` | `Long` | Fixed 64-bit (1) | Little-endian 8 bytes, signed |
| `bool` | `boolean` | `Boolean` | Varint (0) | 0 = false, 1 = true |
| `string` | `String` | `String` | Length-delimited (2) | UTF-8 encoded |
| `bytes` | `Bytes` | `Bytes` | Length-delimited (2) | Raw bytes |

Note that PBJ uses its own `Bytes` type (an immutable byte-sequence wrapper) rather than `byte[]` or `ByteString`. `Bytes` prevents accidental mutation and supports efficient operations like `writeTo(MessageDigest)` without copying.

### Varint Encoding

Varints are the standard protobuf variable-width integer encoding. Each byte uses 7 bits for data and 1 bit as a continuation flag. Small positive values use fewer bytes (1 byte for 0-127). Negative `int32`/`int64` values are sign-extended to 10 bytes — use `sint32`/`sint64` with ZigZag encoding if negative values are common.

### ZigZag Encoding

ZigZag encoding maps signed integers to unsigned integers so that values with small absolute values have small varint encodings: 0 → 0, -1 → 1, 1 → 2, -2 → 3, etc. Used by `sint32` and `sint64`.

## Messages and Fields

For each protobuf message, PBJ generates an immutable Java class (not a Java record — see below) with:

- **Private final fields** for each proto field
- **Getter methods**: `foo()` returns the value or `null` if absent; `fooOrElse(defaultValue)` returns a fallback
- **A `Builder` inner class** with fluent setter methods for construction
- **Static codec instances**: `PROTOBUF` (binary) and `JSON` (JSON format)
- **A `DEFAULT` singleton** with all fields at their default values

### Example

Given this proto definition:

```protobuf
message HelloRequest {
  string name = 1;
  int32 count = 2;
}
```

PBJ generates:

```java
public final class HelloRequest {
    public static final Codec<HelloRequest> PROTOBUF = new HelloRequestProtoCodec();
    public static final JsonCodec<HelloRequest> JSON = new HelloRequestJsonCodec();
    public static final HelloRequest DEFAULT = new HelloRequest("", 0);

    private final String name;
    private final int count;

    // Getters
    public String name() { ... }            // returns null if not present
    public String nameOrElse(String def) { ... }  // returns def if not present
    public int count() { ... }
    public int countOrElse(int def) { ... }

    // Builder
    public static final class Builder {
        public Builder name(String name) { ... }
        public Builder count(int count) { ... }
        public HelloRequest build() { ... }
    }

    public static Builder newBuilder() { ... }
    public Builder copyBuilder() { ... }
}
```

### Why Not Java Records?

PBJ generates regular immutable classes rather than Java records because:

1. Records cannot have lazy-computed mutable fields. PBJ caches `hashCode()` and `protobufEncodedSize()` on first access — these cannot be record components.
2. Records have limited constructor flexibility. PBJ needs multiple constructor overloads for unknown field handling and internal precomputation.

PBJ tracks [JEP 401: Value Classes and Objects](https://openjdk.org/jeps/401) as a potential future alternative.

### Default Values and Wire Semantics

Proto3 defines default values for each type: `0` for numeric types, `false` for bools, `""` for strings, empty bytes for `bytes`. These defaults have two important consequences in PBJ:

1. **Fields with default values are not serialized on the wire** — this is standard proto3 behavior. A message with `count = 0` serializes identically to a message where `count` was never set.

2. **Fields with default values are excluded from `hashCode()` and `equals()`** — this is a PBJ-specific design choice. It means adding a new field to a message definition does not change the hash of existing objects that don't set the new field. This stability is critical for long-lived hash maps in the Hiero consensus node.

## Nullability and Field Presence

This is PBJ's most significant deviation from standard protobuf Java code generation.

### Standard Proto3 Behavior

In proto3, there is no distinction between "field was not set" and "field was set to its default value." Google's `protoc`-generated Java code returns the default value in both cases:

```java
// protoc-generated code
msg.getCount()  // returns 0 whether count was set to 0 or never set
msg.getName()   // returns "" whether name was set to "" or never set
```

### PBJ Behavior

PBJ returns `null` for fields that were not present on the wire, even for scalar types that use Java primitives:

```java
// PBJ-generated code
msg.name()              // returns null if name was not on the wire
msg.nameOrElse("")      // returns "" if name was not on the wire
msg.count()             // returns 0 (Java primitive — cannot be null)
msg.countOrElse(42)     // returns 42 if count was not on the wire
```

For primitive fields (`int`, `long`, `float`, `double`, `boolean`), the getter returns the Java default (`0`, `0L`, `0.0f`, `0.0`, `false`) when absent — Java primitives cannot be `null`. The `fooOrElse()` method provides a way to distinguish "not set" from "set to default" for these types.

For reference types (`String`, `Bytes`, message types), the getter returns `null` when absent.

### Rationale

This design forces developers to explicitly handle the case where a field is missing, rather than silently receiving a default value. In a consensus system, confusing "not set" with "set to zero" could lead to incorrect state transitions or agreement failures. PBJ's approach, analogous to Java's checked exceptions, makes these edge cases visible at the call site.

### Wrapper Types (Optional Value Types)

Proto3 supports wrapper types from `google/protobuf/wrappers.proto` as a convention for optional scalar values:

| Wrapper type | Unwrapped Java type |
|--------------|---------------------|
| `google.protobuf.StringValue` | `String` (nullable) |
| `google.protobuf.Int32Value` | `Integer` (nullable) |
| `google.protobuf.UInt32Value` | `Integer` (nullable) |
| `google.protobuf.Int64Value` | `Long` (nullable) |
| `google.protobuf.UInt64Value` | `Long` (nullable) |
| `google.protobuf.FloatValue` | `Float` (nullable) |
| `google.protobuf.DoubleValue` | `Double` (nullable) |
| `google.protobuf.BoolValue` | `Boolean` (nullable) |
| `google.protobuf.BytesValue` | `Bytes` (nullable) |

PBJ recognizes these wrapper types and generates nullable boxed Java types instead of nested message objects. On the wire, they are still encoded as nested messages (matching protoc), but the generated API presents them as simple nullable values.

### Proto3 `optional` Keyword

Proto3 later introduced the `optional` keyword for explicit field presence (tracking whether a field was explicitly set). **PBJ does not currently support the `optional` keyword.** Use wrapper types or oneof fields when you need to distinguish "not set" from "set to default."

## Enums

PBJ generates a Java `enum` for each protobuf enum, implementing `EnumWithProtoMetadata`:

```protobuf
enum Suit {
  SUIT_UNSPECIFIED = 0;
  SUIT_HEARTS = 1;
  SUIT_DIAMONDS = 2;
}
```

Generates:

```java
public enum Suit implements EnumWithProtoMetadata {
    SUIT_UNSPECIFIED(0),
    SUIT_HEARTS(1),
    SUIT_DIAMONDS(2);

    public int protoOrdinal() { ... }    // wire value
    public String protoName() { ... }    // original proto name

    public static Suit fromProtobufOrdinal(int ordinal) { ... }
    public static Suit fromString(String name) { ... }
}
```

### Key behaviors

- **First value must be 0** — this is a proto3 requirement and serves as the default value.
- **Unknown enum values** are preserved as raw `Integer` values rather than mapped to a sentinel constant. This supports forward compatibility: older code can read enum values added in newer schema versions without losing information.
- **`@Deprecated`** annotations are applied when `deprecated = true` is set on an enum value in the proto file.
- **Enum values are encoded as varints** on the wire, using their numeric value (not their ordinal position in the Java enum).

## Oneof Fields

Protobuf `oneof` declares a set of fields where at most one can be set at a time. PBJ represents oneofs using a type-safe discriminated union:

```protobuf
message Account {
  oneof staked_id {
    int64 staked_account_id = 1;
    int64 staked_node_id = 2;
  }
}
```

PBJ generates:

1. **An inner enum** for the oneof variants:
   ```java
   public enum StakedIdOneOfType implements EnumWithProtoMetadata {
       UNSET(-1),
       STAKED_ACCOUNT_ID(1),
       STAKED_NODE_ID(2);
   }
   ```

2. **A `OneOf<StakedIdOneOfType>` field** on the model class:
   ```java
   public OneOf<StakedIdOneOfType> stakedId() { ... }
   ```

3. **Typed convenience accessors**:
   ```java
   public long stakedAccountId()                   // returns value or default
   public boolean hasStakedAccountId()              // presence check
   public long stakedAccountIdOrElse(long def)      // with fallback
   public long stakedAccountIdOrThrow()             // throws if not set
   ```

### `OneOf<E>` and `ComparableOneOf<E>`

The `OneOf<E>` type is a record with two components:

- `kind()` — the discriminator enum value (e.g., `STAKED_ACCOUNT_ID`, `STAKED_NODE_ID`, or `UNSET`)
- `as()` — the value, cast to the appropriate type

`ComparableOneOf<E>` extends this with `Comparable` support for fields marked with `pbj.comparable`.

### Wire Format

On the wire, oneof fields are encoded as regular fields — the oneof constraint is not visible in the encoding. If multiple alternatives appear in the wire data, the last one wins (standard protobuf behavior).

## Repeated Fields

Repeated fields are the protobuf equivalent of lists/arrays:

```protobuf
message Block {
  repeated Transaction transactions = 1;
  repeated int32 numbers = 2;
}
```

PBJ generates immutable `List<T>` fields:

```java
public List<Transaction> transactions() { ... }  // unmodifiable list
public List<Integer> numbers() { ... }            // boxed for generics
```

### Key behaviors

- **Immutable after construction** — lists are backed by `UnmodifiableArrayList`, a PBJ runtime type that is marked read-only after parsing completes.
- **Packed encoding** for numeric repeated fields — multiple values are concatenated into a single length-delimited field on the wire, reducing overhead. PBJ writes packed encoding and accepts both packed and unpacked on read (per the proto3 spec).
- **Empty list default** — a repeated field that is not present on the wire returns `Collections.emptyList()`, not `null`.
- **Size limits** — the parse method enforces `maxSize` on the number of elements to prevent denial-of-service via extremely large repeated fields.

## Maps

Map fields declare key-value associations:

```protobuf
message Config {
  map<string, int32> settings = 1;
}
```

PBJ generates a `PbjMap<K, V>` field — an immutable map implementation that supports deterministic key ordering:

```java
public PbjMap<String, Integer> settings() { ... }
```

### Wire format

On the wire, maps are encoded as repeated length-delimited entries, each containing a key (field 1) and value (field 2) sub-field. This is the standard protobuf map encoding.

### Deterministic ordering

**This is a PBJ-specific guarantee.** Standard protobuf does not define map iteration order. PBJ's `PbjMap` provides a `getSortedKeys()` method that returns keys in their natural sort order. During serialization, map entries are always written in sorted key order. This ensures:

- Identical maps produce identical bytes
- Hash computation over serialized maps is deterministic
- Digital signatures over messages containing maps are reproducible

### Allowed key types

Per the protobuf specification, map keys can be any scalar type except `float`, `double`, and `bytes`. Map values can be any type (scalar, enum, or message) except another map.

## Deterministic Encoding

PBJ guarantees deterministic binary encoding: the same logical message always serializes to exactly the same bytes. This is achieved through three ordering rules:

1. **Fields are always written in ascending field number order** — the proto3 spec allows fields in any order, but PBJ always writes them sorted.

2. **Map entries are sorted by key** — using the natural sort order of the key type (lexicographic for strings, numeric for integers, etc.).

3. **Unknown fields are sorted by field number** — unknown fields collected during parsing are written in field-number order during re-serialization.

### Why Deterministic Encoding Matters

Standard protobuf explicitly does **not** guarantee deterministic encoding. The [protobuf documentation](https://protobuf.dev/programming-guides/encoding/) warns that serialization output may change between library versions and should not be relied upon for hashing or comparison.

PBJ takes the opposite stance because it is designed for a consensus network. In the Hiero consensus node:

- Nodes must agree on the hash of a serialized transaction — non-deterministic encoding would cause consensus failures
- Digital signatures over serialized messages must be verifiable by any node — different byte orderings would invalidate signatures
- State snapshots are hashed for integrity verification — deterministic encoding ensures all nodes compute the same hash

### Wire Compatibility

Despite the deterministic ordering, PBJ's wire encoding is fully compatible with `protoc`. The bytes PBJ produces are valid protobuf and can be parsed by any compliant protobuf implementation. The difference is that PBJ's output is a specific canonical form of the many valid encodings that protobuf allows.

## Stable `hashCode()` and `equals()`

PBJ generates `hashCode()` and `equals()` implementations with a specific stability guarantee: **fields with default values are excluded from the computation.**

### How It Works

For a message like:

```protobuf
message Account {
  int64 account_id = 1;
  string memo = 2;
  int64 balance = 3;   // added in a later schema version
}
```

If `balance` has its default value (`0`), it is not included in `hashCode()` or `equals()`. This means:

- An `Account` object parsed from bytes that predate the `balance` field has the same hash as an `Account` object with `balance = 0`
- Existing hash maps that use `Account` as a key continue to work correctly after the field is added

### Rationale

This mirrors the wire format semantics: fields with default values are not encoded on the wire, so they have no presence in the serialized form. PBJ extends this logic to `hashCode()` and `equals()` for consistency and to support long-lived data structures across schema evolution.

### Lazy Computation

Both `hashCode()` and the protobuf-encoded size are computed lazily on first access and cached in internal fields (`$hashCode` and `$protobufEncodedSize`). This avoids paying the computation cost in constructors, which is important when objects are created in performance-critical paths but may never be hashed or serialized.

## Wire Format

PBJ follows the standard [Protocol Buffers encoding](https://protobuf.dev/programming-guides/encoding/). This section summarizes the key concepts for reference.

### Tags

Every field on the wire is preceded by a **tag** — a varint encoding both the field number and wire type:

```
tag = (field_number << 3) | wire_type
```

### Wire Types

| Value | Name | Used for |
|-------|------|----------|
| 0 | Varint | int32, int64, uint32, uint64, sint32, sint64, bool, enum |
| 1 | 64-bit | fixed64, sfixed64, double |
| 2 | Length-delimited | string, bytes, messages, packed repeated fields, map entries |
| 5 | 32-bit | fixed32, sfixed32, float |

Wire types 3 and 4 (start/end group) are deprecated proto2 features and not supported.

### Unknown Fields

When PBJ encounters a field number not defined in the schema, it can handle it in three ways:

| Mode | Behavior | Use case |
|------|----------|----------|
| **Skip** (default) | Reads and discards the bytes | Normal operation, forward compatibility |
| **Strict** | Throws `UnknownFieldException` | Validation, detecting schema mismatches |
| **Collect** | Stores as `UnknownField` objects | Round-trip fidelity, preserving unrecognized data |

Collected unknown fields are included in re-serialization, sorted by field number for determinism.

### Safety Limits

PBJ enforces safety limits during parsing to prevent denial-of-service attacks from malicious payloads:

- **`maxSize`** (default: 2 MB) — maximum size of any length-delimited field (string, bytes, message, repeated)
- **`maxDepth`** (default: 512) — maximum nesting depth for recursive message parsing

These can be overridden per parse call.

## JSON Mapping

PBJ implements the standard [proto3 JSON mapping](https://protobuf.dev/programming-guides/proto3/#json):

- **Field names** are converted from proto `snake_case` to JSON `camelCase` (e.g., `account_id` becomes `"accountId"`)
- **Only non-default fields** are included in JSON output
- **Enums** are serialized as their string name
- **`bytes` fields** are serialized as base64-encoded strings
- **64-bit integers** (`int64`, `uint64`, etc.) are serialized as strings in JSON to avoid JavaScript precision loss
- **Nested messages** are serialized as JSON objects

JSON parsing uses an ANTLR-based parser (`JSONParser`) that builds a parse tree before walking it. This two-phase approach is simpler than streaming JSON parsing but less suitable for very large payloads.

Both strict and non-strict modes are supported: strict mode throws on unrecognized JSON fields, non-strict mode silently ignores them.

## Services and gRPC

PBJ generates Java interfaces for protobuf services:

```protobuf
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
  rpc SayHelloStream (stream HelloRequest) returns (stream HelloReply);
}
```

Generates a `GreeterServiceInterface` with:

- Method declarations for each RPC
- A `Method` inner enum listing all RPCs
- An `open()` routing method for dispatching by method enum
- Default implementations that throw `UnsupportedOperationException`

All four gRPC call types are supported:

| Call type | Client | Server |
|-----------|--------|--------|
| Unary | Single request | Single response |
| Client-streaming | Stream of requests | Single response |
| Server-streaming | Single request | Stream of responses |
| Bidirectional | Stream of requests | Stream of responses |

PBJ's gRPC implementation runs on Helidon SE (HTTP/2 web client/server) rather than the standard `io.grpc` library. This eliminates the `io.grpc` dependency tree and provides low-level access to bytes and HTTP/2 frames.

## PBJ-Specific Extensions

PBJ adds several custom options and conventions beyond the standard protobuf specification:

### Custom Package Override

```protobuf
// <<<pbj.java_package = "com.example.custom.package">>>
```

This special comment syntax (not a standard protobuf option) overrides the Java package for all generated classes in the file. It takes highest priority in package resolution.

### Per-Definition Package Options

```protobuf
option (pbj.message_java_package) = "com.example.messages";
option (pbj.enum_java_package) = "com.example.enums";
option (pbj.service_java_package) = "com.example.services";
```

These options override the Java package for specific definition types within a file.

### Comparable Fields

```protobuf
option (pbj.comparable) = true;
```

When set on a message or field, PBJ generates a `compareTo()` method on the model class, implementing `Comparable<T>`. Fields are compared in their definition order.

### Gradle Plugin Configuration

```groovy
pbj {
    javaPackageSuffix = ".pbj"          // suffix appended to derived package names
    generateTestClasses = true           // whether to generate unit tests (default: true)
}
```

## Limitations and Unsupported Features

### Not Supported

| Feature | Reason |
|---------|--------|
| **Proto2 syntax** | PBJ is proto3-only. The ANTLR grammar requires `syntax = "proto3"` |
| **`optional` keyword** | Proto3 explicit field presence is not implemented. Use wrapper types or oneof as alternatives |
| **Groups** | Deprecated proto2 feature, not part of proto3 |
| **Extensions** | Proto2 feature, not part of proto3 |
| **Custom protobuf options** | Only standard options and PBJ-specific `pbj.*` options are recognized |
| **`google.protobuf.Any`** | The well-known `Any` type (runtime type embedding) is not handled specially |
| **`google.protobuf.Duration`** | Not handled as a special type — parsed as a regular message |
| **`google.protobuf.Timestamp`** | Not handled as a special type — parsed as a regular message |
| **`google.protobuf.Struct`** | Not handled as a special type — parsed as a regular message |

Well-known wrapper types (`StringValue`, `Int32Value`, etc.) **are** supported and receive special treatment as nullable scalar fields.

### Long-Term Goals

PBJ is an active project with ongoing development. Some planned areas include:

- Support for all protobuf features, including the `optional` keyword
- Auto-mapping gRPC APIs to JSON REST APIs (gRPC transcoding)
- Performance optimizations (SIMD-based varint processing)
- Support for additional serialization formats
