// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.SlimBuffer;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
public class GenericParserQuickBench {
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
                    NotCacheableAccountID.PROTOBUF));

            private final Model model;

            Type(Model model) {
                this.model = model;
            }
        }

        @Param
        Type type;

        Model model;
        byte[] array;
        BufferedData bd;
        SlimBuffer slimBuffer;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            model = type.model;

            array = new byte[INVOCATIONS * model.maxSize];
            // For determinism:
            final Random random = new Random(723049435);
            bd = BufferedData.wrap(array);
            for (int i = 0, j = 0; i < INVOCATIONS; i++) {
                model.codec.write(model.factory.apply(random), bd);
            }
            bd.flip();
            slimBuffer = new SlimBuffer(array);
            slimBuffer.limit(bd.limit());
        }

        @TearDown(Level.Trial)
        public void tearDown() {}
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void bench(final BenchState state, final Blackhole blackhole) throws ParseException {
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(state.model.codec.parse(state.slimBuffer));
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(GenericParserQuickBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
