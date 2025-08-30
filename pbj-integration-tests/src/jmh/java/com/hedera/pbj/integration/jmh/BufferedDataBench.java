package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BufferedDataBench {
    public static final byte[] TEST_BYTES = "fooBar".getBytes();

    @SuppressWarnings("rawtypes")
    @State(Scope.Benchmark)
    public static class BufferedDataBenchState {
        private BufferedData smallBuffer;
        private BufferedData offsetBuffer;

        public BufferedDataBenchState() {
        }

        @Setup(Level.Invocation)
        public void setup() {
            smallBuffer = BufferedData.wrap(new byte[1024]);
            offsetBuffer = BufferedData.wrap(new byte[1024*4],1024, 2048);
        }

        @TearDown(Level.Invocation)
        public void tearDown() {
            smallBuffer = null;
            offsetBuffer = null;
        }
    }


    @Benchmark
    public void writeByteSmallBuffer(BufferedDataBenchState benchmarkState, Blackhole blackhole) {
        benchmarkState.smallBuffer.writeByte((byte) 100);
    }

    @Benchmark
    public void writeByteOffsetBuffer(BufferedDataBenchState benchmarkState, Blackhole blackhole) {
        benchmarkState.offsetBuffer.writeByte((byte) 100);
    }

    @Benchmark
    public void writeBytesSmallBuffer(BufferedDataBenchState benchmarkState, Blackhole blackhole) {
        benchmarkState.smallBuffer.writeBytes(TEST_BYTES);
    }

    @Benchmark
    public void writeBytesOffsetBuffer(BufferedDataBenchState benchmarkState, Blackhole blackhole) {
        benchmarkState.offsetBuffer.writeBytes(TEST_BYTES);
    }

}
