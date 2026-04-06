# PBJ Codec Architecture

This document describes how PBJ's serialization codecs work — the runtime interfaces, the generated codec classes, and the design decisions behind them.

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

JSON codecs provide default (non-optimized) implementations for `measure()`, `measureRecord()`, and `fastEquals()` since JSON is not considered performance-critical.

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

## Protobuf Wire Format

PBJ follows the standard [Protocol Buffers encoding](https://protobuf.dev/programming-guides/encoding/). Key concepts used throughout the codec code:

### Tags

Every field on the wire is preceded by a **tag** — a varint that encodes both the field number and wire type:

```
tag = (fieldNumber << 3) | wireType
```

The lower 3 bits are the wire type, the upper bits are the field number.

### Wire Types

Defined in `ProtoConstants`:

| Value | Constant | Used for |
|-------|----------|----------|
| 0 | `WIRE_TYPE_VARINT_OR_ZIGZAG` | int32, int64, uint32, uint64, sint32, sint64, bool, enum |
| 1 | `WIRE_TYPE_FIXED_64_BIT` | fixed64, sfixed64, double |
| 2 | `WIRE_TYPE_DELIMITED` | string, bytes, message, map, packed repeated |
| 5 | `WIRE_TYPE_FIXED_32_BIT` | fixed32, sfixed32, float |

### Tag Constants

Generated codecs use **precomputed tag values** as switch cases for maximum performance:

```java
switch (tag) {
    case 10 /* type=2 [STRING] field=1 [name] */ -> { ... }
    case 18 /* type=2 [MESSAGE] field=2 [address] */ -> { ... }
}
```

The comment `type=2 [STRING] field=1 [name]` documents the wire type (2 = length-delimited), the proto type (STRING), the field number (1), and the field name. The value `10` is `(1 << 3) | 2`.

## Generated Protobuf Codec — Anatomy

### Parse Method

The parse method follows a consistent pattern across all generated codecs:

```
1. Check maxDepth
2. Initialize temp variables for each field (with default values)
3. Enter tag-reading loop:
   a. Read tag varint
   b. Extract field number (tag >>> 3)
   c. Look up FieldDefinition from Schema
   d. Switch on precomputed tag value:
      - Known field → read value, store in temp var
      - Unknown field → strict mode: throw; else: skip or collect
4. Post-processing (make lists read-only, sort unknown fields)
5. Construct and return model object from temp vars
```

The parse loop handles EOF gracefully — `ReadableStreamingData.hasRemaining()` may not detect EOF until an actual read, so the tag-read is wrapped in a try/catch for `EOFException`.

**Field type dispatching** happens within the switch cases using `ProtoParserTools` static methods:

| Proto type | Read call |
|-----------|-----------|
| `int32` | `input.readVarInt(false)` |
| `sint32` | `input.readVarInt(true)` (zigzag) |
| `fixed32` | `input.readInt()` (little-endian) |
| `string` | `readString(input, maxSize)` |
| `bytes` | `readBytes(input, maxSize)` |
| `message` | Read length, set limit, recurse via `T.PROTOBUF.parse(...)` |
| `enum` | Read varint, convert via `EnumType.fromProtobufOrdinal()` |

### Write Methods

Two write methods are generated:

1. **Streaming write** — `write(T data, WritableSequentialData out)`
2. **Array write** — `write(T data, byte[] output, int startOffset)` (performance path)

Both write fields in ascending field number order, then append unknown fields. Each field write uses helper methods from `ProtoWriterTools` (streaming) or `ProtoArrayWriterTools` (array):

```java
// Streaming
writeString(out, HelloRequestSchema.NAME, data.name(), true);
writeMessageList(out, Schema.ITEMS, data.items(), Item.PROTOBUF);

// Array
offset += ProtoArrayWriterTools.writeString(output, offset, Schema.NAME, data.name(), true);
offset += ProtoArrayWriterTools.writeMessageList(output, offset, Schema.ITEMS, data.items(), Item.PROTOBUF);
```

The `boolean skipDefault` parameter (typically `true`) causes the writer to skip fields with default values — this is standard protobuf behavior for non-oneOf fields.

### Measure and FastEquals

- `measureRecord(T)` delegates to `data.protobufSize()`, which the model class caches.
- `measure(ReadableSequentialData)` parses the input and measures bytes consumed.
- `fastEquals(T, ReadableSequentialData)` currently does a full parse-and-compare. The interface is designed for future optimization where fields could be compared incrementally.

## Generated JSON Codec — Anatomy

### Parse Method

JSON parsing uses a two-phase approach:

1. **ANTLR parse** — Raw JSON bytes are parsed into an AST (`JSONParser.ObjContext`) via `JsonTools.parseJson()`
2. **Tree walk** — The generated codec iterates over key-value pairs and dispatches on field names

```java
for (JSONParser.PairContext kvPair : root.pair()) {
    switch (kvPair.STRING().getText()) {
        case "name" -> temp_name = unescape(checkSize("name", kvPair.value().STRING().getText(), maxSize));
        case "accountId" -> { /* parse nested message */ }
        default -> {
            if (strictMode) throw new UnknownFieldException(kvPair.STRING().getText());
        }
    }
}
```

Field names in JSON use **camelCase** (converted from proto's snake_case), following the standard protobuf JSON mapping: `account_id` becomes `"accountId"`.

### Write Method (toJSON)

The `toJSON()` method builds a JSON string with optional pretty-printing:

```java
public String toJSON(HelloRequest data, String indent, boolean inline) {
    StringBuilder sb = new StringBuilder();
    sb.append(inline ? "{\n" : indent + "{\n");
    final String childIndent = indent + INDENT;
    final List<String> fieldLines = new ArrayList<>();

    // Only include non-default fields
    if (data.name() != null && !data.name().isEmpty())
        fieldLines.add(field("name", data.name()));

    if (!fieldLines.isEmpty()) {
        sb.append(childIndent);
        sb.append(String.join(",\n" + childIndent, fieldLines));
        sb.append("\n");
    }
    sb.append(indent + "}");
    return sb.toString();
}
```

`JsonTools.field()` overloads handle quoting, escaping, and formatting for each type.

## Handling Special Field Types

### OneOf Fields

OneOf fields are wrapped in a `OneOf<T>` (or `ComparableOneOf<T>`) type-safe discriminated union:

```java
// Generated constant for the unset state
public static final OneOf<MyMsg.KindOneOfType> KIND_UNSET =
    new OneOf<>(MyMsg.KindOneOfType.UNSET, null);
```

**Parsing:** Each oneOf alternative is a separate case in the tag switch. The parsed value is wrapped with its discriminator:

```java
case 82 /* field=10 [text1] */ -> {
    final var value = readString(input, maxSize);
    temp_oneofExample = new OneOf<>(MyMsg.OneofExampleOneOfType.TEXT1, value);
}
case 90 /* field=11 [text2] */ -> {
    final var value = readString(input, maxSize);
    temp_oneofExample = new OneOf<>(MyMsg.OneofExampleOneOfType.TEXT2, value);
}
```

If two alternatives appear in the wire data, the last one wins (standard protobuf behavior — the temp variable is simply overwritten).

**Writing:** Each alternative is guarded by a `kind()` check:

```java
if (data.oneofExample().kind() == MyMsg.OneofExampleOneOfType.TEXT1)
    writeString(out, Schema.TEXT1, (String) data.oneofExample().as(), true);
if (data.oneofExample().kind() == MyMsg.OneofExampleOneOfType.TEXT2)
    writeString(out, Schema.TEXT2, (String) data.oneofExample().as(), true);
```

### Repeated Fields

Repeated fields are initialized as `Collections.emptyList()` and lazily upgraded to a mutable `UnmodifiableArrayList` on first element:

```java
List<AccountAmount> temp_items = Collections.emptyList();

// In parse loop:
case 10 /* [MESSAGE] field=1 [items] */ -> {
    final var value = /* parse message */;
    if (temp_items.size() >= maxSize) throw new ParseException(...);
    temp_items = addToList(temp_items, value);
}

// After loop:
if (temp_items instanceof UnmodifiableArrayList ual) ual.makeReadOnly();
```

`addToList()` from `ProtoParserTools` handles the lazy list creation. After parsing, the list is made read-only.

**Writing repeated fields** uses batch helper methods:

```java
writeMessageList(out, Schema.ITEMS, data.items(), Item.PROTOBUF);
writeIntegerList(out, Schema.NUMBERS, data.numbers());
```

Packed encoding (for numeric repeated fields) is handled automatically — the codec generates cases for both packed and unpacked wire formats to ensure compatibility.

### Nested Messages

Nested message parsing uses a limit-window pattern:

```java
case 18 /* [MESSAGE] field=2 [address] */ -> {
    final var messageLength = input.readVarInt(false);
    if (messageLength > maxSize) throw new ParseException(...);

    final var limitBefore = input.limit();
    final var startPos = input.position();
    try {
        input.limit(startPos + messageLength);
        value = Address.PROTOBUF.parse(input, strictMode, parseUnknownFields, maxDepth - 1, maxSize);
        if ((startPos + messageLength) != input.position()) throw new BufferOverflowException();
    } finally {
        input.limit(limitBefore);
    }
}
```

Key aspects:
- The input's **limit is temporarily narrowed** to the message boundary
- `maxDepth` is decremented to prevent stack overflow on deeply nested structures
- Position is verified after parsing to ensure the sub-message consumed exactly the right number of bytes
- The limit is always restored in a `finally` block

### Unknown Fields

Unknown fields (fields not recognized by the schema) can be handled in three ways depending on the parse mode:

1. **Strict mode** (`strictMode=true`): Throws `UnknownFieldException`
2. **Collect mode** (`parseUnknownFields=true`): Stores as `UnknownField(fieldNumber, wireType, bytes)` for round-trip fidelity
3. **Skip mode** (default): Reads and discards the bytes via `skipField()`

Collected unknown fields are sorted by field number and appended during writes:

```java
data.getUnknownFields().forEach(uf -> {
    final int tag = (uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal();
    out.writeVarInt(tag, false);
    uf.bytes().writeTo(out);
});
```

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
