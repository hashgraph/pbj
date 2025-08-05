// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import edu.umd.cs.findbugs.annotations.NonNull;

public final class Xxh3 {
    public static int xxh3HashCode(@NonNull final byte[] bytes, int start, int length) {
        if (length <= 16) {
            return xxh3_len_0to16(bytes, start, length);
        } else if (length <= 128) {
            return xxh3_len_17to128(bytes, start, length);
        } else if (length <= 240) {
            return xxh3_len_129to240(bytes, start, length);
        } else {
            return xxh3_hashLong(bytes, start, length);
        }
    }

    private static final long XXH_PRIME32_1 = 0x9E3779B1L;
    private static final long XXH_PRIME32_2 = 0x85EBCA77L;
    private static final long XXH_PRIME32_3 = 0xC2B2AE3DL;
    private static final long XXH_PRIME64_1 = 0x9E3779B185EBCA87L;
    private static final long XXH_PRIME64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long XXH_PRIME64_3 = 0x165667B19E3779F9L;
    private static final long XXH_PRIME64_4 = 0x85EBCA776C2B2AE1L;
    private static final long XXH_PRIME64_5 = 0x27D4EB2F165667C5L;

    private static final long XXH3_AVALANCHE_CONST = 0x165667919E3779F9L;
    private static final long XXH3_MUL_CONST = 0x9FB21C651E98DF25L;

    private static int xxh3_len_0to16(byte[] bytes, int start, int length) {
        if (length >= 9) {
            long inputLo = getLong(bytes, start);
            long inputHi = getLong(bytes, start + length - 8);
            long bitflip = (XXH_PRIME32_1 - 1) ^ (XXH_PRIME32_2 - 1);
            long acc = length + Long.reverseBytes(inputLo) + inputHi + (inputLo ^ inputHi ^ bitflip) * XXH_PRIME64_1;
            acc = xxh3_avalanche(acc);
            return (int) (acc ^ (acc >>> 32));
        } else if (length >= 4) {
            long input1 = getInt(bytes, start) & 0xFFFFFFFFL;
            long input2 = getInt(bytes, start + length - 4) & 0xFFFFFFFFL;
            long bitflip = (XXH_PRIME32_1 - 1) ^ (XXH_PRIME32_2 - 1);
            long keyed = input2 + (input1 << 32);
            long acc = length + keyed + (keyed ^ bitflip) * XXH_PRIME64_1;
            acc = xxh3_avalanche(acc);
            return (int) (acc ^ (acc >>> 32));
        } else if (length > 0) {
            int c1 = bytes[start] & 0xFF;
            int c2 = bytes[start + (length >> 1)] & 0xFF;
            int c3 = bytes[start + length - 1] & 0xFF;
            long combined = c1 + (c2 << 8) + (c3 << 16) + (length << 24);
            long bitflip = (XXH_PRIME32_1 - 1) ^ (XXH_PRIME32_2 - 1);
            long acc = combined ^ bitflip;
            acc *= XXH_PRIME64_1;
            acc = xxh3_avalanche(acc);
            return (int) (acc ^ (acc >>> 32));
        }
        return 0x2D06800B; // XXH3 empty hash
    }

    private static int xxh3_len_17to128(byte[] bytes, int start, int length) {
        long acc = length * XXH_PRIME64_1;

        if (length >= 32) {
            if (length >= 64) {
                if (length >= 96) {
                    acc += xxh3_mix16B(bytes, start + 48, XXH_PRIME32_1, XXH_PRIME32_2);
                    acc += xxh3_mix16B(bytes, start + length - 64, 0, 0);
                }
                acc += xxh3_mix16B(bytes, start + 32, XXH_PRIME32_2, XXH_PRIME32_1);
                acc += xxh3_mix16B(bytes, start + length - 48, 0, 0);
            }
            acc += xxh3_mix16B(bytes, start + 16, 0, 0);
            acc += xxh3_mix16B(bytes, start + length - 32, XXH_PRIME32_1, XXH_PRIME32_2);
        }

        acc += xxh3_mix16B(bytes, start, XXH_PRIME32_1, XXH_PRIME32_2);
        acc += xxh3_mix16B(bytes, start + length - 16, 0, 0);

        acc = xxh3_avalanche(acc);
        return (int) (acc ^ (acc >>> 32));
    }

