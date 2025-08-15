// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.pbj.integration.jmh.hashing.LongBitSet;
import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench;
import java.util.Arrays;

/**
 * A test to evaluate the quality of non-cryptographic hash functions
 * by checking how many unique hashes can be generated from 4-byte inputs.
 * It runs through all combinations of 4 bytes (256^4 = 4,294,967,296 combinations).
 */
public final class NonCryptographicHashQuality4ByteTest {
    public static void main(String[] args) {
        System.out.println("Testing non-cryptographic hash quality - 4 bytes, 4 billion inputs");
        Arrays.stream(NonCryptographicHashingBench.HashAlgorithm.values())
                .parallel()
                .forEach(hashAlgorithm -> {
                    System.out.println("Testing " + hashAlgorithm.name() + "...");
                    testHashQuality4Bytes(hashAlgorithm);
                });
    }

    private static void testHashQuality4Bytes(NonCryptographicHashingBench.HashAlgorithm hashAlgorithm) {
        final long START_TIME = System.currentTimeMillis();
        final LongBitSet bits = new LongBitSet(4_294_967_296L); // 4 billion bits
        final byte[] ba = new byte[6];
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
                        long hash = hashAlgorithm.function.applyAsLong(ba, 0, 4);
                        int bucket = (int) hash;
                        bits.setBit(bucket & 0xFFFFFFFFL); // Use only the lower 32 bits
                    }
                }
            }
        }

        // Check that we have a reasonable number of bits set.
        long numUniqueHashes = bits.cardinality();
        long expectedUniqueHashes = 256L * 256 * 256 * 256; // 4-byte combinations
        long hashCollisions = expectedUniqueHashes - numUniqueHashes;
        final long END_TIME = System.currentTimeMillis();
        System.out.printf(
                "%n%-25s => Number of unique hashes: %,d, hash collisions: %,d, time taken: %.3f seconds%n",
                hashAlgorithm.name(), numUniqueHashes, hashCollisions, (END_TIME - START_TIME) / 1000.0);
    }
}
