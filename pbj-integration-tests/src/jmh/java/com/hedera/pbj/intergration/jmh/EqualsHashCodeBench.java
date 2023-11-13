package com.hedera.pbj.intergration.jmh;

import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
// Add any other JMH annotation imports you use
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
public class EqualsHashCodeBench {
    private TimestampTest testStamp;
    private TimestampTest testStamp;
    
    private TimestampTest testStamp1;

    public EqualsHashCodeBench() {
        testStamp = new TimestampTest(987L, 123);
        testStamp1 = new TimestampTest(987L, 122);
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchHashCode(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.hashCode();
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchEquals(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.equals(testStamp);
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchNotEquals(Blackhole blackhole) throws IOException {
        for (int i = 0; i < 1050; i++) {
            testStamp.equals(testStamp1);
        }
    }
}