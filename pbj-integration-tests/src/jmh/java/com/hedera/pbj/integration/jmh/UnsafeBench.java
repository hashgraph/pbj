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
@Fork(1)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class UnsafeBench {
    private static final int INVOCATIONS = 20_000;

    private static final int SIZE = 1024 * 1024;

    private static final byte[] ARRAY = new byte[SIZE];
    private static final ByteBuffer HEAP_BUFFER = ByteBuffer.allocate(SIZE);
    private static final ByteBuffer DIRECT_BUFFER = ByteBuffer.allocateDirect(SIZE);
    private static final ByteBuffer DIRECT_BUFFER_2 = ByteBuffer.allocateDirect(SIZE);

    private static void randomize(Random random) {
        random.nextBytes(ARRAY);
        HEAP_BUFFER.put(ARRAY);
        HEAP_BUFFER.clear();

        random.nextBytes(ARRAY);
        DIRECT_BUFFER.put(ARRAY);
        DIRECT_BUFFER.clear();

        random.nextBytes(ARRAY);
        DIRECT_BUFFER_2.put(ARRAY);
        DIRECT_BUFFER_2.clear();

        random.nextBytes(ARRAY);
    }

    @State(Scope.Thread)
    public static class BenchState {
        Random random;

        @Setup(Level.Invocation)
        public void setup() {
            random = new Random(349572654);
            randomize(random);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            random = null;
        }
    }

    @State(Scope.Thread)
    public static class ByteOrderBenchState {
        @Param({"false", "true"})
        boolean littleEndian;

        Random random;
        ByteOrder byteOrder;

        @Setup(Level.Invocation)
        public void setup() {
            random = new Random(349572654);
            randomize(random);
            byteOrder = littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            random = null;
            byteOrder = null;
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getArrayByteNoChecks(ARRAY, state.random.nextInt(ARRAY.length));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getArrayByteNoChecks(ARRAY, state.random.nextInt(ARRAY.length));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getHeapBufferByteNoChecks(HEAP_BUFFER, state.random.nextInt(SIZE));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getHeapBufferByteNoChecks(HEAP_BUFFER, state.random.nextInt(SIZE));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferByteNoChecks_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getDirectBufferByteNoChecks(DIRECT_BUFFER, state.random.nextInt(SIZE));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferByteNoChecks_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getDirectBufferByteNoChecks(DIRECT_BUFFER, state.random.nextInt(SIZE));
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getInt_Old(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getInt(ARRAY, state.random.nextInt(SIZE - Integer.BYTES), state.byteOrder);
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getInt_New(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getInt(ARRAY, state.random.nextInt(SIZE - Integer.BYTES), state.byteOrder);
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getLong_Old(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            OldUnsafeUtils.getLong(ARRAY, state.random.nextInt(SIZE - Long.BYTES), state.byteOrder);
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getLong_New(final ByteOrderBenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            UnsafeUtils.getLong(ARRAY, state.random.nextInt(SIZE - Long.BYTES), state.byteOrder);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferToArray_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            OldUnsafeUtils.getHeapBufferToArray(HEAP_BUFFER, offset, ARRAY, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getHeapBufferToArray_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            UnsafeUtils.getHeapBufferToArray(HEAP_BUFFER, offset, ARRAY, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToArray_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            OldUnsafeUtils.getDirectBufferToArray(DIRECT_BUFFER, offset, ARRAY, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToArray_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            UnsafeUtils.getDirectBufferToArray(DIRECT_BUFFER, offset, ARRAY, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToDirectBuffer_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            OldUnsafeUtils.getDirectBufferToDirectBuffer(DIRECT_BUFFER, offset, DIRECT_BUFFER_2, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void getDirectBufferToDirectBuffer_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int dstOffset = state.random.nextInt(SIZE - length);
            UnsafeUtils.getDirectBufferToDirectBuffer(DIRECT_BUFFER, offset, DIRECT_BUFFER_2, dstOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void putByteArrayToDirectBuffer_Old(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int srcOffset = state.random.nextInt(SIZE - length);
            OldUnsafeUtils.putByteArrayToDirectBuffer(DIRECT_BUFFER, offset, ARRAY, srcOffset, length);
        }
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(INVOCATIONS)
    public void putByteArrayToDirectBuffer_New(final BenchState state, final Blackhole blackhole) {
        for (int i = 0; i < INVOCATIONS; i++) {
            final int offset = state.random.nextInt(SIZE);
            final int length = state.random.nextInt(SIZE - offset);
            final int srcOffset = state.random.nextInt(SIZE - length);
            UnsafeUtils.putByteArrayToDirectBuffer(DIRECT_BUFFER, offset, ARRAY, srcOffset, length);
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder().include(UnsafeBench.class.getSimpleName()).build();

        new Runner(opt).run();
    }
}
