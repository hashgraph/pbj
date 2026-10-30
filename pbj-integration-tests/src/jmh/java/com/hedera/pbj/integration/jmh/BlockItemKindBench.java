// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import java.io.BufferedInputStream;
import java.io.IOException;
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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * The forward and reverse writers on the sample block's items of one kind at a time, so the cost of each kind is
 * visible. Every result is the time to write all items of the kind once; divide by the count printed in setup for the
 * time per item. {@code STATE_CHANGES} items hold {@code MapChangeKey}, {@code MapChangeValue},
 * {@code SingletonUpdateChange} and {@code QueuePushChange}, which have {@code google.protobuf} wrapper type
 * alternatives.
 */
@SuppressWarnings("unused")
@Fork(
        value = 3,
        jvmArgsAppend = {"-Xms32g", "-Xmx32g", "-Xmn24g"})
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class BlockItemKindBench {
    private static final String BLOCK_FILE = "/000000000000000000000000000000030221.blk.gz";

    @Param({"SIGNED_TRANSACTION", "STATE_CHANGES", "TRANSACTION_RESULT", "TRACE_DATA", "TRANSACTION_OUTPUT"})
    public String kind;

    /** The most items of the kind to use, 0 for all; a few hundred fit in the CPU caches. */
    @Param("0")
    public int limit;

    private final List<Bytes> itemBytes = new ArrayList<>();
    private List<BlockItem> cached;
    private List<BlockItem> fresh;
    private byte[] out;
    private final ReverseBuffer reverse = new ReverseBuffer(64 * 1024);

    @Setup(Level.Trial)
    public void load() throws IOException, ParseException {
        try (var in = new BufferedInputStream(new GZIPInputStream(
                Objects.requireNonNull(BlockItemKindBench.class.getResourceAsStream(BLOCK_FILE))))) {
            final Block block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
            int max = 0;
            for (final BlockItem item : block.items()) {
                if (item.item().kind().name().equals(kind) && (limit == 0 || itemBytes.size() < limit)) {
                    itemBytes.add(BlockItem.PROTOBUF.toBytes(item));
                    max = Math.max(max, item.protobufSize());
                }
            }
            out = new byte[max];
        }
        if (itemBytes.isEmpty()) {
            throw new IllegalStateException("No " + kind + " items");
        }
        System.out.printf("%n%s: %d items%n", kind, itemBytes.size());
        cached = parseAll();
        cached.forEach(BlockItem::protobufSize);
    }

    /** Items parsed again before every invocation, so no size is cached yet. */
    @Setup(Level.Invocation)
    public void rebuild() throws ParseException {
        fresh = parseAll();
    }

    private List<BlockItem> parseAll() throws ParseException {
        final List<BlockItem> items = new ArrayList<>(itemBytes.size());
        for (final Bytes bytes : itemBytes) {
            items.add(BlockItem.PROTOBUF.parse(bytes));
        }
        return items;
    }

    @Benchmark
    public void forwardFresh(final Blackhole bh) {
        for (final BlockItem item : fresh) {
            bh.consume(BlockItem.PROTOBUF.write(item, out, 0));
        }
    }

    @Benchmark
    public void forwardCached(final Blackhole bh) {
        for (final BlockItem item : cached) {
            bh.consume(BlockItem.PROTOBUF.write(item, out, 0));
        }
    }

    @Benchmark
    public void reverseFresh(final Blackhole bh) {
        for (final BlockItem item : fresh) {
            reverse.reset();
            BlockItem.PROTOBUF.writeReverse(item, reverse);
            bh.consume(reverse.length());
        }
    }

    @Benchmark
    public void reverseCached(final Blackhole bh) {
        for (final BlockItem item : cached) {
            reverse.reset();
            BlockItem.PROTOBUF.writeReverse(item, reverse);
            bh.consume(reverse.length());
        }
    }
}
