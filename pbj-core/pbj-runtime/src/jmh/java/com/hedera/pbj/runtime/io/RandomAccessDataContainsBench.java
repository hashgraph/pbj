// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import java.nio.ByteBuffer;
import java.util.Random;
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
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class RandomAccessDataContainsBench {

    private RandomAccessData directBufferedData;
    private RandomAccessData heapBufferedData;
    private byte[] smallPattern;
    private byte[] largePattern;
    private RandomAccessData patternData;

    @Setup
    public void setup() {
        final Random random = new Random(12345);

        // Create test data - 16KB of random bytes
        final byte[] testData = new byte[16 * 1024];
        random.nextBytes(testData);

        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(testData.length);
        directBuffer.put(testData);
        directBuffer.flip();
        directBufferedData = BufferedData.wrap(directBuffer);

        // Create heap-based BufferedData for comparison
        heapBufferedData = BufferedData.wrap(testData.clone());

        // Create patterns to search for
        smallPattern = new byte[8];
        System.arraycopy(testData, 1000, smallPattern, 0, 8);

        largePattern = new byte[256];
        System.arraycopy(testData, 2000, largePattern, 0, 256);

        // Create RandomAccessData pattern for testing contains(RandomAccessData)
        patternData = BufferedData.wrap(largePattern);
    }

    @Benchmark
    public void directBufferedDataContainsSmallByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(1000, smallPattern));
    }

    @Benchmark
    public void heapBufferedDataContainsSmallByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(1000, smallPattern));
    }

    @Benchmark
    public void directBufferedDataContainsLargeByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(2000, largePattern));
    }

    @Benchmark
    public void heapBufferedDataContainsLargeByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(2000, largePattern));
    }

    @Benchmark
    public void directBufferedDataMatchesPrefixSmall(Blackhole blackhole) {
        blackhole.consume(directBufferedData.matchesPrefix(smallPattern));
    }

    @Benchmark
    public void heapBufferedDataMatchesPrefixSmall(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.matchesPrefix(smallPattern));
    }

    @Benchmark
    public void directBufferedDataContainsRandomAccessData(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(2000, patternData));
    }

    @Benchmark
    public void heapBufferedDataContainsRandomAccessData(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(2000, patternData));
    }
}
