# PBJ Protobuf Binary Codec

This document describes how PBJ's generated protobuf binary codecs work — the wire format, generated codec anatomy, and handling of special field types.

For the shared codec interfaces and IO abstractions, see [codecs.md](codecs.md). For protobuf type mappings and spec compliance, see [protobuf-and-schemas.md](protobuf-and-schemas.md).

## Wire Format

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
