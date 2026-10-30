// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
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
 * Per-item benchmarks over every {@link BlockItem} of the sample block-stream file, and over the signed transactions
 * found in that block. Block items are Hiero's hottest serialization path. Every result is the
 * average time per item (or per transaction).
 *
 * <p>PBJ caches {@code protobufSize()} in each model object, so writing the same objects again only times the write.
 * The {@code Fresh} benchmarks write objects parsed again before every invocation, so their sizes are not cached yet
 * and the time includes measuring, as when a block item is built and written once. Their {@code gc.alloc.rate.norm}
 * includes that setup parse; use the {@code Cached} benchmarks for allocation per write. Report {@code gc.count} and
 * {@code gc.time} from {@code -prof gc} too: collections in the timed region change the results.
 */
@SuppressWarnings("unused")
@Fork(3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BlockItemBench {
    /**
     * The sample block files in {@code src/jmh/resources} that parse with the current HAPI schema. Block 497558 was
     * written with an older {@code BlockHeader} layout, whose field now has a different wire type, and no longer parses
     * with PBJ (it fails with "Wire type 'Group Start' is unsupported"), so only block 030221 is used. It alone has
     * 67,869 items, every kind the newer block has, and 21,981 signed transactions.
     */
    private static final String[] BLOCK_FILES = {"/000000000000000000000000000000030221.blk.gz"};

    /**
     * Must match {@link #ITEM_COUNT}; JMH needs a constant for {@code @OperationsPerInvocation}, checked in setup.
     */
    private static final int ITEMS = 67_869;

    /** Must match {@link #TRANSACTION_COUNT}, checked in setup. */
    private static final int TRANSACTIONS = 21_981;

    /** The encoded block items and signed transactions of the sample blocks, loaded once. */
    private static final List<Bytes> ITEM_BYTES = new ArrayList<>();

    private static final List<Bytes> TRANSACTION_BYTES = new ArrayList<>();

    /** The same items and transactions as byte arrays, for protoc. */
    private static final List<byte[]> ITEM_ARRAYS = new ArrayList<>();

    private static final List<byte[]> TRANSACTION_ARRAYS = new ArrayList<>();

    /** Items and transactions in the benchmarks below; kept in sync with the lists above. */
    static final int ITEM_COUNT;

    static final int TRANSACTION_COUNT;

    static {
        for (final String file : BLOCK_FILES) {
            try (var in = new BufferedInputStream(
                    new GZIPInputStream(Objects.requireNonNull(BlockItemBench.class.getResourceAsStream(file))))) {
                final Block block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
                for (final BlockItem item : block.items()) {
                    ITEM_BYTES.add(BlockItem.PROTOBUF.toBytes(item));
                    if (item.hasSignedTransaction()) {
                        TRANSACTION_BYTES.add(item.signedTransactionOrThrow());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ParseException e) {
                throw new IllegalStateException(e);
            }
        }
        ITEM_BYTES.forEach(b -> ITEM_ARRAYS.add(b.toByteArray()));
        TRANSACTION_BYTES.forEach(b -> TRANSACTION_ARRAYS.add(b.toByteArray()));
        ITEM_COUNT = ITEM_BYTES.size();
        TRANSACTION_COUNT = TRANSACTION_BYTES.size();
        if (ITEM_COUNT != ITEMS || TRANSACTION_COUNT != TRANSACTIONS) {
            throw new IllegalStateException("Update ITEMS to " + ITEM_COUNT + " and TRANSACTIONS to "
                    + TRANSACTION_COUNT + " in BlockItemBench");
        }
    }

    /**
     * Model objects whose sizes are cached, and a reusable output array big enough for any of them.
     *
     * <p>Before every invocation this state parses the items again and drops them, untimed, as {@link FreshItems} does.
     * Without that, the fresh benchmarks' setup triggers most young collections outside the timed region, while the
     * cached benchmarks collect inside it, which made cached {@code toBytes} look slower than fresh (1,349 against
     * 928 ns with G1 on AMD EPYC; 1,199 against 1,127 ns with the no-op Epsilon collector).
     */
    @State(Scope.Thread)
    public static class Cached {
        List<BlockItem> items;
        List<SignedTransaction> transactions;
        byte[] out;

        @Setup(Level.Trial)
        public void setup() throws ParseException {
            items = parseAll(ITEM_BYTES, BlockItem.PROTOBUF);
            transactions = parseAll(TRANSACTION_BYTES, SignedTransaction.PROTOBUF);
            out = new byte[maxLength(ITEM_BYTES)];
            // measure once so every size is cached
            items.forEach(BlockItem.PROTOBUF::measureRecord);
            transactions.forEach(SignedTransaction.PROTOBUF::measureRecord);
        }

        /** Allocate as much as the fresh benchmarks' setup does, so collections fall in the same place. */
        @Setup(Level.Invocation)
        public void allocateLikeFresh(final Blackhole bh) throws ParseException {
            bh.consume(parseAll(ITEM_BYTES, BlockItem.PROTOBUF));
        }
    }

    /** Block items parsed again before every invocation, so no size is cached yet. */
    @State(Scope.Thread)
    public static class FreshItems {
        List<BlockItem> items;
        byte[] out;

        @Setup(Level.Trial)
        public void allocate() {
            out = new byte[maxLength(ITEM_BYTES)];
        }

        @Setup(Level.Invocation)
        public void rebuild() throws ParseException {
            items = parseAll(ITEM_BYTES, BlockItem.PROTOBUF);
        }
    }

    /** Signed transactions parsed again before every invocation, so no size is cached yet. */
    @State(Scope.Thread)
    public static class FreshTransactions {
        List<SignedTransaction> transactions;
        byte[] out;

        @Setup(Level.Trial)
        public void allocate() {
            out = new byte[maxLength(TRANSACTION_BYTES)];
        }

        @Setup(Level.Invocation)
        public void rebuild() throws ParseException {
            transactions = parseAll(TRANSACTION_BYTES, SignedTransaction.PROTOBUF);
        }
    }

    /** Protoc block items parsed again before every invocation, so protoc's memoized sizes are not cached yet. */
    @State(Scope.Thread)
    public static class FreshProtoCItems {
        List<com.hedera.hapi.block.stream.protoc.BlockItem> items;

        @Setup(Level.Invocation)
        public void rebuild() throws IOException {
            items = new ArrayList<>(ITEM_ARRAYS.size());
            for (final byte[] bytes : ITEM_ARRAYS) {
                items.add(com.hedera.hapi.block.stream.protoc.BlockItem.parseFrom(bytes));
            }
        }
    }

    /** Protoc signed transactions parsed again before every invocation. */
    @State(Scope.Thread)
    public static class FreshProtoCTransactions {
        List<com.hederahashgraph.api.proto.java.SignedTransaction> transactions;

        @Setup(Level.Invocation)
        public void rebuild() throws IOException {
            transactions = new ArrayList<>(TRANSACTION_ARRAYS.size());
            for (final byte[] bytes : TRANSACTION_ARRAYS) {
                transactions.add(com.hederahashgraph.api.proto.java.SignedTransaction.parseFrom(bytes));
            }
        }
    }

    private static int maxLength(final List<Bytes> all) {
        long max = 0;
        for (final Bytes bytes : all) {
            max = Math.max(max, bytes.length());
        }
        return (int) max;
    }

    private static <T> List<T> parseAll(final List<Bytes> all, final Codec<T> codec) throws ParseException {
        final List<T> parsed = new ArrayList<>(all.size());
        for (final Bytes bytes : all) {
            parsed.add(codec.parse(bytes));
        }
        return parsed;
    }

    // ---------------------------------------------------------------------------------------------- block items

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemWriteByteArrayFresh(final FreshItems state, final Blackhole bh) {
        for (final BlockItem item : state.items) {
            bh.consume(BlockItem.PROTOBUF.write(item, state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemWriteByteArrayCached(final Cached state, final Blackhole bh) {
        for (final BlockItem item : state.items) {
            bh.consume(BlockItem.PROTOBUF.write(item, state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemToBytesFresh(final FreshItems state, final Blackhole bh) {
        for (final BlockItem item : state.items) {
            bh.consume(BlockItem.PROTOBUF.toBytes(item));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemToBytesCached(final Cached state, final Blackhole bh) {
        for (final BlockItem item : state.items) {
            bh.consume(BlockItem.PROTOBUF.toBytes(item));
        }
    }

    /**
     * Measuring freshly built items: {@code protobufSize()} on objects whose sizes are not cached yet. A fresh write
     * should cost about a cached write plus this.
     */
    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemSizeFresh(final FreshItems state, final Blackhole bh) {
        for (final BlockItem item : state.items) {
            bh.consume(item.protobufSize());
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemToBytesProtoCFresh(final FreshProtoCItems state, final Blackhole bh) {
        for (final var item : state.items) {
            bh.consume(item.toByteArray());
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemParseProtoC(final Blackhole bh) throws IOException {
        for (final byte[] bytes : ITEM_ARRAYS) {
            bh.consume(com.hedera.hapi.block.stream.protoc.BlockItem.parseFrom(bytes));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void itemParseBytes(final Blackhole bh) throws ParseException {
        for (final Bytes bytes : ITEM_BYTES) {
            bh.consume(BlockItem.PROTOBUF.parse(bytes));
        }
    }

    // ---------------------------------------------------------------------------------------- signed transactions

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionWriteByteArrayFresh(final FreshTransactions state, final Blackhole bh) {
        for (final SignedTransaction transaction : state.transactions) {
            bh.consume(SignedTransaction.PROTOBUF.write(transaction, state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionWriteByteArrayCached(final Cached state, final Blackhole bh) {
        for (final SignedTransaction transaction : state.transactions) {
            bh.consume(SignedTransaction.PROTOBUF.write(transaction, state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionToBytesFresh(final FreshTransactions state, final Blackhole bh) {
        for (final SignedTransaction transaction : state.transactions) {
            bh.consume(SignedTransaction.PROTOBUF.toBytes(transaction));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionToBytesProtoCFresh(final FreshProtoCTransactions state, final Blackhole bh) {
        for (final var transaction : state.transactions) {
            bh.consume(transaction.toByteArray());
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionParseProtoC(final Blackhole bh) throws IOException {
        for (final byte[] bytes : TRANSACTION_ARRAYS) {
            bh.consume(com.hederahashgraph.api.proto.java.SignedTransaction.parseFrom(bytes));
        }
    }

    @Benchmark
    @OperationsPerInvocation(TRANSACTIONS)
    public void transactionParseBytes(final Blackhole bh) throws ParseException {
        for (final Bytes bytes : TRANSACTION_BYTES) {
            bh.consume(SignedTransaction.PROTOBUF.parse(bytes));
        }
    }
}
