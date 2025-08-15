// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Non-cryptographic 64-bit hash function based on Leemon's hash64 with murmurHash3 mixer function.
 */
public class LeemonMurmur {

    /**
     * Generates a non-cryptographic 64-bit hash for contents of the given byte array within indexes position
     * (inclusive) and position + length (exclusive).
     *
     * @param bytes A byte array. Must not be null. Can be empty.
     * @param position The starting position within the byte array to begin hashing from. Must be non-negative,
     *                 and must be less than the length of the array, and position + length must also be
     *                 less than or equal to the length of the array.
     * @param length
     *         The number of bytes to hash. Must be non-negative, and must be such that position + length
     *         is less than or equal to the length of the byte array.
     *
     * @return a non-cryptographic long hash
     */
    public static long hash64(@NonNull final byte[] bytes, final int position, final int length) {
        // Accumulate the hash in 64-bit chunks. If the length is not a multiple of 8, then read
        // as many complete 8 byte chunks as possible.
        long hash = 1;
        int i = position;
        int end = position + length - 7;
        for (; i < end; i += 8) {
            hash = murmurHash3Mixer(hash ^ UnsafeUtils.getLongNoChecksLittleEndian(bytes, i));
        }

        // Construct a trailing long. If the segment of the byte array we read was exactly a multiple of 8 bytes,
        // then we will append "0x000000000000007F" to the end of the hash. If we had 1 byte remaining, then
        // we will append "0x0000000000007FXX" where XX is the value of the last byte, and so on.
        long tail = 0x7F;
        int start = i;
        i = position + length - 1;
        for (; i >= start; i--) {
            tail <<= 8;
            tail ^= bytes[i];
        }

        // Combine the tail with the previous hash.
        hash = murmurHash3Mixer(hash ^ tail);

        return hash;
    }

    private static long murmurHash3Mixer(long key) {
        key ^= (key >> 33);
        key *= 0xff51afd7ed558ccdL;
        key ^= (key >> 33);
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= (key >> 33);
        return key;
    }
}
