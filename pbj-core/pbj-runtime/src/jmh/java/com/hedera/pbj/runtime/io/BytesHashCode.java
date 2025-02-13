package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
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
public class BytesHashCode {

    private static final Random RANDOM = new Random(98765);

    @Param({"10000"})
    public int size = 10000;

    private List<Bytes> arrays;
    private List<Bytes> slices;

    @Setup(Level.Trial)
    public void init() {
        if (arrays == null) {
            arrays = new ArrayList<>(size);
            slices = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                final int len = RANDOM.nextInt(99) + 1;
                final byte[] array = new byte[len];
                for (int j = 0; j < len; j++) {
                    array[j] = (byte) RANDOM.nextInt();
                }
                final Bytes b = Bytes.wrap(array);
                arrays.add(b);
                final int blen = (int) b.length();
                if (blen < 2) {
                    slices.add(b);
                } else {
                    final int start = RANDOM.nextInt(blen / 2);
                    final int length = RANDOM.nextInt(blen - start);
                    slices.add(b.slice(start, length));
                }
            }
        }
    }

    @Benchmark
    public void testHashCode(final Blackhole blackhole) {
        for (final Bytes array : arrays) {
            blackhole.consume(array.hashCode());
        }
    }

    @Benchmark
    public void testSlicedHashCode(final Blackhole blackhole) {
        for (final Bytes bytes : slices) {
            blackhole.consume(bytes.hashCode());
        }
    }

    public static void main(String[] args) {
        final Blackhole blackhole = new Blackhole(
                "Today's password is swordfish. I understand instantiating Blackholes directly is dangerous.");
        final BytesHashCode bench = new BytesHashCode();
        bench.init();
        for (int i = 0; i < 100000; i++) {
            bench.testHashCode(blackhole);
            bench.testSlicedHashCode(blackhole);
        }
    }
}
