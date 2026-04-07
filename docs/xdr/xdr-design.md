# XDR Codec for PBJ — Design Document

## 1. Introduction

This document describes how to add XDR (External Data Representation, RFC 4506) as a third
serialization format to PBJ, alongside the existing protobuf binary and JSON formats.

**Motivation:** The CLPR (Cross-Ledger Protocol) requires a binary serialization format for
encoding protocol messages that travel both over the wire (gRPC between endpoints) and on-chain
(as calldata in smart contracts on EVM and other chains). The format must be deterministic,
gas-efficient for on-chain parsing, compact, cross-chain-neutral, and formally standardized.
XDR was selected because its big-endian encoding is native to both the EVM and JVM — extracting
a `uint64` from XDR calldata on the EVM is a single `CALLDATALOAD` + `SHR` (~8 gas), with no
byte-swap overhead. See the companion [XDR Use Case document](xdr-use-case.md) for the full
requirements analysis and format comparison.

**Goal:** For every `.proto` message, PBJ will generate an additional XDR codec class that
serializes/deserializes the message in XDR wire format. Users will access it as:

```java
public static final Codec<HelloRequest> XDR = new HelloRequestXdrCodec();

// Usage
Bytes xdrBytes = HelloRequest.XDR.toBytes(msg);
HelloRequest msg = HelloRequest.XDR.parse(xdrData);
```

## 2. XDR Format Summary (RFC 4506)

XDR is a simple, deterministic binary format with these core rules:

- **Big-endian** byte order throughout (network byte order)
- **4-byte alignment** — all values are encoded in multiples of 4 bytes, with zero-padding as needed
- **No type metadata** — encoding contains no tags or field identifiers; both sides must agree on the schema
- **Deterministic** — same input always produces same output

### XDR Primitive Types

| XDR Type | Size | Description |
|----------|------|-------------|
| `int` | 4 bytes | Signed 32-bit, two's complement, big-endian |
| `unsigned int` | 4 bytes | Unsigned 32-bit, big-endian |
| `hyper` | 8 bytes | Signed 64-bit, two's complement, big-endian |
| `unsigned hyper` | 8 bytes | Unsigned 64-bit, big-endian |
| `float` | 4 bytes | IEEE 754 single-precision, big-endian |
| `double` | 8 bytes | IEEE 754 double-precision, big-endian |
| `bool` | 4 bytes | Encoded as int: 0 = false, 1 = true |
| `enum` | 4 bytes | Named signed int constants |
| `void` | 0 bytes | No data |

### XDR Composite Types

| XDR Type | Encoding |
|----------|----------|
| `string<max>` | 4-byte length (uint) + UTF-8 data + 0-3 zero padding bytes to 4-byte boundary |
| `opaque<max>` | 4-byte length (uint) + raw bytes + 0-3 zero padding bytes |
| `opaque[n]` | Fixed n bytes + 0-3 zero padding bytes |
| `type[n]` (fixed array) | n elements consecutively, no length prefix |
| `type<max>` (variable array) | 4-byte count (uint) + elements |
| `struct` | Fields encoded sequentially in declaration order |
| `union switch(disc) { ... }` | 4-byte discriminant + selected arm's encoding |
| `type *identifier` (optional) | 4-byte presence bool + data if present |

### Alignment and Padding

Variable-length data (strings, opaque) is padded to 4-byte boundaries:
```
padding = (4 - (dataLength % 4)) % 4
```
Padding bytes are always zero.

## 3. Protobuf-to-XDR Type Mapping

### Scalar Types

