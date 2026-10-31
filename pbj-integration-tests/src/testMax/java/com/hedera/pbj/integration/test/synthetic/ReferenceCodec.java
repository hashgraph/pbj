// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A hand-written, zero-allocation (in steady state) protobuf codec for the subset of HAPI messages used by the
 * {@link SyntheticBlock} synthetic workload. This is the in-repo performance target: "what a hand-written codec
 * achieves" for the same work PBJ's generated code does. It is benchmark-only code, not shipped as part of the
 * library, and it intentionally skips the strict-mode validation a production parser would do; see "Correctness over
 * validation" below.
 *
 * <h2>Encoding strategy</h2>
 * Every item is written back to front (upb's and PBJ's {@code ReverseBuffer} technique), into a {@link RevBuf} this
 * codec owns: a nested message's content is written before its length and tag, so the length is just the number of
 * bytes written since it began, with no measuring pass, no scratch buffer per nesting level and no copy between
 * levels. Fields are written in descending field order so that, read forward, they come out in ascending order,
 * byte-identical to protoc and to PBJ's forward and reverse writers. Varints of two to eight bytes use a single
 * 8-byte little-endian store into the free space below the write position: unlike a forward writer, that store never
 * needs an end guard, because the space below the write position is free until written. Because {@link
 * SyntheticBlock.Cursor} only reads its flat value stream forward, once, a message or list element's values are read
 * into locals (or, for a repeated field, into small reusable primitive arrays, grown as needed and then reused) before
 * any of it is written, so the write order can run backward over already-read data. One copy remains: {@link
 * #encodeItem} and {@link #encodeBlock} place the result at offset 0 of a buffer this codec owns, for callers that
 * need that; {@link #encodeItemReverse} skips it, for the per-item benchmark.
 *
 * <h2>Scanning and decoding</h2>
 * {@link #scanItem}, {@link #scanBlock}, {@link #scanTransaction}, {@link #decodeBlock}, {@link #decodeItem} and
 * {@link #decodeTransaction} (and the private methods they call, for every message in the subset) parse in order with
 * a general fallback, PBJ's "fast parse" technique: a message's fields are tested one at a time, in ascending field
 * number order, against the single byte a canonically-encoded field's tag would be; a sequence of plain fields is a
 * sequence of such one-byte compares, each consuming its field when it matches. Once a check fails to match (an
 * out-of-order, repeated beyond a fast-pathed prefix, oneof or unknown field) a conventional tag-then-switch loop
 * reads everything that is left, so the result is identical regardless of field order and strict about nothing this
 * reference does not validate. Varints are read with PBJ's XOR-chain algorithm, with the one-byte case inlined at the
 * read call so the common case never calls out to the multi-byte path. Oneof members stay in the general loop rather
 * than the fast path, as PBJ's generator does, because copying every alternative into the fast path only grows the
 * method without saving a compare. {@link #scanItem}, {@link #scanBlock} and {@link #scanTransaction} read directly
 * from a {@code byte[]} and allocate nothing; {@link #decodeBlock} and {@link #decodeTransaction} build a lightweight
 * hand-written object model for the same subset (small records and sealed interfaces below), allocating only the
 * objects the data actually needs. {@link #scan(RBlock)} and {@link #scan(RSignedTx)} sum the decoded objects' fields,
 * and must return the sums {@link ScanObjects} does.
 *
 * <h2>Correctness over validation</h2>
 * This is a benchmark reference, not a hardened parser: there is no strict-mode length/UTF-8/bounds checking, and
 * array indexing is trusted to throw on malformed input rather than reporting a clean parse error. Unknown fields are
 * still skipped correctly, by wire type, so future fields added to these messages do not corrupt the parse.
 *
 * <p>Not thread safe: a single instance owns reusable scratch buffers for encoding, scanning and decoding, so it is
 * meant to be created once per thread (or per benchmark) and reused.
 */
public final class ReferenceCodec {

    // ================================================================================================= wire format

    private static final int WT_VARINT = 0;
    private static final int WT_I64 = 1;
    private static final int WT_LEN = 2;
    private static final int WT_I32 = 5;

    private static long zigzag(final long v) {
        return (v << 1) ^ (v >> 63);
    }

    private static long unzigzag(final long v) {
        return (v >>> 1) ^ -(v & 1);
    }

    private static int varintSize(long value) {
        int size = 1;
        value >>>= 7;
        while (value != 0) {
            size++;
            value >>>= 7;
        }
        return size;
    }

    private static int tagSize(final int fieldNumber, final int wireType) {
        return varintSize(((long) fieldNumber << 3) | wireType);
    }

    /** The one-byte tag of a field in this subset (every field number used here is at most 15, or part of a oneof). */
    private static byte tagByte(final int fieldNumber, final int wireType) {
        return (byte) ((fieldNumber << 3) | wireType);
    }

    private static int appendVarint(final byte[] buf, int pos, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                buf[pos++] = (byte) value;
                return pos;
            }
            buf[pos++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int appendTag(final byte[] buf, final int pos, final int fieldNumber, final int wireType) {
        return appendVarint(buf, pos, ((long) fieldNumber << 3) | wireType);
    }

    // ===================================================================================================== RevBuf

    /**
     * A reusable buffer this codec writes a message into back to front, from its end toward its start (5.4 of
     * {@code docs/fast-codegen-plan.md}: upb's and PBJ's {@code ReverseBuffer} technique). A nested message's content
     * is written before its length and tag, so the length is simply the bytes written since it began: no measuring
     * pass, and nothing is ever shifted. Fields and list elements are written in descending order, so the result
     * reads front to back in ascending field order, byte-identical to a forward writer.
     *
     * <p>{@link #varint} writes two- to eight-byte varints with a single 8-byte little-endian store (5.1) into the
     * free space below the write position. A forward writer bounds that store by the message end, because the bytes
     * past it may belong to the caller; here the bytes below the write position are always free (not yet written),
     * so the store needs no such guard, only enough headroom, which {@link #ensure} provides.
     */
    private static final class RevBuf {
        private static final VarHandle LONG_LE =
                MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

        byte[] buf;
        int pos;

        RevBuf(final int initialCapacity) {
            buf = new byte[initialCapacity];
            pos = buf.length;
        }

        /** Discard the output, ready to write another message. */
        void reset() {
            pos = buf.length;
        }

        /** @return the number of bytes written since the last {@link #reset} */
        int length() {
            return buf.length - pos;
        }

        /** @return the current mark, to pass to {@link #lengthSince} after writing a nested message */
        int mark() {
            return buf.length - pos;
        }

        /** Write, as a varint before it, the length of everything written since {@code mark}. */
        void lengthSince(final int mark) {
            varint(buf.length - pos - mark);
        }

        /** Make room for at least {@code n} more bytes below the write position. */
        void ensure(final int n) {
            if (pos < n) {
                grow(n);
            }
        }

        private void grow(final int n) {
            final int used = buf.length - pos;
            final byte[] bigger = new byte[Math.max(buf.length * 2, used + n + 16)];
            System.arraycopy(buf, pos, bigger, bigger.length - used, used);
            buf = bigger;
            pos = bigger.length - used;
        }

        /** Write a field's tag. */
        void tag(final int fieldNumber, final int wireType) {
            varint(((long) fieldNumber << 3) | wireType);
        }

        /** Write an unsigned varint; see the class doc for the single-store technique. */
        void varint(final long v) {
            ensure(8);
            if ((v & ~0x7FL) == 0) {
                buf[--pos] = (byte) v;
                return;
            }
            if ((v >>> 56) != 0) {
                varintSlow(v);
                return;
            }
            final int size = (64 - Long.numberOfLeadingZeros(v) + 6) / 7; // 2..8
            final long spread = (v & 0x7FL)
                    | ((v & 0x3F80L) << 1)
                    | ((v & 0x1FC000L) << 2)
                    | ((v & 0xFE00000L) << 3)
                    | ((v & 0x7F0000000L) << 4)
                    | ((v & 0x3F800000000L) << 5)
                    | ((v & 0x1FC0000000000L) << 6)
                    | ((v & 0xFE000000000000L) << 7);
            final long continuation = 0x8080808080808080L & ((1L << ((size - 1) << 3)) - 1);
            // the word's low (8 - size) bytes are free space below the final position; shift the varint up to the
            // word's top `size` bytes, which land exactly at [pos - size, pos) once pos is decremented
            LONG_LE.set(buf, pos - 8, (spread | continuation) << ((8 - size) << 3));
            pos -= size;
        }

        /** A 9- or 10-byte varint (value at least 2^56): outside this workload, but handled for correctness. */
        private void varintSlow(long v) {
            int size = 1;
            for (long t = v >>> 7; t != 0; t >>>= 7) {
                size++;
            }
            ensure(size);
            pos -= size;
            int p = pos;
            while (true) {
                if ((v & ~0x7FL) == 0) {
                    buf[p] = (byte) v;
                    return;
                }
                buf[p++] = (byte) ((v & 0x7F) | 0x80);
                v >>>= 7;
            }
        }

        /** Write {@code length} raw bytes, ending at the current position. */
        void raw(final byte[] src, final int offset, final int length) {
            ensure(length);
            pos -= length;
            System.arraycopy(src, offset, buf, pos, length);
        }

        /** Copy the output to {@code dst[offset..]}, forward order; @return the number of bytes copied */
        int copyTo(final byte[] dst, final int offset) {
            final int length = buf.length - pos;
            System.arraycopy(buf, pos, dst, offset, length);
            return length;
        }
    }

    // ============================================================================================ ENCODE: leaves

    private static final byte[] SEMVER_CONTENT = {0x10, 0x42}; // SemanticVersion{minor=66}: tag(2,VARINT), 66

    private static void writeSemanticVersionConstReverse(final RevBuf b, final int fieldNumber) {
        final int m = b.mark();
        b.raw(SEMVER_CONTENT, 0, SEMVER_CONTENT.length);
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    /** {@code AccountID}: shard/realm are always 0 (mainnet); {@code accountNum} is a oneof member, always written. */
    private static void writeAccountIdReverse(final RevBuf b, final int fieldNumber, final long accountNum) {
        final int m = b.mark();
        b.varint(accountNum);
        b.tag(3, WT_VARINT);
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    /** {@code TokenID}: shard/realm always 0; {@code tokenNum} is a plain field, omitted only if ever 0. */
    private static void writeTokenIdReverse(final RevBuf b, final int fieldNumber, final long tokenNum) {
        final int m = b.mark();
        if (tokenNum != 0) {
            b.varint(tokenNum);
            b.tag(3, WT_VARINT);
        }
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    private static void writeTimestampReverse(
            final RevBuf b, final int fieldNumber, final long seconds, final int nanos) {
        final int m = b.mark();
        if (nanos != 0) {
            b.varint(nanos);
            b.tag(2, WT_VARINT);
        }
        if (seconds != 0) {
            b.varint(seconds);
            b.tag(1, WT_VARINT);
        }
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    private static void writeNftIdReverse(
            final RevBuf b, final int fieldNumber, final long tokenNum, final long serial) {
        final int m = b.mark();
        if (serial != 0) {
            b.varint(serial);
            b.tag(2, WT_VARINT);
        }
        writeTokenIdReverse(b, 1, tokenNum); // always written, even if the content is empty
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    private static void writeAccountAmountReverse(
            final RevBuf b, final int fieldNumber, final long accountNum, final long amount) {
        final int m = b.mark();
        final long z = zigzag(amount);
        if (z != 0) {
            b.varint(z);
            b.tag(2, WT_VARINT);
        }
        writeAccountIdReverse(b, 1, accountNum); // always written
        b.lengthSince(m);
        b.tag(fieldNumber, WT_LEN);
    }

    private static void writeBytesFieldReverse(
            final RevBuf b, final int fieldNumber, final byte[] src, final int offset, final int length) {
        if (length != 0) {
            b.raw(src, offset, length);
            b.varint(length);
            b.tag(fieldNumber, WT_LEN);
        }
    }

    // ============================================================================================= ENCODE: items

    /** Growable output of the last {@link #encodeItem} or {@link #encodeBlock} call. Reused; copy out before reuse. */
    private byte[] out = new byte[1 << 16];

    private int outPos;

    /** The message this codec is currently writing, back to front. */
    private final RevBuf rev = new RevBuf(1 << 12);

    // reusable scratch for a transaction result's balance-change list (field 7) and token transfer lists (field 8);
    // grown as needed and then reused, so a steady state of items this size or smaller allocates nothing
    private long[] taAccount = new long[8];
    private long[] taAmount = new long[8];
    private long[] ttlToken = new long[4];
    private int[] ttlStart = new int[4];
    private int[] ttlCount = new int[4];
    private long[] ttAccount = new long[16];
    private long[] ttAmount = new long[16];

    // reusable scratch for a state changes item's list of changes: stride 8 (stateId, op, up to 6 values; unused
    // slots depend on the op and are never read back)
    private long[] scVals = new long[8 * 16];

    /** The buffer written by {@link #encodeItem} and {@link #encodeBlock}, valid for {@code [0, length)}. */
    public byte[] buffer() {
        return out;
    }

    private void ensureOut(final int capacity) {
        if (out.length < capacity) {
            out = Arrays.copyOf(out, Math.max(capacity, out.length * 2));
        }
    }

    private static long[] ensureCap(long[] a, final int n) {
        return a.length < n ? Arrays.copyOf(a, Math.max(n, a.length * 2)) : a;
    }

    private static int[] ensureCap(int[] a, final int n) {
        return a.length < n ? Arrays.copyOf(a, Math.max(n, a.length * 2)) : a;
    }

    /**
     * Encode the next item from {@code c} back to front into the buffer {@link #reverseArray()} owns, with no copy:
     * the per-item benchmark path.
     *
     * @return the number of bytes written
     */
    public int encodeItemReverse(final SyntheticBlock.Cursor c) {
        rev.reset();
        writeItemReverse(c, rev);
        return rev.length();
    }

    /** @return the offset in {@link #reverseArray()} of the bytes the last {@link #encodeItemReverse} call wrote */
    public int reverseOffset() {
        return rev.pos;
    }

    /** @return the buffer {@link #encodeItemReverse} wrote into, valid at {@code [reverseOffset(), +length)} */
    public byte[] reverseArray() {
        return rev.buf;
    }

    /**
     * Encode the next item from {@code c} as one {@code BlockItem} (the message body, not the outer {@code Block}
     * field tag), into {@link #buffer()} at offset 0.
     *
     * @return the number of bytes written
     */
    public int encodeItem(final SyntheticBlock.Cursor c) {
        final int length = encodeItemReverse(c);
        ensureOut(length);
        rev.copyTo(out, 0);
        return length;
    }

    /**
     * Encode every item of {@code block} as a whole {@code Block} (repeated field 1), into {@link #buffer()}. Each
     * item's content is produced back to front (so its exact length is known with no measuring pass) and then copied
     * once into {@link #buffer()}; items themselves stay in forward order because {@link SyntheticBlock.Cursor} reads
     * its value stream forward, once, so they cannot be produced back to front across the whole block.
     *
     * @return the number of bytes written
     */
    public int encodeBlock(final SyntheticBlock block) {
        outPos = 0;
        final SyntheticBlock.Cursor c = block.cursor();
        for (int i = 0; i < block.items; i++) {
            final int length = encodeItemReverse(c);
            ensureOut(outPos + tagSize(1, WT_LEN) + varintSize(length) + length);
            outPos = appendTag(out, outPos, 1, WT_LEN);
            outPos = appendVarint(out, outPos, length);
            rev.copyTo(out, outPos);
            outPos += length;
        }
        return outPos;
    }

    private void writeItemReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        final int kind = c.i();
        switch (kind) {
            case SyntheticBlock.KIND_BLOCK_HEADER -> {
                final int m = b.mark();
                writeBlockHeaderReverse(c, b);
                b.lengthSince(m);
                b.tag(1, WT_LEN);
            }
            case SyntheticBlock.KIND_SIGNED_TRANSACTION -> {
                final int offset = c.i();
                final int length = c.i();
                b.raw(c.heap(), offset, length);
                b.varint(length);
                b.tag(4, WT_LEN);
            }
            case SyntheticBlock.KIND_TRANSACTION_RESULT -> {
                final int m = b.mark();
                writeTransactionResultReverse(c, b);
                b.lengthSince(m);
                b.tag(5, WT_LEN);
            }
            case SyntheticBlock.KIND_STATE_CHANGES -> {
                final int m = b.mark();
                writeStateChangesReverse(c, b);
                b.lengthSince(m);
                b.tag(7, WT_LEN);
            }
            case SyntheticBlock.KIND_BLOCK_FOOTER -> {
                final int m = b.mark();
                writeBlockFooterReverse(c, b);
                b.lengthSince(m);
                b.tag(12, WT_LEN);
            }
            case SyntheticBlock.KIND_BLOCK_PROOF -> {
                final int m = b.mark();
                writeBlockProofReverse(c, b);
                b.lengthSince(m);
                b.tag(9, WT_LEN);
            }
            default -> throw new IllegalStateException("Unknown item kind");
        }
    }

    private static void writeBlockHeaderReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        // read every value first (the cursor reads forward, once), then write the fields in descending order
        final long number = c.l();
        final long seconds = c.l();
        final int nanos = c.i();
        writeTimestampReverse(b, 4, seconds, nanos);
        if (number != 0) {
            b.varint(number);
            b.tag(3, WT_VARINT);
        }
        writeSemanticVersionConstReverse(b, 2);
        writeSemanticVersionConstReverse(b, 1);
        // field 5 (hash_algorithm) is SHA2_384 == 0, the default: omitted
    }

    private static void writeBlockFooterReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        final int o1 = c.i();
        final int l1 = c.i();
        final int o2 = c.i();
        final int l2 = c.i();
        final int o3 = c.i();
        final int l3 = c.i();
        final byte[] heap = c.heap();
        writeBytesFieldReverse(b, 3, heap, o3, l3);
        writeBytesFieldReverse(b, 2, heap, o2, l2);
        writeBytesFieldReverse(b, 1, heap, o1, l1);
    }

    private static void writeBlockProofReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        final long block = c.l();
        final int offset = c.i();
        final int length = c.i();
        // signed_block_proof (field 2, oneof member, always written) wraps TssSignedBlockProof{block_signature=1}
        final int m = b.mark();
        writeBytesFieldReverse(b, 1, c.heap(), offset, length);
        b.lengthSince(m);
        b.tag(2, WT_LEN);
        if (block != 0) {
            b.varint(block);
            b.tag(1, WT_VARINT);
        }
    }

    private void writeTransactionResultReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        final int status = c.i();
        final long seconds = c.l();
        final int nanos = c.i();
        final long fee = c.l();
        final int transfers = c.i();
        taAccount = ensureCap(taAccount, transfers);
        taAmount = ensureCap(taAmount, transfers);
        for (int i = 0; i < transfers; i++) {
            taAccount[i] = c.l();
            taAmount[i] = c.l();
        }
        final int lists = c.i();
        ttlToken = ensureCap(ttlToken, lists);
        ttlStart = ensureCap(ttlStart, lists);
        ttlCount = ensureCap(ttlCount, lists);
        int innerCount = 0;
        for (int j = 0; j < lists; j++) {
            final long token = c.l();
            final int count = c.i();
            ttlToken[j] = token;
            ttlStart[j] = innerCount;
            ttlCount[j] = count;
            ttAccount = ensureCap(ttAccount, innerCount + count);
            ttAmount = ensureCap(ttAmount, innerCount + count);
            for (int k = 0; k < count; k++) {
                ttAccount[innerCount] = c.l();
                ttAmount[innerCount] = c.l();
                innerCount++;
            }
        }
        // write in descending field order: 8 (reverse list order), 7, 6, 2, 1
        for (int j = lists - 1; j >= 0; j--) {
            final int m = b.mark();
            final int start = ttlStart[j];
            final int count = ttlCount[j];
            for (int k = count - 1; k >= 0; k--) {
                writeAccountAmountReverse(b, 2, ttAccount[start + k], ttAmount[start + k]);
            }
            writeTokenIdReverse(b, 1, ttlToken[j]);
            b.lengthSince(m);
            b.tag(8, WT_LEN);
        }
        if (transfers > 0) {
            final int m = b.mark();
            for (int i = transfers - 1; i >= 0; i--) {
                writeAccountAmountReverse(b, 1, taAccount[i], taAmount[i]);
            }
            b.lengthSince(m);
            b.tag(7, WT_LEN);
        }
        if (fee != 0) {
            b.varint(fee);
            b.tag(6, WT_VARINT);
        }
        writeTimestampReverse(b, 2, seconds, nanos);
        if (status != 0) {
            b.varint(status);
            b.tag(1, WT_VARINT);
        }
    }

    private void writeStateChangesReverse(final SyntheticBlock.Cursor c, final RevBuf b) {
        final long seconds = c.l();
        final int nanos = c.i();
        final int count = c.i();
        scVals = ensureCap(scVals, count * 8);
        for (int i = 0; i < count; i++) {
            final int base = i * 8;
            final int stateId = c.i();
            final int op = c.i();
            scVals[base] = stateId;
            scVals[base + 1] = op;
            switch (op) {
                case SyntheticBlock.OP_TOKEN_REL -> {
                    scVals[base + 2] = c.l(); // account
                    scVals[base + 3] = c.l(); // token
                    scVals[base + 4] = c.l(); // balance
                    scVals[base + 5] = c.l(); // automatic association (0/1)
                }
                case SyntheticBlock.OP_ACCOUNT -> {
                    scVals[base + 2] = c.l(); // account
                    scVals[base + 3] = c.l(); // balance
                }
                case SyntheticBlock.OP_NFT -> {
                    scVals[base + 2] = c.l(); // token
                    scVals[base + 3] = c.l(); // serial
                    scVals[base + 4] = c.l(); // owner
                    scVals[base + 5] = c.l(); // spender
                    scVals[base + 6] = c.l(); // mint seconds
                    scVals[base + 7] = c.i(); // mint nanos
                }
                case SyntheticBlock.OP_NFT_PRIVATE -> {
                    // no spender (base + 5): leave it unused so mint time lines up with OP_NFT's slots, which
                    // writeNftChangeReverse always reads from base + 6 / base + 7 regardless of `full`
                    scVals[base + 2] = c.l(); // token
                    scVals[base + 3] = c.l(); // serial
                    scVals[base + 4] = c.l(); // owner
                    scVals[base + 6] = c.l(); // mint seconds
                    scVals[base + 7] = c.i(); // mint nanos
                }
                case SyntheticBlock.OP_NFT_DELETE -> {
                    scVals[base + 2] = c.l(); // token
                    scVals[base + 3] = c.l(); // serial
                }
                case SyntheticBlock.OP_SINGLETON -> scVals[base + 2] = c.l(); // value
                case SyntheticBlock.OP_RECEIPT_PUSH -> {
                    scVals[base + 2] = c.l(); // node id
                    scVals[base + 3] = c.l(); // valid start seconds
                    scVals[base + 4] = c.i(); // valid start nanos
                    scVals[base + 5] = c.l(); // payer account
                    scVals[base + 6] = c.i(); // status
                }
                default -> {} // OP_QUEUE_POP: nothing more
            }
        }
        // field 2 (changes), reverse element order, then field 1 (timestamp)
        for (int i = count - 1; i >= 0; i--) {
            final int base = i * 8;
            final int m = b.mark();
            writeStateChangeContentReverse(b, base);
            b.lengthSince(m);
            b.tag(2, WT_LEN);
        }
        writeTimestampReverse(b, 1, seconds, nanos);
    }

    private void writeStateChangeContentReverse(final RevBuf b, final int base) {
        final int op = (int) scVals[base + 1];
        switch (op) {
            case SyntheticBlock.OP_TOKEN_REL -> writeTokenRelChangeReverse(b, base);
            case SyntheticBlock.OP_ACCOUNT -> writeAccountChangeReverse(b, base);
            case SyntheticBlock.OP_NFT -> writeNftChangeReverse(b, base, true);
            case SyntheticBlock.OP_NFT_PRIVATE -> writeNftChangeReverse(b, base, false);
            case SyntheticBlock.OP_NFT_DELETE -> writeNftDeleteChangeReverse(b, base);
            case SyntheticBlock.OP_SINGLETON -> writeSingletonChangeReverse(b, base);
            case SyntheticBlock.OP_RECEIPT_PUSH -> writeReceiptPushChangeReverse(b, base);
            default -> { // OP_QUEUE_POP: field 8, an empty QueuePopChange
                final int m = b.mark();
                b.lengthSince(m);
                b.tag(8, WT_LEN);
            }
        }
        final int stateId = (int) scVals[base];
        if (stateId != 0) {
            b.varint(stateId);
            b.tag(1, WT_VARINT);
        }
    }

    private void writeTokenRelChangeReverse(final RevBuf b, final int base) {
        final long account = scVals[base + 2];
        final long token = scVals[base + 3];
        final long balance = scVals[base + 4];
        final boolean auto = scVals[base + 5] != 0;
        final int m5 = b.mark(); // StateChange.map_update = field 5
        final int mv = b.mark(); // MapUpdateChange.value = field 2
        final int mtr = b.mark(); // MapChangeValue.token_relation_value = field 12
        if (auto) {
            b.varint(1);
            b.tag(6, WT_VARINT);
        }
        if (balance != 0) {
            b.varint(balance);
            b.tag(3, WT_VARINT);
        }
        writeAccountIdReverse(b, 2, account);
        writeTokenIdReverse(b, 1, token);
        b.lengthSince(mtr);
        b.tag(12, WT_LEN);
        b.lengthSince(mv);
        b.tag(2, WT_LEN);
        final int mk = b.mark(); // MapUpdateChange.key = field 1
        final int mata = b.mark(); // MapChangeKey.token_relationship_key = field 25
        writeTokenIdReverse(b, 2, token);
        writeAccountIdReverse(b, 1, account);
        b.lengthSince(mata);
        b.tag(25, WT_LEN);
        b.lengthSince(mk);
        b.tag(1, WT_LEN);
        b.lengthSince(m5);
        b.tag(5, WT_LEN);
    }

    private void writeAccountChangeReverse(final RevBuf b, final int base) {
        final long account = scVals[base + 2];
        final long balance = scVals[base + 3];
        final int m5 = b.mark(); // StateChange.map_update = field 5
        final int mv = b.mark(); // MapUpdateChange.value = field 2
        final int mtr = b.mark(); // MapChangeValue.token_relation_value = field 12 (balance only)
        if (balance != 0) {
            b.varint(balance);
            b.tag(3, WT_VARINT);
        }
        b.lengthSince(mtr);
        b.tag(12, WT_LEN);
        b.lengthSince(mv);
        b.tag(2, WT_LEN);
        final int mk = b.mark(); // MapUpdateChange.key = field 1
        writeAccountIdReverse(b, 1, account); // MapChangeKey.account_id_key = field 1
        b.lengthSince(mk);
        b.tag(1, WT_LEN);
        b.lengthSince(m5);
        b.tag(5, WT_LEN);
    }

    private void writeNftChangeReverse(final RevBuf b, final int base, final boolean full) {
        final long token = scVals[base + 2];
        final long serial = scVals[base + 3];
        final long owner = scVals[base + 4];
        final long spender = full ? scVals[base + 5] : 0;
        final long mintSeconds = scVals[base + 6];
        final int mintNanos = (int) scVals[base + 7];
        final int m5 = b.mark(); // StateChange.map_update = field 5
        final int mv = b.mark(); // MapUpdateChange.value = field 2
        final int mnft = b.mark(); // MapChangeValue.nft_value = field 5
        writeTimestampReverse(b, 4, mintSeconds, mintNanos);
        if (full) {
            writeAccountIdReverse(b, 3, spender);
        }
        writeAccountIdReverse(b, 2, owner);
        if (full) {
            writeNftIdReverse(b, 1, token, serial);
        }
        b.lengthSince(mnft);
        b.tag(5, WT_LEN);
        b.lengthSince(mv);
        b.tag(2, WT_LEN);
        final int mk = b.mark(); // MapUpdateChange.key = field 1
        writeNftIdReverse(b, 5, token, serial); // MapChangeKey.nft_id_key = field 5
        b.lengthSince(mk);
        b.tag(1, WT_LEN);
        b.lengthSince(m5);
        b.tag(5, WT_LEN);
    }

    private void writeNftDeleteChangeReverse(final RevBuf b, final int base) {
        final long token = scVals[base + 2];
        final long serial = scVals[base + 3];
        final int m6 = b.mark(); // StateChange.map_delete = field 6
        final int mk = b.mark(); // MapDeleteChange.key = field 1
        writeNftIdReverse(b, 5, token, serial); // MapChangeKey.nft_id_key = field 5
        b.lengthSince(mk);
        b.tag(1, WT_LEN);
        b.lengthSince(m6);
        b.tag(6, WT_LEN);
    }

    private void writeSingletonChangeReverse(final RevBuf b, final int base) {
        final long value = scVals[base + 2];
        final int m4 = b.mark(); // StateChange.singleton_update = field 4
        final int m3 = b.mark(); // SingletonUpdateChange.entity_number_value = field 3, oneof: always written
        if (value != 0) {
            b.varint(value);
            b.tag(1, WT_VARINT); // UInt64Value.value = field 1
        }
        b.lengthSince(m3);
        b.tag(3, WT_LEN);
        b.lengthSince(m4);
        b.tag(4, WT_LEN);
    }

    private void writeReceiptPushChangeReverse(final RevBuf b, final int base) {
        final long node = scVals[base + 2];
        final long validSeconds = scVals[base + 3];
        final int validNanos = (int) scVals[base + 4];
        final long payer = scVals[base + 5];
        final int status = (int) scVals[base + 6];
        final int m7 = b.mark(); // StateChange.queue_push = field 7
        final int mqpc = b.mark(); // QueuePushChange.transaction_receipt_entries_element = field 3
        final int mtre = b.mark(); // TransactionReceiptEntries.entries = field 1 (a single element)
        final int mentry = b.mark(); // TransactionReceiptEntry content
        if (status != 0) {
            b.varint(status);
            b.tag(3, WT_VARINT);
        }
        final int mtxid = b.mark(); // TransactionReceiptEntry.transaction_id = field 2
        writeAccountIdReverse(b, 2, payer);
        writeTimestampReverse(b, 1, validSeconds, validNanos);
        b.lengthSince(mtxid);
        b.tag(2, WT_LEN);
        if (node != 0) {
            b.varint(node);
            b.tag(1, WT_VARINT);
        }
        b.lengthSince(mentry);
        b.tag(1, WT_LEN);
        b.lengthSince(mtre);
        b.tag(3, WT_LEN);
        b.lengthSince(mqpc);
        b.tag(7, WT_LEN);
    }

    // ==================================================================================================== SCAN

    private byte[] sBuf;
    private int sPos;

    /** If the next byte before {@code limit} is {@code tag}, consume it and return true (the in-order fast path). */
    private boolean sAt(final int limit, final byte tag) {
        final int q = sPos;
        if (q < limit && sBuf[q] == tag) {
            sPos = q + 1;
            return true;
        }
        return false;
    }

    private long readVarint() {
        final byte b = sBuf[sPos];
        if (b >= 0) {
            sPos++;
            return b;
        }
        return sVarintWide();
    }

    /** PBJ's XOR-chain varint reader ({@code ByteArrayBufferedData.readVar} / {@code ArrayCursor.varintWide}). */
    private long sVarintWide() {
        final byte[] a = sBuf;
        int p = sPos;
        int vi = a[p++];
        long result;
        if ((vi ^= a[p++] << 7) < 0) {
            vi ^= (~0 << 7);
            result = vi;
        } else if ((vi ^= a[p++] << 14) >= 0) {
            vi ^= (~0 << 7) ^ (~0 << 14);
            result = vi;
        } else if ((vi ^= a[p++] << 21) < 0) {
            vi ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            result = vi;
        } else {
            long vl = vi;
            if ((vl ^= (long) a[p++] << 28) >= 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((vl ^= (long) a[p++] << 35) < 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((vl ^= (long) a[p++] << 42) >= 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((vl ^= (long) a[p++] << 49) < 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            } else if ((vl ^= (long) a[p++] << 56) >= 0L) {
                vl ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56);
            } else {
                vl ^= (long) a[p++] << 63;
                vl ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56)
                        ^ (~0L << 63);
            }
            result = vl;
        }
        sPos = p;
        return result;
    }

    private void skip(final int wireType) {
        switch (wireType) {
            case WT_VARINT -> readVarint();
            case WT_I64 -> sPos += 8;
            case WT_LEN -> {
                final int len = (int) readVarint();
                sPos += len;
            }
            case WT_I32 -> sPos += 4;
            default -> throw new IllegalStateException("Unknown wire type " + wireType);
        }
    }

    private long bytesSum(final int from, final int len) {
        return len == 0 ? 0 : len + sBuf[from] + sBuf[from + len - 1];
    }

    /** Parse one encoded {@code BlockItem} and sum every field, the same sum as {@code ScanObjects.item}. */
    public long scanItem(final byte[] buf, final int offset, final int length) {
        sBuf = buf;
        sPos = offset;
        return scanItemContent(offset + length);
    }

    /** Parse an encoded {@code Block} (repeated {@code BlockItem}) and sum every item. */
    public long scanBlock(final byte[] buf, final int offset, final int length) {
        sBuf = buf;
        sPos = offset;
        final int limit = offset + length;
        long sum = 0;
        while (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            final int itemLimit = sPos + len;
            sum += scanItemContent(itemLimit);
            sPos = itemLimit;
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 && wireType == WT_LEN) {
                final int len = (int) readVarint();
                final int itemLimit = sPos + len;
                sum += scanItemContent(itemLimit);
                sPos = itemLimit;
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** The {@code item} oneof: every branch stays in the general loop, so there is no in-order fast path here. */
    private long scanItemContent(final int limit) {
        long sum = 0;
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += 1 + scanBlockHeaderField();
                case 4 -> {
                    final int len = (int) readVarint();
                    sum += 4 + bytesSum(sPos, len);
                    sPos += len;
                }
                case 5 -> sum += 5 + scanTransactionResultField();
                case 7 -> sum += 7 + scanStateChangesField();
                case 9 -> sum += 9 + scanBlockProofField();
                case 12 -> sum += 12 + scanBlockFooterField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /**
     * Each {@code scanXxxField()} reads a length-delimited field's length, scans its content with
     * {@code scanXxx}, and restores {@link #sPos} to just past it. Plain direct calls: no functional
     * interface, no lambda, no allocation.
     */
    private long scanAccountAmountField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanAccountAmount(limit);
        sPos = limit;
        return sum;
    }

    private long scanAccountAmountsField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanAccountAmounts(limit);
        sPos = limit;
        return sum;
    }

    private long scanAccountIdField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanAccountId(limit);
        sPos = limit;
        return sum;
    }

    private long scanAccountTokenAssociationField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanAccountTokenAssociation(limit);
        sPos = limit;
        return sum;
    }

    private long scanAtomicBatchField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanAtomicBatch(limit);
        sPos = limit;
        return sum;
    }

    private long scanBlockFooterField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanBlockFooter(limit);
        sPos = limit;
        return sum;
    }

    private long scanBlockHeaderField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanBlockHeader(limit);
        sPos = limit;
        return sum;
    }

    private long scanBlockProofField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanBlockProof(limit);
        sPos = limit;
        return sum;
    }

    private long scanCryptoTransferField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanCryptoTransfer(limit);
        sPos = limit;
        return sum;
    }

    private long scanMapChangeKeyField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanMapChangeKey(limit);
        sPos = limit;
        return sum;
    }

    private long scanMapChangeValueField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanMapChangeValue(limit);
        sPos = limit;
        return sum;
    }

    private long scanMapDeleteChangeField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanMapDeleteChange(limit);
        sPos = limit;
        return sum;
    }

    private long scanMapUpdateChangeField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanMapUpdateChange(limit);
        sPos = limit;
        return sum;
    }

    private long scanNftField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanNft(limit);
        sPos = limit;
        return sum;
    }

    private long scanNftIdField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanNftId(limit);
        sPos = limit;
        return sum;
    }

    private long scanQueuePushChangeField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanQueuePushChange(limit);
        sPos = limit;
        return sum;
    }

    private long scanSemanticVersionField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanSemanticVersion(limit);
        sPos = limit;
        return sum;
    }

    private long scanSignatureMapField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanSignatureMap(limit);
        sPos = limit;
        return sum;
    }

    private long scanSignaturePairField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanSignaturePair(limit);
        sPos = limit;
        return sum;
    }

    private long scanSingletonUpdateChangeField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanSingletonUpdateChange(limit);
        sPos = limit;
        return sum;
    }

    private long scanStateChangeField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanStateChange(limit);
        sPos = limit;
        return sum;
    }

    private long scanStateChangesField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanStateChanges(limit);
        sPos = limit;
        return sum;
    }

    private long scanTimestampField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTimestamp(limit);
        sPos = limit;
        return sum;
    }

    private long scanTokenIdField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTokenId(limit);
        sPos = limit;
        return sum;
    }

    private long scanTokenRelationField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTokenRelation(limit);
        sPos = limit;
        return sum;
    }

    private long scanTokenTransferListField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTokenTransferList(limit);
        sPos = limit;
        return sum;
    }

    private long scanTransactionIdField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTransactionId(limit);
        sPos = limit;
        return sum;
    }

    private long scanTransactionReceiptEntriesField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTransactionReceiptEntries(limit);
        sPos = limit;
        return sum;
    }

    private long scanTransactionReceiptEntryField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTransactionReceiptEntry(limit);
        sPos = limit;
        return sum;
    }

    private long scanTransactionResultField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTransactionResult(limit);
        sPos = limit;
        return sum;
    }

    private long scanTssSignedBlockProofField() {
        final int len = (int) readVarint();
        final int limit = sPos + len;
        final long sum = scanTssSignedBlockProof(limit);
        sPos = limit;
        return sum;
    }

    private long scanBlockHeader(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanSemanticVersionField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanSemanticVersionField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(4, WT_LEN))) {
            sum += scanTimestampField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1, 2 -> sum += scanSemanticVersionField();
                case 3 -> sum += readVarint();
                case 4 -> sum += scanTimestampField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanSemanticVersion(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1, 2, 3 -> sum += readVarint();
                case 4, 5 -> { // pre/build: never set in this workload; byte length approximates char length
                    final int len = (int) readVarint();
                    sum += len;
                    sPos += len;
                }
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTimestamp(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1, 2 -> sum += readVarint();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /** {@code account} is a oneof (accountNum=3, alias=4): no fast path past the never-present shard/realm. */
    private long scanAccountId(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1, 2 -> sum += readVarint();
                case 3 -> sum += 3 + readVarint();
                case 4 -> {
                    final int len = (int) readVarint();
                    sum += 4 + bytesSum(sPos, len);
                    sPos += len;
                }
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTokenId(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 || field == 2 || field == 3) {
                sum += readVarint();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    private long scanNftId(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTokenIdField();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTokenIdField();
                case 2 -> sum += readVarint();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanAccountAmount(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanAccountIdField();
        }
        if (sAt(limit, tagByte(2, WT_VARINT))) {
            sum += unzigzag(readVarint());
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint(); // is_approval
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanAccountIdField();
                case 2 -> sum += unzigzag(readVarint());
                case 3 -> sum += readVarint(); // is_approval
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanAccountAmounts(final int limit) {
        long sum = 0;
        while (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanAccountAmountField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 && wireType == WT_LEN) {
                sum += scanAccountAmountField();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    private long scanTokenTransferList(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTokenIdField();
        }
        while (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanAccountAmountField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTokenIdField();
                case 2 -> sum += scanAccountAmountField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTransactionResult(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint(); // status (enum ordinal)
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanTimestampField();
        }
        if (sAt(limit, tagByte(6, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(7, WT_LEN))) {
            sum += scanAccountAmountsField();
        }
        while (sAt(limit, tagByte(8, WT_LEN))) {
            sum += scanTokenTransferListField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += readVarint(); // status (enum ordinal)
                case 2, 3 -> sum += scanTimestampField();
                case 6 -> sum += readVarint();
                case 7 -> sum += scanAccountAmountsField();
                case 8 -> sum += scanTokenTransferListField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanAccountTokenAssociation(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanAccountIdField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanTokenIdField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanAccountIdField();
                case 2 -> sum += scanTokenIdField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /** {@code keyChoice} is entirely a oneof (1, 5, 25): no leading plain field, so no fast path. */
    private long scanMapChangeKey(final int limit) {
        long sum = 0;
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += 1 + scanAccountIdField();
                case 5 -> sum += 5 + scanNftIdField();
                case 25 -> sum += 25 + scanAccountTokenAssociationField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTokenRelation(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTokenIdField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanAccountIdField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTokenIdField();
                case 2 -> sum += scanAccountIdField();
                case 3 -> sum += readVarint();
                case 4, 5, 6 -> sum += readVarint();
                case 7, 8 -> sum += scanTokenIdField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanNft(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanNftIdField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanAccountIdField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanNftIdField();
                case 2, 3 -> sum += scanAccountIdField();
                case 4 -> sum += scanTimestampField();
                case 5 -> {
                    final int len = (int) readVarint();
                    sum += bytesSum(sPos, len);
                    sPos += len;
                }
                case 6, 7 -> sum += scanNftIdField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /** {@code valueChoice} is entirely a oneof (5, 12): no leading plain field, so no fast path. */
    private long scanMapChangeValue(final int limit) {
        long sum = 0;
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 5 -> sum += 5 + scanNftField();
                case 12 -> sum += 12 + scanTokenRelationField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTransactionId(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTimestampField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanAccountIdField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTimestampField();
                case 2 -> sum += scanAccountIdField();
                case 3, 4 -> sum += readVarint();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTransactionReceiptEntry(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanTransactionIdField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += readVarint();
                case 2 -> sum += scanTransactionIdField();
                case 3 -> sum += readVarint();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTransactionReceiptEntries(final int limit) {
        long sum = 0;
        while (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTransactionReceiptEntryField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 && wireType == WT_LEN) {
                sum += scanTransactionReceiptEntryField();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** {@code change_operation} is a oneof: the fast path handles only the leading plain {@code state_id}. */
    private long scanStateChange(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint(); // state_id
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += readVarint(); // state_id
                case 4 -> sum += 4 + scanSingletonUpdateChangeField();
                case 5 -> sum += 5 + scanMapUpdateChangeField();
                case 6 -> sum += 6 + scanMapDeleteChangeField();
                case 7 -> sum += 7 + scanQueuePushChangeField();
                case 8 -> {
                    sum += 8;
                    skip(wireType); // QueuePopChange is empty: nothing more to sum
                }
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /** {@code key} and {@code value} are plain fields of {@code MapUpdateChange} itself (their content is a oneof). */
    private long scanMapUpdateChange(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanMapChangeKeyField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanMapChangeValueField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint(); // identical
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanMapChangeKeyField();
                case 2 -> sum += scanMapChangeValueField();
                case 3 -> sum += readVarint(); // identical
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanMapDeleteChange(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanMapChangeKeyField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                sum += scanMapChangeKeyField();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** {@code newValue} is a one-branch oneof (field 3): no leading plain field, so no fast path. */
    private long scanSingletonUpdateChange(final int limit) {
        long sum = 0;
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                final int len = (int) readVarint();
                final int inner = sPos + len;
                long value = 0;
                while (sPos < inner) {
                    final long t2 = readVarint();
                    if ((int) (t2 >>> 3) == 1) {
                        value = readVarint();
                    } else {
                        skip((int) (t2 & 7));
                    }
                }
                sPos = inner;
                sum += 3 + value;
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** {@code value} is a one-branch oneof (field 3): no leading plain field, so no fast path. */
    private long scanQueuePushChange(final int limit) {
        long sum = 0;
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                sum += 3 + scanTransactionReceiptEntriesField();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    private long scanStateChanges(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTimestampField();
        }
        while (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanStateChangeField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTimestampField();
                case 2 -> sum += scanStateChangeField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanBlockFooter(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        if (sAt(limit, tagByte(3, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 || field == 2 || field == 3) {
                final int len = (int) readVarint();
                sum += bytesSum(sPos, len);
                sPos += len;
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** {@code proof} is a oneof: the fast path handles only the leading plain {@code block}. */
    private long scanBlockProof(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += readVarint();
                case 2 -> sum += 2 + scanTssSignedBlockProofField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanTssSignedBlockProof(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) readVarint();
                sum += bytesSum(sPos, len);
                sPos += len;
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    // ----------------------------------------------------------------------------------------------- scan:
    // transactions

    /**
     * Parse an encoded {@code SignedTransaction} and its {@code bodyBytes} as a {@code TransactionBody}, in place
     * from the same array, and sum every field of both.
     */
    public long scanTransaction(final byte[] buf, final int offset, final int length) {
        sBuf = buf;
        sPos = offset;
        final int limit = offset + length;
        long sum = 0;
        int bodyOffset = -1;
        int bodyLength = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            bodyOffset = sPos;
            bodyLength = len;
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanSignatureMapField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> {
                    final int len = (int) readVarint();
                    bodyOffset = sPos;
                    bodyLength = len;
                    sum += bytesSum(sPos, len);
                    sPos += len;
                }
                case 2 -> sum += scanSignatureMapField();
                case 3 -> sum += readVarint();
                default -> skip(wireType);
            }
        }
        if (bodyOffset >= 0) {
            sBuf = buf;
            sPos = bodyOffset;
            sum += scanTransactionBody(bodyOffset + bodyLength);
        }
        return sum;
    }

    private long scanSignatureMap(final int limit) {
        long sum = 0;
        while (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanSignaturePairField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                sum += scanSignaturePairField();
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    /** {@code signature} is a oneof (3, 6): the fast path handles only the leading plain {@code pubKeyPrefix}. */
    private long scanSignaturePair(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> {
                    final int len = (int) readVarint();
                    sum += bytesSum(sPos, len);
                    sPos += len;
                }
                case 3, 6 -> {
                    final int len = (int) readVarint();
                    sum += field + bytesSum(sPos, len);
                    sPos += len;
                }
                default -> skip(wireType);
            }
        }
        return sum;
    }

    /** {@code data} is a oneof (14, 74): the fast path handles only the leading plain fields. */
    private long scanTransactionBody(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanTransactionIdField();
        }
        if (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanAccountIdField();
        }
        if (sAt(limit, tagByte(3, WT_VARINT))) {
            sum += readVarint();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanTransactionIdField();
                case 2 -> sum += scanAccountIdField();
                case 3 -> sum += readVarint();
                case 14 -> sum += 14 + scanCryptoTransferField();
                case 74 -> sum += 74 + scanAtomicBatchField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanCryptoTransfer(final int limit) {
        long sum = 0;
        if (sAt(limit, tagByte(1, WT_LEN))) {
            sum += scanAccountAmountsField();
        }
        while (sAt(limit, tagByte(2, WT_LEN))) {
            sum += scanTokenTransferListField();
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> sum += scanAccountAmountsField();
                case 2 -> sum += scanTokenTransferListField();
                default -> skip(wireType);
            }
        }
        return sum;
    }

    private long scanAtomicBatch(final int limit) {
        long sum = 0;
        while (sAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) readVarint();
            sum += bytesSum(sPos, len);
            sPos += len;
        }
        while (sPos < limit) {
            final long tag = readVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) readVarint();
                sum += bytesSum(sPos, len);
                sPos += len;
            } else {
                skip(wireType);
            }
        }
        return sum;
    }

    // ================================================================================================ DECODE: model

    /** A view of bytes in a caller-owned array: no copy, valid as long as the array is. */
    public record Range(byte[] array, int offset, int length) {
        long sum() {
            return length == 0 ? 0 : length + array[offset] + array[offset + length - 1];
        }
    }

    public record RTimestamp(long seconds, int nanos) {
        long sum() {
            return seconds + nanos;
        }
    }

    public record RAccountAmount(long accountNum, long amount) {
        long sum() {
            return 3 + accountNum + amount;
        }
    }

    public record RTokenTransferList(long tokenNum, RAccountAmount[] transfers) {
        long sum() {
            long s = tokenNum;
            for (final RAccountAmount a : transfers) {
                s += a.sum();
            }
            return s;
        }
    }

    public record RTransactionResult(
            int status,
            RTimestamp consensusTimestamp,
            long fee,
            RAccountAmount[] transferList,
            RTokenTransferList[] tokenTransferLists)
            implements RBlockItem {
        public long sum() {
            long s = 5 + status + consensusTimestamp.sum() + fee;
            for (final RAccountAmount a : transferList) {
                s += a.sum();
            }
            for (final RTokenTransferList t : tokenTransferLists) {
                s += t.sum();
            }
            return s;
        }
    }

    public sealed interface RChange {
        long sum();
    }

    public record RTokenRelChange(long account, long token, long balance, boolean autoAssoc) implements RChange {
        public long sum() {
            // StateChange.map_update=5; key=MapChangeKey{token_relationship_key=25}; value=MapChangeValue{
            // token_relation_value=12}; MapUpdateChange.key/value are plain fields, contributing nothing themselves.
            return 5 + (25 + (3 + account) + token) + (12 + token + (3 + account) + balance + (autoAssoc ? 1 : 0));
        }
    }

    public record RAccountChange(long account, long balance) implements RChange {
        public long sum() {
            return 5 + (1 + (3 + account)) + (12 + balance);
        }
    }

    public record RNftFull(long token, long serial, long owner, long spender, RTimestamp mintTime) implements RChange {
        public long sum() {
            return 5 + (5 + (token + serial)) + (5 + ((token + serial) + (3 + owner) + (3 + spender) + mintTime.sum()));
        }
    }

    public record RNftPrivate(long token, long serial, long owner, RTimestamp mintTime) implements RChange {
        public long sum() {
            return 5 + (5 + (token + serial)) + (5 + ((3 + owner) + mintTime.sum()));
        }
    }

    public record RNftDelete(long token, long serial) implements RChange {
        public long sum() {
            return 6 + (5 + (token + serial));
        }
    }

    public record RSingletonChange(long entityNumber) implements RChange {
        public long sum() {
            return 4 + (3 + entityNumber);
        }
    }

    public record RReceiptPushChange(long nodeId, RTimestamp validStart, long payer, int status) implements RChange {
        public long sum() {
            return 7 + 3 + (nodeId + (validStart.sum() + (3 + payer)) + status);
        }
    }

    public record RQueuePopChange() implements RChange {
        public long sum() {
            return 8;
        }
    }

    public record RStateChange(int stateId, RChange change) {
        long sum() {
            return stateId + change.sum();
        }
    }

    public record RStateChanges(RTimestamp consensusTimestamp, RStateChange[] changes) implements RBlockItem {
        public long sum() {
            long s = 7 + consensusTimestamp.sum();
            for (final RStateChange c : changes) {
                s += c.sum();
            }
            return s;
        }
    }

    public sealed interface RBlockItem {
        long sum();
    }

    public record RBlockHeader(long number, RTimestamp blockTimestamp) implements RBlockItem {
        public long sum() {
            // kind(1) + semanticVersion(hapiProtoVersion)=66 + semanticVersion(softwareVersion)=66 + number
            // + timestamp + hashAlgorithm (SHA2_384 == 0, the default)
            return 1 + 66 + 66 + number + blockTimestamp.sum();
        }
    }

    public record RSignedTransactionItem(Range bytes) implements RBlockItem {
        public long sum() {
            return 4 + bytes.sum();
        }
    }

    public record RBlockFooter(
            Range previousBlockRootHash, Range rootHashOfAllBlockHashesTree, Range startOfBlockStateRootHash)
            implements RBlockItem {
        public long sum() {
            return 12
                    + previousBlockRootHash.sum()
                    + rootHashOfAllBlockHashesTree.sum()
                    + startOfBlockStateRootHash.sum();
        }
    }

    public record RBlockProof(long block, Range blockSignature) implements RBlockItem {
        public long sum() {
            return 9 + block + 2 + blockSignature.sum();
        }
    }

    public record RBlock(List<RBlockItem> items) {
        public long sum() {
            long s = 0;
            for (final RBlockItem item : items) {
                s += item.sum();
            }
            return s;
        }
    }

    public record RSignaturePair(Range pubKeyPrefix, int kind, Range signature) {
        long sum() {
            return pubKeyPrefix.sum() + kind + signature.sum();
        }
    }

    public record RSignedTx(Range bodyBytes, RSignaturePair[] sigPairs, RTransactionBody body) {
        public long sum() {
            long s = bodyBytes.sum();
            for (final RSignaturePair p : sigPairs) {
                s += p.sum();
            }
            return s + body.sum();
        }
    }

    public record RTransactionId(RTimestamp validStart, long accountNum) {
        long sum() {
            return validStart.sum() + 3 + accountNum;
        }
    }

    public sealed interface RTxData {
        long sum();
    }

    public record RCryptoTransfer(RAccountAmount[] transfers, RTokenTransferList[] tokenTransfers) implements RTxData {
        public long sum() {
            long s = 0;
            for (final RAccountAmount a : transfers) {
                s += a.sum();
            }
            for (final RTokenTransferList t : tokenTransfers) {
                s += t.sum();
            }
            return 14 + s;
        }
    }

    public record RAtomicBatch(Range[] transactions) implements RTxData {
        public long sum() {
            long s = 0;
            for (final Range r : transactions) {
                s += r.sum();
            }
            return 74 + s;
        }
    }

    public record RTransactionBody(RTransactionId transactionId, long nodeAccountNum, long fee, RTxData data) {
        long sum() {
            return transactionId.sum() + 3 + nodeAccountNum + fee + data.sum();
        }
    }

    /** Sum of a decoded block, the same as {@link #scanBlock}. */
    public static long scan(final RBlock block) {
        return block.sum();
    }

    /** Sum of a decoded transaction (signed transaction + body), the same as {@link #scanTransaction}. */
    public static long scan(final RSignedTx tx) {
        return tx.sum();
    }

    // ================================================================================================= DECODE: parse

    private static final RTimestamp ZERO_TIMESTAMP = new RTimestamp(0, 0);

    private byte[] dBuf;
    private int dPos;

    /** If the next byte before {@code limit} is {@code tag}, consume it and return true (the in-order fast path). */
    private boolean dAt(final int limit, final byte tag) {
        final int q = dPos;
        if (q < limit && dBuf[q] == tag) {
            dPos = q + 1;
            return true;
        }
        return false;
    }

    private long dVarint() {
        final byte b = dBuf[dPos];
        if (b >= 0) {
            dPos++;
            return b;
        }
        return dVarintWide();
    }

    /** PBJ's XOR-chain varint reader, as {@link #sVarintWide()} but over the decode position. */
    private long dVarintWide() {
        final byte[] a = dBuf;
        int p = dPos;
        int vi = a[p++];
        long result;
        if ((vi ^= a[p++] << 7) < 0) {
            vi ^= (~0 << 7);
            result = vi;
        } else if ((vi ^= a[p++] << 14) >= 0) {
            vi ^= (~0 << 7) ^ (~0 << 14);
            result = vi;
        } else if ((vi ^= a[p++] << 21) < 0) {
            vi ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            result = vi;
        } else {
            long vl = vi;
            if ((vl ^= (long) a[p++] << 28) >= 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((vl ^= (long) a[p++] << 35) < 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((vl ^= (long) a[p++] << 42) >= 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((vl ^= (long) a[p++] << 49) < 0L) {
                vl ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            } else if ((vl ^= (long) a[p++] << 56) >= 0L) {
                vl ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56);
            } else {
                vl ^= (long) a[p++] << 63;
                vl ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56)
                        ^ (~0L << 63);
            }
            result = vl;
        }
        dPos = p;
        return result;
    }

    private void dSkip(final int wireType) {
        switch (wireType) {
            case WT_VARINT -> dVarint();
            case WT_I64 -> dPos += 8;
            case WT_LEN -> {
                final int len = (int) dVarint();
                dPos += len;
            }
            case WT_I32 -> dPos += 4;
            default -> throw new IllegalStateException("Unknown wire type " + wireType);
        }
    }

    private Range dRange(final int len) {
        final Range r = new Range(dBuf, dPos, len);
        dPos += len;
        return r;
    }

    /** Decode an encoded {@code Block} into a lightweight object model. */
    public RBlock decodeBlock(final byte[] buf, final int offset, final int length) {
        dBuf = buf;
        dPos = offset;
        final int limit = offset + length;
        final List<RBlockItem> items = new ArrayList<>();
        while (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            items.add(decodeItemContent(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 && wireType == WT_LEN) {
                final int len = (int) dVarint();
                items.add(decodeItemContent(dPos + len));
            } else {
                dSkip(wireType);
            }
        }
        return new RBlock(items);
    }

    /** Decode one encoded {@code BlockItem}. */
    public RBlockItem decodeItem(final byte[] buf, final int offset, final int length) {
        dBuf = buf;
        dPos = offset;
        return decodeItemContent(offset + length);
    }

    /** The {@code item} oneof: every branch stays in the general loop, so there is no in-order fast path here. */
    private RBlockItem decodeItemContent(final int limit) {
        RBlockItem result = null;
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> {
                    final int len = (int) dVarint();
                    result = decodeBlockHeader(dPos + len);
                }
                case 4 -> {
                    final int len = (int) dVarint();
                    result = new RSignedTransactionItem(dRange(len));
                }
                case 5 -> {
                    final int len = (int) dVarint();
                    result = decodeTransactionResult(dPos + len);
                }
                case 7 -> {
                    final int len = (int) dVarint();
                    result = decodeStateChanges(dPos + len);
                }
                case 9 -> {
                    final int len = (int) dVarint();
                    result = decodeBlockProof(dPos + len);
                }
                case 12 -> {
                    final int len = (int) dVarint();
                    result = decodeBlockFooter(dPos + len);
                }
                default -> dSkip(wireType);
            }
        }
        return result;
    }

    private RTimestamp decodeTimestamp(final int limit) {
        long seconds = 0;
        int nanos = 0;
        if (dAt(limit, tagByte(1, WT_VARINT))) {
            seconds = dVarint();
        }
        if (dAt(limit, tagByte(2, WT_VARINT))) {
            nanos = (int) dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                seconds = dVarint();
            } else if (field == 2) {
                nanos = (int) dVarint();
            } else {
                dSkip(wireType);
            }
        }
        return new RTimestamp(seconds, nanos);
    }

    /** {@code account} is a oneof (accountNum=3, alias=4): no fast path past the never-present shard/realm. */
    private long decodeAccountId(final int limit) {
        long accountNum = 0;
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                accountNum = dVarint();
            } else {
                dSkip(wireType);
            }
        }
        return accountNum;
    }

    private long decodeTokenId(final int limit) {
        long tokenNum = 0;
        if (dAt(limit, tagByte(3, WT_VARINT))) {
            tokenNum = dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                tokenNum = dVarint();
            } else {
                dSkip(wireType);
            }
        }
        return tokenNum;
    }

    private RBlockHeader decodeBlockHeader(final int limit) {
        long number = 0;
        RTimestamp timestamp = ZERO_TIMESTAMP;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            dSkip(WT_LEN); // version constant, not stored
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            dSkip(WT_LEN); // version constant, not stored
        }
        if (dAt(limit, tagByte(3, WT_VARINT))) {
            number = dVarint();
        }
        if (dAt(limit, tagByte(4, WT_LEN))) {
            final int len = (int) dVarint();
            timestamp = decodeTimestamp(dPos + len);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1, 2 -> dSkip(wireType); // version constants, not stored
                case 3 -> number = dVarint();
                case 4 -> {
                    final int len = (int) dVarint();
                    timestamp = decodeTimestamp(dPos + len);
                }
                default -> dSkip(wireType);
            }
        }
        return new RBlockHeader(number, timestamp);
    }

    private RBlockFooter decodeBlockFooter(final int limit) {
        Range a = new Range(dBuf, 0, 0);
        Range b = a;
        Range c = a;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            a = dRange((int) dVarint());
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            b = dRange((int) dVarint());
        }
        if (dAt(limit, tagByte(3, WT_LEN))) {
            c = dRange((int) dVarint());
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1 || field == 2 || field == 3) {
                final int len = (int) dVarint();
                final Range r = dRange(len);
                if (field == 1) {
                    a = r;
                } else if (field == 2) {
                    b = r;
                } else {
                    c = r;
                }
            } else {
                dSkip(wireType);
            }
        }
        return new RBlockFooter(a, b, c);
    }

    /** {@code proof} is a oneof: the fast path handles only the leading plain {@code block}. */
    private RBlockProof decodeBlockProof(final int limit) {
        long block = 0;
        Range signature = new Range(dBuf, 0, 0);
        if (dAt(limit, tagByte(1, WT_VARINT))) {
            block = dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                block = dVarint();
            } else if (field == 2) {
                final int len = (int) dVarint();
                signature = decodeTssSignedBlockProof(dPos + len);
            } else {
                dSkip(wireType);
            }
        }
        return new RBlockProof(block, signature);
    }

    private Range decodeTssSignedBlockProof(final int limit) {
        Range signature = new Range(dBuf, 0, 0);
        if (dAt(limit, tagByte(1, WT_LEN))) {
            signature = dRange((int) dVarint());
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                signature = dRange(len);
            } else {
                dSkip(wireType);
            }
        }
        return signature;
    }

    private RAccountAmount decodeAccountAmount(final int limit) {
        long accountNum = 0;
        long amount = 0;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            accountNum = decodeAccountId(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_VARINT))) {
            amount = unzigzag(dVarint());
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                accountNum = decodeAccountId(dPos + len);
            } else if (field == 2) {
                amount = unzigzag(dVarint());
            } else {
                dSkip(wireType);
            }
        }
        return new RAccountAmount(accountNum, amount);
    }

    private List<RAccountAmount> decodeAccountAmounts(final int limit) {
        final List<RAccountAmount> list = new ArrayList<>();
        while (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            list.add(decodeAccountAmount(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                list.add(decodeAccountAmount(dPos + len));
            } else {
                dSkip(wireType);
            }
        }
        return list;
    }

    private RTokenTransferList decodeTokenTransferList(final int limit) {
        long tokenNum = 0;
        final List<RAccountAmount> transfers = new ArrayList<>();
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            tokenNum = decodeTokenId(dPos + len);
        }
        while (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            transfers.add(decodeAccountAmount(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                tokenNum = decodeTokenId(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                transfers.add(decodeAccountAmount(dPos + len));
            } else {
                dSkip(wireType);
            }
        }
        return new RTokenTransferList(tokenNum, transfers.toArray(RAccountAmount[]::new));
    }

    private RTransactionResult decodeTransactionResult(final int limit) {
        int status = 0;
        RTimestamp timestamp = ZERO_TIMESTAMP;
        long fee = 0;
        List<RAccountAmount> transferList = List.of();
        final List<RTokenTransferList> tokenTransferLists = new ArrayList<>();
        if (dAt(limit, tagByte(1, WT_VARINT))) {
            status = (int) dVarint();
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            timestamp = decodeTimestamp(dPos + len);
        }
        if (dAt(limit, tagByte(6, WT_VARINT))) {
            fee = dVarint();
        }
        if (dAt(limit, tagByte(7, WT_LEN))) {
            final int len = (int) dVarint();
            transferList = decodeAccountAmounts(dPos + len);
        }
        while (dAt(limit, tagByte(8, WT_LEN))) {
            final int len = (int) dVarint();
            tokenTransferLists.add(decodeTokenTransferList(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> status = (int) dVarint();
                case 2 -> {
                    final int len = (int) dVarint();
                    timestamp = decodeTimestamp(dPos + len);
                }
                case 6 -> fee = dVarint();
                case 7 -> {
                    final int len = (int) dVarint();
                    transferList = decodeAccountAmounts(dPos + len);
                }
                case 8 -> {
                    final int len = (int) dVarint();
                    tokenTransferLists.add(decodeTokenTransferList(dPos + len));
                }
                default -> dSkip(wireType);
            }
        }
        return new RTransactionResult(
                status,
                timestamp,
                fee,
                transferList.toArray(RAccountAmount[]::new),
                tokenTransferLists.toArray(RTokenTransferList[]::new));
    }

    private long[] decodeAccountTokenAssociation(final int limit) {
        long account = 0;
        long token = 0;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            account = decodeAccountId(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            token = decodeTokenId(dPos + len);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                account = decodeAccountId(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                token = decodeTokenId(dPos + len);
            } else {
                dSkip(wireType);
            }
        }
        return new long[] {account, token};
    }

    private long[] decodeNftId(final int limit) {
        long token = 0;
        long serial = 0;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            token = decodeTokenId(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_VARINT))) {
            serial = dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                token = decodeTokenId(dPos + len);
            } else if (field == 2) {
                serial = dVarint();
            } else {
                dSkip(wireType);
            }
        }
        return new long[] {token, serial};
    }

    /** {@code change_operation} is a oneof: the fast path handles only the leading plain {@code state_id}. */
    private RStateChange decodeStateChange(final int limit) {
        int stateId = 0;
        RChange change = new RQueuePopChange();
        if (dAt(limit, tagByte(1, WT_VARINT))) {
            stateId = (int) dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> stateId = (int) dVarint();
                case 4 -> {
                    final int len = (int) dVarint();
                    change = decodeSingletonUpdateChange(dPos + len);
                }
                case 5 -> {
                    final int len = (int) dVarint();
                    change = decodeMapUpdateChange(dPos + len);
                }
                case 6 -> {
                    final int len = (int) dVarint();
                    change = decodeMapDeleteChange(dPos + len);
                }
                case 7 -> {
                    final int len = (int) dVarint();
                    change = decodeQueuePushChange(dPos + len);
                }
                case 8 -> {
                    final int len = (int) dVarint();
                    dPos += len;
                    change = new RQueuePopChange();
                }
                default -> dSkip(wireType);
            }
        }
        return new RStateChange(stateId, change);
    }

    /** {@code newValue} is a one-branch oneof (field 3): no leading plain field, so no fast path. */
    private RChange decodeSingletonUpdateChange(final int limit) {
        long value = 0;
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                final int len = (int) dVarint();
                final int inner = dPos + len;
                while (dPos < inner) {
                    final long t2 = dVarint();
                    if ((int) (t2 >>> 3) == 1) {
                        value = dVarint();
                    } else {
                        dSkip((int) (t2 & 7));
                    }
                }
            } else {
                dSkip(wireType);
            }
        }
        return new RSingletonChange(value);
    }

    private RChange decodeMapDeleteChange(final int limit) {
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            final long[] nft = decodeMapChangeKeyAsNft(dPos + len);
            return new RNftDelete(nft[0], nft[1]);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                final int keyLimit = dPos + len;
                final long[] nft = decodeMapChangeKeyAsNft(keyLimit);
                return new RNftDelete(nft[0], nft[1]);
            }
            dSkip(wireType);
        }
        return new RNftDelete(0, 0);
    }

    /** Decode a {@code MapChangeKey} expected to be the {@code nft_id_key} branch, returning [token, serial]. */
    private long[] decodeMapChangeKeyAsNft(final int limit) {
        long[] nft = {0, 0};
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 5) {
                final int len = (int) dVarint();
                nft = decodeNftId(dPos + len);
            } else {
                dSkip(wireType);
            }
        }
        return nft;
    }

    // scratch for decodeMapUpdateChange, set by decodeMapChangeKeyInto/decodeMapChangeValueInto and read immediately
    // after: avoids both a per-call allocation and duplicating the (oneof-dispatching) content loops in a fast path
    private int muKeyKind;
    private long muKeyAccount;
    private long muKeyToken;
    private long muKeySerial;
    private int muValueKind;
    private long muBalance;
    private boolean muAutoAssoc;
    private long muNftOwner;
    private long muNftSpender;
    private RTimestamp muMintTime;
    private boolean muNftHasId;

    /** {@code MapChangeKey}'s branches (1, 5, 25) are a oneof: a plain tag-then-switch loop, no fast path. */
    private void decodeMapChangeKeyInto(final int keyLimit) {
        while (dPos < keyLimit) {
            final long kt = dVarint();
            final int kf = (int) (kt >>> 3);
            final int kw = (int) (kt & 7);
            if (kf == 1) {
                muKeyKind = 1;
                final int l2 = (int) dVarint();
                muKeyAccount = decodeAccountId(dPos + l2);
            } else if (kf == 5) {
                muKeyKind = 5;
                final int l2 = (int) dVarint();
                final long[] nft = decodeNftId(dPos + l2);
                muKeyToken = nft[0];
                muKeySerial = nft[1];
            } else if (kf == 25) {
                muKeyKind = 25;
                final int l2 = (int) dVarint();
                final long[] assoc = decodeAccountTokenAssociation(dPos + l2);
                muKeyAccount = assoc[0];
                muKeyToken = assoc[1];
            } else {
                dSkip(kw);
            }
        }
    }

    /** {@code MapChangeValue}'s branches (5, 12) are a oneof: a plain tag-then-switch loop, no fast path. */
    private void decodeMapChangeValueInto(final int valueLimit) {
        while (dPos < valueLimit) {
            final long vt = dVarint();
            final int vf = (int) (vt >>> 3);
            final int vw = (int) (vt & 7);
            if (vf == 12) {
                muValueKind = 12;
                final int l2 = (int) dVarint();
                final int trLimit = dPos + l2;
                while (dPos < trLimit) {
                    final long tt = dVarint();
                    final int tf = (int) (tt >>> 3);
                    final int tw = (int) (tt & 7);
                    if (tf == 2) {
                        final int l3 = (int) dVarint();
                        muKeyAccount = decodeAccountId(dPos + l3);
                    } else if (tf == 3) {
                        muBalance = dVarint();
                    } else if (tf == 6) {
                        muAutoAssoc = dVarint() != 0;
                    } else {
                        dSkip(tw);
                    }
                }
            } else if (vf == 5) {
                muValueKind = 5;
                final int l2 = (int) dVarint();
                final int nftLimit = dPos + l2;
                while (dPos < nftLimit) {
                    final long nt = dVarint();
                    final int nf = (int) (nt >>> 3);
                    final int nw = (int) (nt & 7);
                    if (nf == 1) {
                        muNftHasId = true;
                        dSkip(nw);
                    } else if (nf == 2) {
                        final int l3 = (int) dVarint();
                        muNftOwner = decodeAccountId(dPos + l3);
                    } else if (nf == 3) {
                        final int l3 = (int) dVarint();
                        muNftSpender = decodeAccountId(dPos + l3);
                    } else if (nf == 4) {
                        final int l3 = (int) dVarint();
                        muMintTime = decodeTimestamp(dPos + l3);
                    } else {
                        dSkip(nw);
                    }
                }
            } else {
                dSkip(vw);
            }
        }
    }

    /** {@code key} and {@code value} are plain fields of {@code MapUpdateChange} itself (their content is a oneof). */
    private RChange decodeMapUpdateChange(final int limit) {
        muKeyKind = 0;
        muKeyAccount = 0;
        muKeyToken = 0;
        muKeySerial = 0;
        muValueKind = 0;
        muBalance = 0;
        muAutoAssoc = false;
        muNftOwner = 0;
        muNftSpender = 0;
        muMintTime = ZERO_TIMESTAMP;
        muNftHasId = false;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            decodeMapChangeKeyInto(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            decodeMapChangeValueInto(dPos + len);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                decodeMapChangeKeyInto(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                decodeMapChangeValueInto(dPos + len);
            } else {
                dSkip(wireType);
            }
        }
        if (muValueKind == 5) {
            return muNftHasId
                    ? new RNftFull(muKeyToken, muKeySerial, muNftOwner, muNftSpender, muMintTime)
                    : new RNftPrivate(muKeyToken, muKeySerial, muNftOwner, muMintTime);
        }
        // valueKind == 12 (token_relation_value): distinguish OP_TOKEN_REL (key = token_relationship_key, 25) from
        // OP_ACCOUNT (key = account_id_key, 1) by which MapChangeKey branch was present.
        return muKeyKind == 25
                ? new RTokenRelChange(muKeyAccount, muKeyToken, muBalance, muAutoAssoc)
                : new RAccountChange(muKeyAccount, muBalance);
    }

    /** {@code value} is a one-branch oneof (field 3): no leading plain field, so no fast path. */
    private RChange decodeQueuePushChange(final int limit) {
        long nodeId = 0;
        RTimestamp validStart = ZERO_TIMESTAMP;
        long payer = 0;
        int status = 0;
        while (dPos < limit) {
            final long et = dVarint();
            if ((int) (et >>> 3) != 3) {
                dSkip((int) (et & 7));
                continue;
            }
            final int len = (int) dVarint();
            final int entriesLimit = dPos + len;
            while (dPos < entriesLimit) {
                final long et2 = dVarint();
                if ((int) (et2 >>> 3) == 1) {
                    final int l2 = (int) dVarint();
                    final int entryLimit = dPos + l2;
                    while (dPos < entryLimit) {
                        final long rt = dVarint();
                        final int rf = (int) (rt >>> 3);
                        final int rw = (int) (rt & 7);
                        if (rf == 1) {
                            nodeId = dVarint();
                        } else if (rf == 2) {
                            final int l3 = (int) dVarint();
                            final int idLimit = dPos + l3;
                            while (dPos < idLimit) {
                                final long it = dVarint();
                                final int iff = (int) (it >>> 3);
                                final int iw = (int) (it & 7);
                                if (iff == 1) {
                                    final int l4 = (int) dVarint();
                                    validStart = decodeTimestamp(dPos + l4);
                                } else if (iff == 2) {
                                    final int l4 = (int) dVarint();
                                    payer = decodeAccountId(dPos + l4);
                                } else {
                                    dSkip(iw);
                                }
                            }
                        } else if (rf == 3) {
                            status = (int) dVarint();
                        } else {
                            dSkip(rw);
                        }
                    }
                } else {
                    dSkip((int) (et2 & 7));
                }
            }
        }
        return new RReceiptPushChange(nodeId, validStart, payer, status);
    }

    private RStateChanges decodeStateChanges(final int limit) {
        RTimestamp timestamp = ZERO_TIMESTAMP;
        final List<RStateChange> changes = new ArrayList<>();
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            timestamp = decodeTimestamp(dPos + len);
        }
        while (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            changes.add(decodeStateChange(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                timestamp = decodeTimestamp(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                changes.add(decodeStateChange(dPos + len));
            } else {
                dSkip(wireType);
            }
        }
        return new RStateChanges(timestamp, changes.toArray(RStateChange[]::new));
    }

    // ----------------------------------------------------------------------------------------------- decode:
    // transactions

    /** Decode an encoded {@code SignedTransaction} and its {@code bodyBytes} as a {@code TransactionBody}. */
    public RSignedTx decodeTransaction(final byte[] buf, final int offset, final int length) {
        dBuf = buf;
        dPos = offset;
        final int limit = offset + length;
        Range bodyBytes = new Range(buf, offset, 0);
        final List<RSignaturePair> pairs = new ArrayList<>();
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            bodyBytes = dRange(len);
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            decodeSignaturePairsInto(dPos + len, pairs);
        }
        if (dAt(limit, tagByte(3, WT_VARINT))) {
            dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> {
                    final int len = (int) dVarint();
                    bodyBytes = dRange(len);
                }
                case 2 -> {
                    final int len = (int) dVarint();
                    decodeSignaturePairsInto(dPos + len, pairs);
                }
                case 3 -> dVarint();
                default -> dSkip(wireType);
            }
        }
        final RTransactionBody body = decodeTransactionBody(bodyBytes);
        return new RSignedTx(bodyBytes, pairs.toArray(RSignaturePair[]::new), body);
    }

    private void decodeSignaturePairsInto(final int mapLimit, final List<RSignaturePair> pairs) {
        while (dAt(mapLimit, tagByte(1, WT_LEN))) {
            final int l2 = (int) dVarint();
            pairs.add(decodeSignaturePair(dPos + l2));
        }
        while (dPos < mapLimit) {
            final long pt = dVarint();
            if ((int) (pt >>> 3) == 1) {
                final int l2 = (int) dVarint();
                pairs.add(decodeSignaturePair(dPos + l2));
            } else {
                dSkip((int) (pt & 7));
            }
        }
    }

    /** {@code signature} is a oneof (3, 6): the fast path handles only the leading plain {@code pubKeyPrefix}. */
    private RSignaturePair decodeSignaturePair(final int limit) {
        Range prefix = new Range(dBuf, 0, 0);
        int kind = 0;
        Range signature = new Range(dBuf, 0, 0);
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            prefix = dRange(len);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                prefix = dRange(len);
            } else if (field == 3 || field == 6) {
                kind = field;
                final int len = (int) dVarint();
                signature = dRange(len);
            } else {
                dSkip(wireType);
            }
        }
        return new RSignaturePair(prefix, kind, signature);
    }

    /** {@code data} is a oneof (14, 74): the fast path handles only the leading plain fields. */
    private RTransactionBody decodeTransactionBody(final Range bodyBytes) {
        dBuf = bodyBytes.array();
        dPos = bodyBytes.offset();
        final int limit = bodyBytes.offset() + bodyBytes.length();
        RTransactionId transactionId = new RTransactionId(ZERO_TIMESTAMP, 0);
        long nodeAccountNum = 0;
        long fee = 0;
        RTxData data = new RCryptoTransfer(new RAccountAmount[0], new RTokenTransferList[0]);
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            transactionId = decodeTransactionId(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            nodeAccountNum = decodeAccountId(dPos + len);
        }
        if (dAt(limit, tagByte(3, WT_VARINT))) {
            fee = dVarint();
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            switch (field) {
                case 1 -> {
                    final int len = (int) dVarint();
                    transactionId = decodeTransactionId(dPos + len);
                }
                case 2 -> {
                    final int len = (int) dVarint();
                    nodeAccountNum = decodeAccountId(dPos + len);
                }
                case 3 -> fee = dVarint();
                case 14 -> {
                    final int len = (int) dVarint();
                    data = decodeCryptoTransfer(dPos + len);
                }
                case 74 -> {
                    final int len = (int) dVarint();
                    data = decodeAtomicBatch(dPos + len);
                }
                default -> dSkip(wireType);
            }
        }
        return new RTransactionBody(transactionId, nodeAccountNum, fee, data);
    }

    private RTransactionId decodeTransactionId(final int limit) {
        RTimestamp validStart = ZERO_TIMESTAMP;
        long accountNum = 0;
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            validStart = decodeTimestamp(dPos + len);
        }
        if (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            accountNum = decodeAccountId(dPos + len);
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                validStart = decodeTimestamp(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                accountNum = decodeAccountId(dPos + len);
            } else {
                dSkip(wireType);
            }
        }
        return new RTransactionId(validStart, accountNum);
    }

    private RCryptoTransfer decodeCryptoTransfer(final int limit) {
        List<RAccountAmount> transfers = List.of();
        final List<RTokenTransferList> tokenTransfers = new ArrayList<>();
        if (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            transfers = decodeAccountAmounts(dPos + len);
        }
        while (dAt(limit, tagByte(2, WT_LEN))) {
            final int len = (int) dVarint();
            tokenTransfers.add(decodeTokenTransferList(dPos + len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                transfers = decodeAccountAmounts(dPos + len);
            } else if (field == 2) {
                final int len = (int) dVarint();
                tokenTransfers.add(decodeTokenTransferList(dPos + len));
            } else {
                dSkip(wireType);
            }
        }
        return new RCryptoTransfer(
                transfers.toArray(RAccountAmount[]::new), tokenTransfers.toArray(RTokenTransferList[]::new));
    }

    private RAtomicBatch decodeAtomicBatch(final int limit) {
        final List<Range> transactions = new ArrayList<>();
        while (dAt(limit, tagByte(1, WT_LEN))) {
            final int len = (int) dVarint();
            transactions.add(dRange(len));
        }
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                transactions.add(dRange(len));
            } else {
                dSkip(wireType);
            }
        }
        return new RAtomicBatch(transactions.toArray(Range[]::new));
    }
}
