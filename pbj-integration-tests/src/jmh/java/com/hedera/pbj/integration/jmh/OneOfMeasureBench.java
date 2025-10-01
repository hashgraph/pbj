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
 * Benchmarks for oneOf size measurement operations.
 * Measures {@link com.hedera.pbj.runtime.Codec#measureRecord(Object)} performance across different oneOf positions.
 *
 * <p>Run with: {@code ./gradlew jmh -Pinclude=OneOfMeasure}
 */
@SuppressWarnings("unused")
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class OneOfMeasureBench {

    private Everything withFirstCase;
    private Everything withMiddleCase;
    private Everything withLastCase;
    private Everything withUnsetCase;
    private Everything[] variedCases;

    @Setup
    public void setup() {
        withFirstCase = OneOfTestData.FIRST_CASE;
        withMiddleCase = OneOfTestData.MIDDLE_CASE;
        withLastCase = OneOfTestData.LAST_CASE;
        withUnsetCase = OneOfTestData.UNSET_CASE;

        variedCases = new Everything[] {
            OneOfTestData.FIRST_CASE,
            OneOfTestData.EARLY_CASE,
            OneOfTestData.MIDDLE_CASE,
            OneOfTestData.LATE_CASE,
            OneOfTestData.LAST_CASE
        };
    }

    @Benchmark
    public int measureFirstCase(Blackhole bh) {
        int size = Everything.PROTOBUF.measureRecord(withFirstCase);
        bh.consume(size);
        return size;
    }

    @Benchmark
    public int measureMiddleCase(Blackhole bh) {
        int size = Everything.PROTOBUF.measureRecord(withMiddleCase);
        bh.consume(size);
        return size;
    }

    @Benchmark
    public int measureLastCase(Blackhole bh) {
        int size = Everything.PROTOBUF.measureRecord(withLastCase);
        bh.consume(size);
        return size;
    }

    @Benchmark
    public int measureUnsetCase(Blackhole bh) {
        int size = Everything.PROTOBUF.measureRecord(withUnsetCase);
        bh.consume(size);
        return size;
    }

    @Benchmark
    public void measureVariedCases(Blackhole bh) {
        for (Everything msg : variedCases) {
            int size = Everything.PROTOBUF.measureRecord(msg);
            bh.consume(size);
        }
    }

    @Benchmark
    public int measureAndWriteLastCase(Blackhole bh) {
        // Measure size
        int size = Everything.PROTOBUF.measureRecord(withLastCase);

        // Allocate buffer
        byte[] buffer = new byte[size];

        // Write
        int written = Everything.PROTOBUF.write(withLastCase, buffer, 0);

        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public void measureRepeated(Blackhole bh) {
        for (int i = 0; i < 10; i++) {
            int size = Everything.PROTOBUF.measureRecord(withLastCase);
            bh.consume(size);
        }
    }
}
