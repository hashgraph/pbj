// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.CopyBuilderTracked;
import com.hedera.pbj.runtime.DiffCodec;
import com.hedera.pbj.runtime.PartialApplier;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.CacheableAccountID;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.WideTrackedModel;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/** Shallow diff costs and allocations. Setup/serialization are excluded from measured operations. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class PartialDiffBench {
    @Param({"SMALL", "EVERYTHING", "WIDE", "WIDE_POPULATED"})
    public String model;

    @Param({"0", "1024", "16384"})
    public int payloadSize;

    private DiffCodec<Object> codec;
    private Object prior;
    private Object tracked;
    private Object plain;
    private Object mask;
    private Bytes priorBytes;
    private Bytes partialBytes;
    private int[] cleared;

    @Setup
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        final Bytes payload = Bytes.wrap(new byte[payloadSize]);
        switch (model) {
            case "SMALL" -> {
                final var root = CacheableAccountID.newBuilder()
                        .shardNum(1)
                        .alias(payload)
                        .build();
                prior = root;
                tracked = root.copyBuilder().shardNum(2).build();
                codec = (DiffCodec<Object>) (DiffCodec<?>) CacheableAccountID.PROTOBUF;
            }
            case "EVERYTHING" -> {
                final var root = Everything.newBuilder()
                        .bytesField(payload)
                        .int32Number(1)
                        .build();
                prior = root;
                tracked = root.copyBuilder().int32Number(2).build();
                codec = (DiffCodec<Object>) (DiffCodec<?>) Everything.PROTOBUF;
            }
            case "WIDE", "WIDE_POPULATED" -> {
                final var builder = WideTrackedModel.newBuilder()
                        .text("x".repeat(payloadSize))
                        .value1(1);
                if (model.equals("WIDE_POPULATED")) {
                    // Reflection is confined to setup, to populate all encoded field runs.
                    for (int i = 2; i <= 70; i++) {
                        builder.getClass().getMethod("value" + i, long.class).invoke(builder, (long) i);
                    }
                }
                final var root = builder.build();
                prior = root;
                tracked = root.copyBuilder().value1(2).build();
                codec = (DiffCodec<Object>) (DiffCodec<?>) WideTrackedModel.PROTOBUF;
            }
            default -> throw new IllegalArgumentException(model);
        }
        plain = CopyBuilderTracked.untracked(tracked);
        mask = codec.diff(prior, tracked);
        final var partial = codec.partial(tracked, mask);
        cleared = codec.clearedFields(prior, tracked, mask);
        priorBytes = codec.toBytes(prior);
        partialBytes = codec.toBytes(partial);
    }

    @Benchmark
    public Object trackedDiff() {
        return codec.diff(prior, tracked);
    }

    @Benchmark
    public Object fullCompareDiff() {
        return codec.diff(prior, plain);
    }

    @Benchmark
    public boolean worthPartial() {
        return codec.worthPartial(tracked, mask, 64, 2);
    }

    @Benchmark
    public Bytes splice() {
        return PartialApplier.splice(priorBytes, partialBytes, cleared);
    }
}
