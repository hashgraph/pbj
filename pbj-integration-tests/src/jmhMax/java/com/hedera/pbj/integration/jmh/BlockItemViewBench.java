// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Routing block items, per item: read the item's kind, and for state changes the consensus time and the id and
 * operation of every change; for signed transactions their length. Through a reusable zero-copy view, against parsing
 * each item into model objects. Only built with {@code -PpbjPerformance=max}, where HAPI messages have views.
 */
@SuppressWarnings("unused")
@State(Scope.Thread)
@Fork(3)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BlockItemViewBench {
    private static final int ITEMS = 67_869;
    private static final List<Bytes> ITEM_BYTES = new ArrayList<>();

    static {
        try (var in = new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(
                BlockItemViewBench.class.getResourceAsStream("/000000000000000000000000000000030221.blk.gz"))))) {
            for (final BlockItem item :
                    Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes())).items()) {
                ITEM_BYTES.add(BlockItem.PROTOBUF.toBytes(item));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
        if (ITEM_BYTES.size() != ITEMS) {
            throw new IllegalStateException("Update ITEMS to " + ITEM_BYTES.size());
        }
    }

    private final BlockItem.View view = new BlockItem.View();

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void routeWithView(final Blackhole bh) throws ParseException {
        for (final Bytes bytes : ITEM_BYTES) {
            final BlockItem.View item = view.wrap(bytes);
            long sum = item.itemKind().ordinal();
            switch (item.itemKind()) {
                case STATE_CHANGES -> {
                    final StateChanges.View changes = item.stateChanges();
                    if (changes.hasConsensusTimestamp()) {
                        sum += changes.consensusTimestamp().seconds();
                    }
                    for (var it = changes.stateChanges(); it.next(); ) {
                        sum += it.value().stateId()
                                + it.value().changeOperationKind().ordinal();
                    }
                }
                case SIGNED_TRANSACTION -> sum += item.signedTransactionLength();
                default -> {}
            }
            bh.consume(sum);
        }
    }

    @Benchmark
    @OperationsPerInvocation(ITEMS)
    public void routeWithParse(final Blackhole bh) throws ParseException {
        for (final Bytes bytes : ITEM_BYTES) {
            final BlockItem item = BlockItem.PROTOBUF.parse(bytes);
            long sum = item.item().kind().ordinal();
            switch (item.item().kind()) {
                case STATE_CHANGES -> {
                    final StateChanges changes = item.stateChangesOrThrow();
                    if (changes.consensusTimestamp() != null) {
                        sum += changes.consensusTimestamp().seconds();
                    }
                    for (final StateChange change : changes.stateChanges()) {
                        sum += change.stateId()
                                + change.changeOperation().kind().ordinal();
                    }
                }
                case SIGNED_TRANSACTION ->
                    sum += item.signedTransactionOrThrow().length();
                default -> {}
            }
            bh.consume(sum);
        }
    }
}
