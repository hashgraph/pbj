// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench;
import java.util.HashSet;
import java.util.Set;

/**
 * A test to evaluate the quality of non-cryptographic hash functions
 * by checking how many unique hashes can be generated from 11-byte inputs.
 * It runs through all 500 million combinations.
 */
public final class NonCryptographicHashQualityTest {
    public static void main(String[] args) {
        System.out.println("Testing non-cryptographic hash quality - 11 bytes, 500 million inputs");
        for (var hashAlgorithm : NonCryptographicHashingBench.HashAlgorithm.values()) {
            System.out.println("Testing " + hashAlgorithm.name() + " ====================================");
            testHashQuality11Bytes2BillionInt(hashAlgorithm);
        }
        System.out.println("Testing non-cryptographic hash quality - 11 bytes, 500 million inputs");
        for (var hashAlgorithm : NonCryptographicHashingBench.HashAlgorithm.values()) {
            System.out.println("Testing " + hashAlgorithm.name() + " ====================================");
            testHashQuality11Bytes2Billion(hashAlgorithm);
        }
    }

    private static void testHashQuality11Bytes2Billion(NonCryptographicHashingBench.HashAlgorithm hashAlgorithm) {
        final long START_TIME = System.currentTimeMillis();
        final long NUM_INPUTS = 500_000_000L; // 500 million inputs
        final int NUM_BYTES = 11; // 11 bytes = 88 bits of data input
        final Set<Long> hashes = new HashSet<>();
        final byte[] ba = new byte[NUM_BYTES];

        for (long i = 0; i < NUM_INPUTS; i++) {
            if (i % 10_000_000 == 0) {
                System.out.printf("\r       Progress: %.2f%%", (i * 100.0) / NUM_INPUTS);
                System.out.flush();
            }
            long value = i;
            for (int j = 0; j < NUM_BYTES; j++) {
                // Map each byte to 1..255 (never zero)
                ba[j] = (byte) ((value % 255) + 1);
                value /= 255;
            }
            final long hash = hashAlgorithm.function.applyAsLong(ba, 0, NUM_BYTES);
            hashes.add(hash);
        }

        long numUniqueHashes = hashes.size();
        long hashCollisions = NUM_INPUTS - numUniqueHashes;
        final long END_TIME = System.currentTimeMillis();
        System.out.printf(
                "       Number of unique hashes: %,d, hash collisions: %,d, time taken: %.3f seconds%n",
                numUniqueHashes, hashCollisions, (END_TIME - START_TIME) / 1000.0);
    }

    private static void testHashQuality11Bytes2BillionInt(NonCryptographicHashingBench.HashAlgorithm hashAlgorithm) {
        final long START_TIME = System.currentTimeMillis();
        final long NUM_INPUTS = 500_000_000L; // 500 million inputs
        final int NUM_BYTES = 11; // 11 bytes = 88 bits of data input
        final int NUM_OF_CHANGES_PER_ROUND_PER_BYTE =
                15; // the number of changes per byte in each round before moving to the next byte
        final Set<Integer> hashes = new HashSet<>();
        final byte[] ba = new byte[NUM_BYTES];

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

            final int hash = (int) hashAlgorithm.function.applyAsLong(ba, 0, NUM_BYTES);
            hashes.add(hash);
        }

        long numUniqueHashes = hashes.size();
        long hashCollisions = NUM_INPUTS - numUniqueHashes;
        final long END_TIME = System.currentTimeMillis();
        System.out.printf(
                "       Number of unique hashes: %,d, hash collisions: %,d, time taken: %.3f seconds%n",
                numUniqueHashes, hashCollisions, (END_TIME - START_TIME) / 1000.0);
    }
}
