# XDR Codec Test Plan

This document describes the testing strategy for the XDR codec addition to PBJ, following
PBJ's established three-stage testing approach: unit tests, integration tests, and performance
benchmarks. The plan prioritizes **round-trip fidelity** and **binary compliance with RFC 4506**.

---

## 1. Unit Tests (`pbj-core`)

Unit tests validate the XDR runtime helpers and compiler-generated code in isolation.

### 1.1 XdrWriterTools Tests

**File:** `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrWriterToolsTest.java`

Follows the pattern of `ProtoWriterToolsTest`. Tests each static write method and its
corresponding size calculation method.

**Primitive write methods — verify RFC 4506 byte layout:**

| Test | Method | Assertion |
|------|--------|-----------|
| `writeInt_positive` | `writeInt(out, 42)` | Bytes are `00 00 00 2A` (big-endian, 4 bytes) |
| `writeInt_negative` | `writeInt(out, -1)` | Bytes are `FF FF FF FF` (two's complement) |
| `writeInt_zero` | `writeInt(out, 0)` | Bytes are `00 00 00 00` |
| `writeInt_minValue` | `writeInt(out, Integer.MIN_VALUE)` | Bytes are `80 00 00 00` |
| `writeInt_maxValue` | `writeInt(out, Integer.MAX_VALUE)` | Bytes are `7F FF FF FF` |
| `writeHyper_positive` | `writeHyper(out, 42L)` | 8 bytes big-endian |
| `writeHyper_negative` | `writeHyper(out, -1L)` | `FF FF FF FF FF FF FF FF` |
| `writeHyper_minValue` | `writeHyper(out, Long.MIN_VALUE)` | `80 00 00 00 00 00 00 00` |
| `writeHyper_maxValue` | `writeHyper(out, Long.MAX_VALUE)` | `7F FF FF FF FF FF FF FF` |
| `writeFloat_one` | `writeFloat(out, 1.0f)` | `3F 80 00 00` (IEEE 754 BE) |
| `writeFloat_negativeZero` | `writeFloat(out, -0.0f)` | `80 00 00 00` |
| `writeFloat_nan` | `writeFloat(out, Float.NaN)` | `7F C0 00 00` |
| `writeFloat_infinity` | `writeFloat(out, Float.POSITIVE_INFINITY)` | `7F 80 00 00` |
| `writeDouble_one` | `writeDouble(out, 1.0)` | `3F F0 00 00 00 00 00 00` (IEEE 754 BE) |
| `writeDouble_negativeZero` | `writeDouble(out, -0.0)` | `80 00 00 00 00 00 00 00` |
| `writeBool_true` | `writeBool(out, true)` | `00 00 00 01` (4-byte int, RFC 4506 §4.4) |
| `writeBool_false` | `writeBool(out, false)` | `00 00 00 00` |
| `writeEnum_zero` | `writeEnum(out, 0)` | `00 00 00 00` (4-byte signed int) |
| `writeEnum_negative` | `writeEnum(out, -1)` | `FF FF FF FF` |
| `writePresence_true` | `writePresence(out, true)` | `00 00 00 01` |
| `writePresence_false` | `writePresence(out, false)` | `00 00 00 00` |

**Variable-length write methods — verify RFC 4506 length prefix + padding:**

| Test | Input | Expected Bytes | RFC Reference |
|------|-------|----------------|---------------|
| `writeString_empty` | `""` | `00 00 00 00` (len=0, no data, no padding) | §4.11 |
| `writeString_len1` | `"A"` | `00 00 00 01 41 00 00 00` (len=1, 1 byte data, 3 pad) | §4.11 |
| `writeString_len2` | `"AB"` | `00 00 00 02 41 42 00 00` (len=2, 2 bytes data, 2 pad) | §4.11 |
| `writeString_len3` | `"ABC"` | `00 00 00 03 41 42 43 00` (len=3, 3 bytes data, 1 pad) | §4.11 |
| `writeString_len4` | `"ABCD"` | `00 00 00 04 41 42 43 44` (len=4, 4 bytes data, 0 pad) | §4.11 |
| `writeString_len5` | `"ABCDE"` | `00 00 00 05 41 42 43 44 45 00 00 00` (len=5, 3 pad) | §4.11 |
| `writeString_utf8` | `"\u00E9"` | Length is UTF-8 byte count (2), not char count (1) | §4.11 |
| `writeString_multibyte` | `"日本語"` | Length is 9 (3 chars × 3 UTF-8 bytes), pad to 12 | §4.11 |
| `writeOpaque_empty` | `Bytes.EMPTY` | `00 00 00 00` | §4.10 |
| `writeOpaque_len1` | 1 byte | `00 00 00 01 XX 00 00 00` (3 pad bytes, all zero) | §4.10 |
| `writeOpaque_len4` | 4 bytes | `00 00 00 04 XX XX XX XX` (0 pad) | §4.10 |
| `writeOpaque_len33` | 33 bytes | `00 00 00 21` + 33 data + 3 zero pad bytes | §4.10 |

**Padding verification — padding bytes must be zero (RFC 4506 §4.10):**

| Test | Assertion |
|------|-----------|
| `writePadding_0` | No bytes written for `paddingSize(0)` |
| `writePadding_1` | 3 zero bytes for `paddingSize(1)` |
| `writePadding_2` | 2 zero bytes for `paddingSize(2)` |
| `writePadding_3` | 1 zero byte for `paddingSize(3)` |
| `writePadding_4` | 0 bytes for `paddingSize(4)` |
| `writePadding_5` | 3 zero bytes for `paddingSize(5)` |

**Size calculation methods:**

| Test | Assertion |
|------|-----------|
| `sizeOfString_empty` | Returns 4 (length prefix only) |
| `sizeOfString_len1` | Returns 8 (4 + 1 + 3 pad) |
| `sizeOfString_len4` | Returns 8 (4 + 4 + 0 pad) |
| `sizeOfString_len5` | Returns 12 (4 + 5 + 3 pad) |
| `sizeOfOpaque_empty` | Returns 4 |
| `sizeOfOpaque_len33` | Returns 40 (4 + 33 + 3 pad) |
| `paddingSize_formula` | Verify `(4 - (len % 4)) % 4` for len 0..100 |

### 1.2 XdrParserTools Tests

**File:** `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrParserToolsTest.java`

Follows the pattern of `ProtoParserToolsTest`. For each type, write known bytes then parse
and verify the value.

**Primitive read methods — round-trip with known byte sequences:**

| Test | Input Bytes | Expected Value | RFC Reference |
|------|-------------|----------------|---------------|
| `readInt_positive` | `00 00 00 2A` | `42` | §4.1 |
| `readInt_negative` | `FF FF FF FF` | `-1` | §4.1 |
| `readInt_minValue` | `80 00 00 00` | `Integer.MIN_VALUE` | §4.1 |
| `readInt_maxValue` | `7F FF FF FF` | `Integer.MAX_VALUE` | §4.1 |
| `readHyper_positive` | 8 bytes BE | `42L` | §4.5 |
| `readHyper_minValue` | `80 00...00` | `Long.MIN_VALUE` | §4.5 |
| `readFloat_one` | `3F 80 00 00` | `1.0f` | §4.7 |
| `readFloat_nan` | `7F C0 00 00` | `Float.NaN` | §4.7 |
| `readDouble_one` | `3F F0 00 00 00 00 00 00` | `1.0` | §4.8 |
| `readBool_true` | `00 00 00 01` | `true` | §4.4 |
| `readBool_false` | `00 00 00 00` | `false` | §4.4 |
| `readBool_invalid` | `00 00 00 02` | Throw `ParseException` | §4.4 |
| `readEnum` | `00 00 00 03` | `3` | §4.3 |
| `readPresence_true` | `00 00 00 01` | `true` | §4.19 |
| `readPresence_false` | `00 00 00 00` | `false` | §4.19 |

**Variable-length read methods — verify padding is consumed correctly:**

| Test | Input Bytes | Expected Value |
|------|-------------|----------------|
| `readString_empty` | `00 00 00 00` | `""` |
| `readString_len1` | `00 00 00 01 41 00 00 00` | `"A"` (3 padding bytes consumed) |
| `readString_len4` | `00 00 00 04 41 42 43 44` | `"ABCD"` (0 padding) |
| `readString_len5` | `00 00 00 05 41 42 43 44 45 00 00 00` | `"ABCDE"` (3 padding consumed) |
| `readString_utf8` | len=2 + `C3 A9` + 2 pad | `"\u00E9"` |
| `readString_exceedsMaxSize` | len > maxSize | Throw `ParseException` |
| `readOpaque_empty` | `00 00 00 00` | `Bytes.EMPTY` |
| `readOpaque_len1` | `00 00 00 01 FF 00 00 00` | 1-byte `Bytes` (padding consumed) |
| `readOpaque_len33` | len=33 + 33 bytes + 3 pad | 33-byte `Bytes` |
| `readOpaque_exceedsMaxSize` | len > maxSize | Throw `ParseException` |

**Position advancement — verify the parser advances past padding:**

| Test | Assertion |
|------|-----------|
| `readString_positionAdvancedPastPadding` | After reading `"A"` (1 byte), position advanced by 8 (4 len + 1 data + 3 pad) |
| `readOpaque_positionAdvancedPastPadding` | After reading 5-byte opaque, position advanced by 12 (4 len + 5 data + 3 pad) |
| `skipPadding_advancesCorrectly` | `skipPadding(in, 1)` advances 3 bytes; `skipPadding(in, 4)` advances 0 |

### 1.3 Writer/Parser Round-Trip Tests

**File:** `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrRoundTripTest.java`

For every XDR type: write with `XdrWriterTools`, then parse with `XdrParserTools` from the
same buffer. Verify the parsed value equals the written value.

**Parameterized round-trip tests:**

```java
@ParameterizedTest
@MethodSource("intValues")
void roundTrip_int(int value) {
    XdrWriterTools.writeInt(out, value);
    out.flip();
    assertEquals(value, XdrParserTools.readInt(in));
}

static Stream<Integer> intValues() {
    return Stream.of(0, 1, -1, 42, -42, Integer.MIN_VALUE, Integer.MAX_VALUE);
}
```

Repeat for: `hyper`, `float`, `double`, `bool`, `enum`, `string`, `opaque`, `presence`.

**String round-trip with all padding cases:**
- Lengths 0 through 8 (covers 0, 1, 2, 3 padding bytes twice)
- Multi-byte UTF-8 strings
- Strings with embedded nulls

**Opaque round-trip with all padding cases:**
- Lengths 0 through 8
- Binary data with all byte values (0x00-0xFF)

### 1.4 RFC 4506 Binary Compliance Tests

**File:** `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrRfcComplianceTest.java`

These tests verify exact byte-level compliance with RFC 4506 by comparing against
hand-computed reference encodings from the RFC examples and specification.

**RFC 4506 Section 4 reference encodings:**

| Test | XDR Type | Value | Expected Bytes | RFC Section |
|------|----------|-------|----------------|-------------|
| `rfc_integer_example` | int | `262144` | `00 04 00 00` | §4.1 |
| `rfc_bool_true` | bool | `TRUE` | `00 00 00 01` | §4.4 |
| `rfc_bool_false` | bool | `FALSE` | `00 00 00 00` | §4.4 |
| `rfc_hyper_example` | hyper | `1099511627776` | `00 00 01 00 00 00 00 00` | §4.5 |
| `rfc_float_example` | float | `0.0f` | `00 00 00 00` | §4.6 |
| `rfc_double_zero` | double | `0.0` | `00 00 00 00 00 00 00 00` | §4.7 |

**RFC 4506 Section 4.9 — Fixed-length opaque:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_fixedOpaque_aligned` | `opaque[4] = {0x01,0x02,0x03,0x04}` | `01 02 03 04` (no padding) |
| `rfc_fixedOpaque_unaligned` | `opaque[5] = {0x01..0x05}` | `01 02 03 04 05 00 00 00` (3 zero pad) |
| `rfc_fixedOpaque_1byte` | `opaque[1] = {0xFF}` | `FF 00 00 00` (3 zero pad) |

**RFC 4506 Section 4.10 — Variable-length opaque:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_varOpaque_empty` | `opaque<> = {}` | `00 00 00 00` |
| `rfc_varOpaque_5bytes` | `opaque<> = {0x01..0x05}` | `00 00 00 05 01 02 03 04 05 00 00 00` |

**RFC 4506 Section 4.11 — String:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_string_sillyprog` | `string = "sillyprog"` | `00 00 00 09` + `sillyprog` + `000` (3 pad) |

**RFC 4506 Section 4.13 — Variable-length array:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_varArray_3ints` | `int<5> = {1, 2, 3}` | `00 00 00 03 00 00 00 01 00 00 00 02 00 00 00 03` |
| `rfc_varArray_empty` | `int<5> = {}` | `00 00 00 00` |

**RFC 4506 Section 4.14 — Struct:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_struct_sequential` | struct{int a=1; int b=2} | `00 00 00 01 00 00 00 02` (fields in order) |

**RFC 4506 Section 4.15 — Discriminated union:**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_union_arm0` | union switch(0) { case 0: int=42 } | `00 00 00 00 00 00 00 2A` |
| `rfc_union_arm1` | union switch(1) { case 1: string="hi" } | `00 00 00 01 00 00 00 02 68 69 00 00` |

**RFC 4506 Section 4.19 — Optional-data (pointer):**

| Test | Description | Expected Bytes |
|------|-------------|----------------|
| `rfc_optional_present` | *int = 42 (present) | `00 00 00 01 00 00 00 2A` |
| `rfc_optional_absent` | *int (absent) | `00 00 00 00` |

**4-byte alignment invariant:**

```java
@ParameterizedTest
@ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 33, 100, 255})
void alignment_totalBytesMultipleOf4(int dataLength) {
    XdrWriterTools.writeOpaque(out, Bytes.wrap(new byte[dataLength]));
    assertEquals(0, out.position() % 4, "XDR output must be 4-byte aligned");
}
```

**Padding bytes are zero:**

```java
@ParameterizedTest
@ValueSource(ints = {1, 2, 3, 5, 6, 7})
void padding_bytesAreZero(int dataLength) {
    XdrWriterTools.writeOpaque(out, Bytes.wrap(new byte[dataLength]));
    byte[] bytes = toByteArray(out);
    int paddingStart = 4 + dataLength; // after length prefix + data
    for (int i = paddingStart; i < bytes.length; i++) {
        assertEquals(0, bytes[i], "Padding byte at offset " + i + " must be zero");
    }
}
```

### 1.5 XdrCodec Interface Tests

**File:** `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrCodecTest.java`

- Verify `XdrCodec<T>` extends `Codec<T>`
- Verify default methods from `Codec<T>` work (convenience `parse()` overloads, `toBytes()`)

---

## 2. Integration Tests (`pbj-integration-tests`)

Integration tests validate generated XDR codecs against real protobuf schemas and cross-codec
consistency. These go in `pbj-integration-tests/src/test/java/com/hedera/pbj/integration/test/`.

### 2.1 Generated Test Extension

The `TestGenerator` should be extended to generate XDR round-trip tests alongside existing
protobuf and JSON tests. For each message, the generated test class adds:

**XDR round-trip test (parameterized over all ARGUMENTS):**

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void testXdrRoundTrip(NoToStringWrapper<MessageType> wrapper) {
    final MessageType modelObj = wrapper.getValue();
    // Write XDR
    final BufferedData dataBuffer = getThreadLocalDataBuffer();
    MessageType.XDR.write(modelObj, dataBuffer);
    dataBuffer.flip();
    // Parse XDR
    final MessageType parsed = MessageType.XDR.parse(dataBuffer);
    // Verify round-trip
    assertEquals(modelObj, parsed);
}
```

**XDR measure consistency test:**

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void testXdrMeasureConsistency(NoToStringWrapper<MessageType> wrapper) {
    final MessageType modelObj = wrapper.getValue();
    final int measuredSize = MessageType.XDR.measureRecord(modelObj);
    final BufferedData dataBuffer = getThreadLocalDataBuffer();
    MessageType.XDR.write(modelObj, dataBuffer);
    assertEquals(measuredSize, dataBuffer.position());
}
```

**XDR fastEquals test:**

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void testXdrFastEquals(NoToStringWrapper<MessageType> wrapper) {
    final MessageType modelObj = wrapper.getValue();
    final Bytes xdrBytes = MessageType.XDR.toBytes(modelObj);
    assertTrue(MessageType.XDR.fastEquals(modelObj, xdrBytes.toReadableSequentialData()));
}
```

### 2.2 Cross-Codec Consistency Tests

**File:** `XdrCrossCodecConsistencyTest.java`

The most critical integration test: verify that an object serialized to XDR and parsed back
produces the same object as one serialized to protobuf and parsed back.

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void xdrRoundTrip_equalsProtobufRoundTrip(NoToStringWrapper<Everything> wrapper) {
    final Everything original = wrapper.getValue();

    // Protobuf round-trip
    final Bytes protoBytes = Everything.PROTOBUF.toBytes(original);
    final Everything fromProto = Everything.PROTOBUF.parse(protoBytes.toReadableSequentialData());

    // XDR round-trip
    final Bytes xdrBytes = Everything.XDR.toBytes(original);
    final Everything fromXdr = Everything.XDR.parse(xdrBytes.toReadableSequentialData());

    // Both round-trips must produce equal objects
    assertEquals(fromProto, fromXdr);
    assertEquals(original, fromXdr);
}
```

Run this for: `Everything`, `InnerEverything`, `TimestampTest`, `Hasheval`,
`MessageWithBytesAndString`, `MessageWithMaps`, and all other test message types.

### 2.3 XDR RFC Binary Compliance — Full Message Tests

**File:** `XdrBinaryComplianceTest.java`

Hand-compute expected XDR byte sequences for known message instances and verify the generated
codec produces exactly those bytes. This is the strongest guarantee of RFC compliance.

**Test: Simple two-field message**

```java
@Test
void timestampTest_knownEncoding() {
    // TimestampTest: int64 seconds = 1; int32 nanos = 2;
    TimestampTest msg = new TimestampTest(1234L, 567);
    Bytes xdr = TimestampTest.XDR.toBytes(msg);
    // Field 1 (seconds, int64): presence=1, value=1234
    // Field 2 (nanos, int32): presence=1, value=567
    byte[] expected = new byte[] {
        0x00, 0x00, 0x00, 0x01,                         // presence=1
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, (byte)0xD2, // hyper 1234
        0x00, 0x00, 0x00, 0x01,                         // presence=1
        0x00, 0x00, 0x02, 0x37                          // int 567
    };
    assertArrayEquals(expected, xdr.toByteArray());
}
```

**Test: Message with default-valued fields (presence=0)**

```java
@Test
void defaultFields_encodedWithPresenceZero() {
    TimestampTest msg = new TimestampTest(0L, 0);
    Bytes xdr = TimestampTest.XDR.toBytes(msg);
    byte[] expected = new byte[] {
        0x00, 0x00, 0x00, 0x00, // presence=0 for seconds (default)
        0x00, 0x00, 0x00, 0x00  // presence=0 for nanos (default)
    };
    assertArrayEquals(expected, xdr.toByteArray());
}
```

**Test: Message with string + padding**

```java
@Test
void stringField_correctPadding() {
    MessageWithString msg = MessageWithString.newBuilder().aString("hello").build();
    Bytes xdr = MessageWithString.XDR.toBytes(msg);
    // "hello" = 5 UTF-8 bytes → 4 len + 5 data + 3 pad = 12 bytes
    // Total: 4 presence + 12 string = 16 bytes
    assertEquals(16, xdr.length());
    // Verify padding bytes are zero
    byte[] bytes = xdr.toByteArray();
    assertEquals(0, bytes[13]); // pad byte 1
    assertEquals(0, bytes[14]); // pad byte 2
    assertEquals(0, bytes[15]); // pad byte 3
}
```

**Test: Repeated field encoding**

```java
@Test
void repeatedField_xdrVariableLengthArray() {
    // repeated int32 tags = [1, 2, 3]
    // XDR: 4-byte count + N × 4-byte int = 4 + 12 = 16 bytes
    byte[] expected = new byte[] {
        0x00, 0x00, 0x00, 0x03, // count=3
        0x00, 0x00, 0x00, 0x01, // 1
        0x00, 0x00, 0x00, 0x02, // 2
        0x00, 0x00, 0x00, 0x03  // 3
    };
    // Assert against actual XDR output of the repeated field portion
}
```

**Test: OneOf field encoding**

```java
@Test
void oneOfField_discriminantIsFieldNumber() {
    // oneof with field_number=4 active, value is string "hi"
    // XDR: 4-byte discriminant (field number) + arm encoding
    // discriminant=4, string "hi": len=2 + "hi" + 2 pad
    byte[] expected = new byte[] {
        0x00, 0x00, 0x00, 0x04, // discriminant = field number 4
        0x00, 0x00, 0x00, 0x02, // string length = 2
        0x68, 0x69, 0x00, 0x00  // "hi" + 2 pad bytes
    };
}
```

**Test: Empty repeated and map fields**

```java
@Test
void emptyRepeatedField_zeroCount() {
    // Empty repeated: just 4-byte count = 0
    byte[] expected = new byte[] { 0x00, 0x00, 0x00, 0x00 };
}

@Test
void emptyMapField_zeroCount() {
    // Empty map: just 4-byte count = 0
    byte[] expected = new byte[] { 0x00, 0x00, 0x00, 0x00 };
}
```

**Test: Map field — sorted key order**

```java
@Test
void mapField_keysSortedInXdr() {
    // map<string, int32> with keys "b"=2, "a"=1
    // XDR must emit "a" before "b" (PBJ deterministic key ordering)
}
```

**Test: Nested message — no length prefix**

```java
@Test
void nestedMessage_noLengthPrefix() {
    // XDR structs have no length prefix — fields are concatenated inline
    // Verify the nested message bytes follow the presence flag directly
    // with no intervening length varint
}
```

### 2.4 XDR 4-Byte Alignment Invariant Tests

**File:** `XdrAlignmentTest.java`

Verify the fundamental XDR invariant: every field starts and ends on a 4-byte boundary.

```java
@ParameterizedTest
@MethodSource("allTestMessages")
void totalEncodedSize_isMultipleOf4(Object message) {
    Bytes xdr = codec.toBytes(message);
    assertEquals(0, xdr.length() % 4,
        "XDR encoding of " + message.getClass().getSimpleName()
        + " must be 4-byte aligned, got " + xdr.length() + " bytes");
}
```

Run for all message types in the integration test suite (Everything, InnerEverything,
TimestampTest, Hasheval, MessageWithMaps, MessageWithString, etc.).

### 2.5 XDR Determinism Tests

**File:** `XdrDeterminismTest.java`

Verify the core XDR property: same object always produces identical bytes.

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void sameObject_producesIdenticalBytes(NoToStringWrapper<Everything> wrapper) {
    final Everything obj = wrapper.getValue();
    final Bytes xdr1 = Everything.XDR.toBytes(obj);
    final Bytes xdr2 = Everything.XDR.toBytes(obj);
    assertEquals(xdr1, xdr2, "Determinism: same object must produce identical XDR bytes");
}

@ParameterizedTest
@MethodSource("createModelTestArguments")
void equalObjects_produceIdenticalBytes(NoToStringWrapper<Everything> wrapper) {
    final Everything obj1 = wrapper.getValue();
    // Reconstruct via protobuf round-trip (different object identity, same value)
    final Bytes protoBytes = Everything.PROTOBUF.toBytes(obj1);
    final Everything obj2 = Everything.PROTOBUF.parse(protoBytes.toReadableSequentialData());
    assertEquals(obj1, obj2); // sanity check
    final Bytes xdr1 = Everything.XDR.toBytes(obj1);
    final Bytes xdr2 = Everything.XDR.toBytes(obj2);
    assertEquals(xdr1, xdr2, "Canonical: equal objects must produce identical XDR bytes");
}
```

### 2.6 XDR Size Comparison Tests

**File:** `XdrSizeComparisonTest.java`

Measure XDR size vs protobuf size for representative messages. These are not pass/fail tests
but produce a report for analysis. Verify expected relative sizes from the design doc.

```java
@ParameterizedTest
@MethodSource("createModelTestArguments")
void recordSizes(NoToStringWrapper<Everything> wrapper) {
    final Everything obj = wrapper.getValue();
    int protoSize = Everything.PROTOBUF.measureRecord(obj);
    int xdrSize = Everything.XDR.measureRecord(obj);
    // Log for analysis
    System.out.printf("Proto: %d bytes, XDR: %d bytes, ratio: %.2fx%n",
        protoSize, xdrSize, (double) xdrSize / protoSize);
    // XDR should always be >= protobuf (no varints, 4-byte alignment)
    assertTrue(xdrSize >= protoSize || protoSize == 0,
        "XDR size should be >= protobuf size for non-empty messages");
}
```

### 2.7 XDR Unknown Fields Handling Tests

**File:** `XdrUnknownFieldsTest.java`

Verify XDR behavior with unknown fields (per design doc Section 5.3).

```java
@Test
void unknownFields_notWrittenToXdr() {
    // Parse a protobuf message with unknown fields
    MessageWithBytesAndString full = new MessageWithBytesAndString(testBytes, testString);
    Bytes protoBytes = MessageWithBytesAndString.PROTOBUF.toBytes(full);
    // Parse as subset message (captures unknown fields)
    MessageWithBytes subset = MessageWithBytes.PROTOBUF.parse(
        protoBytes.toReadableSequentialData(), false, true, 16, Codec.DEFAULT_MAX_SIZE);
    assertTrue(subset.getUnknownFields().size() > 0);
    // Write to XDR — unknown fields must NOT appear
    Bytes xdrBytes = MessageWithBytes.XDR.toBytes(subset);
    MessageWithBytes reparsed = MessageWithBytes.XDR.parse(xdrBytes.toReadableSequentialData());
    assertEquals(0, reparsed.getUnknownFields().size());
}
```

### 2.8 XDR Safety Limits Tests

**File:** `XdrSafetyLimitsTest.java`

Verify maxDepth and maxSize enforcement in XDR parsing.

**Max depth:**

```java
@Test
void maxDepth_enforcedForNestedMessages() {
    // Build a deeply nested message
    MessageWithMessage msg = MessageWithMessage.newBuilder()
        .message(MessageWithMessage.newBuilder()
            .message(MessageWithMessage.newBuilder().build())
            .build())
        .build();
    Bytes xdr = MessageWithMessage.XDR.toBytes(msg);
    // Parse with depth limit 0 — should fail
    assertThrows(ParseException.class,
        () -> MessageWithMessage.XDR.parse(xdr.toReadableSequentialData(), false, false, 0, Codec.DEFAULT_MAX_SIZE));
    // Parse with depth limit 3 — should succeed
    assertDoesNotThrow(
        () -> MessageWithMessage.XDR.parse(xdr.toReadableSequentialData(), false, false, 3, Codec.DEFAULT_MAX_SIZE));
}
```

**Max size:**

```java
@Test
void maxSize_enforcedForStrings() {
    // Build message with a string longer than maxSize
    String longString = "x".repeat(1000);
    MessageWithString msg = MessageWithString.newBuilder().aString(longString).build();
    Bytes xdr = MessageWithString.XDR.toBytes(msg);
    // Parse with maxSize=100 — should fail
    assertThrows(ParseException.class,
        () -> MessageWithString.XDR.parse(xdr.toReadableSequentialData(), false, false,
            Codec.DEFAULT_MAX_DEPTH, 100));
}
```

### 2.9 XDR Malformed Data Tests

**File:** `XdrMalformedDataTest.java`

Verify graceful handling of corrupted XDR data. Follows the pattern of `MalformedMessageTest`
and `TruncatedDataTests`.

**Truncated data:**

| Test | Description |
|------|-------------|
| `truncated_midPresenceFlag` | Input ends after 2 bytes of a 4-byte presence flag |
| `truncated_midIntValue` | Presence=1 but only 2 bytes of int value remain |
| `truncated_midStringLength` | Input ends after 2 bytes of string length prefix |
| `truncated_midStringData` | String length says 10 but only 5 data bytes available |
| `truncated_midPadding` | String data complete but padding bytes missing |

**Invalid data:**

| Test | Description |
|------|-------------|
| `invalid_boolNotZeroOrOne` | Presence flag or bool value is 2 or 0xFFFFFFFF |
| `invalid_negativeStringLength` | String length prefix is negative (0x80000000) |
| `invalid_stringLengthExceedsRemaining` | Length says more bytes than available |
| `invalid_negativeArrayCount` | Repeated field count is negative |
| `invalid_arrayCountExceedsMaxSize` | Count > maxSize |

All must throw `ParseException` without crashing, hanging, or allocating unbounded memory.

### 2.10 XDR Fuzz Tests

**File:** `XdrFuzzTest.java`

Follows the pattern of `SampleFuzzTest`. Uses the existing fuzz framework with the XDR codec.

```java
@Test
@Tag(FUZZ_TEST_TAG)
void fuzzXdrCodec() {
    assumeFalse(getClass().desiredAssertionStatus());
    final var results = new ArrayList<FuzzTestResult>();
    // For each message type with XDR codec
    for (var arg : EverythingTest.ARGUMENTS) {
        results.add(FuzzTest.fuzzTest(arg, Everything.XDR, random));
    }
    for (var arg : TimestampTestTest.ARGUMENTS) {
        results.add(FuzzTest.fuzzTest(arg, TimestampTest.XDR, random));
    }
    // etc. for all message types
    double meanDeserFailed = results.stream()
        .mapToDouble(r -> r.deserializationFailedRate())
        .average().orElse(0);
    assertTrue(meanDeserFailed >= DESERIALIZATION_FAILED_MEAN_THRESHOLD,
        "Mean deserialization failure rate " + meanDeserFailed + " below threshold");
}
```

**XDR-specific fuzz considerations:**
- XDR has no tags or wire types, so random byte mutations are more likely to produce
  "valid-looking" data that parses without error (compared to protobuf where random tags are
  likely invalid). The deserialization failure threshold may need to be lower than protobuf's.
- Verify that fuzzed XDR data never causes: OOM, infinite loops, stack overflow, or
  `ArrayIndexOutOfBoundsException`.

### 2.11 Schema Coverage

XDR codecs should be generated and tested for all existing integration test proto schemas:

| Proto File | Key Coverage |
|------------|-------------|
| `everything.proto` | All 15 scalar types, nested messages, repeated, maps, oneOf, enums, boxed types |
| `comparable.proto` | Comparable messages with partial fields |
| `timestampTest.proto` | Simple 2-field message (baseline) |
| `bytesAndString.proto` | Bytes and string fields |
| `map.proto` | Multiple map key/value type combinations |
| `oneof.proto` | OneOf with reversed field number order |
| `extendedUtf8StingTest.proto` | Multi-byte UTF-8 strings (Arabic, Hindi, Latin) |
| `enum_unrecognized.proto` | Unrecognized enum value handling |
| `nestedTest.proto` | Multi-level nested messages |

---

## 3. Performance Benchmarks (`pbj-integration-tests/src/jmh`)

JMH benchmarks measure XDR codec performance and compare against protobuf. Follow the
patterns established in `ProtobufObjectBench`, `JsonBench`, and `SampleBlockBench`.

### 3.1 XDR Parse/Write Throughput Benchmark

**File:** `XdrObjectBench.java`

Mirrors `ProtobufObjectBench` structure. Measures parse and write throughput across buffer
types, comparing XDR vs protobuf vs JSON.

**State classes (nested, parameterized):**

```java
@State(Scope.Thread)
public static class EverythingBench {
    BufferedData xdrDataBuffer;
    BufferedData xdrDataBufferDirect;
    byte[] xdrByteArray;
    Bytes xdrBytes;

    @Setup
    public void setup() {
        Everything obj = EverythingTestData.EVERYTHING;
        xdrBytes = Everything.XDR.toBytes(obj);
        xdrByteArray = xdrBytes.toByteArray();
        xdrDataBuffer = BufferedData.allocate(xdrByteArray.length);
        // etc.
    }
}
```

**Benchmark methods — parse:**

| Method | Description |
|--------|-------------|
| `parseXdrByteArray` | XDR parse from `BufferedData` wrapping byte[] |
| `parseXdrByteBuffer` | XDR parse from `BufferedData` wrapping ByteBuffer |
| `parseXdrByteBufferDirect` | XDR parse from `BufferedData` wrapping direct ByteBuffer |
| `parseXdrInputStream` | XDR parse from `ReadableStreamingData` |
| `parsePbjByteArray` | (baseline) Protobuf parse from byte[] |

**Benchmark methods — write:**

| Method | Description |
|--------|-------------|
| `writeXdrByteBuffer` | XDR write to `BufferedData` |
| `writeXdrByteDirect` | XDR write to off-heap `BufferedData` |
| `writeXdrOutputStream` | XDR write to `WritableStreamingData` |
| `writePbjByteBuffer` | (baseline) Protobuf write to `BufferedData` |

**JMH annotations:**

```java
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@OperationsPerInvocation(1000)
```

**Message types:** `Everything`, `TimestampTest`, `AccountDetails` (same as ProtobufObjectBench).

### 3.2 XDR Measure Benchmark

**File:** `XdrMeasureBench.java`

Measures the cost of `measureRecord()` for XDR compared to protobuf. This validates the
design decision to compute XDR size on-the-fly rather than caching it.

```java
@Benchmark
public int measureXdr(EverythingBench state) {
    return Everything.XDR.measureRecord(state.pbjObj);
}

@Benchmark
public int measureProtobuf(EverythingBench state) {
    return Everything.PROTOBUF.measureRecord(state.pbjObj);
}
```

### 3.3 XDR Size Overhead Benchmark

**File:** `XdrSizeBench.java`

Reports XDR vs protobuf encoded size for representative messages. Not a throughput test —
measures message size only.

```java
@Benchmark
public int[] sizeComparison(EverythingBench state) {
    return new int[] {
        Everything.PROTOBUF.measureRecord(state.pbjObj),
        Everything.XDR.measureRecord(state.pbjObj)
    };
}
```

### 3.4 XDR Hash Benchmark

**File:** Extend existing `HashBench.java`

Add XDR-based hashing benchmark alongside existing SHA256 and field-wise hashing:

```java
@Benchmark
public byte[] hashBenchXdrSHA256(BenchState state) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    Bytes xdr = Hasheval.XDR.toBytes(state.hasheval);
    xdr.writeTo(digest);
    return digest.digest();
}
```

This measures the end-to-end cost of deterministic hashing via XDR serialization, which is
the primary use case for CLPR running hash chains.

### 3.5 XDR Real-World Block Benchmark

**File:** Extend existing `SampleBlockBench.java` or new `XdrBlockBench.java`

If block-related protos get XDR codecs, benchmark parse/write of real block data:

```java
@Benchmark
public Block parseXdrBlock(BlockState state) {
    return Block.XDR.parse(state.xdrBlockData.toReadableSequentialData());
}

