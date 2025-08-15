// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

/**
 * The MurmurHash3 algorithm was created by Austin Appleby and placed in the public domain.
 * This java port was authored by Yonik Seeley and also placed into the public domain.
 * It has been modified by Konstantin Sobolev and, you guessed it, also placed in the public domain.
 * The author hereby disclaims copyright to this source code.
 * <p>
 * This produces exactly the same hash values as the final C++
 * version of MurmurHash3 and is thus suitable for producing the same hash values across
 * platforms.
 * </p>
 *
 * @see <a href="https://github.com/eprst/murmur3/blob/master/src/main/java/com/github/eprst/murmur3/MurmurHash3.java">
 *      Original Java Port Source</a>
 */
@SuppressWarnings("fallthrough")
public final class MurmurHash3 {
    private static final int c1 = 0xcc9e2d51;
    private static final int c2 = 0x1b873593;

    /**
     * Computes the MurmurHash3_x86_32 hash of the given byte array, using seed of 1.
     *
     * @param data   the byte array to hash
     * @param offset the starting offset in the byte array
     * @param len    the length of the data to hash
     * @return the computed hash value
     */
    public static int murmurhash3_x86_32(byte[] data, int offset, int len) {
        int h1 = 1;
        int roundedEnd = offset + (len & 0xfffffffc); // round down to 4 byte block

        for (int i = offset; i < roundedEnd; i += 4) {
            // little endian load order
            int k1 =
                    (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8) | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k1 *= c1;
            k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
            k1 *= c2;

            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19); // ROTL32(h1,13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        // tail
        int k1 = 0;

        switch (len & 0x03) {
            case 3:
                k1 = (data[roundedEnd + 2] & 0xff) << 16;
            // fall through
            case 2:
                k1 |= (data[roundedEnd + 1] & 0xff) << 8;
            // fall through
            case 1:
                k1 |= (data[roundedEnd] & 0xff);
                k1 *= c1;
                k1 = (k1 << 15) | (k1 >>> 17); // ROTL32(k1,15);
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
}
