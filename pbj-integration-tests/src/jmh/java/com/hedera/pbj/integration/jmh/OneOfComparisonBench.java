// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.OneOfTestData;
import com.hedera.pbj.test.proto.pbj.Everything;
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

/**
 * Comparison benchmark for oneOf write performance.
 * Measures best case, average case, worst case, and varied workloads.
 *
 * <p>Run with: {@code ./gradlew jmh -Pinclude=OneOfComparison}
 */
@SuppressWarnings("unused")
@Fork(3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@State(Scope.Benchmark)
public class OneOfComparisonBench {

    private Everything[] variedCases;
    private Everything bestCase;
    private Everything averageCase;
    private Everything worstCase;
    private byte[] buffer;

    @Setup
    public void setup() {
        // Best case: first oneOf field (field 100001)
        bestCase = OneOfTestData.FIRST_CASE;

        // Average case: middle oneOf field (field 100013)
        averageCase = OneOfTestData.MIDDLE_CASE;

        // Worst case: last oneOf field (field 100026)
        worstCase = OneOfTestData.LAST_CASE;

        // Array with varied positions for average measurement
        variedCases = new Everything[]{
            OneOfTestData.FIRST_CASE,    // Position 1/26
            OneOfTestData.EARLY_CASE,    // Position 6/26
            OneOfTestData.MIDDLE_CASE,   // Position 13/26
            OneOfTestData.LATE_CASE,     // Position 16/26
            OneOfTestData.LAST_CASE      // Position 26/26
        };

        buffer = new byte[2048];
    }

    @Benchmark
    public int writeWorstCase(Blackhole bh) {
        int written = Everything.PROTOBUF.write(worstCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public int writeBestCase(Blackhole bh) {
        int written = Everything.PROTOBUF.write(bestCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public int writeAverageCase(Blackhole bh) {
        int written = Everything.PROTOBUF.write(averageCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public void writeVariedPositions(Blackhole bh) {
        for (Everything msg : variedCases) {
            int written = Everything.PROTOBUF.write(msg, buffer, 0);
            bh.consume(written);
        }
    }

    @Benchmark
    public void writeRepeatedWorstCase(Blackhole bh) {
        for (int i = 0; i < 5; i++) {
            int written = Everything.PROTOBUF.write(worstCase, buffer, 0);
            bh.consume(written);
        }
    }

    @Benchmark
    public void writeFirstAndLast(Blackhole bh) {
        int written1 = Everything.PROTOBUF.write(bestCase, buffer, 0);
        bh.consume(written1);

        int written2 = Everything.PROTOBUF.write(worstCase, buffer, 0);
        bh.consume(written2);
    }
}