@Benchmark
public Bytes writeXdrBlock(BlockState state) {
    return Block.XDR.toBytes(state.pbjBlock);
}
```

---

## 4. Test Execution

### Running Tests

```bash
# Unit tests (runtime + compiler)
cd pbj-core
./gradlew pbj-runtime:test --tests "com.hedera.pbj.runtime.Xdr*"

# Integration tests (all XDR tests)
cd pbj-integration-tests
./gradlew test --tests "*Xdr*"

# Integration tests (generated round-trip tests — includes XDR)
cd pbj-integration-tests
./gradlew test

# Fuzz tests (requires assertions disabled)
cd pbj-integration-tests
./gradlew fuzzTest

# Performance benchmarks
cd pbj-integration-tests
./gradlew jmh -Pinclude=".*Xdr.*"

# Full quality gate (formatting + all quality checks)
cd pbj-core
./gradlew qualityGate
```

### Test Priority Order

1. **XdrWriterTools / XdrParserTools unit tests** — foundational correctness
2. **XdrRfcComplianceTest** — RFC 4506 binary compliance
3. **Generated round-trip tests** — every message type round-trips through XDR
4. **XdrCrossCodecConsistencyTest** — XDR and protobuf produce equivalent objects
5. **XdrAlignmentTest + XdrDeterminismTest** — core XDR properties
6. **XdrMalformedDataTest + XdrSafetyLimitsTest** — robustness
7. **XdrFuzzTest** — random mutation resilience
8. **JMH benchmarks** — performance characterization

### Success Criteria

| Criterion | Threshold |
|-----------|-----------|
| Round-trip fidelity | 100% — `parse(write(obj)) == obj` for all valid objects |
| RFC 4506 compliance | 100% — all reference encodings match byte-for-byte |
| 4-byte alignment | 100% — every encoded message length is a multiple of 4 |
| Padding bytes zero | 100% — all padding bytes are `0x00` |
| Determinism | 100% — equal objects produce identical bytes |
| Cross-codec consistency | 100% — XDR round-trip equals protobuf round-trip |
| Malformed data handling | 100% — all invalid inputs throw `ParseException` |
| Fuzz deserialization failure rate | >= 95% for each message type |
| No OOM/hang/crash on fuzz | 0 non-ParseException failures |
