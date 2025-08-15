// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import com.hedera.pbj.runtime.io.UnsafeUtils;

/**
 * CityHash implementation in Java. CityHash is a family of hash functions developed by Google, designed to be fast and
 * efficient for hashing strings and byte arrays. Based on Apache code from tamtam180 - kirscheless at gmail.com
 *
 * @see <a href="https://gist.github.com/andriimartynov/bae6b8b2e8a3ecaace61">Original Java Port Source</a>
 * @see <a href="https://opensource.googleblog.com/2011/04/introducing-cityhash.html">Blog on CityHash</a>
 * @see <a href="https://code.google.com/p/cityhash/">CityHash Original Code</a>
 */
public class CityHashUnsafe {
    private static final long k0 = 0xc3a5c85c97cb3127L;
    private static final long k1 = 0xb492b66fbe98f273L;
    private static final long k2 = 0x9ae16a3b2f90404fL;
    private static final long k3 = 0xc949d7c7509e6557L;

    private static long rotate(long val, int shift) {
        return shift == 0 ? val : (val >>> shift) | (val << (64 - shift));
    }

    private static long rotateByAtLeast1(long val, int shift) {
        return (val >>> shift) | (val << (64 - shift));
    }

    private static long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

    private static final long kMul = 0x9ddfea08eb382d69L;

    private static long hash128to64(long u, long v) {
        long a = (u ^ v) * kMul;
        a ^= (a >>> 47);
        long b = (v ^ a) * kMul;
        b ^= (b >>> 47);
        b *= kMul;
        return b;
    }

    private static long hashLen16(long u, long v) {
        return hash128to64(u, v);
    }

    private static long hashLen0to16(byte[] s, int pos, int len) {
        if (len > 8) {
            long a = UnsafeUtils.getLongNoChecksLittleEndian(s, pos);
            long b = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 8);
            return hashLen16(a, rotateByAtLeast1(b + len, len)) ^ b;
        }
        if (len >= 4) {
            long a = 0xffffffffL & UnsafeUtils.getIntUnsafeLittleEndian(s, pos);
            return hashLen16((a << 3) + len, 0xffffffffL & UnsafeUtils.getIntUnsafeLittleEndian(s, pos + len - 4));
        }
        if (len > 0) {
            int a = s[pos] & 0xFF;
            int b = s[pos + (len >>> 1)] & 0xFF;
            int c = s[pos + len - 1] & 0xFF;
            int y = a + (b << 8);
            int z = len + (c << 2);
            return shiftMix(y * k2 ^ z * k3) * k2;
        }
        return k2;
    }

    private static long hashLen17to32(byte[] s, int pos, int len) {
        long a = UnsafeUtils.getLongNoChecksLittleEndian(s, pos) * k1;
        long b = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 8);
        long c = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 8) * k2;
        long d = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 16) * k0;
        return hashLen16(rotate(a - b, 43) + rotate(c, 30) + d, a + rotate(b ^ k3, 20) - c + len);
    }

    private static long[] weakHashLen32WithSeeds(long w, long x, long y, long z, long a, long b) {
        a += w;
        b = rotate(b + a + z, 21);
        long c = a;
        a += x;
        a += y;
        b += rotate(a, 44);
        return new long[] {a + z, b + c};
    }

    private static long[] weakHashLen32WithSeeds(byte[] s, int pos, long a, long b) {
        return weakHashLen32WithSeeds(
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos),
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 8),
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 16),
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 24),
                a,
                b);
    }

    private static long hashLen33to64(byte[] s, int pos, int len) {

        long z = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 24);
        long a = UnsafeUtils.getLongNoChecksLittleEndian(s, pos)
                + (UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 16) + len) * k0;
        long b = rotate(a + z, 52);
        long c = rotate(a, 37);

        a += UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 8);
        c += rotate(a, 7);
        a += UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 16);

        long vf = a + z;
        long vs = b + rotate(a, 31) + c;

        a = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 16)
                + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 32);
        z = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 8);
        b = rotate(a + z, 52);
        c = rotate(a, 37);
        a += UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 24);
        c += rotate(a, 7);
        a += UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 16);

        long wf = a + z;
        long ws = b + rotate(a, 31) + c;
        long r = shiftMix((vf + ws) * k2 + (wf + vs) * k0);

        return shiftMix(r * k0 + vs) * k2;
    }

    public static long cityHash64(byte[] s, int pos, int len) {
        if (len <= 32) {
            if (len <= 16) {
                return hashLen0to16(s, pos, len);
            } else {
                return hashLen17to32(s, pos, len);
            }
        } else if (len <= 64) {
            return hashLen33to64(s, pos, len);
        }

        long x = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 40);
        long y = UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 16)
                + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 56);
        long z = hashLen16(
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 48) + len,
                UnsafeUtils.getLongNoChecksLittleEndian(s, pos + len - 24));

        long[] v = weakHashLen32WithSeeds(s, pos + len - 64, len, z);
        long[] w = weakHashLen32WithSeeds(s, pos + len - 32, y + k1, x);
        x = x * k1 + UnsafeUtils.getLongNoChecksLittleEndian(s, pos);

        len = (len - 1) & (~63);
        do {
            x = rotate(x + y + v[0] + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 8), 37) * k1;
            y = rotate(y + v[1] + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 48), 42) * k1;
            x ^= w[1];
            y += v[0] + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 40);
            z = rotate(z + w[0], 33) * k1;
            v = weakHashLen32WithSeeds(s, pos, v[1] * k1, x + w[0]);
            w = weakHashLen32WithSeeds(s, pos + 32, z + w[1], y + UnsafeUtils.getLongNoChecksLittleEndian(s, pos + 16));
            {
                long swap = z;
                z = x;
                x = swap;
            }
            pos += 64;
            len -= 64;
        } while (len != 0);
        return hashLen16(hashLen16(v[0], w[0]) + shiftMix(y) * k1 + z, hashLen16(v[1], w[1]) + x);
    }
}
