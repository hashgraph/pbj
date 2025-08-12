// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Minimalized port of https://github.com/dynatrace-oss/hash4j/blob/main/src/main/java/com/dynatrace/hash4j/hashing/Rapidhash3.java
 *
 * This file includes a Java port of the Rapidhash algorithm originally published
 * at https://github.com/Nicoshev/rapidhash under the following license:
 *
 * Copyright 2025 Nicolas De Carli
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public final class RapidHash3 {
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    private static final long SEC0 = 0x2d358dccaa6c78a5L;
    private static final long SEC1 = 0x8bb84b93962eacc9L;
    private static final long SEC2 = 0x4b33a62ed433d4a3L;
    private static final long SEC3 = 0x4d5a2da51de1aa47L;
    private static final long SEC4 = 0xa0761d6478bd642fL;
    private static final long SEC5 = 0xe7037ed1a0b428dbL;
    private static final long SEC6 = 0x90ed1765281c388cL;
    private static final long SEC7 = 0xaaaaaaaaaaaaaaaaL;

    private static final long SEED;

    static {
        final long startSeed = 0L;
        SEED = startSeed ^ mix(startSeed ^ SEC2, SEC1);
    }

    /**
     * Returns the most significant 64 bits of the unsigned 128-bit product of two unsigned 64-bit
     * factors as a long.
     *
     * @param x the first value
     * @param y the second value
     * @return the result
     */
    private static long unsignedMultiplyHigh(long x, long y) {
        return Math.multiplyHigh(x, y) + ((x >> 63) & y) + ((y >> 63) & x);
    }

    private static long mix(long a, long b) {
        long x = a * b;
        long y = unsignedMultiplyHigh(a, b);
        return x ^ y;
    }

    /**
     * Reads a {@code long} value from a byte array with given offset.
     *
     * @param b a byte array
     * @param off an offset
     * @return the read value
     */
    private static long getLong(byte[] b, int off) {
        return (long) LONG_HANDLE.get(b, off);
    }

    /**
     * Reads an {@code int} value from a byte array with given offset.
     *
     * @param b a byte array
     * @param off an offset
     * @return the read value
     */
    public static int getInt(byte[] b, int off) {
        return (int) INT_HANDLE.get(b, off);
    }

    /**
     * Hashes the given byte array to a long value using the RapidHash3 algorithm.
     *
     * @param input the byte array to hash
     * @param off the offset in the byte array to start hashing from
     * @param len the length of the byte array to hash
     * @return the resulting hash as a long value
     */
    public static long hashBytesToLong(byte[] input, int off, int len) {
        long see0 = SEED;
        long a;
        long b;
        if (len <= 16) {
            if (len >= 4) {
                if (len >= 8) {
                    a = getLong(input, off);
                    b = getLong(input, off + len - 8);
                } else {
                    b = getInt(input, off) & 0xFFFFFFFFL;
                    a = getInt(input, off + len - 4) & 0xFFFFFFFFL;
                }
                a ^= len;
                see0 ^= len;
            } else if (len > 0) {
                a = ((input[off] & 0xFFL) << 45) ^ (input[off + len - 1] & 0xFFL) ^ len;
                b = input[off + (len >> 1)] & 0xFFL;
            } else {
                a = 0;
                b = 0;
            }
        } else {
            long see1 = see0;
            long see2 = see0;
            long see3 = see0;
            long see4 = see0;
            long see5 = see0;
            long see6 = see0;
            if (len > 112) {
                do {
                    see0 = mix(getLong(input, off) ^ SEC0, getLong(input, off + 8) ^ see0);
                    see1 = mix(getLong(input, off + 16) ^ SEC1, getLong(input, off + 24) ^ see1);
                    see2 = mix(getLong(input, off + 32) ^ SEC2, getLong(input, off + 40) ^ see2);
                    see3 = mix(getLong(input, off + 48) ^ SEC3, getLong(input, off + 56) ^ see3);
                    see4 = mix(getLong(input, off + 64) ^ SEC4, getLong(input, off + 72) ^ see4);
                    see5 = mix(getLong(input, off + 80) ^ SEC5, getLong(input, off + 88) ^ see5);
                    see6 = mix(getLong(input, off + 96) ^ SEC6, getLong(input, off + 104) ^ see6);
                    off += 112;
                    len -= 112;
                } while (len > 112);
                see0 ^= see1;
                see2 ^= see3;
                see4 ^= see5;
                see0 ^= see6;
                see2 ^= see4;
                see0 ^= see2;
            }
            if (len > 16) {
                see0 = mix(getLong(input, off) ^ SEC2, getLong(input, off + 8) ^ see0);
                if (len > 32) {
                    see0 = mix(getLong(input, off + 16) ^ SEC2, getLong(input, off + 24) ^ see0);
                    if (len > 48) {
                        see0 = mix(getLong(input, off + 32) ^ SEC1, getLong(input, off + 40) ^ see0);
                        if (len > 64) {
                            see0 = mix(getLong(input, off + 48) ^ SEC1, getLong(input, off + 56) ^ see0);
                            if (len > 80) {
                                see0 = mix(getLong(input, off + 64) ^ SEC2, getLong(input, off + 72) ^ see0);
                                if (len > 96) {
                                    see0 = mix(getLong(input, off + 80) ^ SEC1, getLong(input, off + 88) ^ see0);
                                }
                            }
                        }
                    }
                }
            }
            a = getLong(input, off + len - 16);
            b = getLong(input, off + len - 8);
        }
        long a1 = a;
        long b1 = b;
        long len1 = len;
        len1 ^= SEC1;
        a1 ^= len1;
        b1 ^= see0;
        return mix((a1 * b1) ^ SEC7, unsignedMultiplyHigh(a1, b1) ^ len1);
    }
}
