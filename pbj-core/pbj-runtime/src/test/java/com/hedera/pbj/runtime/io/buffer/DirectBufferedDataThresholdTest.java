// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Simple performance testing utility to determine optimal threshold for DirectBufferedData.contains().
 * This is much faster than full JMH benchmarks for quick threshold evaluation.
 *
 * <p>Usage:
 * <pre>
 * # Compile classes first
 * ./gradlew :pbj-runtime:compileTestJava
 *
 * # Run threshold analysis
 * cd pbj-runtime && java -cp "build/classes/java/main:build/classes/java/test" \
 *   com.hedera.pbj.runtime.io.buffer.DirectBufferedDataThresholdTest
 * </pre>
 *
 * <p>The test output shows:
 * <ul>
 * <li>Performance comparison between optimized vs original implementations</li>
 * <li>Threshold analysis showing optimal crossover points</li>
 * <li>Speedup ratios for different pattern sizes</li>
 * </ul>
 */
public class DirectBufferedDataThresholdTest {

    private static final int ITERATIONS = 100_000;
    private static final int DATA_SIZE = 16 * 1024; // 16KB test data
    private static final long TEST_OFFSET = 1000; // Consistent offset for all tests

    private RandomAccessData directBufferedData;
    private RandomAccessData heapBufferedData;
    private final Random random = new Random(12345);

    // Volatile field to prevent dead code elimination in performance tests
    private volatile boolean resultSink;

    public void setup() {
        // Create test data
        final byte[] testData = new byte[DATA_SIZE];
        random.nextBytes(testData);

        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(testData.length);
        directBuffer.put(testData);
        directBuffer.flip();
        directBufferedData = BufferedData.wrap(directBuffer);

        heapBufferedData = BufferedData.wrap(testData.clone());
    }

    /**
     * Test the performance of DirectBufferedData.contains() vs original byte-by-byte approach
     * for different pattern sizes to determine optimal threshold.
     */
    public void testThresholdPerformance() {
        setup();

        int[] patternSizes = {4, 8, 16, 32, 64, 128, 256};

        System.out.println("DirectBufferedData Contains Performance Test");
        System.out.println("============================================");
        System.out.printf(
                "%-12s %-15s %-15s %-15s %-10s%n",
                "Pattern Size", "Optimized (ms)", "Original (ms)", "Heap (ms)", "Speedup");
        System.out.println("------------------------------------------------------------");

        for (int size : patternSizes) {
            testPatternSize(size);
        }
    }

    private void testPatternSize(int size) {
        // Create pattern from known location in test data
        byte[] pattern = new byte[size];
        heapBufferedData.getBytes(TEST_OFFSET, pattern);

        // Test optimized DirectBufferedData implementation
        long startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            resultSink = directBufferedData.contains(TEST_OFFSET, pattern);
        }
        long optimizedTime = System.nanoTime() - startTime;

        // Test original byte-by-byte implementation
        startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            containsOriginalImpl(directBufferedData, pattern);
        }
        long originalTime = System.nanoTime() - startTime;

        // Test heap implementation for comparison
        startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            resultSink = heapBufferedData.contains(TEST_OFFSET, pattern);
        }
        long heapTime = System.nanoTime() - startTime;

        // Calculate results
        double optimizedMs = optimizedTime / 1_000_000.0;
        double originalMs = originalTime / 1_000_000.0;
        double heapMs = heapTime / 1_000_000.0;
        double speedup = originalMs / optimizedMs;

        System.out.printf("%-12d %-15.2f %-15.2f %-15.2f %-10.2fx%n", size, optimizedMs, originalMs, heapMs, speedup);
    }

    /**
     * Replicate the original default method implementation from RandomAccessData interface.
     * Uses void return and volatile field to prevent dead code elimination during benchmarks.
     */
    private void containsOriginalImpl(RandomAccessData data, byte[] bytes) {
        data.checkOffset(TEST_OFFSET, data.length());
        if (data.length() - TEST_OFFSET < bytes.length) {
            resultSink = false;
            return;
        }
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != data.getByte(TEST_OFFSET + i)) {
                resultSink = false;
                return;
            }
        }
        resultSink = true;
    }

    /**
     * Test different threshold values by simulating the threshold logic
     */
    public void testThresholdValues() {
        setup();

        int[] thresholds = {4, 8, 16, 32, 64};
        int[] testSizes = {4, 8, 12, 16, 24, 32, 48, 64, 96, 128};

        System.out.println("\nThreshold Analysis");
        System.out.println("==================");
        System.out.printf("%-12s", "Pattern Size");
        for (int threshold : thresholds) {
            System.out.printf(" %-12s", "Thresh=" + threshold);
        }
        System.out.println();
        System.out.println("----------------------------------------------------------------");

        for (int size : testSizes) {
            System.out.printf("%-12d", size);

            byte[] pattern = new byte[size];
            heapBufferedData.getBytes(TEST_OFFSET, pattern);

            for (int threshold : thresholds) {
                long startTime = System.nanoTime();

                for (int i = 0; i < ITERATIONS; i++) {
                    if (size <= threshold) {
                        // Use original implementation
                        containsOriginalImpl(directBufferedData, pattern);
                    } else {
                        // Use optimized implementation
                        resultSink = directBufferedData.contains(TEST_OFFSET, pattern);
                    }
                }

                long time = System.nanoTime() - startTime;
                double ms = time / 1_000_000.0;
                System.out.printf(" %-12.2f", ms);
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        DirectBufferedDataThresholdTest test = new DirectBufferedDataThresholdTest();
        test.testThresholdPerformance();
        test.testThresholdValues();
    }
}
