// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.pbj.integration.jmh.hashing.CountingArray;
import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench.HashAlgorithm;

/**
 * A test to evaluate the quality of non-cryptographic hash functions by checking 1MB of zeros with one bit moving
 * through it.
 */
@SuppressWarnings("DuplicatedCode")
public final class NonCryptographicHashQualityOneBitTest {
    public static void main(String[] args) {
        System.out.println("Testing non-cryptographic hash quality - 1 MB of zeros with one bit moving through it");
        final CountingArray[] counts = new CountingArray[HashAlgorithm.values().length];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = new CountingArray(); // 4 billion counts
        }
        final byte[] bigArray = new byte[1024 * 1024]; // 1MB of zeros
        final long[] TIMES = new long[HashAlgorithm.values().length];

        final long NUM_INPUTS = bigArray.length;
        double percent = 0;
        for (int i = 0; i < bigArray.length; i++) {
            if (i % 100 == 0) {
                double progress = (i * 100.0) / NUM_INPUTS;
                System.out.printf("\r       Progress: %.2f%%", progress);
                System.out.flush();
                if (progress > (percent + 10)) {
                    printResults(counts, NUM_INPUTS, TIMES);
                    percent += 10;
                }
            }
            bigArray[i] = 1; // set a bit to 1
            for (int h = 0; h < HashAlgorithm.values().length; h++) {
                final HashAlgorithm hashAlgorithm = HashAlgorithm.values()[h];
                final long startTime = System.nanoTime();
                final int hash = (int) hashAlgorithm.function.applyAsLong(bigArray, 0, bigArray.length);
                final long endTime = System.nanoTime();
                TIMES[h] += (endTime - startTime);
                counts[h].increment(Integer.toUnsignedLong(hash));
            }
            bigArray[i] = 0; // set a bit back to 0
        }

        printResults(counts, NUM_INPUTS, TIMES);
    }

    private static void printResults(CountingArray[] counts, long NUM_INPUTS, long[] TIMES) {
        final HashAlgorithm[] algorithms = HashAlgorithm.values();
        for (int h = 0; h < algorithms.length; h++) {
            final HashAlgorithm hashAlgorithm = algorithms[h];
            long numUniqueHashes = counts[h].numberOfGreaterThanZeroCounts();
            long hashCollisions = counts[h].numberOfGreaterThanOneCounts();
            double collisionRate = (double) hashCollisions / NUM_INPUTS * 100;
            double timeTaken = TIMES[h] / 1_000_000_000.0; // convert to seconds
            System.out.print("\n");
            System.out.printf(
                    "%20s --> Number of unique hashes: %,d, hash collisions: %,d, collision rate: %.2f%% time taken: %.3f seconds%n",
                    hashAlgorithm.name(), numUniqueHashes, hashCollisions, collisionRate, timeTaken);
            StringBuilder resultStr = new StringBuilder();
            counts[h].printStats(resultStr);
            System.out.print(resultStr);
        }
    }
}
