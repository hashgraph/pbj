package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Random;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@Fork(value = 1)
@State(Scope.Benchmark)
public class BytesGetLong {

    @Param({"10000"})
    public int size = 10000;

    private byte[] array;

    private boolean printSum;

    @Setup(Level.Trial)
    public void init() {
        System.out.println("Initializing array, size = " + size);
        array = new byte[size];
        final Random r = new Random(size);
        for (int i = 0; i < size; i++) {
            array[i] = (byte) r.nextInt(127);
        }
    }

    @Setup(Level.Iteration)
    public void initEach() {
        printSum = true;
    }

    @Benchmark
    public void testBytesGetLong(final Blackhole blackhole) {
        long sum = 0;
        final Bytes bytes = Bytes.wrap(array);
        for (int i = 0; i < size + 1 - Long.BYTES; i++) {
            sum += bytes.getLong(i);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void testUnsafeGetLong(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size + 1 - Long.BYTES; i++) {
            sum += UnsafeUtils.getLong(array, i);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

}
