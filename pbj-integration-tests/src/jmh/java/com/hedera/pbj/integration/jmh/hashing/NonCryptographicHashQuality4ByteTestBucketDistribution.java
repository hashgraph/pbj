// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import com.hedera.pbj.integration.jmh.NonCryptographicHashingBench;
import java.util.Arrays;
import java.util.Map;

/**
 * A test to evaluate the quality of non-cryptographic hash functions
 * by checking how many unique hashes can be generated from 4-byte inputs.
 * It runs through all combinations of 4 bytes (256^4 = 4,294,967,296 combinations).
 */
public final class NonCryptographicHashQuality4ByteTestBucketDistribution {
    public static void main(String[] args) {
        System.out.println("Testing non-cryptographic hash quality - 4 bytes, 4 billion inputs");
        for (var hashAlgorithm : NonCryptographicHashingBench.HashAlgorithm.values()) {
            System.out.println("Testing " + hashAlgorithm.name() + " ====================================");
            testHashQuality4Bytes(hashAlgorithm);
        }
    }

    private static void testHashQuality4Bytes(NonCryptographicHashingBench.HashAlgorithm hashAlgorithm) {
        final long START_TIME = System.currentTimeMillis();
        final int[] bucketCounts = new int[33_554_432]; // 2^25 33 million buckets
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
                        long bucket = computeBucketIndex(hash32) ;
                        bucketCounts[(int)bucket]++;
                    }
                }
            }
        }
        // print the distribution of hash buckets sorted by bucket index
        // convert the bucketCounts into the number of buckets with each count
        Map<Integer,Integer> bucketDistribution = Arrays.stream(bucketCounts)
                .boxed()
                .collect(java.util.stream.Collectors.toMap(
//                        count -> count/1000, // Group counts by 1000 for better readability
                        count -> count,
                        count -> 1,
                        Integer::sum
                ));
        System.out.println("\n       Bucket distribution:");
        bucketDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> System.out.printf("       Count %d: %d buckets%n", entry.getKey(), entry.getValue()));

    }


    /**
     * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets
     * is a power of two. Based on same calculation that is used in java HashMap.
     *
     * @param keyHash the int hash for key
     * @return the index of the bucket that key falls in
     */
    private static int computeBucketIndex(final int keyHash) {
        return (33_554_432 - 1) & keyHash;
    }

}
