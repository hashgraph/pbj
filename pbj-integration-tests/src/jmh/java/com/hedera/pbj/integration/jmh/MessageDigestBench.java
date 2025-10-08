// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.hashing.WritableMessageDigest;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class MessageDigestBench {
    private static final int INVOCATIONS = 20_000;

    @State(Scope.Thread)
    public static class BenchState {
        // These are purely random indices. I've no idea what specific models are at which indices
        // and whether these particular ones are "good" or "bad" for the purpose of the bench.
        // Ideally, we'd run it for every model. However, the Java annotation parameter must be
        // a compile time constant, so it's impossible to infer all the possible indices from the
        // EverythingTest.ARGUMENTS directly here. So hard-coding a few random indices is our best bet here:
        @Param({"5", "10", "16", "51"})
        int everythingTestArgumentsIndex;

        MessageDigest messageDigest;

        WritableMessageDigest writableMessageDigest;

        @Setup
        public void setup() {
            try {
                messageDigest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            writableMessageDigest = new WritableMessageDigest(messageDigest);
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchMessageDigest(final BenchState state, final Blackhole blackhole) {
        final Everything model = EverythingTest.ARGUMENTS.get(state.everythingTestArgumentsIndex);

        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                final BufferedData bd = BufferedData.allocate(model.protobufSize());
                Everything.PROTOBUF.write(model, bd);
                bd.flip();
                final byte[] bytes = new byte[model.protobufSize()];
                bd.readBytes(bytes);
                state.messageDigest.update(bytes);
                blackhole.consume(state.messageDigest.digest());
            } catch (Exception e) {
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void benchWritableMessageDigest(final BenchState state, final Blackhole blackhole) {
        final Everything model = EverythingTest.ARGUMENTS.get(state.everythingTestArgumentsIndex);

        for (int i = 1; i <= INVOCATIONS; i++) {
            try {
                Everything.PROTOBUF.write(model, state.writableMessageDigest);
                blackhole.consume(state.messageDigest.digest());
            } catch (Exception e) {
                new RuntimeException(e).printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MessageDigestBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
