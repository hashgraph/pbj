package com.hedera.pbj.intergration.jmh;

import com.hedera.pbj.intergration.test.TestHashFunctions;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class HashBench {
    private Hasheval hasheval;

    public HashBench() {
        TimestampTest tst = new TimestampTest(987L, 123);
        hasheval = new Hasheval(1, -1, 2, 3, -2,
                                123f, 7L, -7L, 123L, 234L,
                                -345L, 456.789D, true, Suit.ACES, tst, "FooBarKKKKHHHHOIOIOI",
                                 Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void hashBenchSHA256(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            TestHashFunctions.hash1(hasheval);
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void hashBenchFieldWise(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            TestHashFunctions.hash2(hasheval);
        }
    }
}