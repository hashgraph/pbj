// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.integration.test.synthetic.ReferenceCodec;
import com.hedera.pbj.integration.test.synthetic.ScanObjects;
import com.hedera.pbj.integration.test.synthetic.ScanViews;
import com.hedera.pbj.integration.test.synthetic.SyntheticBlock;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * The synthetic block workload ({@link SyntheticBlock}): a block of {@value SyntheticBlock#TRANSACTIONS} transactions
 * with the shape of an external block-stream workload, built only from HAPI messages. Every result is per transaction
 * (block operations ÷ {@value SyntheticBlock#TRANSACTIONS}); report {@code gc.alloc.rate.norm} from {@code -prof gc}
 * divided the same way. Only built with {@code -PpbjPerformance=max}, where HAPI messages have views and writers.
 *
 * <ul>
 *   <li>{@code encode…Fresh}: model objects built in setup (sizes not yet measured), then encoded.
 *   <li>{@code produce…}: from the block's flat values, everything timed: build objects and encode them, or write the
 *       values with the generated writers.
 *   <li>{@code decode}, {@code scan…}: parse the encoded block into objects, and read every field through objects or
 *       views ({@link ScanObjects}, {@link ScanViews}).
 *   <li>{@code tx…}: {@value SyntheticBlock#TRANSACTIONS} {@code SignedTransaction}s and their bodies.
 *   <li>{@code reference…}: the same operations with the hand-written reference codec ({@link ReferenceCodec}), the
 *       target: produce from values, decode into its own light objects, read every field, and the transactions.
 * </ul>
 */
@SuppressWarnings("unused")
@State(Scope.Thread)
@Fork(
        value = 3,
        jvmArgsAppend = {"-Xms16g", "-Xmx16g", "-Xmn12g"})
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SyntheticBlockBench {
    private static final int TRANSACTIONS = SyntheticBlock.TRANSACTIONS;

    private static final SyntheticBlock BLOCK = SyntheticBlock.generate();
    private static final Bytes ENCODED =
            Block.PROTOBUF.toBytes(Block.newBuilder().items(BLOCK.buildAll()).build());
    private static final List<Bytes> SIGNED_TRANSACTIONS = SyntheticBlock.transactions();
    private static final byte[] ENCODED_ARRAY = ENCODED.toByteArray();
    private static final byte[][] SIGNED_TRANSACTION_ARRAYS =
            SIGNED_TRANSACTIONS.stream().map(Bytes::toByteArray).toArray(byte[][]::new);
    /** Room for the whole encoded block. */
    private static final int CAPACITY = Math.toIntExact(ENCODED.length()) + 1024;

    private final SyntheticBlock.Cursor cursor = BLOCK.cursor();
    private final byte[] out = new byte[CAPACITY];
    private final ReverseBuffer reverse = new ReverseBuffer(CAPACITY);
    private final ProtoWriteBuffer buffer = new ProtoWriteBuffer(CAPACITY);
    private final BlockItem.Writer itemWriter = new BlockItem.Writer();
    private final Block.Writer blockWriter = new Block.Writer();
    private final ScanViews views = new ScanViews();
    private final ReferenceCodec reference = new ReferenceCodec();
    private final MessageDigest digest;
    private final byte[] hash = new byte[48];

    public SyntheticBlockBench() {
        try {
            digest = MessageDigest.getInstance("SHA-384");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Model objects of the block, built again before every invocation, so their sizes are not measured yet. */
    @State(Scope.Thread)
    public static class Fresh {
        Block block;

        @Setup(Level.Invocation)
        public void build() {
            block = Block.newBuilder().items(BLOCK.buildAll()).build();
        }
    }

    // ----------------------------------------------------------------------------- encode fresh objects

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void encodeFresh(final Fresh fresh, final Blackhole bh) {
        bh.consume(Block.PROTOBUF.write(fresh.block, out, 0));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void encodeFreshToBytes(final Fresh fresh, final Blackhole bh) {
        bh.consume(Block.PROTOBUF.toBytes(fresh.block));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void encodeReverseFresh(final Fresh fresh, final Blackhole bh) {
        reverse.reset();
        Block.PROTOBUF.writeReverse(fresh.block, reverse);
        bh.consume(reverse.length());
    }

    // ----------------------------------------------------------------------------- produce from values

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void produceObjectsToBytes(final Blackhole bh) {
        cursor.reset();
        for (int i = 0; i < BLOCK.items; i++) {
            bh.consume(BlockItem.PROTOBUF.toBytes(SyntheticBlock.build(cursor)));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void produceObjectsReverse(final Blackhole bh) {
        cursor.reset();
        for (int i = 0; i < BLOCK.items; i++) {
            reverse.reset();
            BlockItem.PROTOBUF.writeReverse(SyntheticBlock.build(cursor), reverse);
            bh.consume(reverse.length());
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void produceWriters(final Blackhole bh) {
        cursor.reset();
        for (int i = 0; i < BLOCK.items; i++) {
            buffer.reset();
            SyntheticBlock.write(cursor, itemWriter.start(buffer));
            bh.consume(itemWriter.end());
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void produceWritersBlockHashed(final Blackhole bh) {
        cursor.reset();
        buffer.reset();
        final Block.Writer block = blockWriter.start(buffer);
        for (int i = 0; i < BLOCK.items; i++) {
            final BlockItem.Writer item = block.addItems();
            SyntheticBlock.write(cursor, item);
            item.end();
            hash(buffer.array(), item.offset(), item.length());
        }
        bh.consume(block.end());
        bh.consume(hash);
    }

    /** SHA-384 over {@code 0x00 ‖ item}, as for a Merkle leaf. */
    private void hash(final byte[] array, final int offset, final int length) {
        digest.update((byte) 0);
        digest.update(array, offset, length);
        try {
            digest.digest(hash, 0, hash.length);
        } catch (DigestException e) {
            throw new IllegalStateException(e);
        }
    }

    // ----------------------------------------------------------------------------- decode and read

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void decode(final Blackhole bh) throws ParseException {
        bh.consume(Block.PROTOBUF.parse(ENCODED));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void scanObjects(final Blackhole bh) throws ParseException {
        bh.consume(ScanObjects.items(Block.PROTOBUF.parse(ENCODED).items()));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void scanViews(final Blackhole bh) throws ParseException {
        bh.consume(views.block(ENCODED));
    }

    // ----------------------------------------------------------------------------- transactions

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void txDecode(final Blackhole bh) throws ParseException {
        for (int i = 0; i < TRANSACTIONS; i++) {
            final SignedTransaction signed = SignedTransaction.PROTOBUF.parse(SIGNED_TRANSACTIONS.get(i));
            bh.consume(TransactionBody.PROTOBUF.parse(signed.bodyBytes()));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void txScanObjects(final Blackhole bh) throws ParseException {
        for (int i = 0; i < TRANSACTIONS; i++) {
            bh.consume(ScanObjects.transaction(SIGNED_TRANSACTIONS.get(i)));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void txScanViews(final Blackhole bh) throws ParseException {
        for (int i = 0; i < TRANSACTIONS; i++) {
            bh.consume(views.transaction(SIGNED_TRANSACTIONS.get(i)));
        }
    }

    // ----------------------------------------------------------------------------- the reference codec

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void referenceProduce(final Blackhole bh) {
        cursor.reset();
        for (int i = 0; i < BLOCK.items; i++) {
            // the reverse-write result, in the buffer the codec owns; no copy to offset 0 (encodeItem does that,
            // for callers that need it)
            bh.consume(reference.encodeItemReverse(cursor));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void referenceDecode(final Blackhole bh) {
        bh.consume(reference.decodeBlock(ENCODED_ARRAY, 0, ENCODED_ARRAY.length));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void referenceScan(final Blackhole bh) {
        bh.consume(reference.scanBlock(ENCODED_ARRAY, 0, ENCODED_ARRAY.length));
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void referenceTxDecode(final Blackhole bh) {
        for (int i = 0; i < TRANSACTIONS; i++) {
            final byte[] tx = SIGNED_TRANSACTION_ARRAYS[i];
            bh.consume(reference.decodeTransaction(tx, 0, tx.length));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void referenceTxScan(final Blackhole bh) {
        for (int i = 0; i < TRANSACTIONS; i++) {
            final byte[] tx = SIGNED_TRANSACTION_ARRAYS[i];
            bh.consume(reference.scanTransaction(tx, 0, tx.length));
        }
    }
}
