# XDR Codec — Project Plan

Step-by-step implementation plan for adding XDR codec generation (Java and Solidity) to PBJ.
Each task is designed to be a single working session with Claude. Tasks within a phase are
sequential unless noted otherwise.

**Reference documents:**
- [xdr-design.md](xdr-design.md) — encoding format, type mapping, architecture
- [xdr-use-case.md](xdr-use-case.md) — CLPR requirements, format comparison, gas analysis
- [xdr-test-plan.md](xdr-test-plan.md) — detailed test specifications

---

## Phase 1: Runtime Foundation

Build the runtime classes that generated XDR codecs will depend on. All in `pbj-core/pbj-runtime`.

### 1.1 Create `XdrCodec<T>` interface

**Files to create:**
- `pbj-runtime/src/main/java/com/hedera/pbj/runtime/XdrCodec.java`

**What to do:**
- Create `public interface XdrCodec<T> extends Codec<T>` in package `com.hedera.pbj.runtime`
- Empty body for now (follows `JsonCodec<T>` pattern — allows XDR-specific methods later)
- Add to `module-info.java` exports if needed (already exports `com.hedera.pbj.runtime`)

**Verify:** Compiles with `cd pbj-core && ./gradlew pbj-runtime:compileJava`

### 1.2 Create `XdrWriterTools`

**Files to create:**
- `pbj-runtime/src/main/java/com/hedera/pbj/runtime/XdrWriterTools.java`

**What to do:**
- Create `public final class XdrWriterTools` in `com.hedera.pbj.runtime`
- Implement all static write methods per design doc Section 7.2:
  - `writeInt(WritableSequentialData, int)` — `out.writeInt(value)` (big-endian default)
  - `writeHyper(WritableSequentialData, long)` — `out.writeLong(value)`
  - `writeFloat(WritableSequentialData, float)` — `out.writeFloat(value)`
  - `writeDouble(WritableSequentialData, double)` — `out.writeDouble(value)`
  - `writeBool(WritableSequentialData, boolean)` — `out.writeInt(value ? 1 : 0)`
  - `writeString(WritableSequentialData, String)` — write length (uint), UTF-8 bytes, zero padding
  - `writeOpaque(WritableSequentialData, Bytes)` — write length (uint), raw bytes, zero padding
  - `writeEnum(WritableSequentialData, int)` — `out.writeInt(protoOrdinal)`
  - `writePresence(WritableSequentialData, boolean)` — `out.writeInt(present ? 1 : 0)`
  - `writePadding(WritableSequentialData, int dataLength)` — write `(4 - (len % 4)) % 4` zero bytes
- Implement size measurement methods:
  - `sizeOfString(String)` — `4 + utf8ByteLength + paddingSize(utf8ByteLength)`
  - `sizeOfOpaque(Bytes)` — `4 + data.length() + paddingSize(data.length())`
  - `sizeOfOpaque(int dataLength)` — `4 + dataLength + paddingSize(dataLength)`
  - `paddingSize(int dataLength)` — `(4 - (dataLength % 4)) % 4`
- Reference: `ProtoWriterTools.java` for style conventions

**Verify:** Compiles with `cd pbj-core && ./gradlew pbj-runtime:compileJava`

### 1.3 Create `XdrParserTools`

**Files to create:**
- `pbj-runtime/src/main/java/com/hedera/pbj/runtime/XdrParserTools.java`

**What to do:**
- Create `public final class XdrParserTools` in `com.hedera.pbj.runtime`
- Implement all static read methods per design doc Section 7.2:
  - `readInt(ReadableSequentialData)` — `in.readInt()` (big-endian default)
  - `readHyper(ReadableSequentialData)` — `in.readLong()`
  - `readFloat(ReadableSequentialData)` — `in.readFloat()`
  - `readDouble(ReadableSequentialData)` — `in.readDouble()`
  - `readBool(ReadableSequentialData)` — read 4-byte int, validate 0 or 1, return boolean
  - `readString(ReadableSequentialData, int maxSize)` — read length, validate vs maxSize, read UTF-8 bytes, skip padding
  - `readOpaque(ReadableSequentialData, int maxSize)` — read length, validate vs maxSize, read bytes, skip padding
  - `readEnum(ReadableSequentialData)` — `in.readInt()` (returns raw int)
  - `readPresence(ReadableSequentialData)` — read 4-byte int, validate 0 or 1, return boolean
  - `skipPadding(ReadableSequentialData, int dataLength)` — skip `(4 - (len % 4)) % 4` bytes
