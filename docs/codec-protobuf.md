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

### Measure

- `measureRecord(T)` delegates to `data.protobufSize()`, which the model class caches.

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

## Performance Options

Generated codecs can include extra fast paths, chosen per message with the options in
[usage-guide.md](usage-guide.md#performance-options). All are off by default; `pbj.performance = "max"` turns them
all on. Generated code stays byte-identical to protoc with any combination, and a message with one setting can
contain messages with another.

| Option | What it generates |
|---|---|
| `pbj.fast_parse` | A byte-array parser with in-order fast paths, used automatically by `parse` for array-backed input |
| `pbj.fast_write` | A back-to-front writer into a reusable buffer: no measuring pass, no allocation |
| `pbj.views` | A zero-copy, reusable view class that reads fields in place from encoded bytes |
| `pbj.oneof_storage = "unboxed"` | Models store each oneof as a kind and a value, so parsing allocates no `OneOf` |

The code each option generates is added step by step (see [fast-codegen-plan.md](fast-codegen-plan.md)). Each
section below describes what an option generates today; an option without a section has no effect yet.

### Byte-Array Parser (`pbj.fast_parse`)

The codec gets a second parser, a static `parseArray(ArrayCursor, strictMode, parseUnknownFields, maxDepth, maxSize)`
that reads straight from a `byte[]` instead of through `ReadableSequentialData`. Nothing changes for callers:
`Codec.parse` uses it automatically when the input is array-backed, which means a `Bytes` or a `BufferedData` over a
heap array (`BufferedData.wrap(byte[])`, `BufferedData.allocate(int)`). Direct buffers and streams still use the
stream parser. Generated array parsers call each other directly for nested messages.

Writers emit fields in field-number order, so the parser first tests for each field's tag in that order, one byte
compare per field, and a repeated field loops on its tag. Oneofs and maps
stay out of this fast path. Anything else (oneofs, maps, fields out of order or repeated, unknown fields) falls
through to a general loop. Results and errors are the same as the stream parser's for the same input and arguments:
strict mode, unknown-field collection, `maxDepth`, `maxSize`, malformed UTF-8 and truncated input all behave alike.

Every field type is supported, so every message that asks for an array parser gets one.

### Reverse Writer (`pbj.fast_write`)

The codec gets a static `writeReverseTo(item, ReverseBuffer)` and overrides `Codec.writeReverse(item, buffer)` to use
it. `toBytes` and the other `write` methods are unchanged. The reverse writer writes the message back to front into a
reusable `ReverseBuffer`: fields, list elements and unknown fields in reverse order, and each value before its length
and tag. A nested message's length is then just the number of bytes written since it began, so nothing is measured
first, and once the buffer has grown to fit the largest message, writing allocates nothing. The output is
byte-identical to `toBytes`.

```java
final ReverseBuffer buffer = new ReverseBuffer(4096);
for (Order order : orders) {
    buffer.reset();
    Order.PROTOBUF.writeReverse(order, buffer);
    send(buffer.bytes());   // a view, valid until the next reset or write; toBytes() or copyTo() copy it
}
```

Maps are written as entries in reverse sorted-key order, and `google.protobuf` wrapper types as the wrapped value, then
its length and tag. A message with a map of enums writes itself forward into the buffer instead, measuring only that
message. `writeReverse` works for every codec: one without a reverse writer measures the item and writes it forward
into the buffer.

**When it pays: measure on your own data.** `pbj.fast_write` is off by default, and `toBytes` and the other `write`
methods never use the reverse writer; only `writeReverse` does. Skipping the measuring pass helps for objects encoded
right after they are built, while they are still in the CPU caches. But the reverse writer reads each object's fields
last first, against the order they were allocated in, which costs a lot once objects are out of cache. On AMD EPYC,
with HAPI block items:

| `STATE_CHANGES` items | Forward, fresh | Reverse, fresh | Forward, sizes cached | Reverse, sizes cached |
|---|---:|---:|---:|---:|
| 200 items, in cache | 89 µs | 72 µs (−20%) | 63 µs | 81 µs (+29%) |
| all 21,986 items, out of cache | 13.8 ms | 39.3 ms (2.8× slower) | 9.2 ms | 39.1 ms (4.3× slower) |

So it wins for small objects encoded straight after building, and loses for large workloads like HAPI's blocks and
whenever sizes are already cached. On Apple Silicon the two writers are about equal. Compare both writers on your
own messages before turning it on; `BlockItemKindBench` in the integration tests shows how.

### Zero-Copy Views (`pbj.views`)

The model gets a nested, reusable `View` class that reads fields in place from encoded bytes instead of parsing them
into objects:

```java
final Order.View view = new Order.View();
for (Bytes bytes : orders) {
    view.wrap(bytes);          // or wrap(byte[], offset, length)
    total += view.amount();    // numbers and enums are decoded once, by wrap
    if (view.hasCustomer()) {
        names.add(view.customer().name());   // a child view, re-wrapped with the parent
    }
}
```

`wrap` indexes the message in one pass, with the same in-order fast path and the same checks of tags, wire types,
lengths, bools and UTF-8 as parsing; a malformed message throws `ParseException`. Numbers are decoded into fields of
the view, and strings, bytes and nested messages are recorded as offsets and lengths. String and bytes fields also have
`xxxArray()`, `xxxOffset()`, `xxxLength()` and `xxxEquals(byte[])`, which read them without allocating; `xxx()` builds a
`String` or `Bytes`. An enum field's `xxxProtoOrdinal()` returns the raw number. `toModel()` parses the wrapped bytes
into a model object. Wrapping and reading numbers, enums, nested views and offsets allocate nothing.

A view and its child views are reused, so a child view returned earlier reads whatever its parent was last wrapped
over. The wrapped bytes must not change while a view reads them.

A message gets a view only if every field is a singular scalar, string, bytes, enum or message, and every message
field's type also gets a view. Messages with repeated fields, maps, oneofs or `google.protobuf` wrapper types get no
view.

