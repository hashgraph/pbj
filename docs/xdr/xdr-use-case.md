# 1. Executive Summary

CLPR requires a binary serialization format for encoding all protocol messages — bundles, queue metadata, message payloads, and control messages — that travel both over the wire (gRPC between endpoints) and on-chain (as calldata and storage in smart contracts on EVM and other chains). The format must be **network-neutral** (not tied to any single blockchain ecosystem), **formally standardized**, **deterministic**, **gas-efficient for on-chain parsing**, and **compact on the wire**.

After evaluating XDR, SSZ, Borsh, Protobuf wire format, Solidity ABI encoding, and RLP, **XDR (External Data Representation, RFC 4506)** was selected as the CLPR streaming format. XDR offers the best overall balance of cross-platform neutrality, formal standardization, EVM gas efficiency, and wire compactness for CLPR's specific workload.

The format will be implemented as a new codec target in the PBJ (Protobuf Java) library, using the existing protobuf `.proto` schemas as the interface definition language (IDL) and generating both Java codecs and optimized Solidity decoder contracts.

---

# 2. Requirements

The serialization format for CLPR must satisfy the following requirements, derived from the protocol's cross-ledger design:

**Deterministic serialization.** The same logical message must always produce the identical byte sequence. This is essential for the running hash chains (§3.2.1 of the CLPR design doc), where the CLPR Service recomputes hashes by walking through message payloads sequentially. Any non-determinism would break hash verification and potentially halt a Connection.

**Gas-efficient on-chain parsing.** On Ethereum and other EVM chains, the CLPR Service is a smart contract. Every byte of calldata costs gas (16 gas per non-zero byte, 4 gas per zero byte), and every parsing operation (memory loads, shifts, branches) costs gas. The format must minimize both call data size and computational overhead for decoding.

**Compact wire representation.** Bundles travel between endpoints via gRPC and are submitted as calldata in on-chain transactions. Excessive padding or encoding overhead increases both network bandwidth and on-chain transaction costs.

**Cross-chain neutrality.** CLPR is designed to work across Hiero, Ethereum, and potentially any ledger (Solana, Avalanche, etc.). The format must not be tied to any single blockchain ecosystem.

**Formal standard with a specification.** The format must have a published, stable specification so that independent implementations on different chains can be built with confidence in interoperability.

**Protobuf schema compatibility.** CLPR's message types are defined in protobuf `.proto` files. The chosen format must have a clean mapping from protobuf types so that PBJ can generate codecs from the existing schemas.

**No requirement for Merkleization.** CLPR's Merkle proofs operate against the source ledger's native state tree (Hiero's Merkle state tree, Ethereum's Patricia trie via `eth_getProof`). The serialization format does not need built-in Merkle tree support. The running hash chain is a linear `hash(prev_hash, payload)` construction, not a Merkle tree.

---

# 3. Why XDR

## 3.1 Big-Endian Native Alignment

XDR uses big-endian (network byte order) for all integer types. This is the single most significant advantage for CLPR's two primary platforms:

**EVM (Ethereum Virtual Machine):** The EVM's native word representation is big-endian. When XDR-encoded integers arrive as calldata, extracting a `uint64` is a single `CALLDATALOAD` + `SHR` operation (~8 gas total). No byte-reversal is needed. For a little-endian format, every integer extraction requires an additional byte-swap operation (~20 gas), which compounds across every field in every message in every bundle.

**JVM (Java Virtual Machine):** Java is natively big-endian. Hiero's consensus node software runs on the JVM. XDR integers can be read directly from byte buffers with `ByteBuffer.getLong()` / `ByteBuffer.getInt()` without any byte order conversion. This means zero conversion overhead on both the endpoint software (Java/gRPC) and the on-chain CLPR Service (Solidity/EVM).

## 3.2 IETF Standard (RFC 4506)

XDR is defined in RFC 4506 (published 2006, successor to RFC 1832 from 1995). It is an IETF standard with decades of deployment history. For a cross-ledger protocol that must be chain-agnostic by design, using a format governed by a neutral standards body (rather than one owned by a specific blockchain ecosystem) carries meaningful weight for adoption and independent implementation.

## 3.3 Deterministic by Design

XDR serialization is fully deterministic. Given a schema and a set of values, there is exactly one valid byte sequence. There is no field reordering, no optional field omission, no variable-length integer encoding with multiple valid representations. This is critical for CLPR's running hash chains, which require identical serialization on both the sending and receiving ledger.