- Throw `ParseException` for: invalid bool values (!= 0 or 1), negative lengths, length > maxSize
- Reference: `ProtoParserTools.java` for style conventions

**Verify:** Compiles with `cd pbj-core && ./gradlew pbj-runtime:compileJava`

### 1.4 Unit test `XdrWriterTools` and `XdrParserTools`

**Files to create:**
- `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrWriterToolsTest.java`
- `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrParserToolsTest.java`
- `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrRoundTripTest.java`
- `pbj-runtime/src/test/java/com/hedera/pbj/runtime/XdrRfcComplianceTest.java`

**What to do:**
- Implement all tests from test plan Sections 1.1-1.4:
  - Writer: verify exact byte output for every primitive and variable-length type
  - Parser: verify correct value from known byte sequences
  - Round-trip: write then parse for all types with parameterized edge-case values
  - RFC compliance: hand-computed byte sequences from RFC 4506 examples
- Key invariants to test:
  - All output lengths are multiples of 4
  - Padding bytes are always zero
  - String lengths are UTF-8 byte counts, not char counts
  - Bool/presence values reject anything other than 0 or 1

**Verify:** `cd pbj-core && ./gradlew pbj-runtime:test --tests "com.hedera.pbj.runtime.Xdr*"`

### 1.5 Add `XDR_CODEC` to `FileType` enum

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/FileType.java`

**What to do:**
- Add `XDR_CODEC` enum value with appropriate description (follow existing pattern)

**Verify:** `cd pbj-core && ./gradlew pbj-compiler:compileJava`

---

## Phase 2: Code Generator — Scaffolding

Wire up the XDR code generator in the compiler pipeline. No code generation logic yet —
just the empty generator registered and producing a compilable empty codec class.

### 2.1 Update `FileAndPackageNamesConfig` and `LookupHelper`

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/FileAndPackageNamesConfig.java`
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/LookupHelper.java`

**What to do:**
- Add `XDR_CODEC_JAVA_FILE_SUFFIX = "XdrCodec"` constant to `FileAndPackageNamesConfig`
- Update `LookupHelper.formatFileTypeName()` to handle `XDR_CODEC` → append `"XdrCodec"`
- Update `LookupHelper.getPackage()` to route `XDR_CODEC` to `.codec` subpackage

**Verify:** `cd pbj-core && ./gradlew pbj-compiler:compileJava`

### 2.2 Update `FileSetWriter`

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/FileSetWriter.java`

**What to do:**
- Add `JavaFileWriter xdrCodecWriter` field to the record
- Update `create()` method to instantiate the writer for `FileType.XDR_CODEC`
- Update `writeAllFiles()` to include `xdrCodecWriter`

**Verify:** `cd pbj-core && ./gradlew pbj-compiler:compileJava`

### 2.3 Create `XdrCodecGenerator` (skeleton)

**Files to create:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecGenerator.java`

**What to do:**
- Create package `com.hedera.pbj.compiler.impl.generators.xdr`
- Create `XdrCodecGenerator implements Generator`
- Generate a minimal compilable XDR codec class that:
  - Implements `XdrCodec<ModelClass>`
  - Has stub methods (`parse` returns default, `write` is no-op, `measureRecord` returns 0, etc.)
  - Follows the pattern of `CodecGenerator` for class structure, imports, and Javadoc

**Verify:** `cd pbj-core && ./gradlew pbj-compiler:compileJava`

### 2.4 Register generator and update `ModelGenerator`

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/Generator.java`
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/ModelGenerator.java`

**What to do:**
- Add `XdrCodecGenerator.class -> FileSetWriter::xdrCodecWriter` to `Generator.GENERATORS` map
- In `ModelGenerator.generateCodecFields()`, add:
  `public static final XdrCodec<Xxx> XDR = new XxxXdrCodec();`
- Add import for `XdrCodec` in model class generation

**Verify:** Build integration tests to confirm XDR codecs are generated and compile:
```bash
cd pbj-core && ./gradlew assemble
cd pbj-integration-tests && ./gradlew compileJava
```

---

## Phase 3: Code Generator — Write Method

Implement the XDR write (serialization) method generator. After this phase, XDR codecs can
serialize objects to XDR bytes.

### 3.1 Create `XdrCodecWriteMethodGenerator`

**Files to create:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecWriteMethodGenerator.java`

