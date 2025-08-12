// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.ByteOrder.nativeOrder;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import sun.misc.Unsafe;

/**
 * XXH3 is a non-cryptographic hash function designed for speed and quality. Ported from
 * <a href="https://github.com/OpenHFT/Zero-Allocation-Hashing/tree/ea">OpenHFT</a>.
 * Adapted version of XXH3 implementation from <a href="https://github.com/Cyan4973/xxHash">xxHash</a>.
 * This implementation provides endian-independent hash values, but it's slower on big-endian platforms.
 */
public class XXH3OpenHFT {
    private static final Access<Object> unsafeLE = UnsafeAccess.INSTANCE.byteOrder(null, LITTLE_ENDIAN);

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

    private static <T> long XXH3_mix16B(
            final long seed, final T input, final Access<T> access, final long offIn, final long offSec) {
        final long input_lo = access.i64(input, offIn);
        final long input_hi = access.i64(input, offIn + 8);
        return unsignedLongMulXorFold(
                input_lo ^ (unsafeLE.i64(XXH3_kSecret, offSec) + seed),
                input_hi ^ (unsafeLE.i64(XXH3_kSecret, offSec + 8) - seed));
    }

    private static long XXH3_mix2Accs(final long acc_lh, final long acc_rh, final byte[] secret, final long offSec) {
        return unsignedLongMulXorFold(acc_lh ^ unsafeLE.i64(secret, offSec), acc_rh ^ unsafeLE.i64(secret, offSec + 8));
    }

    public static long hash64(byte[] bytes, int offset, int length) {
        return XXH3_64bits_internal(0, XXH3_kSecret, bytes, UnsafeAccess.INSTANCE, offset, length);
    }