## 3.4 Simple Specification

The full XDR spec fits in a single RFC. The type system is small: integers (int, unsigned int, hyper, unsigned hyper), floating point (float, double), booleans, fixed/variable-length opaque data, strings, fixed/variable-length arrays, structs, discriminated unions, enums, and void. There are no extension mechanisms, no backward-compatible field evolution semantics, and no optional fields. This simplicity makes it straightforward to implement correct code generators in PBJ for both Java and Solidity targets.

## 3.5 Clean Protobuf Mapping

XDR's type system maps cleanly to protobuf types used in the CLPR `.proto` schemas:

| Protobuf Type | XDR Type | Size (bytes) | Notes |
|---|---|---|---|
| `uint32` | `unsigned int` | 4 | Direct mapping |
| `uint64` | `unsigned hyper` | 8 | Direct mapping |
| `int32` | `int` | 4 | Direct mapping |
| `int64` | `hyper` | 8 | Direct mapping |
| `bytes` | `opaque<>` | 4 + len + pad | Variable-length opaque with 4-byte length prefix, padded to 4-byte boundary |
| `bytes` (fixed, e.g. hash) | `opaque[N]` | N (+ pad) | Fixed-length opaque, no length prefix. Requires PBJ annotation. |
| `string` | `string<>` | 4 + len + pad | Same encoding as variable-length opaque, semantically string |
| `bool` | `bool` | 4 | XDR bool is a 4-byte int (0 or 1) |
| `message` (nested) | `struct` | Sum of fields | Fields serialized in declaration order |
| `repeated T` | `T<>` | 4 + elements | Variable-length array with 4-byte count prefix |
| `oneof` | `union` | 4 + arm | 4-byte discriminant followed by the selected arm |
| `enum` | `enum` | 4 | Direct mapping, 4-byte integer |

## 3.6 Proven Cross-Chain Deployment

XDR is the serialization format used by the Stellar network (XLM) and XRP Ledger. These are major production blockchain networks that have used XDR for years for transaction encoding and ledger state. This provides confidence that XDR works well in a blockchain/ledger context and that implementations exist across multiple languages.

---

# 4. Alternatives Considered

## 4.1 SSZ (Simple Serialize)

SSZ is the serialization format used by the Ethereum beacon chain (consensus layer). It was the strongest alternative to XDR.

**Advantages of SSZ:**
- Built-in Merkleization via `hash_tree_root`, enabling native Merkle proof generation from serialized structures.
- Existing Solidity decoder code from the Ethereum beacon chain bridge ecosystem (Snowbridge, DendrETH, and others).
- Offset tables for containers with variable-length fields, enabling O(1) random access to any field without sequential scanning.
- Established within the Ethereum ecosystem.

**Why SSZ was not chosen:**
- **Little-endian encoding.** SSZ encodes all integers as little-endian. This imposes a byte-swap cost (~20 gas per integer) on every field extraction in Solidity, and a conversion cost on the Java/Hiero side (Java is big-endian native). XDR avoids both.
- **Merkleization not needed.** CLPR's Merkle proofs are against the source ledger's native state tree, not against the serialization format's own Merkle structure. The running hash chain is a linear hash, not a Merkle tree. SSZ's primary differentiator does not apply.
- **Ethereum-centric.** SSZ is not an IETF or ISO standard. It is an internal Ethereum specification maintained by the Ethereum Foundation. For a protocol designed to be chain-neutral, adopting Ethereum's proprietary format sends the wrong signal to non-Ethereum chains.
- **More complex specification.** SSZ's offset table scheme, bitlist/bitvector types, and Merkleization rules add spec complexity that CLPR does not need.

**Net assessment:** SSZ would be a reasonable choice if Merkleization were needed or if Ethereum were the only target chain. Since neither applies, XDR's simpler spec, native endianness, and neutral standardization win.

## 4.2 Borsh (Binary Object Representation Serializer for Hashing)

Borsh is used by NEAR Protocol and Solana's Anchor framework. It was designed for deterministic serialization for hashing, which aligns well with CLPR's running hash chains.

**Advantages of Borsh:**
- Deterministic by design (canonical serialization for hashing).
- Very compact — no inter-field padding.
- Strong Solana/NEAR ecosystem presence.