**What to do:**
- Study `CodecWriteMethodGenerator` as the reference pattern
- Generate `write(T item, WritableSequentialData output)` method
- For each field (sorted by field number), generate code based on field kind:
  - **Singular scalar**: write presence flag, if non-default write value via `XdrWriterTools`
  - **Singular 64-bit**: write presence flag, if non-default write hyper
  - **Singular bool**: write presence flag, if non-default write bool
  - **Singular string**: write presence flag, if non-empty write string
  - **Singular bytes**: write presence flag, if non-empty write opaque
  - **Singular enum**: write presence flag, if non-default write enum ordinal
  - **Singular message**: write presence flag, if non-null recursively write via `XDR.write()`
  - **Repeated**: write count (int), for each element write value
  - **Map**: write count (int), for each sorted key write key then value
  - **OneOf**: write discriminant (field number or 0), if set write arm value
  - **Optional wrapper**: write presence flag, if non-null write inner value
- Use the existing `Field` / `SingleField` / `OneOfField` / `MapField` accessors

**Verify:** `cd pbj-core && ./gradlew assemble && cd ../pbj-integration-tests && ./gradlew compileJava`

### 3.2 Create `XdrCodecMeasureRecordMethodGenerator`

**Files to create:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecMeasureRecordMethodGenerator.java`

**What to do:**
- Generate `measureRecord(T item)` method
- For each field, accumulate size:
  - **Singular**: 4 (presence flag) + value size if non-default
  - **Repeated**: 4 (count) + sum of element sizes
  - **Map**: 4 (count) + sum of key-value pair sizes
  - **OneOf**: 4 (discriminant) + arm size if set
  - **String/bytes**: use `XdrWriterTools.sizeOfString()` / `sizeOfOpaque()`
  - **Fixed scalars**: 4 (int/float/bool/enum) or 8 (long/double)
  - **Nested message**: recursively call `MessageType.XDR.measureRecord()`

**Verify:** `cd pbj-core && ./gradlew assemble && cd ../pbj-integration-tests && ./gradlew compileJava`

### 3.3 Wire write and measure into `XdrCodecGenerator`

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecGenerator.java`

**What to do:**
- Replace stub `write()` and `measureRecord()` with calls to the new sub-generators
- Implement `measure(ReadableSequentialData)` as parse-and-measure (design doc Section 7.6)
- Implement `fastEquals()` as `item.equals(parse(input))`
- Implement `getDefaultInstance()` returning `T.DEFAULT`

**Verify:**
```bash
cd pbj-core && ./gradlew assemble
cd pbj-integration-tests && ./gradlew compileJava
```

### 3.4 Manual verification of write output

**What to do:**
- Write a quick manual test or use the debugger to verify that a simple message (e.g.,
  `TimestampTest` with known values) produces the expected XDR bytes per the design doc
  Section 4 encoding rules
- Verify 4-byte alignment, big-endian byte order, presence flags, padding

---

## Phase 4: Code Generator — Parse Method

Implement the XDR parse (deserialization) method generator. After this phase, XDR codecs
can both serialize and deserialize — round-trip works.

### 4.1 Create `XdrCodecParseMethodGenerator`

