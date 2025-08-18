// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.pbj.integration.jmh.hashing.CountingArray;
import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench.HashAlgorithm;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;

/**
 * A test to evaluate the quality of non-cryptographic hash functions by checking how many unique hashes can be
 * generated from 4.5 billion 11-byte inputs.
 */
public final class NonCryptographicHashQuality11ByteTest {
    private static final int NUM_BUCKETS = 33_554_432; // 2^25 33 million buckets

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("Testing non-cryptographic hash quality - 11 bytes, 4.5 billion inputs");
        try (ForkJoinPool customPool = new ForkJoinPool(4)) { // limit to 4 threads
            customPool
                    .submit(() -> Arrays.stream(HashAlgorithm.values())
                            .parallel()
                            .forEach(hashAlgorithm -> {
                                final CountingArray counts = new CountingArray(); // 4 billion counts
                                System.out.println("Testing " + hashAlgorithm.name() + "...");
                                testHashQuality4Bytes(hashAlgorithm, counts);
                            }))
                    .get(); // handle exceptions as needed
        }
    }

    private static void testHashQuality4Bytes(HashAlgorithm hashAlgorithm, CountingArray counts) {
        final long START_TIME = System.currentTimeMillis();
        final long NUM_INPUTS = 4_500_000_000L; // 4.5 billion inputs
        final int NUM_BYTES = 11; // 11 bytes = 88 bits of data input
        final byte[] ba = new byte[NUM_BYTES];
        final int[] bucketCounts = new int[NUM_BUCKETS]; // 2^25 33 million buckets

        for (long i = 0; i < NUM_INPUTS; i++) {
            if (i % 10_000_000 == 0) {
                System.out.printf("\r       Progress: %.2f%%", (i * 100.0) / NUM_INPUTS);
                System.out.flush();
            }

            // Cascading increment - like an odometer
            // This ensures values are in batches and every byte changes
            boolean carry = true;
            for (int j = 0; j < NUM_BYTES && carry; j++) {
                if (ba[j] == (byte) 255) {
                    ba[j] = 1; // Reset to 1 (avoid 0)
                    carry = true; // Continue to next byte
                } else {
                    ba[j]++;
                    carry = false; // No carry needed
                }
            }

            final int hash32 = (int) hashAlgorithm.function.applyAsLong(ba, 0, NUM_BYTES);
            counts.increment(Integer.toUnsignedLong(hash32));
            long bucket = computeBucketIndex(hash32);
            bucketCounts[(int) bucket]++;
        }

        long numUniqueHashes = counts.numberOfGreaterThanZeroCounts();
        long hashCollisions = counts.numberOfGreaterThanOneCounts();
        double collisionRate = (double) hashCollisions / NUM_INPUTS * 100;
        final long END_TIME = System.currentTimeMillis();
        StringBuilder resultStr = new StringBuilder(String.format(
                "%n%s => Number of unique hashes: %,d, hash collisions: %,d, collision rate: %.2f%% time taken: %.3f seconds%n",
                hashAlgorithm.name(),
                numUniqueHashes,
                hashCollisions,
                collisionRate,
                (END_TIME - START_TIME) / 1000.0));
        counts.printStats(resultStr);
        // print the distribution of hash buckets sorted by bucket index
        // convert the bucketCounts into the number of buckets with each count
        Map<String, Integer> bucketDistribution = Arrays.stream(bucketCounts)
                .mapToObj(count -> {
                    if (count == 0) {
                        return "0";
                    } else if (count <= 10) {
                        return "1->10";
                    } else if (count <= 100) {
                        return "11->100";
                    } else if (count <= 1000) {
                        return "101->1,000";
                    } else if (count <= 10000) {
                        return "1,001->10,000";
                    } else if (count <= 100_000) {
                        return "10,001->100,000";
                    } else if (count <= 250_000) {
                        return "100,001->250,000";
                    } else if (count <= 500_000) {
                        return "250,001->500,000";
                    } else {
                        return "500,000+";
                    }
                })
                .collect(java.util.stream.Collectors.toMap(count -> count, count -> 1, Integer::sum));
        resultStr.append("      Bucket distribution: ");
        bucketDistribution.forEach((category, count) -> {
            resultStr.append(String.format("  %s=%,d", category, count));
        });
        resultStr.append("\n");
        // print the total number of buckets
        System.out.print(resultStr);
        System.out.flush();
    }

    /**
     * <p>Code direct from HalfDiskHashMap, only change is NUM_BUCKETS</p>
     *
     * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets
     * is a power of two. Based on same calculation that is used in java HashMap.
     *
     * @param keyHash the int hash for key
     * @return the index of the bucket that key falls in
     */
    private static int computeBucketIndex(final int keyHash) {
        return (NUM_BUCKETS - 1) & keyHash;
    }
}
