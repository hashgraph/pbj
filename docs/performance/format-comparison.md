# Protobuf and PBJ against other binary formats

Where protobuf, and PBJ in particular, stands against the other binary formats a high-throughput system might choose,
why the gaps exist, and what that means for schema design. Measured on an independent block-stream workload (deeply
nested block items, state changes, map keys and values, oneofs at every level, signed transactions; no maps or wrapper
types), the same data encoded natively by every format, in Java, on two identical AMD EPYC 9275F machines (JDK 25, JMH 3
forks, `-prof gc`). How PBJ got to these numbers is in [history.md](history.md) and
[zero-allocation.md](zero-allocation.md).

## Results

Nanoseconds per transaction for a block of 20,000 transactions (about 10 block items and 12 state changes per
transaction), and per single transaction for the transaction columns. "Scan" reads every field. Sizes are bytes per
transaction in a large block, raw and after zstd, and the average size of one signed transaction.

| Format | Block encode | Block decode | Block scan | Tx encode | Tx decode | Block bytes | zstd | Tx bytes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| PBJ 0.16.4 | 1,718 | 2,240 | 2,872 | 150 | 232 | 691 | 302 | 116 |
| PBJ with #991-#1035, all options | 947 | 917 | 1,237 (views) | 98 | 107 | 691 | 302 | 116 |
| Protobuf, hand-written codec | 623 | 750 | 503 | 88 | 88 | 691 | 302 | 116 |
| SBE | 562 | 371 | 206 | 72 | 64 | 1,234 | 290 | 199 |
| H2O AutoBuffer (positional) | 396 | 243 | 115 | 43 | 41 | 747 | 268 | 140 |
| Borsh | 429 | 245 | 406 | 50 | 40 | 865 | 279 | 149 |
| SSZ | 722 | 368 | 253 | 46 | 55 | 1,074 | 323 | 162 |
| MessagePack | 1,120 | 849 | 1,361 | 109 | 101 | 675 | 289 | 119 |
| RLP | 5,130 | 1,528 | 1,025 | 447 | 216 | 588 | 297 | 117 |
| Cap'n Proto | 3,537 | 995 | 837 | 433 | 144 | 1,984 | 366 | 253 |
| Cap'n Proto, packed | 6,972 | 5,786 | 5,768 | 832 | 657 | 933 | 371 | 144 |
| FlatBuffers | 2,771 | 1,070 | 771 | 244 | 117 | 2,034 | 433 | 267 |
| Kryo | 3,709 | 2,711 | 3,434 | 340 | 311 | 542 | 269 | 109 |
| Hand-rolled positional varints (floor) | 593 | 556 | 740 | 80 | 70 | 469 | 263 | 102 |

Read with care:
- Each format uses its usual Java implementation (generated code where the format has a generator, the common library
  otherwise). Library quality matters as much as the format: the RLP, Cap'n Proto and FlatBuffers Java encoders were
  slow here, not necessarily the formats.
- Encode and decode are timed from and to each codec's own types; building them is untimed. PBJ "all options" encodes
  through its flyweight writers from plain values, and scans through views.
- Apache Fory is not in the table: it failed to start under JDK 25 with these JVM flags. Like Kryo, it writes Java class
  information into the bytes, so it fails the determinism requirement anyway.

## Why the gaps exist

The hand-written protobuf codec writes exactly PBJ's bytes. So the gap between it and PBJ is implementation (see
[zero-allocation.md](zero-allocation.md)), and the gap between it and SBE or H2O is the **wire format**:

- **Varints.** Protobuf encodes integers as varints: a loop or branch chain per value on read, a size computation and up
  to 10 stores on write. Fixed-width formats (SBE, H2O, Borsh) load and store integers in one instruction. Large values
  (ids, balances, 64-bit nanosecond timestamps, which take 9 bytes) are where it hurts most; after the PBJ work most of
  the remaining read cost is multi-byte varints.
- **Length prefixes on nested messages.** A protobuf writer must know a nested message's size before its bytes, so it
  either measures first, reserves and patches (moving bytes when the guess is wrong), or writes back to front. SBE and
  H2O have no nesting or fixed-size headers. Per nested message, PBJ's views still spend about twice the hand-written
  codec's bookkeeping (about 12 ns against 6), and the workload has roughly 80 nested messages per transaction.
