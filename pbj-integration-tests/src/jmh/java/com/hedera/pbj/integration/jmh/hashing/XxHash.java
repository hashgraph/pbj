// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import edu.umd.cs.findbugs.annotations.NonNull;

public class XxHash {

    public static int xxHashCodeFast(@NonNull final byte[] bytes, int start, int length) {
        final long PRIME1 = 0x9E3779B185EBCA87L;
        final long PRIME2 = 0xC2B2AE3D27D4EB4FL;
        final long PRIME3 = 0x165667B19E3779F9L;
        final long PRIME4 = 0x85EBCA776C2B2AE1L;
        final long PRIME5 = 0x27D4EB2F165667C5L;

        final int seed = 0;
        final int end = start + length;
        long h64;

        if (length >= 32) {
            final int limit = end - 32;
            long v1 = seed + PRIME1 + PRIME2;
            long v2 = seed + PRIME2;
            long v3 = seed;
            long v4 = seed - PRIME1;

            do {
                v1 = Long.rotateLeft(v1 + getLong(bytes, start) * PRIME2, 31) * PRIME1;
                start += 8;
                v2 = Long.rotateLeft(v2 + getLong(bytes, start) * PRIME2, 31) * PRIME1;
                start += 8;
                v3 = Long.rotateLeft(v3 + getLong(bytes, start) * PRIME2, 31) * PRIME1;
                start += 8;
                v4 = Long.rotateLeft(v4 + getLong(bytes, start) * PRIME2, 31) * PRIME1;
                start += 8;
            } while (start <= limit);

            h64 = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);

            h64 = (h64 ^ Long.rotateLeft(v1 * PRIME2, 31) * PRIME1) * PRIME1 + PRIME4;
            h64 = (h64 ^ Long.rotateLeft(v2 * PRIME2, 31) * PRIME1) * PRIME1 + PRIME4;
            h64 = (h64 ^ Long.rotateLeft(v3 * PRIME2, 31) * PRIME1) * PRIME1 + PRIME4;
            h64 = (h64 ^ Long.rotateLeft(v4 * PRIME2, 31) * PRIME1) * PRIME1 + PRIME4;
        } else {
            h64 = seed + PRIME5;
        }

        h64 += length;

        while (start <= end - 8) {
            h64 = Long.rotateLeft(h64 ^ Long.rotateLeft(getLong(bytes, start) * PRIME2, 31) * PRIME1, 27) * PRIME1
                    + PRIME4;
            start += 8;
        }

        if (start <= end - 4) {
            h64 = Long.rotateLeft(h64 ^ (getInt(bytes, start) * PRIME1), 23) * PRIME2 + PRIME3;
            start += 4;
        }

        while (start < end) {
            h64 = Long.rotateLeft(h64 ^ ((bytes[start] & 0xFF) * PRIME5), 11) * PRIME1;
            start++;
        }

        h64 ^= h64 >>> 33;
        h64 *= PRIME2;
        h64 ^= h64 >>> 29;
        h64 *= PRIME3;
        h64 ^= h64 >>> 32;

        //        return (int)(h64 ^ (h64 >>> 32));
        return (int) h64;
    }

    private static long getLong(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFFL)
                | ((bytes[offset + 1] & 0xFFL) << 8)
                | ((bytes[offset + 2] & 0xFFL) << 16)
                | ((bytes[offset + 3] & 0xFFL) << 24)
                | ((bytes[offset + 4] & 0xFFL) << 32)
                | ((bytes[offset + 5] & 0xFFL) << 40)
                | ((bytes[offset + 6] & 0xFFL) << 48)
                | ((bytes[offset + 7] & 0xFFL) << 56);
    }

    public static int xxHashCode(@NonNull final byte[] bytes, int start, int length) {
        final int PRIME1 = 0x9E3779B1;
        final int PRIME2 = 0x85EBCA77;
        final int PRIME3 = 0xC2B2AE3D;
        final int PRIME4 = 0x27D4EB2F;
        final int PRIME5 = 0x165667B1;

        final int seed = 0; // You can make this a parameter if needed
        final int end = start + length;
        int h32;

        if (length >= 16) {
            final int limit = end - 16;
            int v1 = seed + PRIME1 + PRIME2;
            int v2 = seed + PRIME2;
            int v3 = seed;
            int v4 = seed - PRIME1;

            do {
                v1 = rotateLeft(v1 + getInt(bytes, start) * PRIME2, 13) * PRIME1;
                start += 4;
                v2 = rotateLeft(v2 + getInt(bytes, start) * PRIME2, 13) * PRIME1;
                start += 4;
                v3 = rotateLeft(v3 + getInt(bytes, start) * PRIME2, 13) * PRIME1;
                start += 4;
                v4 = rotateLeft(v4 + getInt(bytes, start) * PRIME2, 13) * PRIME1;
                start += 4;
            } while (start <= limit);

            h32 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18);
        } else {
            h32 = seed + PRIME5;
        }

        h32 += length;

        while (start <= end - 4) {
            h32 = rotateLeft(h32 + getInt(bytes, start) * PRIME3, 17) * PRIME4;
            start += 4;
        }

        while (start < end) {
            h32 = rotateLeft(h32 + (bytes[start] & 0xFF) * PRIME5, 11) * PRIME1;
            start++;
        }

        h32 ^= h32 >>> 15;
        h32 *= PRIME2;
        h32 ^= h32 >>> 13;
        h32 *= PRIME3;
        h32 ^= h32 >>> 16;

        return h32;
    }

    private static int rotateLeft(int value, int shift) {
        return (value << shift) | (value >>> (32 - shift));
    }

    private static int getInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
