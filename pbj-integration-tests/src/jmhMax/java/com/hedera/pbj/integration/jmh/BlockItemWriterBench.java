// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.pbj.integration.test.BlockItemValues;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Writing block items with the generated writers ({@code pbj.writers}), against building model objects and encoding
 * them, per item of the sample block. Only built with {@code -PpbjPerformance=max}, where HAPI messages have writers.
 *
 * <p>The {@code objectsThen…} and {@code writerFromValues} benchmarks start from the same data: the field values of the
 * block's signed transactions, transaction results, event headers and state changes (map updates of accounts, token
 * relations, NFTs, topics and storage slots, and map deletes), {@value #VALUE_ITEMS} of its {@value #ITEMS} items,
 * extracted once into flat arrays by {@link BlockItemValues}. The first build model objects from them with builders and
 * encode the objects, as a node does today; the last writes the same items from the values with no objects.
 *
 * <p>{@code writerFromObjects} writes cached model objects (sizes already measured) of every item through
 * {@code writeFrom}, against {@code write(T, byte[])} and {@code writeReverse} of the same objects: pure encoding speed.
 * The whole-block benchmarks write every item into one block and hash each item's bytes (SHA-384 over
 * {@code 0x00 ‖ item}, as for a Merkle leaf): with the writer, from its range in the buffer as soon as it ends; with
 * objects, by encoding each item for the hash and the whole block again.
 *
 * <p>Report {@code gc.alloc.rate.norm} and {@code gc.time} from {@code -prof gc}.
 */