**Why Borsh was not chosen:**
- **Little-endian encoding.** Same byte-swap tax as SSZ on both EVM and JVM platforms.
- **No formal standard.** Borsh has a specification on GitHub but is not an IETF, ISO, or any formal standards body publication.
- **Zero EVM ecosystem presence.** No existing Solidity libraries or on-chain usage in the Ethereum ecosystem. Every decoder would be written from scratch.
- **Limited adoption outside Solana/NEAR.** Would be a reasonable choice only if Solana were a primary CLPR target.

**Net assessment:** Borsh is a well-designed format, but its little-endian encoding and lack of formal standardization make it inferior to XDR for CLPR's requirements.

## 4.3 Protobuf Wire Format

Since CLPR's message types are already defined in `.proto` files and the gRPC transport layer uses protobuf natively, using protobuf's own wire format for on-chain encoding was considered.

**Advantages of Protobuf wire format:**
- Zero translation layer needed — the gRPC endpoint already produces protobuf-encoded bytes.
- Most compact wire format of all alternatives due to varint encoding for integers and tag-length-value structure.
- Widest cross-language library support of any serialization format.

**Why Protobuf wire format was not chosen:**
- **Non-deterministic serialization.** Protobuf does not guarantee canonical encoding. Field ordering can vary, default values may or may not be present, map fields have no defined order, and different implementations may produce different byte sequences for the same logical message. Achieving deterministic protobuf requires additional canonicalization rules layered on top, which creates fragile implementation-dependent behavior.
- **Varint decoding is expensive on-chain.** Protobuf encodes integers as variable-length integers (varints). Each byte requires a conditional branch to check the continuation bit, then a shift and OR. A single `uint64` can cost 50+ gas to decode vs ~8 gas for a fixed-width big-endian integer. Across all fields in all messages in a bundle, this is significant.
- **Tag-based field identification adds overhead.** Every field is preceded by a tag (field number + wire type). Parsing requires reading the tag, branching on field number, and handling unknown fields. This is unnecessary overhead when the schema is known at compile time.

**Net assessment:** Protobuf is the right choice for gRPC transport between endpoints (where both sides are running full protobuf libraries), but the wrong choice for on-chain encoding where determinism is critical and every gas unit matters. CLPR can transcode from protobuf to XDR at the endpoint before submitting bundles on-chain.

## 4.4 Solidity ABI Encoding

The EVM's native encoding format. Zero parsing cost in Solidity since the compiler handles decoding natively.

**Advantages of ABI encoding:**
- Zero parsing cost on EVM — the Solidity compiler generates free decoding.
- Native type support for all EVM-relevant types.

**Why ABI encoding was not chosen:**
- **Extreme wire overhead.** ABI encoding pads every value to 32 bytes. A `uint64` (8 bytes of data) becomes 32 bytes. A `ClprQueueMetadata` with three `uint64` fields and two `bytes32` fields would be 160 bytes in ABI vs ~88 bytes in XDR. At 16 gas per non-zero calldata byte, the calldata cost alone exceeds the parsing savings.
- **EVM-only.** ABI encoding is meaningful only on EVM chains. It is useless on Hiero (native service), Solana, or any non-EVM platform.
- **Not a general-purpose standard.** It is an Ethereum-internal encoding, not suitable as a cross-ledger protocol format.

**Net assessment:** ABI encoding would be optimal if CLPR only needed to work on a single EVM chain, but its excessive padding and EVM exclusivity make it unacceptable for a cross-ledger protocol.

## 4.5 RLP (Recursive Length Prefix)

Ethereum execution layer's legacy serialization format.

**Why RLP was rejected:** Limited type system (no fixed-width integers, no unions, no enums), painful to map from protobuf schemas, being phased out even within Ethereum in favor of SSZ. Not a serious contender.

---

# 5. Detailed Comparison

