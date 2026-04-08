# PBJ XDR Binary Codec

This document describes how PBJ's generated XDR binary codecs work — the wire format, encoding rules, and generated codec anatomy.

For the shared codec interfaces and IO abstractions, see [codecs.md](codecs.md). For the XDR specification, see [RFC 4506](https://datatracker.ietf.org/doc/html/rfc4506).

## Wire Format

PBJ's XDR codec follows [RFC 4506 — XDR: External Data Representation Standard](https://datatracker.ietf.org/doc/html/rfc4506). XDR is a compact, portable, big-endian binary format where all fields are aligned to 4-byte boundaries.

### Alignment

Every XDR value occupies a multiple of 4 bytes on the wire. Values shorter than 4 bytes are padded with zero bytes to reach the next 4-byte boundary. Shorter primitives (bool, enum/int) are represented as 4-byte integers; longer ones (int64/hyper) as 8 bytes.

| Proto Type | XDR Type | Wire Size |
|-----------|----------|-----------|
| `bool` | XDR bool (RFC §4.4) | 4 bytes |
| `int32`, `uint32`, `sint32`, `fixed32`, `sfixed32`, `enum` | XDR int (RFC §4.1) | 4 bytes |
| `int64`, `uint64`, `sint64`, `fixed64`, `sfixed64` | XDR hyper (RFC §4.5) | 8 bytes |
| `float` | XDR float (RFC §4.7) | 4 bytes |
| `double` | XDR double (RFC §4.8) | 8 bytes |
| `string`, `bytes` | XDR opaque/string (RFC §4.11) | 4 (length) + data + padding |
| `message` | XDR structure (RFC §4.14) | inline, no prefix |
| `repeated` | XDR array (RFC §4.13) | 4 (count) + elements |
| `map` | XDR array of pairs | 4 (count) + key-value pairs |
| `oneof` | XDR union (RFC §4.15) | 4 (discriminant) + arm |

### Presence Encoding

XDR has no concept of optional fields natively. PBJ uses a **presence flag** (a 4-byte XDR int) before each singular non-message field:

- `0x00000000` — field is absent (default value; no value bytes follow)
- `0x00000001` — field is present (value bytes follow)

This is equivalent to the XDR optional type from RFC §4.19. Presence flags are not used for:
- **Repeated fields** — preceded by a 4-byte count (0 = empty)
- **OneOf fields** — preceded by a 4-byte discriminant (0 = UNSET)
- **Message fields** — always written inline (message presence is determined by field-level presence of the parent)

### Canonical Encoding

PBJ enforces **canonical** XDR encoding:

- Fields with default values (0, false, empty string/bytes, null) are written with presence=0 and no value bytes
- Fields with non-default values are written with presence=1 followed by the encoded value
- Parsing fails with `ParseException` if presence=1 but the decoded value is the default

This ensures that `toBytes(object)` is deterministic and that `parse(toBytes(obj)).equals(obj)` holds for all objects.

### String and Bytes Encoding

Strings are UTF-8 encoded. Both strings and bytes fields use the XDR opaque encoding:

```
[length: 4 bytes big-endian] [data: N bytes] [padding: (4 - N%4) % 4 zero bytes]
```

Example: `"hello"` (5 bytes) →
```
0x00 0x00 0x00 0x05  // length = 5
0x68 0x65 0x6C 0x6C  // 'h' 'e' 'l' 'l'
0x6F 0x00 0x00 0x00  // 'o' + 3 padding bytes
```

### OneOf Encoding

OneOf fields use an XDR union (RFC §4.15): a 4-byte discriminant followed by the arm value if the discriminant is non-zero.

The discriminant is the `protoOrdinal()` of the active arm's enum value. `UNSET` (which has `protoOrdinal() = -1`) maps to discriminant 0:

```
[discriminant: 4 bytes] [arm value, if discriminant != 0]
```

### Nested Message Encoding

Nested messages are written inline (no length prefix, unlike protobuf's length-delimited encoding). The nested message's own presence flags establish field boundaries.

## Generated XDR Codec — Anatomy

For each protobuf message, PBJ generates `<MessageName>XdrCodec.java` in the `.codec` sub-package. The codec implements `XdrCodec<T>`, which extends `Codec<T>`.

### Parse Method

```java
@Override
public HelloRequest parse(ReadableSequentialData input,
                          boolean strictMode, boolean parseUnknownFields,
                          int maxDepth, int maxSize) throws ParseException {
    if (maxDepth < 0) throw new ParseException("Max depth exceeded");
    // For each field, read presence flag; if 1, read value
    String name = "";
    if (XdrParserTools.readPresence(input)) {
        name = XdrParserTools.readString(input, maxSize);
    }
    Address address = Address.DEFAULT;
    if (XdrParserTools.readPresence(input)) {
        address = Address.XDR.parse(input, strictMode, parseUnknownFields, maxDepth - 1, maxSize);
    }
    return new HelloRequest(name, address);
}
```

Key points:
- Fields are written and read **in proto field number order** (ascending)
- `maxDepth` is decremented for each nested message parse call
- `XdrParserTools.readPresence()` reads a 4-byte int and validates it is 0 or 1

### Write Method

```java
@Override
public void write(HelloRequest item, WritableSequentialData output) throws IOException {
    // Field 1: name
    if (item.name() != null && !item.name().isEmpty()) {
        XdrWriterTools.writePresence(output, true);
        XdrWriterTools.writeString(output, item.name());
    } else {
        XdrWriterTools.writePresence(output, false);
    }
    // Field 2: address
    if (item.address() != null) {
        XdrWriterTools.writePresence(output, true);
        Address.XDR.write(item.address(), output);
    } else {
        XdrWriterTools.writePresence(output, false);
    }
}
```

### MeasureRecord Method

```java
@Override
public int measureRecord(HelloRequest item) {
    int size = 0;
    // Presence flag is always written (4 bytes per singular field)
    size += 4;
    if (item.name() != null && !item.name().isEmpty()) {
        size += XdrWriterTools.sizeOfString(item.name());
    }
    size += 4;
    if (item.address() != null) {
        size += Address.XDR.measureRecord(item.address());
    }
    return size;
}
```

## Runtime Helper Classes

### `XdrWriterTools`

Static utility methods for writing XDR field values:

| Method | Description |
|--------|-------------|
| `writePresence(out, present)` | Write a 4-byte presence flag (0 or 1) |
| `writeInt(out, value)` | Write a 4-byte big-endian integer |
| `writeHyper(out, value)` | Write an 8-byte big-endian long |
| `writeFloat(out, value)` | Write a 4-byte IEEE 754 float |
| `writeDouble(out, value)` | Write an 8-byte IEEE 754 double |
| `writeBool(out, value)` | Write a 4-byte bool (0 or 1) |
| `writeString(out, value)` | Write 4-byte length + UTF-8 bytes + padding |
| `writeBytes(out, value)` | Write 4-byte length + bytes + padding |
| `sizeOfString(value)` | Compute encoded size: 4 + len + padding |
| `sizeOfBytes(value)` | Compute encoded size: 4 + len + padding |

### `XdrParserTools`

Static utility methods for reading XDR field values:

| Method | Description |
|--------|-------------|
| `readPresence(input)` | Read and validate a presence flag; throws `ParseException` if not 0 or 1 |
| `readInt(input)` | Read a 4-byte big-endian integer |
| `readHyper(input)` | Read an 8-byte big-endian long |
| `readFloat(input)` | Read a 4-byte IEEE 754 float |
| `readDouble(input)` | Read an 8-byte IEEE 754 double |
| `readBool(input)` | Read and validate a 4-byte bool; throws if not 0 or 1 |
| `readString(input, maxSize)` | Read length-prefixed UTF-8 string with padding |
| `readBytes(input, maxSize)` | Read length-prefixed bytes with padding |
| `readEnum(input)` | Read a 4-byte integer for enum ordinal lookup |

## Design Decisions

### Why Presence Flags Instead of Optional Types?

XDR's native optional type (`optional<T>`) is functionally identical to a presence flag. PBJ uses explicit presence flags because it matches protobuf's "singular field absent if default" semantics and makes the canonical encoding invariant explicit in the generated code.

### Why No Field Tags?

Unlike protobuf, XDR has no field tags on the wire. Fields are identified by position. This means:
- XDR is **not self-describing** — the decoder must know the schema
- XDR is **schema-strict** — adding or removing fields is a breaking change
- XDR is **faster to decode** for known schemas — no tag parsing, direct sequential reads

### Why Inline Nested Messages?

Protobuf uses length-prefixed nested messages so the parser can skip unknown nested messages. XDR's strict forward-compatibility model means nested messages are always inlined — the parser must read all fields. This simplifies the generated code and avoids an extra length varint for each nested message.

### XDR vs Protobuf Size

XDR typically produces larger output than protobuf for messages with many fields because presence flags are always written (4 bytes per field), while protobuf omits the entire field (including tag) for default values. However, XDR avoids varint encoding overhead for large integers, making it faster for messages with many non-default large integer fields.
