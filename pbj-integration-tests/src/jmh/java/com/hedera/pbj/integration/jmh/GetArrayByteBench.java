// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import sun.misc.Unsafe;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class GetArrayByteBench {
    /// Num of invocations per measurement method call.
    private static final int INVOCATIONS = 20 * 1024;
    /// Size of working arrays/buffers. MUST be greater than INVOCATIONS above
    private static final int SIZE = INVOCATIONS * 2;

    // Local Unsafe setup to avoid going to the OldUnsafeUtils and inline Unsafe calls directly instead:
    private static final Unsafe UNSAFE;
    private static final int BYTE_ARRAY_BASE_OFFSET;

    static {
        try {
            final Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    // Local VarHandle setup to avoid going to the UnsafeUtils and inline VarHandle calls directly instead:
    private static final VarHandle BYTE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);

    @State(Scope.Thread)
    public static class BenchState {
        byte[] array;

        // Maintain the sum in state to avoid JVM putting it into a CPU register.
        // Make it a byte, and not a long. We don't care about overflows here.
        // But we don't want to touch more significant bytes either.
        byte sum;

        @Setup(Level.Trial)
        public void setup() {
            array = new byte[SIZE];
            Random random = new Random(349572654);
            randomize(random);
        }

        @TearDown(Level.Trial)
        public void tearDown() {}

        private void randomize(Random random) {
            random.nextBytes(array);
        }
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_OldUnsafeUtils(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int i = 0; i < INVOCATIONS; i++) {
            state.sum += OldUnsafeUtils.getArrayByteNoChecks(state.array, i);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_DirectUnsafeCall(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int i = 0; i < INVOCATIONS; i++) {
            state.sum += UNSAFE.getByte(state.array, BYTE_ARRAY_BASE_OFFSET + i);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_NewUnsafeUtils(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int i = 0; i < INVOCATIONS; i++) {
            state.sum += UnsafeUtils.getArrayByteNoChecks(state.array, i);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByteNoChecks_DirectVarHandleCall(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int i = 0; i < INVOCATIONS; i++) {
            state.sum += (byte) BYTE_ARRAY_HANDLE.get(state.array, i);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void getArrayByte_SimpleJava(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int i = 0; i < INVOCATIONS; i++) {
            state.sum += state.array[i];
        }
        blackhole.consume(state.sum);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(GetArrayByteBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