| Criterion | XDR | SSZ | Borsh | Protobuf | ABI |
|---|---|---|---|---|---|
| Endianness | **Big-endian** (native EVM + JVM) | Little-endian | Little-endian | Varint (variable) | Big-endian |
| Deterministic | **Yes** | Yes | Yes | **No** (without extra work) | Yes (strict mode) |
| Formal standard | **IETF RFC 4506** | Ethereum internal spec | GitHub spec | Google open standard | Ethereum internal spec |
| EVM uint64 decode cost | **~8 gas** (load + shift) | ~28 gas (load + shift + bswap) | ~28 gas (load + shift + bswap) | ~50+ gas (varint loop) | ~3 gas (native) |
| Calldata bytes per uint64 | **8** | 8 | 8 | 1–10 (varint) | 32 |
| Calldata bytes per bytes32 | 32 | 32 | 32 | 34 (tag + len + data) | 32 |
| Variable-length access pattern | Sequential scan (length-prefixed) | **O(1) via offset table** | Sequential scan | Sequential scan (tag-based) | O(1) via offsets |
| Merkle-tree native | No | **Yes** (hash_tree_root) | No | No | No |
| Existing Solidity decoders | No (generated by PBJ) | **Yes** (beacon chain bridges) | No | Yes (expensive) | **Native** |
| Cross-chain neutrality | **Excellent** (IETF, Stellar, XRP) | Ethereum-centric | Solana/NEAR-centric | Excellent | EVM-only |
| Protobuf schema compatibility | **Clean mapping** | Clean (needs max lengths) | Clean | Native | Clean |
| Wire compactness | Good (4-byte alignment padding) | Good | **Best** (no padding) | **Best** (varints) | Poor (32-byte padding) |

---

# 6. Trade-Offs and Known Limitations

## 6.1 4-Byte Alignment Padding

XDR pads all variable-length opaque data and strings to 4-byte boundaries. For a 33-byte value, XDR encodes 4 (length) + 33 (data) + 3 (padding) = 40 bytes. SSZ and Borsh would encode this as 4 (length) + 33 (data) = 37 bytes. This is a small wire overhead (~3 bytes per variable-length field on average) but is acceptable given the other advantages. Snappy compression on the gRPC layer can mitigate this for endpoint-to-endpoint transport.

## 6.2 Sequential Scanning for Variable-Length Fields

Unlike SSZ (which uses offset tables), XDR requires sequential scanning past variable-length fields to reach later fields in a struct. This means extracting the Nth field of a struct with variable-length fields before it requires reading the length of each preceding variable-length field.

**Mitigation for CLPR:** This is manageable because CLPR's gas-critical path (running hash verification) already walks every message sequentially. And for structs like `ClprQueueMetadata` (all fixed-size fields), there is no scanning overhead at all. The PBJ-generated Solidity decoders can compute field offsets at compile time for fixed-size structs and use efficient pointer arithmetic for variable-length structs.

## 6.3 4-Byte Boolean Encoding

XDR encodes booleans as 4-byte integers (0 or 1). This is wasteful compared to SSZ (1 byte) or Borsh (1 byte). However, CLPR's current protobuf schemas contain no boolean fields in the hot-path message types (`ClprQueueMetadata`, `ClprMessagePayload`, `ClprSyncPayload`), so this is a non-issue in practice.

## 6.4 No Existing Solidity Libraries

There are no pre-existing Solidity XDR parsing libraries. All Solidity decoders must be purpose-built. This is acceptable because PBJ will generate these decoders directly from the protobuf schemas — the generated code will be tighter and more gas-efficient than any generic library because it has full knowledge of the field layout at compile time.

## 6.5 Floating Point Types

XDR natively supports `float` and `double` (IEEE 754). The EVM has zero floating-point support. If any future CLPR schema uses floating-point types, they could not be decoded on-chain without extremely expensive software emulation. CLPR's current schemas do not use floating point, and future schemas should avoid it for on-chain types.

---

# 7. Gas Cost Analysis for CLPR-Critical Structures

## 7.1 ClprQueueMetadata (Fixed-Size)

This struct is decoded on every bundle verification. All fields are fixed-size.

Fields: `next_message_id` (uint64), `acked_message_id` (uint64), `sent_running_hash` (bytes32), `received_running_hash` (bytes32), `received_message_id` (uint64).

In XDR this is 8 + 8 + 32 + 32 + 8 = **88 bytes**, with all fields at known offsets. Decoding is pure offset arithmetic — no scanning, no branching.

Estimated gas for full decode: ~40 gas (5 × `CALLDATALOAD` at 3 gas each + shifts).

## 7.2 ClprMessagePayload (Union)

This is decoded for each message in a bundle during running hash verification and dispatch.

XDR encoding: 4-byte discriminant (message=0, reply=1, control=2) + arm-specific data. Reading the discriminant is a single `CALLDATALOAD` + `SHR` (~8 gas). Branching on the 3-way union is a small `if/else` chain.

