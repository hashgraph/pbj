// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * XXH3 is a non-cryptographic hash function designed for speed and quality. Ported from
 * <a href="https://github.com/OpenHFT/Zero-Allocation-Hashing/tree/ea">OpenHFT</a> with dependencies removed and
 * cleaned up to be minimal.
 * <p>
 * Adapted version of XXH3 implementation from <a href="https://github.com/Cyan4973/xxHash">xxHash</a>.
 * This implementation provides endian-independent hash values, but it's slower on big-endian platforms.
 * </p>
 */
@SuppressWarnings("DuplicatedCode")
public final class XXH3OpenHFT2 {
    private static final long SEED = 0L; // Default seed value
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    /*! Pseudorandom secret taken directly from FARSH. */
    private static final byte[] XXH3_kSecret = {
        (byte) 0xb8, (byte) 0xfe, (byte) 0x6c, (byte) 0x39, (byte) 0x23, (byte) 0xa4, (byte) 0x4b, (byte) 0xbe,
                (byte) 0x7c, (byte) 0x01, (byte) 0x81, (byte) 0x2c, (byte) 0xf7, (byte) 0x21, (byte) 0xad, (byte) 0x1c,
        (byte) 0xde, (byte) 0xd4, (byte) 0x6d, (byte) 0xe9, (byte) 0x83, (byte) 0x90, (byte) 0x97, (byte) 0xdb,
                (byte) 0x72, (byte) 0x40, (byte) 0xa4, (byte) 0xa4, (byte) 0xb7, (byte) 0xb3, (byte) 0x67, (byte) 0x1f,
        (byte) 0xcb, (byte) 0x79, (byte) 0xe6, (byte) 0x4e, (byte) 0xcc, (byte) 0xc0, (byte) 0xe5, (byte) 0x78,
                (byte) 0x82, (byte) 0x5a, (byte) 0xd0, (byte) 0x7d, (byte) 0xcc, (byte) 0xff, (byte) 0x72, (byte) 0x21,
        (byte) 0xb8, (byte) 0x08, (byte) 0x46, (byte) 0x74, (byte) 0xf7, (byte) 0x43, (byte) 0x24, (byte) 0x8e,
                (byte) 0xe0, (byte) 0x35, (byte) 0x90, (byte) 0xe6, (byte) 0x81, (byte) 0x3a, (byte) 0x26, (byte) 0x4c,
        (byte) 0x3c, (byte) 0x28, (byte) 0x52, (byte) 0xbb, (byte) 0x91, (byte) 0xc3, (byte) 0x00, (byte) 0xcb,
                (byte) 0x88, (byte) 0xd0, (byte) 0x65, (byte) 0x8b, (byte) 0x1b, (byte) 0x53, (byte) 0x2e, (byte) 0xa3,
        (byte) 0x71, (byte) 0x64, (byte) 0x48, (byte) 0x97, (byte) 0xa2, (byte) 0x0d, (byte) 0xf9, (byte) 0x4e,
                (byte) 0x38, (byte) 0x19, (byte) 0xef, (byte) 0x46, (byte) 0xa9, (byte) 0xde, (byte) 0xac, (byte) 0xd8,
        (byte) 0xa8, (byte) 0xfa, (byte) 0x76, (byte) 0x3f, (byte) 0xe3, (byte) 0x9c, (byte) 0x34, (byte) 0x3f,
                (byte) 0xf9, (byte) 0xdc, (byte) 0xbb, (byte) 0xc7, (byte) 0xc7, (byte) 0x0b, (byte) 0x4f, (byte) 0x1d,
        (byte) 0x8a, (byte) 0x51, (byte) 0xe0, (byte) 0x4b, (byte) 0xcd, (byte) 0xb4, (byte) 0x59, (byte) 0x31,
                (byte) 0xc8, (byte) 0x9f, (byte) 0x7e, (byte) 0xc9, (byte) 0xd9, (byte) 0x78, (byte) 0x73, (byte) 0x64,
        (byte) 0xea, (byte) 0xc5, (byte) 0xac, (byte) 0x83, (byte) 0x34, (byte) 0xd3, (byte) 0xeb, (byte) 0xc3,
                (byte) 0xc5, (byte) 0x81, (byte) 0xa0, (byte) 0xff, (byte) 0xfa, (byte) 0x13, (byte) 0x63, (byte) 0xeb,
        (byte) 0x17, (byte) 0x0d, (byte) 0xdd, (byte) 0x51, (byte) 0xb7, (byte) 0xf0, (byte) 0xda, (byte) 0x49,
                (byte) 0xd3, (byte) 0x16, (byte) 0x55, (byte) 0x26, (byte) 0x29, (byte) 0xd4, (byte) 0x68, (byte) 0x9e,
        (byte) 0x2b, (byte) 0x16, (byte) 0xbe, (byte) 0x58, (byte) 0x7d, (byte) 0x47, (byte) 0xa1, (byte) 0xfc,
                (byte) 0x8f, (byte) 0xf8, (byte) 0xb8, (byte) 0xd1, (byte) 0x7a, (byte) 0xd0, (byte) 0x31, (byte) 0xce,
        (byte) 0x45, (byte) 0xcb, (byte) 0x3a, (byte) 0x8f, (byte) 0x95, (byte) 0x16, (byte) 0x04, (byte) 0x28,
                (byte) 0xaf, (byte) 0xd7, (byte) 0xfb, (byte) 0xca, (byte) 0xbb, (byte) 0x4b, (byte) 0x40, (byte) 0x7e,
    };
    // Primes
    private static final long XXH_PRIME32_1 = 0x9E3779B1L; /*!< 0b10011110001101110111100110110001 */
    private static final long XXH_PRIME32_2 = 0x85EBCA77L; /*!< 0b10000101111010111100101001110111 */
    private static final long XXH_PRIME32_3 = 0xC2B2AE3DL; /*!< 0b11000010101100101010111000111101 */
    private static final long XXH_PRIME64_1 =
            0x9E3779B185EBCA87L; /*!< 0b1001111000110111011110011011000110000101111010111100101010000111 */
    private static final long XXH_PRIME64_2 =
            0xC2B2AE3D27D4EB4FL; /*!< 0b1100001010110010101011100011110100100111110101001110101101001111 */
    private static final long XXH_PRIME64_3 =
            0x165667B19E3779F9L; /*!< 0b0001011001010110011001111011000110011110001101110111100111111001 */
    private static final long XXH_PRIME64_4 =
            0x85EBCA77C2B2AE63L; /*!< 0b1000010111101011110010100111011111000010101100101010111001100011 */
    private static final long XXH_PRIME64_5 =
            0x27D4EB2F165667C5L; /*!< 0b0010011111010100111010110010111100010110010101100110011111000101 */
    // only support fixed size secret
    private static final long nbStripesPerBlock = (192 - 64) / 8;
    private static final long block_len = 64 * nbStripesPerBlock;

