// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
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
    private byte[] pattern4Bytes;
    private byte[] pattern16Bytes;
    private byte[] pattern32Bytes;
    private byte[] pattern64Bytes;
    private byte[] pattern128Bytes;
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

        // Create patterns to search for - various sizes to test threshold performance
        pattern4Bytes = new byte[4];
        System.arraycopy(testData, 500, pattern4Bytes, 0, 4);

        smallPattern = new byte[8];
        System.arraycopy(testData, 1000, smallPattern, 0, 8);

        pattern16Bytes = new byte[16];
        System.arraycopy(testData, 1500, pattern16Bytes, 0, 16);

        pattern32Bytes = new byte[32];
        System.arraycopy(testData, 1800, pattern32Bytes, 0, 32);

        pattern64Bytes = new byte[64];
        System.arraycopy(testData, 2200, pattern64Bytes, 0, 64);

        pattern128Bytes = new byte[128];
        System.arraycopy(testData, 2500, pattern128Bytes, 0, 128);

        largePattern = new byte[256];
        System.arraycopy(testData, 2000, largePattern, 0, 256);

        // Create RandomAccessData pattern for testing contains(RandomAccessData)
        patternData = BufferedData.wrap(largePattern);
    }

    @Benchmark
    public void directBufferedDataContains4ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(500, pattern4Bytes));
    }

    @Benchmark
    public void heapBufferedDataContains4ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(500, pattern4Bytes));
    }

    @Benchmark
    public void directBufferedDataContains8ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(1000, smallPattern));
    }

    @Benchmark
    public void heapBufferedDataContains8ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(1000, smallPattern));
    }

    @Benchmark
    public void directBufferedDataContains16ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(1500, pattern16Bytes));
    }

    @Benchmark
    public void heapBufferedDataContains16ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(1500, pattern16Bytes));
    }

    @Benchmark
    public void directBufferedDataContains32ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(1800, pattern32Bytes));
    }

    @Benchmark
    public void heapBufferedDataContains32ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(1800, pattern32Bytes));
    }

    @Benchmark
    public void directBufferedDataContains64ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(2200, pattern64Bytes));
    }

    @Benchmark
    public void heapBufferedDataContains64ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(2200, pattern64Bytes));
    }

    @Benchmark
    public void directBufferedDataContains128ByteArray(Blackhole blackhole) {
        blackhole.consume(directBufferedData.contains(2500, pattern128Bytes));
    }

    @Benchmark
    public void heapBufferedDataContains128ByteArray(Blackhole blackhole) {
        blackhole.consume(heapBufferedData.contains(2500, pattern128Bytes));
    }

    // Keep existing methods for backward compatibility
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

    /* Original byte-by-byte implementation benchmarks for baseline comparison
     * These use a helper method to force the default RandomAccessData implementation */

    private boolean containsOriginalImpl(RandomAccessData data, long offset, byte[] bytes) {
        // Replicate the original default method implementation from RandomAccessData interface
        data.checkOffset(offset, data.length());
        if (data.length() - offset < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != data.getByte(offset + i)) {
                return false;
            }
        }
        return true;
    }

    @Benchmark
    public void directBufferedDataContainsOriginal4Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 500, pattern4Bytes));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal8Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 1000, smallPattern));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal16Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 1500, pattern16Bytes));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal32Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 1800, pattern32Bytes));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal64Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 2200, pattern64Bytes));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal128Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 2500, pattern128Bytes));
    }

    @Benchmark
    public void directBufferedDataContainsOriginal256Bytes(Blackhole blackhole) {
        blackhole.consume(containsOriginalImpl(directBufferedData, 2000, largePattern));
    }
}
