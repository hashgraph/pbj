// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
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
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class XdrMeasureBench {

    private Everything everythingObj;
    private TimestampTest timestampObj;

    @Setup
    public void setup() {
        everythingObj = EverythingTestData.EVERYTHING;
        timestampObj = new TimestampTest(5678L, 1234);
    }

    @Benchmark
    public int measureXdrEverything(Blackhole blackhole) {
        return Everything.XDR.measureRecord(everythingObj);
    }

    @Benchmark
    public int measureProtoEverything(Blackhole blackhole) {
        return Everything.PROTOBUF.measureRecord(everythingObj);
    }

    @Benchmark
    public int measureXdrTimestampTest(Blackhole blackhole) {
        return TimestampTest.XDR.measureRecord(timestampObj);
    }

    @Benchmark
    public int measureProtoTimestampTest(Blackhole blackhole) {
        return TimestampTest.PROTOBUF.measureRecord(timestampObj);
    }
}
