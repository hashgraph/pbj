// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import edu.umd.cs.findbugs.annotations.NonNull;

public class OlegHash {
    static final int[] preHashed;

    static {
        preHashed = new int[256];
        for (int b = 0; b < 256; ++b) {
            int hash = 0;
            for (int m = 1 << 7; m != 0; m >>= 1) {
                hash <<= 1;
                if ((b & m) != 0) {
                    hash ^= 0x8b;
                }
            }
            preHashed[b] = hash;
        }
    }

    public static int hash32(@NonNull final byte[] bytes, final int start, final int length) {
        int hash = 0;
        for (int i = start; i < start + length; ++i) {
            hash = (hash << 8) ^ preHashed[(hash >> 24) & 0xff] ^ (bytes[i] & 0xff);
        }
        return hash;
    }

    public static int hash32_2old(byte[] bytes, final int start, final int length) {
        int hash = 0;
        for (int i = start; i < start + length; ++i) {
            hash = (hash << 8) + (hash >>> 24) + (bytes[i] & 0xff);
        }
        return hash;
    }

    public static int hash32_2(byte[] bytes, final int start, final int length) {
        int hash = 0;
        for (int i = start; i < start + length; ++i) {
            hash = (hash << 8) + (hash >>> 24) * 3 + (bytes[i] & 0xff);
        }
        return hash;
    }

    public static long hash64(@NonNull final byte[] bytes, final int start, final int length) {
        long hash = 0;
        for (int i = start; i < start + length; ++i) {
            hash = (hash << 8) + (hash >>> 56) + (bytes[i] & 0xff);
        }
        return hash;
    }
}
