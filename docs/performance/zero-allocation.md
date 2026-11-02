# Zero-allocation, high-performance PBJ

How PBJ reads and writes protobuf without garbage, why each API looks the way it does, what each costs, and what's
left. The options are described for users in [`../usage-guide.md`](../usage-guide.md#performance-options); the PR that
introduced each piece is in [history.md](history.md).

Numbers are ns per transaction on the synthetic HAPI block (#1028), on AMD EPYC 9275F, every option on, unless stated.

## The paths

| Need | Use | Allocation | Option |
|---|---|---|---|
| Objects from bytes | `Codec.parse(Bytes)` → array parser | the model objects only | `fast_parse` (B) |
| Objects to bytes | `write(T, byte[])` / `toBytes` | none, once sizes are cached | always |
| Objects to bytes, no measuring pass | `writeReverse` into a `ReverseBuffer` | none | `fast_write` (B) |
| Read fields without objects | `Xxx.View` | none | `views` (C) |
| Write fields without objects | `Xxx.Writer` into a `ProtoWriteBuffer` | none | `writers` (C) |
| No `OneOf` per oneof | unboxed oneof storage | one object less per oneof | `oneof_storage` (C) |

### Byte-array parser (`fast_parse`)

`ArrayCursor` is a position and limit over a `byte[]`; `Codec.parse` routes array-backed input to it. Each message's
parser has an **in-order fast path**: protobuf writers emit fields in ascending order, so the parser expects the next
field's tag, compares it in one go (`at(tag)`), reads the value and moves on. Anything unexpected (another order, an
unknown field, a repeated field split up) drops to a general loop with the same validation as the stream parser.

- Small oneofs (up to four alternatives) are tried in order inside the fast path; larger ones are a switch, because
  copying every alternative into the fast path made parsing slower.
- Short runs of repeated messages are counted first (`ArrayCursor.run`) and the list is created at that size. Counting
  gives up past 4,096 elements or 64 KiB: counting a list that spans megabytes costs more in cache misses than growing
  it.
- The varint reader is split so that its common path (two to eight bytes) inlines everywhere and everything else is a
  call. `varint64` does the same for 64-bit fields.
- `bytes` fields from `Bytes` input are views of it, not copies.

### Reverse writer (`fast_write`)

Writing back to front means every length is known when it is written, so there is no measuring pass and nothing is
moved. It is the fastest way to encode objects that are still in cache (989 against 1,410 ns forward). It reads each
object graph last field first, against the order the objects were allocated in, and out of cache that made it 2.7-4.3×
slower on a real block; so it stays an option. Measure on your own data.

### Views (`views`)

A view is a reusable flyweight over encoded bytes. `wrap` indexes the message once (offsets of each field into
primitive fields of the view) with the same fast path and validation as the parser; accessors read in place.

API decisions:
- **Nested in the model** (`BlockItem.View`), so nested messages get nested views without a new file type.
- **Reusable, owned by the parent.** Each nested message has one child view, re-wrapped on access; repeated fields
  give a reusable cursor (`for (var c = view.items(); c.next(); ) { … c.value() … }`). Nothing is allocated after the
  first wrap. The price: a child view is valid until the next access to the same field.
- **Wrapper types read without boxing:** `xxxOrElse(long)` next to the boxed accessor.
- **Bytes read in place:** `xxxByteAt(int)`, and `xxxAs(view)` wraps any view over a bytes field holding a message
  (such as `SignedTransaction.bodyBytes`) without copying it. Every view implements `MessageView` for this.
- **Same validation as parsing** (`strictMode`, `maxDepth`, `maxSize`): a view of malformed bytes throws, as parsing
  would.

### Unboxed oneofs (`oneof_storage = "unboxed"`)

A kind field and a value field instead of a `OneOf` object: 12-24% less allocation per block item, 13% per block. Hot
code switches on `xxxKind()`; `xxx()` still returns a `OneOf`, built on each call.

### Writers (`writers`)

`Xxx.Writer` encodes fields as the caller gives them, straight into a reusable `ProtoWriteBuffer`, byte-identical to
`toBytes`. One option on a root message (such as `Block`) covers everything reachable from it.

API decisions:
- **Always checked.** Fields must come in ascending order; the check is one compare against a `guard` field whose
  sentinel values also mean "a child is open" and "not open", so ending an open child is that compare's slow path.
- **Nested messages need no close in the common case:** a parent's next field ends the open child (auto-close).
  Lengths are reserved per type or adaptively and patched on close, moving the body only when the guess was wrong.
- **One-call setters for small messages:** `amount.accountID(shard, realm, num)`. Messages of up to six number, bool or
  enum fields with nothing nested are written in one call with no child writer; ids and timestamps are most of HAPI's
  nested messages, so this was the writers' single biggest win (−19%).
- **`writeFrom(object)`** writes a model object through the writer, reserving lengths exactly from its cached size.

## What it costs per nested message

HAPI nests a message for every id, timestamp and amount, so per-message cost dominates everything here.

- **Codecs** with cached sizes write a nested message as a tag, a length and a static call: almost free.
- **Writers** open a child, check each field and patch the length: most of their 1.5× against the reference.
- **Views** pay bookkeeping per nested message on `wrap` and on access: storing field offsets, re-wrapping and
  resetting the child, stepping the cursor, checking accessors. An EPYC profile puts it at about half of the view scan
  (about 12 ns against 6 ns for the reference per nested message), and almost none of it is in the hand-written reader,
  which only decodes varints. That is why views stay at 3× while the array parser reached 1.4×.

## What's left

- **A streaming pull reader.** A generated reader whose `next()` returns the next field's constant, with typed reads
  and `enter()`/`leave()` for nested messages and no per-message object state, is the only design that reaches the
  hand-written scan. It is a new API and was deferred on 2026-11-02, as were the two below.
- **Writers on a reverse buffer.** Filling the buffer back to front, as the reference codec does, would never guess or
  patch a length.
- **A lean model.** Models still carry hash and size caches, allocate a `Bytes` per bytes field and a list per repeated
  field, and box oneof values of number types. A leaner variant (no caches, sealed oneof-only messages, primitives where
  possible) would cut most of the decode allocation gap (8,151 against 3,750 B per transaction on HAPI).
- **Schema choices.** After all of this, much of what remains of decoding is multi-byte varints of large values (ids,
  amounts, nanosecond times). `fixed64` for such fields is a schema decision, not something PBJ can change; see
  [format-comparison.md](format-comparison.md).

## Independent block-stream workload

The same paths on an independent block-stream workload (deeply nested block items, state changes, map keys and values,
oneofs at every level, signed transactions; no maps or wrapper types): AMD EPYC 9275F, ns per transaction of a
20,000-transaction block, bytes allocated per transaction.

| Path | Before | After | Notes |
|---|---:|---:|---|
| Array parser (#999, #1002): block decode | 2,264 | 1,084 | 0.16.4: 2,221; transaction decode 233 → 201 |
| Varint split (#1035): block decode | 1,005 | 911 | −9%; transaction view scan −19% |
| Decode allocation (#1016, #1035) | 6,365 B | 5,058 B | 5,423 B after unboxed oneofs; the hand-written codec allocates 2,856 B |
| Reverse writer (#1003): block encode from objects | 1,134 | 840 | −26% |
| Views (#1005, #1007): block read every field | 1,640 (6,396 B) | 1,311 (0 B) | transaction scan 215 → 128 |
| Views, final (#1034, #1035) | | 1,237 (0 B) | transaction scan 117 (0 B) |
| `varint64` (#1035): block view scan | 1,279 | 1,128 | −12%; transaction view scan 104 → 116 |
| Inline setters (#1033): block encode with writers | 1,073 | 954 | final 948 ns, 32 B; transaction encode 98 |

The producer path end to end, from values to bytes with everything timed: building PBJ objects and writing forward
1,735 ns (3,738 B); building objects and writing in reverse 1,350 ns (3,738 B); writers 1,240 ns (0 B); the
hand-written codec 714 ns (0 B).

Final, against that workload's hand-written protobuf codec (the same bytes): block decode 1.22×, block encode 1.52×,
block view scan 2.46×, transaction decode 1.22×, transaction encode 1.11×, transaction view scan 1.98×.

Where the rest goes, from EPYC profiles:
- **View scan:** `index` 56% (its own code 17%, varint reads 22%, `at()` 9%), wrap and reset 10%, cursor `next` 11%,
  accessors 6.5%, oneof kind 2.5%. The hand-written scan is 42% varint decoding and little else: about 12 ns per nested
  message for views against 6, with roughly 80 nested messages per transaction.
- **Writer encode:** `tagVarint` 8.5%, `writeVarIntWide` as a call 8.5% (four- to eight-byte values), closing a child
  7.8%, opening one 6.4%, field checks 2.3%.