**Files to create:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecParseMethodGenerator.java`

**What to do:**
- Study `CodecParseMethodGenerator` as the reference pattern
- Generate `parse(ReadableSequentialData input, boolean strictMode, boolean parseUnknownFields, int maxDepth, int maxSize)` method
- Structure:
  1. Check `maxDepth`
  2. Initialize temp variables for each field with defaults
  3. For each field (sorted by field number), generate code based on field kind:
     - **Singular**: read presence via `XdrParserTools.readPresence()`, if true read value
     - **Repeated**: read count via `input.readInt()`, validate against maxSize, loop reading elements using `addToList()`
     - **Map**: read count, validate, loop reading key-value pairs using `addToMap()`
     - **OneOf**: read discriminant via `input.readInt()`, switch on field number to read arm
     - **Optional wrapper**: read presence, if true read inner value
     - **Nested message**: recursively call `MessageType.XDR.parse()` with `maxDepth - 1`
  4. Post-processing: `makeReadOnly()` on `UnmodifiableArrayList` instances
  5. Construct and return model object from temp vars
- Note: `strictMode` and `parseUnknownFields` are ignored for XDR (no unknown fields possible)

**Verify:**
```bash
cd pbj-core && ./gradlew assemble
cd pbj-integration-tests && ./gradlew compileJava
```

### 4.2 Wire parse into `XdrCodecGenerator`

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/XdrCodecGenerator.java`

**What to do:**
- Replace stub `parse()` with call to `XdrCodecParseMethodGenerator`
- Ensure `measure()` and `fastEquals()` now work correctly (they depend on `parse()`)

**Verify:**
```bash
cd pbj-core && ./gradlew assemble
cd pbj-integration-tests && ./gradlew compileJava
```

### 4.3 Manual round-trip verification

**What to do:**
- Write a quick test: create a `TimestampTest` object, write to XDR, parse back, assert equals
- Verify with a complex object (`Everything`) that all field types round-trip correctly
- This is the milestone: **XDR round-trip works end-to-end**

---

## Phase 5: Generated Test Extension

Extend the test generator to produce XDR-specific tests automatically for every message.

### 5.1 Extend `TestGenerator` for XDR

