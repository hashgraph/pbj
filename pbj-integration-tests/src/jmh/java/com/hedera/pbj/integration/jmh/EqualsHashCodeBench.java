// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
// Add any other JMH annotation imports you use
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/*
Mac Results

Benchmark                                     Mode  Cnt  Score   Error  Units
EqualsHashCodeBench.benchJavaRecordHashCode   avgt    5  0.461 ± 0.007  ns/op
EqualsHashCodeBench.benchHashCode             avgt    5  1.936 ± 0.019  ns/op   --- 4.2x slower

EqualsHashCodeBench.benchJavaRecordEquals     avgt    5  0.664 ± 0.006  ns/op
EqualsHashCodeBench.benchEquals               avgt    5  0.585 ± 0.009  ns/op   --- 1.1x slower

EqualsHashCodeBench.benchJavaRecordNotEquals  avgt    5  0.508 ± 0.004  ns/op
EqualsHashCodeBench.benchNotEquals            avgt    5  0.655 ± 0.004  ns/op   --- 1.3x slower
 */

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class EqualsHashCodeBench {
    public record TimestampStandardRecord(long seconds, int nanos) {}

    private final TimestampTest testStamp;
    private final TimestampTest testStamp1;
    private final TimestampTest testStampDifferent;
    private final TimestampStandardRecord timestampStandardRecord;
    private final TimestampStandardRecord timestampStandardRecord1;
    private final TimestampStandardRecord timestampStandardRecordDifferent;

    public EqualsHashCodeBench() {
        testStamp = new TimestampTest(987L, 123);
        testStamp1 = new TimestampTest(987L, 123);
        testStampDifferent = new TimestampTest(987L, 122);
        timestampStandardRecord = new TimestampStandardRecord(987L, 123);
        timestampStandardRecord1 = new TimestampStandardRecord(987L, 123);
        timestampStandardRecordDifferent = new TimestampStandardRecord(987L, 122);
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchHashCode(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(testStamp.hashCode());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordHashCode(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(timestampStandardRecord.hashCode());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(testStamp.equals(testStamp1));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(timestampStandardRecord.equals(timestampStandardRecord1));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchNotEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(testStamp.equals(testStampDifferent));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordNotEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(timestampStandardRecord.equals(timestampStandardRecordDifferent));
        }
    }
}
