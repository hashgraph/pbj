package com.hedera.pbj.runtime.io;

import java.nio.ByteOrder;
import java.util.Random;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@Fork(value = 1)
@State(Scope.Benchmark)
public class ByteOrderEquals {

    private static final int SIZE = 10000;

    final ByteOrder[] array = new ByteOrder[SIZE];

    @Setup(Level.Trial)
    public void init() {
        final Random r = new Random(1024);
        for (int i = 0; i < SIZE; i++) {
            array[i] = r.nextBoolean() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        }
    }

    @Benchmark
    public void testEqualsEquals(final Blackhole blackhole) {
        boolean value = false;
        for (int i = 0; i < SIZE; i++) {
            value = value ^ (array[i] == ByteOrder.BIG_ENDIAN);
        }
        blackhole.consume(value);
    }

    @Benchmark
    public void testObjectEquals(final Blackhole blackhole) {
        boolean value = false;
        for (int i = 0; i < SIZE; i++) {
            value = value ^ (array[i].equals(ByteOrder.BIG_ENDIAN));
        }
        blackhole.consume(value);
    }
}
