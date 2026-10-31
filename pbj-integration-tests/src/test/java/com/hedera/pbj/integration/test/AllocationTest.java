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
    /**
     * Measured operations, and warm-up operations before them: well past C2's compile threshold, so the measured code is
     * compiled on any JVM version.
     */
    private static final int ITERATIONS = 50_000;

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
     * zero. Each figure is an average over the first {@value #ITERATIONS} items of its kind (cycling), so it changes if
     * the iteration count does.
     */
    private static final Map<String, Long> PARSE_CEILINGS = Map.ofEntries(
            Map.entry("BLOCK_FOOTER", 240L),
            Map.entry("BLOCK_HEADER", 1048L),
            Map.entry("BLOCK_PROOF", 232L),
            Map.entry("EVENT_HEADER", 456L),
            Map.entry("ROUND_HEADER", 136L),
            Map.entry("SIGNED_TRANSACTION", 136L),
            Map.entry("STATE_CHANGES", 4000L),
            Map.entry("SignedTransaction", 400L),
            Map.entry("TRACE_DATA", 5696L),
            Map.entry("TRANSACTION_OUTPUT", 480L),
            Map.entry("TRANSACTION_RESULT", 928L));

    /**
     * The same ceilings with every fast code generation option on ({@code -PpbjPerformance=max}), which stores oneofs
     * unboxed, so parsing allocates no {@code OneOf}. With max options but boxed oneofs
     * ({@code -PpbjOneofStorage=boxed}) the figures were BLOCK_FOOTER 224, BLOCK_HEADER 416, BLOCK_PROOF 216,
     * EVENT_HEADER 434, ROUND_HEADER 120, SIGNED_TRANSACTION 120, STATE_CHANGES 3554, SignedTransaction 378,
     * TRACE_DATA 5674, TRANSACTION_OUTPUT 458 and TRANSACTION_RESULT 911: every ceiling here is below them.
     */
    private static final Map<String, Long> UNBOXED_PARSE_CEILINGS = Map.ofEntries(
            Map.entry("BLOCK_FOOTER", 200L),
            Map.entry("BLOCK_HEADER", 392L),
            Map.entry("BLOCK_PROOF", 168L),
            Map.entry("EVENT_HEADER", 368L),
            Map.entry("ROUND_HEADER", 96L),
            Map.entry("SIGNED_TRANSACTION", 96L),
            Map.entry("STATE_CHANGES", 3016L),
            Map.entry("SignedTransaction", 360L),
            Map.entry("TRACE_DATA", 4592L),
            Map.entry("TRANSACTION_OUTPUT", 352L),
            Map.entry("TRANSACTION_RESULT", 808L));

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

    /**
     * Measuring and writing freshly built objects, whose sizes are not cached yet: the objects are built before the
     * measurement, which covers only {@code protobufSize()} and {@code write(T, byte[], int)}.
     */
    @Test
    void sizeAndWriteFreshObjects() throws ParseException {
        final List<Bytes> encoded = ITEMS_BY_KIND.values().stream()
                .flatMap(List::stream)
                .map(BlockItem.PROTOBUF::toBytes)
                .toList();
        final byte[] out =
                new byte[encoded.stream().mapToInt(b -> (int) b.length()).max().orElse(0)];
        long bytesPerOperation = -1;
        // several rounds over newly parsed objects, the last one measured once the code is compiled
        for (int round = 0; round < 6; round++) {
            final List<BlockItem> fresh = new ArrayList<>(encoded.size());
            for (final Bytes bytes : encoded) {
                fresh.add(BlockItem.PROTOBUF.parse(bytes));
            }
            final long before = allocatedBytes();
            for (final BlockItem item : fresh) {
                BlockItem.PROTOBUF.write(item, out, 0);
            }
            bytesPerOperation = (allocatedBytes() - before) / fresh.size();
        }
        System.out.println(
                "protobufSize() and write(T, byte[], int) of fresh objects, bytes per operation: " + bytesPerOperation);
        assertEquals(0L, bytesPerOperation, "measuring and writing a fresh object");
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
        // the build says which configuration generated the code; oneofs are unboxed in max unless overridden
        final boolean unboxed = "max".equals(System.getProperty("pbj.performance"))
                        && !"boxed".equals(System.getProperty("pbj.oneofStorage"))
                || "unboxed".equals(System.getProperty("pbj.oneofStorage"));
        final Map<String, Long> ceilings = unboxed ? UNBOXED_PARSE_CEILINGS : PARSE_CEILINGS;
        assertEquals(ceilings.keySet(), figures.keySet(), "a ceiling for every kind in the sample blocks");
        figures.forEach((kind, bytes) -> assertTrue(
                bytes <= ceilings.get(kind),
                kind + " allocated " + bytes + " bytes per parse, ceiling " + ceilings.get(kind)));
    }
}
