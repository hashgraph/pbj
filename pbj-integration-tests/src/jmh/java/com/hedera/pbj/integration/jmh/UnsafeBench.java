// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;
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

/// A benchmark to compare the old UnsafeUtils and their updated implementations in PBJ 0.15.0
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class UnsafeBench {
    /// Num of invocations per measurement method call. MUST be smaller than SIZE below
    private static final int INVOCATIONS = 20 * 1024;
    /// Size of working arrays/buffers. MUST be greater than INVOCATIONS above
    private static final int SIZE = INVOCATIONS * 2;
    /// Length of sub-sequences for copying between buffers/arrays. MUST be smaller than SIZE above
    private static final int LENGTH = SIZE / 4;

    @State(Scope.Thread)
    public static class BenchState {
        byte[] array;
        ByteBuffer heapBuffer;
        ByteBuffer directBuffer;
        ByteBuffer directBuffer2;

        @Setup(Level.Trial)
        public void setup() {
            array = new byte[SIZE];
            heapBuffer = ByteBuffer.allocate(SIZE);
            directBuffer = ByteBuffer.allocateDirect(SIZE);
            directBuffer2 = ByteBuffer.allocateDirect(SIZE);

            Random random = new Random(349572654);
            randomize(random);
        }

        @TearDown(Level.Trial)
        public void tearDown() {}

        private void randomize(Random random) {
            random.nextBytes(array);
            heapBuffer.put(array);
            heapBuffer.clear();

            random.nextBytes(array);
            directBuffer.put(array);
            directBuffer.clear();

            random.nextBytes(array);
            directBuffer2.put(array);
            directBuffer2.clear();

            random.nextBytes(array);
        }
    }

    @State(Scope.Thread)
    public static class ByteOrderBenchState {
        @Param({"false", "true"})
        boolean littleEndian;

        byte[] array;
        ByteOrder byteOrder;

        @Setup(Level.Trial)
        public void setup() {
            array = new byte[SIZE];
            Random random = new Random(349572654);
            random.nextBytes(array);
            byteOrder = littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            byteOrder = null;
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(OldUnsafeUtils.getArrayByteNoChecks(state.array, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(UnsafeUtils.getArrayByteNoChecks(state.array, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(OldUnsafeUtils.getHeapBufferByteNoChecks(state.heapBuffer, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(UnsafeUtils.getHeapBufferByteNoChecks(state.heapBuffer, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(OldUnsafeUtils.getDirectBufferByteNoChecks(state.directBuffer, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(UnsafeUtils.getDirectBufferByteNoChecks(state.directBuffer, i));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getInt_Old(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(OldUnsafeUtils.getInt(state.array, i, state.byteOrder));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getInt_New(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(UnsafeUtils.getInt(state.array, i, state.byteOrder));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getLong_Old(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(OldUnsafeUtils.getLong(state.array, i, state.byteOrder));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getLong_New(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            blackhole.consume(UnsafeUtils.getLong(state.array, i, state.byteOrder));
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferToArray_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getHeapBufferToArray(state.heapBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.array);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferToArray_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getHeapBufferToArray(state.heapBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.array);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToArray_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getDirectBufferToArray(state.directBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.array);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToArray_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getDirectBufferToArray(state.directBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.array);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToDirectBuffer_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getDirectBufferToDirectBuffer(state.directBuffer, i, state.directBuffer2, i, LENGTH);
            blackhole.consume(state.directBuffer2);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToDirectBuffer_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getDirectBufferToDirectBuffer(state.directBuffer, i, state.directBuffer2, i, LENGTH);
            blackhole.consume(state.directBuffer2);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void putByteArrayToDirectBuffer_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.putByteArrayToDirectBuffer(state.directBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.directBuffer);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void putByteArrayToDirectBuffer_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.putByteArrayToDirectBuffer(state.directBuffer, i, state.array, i, LENGTH);
            blackhole.consume(state.directBuffer);
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder().include(UnsafeBench.class.getSimpleName()).build();

        new Runner(opt).run();
    }
}