    private static <T> long XXH3_64bits_internal(
            final long seed,
            final byte[] secret,
            final T input,
            final Access<T> access,
            final long off,
            final long length) {
        if (length <= 16) {
            // XXH3_len_0to16_64b
            if (length > 8) {
                // XXH3_len_9to16_64b
                final long bitflip1 = (unsafeLE.i64(XXH3_kSecret, 24 + UnsafeAccess.BYTE_BASE)
                                ^ unsafeLE.i64(XXH3_kSecret, 32 + UnsafeAccess.BYTE_BASE))
                        + seed;
                final long bitflip2 = (unsafeLE.i64(XXH3_kSecret, 40 + UnsafeAccess.BYTE_BASE)
                                ^ unsafeLE.i64(XXH3_kSecret, 48 + UnsafeAccess.BYTE_BASE))
                        - seed;
                final long input_lo = access.i64(input, off) ^ bitflip1;
                final long input_hi = access.i64(input, off + length - 8) ^ bitflip2;
                final long acc =
                        length + Long.reverseBytes(input_lo) + input_hi + unsignedLongMulXorFold(input_lo, input_hi);
                return XXH3_avalanche(acc);
            }
            if (length >= 4) {
                // XXH3_len_4to8_64b
                long s = seed ^ Long.reverseBytes(seed & 0xFFFFFFFFL);
                final long input1 = (long) access.i32(input, off); // high int will be shifted
                final long input2 = access.u32(input, off + length - 4);
                final long bitflip = (unsafeLE.i64(XXH3_kSecret, 8 + UnsafeAccess.BYTE_BASE)
                                ^ unsafeLE.i64(XXH3_kSecret, 16 + UnsafeAccess.BYTE_BASE))
                        - s;
                final long keyed = (input2 + (input1 << 32)) ^ bitflip;
                return XXH3_rrmxmx(keyed, length);
            }
            if (length != 0) {
                // XXH3_len_1to3_64b
                final int c1 = access.u8(input, off + 0);
                final int c2 = access.i8(input, off + (length >> 1)); // high 3 bytes will be shifted
                final int c3 = access.u8(input, off + length - 1);
                final long combined = Primitives.unsignedInt((c1 << 16) | (c2 << 24) | c3 | ((int) length << 8));
                final long bitflip = Primitives.unsignedInt(unsafeLE.i32(XXH3_kSecret, UnsafeAccess.BYTE_BASE)
                                ^ unsafeLE.i32(XXH3_kSecret, 4 + UnsafeAccess.BYTE_BASE))
                        + seed;
                return XXH64_avalanche(combined ^ bitflip);
            }
            return XXH64_avalanche(seed
                    ^ unsafeLE.i64(XXH3_kSecret, 56 + UnsafeAccess.BYTE_BASE)
                    ^ unsafeLE.i64(XXH3_kSecret, 64 + UnsafeAccess.BYTE_BASE));
        }
        if (length <= 128) {
            // XXH3_len_17to128_64b
            long acc = length * XXH_PRIME64_1;

            if (length > 32) {
                if (length > 64) {
                    if (length > 96) {
                        acc += XXH3_mix16B(seed, input, access, off + 48, UnsafeAccess.BYTE_BASE + 96);
                        acc += XXH3_mix16B(seed, input, access, off + length - 64, UnsafeAccess.BYTE_BASE + 112);
                    }
                    acc += XXH3_mix16B(seed, input, access, off + 32, UnsafeAccess.BYTE_BASE + 64);
                    acc += XXH3_mix16B(seed, input, access, off + length - 48, UnsafeAccess.BYTE_BASE + 80);
                }
                acc += XXH3_mix16B(seed, input, access, off + 16, UnsafeAccess.BYTE_BASE + 32);
                acc += XXH3_mix16B(seed, input, access, off + length - 32, UnsafeAccess.BYTE_BASE + 48);
            }
            acc += XXH3_mix16B(seed, input, access, off, UnsafeAccess.BYTE_BASE);
            acc += XXH3_mix16B(seed, input, access, off + length - 16, UnsafeAccess.BYTE_BASE + 16);

            return XXH3_avalanche(acc);
        }
        if (length <= 240) {
            // XXH3_len_129to240_64b
            long acc = length * XXH_PRIME64_1;
            final int nbRounds = (int) length / 16;
            int i = 0;
            for (; i < 8; ++i) {
                acc += XXH3_mix16B(seed, input, access, off + 16 * i, UnsafeAccess.BYTE_BASE + 16 * i);
            }
            acc = XXH3_avalanche(acc);

            for (; i < nbRounds; ++i) {
                acc += XXH3_mix16B(seed, input, access, off + 16 * i, UnsafeAccess.BYTE_BASE + 16 * (i - 8) + 3);
            }

            /* last bytes */
            acc += XXH3_mix16B(seed, input, access, off + length - 16, UnsafeAccess.BYTE_BASE + 136 - 17);
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
                    final long data_val_0 = access.i64(input, offStripe + 8 * 0);
                    final long data_val_1 = access.i64(input, offStripe + 8 * 1);
                    final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 0);
                    final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 1);
                    /* swap adjacent lanes */
                    acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = access.i64(input, offStripe + 8 * 2);
                    final long data_val_1 = access.i64(input, offStripe + 8 * 3);
                    final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 2);
                    final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 3);
                    /* swap adjacent lanes */
                    acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = access.i64(input, offStripe + 8 * 4);
                    final long data_val_1 = access.i64(input, offStripe + 8 * 5);
                    final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 4);
                    final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 5);
                    /* swap adjacent lanes */
                    acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
                {
                    final long data_val_0 = access.i64(input, offStripe + 8 * 6);
                    final long data_val_1 = access.i64(input, offStripe + 8 * 7);
                    final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 6);
                    final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 7);
                    /* swap adjacent lanes */
                    acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                    acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
                }
            }

            // XXH3_scrambleAcc_scalar
            final long offSec = UnsafeAccess.BYTE_BASE + 192 - 64;
            acc_0 = (acc_0 ^ (acc_0 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 0)) * XXH_PRIME32_1;
            acc_1 = (acc_1 ^ (acc_1 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 1)) * XXH_PRIME32_1;
            acc_2 = (acc_2 ^ (acc_2 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 2)) * XXH_PRIME32_1;
            acc_3 = (acc_3 ^ (acc_3 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 3)) * XXH_PRIME32_1;
            acc_4 = (acc_4 ^ (acc_4 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 4)) * XXH_PRIME32_1;
            acc_5 = (acc_5 ^ (acc_5 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 5)) * XXH_PRIME32_1;
            acc_6 = (acc_6 ^ (acc_6 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 6)) * XXH_PRIME32_1;
            acc_7 = (acc_7 ^ (acc_7 >>> 47) ^ unsafeLE.i64(secret, offSec + 8 * 7)) * XXH_PRIME32_1;
        }

        /* last partial block */
        final long nbStripes = ((length - 1) - (block_len * nb_blocks)) / 64;
        final long offBlock = off + block_len * nb_blocks;
        for (long s = 0; s < nbStripes; s++) {
            // XXH3_accumulate_512
            final long offStripe = offBlock + s * 64;
            final long offSec = s * 8;
            {
                final long data_val_0 = access.i64(input, offStripe + 8 * 0);
                final long data_val_1 = access.i64(input, offStripe + 8 * 1);
                final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 0);
                final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 1);
                /* swap adjacent lanes */
                acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = access.i64(input, offStripe + 8 * 2);
                final long data_val_1 = access.i64(input, offStripe + 8 * 3);
                final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 2);
                final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 3);
                /* swap adjacent lanes */
                acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = access.i64(input, offStripe + 8 * 4);
                final long data_val_1 = access.i64(input, offStripe + 8 * 5);
                final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 4);
                final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 5);
                /* swap adjacent lanes */
                acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
                acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
            }
            {
                final long data_val_0 = access.i64(input, offStripe + 8 * 6);
                final long data_val_1 = access.i64(input, offStripe + 8 * 7);
                final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 6);
                final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 7);
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
            final long data_val_0 = access.i64(input, offStripe + 8 * 0);
            final long data_val_1 = access.i64(input, offStripe + 8 * 1);
            final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 0);
            final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 1);
            /* swap adjacent lanes */
            acc_0 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_1 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = access.i64(input, offStripe + 8 * 2);
            final long data_val_1 = access.i64(input, offStripe + 8 * 3);
            final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 2);
            final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 3);
            /* swap adjacent lanes */
            acc_2 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_3 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = access.i64(input, offStripe + 8 * 4);
            final long data_val_1 = access.i64(input, offStripe + 8 * 5);
            final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 4);
            final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 5);
            /* swap adjacent lanes */
            acc_4 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_5 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }
        {
            final long data_val_0 = access.i64(input, offStripe + 8 * 6);
            final long data_val_1 = access.i64(input, offStripe + 8 * 7);
            final long data_key_0 = data_val_0 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 6);
            final long data_key_1 = data_val_1 ^ unsafeLE.i64(secret, UnsafeAccess.BYTE_BASE + offSec + 8 * 7);
            /* swap adjacent lanes */
            acc_6 += data_val_1 + (0xFFFFFFFFL & data_key_0) * (data_key_0 >>> 32);
            acc_7 += data_val_0 + (0xFFFFFFFFL & data_key_1) * (data_key_1 >>> 32);
        }

        // XXH3_mergeAccs
        final long result64 = length * XXH_PRIME64_1
                + XXH3_mix2Accs(acc_0, acc_1, secret, UnsafeAccess.BYTE_BASE + 11)
                + XXH3_mix2Accs(acc_2, acc_3, secret, UnsafeAccess.BYTE_BASE + 11 + 16)
                + XXH3_mix2Accs(acc_4, acc_5, secret, UnsafeAccess.BYTE_BASE + 11 + 16 * 2)
                + XXH3_mix2Accs(acc_6, acc_7, secret, UnsafeAccess.BYTE_BASE + 11 + 16 * 3);

        return XXH3_avalanche(result64);
    }

    public abstract static class CharSequenceAccess extends Access<CharSequence> {

        static CharSequenceAccess charSequenceAccess(ByteOrder order) {
            return order == LITTLE_ENDIAN
                    ? LittleEndianCharSequenceAccess.INSTANCE
                    : BigEndianCharSequenceAccess.INSTANCE;
        }

        static CharSequenceAccess nativeCharSequenceAccess() {
            return charSequenceAccess(nativeOrder());
        }

        private static int ix(long offset) {
            return (int) (offset >> 1);
        }

        protected static long getLong(
                CharSequence input,
                long offset,
                int char0Off,
                int char1Off,
                int char2Off,
                int char3Off,
                int char4Off,
                int delta) {
            final int base = ix(offset);
            if (0 == ((int) offset & 1)) {
                final long char0 = input.charAt(base + char0Off);
                final long char1 = input.charAt(base + char1Off);
                final long char2 = input.charAt(base + char2Off);
                final long char3 = input.charAt(base + char3Off);
                return char0 | (char1 << 16) | (char2 << 32) | (char3 << 48);
            } else {
                final long char0 = input.charAt(base + char0Off + delta) >>> 8;
                final long char1 = input.charAt(base + char1Off + delta);
                final long char2 = input.charAt(base + char2Off + delta);
                final long char3 = input.charAt(base + char3Off + delta);
                final long char4 = input.charAt(base + char4Off);
                return char0 | (char1 << 8) | (char2 << 24) | (char3 << 40) | (char4 << 56);
            }
        }

        protected static long getUnsignedInt(
                CharSequence input, long offset, int char0Off, int char1Off, int char2Off, int delta) {
            final int base = ix(offset);
            if (0 == ((int) offset & 1)) {
                final long char0 = input.charAt(base + char0Off);
                final long char1 = input.charAt(base + char1Off);
                return char0 | (char1 << 16);
            } else {
                final long char0 = input.charAt(base + char0Off + delta) >>> 8;
                final long char1 = input.charAt(base + char1Off + delta);
                final long char2 = UnsafeAccess.unsignedByte(input.charAt(base + char2Off));
                return char0 | (char1 << 8) | (char2 << 24);
            }
        }

        protected static char getUnsignedShort(CharSequence input, long offset, int char1Off, int delta) {
            if (0 == ((int) offset & 1)) {
                return input.charAt(ix(offset));
            } else {
                final int base = ix(offset);
                final int char0 = input.charAt(base + delta) >>> 8;
                final int char1 = input.charAt(base + char1Off);
                return (char) (char0 | (char1 << 8));
            }
        }

        protected static int getUnsignedByte(CharSequence input, long offset, int shift) {
            return UnsafeAccess.unsignedByte(input.charAt(ix(offset)) >> shift);
        }

        private CharSequenceAccess() {}

        @Override
        public int getInt(CharSequence input, long offset) {
            return (int) getUnsignedInt(input, offset);
        }

        @Override
        public int getShort(CharSequence input, long offset) {
            return (int) (short) getUnsignedShort(input, offset);
        }

        @Override
        public int getByte(CharSequence input, long offset) {
            return (int) (byte) getUnsignedByte(input, offset);
        }
    }

    static final class Primitives {

        private Primitives() {}

        static final boolean NATIVE_LITTLE_ENDIAN = nativeOrder() == LITTLE_ENDIAN;

        static long unsignedInt(int i) {
            return i & 0xFFFFFFFFL;
        }

        static int unsignedShort(int s) {
            return s & 0xFFFF;
        }

        static int unsignedByte(int b) {
            return b & 0xFF;
        }

        private static final ByteOrderHelper H2LE =
                NATIVE_LITTLE_ENDIAN ? new ByteOrderHelper() : new ByteOrderHelperReverse();
        private static final ByteOrderHelper H2BE =
                NATIVE_LITTLE_ENDIAN ? new ByteOrderHelperReverse() : new ByteOrderHelper();

        static long nativeToLittleEndian(final long v) {
            return H2LE.adjustByteOrder(v);
        }

        static int nativeToLittleEndian(final int v) {
            return H2LE.adjustByteOrder(v);
        }

        static short nativeToLittleEndian(final short v) {
            return H2LE.adjustByteOrder(v);
        }

        static char nativeToLittleEndian(final char v) {
            return H2LE.adjustByteOrder(v);
        }

        static long nativeToBigEndian(final long v) {
            return H2BE.adjustByteOrder(v);
        }

        static int nativeToBigEndian(final int v) {
            return H2BE.adjustByteOrder(v);
        }

        static short nativeToBigEndian(final short v) {
            return H2BE.adjustByteOrder(v);
        }

        static char nativeToBigEndian(final char v) {
            return H2BE.adjustByteOrder(v);
        }

        private static class ByteOrderHelper {
            long adjustByteOrder(final long v) {
                return v;
            }

            int adjustByteOrder(final int v) {
                return v;
            }

            short adjustByteOrder(final short v) {
                return v;
            }

            char adjustByteOrder(final char v) {
                return v;
            }
        }

        private static class ByteOrderHelperReverse extends ByteOrderHelper {
            long adjustByteOrder(final long v) {
                return Long.reverseBytes(v);
            }

            int adjustByteOrder(final int v) {
                return Integer.reverseBytes(v);
            }

            short adjustByteOrder(final short v) {
                return Short.reverseBytes(v);
            }

            char adjustByteOrder(final char v) {
                return Character.reverseBytes(v);
            }
        }
    }

    private static class LittleEndianCharSequenceAccess extends CharSequenceAccess {
        private static final CharSequenceAccess INSTANCE = new LittleEndianCharSequenceAccess();
        private static final Access<CharSequence> INSTANCE_REVERSE = Access.newDefaultReverseAccess(INSTANCE);

        private LittleEndianCharSequenceAccess() {}

        @Override
        public long getLong(CharSequence input, long offset) {
            return getLong(input, offset, 0, 1, 2, 3, 4, 0);
        }

        @Override
        public long getUnsignedInt(CharSequence input, long offset) {
            return getUnsignedInt(input, offset, 0, 1, 2, 0);
        }

        @Override
        public int getUnsignedShort(CharSequence input, long offset) {
            return getUnsignedShort(input, offset, 1, 0);
        }

        @Override
        public int getUnsignedByte(CharSequence input, long offset) {
            return getUnsignedByte(input, offset, ((int) offset & 1) << 3);
        }

        @Override
        public ByteOrder byteOrder(CharSequence input) {
            return LITTLE_ENDIAN;
        }

        @Override
        protected Access<CharSequence> reverseAccess() {
            return INSTANCE_REVERSE;
        }
    }

    private static class BigEndianCharSequenceAccess extends CharSequenceAccess {
        private static final CharSequenceAccess INSTANCE = new BigEndianCharSequenceAccess();
        private static final Access<CharSequence> INSTANCE_REVERSE = Access.newDefaultReverseAccess(INSTANCE);

        private BigEndianCharSequenceAccess() {}

        @Override
        public long getLong(CharSequence input, long offset) {
            return getLong(input, offset, 3, 2, 1, 0, 0, 1);
        }

        @Override
        public long getUnsignedInt(CharSequence input, long offset) {
            return getUnsignedInt(input, offset, 1, 0, 0, 1);
        }

        @Override
        public int getUnsignedShort(CharSequence input, long offset) {
            return getUnsignedShort(input, offset, 0, 1);
        }

        @Override
        public int getUnsignedByte(CharSequence input, long offset) {
            return getUnsignedByte(input, offset, (((int) offset & 1) ^ 1) << 3);
        }

        @Override
        public ByteOrder byteOrder(CharSequence input) {
            return BIG_ENDIAN;
        }

        @Override
        protected Access<CharSequence> reverseAccess() {
            return INSTANCE_REVERSE;
        }
    }

    private abstract static class Access<T> {

        /**
         * Returns the {@code Access} delegating {@code getXXX(input, offset)} methods to {@code
         * sun.misc.Unsafe.getXXX(input, offset)}.
         *
         * <p>Usage example: <pre>{@code
         * class Pair {
         *     long first, second;
         *
         *     static final long pairDataOffset =
         *         theUnsafe.objectFieldOffset(Pair.class.getDeclaredField("first"));
         *
         *     static long hashPair(Pair pair, LongHashFunction hashFunction) {
         *         return hashFunction.hash(pair, Access.unsafe(), pairDataOffset, 16L);
         *     }
         * }}</pre>
         *
         * @param <T> the type of objects to access
         * @return the unsafe memory {@code Access}
         */
        @SuppressWarnings("unchecked")
        public static <T> Access<T> unsafe() {
            return (Access<T>) UnsafeAccess.INSTANCE;
        }

        /**
         * Returns the {@code Access} to any {@link ByteBuffer}.
         *
         * @return the {@code Access} to {@link ByteBuffer}s
         */
        public static Access<ByteBuffer> toByteBuffer() {
            return ByteBufferAccess.INSTANCE;
        }

        /**
         * Returns the {@code Access} to {@link CharSequence}s backed by {@linkplain
         * ByteOrder#nativeOrder() native} {@code char} reads, typically from {@code char[]} array.
         *
         * <p>Usage example:<pre>{@code
         * static long hashStringBuffer(StringBuffer buffer, LongHashFunction hashFunction) {
         *     return hashFunction.hash(buffer, Access.toNativeCharSequence(),
         *         // * 2L because length is passed in bytes, not chars
         *         0L, buffer.length() * 2L);
         * }}</pre>
         *
         * <p>This method is a shortcut for {@code Access.toCharSequence(ByteOrder.nativeOrder())}.
         *
         * @param <T> the {@code CharSequence} subtype (backed by native {@code char reads}) to access
         * @return the {@code Access} to {@link CharSequence}s backed by native {@code char} reads
         * @see #toCharSequence(ByteOrder)
         */
        @SuppressWarnings("unchecked")
        public static <T extends CharSequence> Access<T> toNativeCharSequence() {
            return (Access<T>) CharSequenceAccess.nativeCharSequenceAccess();
        }

        /**
         * Returns the {@code Access} to {@link CharSequence}s backed by {@code char} reads made in
         * the specified byte order.
         *
         * <p>Usage example:<pre>{@code
         * static long hashCharBuffer(CharBuffer buffer, LongHashFunction hashFunction) {
         *     return hashFunction.hash(buffer, Access.toCharSequence(buffer.order()),
         *         // * 2L because length is passed in bytes, not chars
         *         0L, buffer.length() * 2L);
         * }}</pre>
         *
         * @param backingOrder the byte order of {@code char} reads backing
         * {@code CharSequences} to access
         * @return the {@code Access} to {@link CharSequence}s backed by {@code char} reads made in
         * the specified byte order
         * @param <T> the {@code CharSequence} subtype to access
         * @see #toNativeCharSequence()
         */
        @SuppressWarnings("unchecked")
        public static <T extends CharSequence> Access<T> toCharSequence(ByteOrder backingOrder) {
            return (Access<T>) CharSequenceAccess.charSequenceAccess(backingOrder);
        }

        /**
         * Constructor for use in subclasses.
         */
        protected Access() {}

        /**
         * Reads {@code [offset, offset + 7]} bytes of the byte sequence represented by the given
         * {@code input} as a single {@code long} value.
         *
         * @param input the object to access
         * @param offset offset to the first byte to read within the byte sequence represented
         * by the given object
         * @return eight bytes as a {@code long} value, in {@linkplain #byteOrder(Object) the expected
         * order}
         */
        public long getLong(T input, long offset) {
            if (byteOrder(input) == LITTLE_ENDIAN) {
                return getUnsignedInt(input, offset) | (getUnsignedInt(input, offset + 4L) << 32);
            } else {
                return getUnsignedInt(input, offset + 4L) | (getUnsignedInt(input, offset) << 32);
            }
        }

        /**
         * Shortcut for {@code getInt(input, offset) & 0xFFFFFFFFL}. Could be implemented more
         * efficiently.
         *
         * @param input the object to access
         * @param offset offset to the first byte to read within the byte sequence represented
         * by the given object
         * @return four bytes as an unsigned int value, in {@linkplain #byteOrder(Object) the expected
         * order}
         */
        public long getUnsignedInt(T input, long offset) {
            return ((long) getInt(input, offset)) & 0xFFFFFFFFL;
        }

        /**
         * Reads {@code [offset, offset + 3]} bytes of the byte sequence represented by the given
         * {@code input} as a single {@code int} value.
         *
         * @param input the object to access
         * @param offset offset to the first byte to read within the byte sequence represented
         * by the given object
         * @return four bytes as an {@code int} value, in {@linkplain #byteOrder(Object) the expected
         * order}
         */
        public int getInt(T input, long offset) {
            if (byteOrder(input) == LITTLE_ENDIAN) {
                return getUnsignedShort(input, offset) | (getUnsignedShort(input, offset + 2L) << 16);
            } else {
                return getUnsignedShort(input, offset + 2L) | (getUnsignedShort(input, offset) << 16);
            }
        }

        /**
         * Shortcut for {@code getShort(input, offset) & 0xFFFF}. Could be implemented more
         * efficiently.
         *
         * @param input the object to access
         * @param offset offset to the first byte to read within the byte sequence represented
         * by the given object
         * @return two bytes as an unsigned short value, in {@linkplain #byteOrder(Object) the expected
         * order}
         */
        public int getUnsignedShort(T input, long offset) {
            if (byteOrder(input) == LITTLE_ENDIAN) {
                return getUnsignedByte(input, offset) | (getUnsignedByte(input, offset + 1L) << 8);
            } else {
                return getUnsignedByte(input, offset + 1L) | (getUnsignedByte(input, offset) << 8);
            }
        }

        /**
         * Reads {@code [offset, offset + 1]} bytes of the byte sequence represented by the given
         * {@code input} as a single {@code short} value, returned widened to {@code int}.
         *
         * @param input the object to access
         * @param offset offset to the first byte to read within the byte sequence represented
         * by the given object
         * @return two bytes as a {@code short} value, in {@linkplain #byteOrder(Object) the expected
         * order}, widened to {@code int}
         */
        public int getShort(T input, long offset) {
            return (int) (short) getUnsignedShort(input, offset);
        }

        /**
         * Shortcut for {@code getByte(input, offset) & 0xFF}. Could be implemented more efficiently.
         *
         * @param input the object to access
         * @param offset offset to the byte to read within the byte sequence represented
         * by the given object
         * @return a byte by the given {@code offset}, interpreted as unsigned
         */
        public int getUnsignedByte(T input, long offset) {
            return getByte(input, offset) & 0xFF;
        }

        /**
         * Reads a single byte at the given {@code offset} in the byte sequence represented by the given
         * {@code input}, returned widened to {@code int}.
         *
         * @param input the object to access
         * @param offset offset to the byte to read within the byte sequence represented
         * by the given object
         * @return a byte by the given {@code offset}, widened to {@code int}
         */
        public abstract int getByte(T input, long offset);

        // short names
        public long i64(final T input, final long offset) {
            return getLong(input, offset);
        }

        public long u32(final T input, final long offset) {
            return getUnsignedInt(input, offset);
        }

        public int i32(final T input, final long offset) {
            return getInt(input, offset);
        }

        public int u16(final T input, final long offset) {
            return getUnsignedShort(input, offset);
        }

        public int i16(final T input, final long offset) {
            return getShort(input, offset);
        }

        public int u8(final T input, final long offset) {
            return getUnsignedByte(input, offset);
        }

        public int i8(final T input, final long offset) {
            return getByte(input, offset);
        }

        /**
         * The byte order in which all multi-byte {@code getXXX()} reads from the given {@code input}
         * are performed.
         *
         * @param input the accessed object
         * @return the byte order of all multi-byte reads from the given {@code input}
         */
        public abstract ByteOrder byteOrder(T input);

        /**
         * Get {@code this} or the reversed access object for reading the input as fixed
         * byte order of {@code byteOrder}.
         *
         * @param input the accessed object
         * @param byteOrder the byte order to be used for reading the {@code input}
         * @return a {@code Access} object which will read the {@code input} with the
         * byte order of {@code byteOrder}.
         */
        public Access<T> byteOrder(final T input, final ByteOrder byteOrder) {
            return byteOrder(input) == byteOrder ? this : reverseAccess();
        }

        /**
         * Get the {@code Access} object with a different byte order. This method should
         * always return a fixed reference.
         */
        protected abstract Access<T> reverseAccess();

        /**
         * Get or create the reverse byte order {@code Access} object for {@code access}.
         */
        static <T> Access<T> newDefaultReverseAccess(final Access<T> access) {
            return access instanceof ReverseAccess ? access.reverseAccess() : new ReverseAccess<T>(access);
        }

        /**
         * The default reverse byte order delegating {@code Access} class.
         */
        private static class ReverseAccess<T> extends Access<T> {
            final Access<T> access;

            private ReverseAccess(final Access<T> access) {
                this.access = access;
            }

            @Override
            public long getLong(final T input, final long offset) {
                return Long.reverseBytes(access.getLong(input, offset));
            }

            @Override
            public long getUnsignedInt(final T input, final long offset) {
                return Long.reverseBytes(access.getUnsignedInt(input, offset)) >>> 32;
            }

            @Override
            public int getInt(final T input, final long offset) {
                return Integer.reverseBytes(access.getInt(input, offset));
            }

            @Override
            public int getUnsignedShort(final T input, final long offset) {
                return Integer.reverseBytes(access.getUnsignedShort(input, offset)) >>> 16;
            }

            @Override
            public int getShort(final T input, final long offset) {
                return Integer.reverseBytes(access.getShort(input, offset)) >> 16;
            }

            @Override
            public int getUnsignedByte(final T input, final long offset) {
                return access.getUnsignedByte(input, offset);
            }

            @Override
            public int getByte(final T input, final long offset) {
                return access.getByte(input, offset);
            }

            @Override
            public ByteOrder byteOrder(final T input) {
                return LITTLE_ENDIAN == access.byteOrder(input) ? BIG_ENDIAN : LITTLE_ENDIAN;
            }

            @Override
            protected Access<T> reverseAccess() {
                return access;
            }
        }
    }

    private static class UnsafeAccess extends Access<Object> {
        static final UnsafeAccess INSTANCE;
        private static final Access<Object> INSTANCE_NON_NATIVE;
        static final boolean NATIVE_LITTLE_ENDIAN = nativeOrder() == LITTLE_ENDIAN;

        // for test only
        static final UnsafeAccess OLD_INSTANCE =
                NATIVE_LITTLE_ENDIAN ? new OldUnsafeAccessLittleEndian() : new OldUnsafeAccessBigEndian();

        static final Unsafe UNSAFE;

        static final long BOOLEAN_BASE;
        static final long BYTE_BASE;
        static final long CHAR_BASE;
        static final long SHORT_BASE;
        static final long INT_BASE;
        static final long LONG_BASE;

        static final byte TRUE_BYTE_VALUE;
        static final byte FALSE_BYTE_VALUE;

        static {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                UNSAFE = (Unsafe) theUnsafe.get(null);

                BOOLEAN_BASE = UNSAFE.arrayBaseOffset(boolean[].class);
                BYTE_BASE = UNSAFE.arrayBaseOffset(byte[].class);
                CHAR_BASE = UNSAFE.arrayBaseOffset(char[].class);
                SHORT_BASE = UNSAFE.arrayBaseOffset(short[].class);
                INT_BASE = UNSAFE.arrayBaseOffset(int[].class);
                LONG_BASE = UNSAFE.arrayBaseOffset(long[].class);

                TRUE_BYTE_VALUE = (byte) UNSAFE.getInt(new boolean[] {true, true, true, true}, BOOLEAN_BASE);
                FALSE_BYTE_VALUE = (byte) UNSAFE.getInt(new boolean[] {false, false, false, false}, BOOLEAN_BASE);
            } catch (final Exception e) {
                throw new AssertionError(e);
            }

            boolean hasGetByte = true;
            try {
                UNSAFE.getByte(new byte[1], BYTE_BASE);
            } catch (final Throwable ignore) {
                // Unsafe in pre-Nougat Android does not have getByte(), fall back to workround
                hasGetByte = false;
            }

            INSTANCE = hasGetByte ? new UnsafeAccess() : OLD_INSTANCE;
            INSTANCE_NON_NATIVE = Access.newDefaultReverseAccess(INSTANCE);
        }

        private UnsafeAccess() {}

        static long unsignedInt(int i) {
            return i & 0xFFFFFFFFL;
        }

        static int unsignedShort(int s) {
            return s & 0xFFFF;
        }

        static int unsignedByte(int b) {
            return b & 0xFF;
        }

        @Override
        public long getLong(Object input, long offset) {
            return UNSAFE.getLong(input, offset);
        }

        @Override
        public long getUnsignedInt(Object input, long offset) {
            return unsignedInt(getInt(input, offset));
        }

        @Override
        public int getInt(Object input, long offset) {
            return UNSAFE.getInt(input, offset);
        }

        @Override
        public int getUnsignedShort(Object input, long offset) {
            return unsignedShort(getShort(input, offset));
        }

        @Override
        public int getShort(Object input, long offset) {
            return UNSAFE.getShort(input, offset);
        }

        @Override
        public int getUnsignedByte(Object input, long offset) {
            return unsignedByte(getByte(input, offset));
        }

        @Override
        public int getByte(Object input, long offset) {
            return UNSAFE.getByte(input, offset);
        }

        @Override
        public ByteOrder byteOrder(Object input) {
            return nativeOrder();
        }

        @Override
        protected Access<Object> reverseAccess() {
            return INSTANCE_NON_NATIVE;
        }

        private static class OldUnsafeAccessLittleEndian extends UnsafeAccess {
            @Override
            public int getShort(final Object input, final long offset) {
                return UNSAFE.getInt(input, offset - 2) >> 16;
            }

            @Override
            public int getByte(final Object input, final long offset) {
                return UNSAFE.getInt(input, offset - 3) >> 24;
            }
        }

        private static class OldUnsafeAccessBigEndian extends UnsafeAccess {
            @Override
            public int getShort(final Object input, final long offset) {
                return (int) (short) UNSAFE.getInt(input, offset - 2);
            }

            @Override
            public int getByte(final Object input, final long offset) {
                return (int) (byte) UNSAFE.getInt(input, offset - 3);
            }
        }
    }

    public static final class ByteBufferAccess extends Access<ByteBuffer> {
        public static final ByteBufferAccess INSTANCE = new ByteBufferAccess();
        private static final Access<ByteBuffer> INSTANCE_REVERSE = Access.newDefaultReverseAccess(INSTANCE);

        private ByteBufferAccess() {}

        @Override
        public long getLong(ByteBuffer input, long offset) {
            return input.getLong((int) offset);
        }

        @Override
        public long getUnsignedInt(ByteBuffer input, long offset) {
            return UnsafeAccess.unsignedInt(getInt(input, offset));
        }

        @Override
        public int getInt(ByteBuffer input, long offset) {
            return input.getInt((int) offset);
        }

        @Override
        public int getUnsignedShort(ByteBuffer input, long offset) {
            return UnsafeAccess.unsignedShort(getShort(input, offset));
        }

        @Override
        public int getShort(ByteBuffer input, long offset) {
            return input.getShort((int) offset);
        }

        @Override
        public int getUnsignedByte(ByteBuffer input, long offset) {
            return UnsafeAccess.unsignedByte(getByte(input, offset));
        }

        @Override
        public int getByte(ByteBuffer input, long offset) {
            return input.get((int) offset);
        }

        @Override
        public ByteOrder byteOrder(ByteBuffer input) {
            return input.order();
        }

        @Override
        public Access<ByteBuffer> reverseAccess() {
            return INSTANCE_REVERSE;
        }
    }
}