For the running hash path, the contract does not even need to parse the union internals — it just needs the raw bytes of the entire payload to feed into `keccak256`. The union parsing only matters during dispatch, where the per-message gas budget (`clpr.maxGasPerMessage`) is already allocated.

## 7.3 Repeated Messages in a Bundle

A bundle of N messages: 4 bytes (array count) + N × (message payload + running hash). Sequential iteration to verify the running hash chain: read count, then loop. Each iteration: slice the payload bytes, hash them with the previous running hash, compare at the end. The XDR sequential-access pattern matches this workload perfectly.

---

# 8. PBJ Integration

## 8.1 Architecture

PBJ will be extended with a new XDR codec target alongside the existing protobuf codec. The architecture:

**Protobuf `.proto` schemas** remain the single source of truth for all CLPR message type definitions.

**PBJ Java XDR Codec** generates Java serialization/deserialization code that reads and writes XDR-encoded byte buffers. Used by the Hiero node software (CLPR Service native implementation) and endpoint gRPC code. Because Java is big-endian native, the generated code uses `ByteBuffer` in network byte order with no conversion overhead.

**PBJ Solidity XDR Codec** generates purpose-built Solidity decoder contracts for each CLPR message type. These are tight, schema-aware decoders that work directly on `calldata` using `calldataload` and `calldatacopy`. Because the schema is known at compile time, all fixed-size field offsets are computed as constants and all variable-length field access uses efficient pointer arithmetic.

## 8.2 PBJ Annotations

The protobuf-to-XDR mapping requires PBJ to introduce annotations for cases where protobuf types are ambiguous:

**Fixed-length opaque.** Protobuf `bytes` does not distinguish fixed-length from variable-length. CLPR uses `bytes` for both 32-byte hashes (always fixed-length) and opaque message payloads (variable-length). A PBJ annotation (e.g., `option (pbj.xdr_fixed_length) = 32`) can tell the code generator to emit `opaque[32]` (no length prefix) instead of `opaque<>` (with length prefix). This saves 4 bytes per hash field on the wire and one length-read on decode.

**Maximum lengths for variable-length fields.** XDR variable-length arrays and opaque data require a maximum length in the schema. These can be derived from CLPR's existing protocol limits: `MaxMessagesPerBundle` for the messages array, `MaxQueueDepth` for queue-related arrays, and protocol-defined limits for string fields like `chain_id`.

## 8.3 Solidity Code Generation Strategy

PBJ-generated Solidity decoders should follow these principles:

**Work directly on calldata.** Use `calldataload` and `calldatacopy` instead of first copying to memory. This avoids the quadratic memory expansion cost for fields that are only being hashed.

**Constant offsets for fixed-size structs.** For structs like `ClprQueueMetadata` where all fields are fixed-size, all offsets are compile-time constants. No runtime computation needed.

**Iterator pattern for variable-length arrays.** For `repeated` fields (e.g., messages in a bundle), generate an iterator that advances a pointer rather than materializing the entire array in memory.

**Minimal decoding for hash verification.** For the running hash chain path, generate a specialized function that extracts raw payload bytes without parsing the internal structure, since only the bytes matter for hashing.

## 8.4 Implementation Approach

Adding XDR to PBJ is a substantial change. The recommended approach is to decompose it into focused work chunks:

1. Define the protobuf-to-XDR type mapping rules and PBJ annotations.
2. Implement the Java XDR serializer/deserializer code generator.
3. Implement the Java XDR deserializer tests using the CLPR proto schemas.
4. Implement the Solidity XDR decoder code generator for fixed-size structs.
5. Implement the Solidity XDR decoder code generator for variable-length and union types.
6. Implement gas-cost benchmarks for the generated Solidity decoders.
7. Integrate with the CLPR Service contract and Hiero node CLPR implementation.

---

# 9. References

- [RFC 4506 — XDR: External Data Representation Standard](https://www.rfc-editor.org/rfc/rfc4506)
- [PBJ (hashgraph/pbj)](https://github.com/hashgraph/pbj)
- [SSZ — Simple Serialize (Ethereum Consensus Specs)](https://github.com/ethereum/consensus-specs/blob/master/ssz/simple-serialize.md)
- [Borsh — Binary Object Representation Serializer for Hashing](https://borsh.io/)
- [Stellar XDR Definitions](https://github.com/stellar/stellar-xdr)