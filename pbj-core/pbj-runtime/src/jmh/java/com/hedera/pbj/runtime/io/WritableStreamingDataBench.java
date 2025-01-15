// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.io.ByteArrayOutputStream;
import java.util.Random;
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
import org.openjdk.jmh.annotations.Warmup;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 3, time = 5)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class WritableStreamingDataBench {

    private static final int SIZE = 100_000;

    private static final BufferedData BUF_TO_WRITE;

    static {
        final Random random = new Random(1234509876);
        byte[] data = new byte[SIZE];
        random.nextBytes(data);
        BUF_TO_WRITE = BufferedData.wrap(data);
    }

    @Setup(Level.Invocation)
    public void beforeEach() {
        BUF_TO_WRITE.reset();
    }

    // Tests WritableSequentialData.writeBytes(BufferedData)
    @Benchmark
    public void writeBufferedData() {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final WritableSequentialData out = new WritableStreamingData(bout);
        out.writeBytes(BUF_TO_WRITE);
        assert out.position() == SIZE;
        assert bout.toByteArray().length == SIZE;
    }

    // Tests WritableSequentialData.writeBytes(RandomAccessData)
    @Benchmark
    public void writeRandomAccessData() {
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final WritableSequentialData out = new WritableStreamingData(bout);
        out.writeBytes((RandomAccessData) BUF_TO_WRITE);
        assert out.position() == SIZE;
        assert bout.toByteArray().length == SIZE;
    }

}
