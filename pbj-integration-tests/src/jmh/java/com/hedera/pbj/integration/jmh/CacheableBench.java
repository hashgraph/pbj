// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.CacheableAccountID;
import com.hedera.pbj.test.proto.pbj.NotCacheableAccountID;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class CacheableBench {
    private static final int INVOCATIONS = 1 * 1024;

    @State(Scope.Thread)
    public static class BenchState {
        record Model<T>(int maxSize, Function<Random, T> factory, Codec<T> codec) {}

        public enum Type {
            NotCacheableAccountIDType(new Model<NotCacheableAccountID>(
                    256,
                    random -> {
                        final NotCacheableAccountID.Builder builder = NotCacheableAccountID.newBuilder()
                                .shardNum(random.nextLong())
                                .realmNum(random.nextLong());
                        if (random.nextBoolean()) {
                            builder.accountNum(random.nextLong());
                        } else {
                            byte[] arr = new byte[32];
                            random.nextBytes(arr);
                            builder.alias(Bytes.wrap(arr));
                        }
                        return builder.build();
                    },
                    NotCacheableAccountID.PROTOBUF)),
            CacheableAccountIDType(new Model<CacheableAccountID>(
                    256,
                    random -> {
                        final CacheableAccountID.Builder builder = CacheableAccountID.newBuilder()
                                .shardNum(random.nextLong())
                                .realmNum(random.nextLong());
                        if (random.nextBoolean()) {
                            builder.accountNum(random.nextLong());
                        } else {
                            byte[] arr = new byte[32];
                            random.nextBytes(arr);
                            builder.alias(Bytes.wrap(arr));
                        }
                        return builder.build();
                    },
                    CacheableAccountID.PROTOBUF));

            private final Model model;

            Type(Model model) {
                this.model = model;
            }
        }

        @Param
        Type type;

        // Cache size is 16, so 17 exceeds that
        @Param({"1", "2", "3", "7", "17"})
        int numOfFrequentModels;

        // Cache size is 16, so 37 exceeds that
        @Param({"2", "5", "11", "37"})
        int period;

        Model model;
        byte[] array;
        BufferedData bd;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            model = type.model;

            array = new byte[INVOCATIONS * model.maxSize];

            // For determinism:
            final Random random = new Random(723049435);

            final Object frequentModels[] = new Object[numOfFrequentModels];
            for (int i = 0; i < numOfFrequentModels; i++) {
                frequentModels[i] = model.factory.apply(random);
            }

            bd = BufferedData.wrap(array);
            for (int i = 0, j = 0; i < INVOCATIONS; i++) {
                // Sprinkle our "frequent" object every now and then.
                if (i % period == 0) {
                    model.codec.write(frequentModels[j++ % numOfFrequentModels], bd);
                } else {
                    model.codec.write(model.factory.apply(random), bd);
                }
            }
            bd.flip();
        }

        @TearDown(Level.Trial)
        public void tearDown() {}
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void bench(final BenchState state, final Blackhole blackhole) throws ParseException {
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(state.model.codec.parse(state.bd));
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(CacheableBench.class.getSimpleName())
                .build();

        BenchmarkReporter.printResults(new Runner(opt).run());
    }
}
