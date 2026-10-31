// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test.synthetic;

import java.util.ArrayList;
import java.util.List;

/**
 * A hand-written, zero-allocation (in steady state) protobuf codec for the subset of HAPI messages used by the
 * {@link SyntheticBlock} synthetic workload. This is the in-repo performance target: "what a hand-written codec
 * achieves" for the same work PBJ's generated code does. It is benchmark-only code, not shipped as part of the
 * library, and it intentionally skips the strict-mode validation a production parser would do; see "Correctness over
 * validation" below.
 *
 * <h2>Encoding strategy</h2>
 * Protobuf needs a nested message's length before its tag can be written, but {@link SyntheticBlock.Cursor} only
 * reads forward, once. For every nested message whose size cannot be computed directly from already-read values
 * (because it has a dynamic number of repeated elements: a transfer list, a token transfer list, or a block of state
 * changes) this codec writes the elements into a small reusable {@link ByteBuilder} scratch buffer first; once that
 * buffer holds the element's complete bytes, its length is just {@link ByteBuilder#length}, so the parent can write
 * the element's tag and length and then copy the bytes in. Everything else (identifiers, timestamps, amounts, and
 * every state-change operation, which all have a fixed shape once their discriminant is known) is sized with a small
 * closed-form formula and written directly, field by field, in ascending field order, with no buffering at all. Both
 * techniques produce exactly the same bytes as the forward (measure-then-write) and reverse generated writers.
 *
 * <h2>Scanning</h2>
 * {@link #scanItem}, {@link #scanBlock} and {@link #scanTransaction} are a conventional recursive-descent protobuf
 * parser reading directly from a {@code byte[]}: for every field read, decode the value and recurse into length-
 * delimited content with a nested call; for every field not in this subset, skip it by wire type. No objects are
 * allocated.
 *
 * <h2>Decoding into objects</h2>
 * {@link #decodeBlock} and {@link #decodeTransaction} build a lightweight hand-written object model for the same
 * subset (small records and sealed interfaces below), allocating only the objects the data actually needs: arrays
 * sized to the repeated field's count, one record per message. {@link #scan(RBlock)} and {@link #scan(RSignedTx)} sum
 * the decoded objects' fields, and must return the sums {@link ScanObjects} does.
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

    /** The size of a message field wrapping {@code contentSize} bytes, always written (oneof, or known present). */
    private static int sizeMsgField(final int fieldNumber, final int contentSize) {
        return tagSize(fieldNumber, WT_LEN) + varintSize(contentSize) + contentSize;
    }

    /** The size of a plain (non-oneof) varint-ish field, omitted when {@code value} is the default (zero). */
    private static int sizeVarintField(final int fieldNumber, final long value) {
        return value == 0 ? 0 : tagSize(fieldNumber, WT_VARINT) + varintSize(value);
    }

    private static int sizeBoolField(final int fieldNumber, final boolean value) {
        return value ? tagSize(fieldNumber, WT_VARINT) + 1 : 0;
    }

    // =================================================================================================== ByteBuilder

    /** A small reusable, growable, forward-appending byte buffer: a hand-written {@code ByteArrayOutputStream}. */
    static final class ByteBuilder {
        byte[] array;
        int length;

        ByteBuilder(final int initialCapacity) {
            array = new byte[initialCapacity];
        }

        void reset() {
            length = 0;
        }

        private void ensure(final int more) {
            if (length + more > array.length) {
                array = java.util.Arrays.copyOf(array, Math.max(array.length * 2, length + more));
            }
        }

        void varint(long value) {
            ensure(10);
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    array[length++] = (byte) value;
                    return;
                }
                array[length++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }

        void tag(final int fieldNumber, final int wireType) {
            varint(((long) fieldNumber << 3) | wireType);
        }

        void raw(final byte[] src, final int offset, final int len) {
            ensure(len);
            System.arraycopy(src, offset, array, length, len);
            length += len;
        }
    }

    /** Write {@code child}'s bytes as the content of a length-delimited field, always (oneof, or known present). */
    private static void appendMsgField(final ByteBuilder parent, final int fieldNumber, final ByteBuilder child) {
        parent.tag(fieldNumber, WT_LEN);
        parent.varint(child.length);
        parent.raw(child.array, 0, child.length);
    }

    private static void appendVarintField(final ByteBuilder b, final int fieldNumber, final long value) {
        if (value != 0) {
            b.tag(fieldNumber, WT_VARINT);
            b.varint(value);
        }
    }

    private static void appendBoolField(final ByteBuilder b, final int fieldNumber, final boolean value) {
        if (value) {
            b.tag(fieldNumber, WT_VARINT);
            b.varint(1);
        }
    }

    private static void appendBytesField(
            final ByteBuilder b, final int fieldNumber, final byte[] src, final int offset, final int length) {
        if (length != 0) {
            b.tag(fieldNumber, WT_LEN);
            b.varint(length);
            b.raw(src, offset, length);
        }
    }

    // ------------------------------------------------------------------------------------------- leaf message types

    /** {@code AccountID}: shard/realm are always 0 (mainnet); {@code accountNum} is a oneof member, always written. */
    private static int sizeAccountIdContent(final long accountNum) {
        return tagSize(3, WT_VARINT) + varintSize(accountNum);
    }

    private static void appendAccountIdField(final ByteBuilder b, final int fieldNumber, final long accountNum) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(sizeAccountIdContent(accountNum));
        b.tag(3, WT_VARINT);
        b.varint(accountNum);
    }

    /** {@code TokenID}: shard/realm always 0; {@code tokenNum} is a plain field, omitted only if ever 0. */
    private static int sizeTokenIdContent(final long tokenNum) {
        return sizeVarintField(3, tokenNum);
    }

    private static void appendTokenIdField(final ByteBuilder b, final int fieldNumber, final long tokenNum) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(sizeTokenIdContent(tokenNum));
        appendVarintField(b, 3, tokenNum);
    }

    private static int sizeTimestampContent(final long seconds, final int nanos) {
        return sizeVarintField(1, seconds) + sizeVarintField(2, nanos);
    }

    private static void appendTimestampField(
            final ByteBuilder b, final int fieldNumber, final long seconds, final int nanos) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(sizeTimestampContent(seconds, nanos));
        appendVarintField(b, 1, seconds);
        appendVarintField(b, 2, nanos);
    }

    private static int sizeNftIdContent(final long tokenNum, final long serial) {
        return sizeMsgField(1, sizeTokenIdContent(tokenNum)) + sizeVarintField(2, serial);
    }

    private static void appendNftIdField(
            final ByteBuilder b, final int fieldNumber, final long tokenNum, final long serial) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(sizeNftIdContent(tokenNum, serial));
        appendTokenIdField(b, 1, tokenNum);
        appendVarintField(b, 2, serial);
    }

    private static int sizeAccountAmountContent(final long accountNum, final long amount) {
        return sizeMsgField(1, sizeAccountIdContent(accountNum)) + sizeVarintField(2, zigzag(amount));
    }

    private static void appendAccountAmountField(
            final ByteBuilder b, final int fieldNumber, final long accountNum, final long amount) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(sizeAccountAmountContent(accountNum, amount));
        appendAccountIdField(b, 1, accountNum);
        appendVarintField(b, 2, zigzag(amount));
    }

    // ================================================================================================ ENCODE: items

    private static final byte[] SEMVER_CONTENT = {0x10, 0x42}; // SemanticVersion{minor=66}: tag(2,VARINT), 66

    private static void appendSemanticVersionConst(final ByteBuilder b, final int fieldNumber) {
        b.tag(fieldNumber, WT_LEN);
        b.varint(SEMVER_CONTENT.length);
        b.raw(SEMVER_CONTENT, 0, SEMVER_CONTENT.length);
    }

    /** Growable output of the last {@link #encodeItem} or {@link #encodeBlock} call. Reused; copy out before reuse. */
    private byte[] out = new byte[1 << 16];

    private int outPos;

    private final ByteBuilder content = new ByteBuilder(512);
    private final ByteBuilder pairsBuf = new ByteBuilder(64);
    private final ByteBuilder oneListBuf = new ByteBuilder(64);
    private final ByteBuilder listsBuf = new ByteBuilder(64);
    private final ByteBuilder changesBuf = new ByteBuilder(2048);
    private final ByteBuilder changeContentBuf = new ByteBuilder(96);
    private final ByteBuilder nestA = new ByteBuilder(64);
    private final ByteBuilder nestB = new ByteBuilder(64);
    private final ByteBuilder nestC = new ByteBuilder(64);

    /** The buffer written by {@link #encodeItem} and {@link #encodeBlock}, valid for {@code [0, length)}. */
    public byte[] buffer() {
        return out;
    }

    private void ensureOut(final int capacity) {
        if (out.length < capacity) {
            out = java.util.Arrays.copyOf(out, Math.max(capacity, out.length * 2));
        }
    }

    /**
     * Encode the next item from {@code c} as one {@code BlockItem} (the message body, not the outer {@code Block}
     * field tag), into {@link #buffer()}.
     *
     * @return the number of bytes written
     */
    public int encodeItem(final SyntheticBlock.Cursor c) {
        outPos = 0;
        writeItem(c);
        return outPos;
    }

    /**
     * Encode every item of {@code block} as a whole {@code Block} (repeated field 1), into {@link #buffer()}.
     *
     * @return the number of bytes written
     */
    public int encodeBlock(final SyntheticBlock block) {
        outPos = 0;
        final SyntheticBlock.Cursor c = block.cursor();
        for (int i = 0; i < block.items; i++) {
            final int start = outPos;
            // reserve room for the outer tag + a worst-case varint length, then shift the item down if it is smaller
            ensureOut(outPos + 1 + 10);
            outPos += 1 + 10;
            writeItem(c);
            final int itemLength = outPos - (start + 1 + 10);
            final int lengthSize = varintSize(itemLength);
            // shift the item bytes left so the tag + exact-size length sit immediately before them
            System.arraycopy(out, start + 1 + 10, out, start + 1 + lengthSize, itemLength);
            out[start] = 0x0A; // tag(1, LEN)
            int p = start + 1;
            long v = itemLength;
            while (true) {
                if ((v & ~0x7FL) == 0) {
                    out[p++] = (byte) v;
                    break;
                }
                out[p++] = (byte) ((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            outPos = start + 1 + lengthSize + itemLength;
        }
        return outPos;
    }

    private void writeItem(final SyntheticBlock.Cursor c) {
        content.reset();
        final int kind = c.i();
        final int fieldNumber =
                switch (kind) {
                    case SyntheticBlock.KIND_BLOCK_HEADER -> {
                        encodeBlockHeader(c, content);
                        yield 1;
                    }
                    case SyntheticBlock.KIND_SIGNED_TRANSACTION -> {
                        final int offset = c.i();
                        final int length = c.i();
                        ensureOut(outPos + tagSize(4, WT_LEN) + varintSize(length) + length);
                        outPos = appendTag(out, outPos, 4, WT_LEN);
                        outPos = appendVarint(out, outPos, length);
                        System.arraycopy(c.heap(), offset, out, outPos, length);
                        outPos += length;
                        yield 0; // already written directly; nothing more to do
                    }
                    case SyntheticBlock.KIND_TRANSACTION_RESULT -> {
                        encodeTransactionResult(c, content);
                        yield 5;
                    }
                    case SyntheticBlock.KIND_STATE_CHANGES -> {
                        encodeStateChanges(c, content);
                        yield 7;
                    }
                    case SyntheticBlock.KIND_BLOCK_FOOTER -> {
                        encodeBlockFooter(c, content);
                        yield 12;
                    }
                    case SyntheticBlock.KIND_BLOCK_PROOF -> {
                        encodeBlockProof(c, content);
                        yield 9;
                    }
                    default -> throw new IllegalStateException("Unknown item kind");
                };
        if (fieldNumber != 0) {
            ensureOut(outPos + tagSize(fieldNumber, WT_LEN) + varintSize(content.length) + content.length);
            outPos = appendTag(out, outPos, fieldNumber, WT_LEN);
            outPos = appendVarint(out, outPos, content.length);
            System.arraycopy(content.array, 0, out, outPos, content.length);
            outPos += content.length;
        }
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

    private static void encodeBlockHeader(final SyntheticBlock.Cursor c, final ByteBuilder content) {
        final long number = c.l();
        final long seconds = c.l();
        final int nanos = c.i();
        appendSemanticVersionConst(content, 1);
        appendSemanticVersionConst(content, 2);
        appendVarintField(content, 3, number);
        appendTimestampField(content, 4, seconds, nanos);
        // field 5 (hash_algorithm) is SHA2_384 == 0, the default: omitted
    }

    private static void encodeBlockFooter(final SyntheticBlock.Cursor c, final ByteBuilder content) {
        appendBytesField(content, 1, c.heap(), c.i(), c.i());
        appendBytesField(content, 2, c.heap(), c.i(), c.i());
        appendBytesField(content, 3, c.heap(), c.i(), c.i());
    }

    private static void encodeBlockProof(final SyntheticBlock.Cursor c, final ByteBuilder content) {
        final long block = c.l();
        final int offset = c.i();
        final int length = c.i();
        appendVarintField(content, 1, block);
        // signed_block_proof (field 2, oneof member, always written) wraps TssSignedBlockProof{block_signature=1}
        final int innerSize = length == 0 ? 0 : tagSize(1, WT_LEN) + varintSize(length) + length;
        content.tag(2, WT_LEN);
        content.varint(innerSize);
        if (length != 0) {
            content.tag(1, WT_LEN);
            content.varint(length);
            content.raw(c.heap(), offset, length);
        }
    }

    private void encodeTransactionResult(final SyntheticBlock.Cursor c, final ByteBuilder content) {
        final int status = c.i();
        final long seconds = c.l();
        final int nanos = c.i();
        final long fee = c.l();
        final int transfers = c.i();
        pairsBuf.reset();
        for (int i = 0; i < transfers; i++) {
            final long account = c.l();
            final long amount = c.l();
            appendAccountAmountField(pairsBuf, 1, account, amount);
        }
        appendVarintField(content, 1, status);
        appendTimestampField(content, 2, seconds, nanos);
        appendVarintField(content, 6, fee);
        if (transfers > 0) {
            appendMsgField(content, 7, pairsBuf);
        }
        final int lists = c.i();
        listsBuf.reset();
        for (int j = 0; j < lists; j++) {
            final long token = c.l();
            final int count = c.i();
            pairsBuf.reset();
            for (int k = 0; k < count; k++) {
                final long account = c.l();
                final long amount = c.l();
                appendAccountAmountField(pairsBuf, 2, account, amount);
            }
            oneListBuf.reset();
            appendTokenIdField(oneListBuf, 1, token);
            oneListBuf.raw(pairsBuf.array, 0, pairsBuf.length);
            appendMsgField(listsBuf, 8, oneListBuf);
        }
        content.raw(listsBuf.array, 0, listsBuf.length);
    }

    private void encodeStateChanges(final SyntheticBlock.Cursor c, final ByteBuilder content) {
        final long seconds = c.l();
        final int nanos = c.i();
        final int count = c.i();
        changesBuf.reset();
        for (int i = 0; i < count; i++) {
            final int stateId = c.i();
            final int op = c.i();
            changeContentBuf.reset();
            appendVarintField(changeContentBuf, 1, stateId);
            switch (op) {
                case SyntheticBlock.OP_TOKEN_REL -> appendTokenRelChange(c, changeContentBuf);
                case SyntheticBlock.OP_ACCOUNT -> appendAccountChange(c, changeContentBuf);
                case SyntheticBlock.OP_NFT -> appendNftChange(c, changeContentBuf, true);
                case SyntheticBlock.OP_NFT_PRIVATE -> appendNftChange(c, changeContentBuf, false);
                case SyntheticBlock.OP_NFT_DELETE -> appendNftDeleteChange(c, changeContentBuf);
                case SyntheticBlock.OP_SINGLETON -> appendSingletonChange(c, changeContentBuf);
                case SyntheticBlock.OP_RECEIPT_PUSH -> appendReceiptPushChange(c, changeContentBuf);
                default -> appendMsgField(changeContentBuf, 8, nestAEmpty()); // OP_QUEUE_POP
            }
            appendMsgField(changesBuf, 2, changeContentBuf);
        }
        appendTimestampField(content, 1, seconds, nanos);
        content.raw(changesBuf.array, 0, changesBuf.length);
    }

    private ByteBuilder nestAEmpty() {
        nestA.reset();
        return nestA;
    }

    private void appendTokenRelChange(final SyntheticBlock.Cursor c, final ByteBuilder out) {
        final long account = c.l();
        final long token = c.l();
        final long balance = c.l();
        final boolean auto = c.l() != 0;
        nestC.reset(); // MapUpdateChange content
        nestA.reset();
        appendAccountIdField(nestA, 1, account);
        appendTokenIdField(nestA, 2, token);
        nestB.reset();
        appendMsgField(nestB, 25, nestA); // MapChangeKey{token_relationship_key=25}
        appendMsgField(nestC, 1, nestB); // key field
        nestA.reset();
        appendTokenIdField(nestA, 1, token);
        appendAccountIdField(nestA, 2, account);
        appendVarintField(nestA, 3, balance);
        appendBoolField(nestA, 6, auto);
        nestB.reset();
        appendMsgField(nestB, 12, nestA); // MapChangeValue{token_relation_value=12}
        appendMsgField(nestC, 2, nestB); // value field
        appendMsgField(out, 5, nestC); // map_update field of StateChange
    }

    private void appendAccountChange(final SyntheticBlock.Cursor c, final ByteBuilder out) {
        final long account = c.l();
        final long balance = c.l();
        nestC.reset();
        nestB.reset();
        appendAccountIdField(nestB, 1, account); // MapChangeKey{account_id_key=1}
        appendMsgField(nestC, 1, nestB);
        nestA.reset();
        appendVarintField(nestA, 3, balance); // TokenRelation{balance=3} only
        nestB.reset();
        appendMsgField(nestB, 12, nestA); // MapChangeValue{token_relation_value=12}
        appendMsgField(nestC, 2, nestB);
        appendMsgField(out, 5, nestC);
    }

    private void appendNftChange(final SyntheticBlock.Cursor c, final ByteBuilder out, final boolean full) {
        final long token = c.l();
        final long serial = c.l();
        final long owner = c.l();
        final long spender = full ? c.l() : 0;
        final long mintSeconds = c.l();
        final int mintNanos = c.i();
        nestC.reset();
        nestB.reset();
        appendNftIdField(nestB, 5, token, serial); // MapChangeKey{nft_id_key=5}
        appendMsgField(nestC, 1, nestB);
        nestA.reset();
        if (full) {
            appendNftIdField(nestA, 1, token, serial);
        }
        appendAccountIdField(nestA, 2, owner);
        if (full) {
            appendAccountIdField(nestA, 3, spender);
        }
        appendTimestampField(nestA, 4, mintSeconds, mintNanos);
        nestB.reset();
        appendMsgField(nestB, 5, nestA); // MapChangeValue{nft_value=5}
        appendMsgField(nestC, 2, nestB);
        appendMsgField(out, 5, nestC);
    }

    private void appendNftDeleteChange(final SyntheticBlock.Cursor c, final ByteBuilder out) {
        final long token = c.l();
        final long serial = c.l();
        nestB.reset();
        appendNftIdField(nestB, 5, token, serial); // MapChangeKey{nft_id_key=5}
        nestC.reset();
        appendMsgField(nestC, 1, nestB); // MapDeleteChange{key=1}
        appendMsgField(out, 6, nestC); // map_delete field of StateChange
    }

    private void appendSingletonChange(final SyntheticBlock.Cursor c, final ByteBuilder out) {
        final long value = c.l();
        nestA.reset();
        appendVarintField(nestA, 1, value); // UInt64Value{value=1}
        nestB.reset();
        appendMsgField(nestB, 3, nestA); // SingletonUpdateChange{entity_number_value=3}, oneof: always written
        appendMsgField(out, 4, nestB); // singleton_update field of StateChange
    }

    private void appendReceiptPushChange(final SyntheticBlock.Cursor c, final ByteBuilder out) {
        final long node = c.l();
        final long validSeconds = c.l();
        final int validNanos = c.i();
        final long payer = c.l();
        final int status = c.i();
        nestA.reset();
        appendTimestampField(nestA, 1, validSeconds, validNanos);
        appendAccountIdField(nestA, 2, payer);
        // nestA = TransactionID content
        nestB.reset();
        appendVarintField(nestB, 1, node);
        appendMsgField(nestB, 2, nestA);
        appendVarintField(nestB, 3, status);
        // nestB = TransactionReceiptEntry content
        nestA.reset();
        appendMsgField(nestA, 1, nestB); // entries=1 (single element)
        // nestA = TransactionReceiptEntries content
        nestB.reset();
        appendMsgField(nestB, 3, nestA); // transaction_receipt_entries_element=3 of QueuePushChange, always written
        // nestB = QueuePushChange content
        appendMsgField(out, 7, nestB); // queue_push field of StateChange
    }

    // ==================================================================================================== SCAN

    private byte[] sBuf;
    private int sPos;

    private long readVarint() {
        long result = 0;
        int shift = 0;
        byte b;
        do {
            b = sBuf[sPos++];
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while (b < 0);
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

    private long scanAccountId(final int limit) {
        long sum = 0;
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

    private long scanStateChange(final int limit) {
        long sum = 0;
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

    private long scanMapUpdateChange(final int limit) {
        long sum = 0;
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

    private long scanBlockProof(final int limit) {
        long sum = 0;
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

    private long scanSignaturePair(final int limit) {
        long sum = 0;
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

    private long scanTransactionBody(final int limit) {
        long sum = 0;
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

    private byte[] dBuf;
    private int dPos;

    private long dVarint() {
        long result = 0;
        int shift = 0;
        byte b;
        do {
            b = dBuf[dPos++];
            result |= (long) (b & 0x7F) << shift;
            shift += 7;
        } while (b < 0);
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
        RTimestamp timestamp = new RTimestamp(0, 0);
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

    private RBlockProof decodeBlockProof(final int limit) {
        long block = 0;
        Range signature = new Range(dBuf, 0, 0);
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
        RTimestamp timestamp = new RTimestamp(0, 0);
        long fee = 0;
        List<RAccountAmount> transferList = List.of();
        final List<RTokenTransferList> tokenTransferLists = new ArrayList<>();
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

    private RStateChange decodeStateChange(final int limit) {
        int stateId = 0;
        RChange change = new RQueuePopChange();
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

    private RChange decodeMapUpdateChange(final int limit) {
        int keyKind = 0;
        long keyAccount = 0;
        long keyToken = 0;
        long keySerial = 0;
        int valueKind = 0;
        long balance = 0;
        boolean autoAssoc = false;
        long nftOwner = 0;
        long nftSpender = 0;
        RTimestamp mintTime = new RTimestamp(0, 0);
        boolean nftHasId = false;
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 1) {
                final int len = (int) dVarint();
                final int keyLimit = dPos + len;
                while (dPos < keyLimit) {
                    final long kt = dVarint();
                    final int kf = (int) (kt >>> 3);
                    final int kw = (int) (kt & 7);
                    if (kf == 1) {
                        keyKind = 1;
                        final int l2 = (int) dVarint();
                        keyAccount = decodeAccountId(dPos + l2);
                    } else if (kf == 5) {
                        keyKind = 5;
                        final int l2 = (int) dVarint();
                        final long[] nft = decodeNftId(dPos + l2);
                        keyToken = nft[0];
                        keySerial = nft[1];
                    } else if (kf == 25) {
                        keyKind = 25;
                        final int l2 = (int) dVarint();
                        final long[] assoc = decodeAccountTokenAssociation(dPos + l2);
                        keyAccount = assoc[0];
                        keyToken = assoc[1];
                    } else {
                        dSkip(kw);
                    }
                }
            } else if (field == 2) {
                final int len = (int) dVarint();
                final int valueLimit = dPos + len;
                while (dPos < valueLimit) {
                    final long vt = dVarint();
                    final int vf = (int) (vt >>> 3);
                    final int vw = (int) (vt & 7);
                    if (vf == 12) {
                        valueKind = 12;
                        final int l2 = (int) dVarint();
                        final int trLimit = dPos + l2;
                        while (dPos < trLimit) {
                            final long tt = dVarint();
                            final int tf = (int) (tt >>> 3);
                            final int tw = (int) (tt & 7);
                            if (tf == 2) {
                                final int l3 = (int) dVarint();
                                keyAccount = decodeAccountId(dPos + l3);
                            } else if (tf == 3) {
                                balance = dVarint();
                            } else if (tf == 6) {
                                autoAssoc = dVarint() != 0;
                            } else {
                                dSkip(tw);
                            }
                        }
                    } else if (vf == 5) {
                        valueKind = 5;
                        final int l2 = (int) dVarint();
                        final int nftLimit = dPos + l2;
                        while (dPos < nftLimit) {
                            final long nt = dVarint();
                            final int nf = (int) (nt >>> 3);
                            final int nw = (int) (nt & 7);
                            if (nf == 1) {
                                nftHasId = true;
                                dSkip(nw);
                            } else if (nf == 2) {
                                final int l3 = (int) dVarint();
                                nftOwner = decodeAccountId(dPos + l3);
                            } else if (nf == 3) {
                                final int l3 = (int) dVarint();
                                nftSpender = decodeAccountId(dPos + l3);
                            } else if (nf == 4) {
                                final int l3 = (int) dVarint();
                                mintTime = decodeTimestamp(dPos + l3);
                            } else {
                                dSkip(nw);
                            }
                        }
                    } else {
                        dSkip(vw);
                    }
                }
            } else {
                dSkip(wireType);
            }
        }
        if (valueKind == 5) {
            return nftHasId
                    ? new RNftFull(keyToken, keySerial, nftOwner, nftSpender, mintTime)
                    : new RNftPrivate(keyToken, keySerial, nftOwner, mintTime);
        }
        // valueKind == 12 (token_relation_value): distinguish OP_TOKEN_REL (key = token_relationship_key, 25) from
        // OP_ACCOUNT (key = account_id_key, 1) by which MapChangeKey branch was present.
        return keyKind == 25
                ? new RTokenRelChange(keyAccount, keyToken, balance, autoAssoc)
                : new RAccountChange(keyAccount, balance);
    }

    private RChange decodeQueuePushChange(final int limit) {
        long nodeId = 0;
        RTimestamp validStart = new RTimestamp(0, 0);
        long payer = 0;
        int status = 0;
        while (dPos < limit) {
            final long tag = dVarint();
            final int field = (int) (tag >>> 3);
            final int wireType = (int) (tag & 7);
            if (field == 3) {
                final int len = (int) dVarint();
                final int entriesLimit = dPos + len;
                while (dPos < entriesLimit) {
                    final long et = dVarint();
                    if ((int) (et >>> 3) == 1) {
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
                        dSkip((int) (et & 7));
                    }
                }
            } else {
                dSkip(wireType);
            }
        }
        return new RReceiptPushChange(nodeId, validStart, payer, status);
    }

    private RStateChanges decodeStateChanges(final int limit) {
        RTimestamp timestamp = new RTimestamp(0, 0);
        final List<RStateChange> changes = new ArrayList<>();
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
                    final int mapLimit = dPos + len;
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
                case 3 -> dVarint();
                default -> dSkip(wireType);
            }
        }
        final RTransactionBody body = decodeTransactionBody(bodyBytes);
        return new RSignedTx(bodyBytes, pairs.toArray(RSignaturePair[]::new), body);
    }

    private RSignaturePair decodeSignaturePair(final int limit) {
        Range prefix = new Range(dBuf, 0, 0);
        int kind = 0;
        Range signature = new Range(dBuf, 0, 0);
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

    private RTransactionBody decodeTransactionBody(final Range bodyBytes) {
        dBuf = bodyBytes.array();
        dPos = bodyBytes.offset();
        final int limit = bodyBytes.offset() + bodyBytes.length();
        RTransactionId transactionId = new RTransactionId(new RTimestamp(0, 0), 0);
        long nodeAccountNum = 0;
        long fee = 0;
        RTxData data = new RCryptoTransfer(new RAccountAmount[0], new RTokenTransferList[0]);
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
        RTimestamp validStart = new RTimestamp(0, 0);
        long accountNum = 0;
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
