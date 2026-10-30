// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.MixedFastInner;
import com.hedera.pbj.test.proto.java.pbj.integration.tests.MixedKind;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Allocation per operation on the hot serialization paths, measured over the block items and signed transactions of
 * the sample block-stream file. Each figure is measured with the current thread's allocation counter around
 * {@value #ITERATIONS} operations after {@value #ITERATIONS} warm-up operations.
 *
 * <p>Writes of objects whose size is already cached must not allocate beyond the recorded ceiling, and parses must not
 * allocate more than the recorded figure per message kind. When a change lowers a figure, lower its ceiling here so it
 * cannot grow back.
 */
final class AllocationTest {
    /** Warm-up and measured operations. */
    private static final int ITERATIONS = 10_000;

    /**
     * The sample block shared with the JMH benchmarks. The other sample block, 497558, was written with an older
     * {@code BlockHeader} layout and no longer parses with the current HAPI schema.
     */
    private static final Path SAMPLE_BLOCK = Path.of("src/jmh/resources/000000000000000000000000000000030221.blk.gz");

    /**
     * Ceiling for writing an object with a cached size to a byte array, in bytes per operation. The goal, and the
     * figure today, is zero.
     */
    private static final long WRITE_BYTE_ARRAY_CEILING = 0;

    /**
     * Ceilings for parsing from {@link Bytes}, in bytes per message, by block item kind (and for signed transactions).
     * These are today's figures rounded up to the next 8 bytes; parsing allocates the result objects, so they are not
     * zero.
     */
    private static final Map<String, Long> PARSE_CEILINGS = Map.ofEntries(
            Map.entry("BLOCK_FOOTER", 240L),
            Map.entry("BLOCK_HEADER", 1048L),
            Map.entry("BLOCK_PROOF", 232L),
            Map.entry("EVENT_HEADER", 456L),
            Map.entry("ROUND_HEADER", 136L),
            Map.entry("SIGNED_TRANSACTION", 136L),
            Map.entry("STATE_CHANGES", 3944L),
            Map.entry("SignedTransaction", 400L),
            Map.entry("TRACE_DATA", 5696L),
            Map.entry("TRANSACTION_OUTPUT", 480L),
            Map.entry("TRANSACTION_RESULT", 928L));

    private static final Map<String, List<BlockItem>> ITEMS_BY_KIND = new TreeMap<>();
    private static final List<SignedTransaction> TRANSACTIONS = new ArrayList<>();

    @BeforeAll
    static void loadSampleBlocks() throws IOException, ParseException {
        final Block block;
        try (InputStream in = new GZIPInputStream(Files.newInputStream(SAMPLE_BLOCK))) {
            block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
        }
        for (final BlockItem item : block.items()) {
            ITEMS_BY_KIND
                    .computeIfAbsent(item.item().kind().name(), k -> new ArrayList<>())
                    .add(item);
            if (item.hasSignedTransaction()) {
                TRANSACTIONS.add(SignedTransaction.PROTOBUF.parse(item.signedTransactionOrThrow()));
            }
        }
        assertTrue(ITEMS_BY_KIND.size() > 1, "expected several kinds of block item in " + SAMPLE_BLOCK);
        assertTrue(!TRANSACTIONS.isEmpty(), "expected signed transactions in " + SAMPLE_BLOCK);
    }

    /** Bytes allocated by the current thread so far. */
    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    /**
     * Runs {@code operation} {@value #ITERATIONS} times to warm up, then measures the bytes the current thread allocates
     * over another {@value #ITERATIONS} runs.
     *
     * @param operation one operation, called with the iteration number
     * @return allocated bytes per operation, rounded down
     */
    static long allocatedPerOperation(final Operation operation) {
        try {
            for (int i = 0; i < ITERATIONS; i++) {
                operation.run(i);
            }
            final long before = allocatedBytes();
            for (int i = 0; i < ITERATIONS; i++) {
                operation.run(i);
            }
            return (allocatedBytes() - before) / ITERATIONS;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** One measured operation. */
    @FunctionalInterface
    interface Operation {
        void run(int iteration) throws Exception;
    }

    private static <T> long writeByteArrayPerOperation(final List<T> objects, final Codec<T> codec) {
        final byte[] out =
                new byte[objects.stream().mapToInt(codec::measureRecord).max().orElse(0)];
        return allocatedPerOperation(i -> codec.write(objects.get(i % objects.size()), out, 0));
    }

    private static <T> long parsePerOperation(final List<T> objects, final Codec<T> codec) {
        final List<Bytes> encoded = objects.stream().map(codec::toBytes).toList();
        return allocatedPerOperation(i -> codec.parse(encoded.get(i % encoded.size())));
    }

    @Test
    void writeByteArrayWithCachedSize() {
        final Map<String, Long> figures = new TreeMap<>();
        ITEMS_BY_KIND.forEach(
                (kind, items) -> figures.put(kind, writeByteArrayPerOperation(items, BlockItem.PROTOBUF)));
        figures.put("SignedTransaction", writeByteArrayPerOperation(TRANSACTIONS, SignedTransaction.PROTOBUF));
        System.out.println("write(T, byte[], int) with cached size, bytes per operation: " + figures);
        figures.forEach((kind, bytes) -> assertTrue(
                bytes <= WRITE_BYTE_ARRAY_CEILING,
                kind + " allocated " + bytes + " bytes per write, ceiling " + WRITE_BYTE_ARRAY_CEILING));
    }

    @Test
    void writeReverseIntoAWarmBuffer() {
        final ReverseBuffer buffer = new ReverseBuffer(16);
        final Map<String, Long> figures = new TreeMap<>();
        ITEMS_BY_KIND.forEach((kind, items) -> figures.put(kind, allocatedPerOperation(i -> {
            buffer.reset();
            BlockItem.PROTOBUF.writeReverse(items.get(i % items.size()), buffer);
        })));
        figures.put("SignedTransaction", allocatedPerOperation(i -> {
            buffer.reset();
            SignedTransaction.PROTOBUF.writeReverse(TRANSACTIONS.get(i % TRANSACTIONS.size()), buffer);
        }));
        System.out.println("writeReverse into a warm buffer, bytes per operation: " + figures);
        figures.forEach(
                (kind, bytes) -> assertEquals(0L, bytes, kind + " allocated " + bytes + " bytes per reverse write"));
    }

    @Test
    void viewWrapAndReadEveryField() {
        final MixedFastInner message = MixedFastInner.newBuilder()
                .delta(-123_456_789L)
                .flag(true)
                .ratio(0.25)
                .fixed(42)
                .kind(MixedKind.MIXED_B)
                .big(Long.MIN_VALUE)
                .scale(1.5f)
                .offset(-7)
                .far("far away")
                .build();
        final Bytes bytes = MixedFastInner.PROTOBUF.toBytes(message);
        final MixedFastInner.View view = new MixedFastInner.View();
        final long[] sink = new long[1];
        final long bytesPerOperation = allocatedPerOperation(i -> {
            view.wrap(bytes);
            sink[0] += view.delta()
                    + (view.flag() ? 1 : 0)
                    + (long) view.ratio()
                    + view.fixed()
                    + view.kindProtoOrdinal()
                    + view.big()
                    + (long) view.scale()
                    + view.offset()
                    + view.farLength();
        });
        assertEquals(0L, bytesPerOperation, "view wrap and reading every field");
        assertTrue(sink[0] != 0);
    }

    @Test
    void parseDoesNotAllocateMore() {
        final Map<String, Long> figures = new TreeMap<>();
        ITEMS_BY_KIND.forEach((kind, items) -> figures.put(kind, parsePerOperation(items, BlockItem.PROTOBUF)));
        figures.put("SignedTransaction", parsePerOperation(TRANSACTIONS, SignedTransaction.PROTOBUF));
        System.out.println("parse(Bytes), bytes per operation: " + figures);
        assertEquals(PARSE_CEILINGS.keySet(), figures.keySet(), "a ceiling for every kind in the sample blocks");
        figures.forEach((kind, bytes) -> assertTrue(
                bytes <= PARSE_CEILINGS.get(kind),
                kind + " allocated " + bytes + " bytes per parse, ceiling " + PARSE_CEILINGS.get(kind)));
    }
}
