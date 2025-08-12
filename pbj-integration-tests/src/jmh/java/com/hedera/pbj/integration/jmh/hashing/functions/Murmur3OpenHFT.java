// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * MurmurHash3 implementation in Java, specifically the OpenHFT variant but ported to use VarHandles and hard coded to
 * byte[] inputs.
 *
 * @see <a href="https://github.com/OpenHFT/Zero-Allocation-Hashing/blob/ea/src/main/java/net/openhft/hashing/MurmurHash_3.java">
 *      Original OpenHFT MurmurHash3 Source</a>
 */
public final class Murmur3OpenHFT {
    private static final long SEED = 1L; // Default seed value
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final long C1 = 0x87c37b91114253d5L;
    private static final long C2 = 0x4cf5ad432745937fL;

    @SuppressWarnings("fallthrough")
    public static long hash64(final byte[] input, int offset, final long length) {
        long h1 = SEED;
        long h2 = SEED;
        long remaining = length;
        while (remaining >= 16L) {
            long k1 = i64(input, offset);
            long k2 = i64(input, offset + 8L);
            offset += 16;
            remaining -= 16L;
            h1 ^= mixK1(k1);

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5L + 0x52dce729L;

            h2 ^= mixK2(k2);

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5L + 0x38495ab5L;
        }

        if (remaining > 0L) {
            long k1 = 0L;
            long k2 = 0L;
            switch ((int) remaining) {
                case 15:
                    k2 ^= ((long) u8(input, offset + 14L)) << 48; // fall through
                case 14:
                    k2 ^= ((long) u8(input, offset + 13L)) << 40; // fall through
                case 13:
                    k2 ^= ((long) u8(input, offset + 12L)) << 32; // fall through
                case 12:
                    k2 ^= ((long) u8(input, offset + 11L)) << 24; // fall through
                case 11:
                    k2 ^= ((long) u8(input, offset + 10L)) << 16; // fall through
                case 10:
                    k2 ^= ((long) u8(input, offset + 9L)) << 8; // fall through
                case 9:
                    k2 ^= ((long) u8(input, offset + 8L)); // fall through
                case 8:
                    k1 ^= i64(input, offset);
                    break;
                case 7:
                    k1 ^= ((long) u8(input, offset + 6L)) << 48; // fall through
                case 6:
                    k1 ^= ((long) u8(input, offset + 5L)) << 40; // fall through
                case 5:
                    k1 ^= ((long) u8(input, offset + 4L)) << 32; // fall through
                case 4:
                    k1 ^= u32(input, offset);
                    break;
                case 3:
                    k1 ^= ((long) u8(input, offset + 2L)) << 16; // fall through
                case 2:
                    k1 ^= ((long) u8(input, offset + 1L)) << 8; // fall through
                case 1:
                    k1 ^= u8(input, offset);
                case 0:
                    break;
                default:
                    throw new AssertionError("Should never get here.");
            }
            h1 ^= mixK1(k1);
            h2 ^= mixK2(k2);
        }
        return finalize(length, h1, h2);
    }

    private static long finalize(long length, long h1, long h2) {
        h1 ^= length;
        h2 ^= length;
        h1 += h2;
        h2 += h1;
        h1 = fmix64(h1);
        h2 = fmix64(h2);
        return h1 + h2;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    private static long mixK1(long k1) {
        k1 *= C1;
        k1 = Long.rotateLeft(k1, 31);
        k1 *= C2;
        return k1;
    }

    private static long mixK2(long k2) {
        k2 *= C2;
        k2 = Long.rotateLeft(k2, 33);
        k2 *= C1;
        return k2;
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

    //
    //    /**
    //     * Shortcut for {@code getInt(input, offset) & 0xFFFFFFFFL}. Could be implemented more
    //     * efficiently.
    //     *
    //     * @param input the object to access
    //     * @param offset offset to the first byte to read within the byte sequence represented
    //     * by the given object
    //     * @return four bytes as an unsigned int value, little-endian encoded
    //     */
    //    private static long u32(final byte[] input, final long offset) {
    //        return (long) INT_HANDLE.get(input, (int)offset) & 0xFFFFFFFFL;
    //    }
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
}