**Files to modify:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/TestGenerator.java`

**What to do:**
- Add generated test method: `testXdrRoundTrip` — parameterized over ARGUMENTS
  - Write object to XDR → parse back → assertEquals
- Add generated test method: `testXdrMeasureConsistency`
  - measureRecord() matches actual write size
- Add generated test method: `testXdrFastEquals`
  - fastEquals(obj, XDR.toBytes(obj)) returns true
- Add generated test method: `testXdrGetDefaultValueMethod`
  - XDR codec returns correct default instance
- Add necessary imports for `XdrCodec`, `XdrParserTools`, `XdrWriterTools`

**Verify:**
```bash
cd pbj-core && ./gradlew assemble
cd pbj-integration-tests && ./gradlew build
```
This runs all 100k+ generated tests including the new XDR ones.

---

## Phase 6: Hand-Written Integration Tests

Write the integration tests that can't be auto-generated. All in
`pbj-integration-tests/src/test/java/com/hedera/pbj/integration/test/`.

### 6.1 Cross-codec consistency tests

**Files to create:**
- `XdrCrossCodecConsistencyTest.java`

**What to do:**
- Per test plan Section 2.2: for each message type, verify XDR round-trip produces the
  same object as protobuf round-trip
- Use `Everything`, `InnerEverything`, `TimestampTest`, `Hasheval`, message types with
  maps, oneOf, nested messages
- Parameterized over the existing ARGUMENTS lists

**Verify:** `cd pbj-integration-tests && ./gradlew test --tests "*XdrCrossCodecConsistency*"`

### 6.2 RFC binary compliance tests (full messages)

**Files to create:**
- `XdrBinaryComplianceTest.java`

**What to do:**
- Per test plan Section 2.3: hand-compute expected XDR byte sequences for known message
  instances and assert byte-for-byte equality
- Test cases:
  - Simple message with known scalar values
  - Message with all-default fields (presence=0 only)
  - Message with string padding (lengths 1-5)
  - Repeated field encoding
  - OneOf field encoding (discriminant = field number)
  - Map field encoding (sorted keys)
  - Nested message encoding (no length prefix)
  - Empty repeated/map fields (count=0)

**Verify:** `cd pbj-integration-tests && ./gradlew test --tests "*XdrBinaryCompliance*"`

### 6.3 Alignment, determinism, and size tests

**Files to create:**
- `XdrAlignmentTest.java`
- `XdrDeterminismTest.java`
- `XdrSizeComparisonTest.java`

**What to do:**
- Per test plan Sections 2.4-2.6:
  - Alignment: every encoded message length is a multiple of 4
  - Determinism: same object always produces identical bytes; equal objects produce identical bytes
  - Size comparison: XDR >= protobuf for all non-empty messages (log ratios)

**Verify:** `cd pbj-integration-tests && ./gradlew test --tests "*Xdr*Test"`

### 6.4 Unknown fields, safety limits, and malformed data tests

**Files to create:**
- `XdrUnknownFieldsTest.java`
- `XdrSafetyLimitsTest.java`
- `XdrMalformedDataTest.java`

**What to do:**
- Per test plan Sections 2.7-2.9:
  - Unknown fields are not written to XDR output
  - maxDepth enforcement for nested messages
  - maxSize enforcement for strings, bytes, repeated fields
  - Truncated data throws ParseException
  - Invalid data (bad bools, negative lengths, oversized counts) throws ParseException
  - No OOM, hang, or crash on any malformed input

**Verify:** `cd pbj-integration-tests && ./gradlew test --tests "*Xdr*Test"`

### 6.5 Fuzz tests

**Files to create:**
- `XdrFuzzTest.java`

**What to do:**
- Per test plan Section 2.10: use existing `FuzzTest` framework with XDR codec
- Run fuzz tests for: Everything, Hasheval, InnerEverything, TimestampTest, MessageWithString
- Threshold: >= 95% deserialization failure rate per message type
- Note: XDR may have lower failure rates than protobuf due to no tags/wire types —
  adjust thresholds if needed after initial run

**Verify:** `cd pbj-integration-tests && ./gradlew fuzzTest`

---

## Phase 7: Performance Benchmarks

JMH benchmarks in `pbj-integration-tests/src/jmh/java/com/hedera/pbj/integration/jmh/`.

### 7.1 XDR parse/write throughput benchmark

**Files to create:**
- `XdrObjectBench.java`

**What to do:**
- Per test plan Section 3.1: mirror `ProtobufObjectBench` structure
- State classes for: Everything, TimestampTest, AccountDetails
- Benchmark methods: parseXdr (4 buffer variants), writeXdr (3 buffer variants)
- Include protobuf baselines for direct comparison
- JMH config: `@Fork(1)`, `@Warmup(iterations=3, time=2)`, `@Measurement(iterations=7, time=2)`,
  `@BenchmarkMode(Mode.AverageTime)`, `@OutputTimeUnit(TimeUnit.NANOSECONDS)`,
  `@OperationsPerInvocation(1000)`

**Verify:** `cd pbj-integration-tests && ./gradlew jmh -Pinclude=".*XdrObject.*"`

### 7.2 XDR measure and hash benchmarks

**Files to create or modify:**
- `XdrMeasureBench.java`
- Extend `HashBench.java` with XDR-based hashing

**What to do:**
- Per test plan Sections 3.2 and 3.4:
  - Measure benchmark: compare `measureRecord()` cost for XDR vs protobuf
  - Hash benchmark: XDR serialize → SHA-256 (the CLPR running hash chain path)
- This validates the design decision to not cache XDR encoded size

**Verify:** `cd pbj-integration-tests && ./gradlew jmh -Pinclude=".*Xdr.*"`

---

## Phase 8: Quality Gate and Polish

### 8.1 Code formatting and quality checks

**What to do:**
```bash
cd pbj-core && ./gradlew qualityGate
cd pbj-integration-tests && ./gradlew spotlessApply
```
- Fix any spotless, checkstyle, or PMD violations
- Ensure all new files have SPDX license headers

### 8.2 Full build verification

**What to do:**
```bash
cd pbj-core && ./gradlew build
cd pbj-integration-tests && ./gradlew build
```
- All existing tests still pass (no regressions)
- All new XDR tests pass
- Generated XDR codecs compile for all proto schemas

### 8.3 Documentation

**Files to create/modify:**
- `docs/codec-xdr.md` — XDR codec internals (follows pattern of `codec-protobuf.md` and `codec-json.md`)
- `docs/codecs.md` — add XDR to the overview table and codec list
- `docs/architecture.md` — update "Generated Artifacts" section to mention XDR codec
- `docs/code-generation.md` — add XDR generator to the generators table

---

## Phase 9: Solidity XDR Decoder Generation

Generate Solidity contracts that decode XDR-encoded calldata on the EVM.

### 9.1 Design Solidity decoder output format

**Files to create:**
- `xdr-working-notes/solidity-decoder-design.md`

**What to do:**
- Define the target Solidity output for a sample message (e.g., ClprQueueMetadata)
- Design the generated contract structure: library vs internal functions
- Define calldata pointer arithmetic patterns
- Define how fixed-size vs variable-length structs differ in generated code
- Decide on Solidity version constraints and compiler settings

### 9.2 Create `SolidityXdrDecoderGenerator` (fixed-size structs)

**Files to create:**
- `pbj-compiler/src/main/java/com/hedera/pbj/compiler/impl/generators/xdr/SolidityXdrDecoderGenerator.java`

**What to do:**
- Generator that produces `.sol` files for messages where all fields are fixed-size
- Generate constant offset definitions for each field
- Generate `decode(bytes calldata data, uint256 offset)` function
- Use `calldataload` + `shr` for field extraction
- No runtime scanning needed — all offsets are compile-time constants

### 9.3 Extend for variable-length and union types

**Files to modify:**
- `SolidityXdrDecoderGenerator.java`

**What to do:**
- Add support for variable-length fields (strings, bytes, repeated)
- Add support for discriminated unions (oneOf)
- Generate pointer-advancing decode functions
- Generate iterator helpers for repeated fields

### 9.4 Solidity test infrastructure

**What to do:**
- Set up Foundry or Hardhat test project for generated Solidity decoders
- Write tests that encode messages in Java XDR, pass as calldata to Solidity decoder,
  verify decoded values match
- Gas profiling tests for CLPR-critical structures

### 9.5 Gas benchmarks

**What to do:**
- Measure gas cost for decoding ClprQueueMetadata (~40 gas target)
- Measure gas cost for reading ClprMessagePayload discriminant (~8 gas target)
- Measure gas cost for iterating repeated messages in a bundle
- Compare against protobuf varint decoding gas costs
- Document results in `xdr-working-notes/gas-benchmarks.md`

---

## Phase 10: CLPR Integration

### 10.1 Generate XDR codecs for CLPR protos

**What to do:**
- Add PBJ annotations (`pbj.xdr_fixed_length`, `pbj.xdr_max_length`) to CLPR `.proto` files
- Generate Java XDR codecs for all CLPR message types
- Generate Solidity XDR decoders for on-chain CLPR message types
- Verify round-trip for all CLPR messages

### 10.2 End-to-end cross-platform testing

**What to do:**
- Java XDR encode → Solidity XDR decode round-trip tests
- Verify running hash chain: Java and Solidity produce identical hashes from identical XDR payloads
- Integration with CLPR Service contract and Hiero node implementation

---

## Summary — Task Count by Phase

| Phase | Tasks | Description |
|-------|-------|-------------|
| 1 | 5 | Runtime foundation (XdrCodec, WriterTools, ParserTools, unit tests, FileType) |
| 2 | 4 | Generator scaffolding (config, FileSetWriter, skeleton generator, registration) |
| 3 | 4 | Write method generator (write, measure, wire up, manual verify) |
| 4 | 3 | Parse method generator (parse, wire up, manual verify) |
| 5 | 1 | Extend TestGenerator for XDR |
| 6 | 5 | Hand-written integration tests |
| 7 | 2 | JMH performance benchmarks |
| 8 | 3 | Quality gate, full build, documentation |
| 9 | 5 | Solidity decoder generation |
| 10 | 2 | CLPR integration |
| **Total** | **34** | |

**Key milestones:**
- After Phase 1: Runtime helpers work and are tested
- After Phase 4: **XDR round-trip works end-to-end** (first major milestone)
- After Phase 5: All 100k+ generated tests include XDR
- After Phase 8: Java XDR codec is production-ready
- After Phase 9: Solidity decoders generated and gas-tested