    private static int xxh3_len_129to240(byte[] bytes, int start, int length) {
        long acc = length * XXH_PRIME64_1;
        int nbRounds = length / 32;

        for (int i = 0; i < 4; i++) {
            acc += xxh3_mix16B(bytes, start + 16 * i, XXH_PRIME32_1, XXH_PRIME32_2);
        }
        acc = xxh3_avalanche(acc);

        for (int i = 4; i < nbRounds; i++) {
            acc += xxh3_mix16B(bytes, start + 16 * i, XXH_PRIME32_2, XXH_PRIME32_1);
        }

        acc += xxh3_mix16B(bytes, start + length - 16, 0, 0);
        acc = xxh3_avalanche(acc);
        return (int) (acc ^ (acc >>> 32));
    }

    private static int xxh3_hashLong(byte[] bytes, int start, int length) {
        long acc0 = XXH_PRIME32_3;
        long acc1 = XXH_PRIME64_1;
        long acc2 = XXH_PRIME64_2;
        long acc3 = XXH_PRIME64_3;
        long acc4 = XXH_PRIME64_4;
        long acc5 = XXH_PRIME64_5;
        long acc6 = XXH_PRIME32_2;
        long acc7 = XXH_PRIME32_1;

        int nbBlocks = (length - 1) / 64;

        for (int n = 0; n < nbBlocks; n++) {
            int dataPtr = start + n * 64;
            acc0 = xxh3_accumulate_512(acc0, dataPtr, bytes, 0);
            acc1 = xxh3_accumulate_512(acc1, dataPtr, bytes, 1);
            acc2 = xxh3_accumulate_512(acc2, dataPtr, bytes, 2);
            acc3 = xxh3_accumulate_512(acc3, dataPtr, bytes, 3);
            acc4 = xxh3_accumulate_512(acc4, dataPtr, bytes, 4);
            acc5 = xxh3_accumulate_512(acc5, dataPtr, bytes, 5);
            acc6 = xxh3_accumulate_512(acc6, dataPtr, bytes, 6);
            acc7 = xxh3_accumulate_512(acc7, dataPtr, bytes, 7);
        }

        long result = length * XXH_PRIME64_1;
        result += xxh3_mergeAccs(acc0, acc1, acc2, acc3, acc4, acc5, acc6, acc7);

        int lastBlockPtr = start + length - 64;
        result += xxh3_mix16B(bytes, lastBlockPtr, 0, 0);
        result += xxh3_mix16B(bytes, lastBlockPtr + 16, XXH_PRIME32_1, XXH_PRIME32_2);
        result += xxh3_mix16B(bytes, lastBlockPtr + 32, XXH_PRIME32_2, XXH_PRIME32_1);
        result += xxh3_mix16B(bytes, lastBlockPtr + 48, 0, 0);

        result = xxh3_avalanche(result);
        return (int) (result ^ (result >>> 32));
    }

    private static long xxh3_accumulate_512(long acc, int dataPtr, byte[] bytes, int lane) {
        long data = getLong(bytes, dataPtr + lane * 8);
        long key = XXH_PRIME32_1 + XXH_PRIME32_2 * lane;
        return acc + data * key;
    }

    private static long xxh3_mix16B(byte[] bytes, int ptr, long seed1, long seed2) {
        long input1 = getLong(bytes, ptr);
        long input2 = getLong(bytes, ptr + 8);
        return xxh3_mul128_fold64(input1 ^ (seed1 + XXH_PRIME32_1), input2 ^ (seed2 + XXH_PRIME32_2));
    }

    private static long xxh3_mul128_fold64(long lhs, long rhs) {
        long hi = Math.multiplyHigh(lhs, rhs);
        long lo = lhs * rhs;
        return lo ^ hi;
    }

    private static long xxh3_avalanche(long h64) {
        h64 ^= h64 >>> 37;
        h64 *= XXH3_AVALANCHE_CONST;
        h64 ^= h64 >>> 32;
        return h64;
    }

    private static long xxh3_mergeAccs(
            long acc0, long acc1, long acc2, long acc3, long acc4, long acc5, long acc6, long acc7) {
        long result = (acc0 ^ acc1) + (acc2 ^ acc3) + (acc4 ^ acc5) + (acc6 ^ acc7);
        result = (result >>> 47) ^ result;
        result *= XXH3_MUL_CONST;
        result ^= result >>> 32;
        return result;
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

    private static int getInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24);
    }
}
