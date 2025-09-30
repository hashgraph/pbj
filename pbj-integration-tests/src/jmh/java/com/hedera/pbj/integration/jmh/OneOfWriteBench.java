// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.OneOfTestData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.InnerEverything;
import java.io.IOException;
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
 * Benchmarks for oneOf write performance across different case positions.
 * Measures write operations for messages with oneOf fields set to first, middle, last, and unset cases.
 *
 * <p>Run with: {@code ./gradlew jmh -Pinclude=OneOfWrite}
 */
@SuppressWarnings("unused")
@Fork(3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class OneOfWriteBench {

    // Test data with different oneOf positions
    private Everything withFirstCase;      // INT32_NUMBER_ONE_OF (field 100001)
    private Everything withEarlyCase;      // FLOAT_NUMBER_ONE_OF (field 100006)
    private Everything withMiddleCase;     // BOOLEAN_FIELD_ONE_OF (field 100013)
    private Everything withLateCase;       // TEXT_ONE_OF (field 100016)
    private Everything withLastCase;       // STRING_BOXED_ONE_OF (field 100026)
    private Everything withUnsetCase;      // No oneOf set

    // Output buffers - reused across iterations
    private byte[] buffer;
    private BufferedData bufferedData;
    private BufferedData bufferedDataDirect;

    @Setup
    public void setup() {
        // Load test data from OneOfTestData
        withFirstCase = OneOfTestData.FIRST_CASE;
        withEarlyCase = OneOfTestData.EARLY_CASE;
        withMiddleCase = OneOfTestData.MIDDLE_CASE;
        withLateCase = OneOfTestData.LATE_CASE;
        withLastCase = OneOfTestData.LAST_CASE;
        withUnsetCase = OneOfTestData.UNSET_CASE;

        // Allocate output buffers (sized to handle Everything message)
        buffer = new byte[2048];
        bufferedData = BufferedData.allocate(2048);
        bufferedDataDirect = BufferedData.allocateOffHeap(2048);
    }

    // ===== BufferedData Write Benchmarks =====

    @Benchmark
    public int writeFirstCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withFirstCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    @Benchmark
    public int writeEarlyCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withEarlyCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    @Benchmark
    public int writeMiddleCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withMiddleCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    @Benchmark
    public int writeLateCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withLateCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    @Benchmark
    public int writeLastCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withLastCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    @Benchmark
    public int writeUnsetCase(Blackhole bh) throws IOException {
        bufferedData.reset();
        Everything.PROTOBUF.write(withUnsetCase, bufferedData);
        bh.consume(bufferedData);
        return (int) bufferedData.position();
    }

    // ===== Byte Array Write Benchmarks =====

    @Benchmark
    public int writeFirstCaseByteArray(Blackhole bh) {
        int written = Everything.PROTOBUF.write(withFirstCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public int writeMiddleCaseByteArray(Blackhole bh) {
        int written = Everything.PROTOBUF.write(withMiddleCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    @Benchmark
    public int writeLastCaseByteArray(Blackhole bh) {
        int written = Everything.PROTOBUF.write(withLastCase, buffer, 0);
        bh.consume(buffer);
        return written;
    }

    // ===== Direct Buffer Write Benchmarks =====

    @Benchmark
    public int writeFirstCaseDirect(Blackhole bh) throws IOException {
        bufferedDataDirect.reset();
        Everything.PROTOBUF.write(withFirstCase, bufferedDataDirect);
        bh.consume(bufferedDataDirect);
        return (int) bufferedDataDirect.position();
    }

    @Benchmark
    public int writeLastCaseDirect(Blackhole bh) throws IOException {
        bufferedDataDirect.reset();
        Everything.PROTOBUF.write(withLastCase, bufferedDataDirect);
        bh.consume(bufferedDataDirect);
        return (int) bufferedDataDirect.position();
    }
}