    private static long unsignedLongMulXorFold(final long lhs, final long rhs) {
        // The Grade School method of multiplication is a hair faster in Java, primarily used here
        // because the implementation is simpler.
        final long lhs_l = lhs & 0xFFFFFFFFL;
        final long lhs_h = lhs >>> 32;
        final long rhs_l = rhs & 0xFFFFFFFFL;
        final long rhs_h = rhs >>> 32;
        final long lo_lo = lhs_l * rhs_l;
        final long hi_lo = lhs_h * rhs_l;
        final long lo_hi = lhs_l * rhs_h;
        final long hi_hi = lhs_h * rhs_h;

        // Add the products together. This will never overflow.
        final long cross = (lo_lo >>> 32) + (hi_lo & 0xFFFFFFFFL) + lo_hi;
        final long upper = (hi_lo >>> 32) + (cross >>> 32) + hi_hi;
        final long lower = (cross << 32) | (lo_lo & 0xFFFFFFFFL);
        return lower ^ upper;
    }

    private static long XXH64_avalanche(long h64) {
        h64 ^= h64 >>> 33;
        h64 *= XXH_PRIME64_2;
        h64 ^= h64 >>> 29;
        h64 *= XXH_PRIME64_3;
        return h64 ^ (h64 >>> 32);
    }

