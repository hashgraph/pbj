package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 4)
@Measurement(iterations = 10, time = 4)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class NonCryptographicHashingBench {
    public static final int DATA_SIZE = 10; // Size of the byte array to hash
    public static final int SAMPLES = 1000;

    private Random random;
    private byte[][] sampleBytes;


    @Setup
    public void setup() {
        random =new Random(6351384163846453326L);
        sampleBytes = new byte[SAMPLES][DATA_SIZE];
        for (int i = 0; i < SAMPLES; i++) {
            sampleBytes[i] = new byte[DATA_SIZE];
            random.nextBytes(sampleBytes[i]);
        }
    }

    @Benchmark
    public void hashCodeOriginal(Blackhole blackhole){
        byte[] bytes = sampleBytes[random.nextInt(SAMPLES)];
        blackhole.consume(oldBytesHashCode(bytes,0,DATA_SIZE));
    }

    @Benchmark
    public void hashCodeNonCryptographicHashing(Blackhole blackhole){
        byte[] bytes = sampleBytes[random.nextInt(SAMPLES)];
        blackhole.consume(NonCryptographicHashing.hash64(bytes, 0, DATA_SIZE));
    }

    @Benchmark
    public void hashCodeNonCryptographicHashingVarHandle(Blackhole blackhole){
        byte[] bytes = sampleBytes[random.nextInt(SAMPLES)];
        blackhole.consume(hash64VarHandle(bytes, 0, DATA_SIZE));
    }


    /**
     * Old hashcode implementation for Bytes from before it used NonCryptographicHashing. Slightly different as it is
     * done from outside the Bytes class.
     */
    public int oldBytesHashCode(@NonNull final byte[] bytes, int start, int length) {
        int h = 1;
        for (int i = length - 1; i >= start; i--) {
            h = 31 * h + bytes[i];
        }
        return h;
    }

    public static long hash64VarHandle(@NonNull final byte[] bytes, int start, int length) {
        long hash = perm64(length);
        for (int i = start; i < length; i += 8) {
            hash = perm64(hash ^ byteArrayToLong(bytes, i));
        }
        return hash;
    }

    private static long perm64(long x) {
        // This is necessary so that 0 does not hash to 0.
        // As a side effect, this constant will hash to 0.
        // It was randomly generated (not using Java),
        // so that it will occur in practice less often than more
        // common numbers like 0 or -1 or Long.MAX_VALUE.
        x ^= 0x5e8a016a5eb99c18L;

        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }

    public static long byteArrayToLong(final byte[] data, final int position) {
        if (data.length > position + 8) {
            return (long) vh.get(data, position);
        } else {
            // There isn't enough data to fill the long, so pad with zeros.
            long result = 0;
            for (int offset = 0; offset < 8; offset++) {
                final int index = position + offset;
                if (index >= data.length) {
                    break;
                }
                result += (data[index] & 0xffL) << (8 * (7 - offset));
            }
            return result;
        }
    }

    private static final VarHandle vh = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
}