@SuppressWarnings("unused")
@Fork(
        value = 3,
        jvmArgsAppend = {"-Xms16g", "-Xmx16g", "-Xmn12g"})
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BlockItemWriterBench {
    /** Every item of the sample block; checked in setup. */
    private static final int ITEMS = 67_869;

    /** The items whose values {@link BlockItemValues} extracts; checked in setup. */
    private static final int VALUE_ITEMS = 66_166;

    private static final Block BLOCK;
    private static final BlockItemValues VALUES;

    static {
        try (var in = new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(
                BlockItemWriterBench.class.getResourceAsStream("/000000000000000000000000000000030221.blk.gz"))))) {
            BLOCK = Block.PROTOBUF.parse(Block.PROTOBUF.toBytes(Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
        VALUES = BlockItemValues.extract(BLOCK.items());
        if (BLOCK.items().size() != ITEMS || VALUES.items().size() != VALUE_ITEMS) {
            throw new IllegalStateException("Update ITEMS to " + BLOCK.items().size() + " and VALUE_ITEMS to "
                    + VALUES.items().size() + " in BlockItemWriterBench");
        }
    }

    /** The extracted values, read again by each invocation, and reusable output for every way of writing. */
    @State(Scope.Thread)
    public static class Values {
        final BlockItemValues.Cursor cursor = VALUES.cursor();
        final byte[] out = new byte[1 << 20];
        final ReverseBuffer reverse = new ReverseBuffer(1 << 16);
    }

    /** A writer and its buffer, with each way of reserving nested messages' length prefixes. */
    @State(Scope.Thread)
    public static class Writer {
        @Param({"ADAPTIVE", "PER_TYPE", "ONE_BYTE"})
        public ProtoWriteBuffer.LengthReservation reservation;

        ProtoWriteBuffer buffer;
        final BlockItem.Writer item = new BlockItem.Writer();
        final Block.Writer block = new Block.Writer();

        @Setup(Level.Trial)
        public void setup() {
            buffer = new ProtoWriteBuffer(1 << 16, reservation);
        }
    }

    /** Model objects of every item, with their sizes measured, and reusable output. */
    @State(Scope.Thread)
    public static class Cached {
        final List<BlockItem> items = BLOCK.items();
        final byte[] out = new byte[1 << 20];
        final byte[] blockOut = new byte[BLOCK.protobufSize()];
        final ReverseBuffer reverse = new ReverseBuffer(1 << 16);
        final MessageDigest digest;
        final byte[] hash = new byte[48];

        public Cached() {
            items.forEach(BlockItem::protobufSize);
            try {
                digest = MessageDigest.getInstance("SHA-384");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        void hash(final byte[] array, final int offset, final int length) {
            digest.update((byte) 0);
            digest.update(array, offset, length);
            try {
                digest.digest(hash, 0, hash.length);
            } catch (DigestException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ------------------------------------------------------------------------------------- from field values

    @Benchmark
    @OperationsPerInvocation(VALUE_ITEMS)
    public void objectsThenToBytes(final Values state, final Blackhole bh) {
        state.cursor.reset();
        for (int i = 0; i < VALUE_ITEMS; i++) {
            bh.consume(BlockItem.PROTOBUF.toBytes(VALUES.build(state.cursor)));
        }
    }

    @Benchmark
    @OperationsPerInvocation(VALUE_ITEMS)
    public void objectsThenWriteByteArray(final Values state, final Blackhole bh) {
        state.cursor.reset();
        for (int i = 0; i < VALUE_ITEMS; i++) {
            bh.consume(BlockItem.PROTOBUF.write(VALUES.build(state.cursor), state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(VALUE_ITEMS)
    public void objectsThenWriteReverse(final Values state, final Blackhole bh) {
        state.cursor.reset();
        for (int i = 0; i < VALUE_ITEMS; i++) {
            state.reverse.reset();
            BlockItem.PROTOBUF.writeReverse(VALUES.build(state.cursor), state.reverse);
            bh.consume(state.reverse.length());
        }
    }

    @Benchmark
    @OperationsPerInvocation(VALUE_ITEMS)
    public void writerFromValues(final Values state, final Writer writer, final Blackhole bh) {
        state.cursor.reset();
        final ProtoWriteBuffer buffer = writer.buffer;
        for (int i = 0; i < VALUE_ITEMS; i++) {
            buffer.reset();
            VALUES.write(state.cursor, writer.item.start(buffer));
            bh.consume(writer.item.end());
        }
    }

    // ------------------------------------------------------------------------------- from cached model objects

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void writerFromObjects(final Cached state, final Writer writer, final Blackhole bh) {
        final ProtoWriteBuffer buffer = writer.buffer;
        final List<BlockItem> items = state.items;
        for (int i = 0; i < ITEMS; i++) {
            buffer.reset();
            bh.consume(writer.item.start(buffer).writeFrom(items.get(i)).end());
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void objectsWriteByteArray(final Cached state, final Blackhole bh) {
        final List<BlockItem> items = state.items;
        for (int i = 0; i < ITEMS; i++) {
            bh.consume(BlockItem.PROTOBUF.write(items.get(i), state.out, 0));
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void objectsWriteReverse(final Cached state, final Blackhole bh) {
        final List<BlockItem> items = state.items;
        for (int i = 0; i < ITEMS; i++) {
            state.reverse.reset();
            BlockItem.PROTOBUF.writeReverse(items.get(i), state.reverse);
            bh.consume(state.reverse.length());
        }
    }

    // ---------------------------------------------------------------------------------- whole block, hashed

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void writerWholeBlock(final Cached state, final Writer writer, final Blackhole bh) {
        final ProtoWriteBuffer buffer = writer.buffer;
        final List<BlockItem> items = state.items;
        buffer.reset();
        final Block.Writer block = writer.block.start(buffer);
        for (int i = 0; i < ITEMS; i++) {
            final BlockItem.Writer item = block.addItems().writeFrom(items.get(i));
            item.end();
            state.hash(buffer.array(), item.offset(), item.length());
        }
        bh.consume(block.end());
        bh.consume(state.hash);
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void objectsWholeBlock(final Cached state, final Blackhole bh) {
        final List<BlockItem> items = state.items;
        for (int i = 0; i < ITEMS; i++) {
            state.hash(state.out, 0, BlockItem.PROTOBUF.write(items.get(i), state.out, 0));
        }
        bh.consume(Block.PROTOBUF.write(BLOCK, state.blockOut, 0));
        bh.consume(state.hash);
    }
}