    private static long XXH3_avalanche(long h64) {
        h64 ^= h64 >>> 37;
        h64 *= 0x165667919E3779F9L;
        return h64 ^ (h64 >>> 32);
    }

    private static long XXH3_rrmxmx(long h64, final long length) {
        h64 ^= Long.rotateLeft(h64, 49) ^ Long.rotateLeft(h64, 24);
        h64 *= 0x9FB21C651E98DF25L;
        h64 ^= (h64 >>> 35) + length;
        h64 *= 0x9FB21C651E98DF25L;
        return h64 ^ (h64 >>> 28);
    }

    private static long XXH3_mix16B(final byte[] input, final int offIn, final int offSec) {
        final long input_lo = i64(input, offIn);
        final long input_hi = i64(input, offIn + 8);
        return unsignedLongMulXorFold(
                input_lo ^ (i64(XXH3_kSecret, offSec) + SEED), input_hi ^ (i64(XXH3_kSecret, offSec + 8) - SEED));
    }

    private static long XXH3_mix2Accs(final long acc_lh, final long acc_rh, final long offSec) {
        return unsignedLongMulXorFold(acc_lh ^ i64(XXH3_kSecret, offSec), acc_rh ^ i64(XXH3_kSecret, offSec + 8));
    }

    public static long hash64(final byte[] input, final int off, final int length) {
        if (length <= 16) {
            // XXH3_len_0to16_64b
            if (length > 8) {
                // XXH3_len_9to16_64b
                final long bitflip1 = (i64(XXH3_kSecret, 24) ^ i64(XXH3_kSecret, 32)) + SEED;
                final long bitflip2 = (i64(XXH3_kSecret, 40) ^ i64(XXH3_kSecret, 48)) - SEED;
                final long input_lo = i64(input, off) ^ bitflip1;
                final long input_hi = i64(input, off + length - 8) ^ bitflip2;
                final long acc =
                        length + Long.reverseBytes(input_lo) + input_hi + unsignedLongMulXorFold(input_lo, input_hi);
                return XXH3_avalanche(acc);
            }
            if (length >= 4) {
                // XXH3_len_4to8_64b
                long s = SEED ^ Long.reverseBytes(SEED & 0xFFFFFFFFL);
                final long input1 = u32(input, off); // first 4 bytes
                final long input2 = u32(input, off + length - 4); // last 4 bytes
                final long bitflip = (i64(XXH3_kSecret, 8) ^ i64(XXH3_kSecret, 16)) - s;
                final long keyed = (((input1 & 0xFFFFFFFFL) << 32) | (input2 & 0xFFFFFFFFL)) ^ bitflip;
                return XXH3_rrmxmx(keyed, length);
            }
            if (length != 0) {
                // XXH3_len_1to3_64b
                final int c1 = u8(input, off);
                final int c2 = u8(input, off + (length >> 1));
                final int c3 = u8(input, off + length - 1);
                final long combined =
                        ((c1 & 0xFFL) << 16) | ((c2 & 0xFFL) << 24) | ((c3 & 0xFFL)) | ((long) length << 8);
                final long bitflip = unsignedInt(i32(XXH3_kSecret, 0) ^ i32(XXH3_kSecret, 4)) + SEED;
                return XXH64_avalanche(combined ^ bitflip);
            }
            return XXH64_avalanche(SEED ^ i64(XXH3_kSecret, 56) ^ i64(XXH3_kSecret, 64));
        }
        if (length <= 128) {
            // XXH3_len_17to128_64b
            long acc = length * XXH_PRIME64_1;

            if (length > 32) {
                if (length > 64) {
                    if (length > 96) {
                        acc += XXH3_mix16B(input, off + 48, 96);
                        acc += XXH3_mix16B(input, off + length - 64, 112);
                    }
                    acc += XXH3_mix16B(input, off + 32, 64);
                    acc += XXH3_mix16B(input, off + length - 48, 80);
                }
                acc += XXH3_mix16B(input, off + 16, 32);
                acc += XXH3_mix16B(input, off + length - 32, 48);
            }
            acc += XXH3_mix16B(input, off, 0);
            acc += XXH3_mix16B(input, off + length - 16, 16);

            return XXH3_avalanche(acc);
        }
        if (length <= 240) {
            // XXH3_len_129to240_64b
            long acc = length * XXH_PRIME64_1;
            final int nbRounds = length / 16;
            int i = 0;
            for (; i < 8; ++i) {
                acc += XXH3_mix16B(input, off + 16 * i, 16 * i);
            }
            acc = XXH3_avalanche(acc);

            for (; i < nbRounds; ++i) {
                acc += XXH3_mix16B(input, off + 16 * i, 16 * (i - 8) + 3);
            }

            /* last bytes */
            acc += XXH3_mix16B(input, off + length - 16, 136 - 17);
            return XXH3_avalanche(acc);
        }

        // XXH3_hashLong_64b_internal
        long acc_0 = XXH_PRIME32_3;
        long acc_1 = XXH_PRIME64_1;
        long acc_2 = XXH_PRIME64_2;
        long acc_3 = XXH_PRIME64_3;
        long acc_4 = XXH_PRIME64_4;
        long acc_5 = XXH_PRIME32_2;
        long acc_6 = XXH_PRIME64_5;
        long acc_7 = XXH_PRIME32_1;

        // XXH3_hashLong_internal_loop
        final long nb_blocks = (length - 1) / block_len;
        for (long n = 0; n < nb_blocks; n++) {
            // XXH3_accumulate
            final long offBlock = off + n * block_len;
            for (long s = 0; s < nbStripesPerBlock; s++) {
                // XXH3_accumulate_512
                final long offStripe = offBlock + s * 64;
                final long offSec = s * 8;
                {
                    final long data_val_0 = i64(input, offStripe);
                    final long data_val_1 = i64(input, offStripe + 8);
                    final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec);
                    final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8);
                    /* swap adjacent lanes */
                    acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = i64(input, offStripe + 8 * 2);
                    final long data_val_1 = i64(input, offStripe + 8 * 3);
                    final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 2);
                    final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 3);
                    /* swap adjacent lanes */
                    acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = i64(input, offStripe + 8 * 4);
                    final long data_val_1 = i64(input, offStripe + 8 * 5);
                    final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 4);
                    final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 5);
                    /* swap adjacent lanes */
                    acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = i64(input, offStripe + 8 * 6);
                    final long data_val_1 = i64(input, offStripe + 8 * 7);
                    final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 6);
                    final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 7);
                    /* swap adjacent lanes */
                    acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
            }

            // XXH3_scrambleAcc_scalar
            final long offSec = 192 - 64;
            acc_0 = (acc_0 ^ (acc_0 >>> 47) ^ i64(XXH3_kSecret, offSec)) * XXH_PRIME32_1;
            acc_1 = (acc_1 ^ (acc_1 >>> 47) ^ i64(XXH3_kSecret, offSec + 8)) * XXH_PRIME32_1;
            acc_2 = (acc_2 ^ (acc_2 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 2)) * XXH_PRIME32_1;
            acc_3 = (acc_3 ^ (acc_3 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 3)) * XXH_PRIME32_1;
            acc_4 = (acc_4 ^ (acc_4 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 4)) * XXH_PRIME32_1;
            acc_5 = (acc_5 ^ (acc_5 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 5)) * XXH_PRIME32_1;
            acc_6 = (acc_6 ^ (acc_6 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 6)) * XXH_PRIME32_1;
            acc_7 = (acc_7 ^ (acc_7 >>> 47) ^ i64(XXH3_kSecret, offSec + 8 * 7)) * XXH_PRIME32_1;
        }

        /* last partial block */
        final long nbStripes = ((length - 1) - (block_len * nb_blocks)) / 64;
        final long offBlock = off + block_len * nb_blocks;
        for (long s = 0; s < nbStripes; s++) {
            // XXH3_accumulate_512
            final long offStripe = offBlock + s * 64;
            final long offSec = s * 8;
            {
                final long data_val_0 = i64(input, offStripe);
                final long data_val_1 = i64(input, offStripe + 8);
                final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec);
                final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8);
                /* swap adjacent lanes */
                acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = i64(input, offStripe + 8 * 2);
                final long data_val_1 = i64(input, offStripe + 8 * 3);
                final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 2);
                final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 3);
                /* swap adjacent lanes */
                acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = i64(input, offStripe + 8 * 4);
                final long data_val_1 = i64(input, offStripe + 8 * 5);
                final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 4);
                final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 5);
                /* swap adjacent lanes */
                acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = i64(input, offStripe + 8 * 6);
                final long data_val_1 = i64(input, offStripe + 8 * 7);
                final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 6);
                final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 7);
                /* swap adjacent lanes */
                acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
        }

        /* last stripe */
        // XXH3_accumulate_512
        final long offStripe = off + length - 64;
        final long offSec = 192 - 64 - 7;
        {
            final long data_val_0 = i64(input, offStripe);
            final long data_val_1 = i64(input, offStripe + 8);
            final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec);
            final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8);
            /* swap adjacent lanes */
            acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = i64(input, offStripe + 8 * 2);
            final long data_val_1 = i64(input, offStripe + 8 * 3);
            final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 2);
            final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 3);
            /* swap adjacent lanes */
            acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = i64(input, offStripe + 8 * 4);
            final long data_val_1 = i64(input, offStripe + 8 * 5);
            final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 4);
            final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 5);
            /* swap adjacent lanes */
            acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = i64(input, offStripe + 8 * 6);
            final long data_val_1 = i64(input, offStripe + 8 * 7);
            final long data_key_0 = data_val_0 ^ i64(XXH3_kSecret, offSec + 8 * 6);
            final long data_key_1 = data_val_1 ^ i64(XXH3_kSecret, offSec + 8 * 7);
            /* swap adjacent lanes */
            acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }

        // XXH3_mergeAccs
        final long result64 = length * XXH_PRIME64_1
                + XXH3_mix2Accs(acc_0, acc_1, 11)
                + XXH3_mix2Accs(acc_2, acc_3, 11 + 16)
                + XXH3_mix2Accs(acc_4, acc_5, 11 + 16 * 2)
                + XXH3_mix2Accs(acc_6, acc_7, 11 + 16 * 3);

        return XXH3_avalanche(result64);
    }

    static long unsignedInt(int i) {
        return i & 0xFFFFFFFFL;
    }

    /**
     * Reads a 64 bit long in little-endian order from the given byte array at the specified offset.
     * *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * @return a 64 bit long value, little-endian encoded
     */
    private static long i64(final byte[] input, final long offset) {
        return (long) LONG_HANDLE.get(input, (int) offset);
    }

    /**
     * Reads an unsigned byte from the given byte array at the specified offset.
     *
     * @param input the object to access
     * @param offset offset to the byte to read within the byte sequence represented
     * by the given object
     * @return a byte by the given {@code offset}, interpreted as unsigned
     */
    private static int u8(final byte[] input, final long offset) {
        return Byte.toUnsignedInt(input[(int) offset]);
    }

    /**
     * Reads an unsigned byte from the given byte array at the specified offset.
     *
     * @param input the object to access
     * @param offset offset to the byte to read within the byte sequence represented
     * by the given object
     * @return a byte by the given {@code offset}, interpreted as unsigned
     */
    private static int i8(final byte[] input, final long offset) {
        return input[(int) offset];
    }

    /**
     * Load 4 bytes from the provided array at the indicated offset.
     *
     * @param source the input bytes
     * @param offset the offset into the array at which to start
     * @return the value found in the array in the form of a long
     */
    private static int u32(byte[] source, int offset) {
        // This is faster than using VarHandle for 4 bytes
        return (source[offset] & 0xFF)
                | ((source[offset + 1] & 0xFF) << 8)
                | ((source[offset + 2] & 0xFF) << 16)
                | ((source[offset + 3] & 0xFF) << 24);
    }

    /**
     * Load 4 bytes from the provided array at the indicated offset.
     *
     * @param source the input bytes
     * @param offset the offset into the array at which to start
     * @return the value found in the array in the form of a long
     */
    private static int i32(byte[] source, int offset) {
        // This is faster than using VarHandle for 4 bytes
        return (source[offset] & 0xFF)
                | ((source[offset + 1] & 0xFF) << 8)
                | ((source[offset + 2] & 0xFF) << 16)
                | ((source[offset + 3] & 0xFF) << 24);
    }
}