| Proto Type | Java Type | XDR Type | XDR Bytes | Notes |
|------------|-----------|----------|-----------|-------|
| `int32` | `int` | `int` | 4 | Direct big-endian write. No varint encoding. |
| `uint32` | `int` | `unsigned int` | 4 | Same bit pattern as signed; Java handles unsigned at app level. |
| `sint32` | `int` | `int` | 4 | No zigzag needed — XDR uses plain two's complement. |
| `int64` | `long` | `hyper` | 8 | Direct big-endian write. |
| `uint64` | `long` | `unsigned hyper` | 8 | Same bit pattern as signed hyper. |
| `sint64` | `long` | `hyper` | 8 | No zigzag needed. |
| `fixed32` | `int` | `unsigned int` | 4 | Proto is little-endian; XDR is big-endian. Byte order swap. |
| `sfixed32` | `int` | `int` | 4 | Proto is little-endian; XDR is big-endian. Byte order swap. |
| `fixed64` | `long` | `unsigned hyper` | 8 | Byte order swap from little to big endian. |
| `sfixed64` | `long` | `hyper` | 8 | Byte order swap from little to big endian. |
| `float` | `float` | `float` | 4 | IEEE 754 both sides. Proto=LE, XDR=BE. |
| `double` | `double` | `double` | 8 | IEEE 754 both sides. Proto=LE, XDR=BE. |
| `bool` | `boolean` | `bool` | 4 | XDR bool is 4 bytes (vs proto's 1-byte varint). |
| `string` | `String` | `string` | 4+N+pad | 4-byte length + UTF-8 data + 0-3 padding bytes. |
| `bytes` | `Bytes` | `opaque<>` | 4+N+pad | 4-byte length + raw data + 0-3 padding bytes. See PBJ annotations below for `opaque[N]` (fixed-length) variant. |
| `enum` | enum class | `enum` | 4 | Write `protoOrdinal()` as 4-byte signed int. |

### Composite Types

| Proto Type | Java Type | XDR Encoding | Details |
|------------|-----------|-------------|---------|
| `message` | model class | `struct` (with presence) | PBJ generates immutable classes, not records. See Section 4 for field-presence design. |
| `repeated T` | `List<T>` | variable-length array | 4-byte count + N elements. |
| `map<K,V>` | `PbjMap<K,V>` | variable-length array of struct{K,V} | 4-byte count + sorted key-value pairs. |
| `oneof` | `OneOf<E>` | discriminated union | 4-byte discriminant (field number) + arm value. |
| optional wrappers (`StringValue`, etc.) | nullable types | XDR optional | 4-byte presence bool + inner value if present. |

### PBJ Annotations for XDR

Protobuf's type system is ambiguous in some cases that XDR needs to resolve. PBJ introduces
annotations to bridge these gaps:

**Fixed-length opaque.** Protobuf `bytes` does not distinguish fixed-length from variable-length.
For fields that are always a known fixed size (e.g., 32-byte hashes), a PBJ annotation tells the
code generator to emit XDR `opaque[N]` (no length prefix) instead of `opaque<>` (with length prefix):

```protobuf
bytes running_hash = 3; // option (pbj.xdr_fixed_length) = 32
```

This saves 4 bytes per field on the wire and eliminates one length-read on decode. The XDR
encoding for a fixed-length opaque is simply `N` data bytes + padding to the next 4-byte
boundary (no length prefix).

**Maximum lengths for variable-length fields.** XDR variable-length arrays and opaque data
require a maximum length in the schema. These are derived from protocol-defined limits (e.g.,
`MaxMessagesPerBundle` for message arrays, `MaxQueueDepth` for queue arrays) and specified via
PBJ annotations:

```protobuf
repeated ClprMessage messages = 1; // option (pbj.xdr_max_length) = 1024
```

The generated XDR codec enforces these limits during both parsing and size measurement.

## 4. Field Presence and Message Encoding

### The Fundamental Challenge

Protobuf identifies fields by tag (field number + wire type) on the wire, and absent fields
simply don't appear. XDR structs are positional — every field occupies its position, and there
is no metadata to identify fields.

### Design: Use XDR Optional for Singular Fields

Each proto message is encoded as follows:

1. Fields are sorted by **field number** (ascending) — this is already the PBJ convention
2. Each **singular** field (non-repeated, non-oneof, non-map) is encoded as an **XDR optional**:
   - 4-byte presence flag: `1` if the field has a non-default value, `0` otherwise
   - If present: the field's value in XDR encoding
3. **Repeated** fields encode as XDR variable-length arrays (count can be 0 for empty)
4. **Map** fields encode as XDR variable-length arrays of key-value structs (count can be 0)
5. **OneOf** fields encode as XDR discriminated unions (discriminant = field number, 0 = unset)

### Wire Format by Field Kind

**Singular scalar (int32, uint32, sint32, fixed32, sfixed32, float, enum):**
```
[4B presence: 0|1] [4B value]  ← value only if presence=1
```

**Singular 64-bit (int64, uint64, sint64, fixed64, sfixed64, double):**
```
[4B presence: 0|1] [8B value]  ← value only if presence=1
```

**Singular bool:**
```
[4B presence: 0|1] [4B bool: 0|1]  ← value only if presence=1
```

**Singular string:**
```
[4B presence: 0|1] [4B length][UTF-8 data][0-3B padding]  ← data only if presence=1
```

**Singular bytes:**
```
[4B presence: 0|1] [4B length][raw data][0-3B padding]  ← data only if presence=1
```

**Singular message (nested):**
```
[4B presence: 0|1] [recursive XDR encoding of nested message]  ← only if presence=1
```

**Repeated field:**
```
[4B count: N] [element_1][element_2]...[element_N]
```
Each element encoded per its type, no per-element presence flag.

**Map field:**
```
[4B count: N] [key_1][value_1][key_2][value_2]...[key_N][value_N]
```
Entries sorted by key (PBJ's existing behavior).

**OneOf field:**
```
[4B discriminant: field_number | 0] [arm value encoding]  ← arm only if discriminant != 0
```
The discriminant is the proto field number of the active case. `0` means UNSET (no arm encoded).

**Optional value wrapper (StringValue, Int32Value, etc.):**
```
[4B presence: 0|1] [inner value encoding]  ← no wrapping message, just the inner value
```

### Example: Complete Message Encoding

Given:
```protobuf
message Example {
    int32 id = 1;
    string name = 2;
    repeated int32 tags = 3;
    oneof payload {
        string text = 4;
        bytes data = 5;
    }
}
```

With values: `id=42, name="hello", tags=[1,2,3], payload=text("hi")`

XDR encoding:
```
Field 1 (id=42):     [00 00 00 01] [00 00 00 2A]           ← presence=1, value=42
Field 2 (name):      [00 00 00 01] [00 00 00 05] "hello" [00 00 00]  ← presence=1, len=5, data, 3B pad
Field 3 (tags):      [00 00 00 03] [00 00 00 01] [00 00 00 02] [00 00 00 03]  ← count=3, values
Field 4-5 (oneof):   [00 00 00 04] [00 00 00 02] "hi" [00 00]  ← disc=4 (text), len=2, data, 2B pad
```

Total: 8 + 16 + 16 + 14 = 54 bytes (all 4-byte aligned).

## 5. Edge Cases and Compatibility Analysis

### 5.1 Field Number Gaps

Proto schemas can have non-contiguous field numbers (1, 5, 100). Since XDR encoding is
positional by sorted field number, gaps simply mean fewer fields to encode — there are no
phantom slots for missing numbers. The decoder knows the schema and reads the same field
sequence.

### 5.2 Schema Evolution

XDR has **limited** schema evolution compared to protobuf:

| Scenario | Protobuf | XDR (this design) |
|----------|----------|-------------------|
| Add field (new highest number) | Works — unknown tags skipped | Works — old decoders stop at their last known field; new decoders see absence for new fields from old data |
| Add field (in the middle) | Works — tag-based | **Breaks** — positional encoding means old and new decoders disagree on field positions |
| Remove field | Works — unknown tags skipped | Partially works — slot remains, always reads as absent |
| Reorder fields | Works — tag-based | **Breaks** — positional encoding |
| Change field type | Risky in both | **Breaks** — no wire type to detect mismatch |

**Recommendation:** Document clearly that XDR encoding is a **snapshot format** — both encoder
and decoder must use the same schema version. It is suitable for deterministic hashing,
storage, and same-version communication, not for protocol evolution.

### 5.3 Unknown Fields

XDR is strictly schema-driven. Unknown fields **cannot** be preserved in XDR:
- **Write:** Unknown fields from the model are not written to XDR output
- **Parse:** `strictMode` and `parseUnknownFields` parameters are not applicable; the parser reads only known fields

### 5.4 Default Values vs Absent Fields

Proto3 does not distinguish "explicitly set to default" from "absent" for scalar fields.
The XDR presence flag follows the same convention:
- Presence = 0 means the field is at its default value (or absent)
- Presence = 1 means the field has a non-default value

For **optional value wrappers** (StringValue, Int32Value, etc.), which PBJ models as nullable
Java types, the presence flag correctly distinguishes null from a default value.

### 5.5 Bool Size Expansion

Proto bool is a 1-byte varint. XDR bool is 4 bytes. This is inherent to XDR's 4-byte alignment
and unavoidable. A message with many bool fields will be significantly larger in XDR.

### 5.6 No Varint Encoding

All XDR integers are fixed-width (4 or 8 bytes). Small values that protobuf encodes in 1-2
bytes via varint will always take 4 or 8 bytes in XDR. This is by design — XDR trades size
for simplicity and O(1) field access.

### 5.7 Unsigned Types in Java

Java has no unsigned primitives. The existing PBJ code already handles `uint32`/`uint64` as
signed Java `int`/`long` with the understanding that the bit pattern represents an unsigned
value. For XDR, unsigned int and unsigned hyper use the same bit patterns on wire — no special
handling needed beyond what already exists.

### 5.8 Enum Values

Proto enums use `protoOrdinal()` values which can be arbitrary integers (not just 0..N).
XDR enums are also arbitrary signed ints. Direct mapping: write/read `protoOrdinal()` as a
4-byte signed int.

Proto3 enums can have values outside the known set (unrecognized). PBJ stores these as raw
integers. XDR encodes the raw integer, preserving unknown enum values across round-trips.

### 5.9 Nested Message Boundaries

In protobuf, nested messages are length-prefixed. In XDR, structs are concatenated with no
length prefix — the schema tells the decoder exactly what fields to expect.

This design does **not** add length prefixes for nested messages, keeping the format pure XDR.
The decoder recursively parses the nested message's fields in order.

**Implication:** You cannot skip a nested message without fully parsing its structure. This is
an acceptable trade-off for a schema-driven format.

### 5.10 Map Key and Value Types

XDR has no native map type. Maps are encoded as sorted arrays of key-value struct pairs.
PBJ's `PbjMap` already guarantees sorted key order via `getSortedKeys()`.

Map key types are restricted to scalars (int32, int64, uint32, uint64, sint32, sint64,
fixed32, fixed64, sfixed32, sfixed64, bool, string) — same restriction as protobuf.

### 5.11 Recursive/Self-Referencing Messages

Messages that reference themselves (directly or indirectly) work correctly because:
- The presence flag (0) terminates recursion for absent nested messages
- The `maxDepth` parameter prevents infinite recursion in malicious data

## 6. IO Layer Compatibility (Java)

PBJ's `ReadableSequentialData` and `WritableSequentialData` already support XDR natively:

| Method | Byte Order | XDR Compatible? |
|--------|-----------|-----------------|
| `readInt()` / `writeInt()` | Big-endian (default) | Yes — exactly what XDR needs |
| `readLong()` / `writeLong()` | Big-endian (default) | Yes |
| `readFloat()` / `writeFloat()` | Big-endian (default) | Yes |
| `readDouble()` / `writeDouble()` | Big-endian (default) | Yes |
| `readByte()` / `writeByte()` | N/A | Yes — for padding bytes |
| `readBytes()` / `writeBytes()` | N/A | Yes — for string/opaque data |

Protobuf codecs explicitly use `ByteOrder.LITTLE_ENDIAN` variants. XDR codecs will use the
default (no byte-order argument) methods, which are big-endian. This is actually the simpler
path — no byte-order parameter needed.

## 7. Implementation Architecture

### 7.1 Design Considerations

**Two write paths.** PBJ's protobuf codec provides both a streaming write path
(`write(T, WritableSequentialData)`) and a byte-array fast path (`write(T, byte[], offset)`)
via `ProtoArrayWriterTools`, which avoids virtual dispatch overhead. The XDR codec should
initially implement only the streaming path. A `XdrArrayWriterTools` class for the byte-array
fast path can be added in a later phase if benchmarks show it is needed (see Open Questions).

**XDR size caching.** PBJ's protobuf codec delegates `measureRecord()` to
`data.protobufSize()`, which is lazily computed and cached in the model object. For XDR, we
have two options:
1. Add a cached `$xdrEncodedSize` field to model objects (paralleling `$protobufEncodedSize`)
2. Compute XDR size on-the-fly in the codec's `measureRecord()` method

Option 2 is simpler and avoids expanding all model objects. Since XDR size computation is
straightforward (no varints, no tag overhead), it should be fast enough without caching.
Recommend starting with option 2 and reconsidering if profiling shows it is a bottleneck.

### 7.2 New Runtime Classes

**`XdrParserTools`** — static helper methods for reading XDR types:
```java
// Package: com.hedera.pbj.runtime
public final class XdrParserTools {
    static int readInt(ReadableSequentialData in)          // 4-byte big-endian int
    static long readHyper(ReadableSequentialData in)       // 8-byte big-endian long
    static float readFloat(ReadableSequentialData in)      // 4-byte IEEE754 BE float
    static double readDouble(ReadableSequentialData in)    // 8-byte IEEE754 BE double
    static boolean readBool(ReadableSequentialData in)     // 4-byte int → boolean
    static String readString(ReadableSequentialData in, int maxSize)  // length + data + skip padding
    static Bytes readOpaque(ReadableSequentialData in, int maxSize)   // length + data + skip padding
    static int readEnum(ReadableSequentialData in)         // 4-byte signed int
    static boolean readPresence(ReadableSequentialData in) // 4-byte bool → boolean
    static void skipPadding(ReadableSequentialData in, int dataLength) // skip 0-3 pad bytes
}
```

**`XdrWriterTools`** — static helper methods for writing XDR types:
```java
// Package: com.hedera.pbj.runtime
public final class XdrWriterTools {
    static void writeInt(WritableSequentialData out, int value)
    static void writeHyper(WritableSequentialData out, long value)
    static void writeFloat(WritableSequentialData out, float value)
    static void writeDouble(WritableSequentialData out, double value)
    static void writeBool(WritableSequentialData out, boolean value)
    static void writeString(WritableSequentialData out, String value)
    static void writeOpaque(WritableSequentialData out, Bytes value)
    static void writeEnum(WritableSequentialData out, int protoOrdinal)
    static void writePresence(WritableSequentialData out, boolean present)
    static void writePadding(WritableSequentialData out, int dataLength)

    // Size measurement
    static int sizeOfString(String value)    // 4 + UTF8 byte length + padding
    static int sizeOfOpaque(Bytes value)     // 4 + data length + padding
    static int sizeOfOpaque(int dataLength)  // 4 + dataLength + padding
    static int paddingSize(int dataLength)   // (4 - (len % 4)) % 4
}
```

**`XdrCodec<T>`** — interface extending `Codec<T>`:
```java
// Package: com.hedera.pbj.runtime
public interface XdrCodec<T> extends Codec<T> {
    // May add XDR-specific convenience methods in the future.
    // For now, Codec<T> methods suffice since XDR reads/writes
    // through the same ReadableSequentialData/WritableSequentialData.
}
```

### 7.3 New Compiler Generator Classes

All in package `com.hedera.pbj.compiler.impl.generators.xdr`:

| Class | Responsibility |
|-------|---------------|
| `XdrCodecGenerator` | Main orchestrator — generates `XxxXdrCodec` class implementing `XdrCodec<Xxx>` |
| `XdrCodecParseMethodGenerator` | Generates the `parse()` method — reads fields sequentially by field number |
| `XdrCodecWriteMethodGenerator` | Generates the `write()` method — writes fields sequentially by field number |
| `XdrCodecMeasureRecordMethodGenerator` | Generates the `measureRecord()` method — calculates XDR byte size |

These follow the exact same pattern as the existing protobuf generators in
`com.hedera.pbj.compiler.impl.generators.protobuf`.

### 7.4 Integration Points

| File | Change |
|------|--------|
| `FileType` enum | Add `XDR_CODEC` value |
| `FileAndPackageNamesConfig` | Add `XDR_CODEC_JAVA_FILE_SUFFIX = "XdrCodec"`. XDR codecs go in the `.codec` subpackage (same as protobuf and JSON codecs). |
| `LookupHelper` | Update `formatFileTypeName()` to append `"XdrCodec"` suffix for `XDR_CODEC`. Update `getPackage()` to route `XDR_CODEC` to the `.codec` subpackage. |
| `FileSetWriter` | Add `JavaFileWriter xdrCodecWriter` field to the record. Update `create()` to instantiate the writer for `FileType.XDR_CODEC`. Update `writeAllFiles()` to include the new writer. |
| `Generator.GENERATORS` map | Add `XdrCodecGenerator.class -> FileSetWriter::xdrCodecWriter` |
| `ModelGenerator` | In `generateCodecFields()`, add `public static final XdrCodec<Xxx> XDR = new XxxXdrCodec();` alongside `PROTOBUF` and `JSON` |
| `ContextualLookupHelper` | No changes needed — delegates to `LookupHelper` which handles `XDR_CODEC` via the `FileType` switch |

### 7.5 Generated Output Structure

XDR codecs follow the same package layout as protobuf and JSON codecs:

```
com/example/proto/
├── MessageName.java                    (model — updated with XDR field)
├── schema/
│   └── MessageNameSchema.java          (schema — unchanged)
├── codec/
│   ├── MessageNameProtoCodec.java      (protobuf codec — unchanged)
│   ├── MessageNameJsonCodec.java       (JSON codec — unchanged)
│   └── MessageNameXdrCodec.java        (NEW: XDR codec)
└── tests/
    └── MessageNameTest.java            (test — extended with XDR round-trip tests)
```

### 7.6 Generated Code Example

For a message:
```protobuf
message HelloRequest {
    string name = 1;
    int32 age = 2;
    repeated string tags = 3;
}
```

Generated `HelloRequestXdrCodec`:
```java
public final class HelloRequestXdrCodec implements XdrCodec<HelloRequest> {

    @Override
    public HelloRequest parse(ReadableSequentialData input, boolean strictMode,
            boolean parseUnknownFields, int maxDepth, int maxSize) throws ParseException {
        // Field 1: name (string, optional)
        String temp_name = "";
        if (XdrParserTools.readPresence(input)) {
            temp_name = XdrParserTools.readString(input, maxSize);
        }
        // Field 2: age (int32, optional)
        int temp_age = 0;
        if (XdrParserTools.readPresence(input)) {
            temp_age = XdrParserTools.readInt(input);
        }
        // Field 3: tags (repeated string)
        List<String> temp_tags = Collections.emptyList();
        final int tags_count = input.readInt();
        if (tags_count > 0) {
            if (tags_count > maxSize) throw new ParseException("tags count exceeds maxSize");
            for (int i = 0; i < tags_count; i++) {
                temp_tags = addToList(temp_tags, XdrParserTools.readString(input, maxSize));
            }
        }
        // Post-processing: make lists read-only
        if (temp_tags instanceof UnmodifiableArrayList<?> ual) ual.makeReadOnly();
        return new HelloRequest(temp_name, temp_age, temp_tags);
    }

    @Override
    public void write(HelloRequest item, WritableSequentialData output) throws IOException {
        // Field 1: name
        final boolean name_present = !item.name().isEmpty();
        XdrWriterTools.writePresence(output, name_present);
        if (name_present) {
            XdrWriterTools.writeString(output, item.name());
        }
        // Field 2: age
        final boolean age_present = item.age() != 0;
        XdrWriterTools.writePresence(output, age_present);
        if (age_present) {
            XdrWriterTools.writeInt(output, item.age());
        }
        // Field 3: tags (repeated)
        XdrWriterTools.writeInt(output, item.tags().size());
        for (final String elem : item.tags()) {
            XdrWriterTools.writeString(output, elem);
        }
    }

    @Override
    public int measureRecord(HelloRequest item) {
        int size = 0;
        // Field 1: name
        size += 4; // presence flag
        if (!item.name().isEmpty()) {
            size += XdrWriterTools.sizeOfString(item.name());
        }
        // Field 2: age
        size += 4; // presence flag
        if (item.age() != 0) {
            size += 4; // int32
        }
        // Field 3: tags (repeated)
        size += 4; // count
        for (final String elem : item.tags()) {
            size += XdrWriterTools.sizeOfString(elem);
        }
        return size;
    }

    @Override
    public int measure(ReadableSequentialData input) throws ParseException {
        final long start = input.position();
        parse(input, false, false, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE);
        return (int)(input.position() - start);
    }

    @Override
    public boolean fastEquals(HelloRequest item, ReadableSequentialData input) throws ParseException {
        return item.equals(parse(input));
    }

    @Override
    public HelloRequest getDefaultInstance() {
        return HelloRequest.DEFAULT;
    }
}
```

## 8. Solidity XDR Decoder Generation (Future Phase)

In addition to Java codecs, PBJ will generate purpose-built Solidity decoder contracts for
each CLPR message type. This is a future phase after the Java codecs are working.

### 8.1 Design Principles

**Work directly on calldata.** Use `calldataload` and `calldatacopy` instead of first copying
to memory. This avoids the quadratic memory expansion cost for fields that are only being hashed.

**Constant offsets for fixed-size structs.** For structs like `ClprQueueMetadata` where all
fields are fixed-size, all field offsets are compile-time constants. No runtime computation needed.

**Iterator pattern for variable-length arrays.** For `repeated` fields (e.g., messages in a
bundle), generate an iterator that advances a pointer rather than materializing the entire
array in memory.

**Minimal decoding for hash verification.** For the running hash chain path, generate a
specialized function that extracts raw payload bytes without parsing the internal structure,
since only the bytes matter for hashing.

### 8.2 Gas Cost Analysis for CLPR-Critical Structures

**ClprQueueMetadata (fixed-size).** Decoded on every bundle verification. Fields:
`next_message_id` (uint64), `acked_message_id` (uint64), `sent_running_hash` (bytes32),
`received_running_hash` (bytes32), `received_message_id` (uint64). XDR encoding: 8 + 8 + 32 +
32 + 8 = **88 bytes**, all at known offsets. Estimated gas for full decode: ~40 gas
(5 × `CALLDATALOAD` at 3 gas each + shifts).

**ClprMessagePayload (union).** XDR encoding: 4-byte discriminant + arm-specific data. Reading
the discriminant is a single `CALLDATALOAD` + `SHR` (~8 gas). For the running hash path, the
contract does not need to parse the union internals — it just needs raw bytes for `keccak256`.

**Repeated messages in a bundle.** A bundle of N messages: 4 bytes (array count) + N ×
(message payload + running hash). Sequential iteration matches XDR's access pattern perfectly.

### 8.3 Implementation Approach

1. Define Solidity XDR decoder generator in PBJ compiler
2. Generate fixed-size struct decoders first (constant offsets, simplest case)
3. Add variable-length and union type decoders
4. Implement gas-cost benchmarks for generated decoders
5. Integrate with the CLPR Service contract

## 9. Size Comparison

For perspective, here's how the same data encodes across formats:

| Field | Proto (bytes) | XDR (bytes) | Notes |
|-------|-------------|-------------|-------|
| `int32 = 42` | 2 (tag + varint) | 8 (4 presence + 4 value) | 4x larger |
| `int32 = 0` (default) | 0 (omitted) | 4 (presence=0 only) | |
| `bool = true` | 2 (tag + varint) | 8 (4 presence + 4 bool) | 4x larger |
| `string = "hello"` | 7 (tag + varint_len + 5) | 16 (4 presence + 4 len + 5 data + 3 pad) | ~2.3x larger |
| `int64 = 1000000` | 4 (tag + varint) | 12 (4 presence + 8 value) | 3x larger |
| `bytes = 100 bytes` | 102 (tag + varint_len + 100) | 108 (4 presence + 4 len + 100 data) | ~6% larger |

XDR is consistently larger than protobuf for small values but approaches parity for large
binary payloads. The trade-off is simplicity and determinism.

## 10. Properties and Guarantees

The XDR codec provides these properties:

1. **Deterministic** — Same model object always produces identical XDR bytes
2. **Round-trip fidelity** — `parse(write(obj)) == obj` for all valid objects (excluding unknown fields)
3. **Canonical encoding** — Only one valid XDR encoding exists for each model state
4. **4-byte aligned** — Every field starts and ends on a 4-byte boundary
5. **No self-description** — Decoder must know the schema; no embedded type information
6. **Big-endian** — Network byte order throughout

These properties make XDR especially suitable for:
- Deterministic hashing (same object = same hash, guaranteed)
- Content-addressed storage
- Consensus protocols where byte-identical encoding is required
- Interoperability with XDR-based systems (Stellar, NFS/RPC, etc.)

## 11. Testing Strategy

### Unit Tests
- Round-trip tests for each proto field type: write → parse → compare
- Empty/default message encoding
- Nested messages at various depths
- OneOf with each arm type
- Maps with various key/value type combinations
- Repeated fields (empty, single, many elements)
- String/bytes alignment padding (lengths 0, 1, 2, 3, 4, 5 to test all padding cases)
- Maximum size and depth limit enforcement

### Integration Tests
- Cross-codec consistency: verify that `XDR.parse(XDR.toBytes(obj))` equals
  `PROTOBUF.parse(PROTOBUF.toBytes(obj))` for the same model object
- All existing test proto messages in `pbj-integration-tests` should get XDR codec generation
- Measure XDR size vs protobuf size for representative messages

### Fuzz Tests
- Random valid model objects → XDR write → XDR parse → compare
- Random byte arrays → XDR parse → verify graceful failure or valid object

## 12. Open Questions

1. ~~**Should we support `XdrCodec<T>` as a separate interface or just use `Codec<T>`?**~~
   **Resolved: Yes.** Using a separate `XdrCodec<T>` follows the `JsonCodec<T>` pattern and
   is consistent with PBJ's "codec-per-format" architecture principle (see `docs/codecs.md`).
   Allows adding XDR-specific methods later (e.g., `toXdrHex()`).

2. **Should XDR encoding include a top-level message length prefix?**
   Pure XDR structs don't have length prefixes. However, for streaming use cases, a 4-byte
   length prefix before the message would allow framing. This could be an option on the codec
   rather than part of the default encoding.

3. **Should we generate `XdrArrayWriterTools` (byte[] fast path) like `ProtoArrayWriterTools`?**
   This is a performance optimization. Recommend deferring to a later phase unless benchmarks
   show it's needed. See Section 7.1 for discussion.

4. **How should `fastEquals()` be implemented?**
   The simplest approach is `item.equals(parse(input))`. A more optimized version could
   compare field-by-field without constructing the full object, but this is a later optimization.

5. **Should model objects cache `xdrEncodedSize()`?**
   PBJ's protobuf codec caches encoded size in a lazy `$protobufEncodedSize` field on the
   model object. XDR size computation is simpler than protobuf (no varints, no tag overhead),
   so on-the-fly computation may be fast enough. Recommend starting without caching and
   reconsidering if profiling shows it is a bottleneck. See Section 7.1 for discussion.

6. **What annotation syntax should be used for fixed-length opaque and max lengths?**
   Options include proto option extensions (`option (pbj.xdr_fixed_length) = 32`), PBJ
   comment annotations (`// <<<pbj.xdr_fixed_length = 32>>>`), or proto field options.
   The comment annotation style is consistent with PBJ's existing `pbj.java_package` convention.
   See Section 3 for the proposed annotations.

## 13. Implementation Phases

### Phase 1: Runtime Foundation
- Add `XDR_CODEC` to `FileType` enum
- Create `XdrCodec<T>` interface
- Create `XdrParserTools` class
- Create `XdrWriterTools` class
- Unit test the parser/writer tools directly

### Phase 2: Code Generator
- Create `XdrCodecGenerator` (main class)
- Create `XdrCodecParseMethodGenerator`
- Create `XdrCodecWriteMethodGenerator`
- Create `XdrCodecMeasureRecordMethodGenerator`

### Phase 3: Compiler Integration
- Add `XDR_CODEC_JAVA_FILE_SUFFIX` to `FileAndPackageNamesConfig`
- Update `LookupHelper.formatFileTypeName()` and `getPackage()` for `XDR_CODEC`
- Add `xdrCodecWriter` field to `FileSetWriter` record; update `create()` and `writeAllFiles()`
- Register `XdrCodecGenerator` in `Generator.GENERATORS`
- Update `ModelGenerator.generateCodecFields()` to add `public static final XdrCodec<T> XDR`

### Phase 4: Java Testing
- Integration test build: verify XDR codecs generate and compile
- Round-trip tests for all proto types
- Cross-format consistency tests (XDR round-trip matches protobuf round-trip)
- Performance benchmarks (JMH)

### Phase 5: Solidity XDR Decoder Generation
- Create Solidity code generator in PBJ compiler
- Generate decoders for fixed-size structs (constant offsets)
- Generate decoders for variable-length and union types
- Gas-cost benchmarks for generated Solidity decoders

### Phase 6: CLPR Integration
- Generate XDR codecs for all CLPR `.proto` schemas
- Integrate Java XDR codecs with Hiero node CLPR implementation
- Integrate Solidity XDR decoders with CLPR Service contract
- End-to-end testing: Java encode → Solidity decode round-trip