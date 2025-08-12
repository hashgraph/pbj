// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A test to evaluate the quality of non-cryptographic hash functions
 * by checking how many unique hashes can be generated from 4-byte inputs.
 * It runs through all combinations of 4 bytes (256^4 = 4,294,967,296 combinations).
 */
public final class NonCryptographicHashQuality4ByteTestBucketDistribution {
    private static final int NUM_BUCKETS = 33_554_432; // 2^25 33 million buckets

    public static void main(String[] args) {
        System.out.println("Testing non-cryptographic hash quality - 4 bytes, 4 billion inputs");
        List<String> results = Arrays.stream(NonCryptographicHashingBench.HashAlgorithm.values())
                .parallel()
                .map(hashAlgorithm -> {
                    System.out.println("Testing " + hashAlgorithm.name() + "...");
                    return testHashQuality4Bytes(hashAlgorithm);
                })
                .toList();
        // Print all results
        results.forEach(System.out::println);
    }

    private static String testHashQuality4Bytes(NonCryptographicHashingBench.HashAlgorithm hashAlgorithm) {
        final int[] bucketCounts = new int[NUM_BUCKETS]; // 2^25 33 million buckets
        final byte[] ba = new byte[4];
        for (int i = 0; i < 256; i++) {
            // print progress as percentage, overwriting the same line
            System.out.printf("\r       Progress: %d%%", (i * 100) / 256);
            System.out.flush();
            for (int j = 0; j < 256; j++) {
                for (int k = 0; k < 256; k++) {
                    for (int l = 0; l < 256; l++) {
                        ba[0] = (byte) i;
                        ba[1] = (byte) j;
                        ba[2] = (byte) k;
                        ba[3] = (byte) l;
                        long hash64 = hashAlgorithm.function.applyAsLong(ba, 0, 4);
                        int hash32 = (int) hash64;
                        long bucket = computeBucketIndex(hash32);
                        bucketCounts[(int) bucket]++;
                    }
                }
            }
        }
        // print the distribution of hash buckets sorted by bucket index
        // convert the bucketCounts into the number of buckets with each count
        Map<Integer, Integer> bucketDistribution = Arrays.stream(bucketCounts)
                .boxed()
                .collect(java.util.stream.Collectors.toMap(count -> count, count -> 1, Integer::sum));
        StringBuilder resultStr = new StringBuilder(hashAlgorithm.name() + " Bucket distribution:\n");
        bucketDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> resultStr.append(
                        String.format("       Count %d: %d buckets%n", entry.getKey(), entry.getValue())));
        return resultStr.toString();
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
