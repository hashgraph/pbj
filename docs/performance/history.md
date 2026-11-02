# History of the fast code generation series

Every pull request in the series, in stack order: what it changed, why, and what it measured. The plan behind it is
[`../fast-codegen-plan.md`](../fast-codegen-plan.md); what was tried and reverted is in its
[§9](../fast-codegen-plan.md#9-lessons-already-learned-do-not-rediscover), which is the one place to add new lessons.

All numbers are from two identical AMD EPYC 9275F machines (one CCD pinned, 3 forks, `-prof gc`; see
[measuring.md](measuring.md)). Tiers are those of the plan: **A** always on, **B** opt-in, more bytecode, **C**
opt-in, new API. `performance = "max"` turns on `fast_parse`, `fast_write`, `views` and unboxed oneofs; writers are a
separate option.

## The workloads

- **HAPI benchmarks** (from #991): `BlockItemBench`, `SampleBlockBench` (a real mainnet block, 67,869 items),
  `ProtobufObjectBench`, `TransactionBodyBench`. Used for every step until #1023.
- **The synthetic HAPI block** (#1028): 20,000 transactions generated with the shape and value sizes of an
  independent block-stream workload, plus a hand-written, zero-allocation reference codec for the same messages. It is
  the in-repo target from #1028 on.
- **An independent block-stream workload**: deeply nested block items, state changes, map keys and values, oneofs at
  every level, signed transactions; no maps or wrapper types. Measured outside this repository, on the same machines,
  as a second opinion on every decision from #1028 on.

## Where the series ended

The synthetic HAPI block, ns per transaction (allocation per transaction in brackets), and each figure's ratio to the
reference codec in the same run:

| | 0.16.4 | Defaults (tier A) | `max`, model API | Every option | Reference |
|---|---:|---:|---:|---:|---:|
| Decode block | 3,002 (10,029 B) 3.0× | 2,826 (10,029 B) 2.9× | 1,368 (8,151 B) 1.4× | 1,353 (8,151 B) 1.4× | 985-994 (3,750 B) |
| Encode block from objects | 3,389 (9,295 B) 4.4× | 1,538 2.0× | 1,401 (8,105 B) 1.9× | 1,410 1.9× | 740-775 (0 B) |
| Encode block, back to front | | | | 989 1.3× | |
| Write block with writers | | | | 1,122 (0 B) 1.5× | |
| Read every field, views | | | | 2,014 (0 B) 3.0× | 634-677 (0 B) |
| Decode a transaction | 1,202 (1,323 B) 11.9× | 278 2.6× | 150 (1,073 B) 1.3× | 148 1.3× | 101-116 (701 B) |
| Read every transaction field, views | | | | 197 (0 B) 3.8× | 50-52 (0 B) |

On the independent workload, with every option on, the same ratios were 1.21× for decode, 1.47× for encode, 2.25× for
the block view scan and 2.0× for the transaction view scan.

## The pull requests

### #991: harness (none)
The baseline every later PR is measured against, and the tests that keep the fast paths honest: the generated
non-canonical-order test (protoc's bytes with the top-level fields reversed must parse to the same object),
`AllocationTest`, and a build with every option on. Baseline: a mainnet block parses in 51.5 ms (protoc 62.7) and
writes in 57.7 ms (protoc 49.8).

### #992: options (infra)
`pbj.fast_parse`, `fast_write`, `views`, `oneof_storage`, `writers` and `performance`, per message, per file and per
project, with precedence. No generated-code change.

### #994: exact-size bulk varint writes (A)
The plan's single 8-byte store, bounded by the message end, lost 4-9% to the unrolled writer on HAPI block items. Exact
stores of 2, 4 or 8 bytes (combined for 3, 5, 6, 7), behind an inline one-byte case, beat both: **block item writes
23% faster with cached sizes, 15% fresh, 12% for `toBytes`**; signed transactions a tie.

### #995: straight-line byte-array writers (A)
Constant tags as literals, proto3 default checks inline, direct static calls into nested messages' codecs instead of
generic helpers taking a `FieldDefinition`. **`write(T, byte[])` 20-22% faster** for block items, 0 B/op.

### #996: straight-line size calculation and stream writers (A)
The same for `protobufSize()` and the stream writer, and `toBytes` routed to the array writer. **`toBytes` of a fresh
block item 2.1× faster** (968 → 452 ns); whole-block stream writes 15-23% faster.

### #997: parser clean-ups (A)
Schema lookup only for unknown fields, no `try/finally` per nested message, direct nested parse calls. **Every parse
5-11% faster**, streams included.

### #1010: string parsing (A)
No per-call `CharsetDecoder`, heap `ByteBuffer` or `CharBuffer`; an ASCII fast path. **28-33% faster** for ASCII, 41-64%
less allocation.

### #999 and #1002: byte-array parser (B, `fast_parse`)
`ArrayCursor` over a `byte[]`, routed to automatically for array-backed input, with an in-order fast path per message
(expect the next field's tag, read it, move on) and a general fallback with full validation. **A whole block 2.3×
faster** (46.5 → 20.4 ms, about 3× protoc), block items 1.9×, `Everything` 2.4× and now faster than protoc.

### #1003: reverse writer (B, `fast_write`)
Back to front into a reusable `ReverseBuffer`: no measuring pass, 0 B. 9-20% faster for objects still in cache, but
**2.7-4.3× slower out of cache**, because it walks each object graph last field first, against allocation order. Kept
off by default; on the synthetic block it is the fastest way to encode objects (989 against 1,410 ns).

### #1005 and #1007: views (C, `views`)
`Xxx.View`: reusable, indexed in one pass on `wrap`, fields read in place, 0 B. Reading a flat message 1.6× faster than
parsing; **routing a block item 7.4× faster** (300.9 → 40.8 ns, 0 against 1,565 B).

### #1015: method-size limit (A)
HotSpot never compiles a method over 8,000 bytes of bytecode. `TransactionBody`'s stream parser was 15 KB and always
ran interpreted. Messages over 32 fields now parse through per-field helpers: **`TransactionBody` parses 3.3× faster**
(1,256 → 376 ns). `GeneratedMethodSizeTest` keeps every generated method under 7,000 bytes. This is most of why the
defaults decode a transaction 4.3× faster than 0.16.4 (likely, not separately measured on the synthetic block).

### #1016: unboxed oneofs (C, `oneof_storage = "unboxed"`)
A kind field and a value field instead of a `OneOf` object. Parsing 5-8% faster, **13% less allocation per block**.

### #1022 and #1026: fixes (A)
Enum map values compile, and map entries write default values as protoc does (#1022). The byte-array writer wrote
corrupt wrapper messages for `-0.0` and `uint32` ≥ 2³¹ (#1026). `-0.0` in non-map fields (#1020) is deliberately left
alone: fixing it changes hashes.

### #1023: flyweight writers (C, `writers`)
`Xxx.Writer` encodes fields straight into a reusable `ProtoWriteBuffer` with no model objects, byte-identical to
`toBytes`, order-checked always. 0 B per item; at first 15% faster than building objects and calling `toBytes`, but 10%
slower than building them and writing in reverse. The gap was per nested message: open, check, patch the length.

### #1028: the synthetic HAPI block and the reference codec (test only)
The workload and target described above. Its first run put writers at 1.79×, decode at 1.46× and views at 3.5× the
reference.

### #1033: writers pay less per nested message (B)
The writer's state folded into one `guard` field with sentinels, a smaller close, and **one-call setters for small
messages** (up to six number, bool or enum fields, no repeated or nested ones: ids, timestamps; 439 in HAPI) that write
tag, length and fields with no child writer. Writers **18.9% faster** (1,381 → 1,121 ns); the setters are the whole
win.

### #1034: views pay less per nested message (B)
Small oneofs in the index fast path, one shared offset per oneof, wrap generations instead of per-child flags, child
views wrapped from the parent's checked cursor, `xxxOrElse(primitive)` so wrapper fields don't box, and bytes fields
read in place (`xxxByteAt`, `xxxAs(view)`). Views **11% faster** and 0 B (from 22 B).

### #1035: cheaper nested decoding (B)
Small oneofs in the array parser's fast path, short repeated fields sized once, a varint reader whose common path
(2-8 bytes) is small enough to inline, and `varint64` for 64-bit fields. Decode 3.5% faster with 414 B less, decode
then read every field 11.5% faster, views 5.9% faster. `varint64` was a trade-off: block view scans 7.5% faster here
and 12% on the independent workload, transaction scans flat here and 12 ns slower there; blocks matter more.

## What the series didn't reach

Against the hand-written codec, the targets were writers ≤ 1.25×, decode and transaction decode ≤ 1.15×, views ≤ 1.5×.
None was met on the synthetic block (1.5×, 1.4×, 1.3×, 3.0-3.8×); 0 B on every option-on path was. Where the remaining
time goes, and what could close it, is in [zero-allocation.md](zero-allocation.md#whats-left).