- **Tags.** Every protobuf field carries a tag that the reader dispatches on. Positional formats know the next field
  from the schema. PBJ's in-order fast paths reduce this to one compare per field when fields arrive in number order.
- **Fixed offsets enable zero-work reads.** SBE reads a field with one load at a known offset, so "scan" costs almost
  nothing beyond touching the bytes. Protobuf views must index a message (find where each field starts) before reading.

What protobuf buys for that cost: compact bytes (varints and omitted defaults made it smaller raw than every format
except MessagePack, RLP, Kryo and the hand-rolled floor), schema evolution (field numbers, optional fields,
unknown-field skipping), and the largest ecosystem (generators for every language, gRPC, tooling that proxies, gateways
and firewalls already understand).

## Size: compression erases most differences

Raw sizes vary 4× (469 to 2,034 bytes per transaction). After zstd, which a stream or archive uses anyway, every format
lands between 263 and 433 bytes, and protobuf at 302 is in the middle of the pack. Size alone is rarely a reason to
leave protobuf; if bytes on the wire matter, compress.

## Determinism

Systems that hash encoded items (Merkle trees, signatures over re-encoded data) need **one encoding per value**.

| Class | Formats | Notes |
|---|---|---|
| Canonical by design | SBE, Borsh, SSZ, RLP | The spec allows exactly one encoding |
| Deterministic by convention | Protobuf, H2O, MessagePack, Cap'n Proto | One implementation is deterministic; the format allows others |
| Implementation-defined | FlatBuffers, Kryo, Fory | Builder order, padding, class identities or list implementations change the bytes; Kryo and Fory encoded the same data differently depending on the `List` implementation |

**A strict subset of protobuf is canonical.** With these rules, every value has exactly one encoding, the property the
canonical formats have:
- fields in ascending field-number order, each at most once;
- default (zero) values omitted, unless a field has explicit presence, which is written exactly when set;
- minimal-length varints and length prefixes;
- repeated scalars always packed;
- no unknown fields and no groups; no maps in hashed messages (a repeated message sorted by key instead);
- strings valid UTF-8.

PBJ already writes this form (and `protoc`'s output matches it). Enforce it where it matters with a strict parser, or by
decoding and re-encoding and comparing. Never re-encode signed bytes: hash and verify the bytes as received.

## Parsing in a smart contract

If encoded items must be verified on chain (for example in an Ethereum contract), the format must be cheap to parse in
the EVM. Fixed-offset formats (SBE, H2O, Borsh, RLP) are easiest; protobuf is feasible (Solidity proto3 decoders exist,
but fields are found by scanning tags, so gas grows with item size); Kryo and Fory are impractical.

## Schema design is the cheapest lever

The profiles show where protobuf time goes: per nested message, and per multi-byte varint. Designing schemas for that
costs nothing:
- **Keep nesting shallow.** Prefer plain `uint64` ids over wrapper messages (a `{ shard, realm, num }` id message is a
  nested message per id), and avoid messages whose only content is a `oneof` of other messages.
- **Use `fixed64` for values that are always large**: nanosecond timestamps (9-byte varints), hashes, large amounts. One
  8-byte load or store, no varint loop.
- **Field numbers 1-15 for hot fields** (one-byte tags), declared in the order they are usually written, which PBJ's
  in-order fast paths rely on.
- **Packed repeated scalars**, and **`bytes`** for opaque payloads (signed bodies, proofs) so they are never parsed or
  re-encoded on the way through.
- **Small messages (up to 6 integer, bool or enum fields) get PBJ's one-call inline setters** when writers are on
  ([zero-allocation.md](zero-allocation.md)): another reason to keep leaf messages small and flat.

## Takeaways

- With the #991-#1035 stack, PBJ is within 1.2× of a hand-written protobuf codec on decode and transaction encode, 1.5×
  on block encode and about 2.5× on read-every-field views.
- SBE and H2O remain 1.4-3.8× faster than PBJ on encode and decode and 6-11× on read-every-field scans. Against the best
  protobuf code possible (the hand-written codec) they are 1.1-3.1× faster on encode and decode and 2.4-4.4× on scans:
  that part is the wire format.
- What would close more of the protobuf gap is listed in [zero-allocation.md](zero-allocation.md#whats-left).
- Protobuf stays competitive with everything else measured, is compact, and is the only one with protobuf's ecosystem.
  For a system that values interoperability, keep protobuf, design flat schemas, and specialise only the hottest paths
  (still writing protobuf bytes).

