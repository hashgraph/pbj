// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * MurmurHash3 is a port of the MurmurHash3 algorithm, which is a non-cryptographic hash function. From Apache Lucene
 * project.
 *
 * @see <a href="https://github.com/apache/lucene/blob/78a622d6075b785b5e5e10eb0be7c17e641204ea/lucene/core/src/java/org/apache/lucene/util/StringHelper.java">
 * Apache Lucene StringHelper</a>
 */
public abstract class LuceneMurmur3 {
    private static final int SEED = 1; // Default seed value
    private static final VarHandle VH_LE_LONG =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle VH_LE_INT =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    /**
     * Returns the MurmurHash3_x86_32 hash. Original source/tests at
     * <a href="https://github.com/yonik/java_util/">...</a>
     */
    @SuppressWarnings("fallthrough")
    public static int murmurhash3_x86_32(byte[] data, int offset, int len) {
        final int c1 = 0xcc9e2d51;
        final int c2 = 0x1b873593;

        int h1 = SEED;
        int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 = (int) VH_LE_INT.get(data, i);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int k1 = 0;

        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
            // fallthrough
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            // fallthrough
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= len;

        // fmix(h1);
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;

        return h1;
    }

    /**
     * Generates 128-bit hash from the byte array with the given offset, length and seed.
     *
     * <p>The code is adopted from Apache Commons (<a
     * href="https://commons.apache.org/proper/commons-codec/jacoco/org.apache.commons.codec.digest/MurmurHash3.java.html">link</a>)
     *
     * @param data The input byte array
     * @param offset The first element of array
     * @param length The length of array
     * @param seed The initial seed value
     * @return The 128-bit hash (2 longs)
     */
    public static long[] murmurhash3_x64_128(final byte[] data, final int offset, final int length, final int seed) {
        // Use an unsigned 32-bit integer as the seed
        return murmurhash3_x64_128(data, offset, length, seed & 0xFFFFFFFFL);
    }

    public static long murmurhash3_x64_128(final byte[] data, final int offset, final int length) {
        // Use an unsigned 32-bit integer as the seed
        return murmurhash3_x64_128(data, offset, length, SEED & 0xFFFFFFFFL)[0];
    }

    @SuppressWarnings("fallthrough")
    private static long[] murmurhash3_x64_128(final byte[] data, final int offset, final int length, final long seed) {
        long h1 = seed;
        long h2 = seed;
        final int nblocks = length >> 4;

        // Constants for 128-bit variant
        final long C1 = 0x87c37b91114253d5L;
        final long C2 = 0x4cf5ad432745937fL;
        final int R1 = 31;
        final int R2 = 27;
        final int R3 = 33;
        final int M = 5;
        final int N1 = 0x52dce729;
        final int N2 = 0x38495ab5;

        // body
        for (int i = 0; i < nblocks; i++) {
            final int index = offset + (i << 4);
            long k1 = (long) VH_LE_LONG.get(data, index);
            long k2 = (long) VH_LE_LONG.get(data, index + 8);

            // mix functions for k1
            k1 *= C1;
            k1 = Long.rotateLeft(k1, R1);
            k1 *= C2;
            h1 ^= k1;
            h1 = Long.rotateLeft(h1, R2);
            h1 += h2;
            h1 = h1 * M + N1;

            // mix functions for k2
            k2 *= C2;
            k2 = Long.rotateLeft(k2, R3);
            k2 *= C1;
            h2 ^= k2;
            h2 = Long.rotateLeft(h2, R1);
            h2 += h1;
            h2 = h2 * M + N2;
        }

        // tail
        long k1 = 0;
        long k2 = 0;
        final int index = offset + (nblocks << 4);
        switch (length & 0x0F) {
            case 15:
                k2 ^= ((long) data[index + 14] & 0xff) << 48;
            case 14:
                k2 ^= ((long) data[index + 13] & 0xff) << 40;
            case 13:
                k2 ^= ((long) data[index + 12] & 0xff) << 32;
            case 12:
                k2 ^= ((long) data[index + 11] & 0xff) << 24;
            case 11:
                k2 ^= ((long) data[index + 10] & 0xff) << 16;
            case 10:
                k2 ^= ((long) data[index + 9] & 0xff) << 8;
            case 9:
                k2 ^= data[index + 8] & 0xff;
                k2 *= C2;
                k2 = Long.rotateLeft(k2, R3);
                k2 *= C1;
                h2 ^= k2;

            case 8:
                k1 ^= ((long) data[index + 7] & 0xff) << 56;
            case 7:
                k1 ^= ((long) data[index + 6] & 0xff) << 48;
            case 6:
                k1 ^= ((long) data[index + 5] & 0xff) << 40;
            case 5:
                k1 ^= ((long) data[index + 4] & 0xff) << 32;
            case 4:
                k1 ^= ((long) data[index + 3] & 0xff) << 24;
            case 3:
                k1 ^= ((long) data[index + 2] & 0xff) << 16;
            case 2:
                k1 ^= ((long) data[index + 1] & 0xff) << 8;
            case 1:
                k1 ^= data[index] & 0xff;
                k1 *= C1;
                k1 = Long.rotateLeft(k1, R1);
                k1 *= C2;
                h1 ^= k1;
        }

        // finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new long[] {h1, h2};
    }

    /**
     * Performs the final avalanche mix step of the 64-bit hash function.
     *
     * @param hash The current hash
     * @return The final hash
     */
    private static long fmix64(long hash) {
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return hash;
    }
}
