// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

/**
 * This class contains a collection of methods for hashing basic data types.
 * Hashes are not cryptographically secure, and are intended to be used when
 * implementing {@link Object#hashCode()} or similar functionality.
 */
public final class NonCryptographicHashing {
    // This class is not meant to be instantiated.
    private NonCryptographicHashing() {}

    public static int hash32(@NonNull final byte[] bytes) {
        return hash32(bytes, 0, bytes.length);
    }

    public static int hash32(@NonNull final byte[] bytes, final int position, final int length) {
        // Accumulate the hash in 32-bit chunks. If the length is not a multiple of 4, then read
        // as many complete 4 byte chunks as possible.
        int hash = 1;
        int i = position;
        int end = position + length - 3;
        for (; i < end; i += 4) {
            // TODO Jasper change this to use a VarHandle so we get native or reverse order as needed
            hash = perm32(hash ^ UnsafeUtils.getIntUnsafeNative(bytes, i));
        }

        // Construct a trailing int. If the segment of the byte array we read was exactly a multiple of 4 bytes,
        // then we will append "0x0000007F" to the end of the hash. If we had 1 byte remaining, then
        // we will append "0x00007FXX" where XX is the value of the last byte, and so on.
        int tail = 0x7F;
        int start = i;
        i = position + length - 1;
        for (; i >= start; i--) {
            tail <<= 8;
            tail ^= bytes[i];
        }

        // Combine the tail with the previous hash.
        hash = perm32(hash ^ tail);

        return hash;
    }

    private static int perm32(int x) {
        // This is necessary so that 0 does not hash to 0. As a side effect, this constant will hash to 0.
        // It was randomly generated (not using Java), so that it will occur in practice less often than more
        // common numbers like 0 or -1 or Integer.MAX_VALUE.
        x ^= 0x5e8a016a;

        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }


    /**
     * Generates a non-cryptographic 64-bit hash for 1 long.
     *
     * @param x0 a single long
     * @return a non-cryptographic long hash
     */
    public static long hash64(final long x0) {
        return perm64(x0);
    }

    /**
     * Generates a non-cryptographic 64-bit hash for a byte array.
     *
     * @param bytes
     * 		a byte array
     * @return a non-cryptographic long hash
     */
    public static long hash64(@NonNull final byte[] bytes) {
        return hash64(bytes, 0, bytes.length);
    }

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
            // TODO Jasper change this to use a VarHandle so we get native or reverse order as needed
            hash = perm64(hash ^ UnsafeUtils.getLongNoChecksNativeOrder(bytes, i));
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
        hash = perm64(hash ^ tail);

        return hash;
    }

    /**
     * Generates a non-cryptographic 64-bit hash for a ByteBuffer covering all bytes from position to limit.
     *
     * @param buf a byte buffer to compute the hash from
     * @return a non-cryptographic long hash
     */
    public static long hash64(@NonNull final ByteBuffer buf) {
        long hash = perm64(buf.remaining());
        final int p = buf.position();
        final int l = buf.limit();
        for (int i = p; i < l; i += 8) {
            final int remaining = l - i;
            if (remaining < 8) {
                // If there are less than 8 bytes remaining, we need to pad with zeros.
                long value = 0;
                for (int j = 0; j < remaining; j++) {
                    value |= (UnsafeUtils.getHeapBufferByteNoChecks(buf, i + j) & 0xffL) << (8 * (7 - j));
                }
                hash = perm64(hash ^ value);
                break;
            } else {
                // If there are 8 or more bytes remaining, we can read a full long.
                hash = perm64(hash ^ buf.getLong(i));
            }
        }
        return hash;
    }

    /**
     * <p>
     * A permutation (invertible function) on 64 bits. The constants were found by automated search, to
     * optimize avalanche. Avalanche means that for a random number x, flipping bit i of x has about a
     * 50 percent chance of flipping bit j of perm64(x). For each possible pair (i,j), this function achieves
     * a probability between 49.8 and 50.2 percent.
     *
     * <p>
     * Warning: there currently exist production use cases that will break if this hashing algorithm is changed.
     * If modifications to this hashing algorithm are ever required, they must be raised with the maintainers
     * of the Hiero Consensus Node and probably the Hiero Technical Steering Committee.
     */
    private static long perm64(long x) {
        // This is necessary so that 0 does not hash to 0. As a side effect, this constant will hash to 0.
        // It was randomly generated (not using Java), so that it will occur in practice less often than more
        // common numbers like 0 or -1 or Long.MAX_VALUE.
        x ^= 0x5e8a016a5eb99c18L;

        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }
}
