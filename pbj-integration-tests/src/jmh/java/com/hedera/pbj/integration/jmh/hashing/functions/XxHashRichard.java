// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public final class XxHashRichard {
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    static final int SEED = 0;
    static final int PRIME1 = 0x9E3779B1;
    static final int PRIME2 = 0x85EBCA77;
    static final int PRIME3 = 0xC2B2AE3D;
    static final int PRIME4 = 0x27D4EB2F;
    static final int PRIME5 = 0x165667B1;

    public static int hash(byte[] data, int offset, int length) {
        int end = offset + length;
        int h32;
        if (data.length >= 16) {
            int limit = end - 16;
            int v1 = SEED + PRIME1 + PRIME2;
            int v2 = SEED + PRIME2;
            int v3 = SEED;
            int v4 = SEED - PRIME1;

            do {
                v1 += (int) INT_HANDLE.get(data, offset) * PRIME2;
                v1 = Integer.rotateLeft(v1, 13);
                v1 *= PRIME1;
                offset += 4;
                v2 += (int) INT_HANDLE.get(data, offset) * PRIME2;
                v2 = Integer.rotateLeft(v2, 13);
                v2 *= PRIME1;
                offset += 4;
                v3 += (int) INT_HANDLE.get(data, offset) * PRIME2;
                v3 = Integer.rotateLeft(v3, 13);
                v3 *= PRIME1;
                offset += 4;
                v4 += (int) INT_HANDLE.get(data, offset) * PRIME2;
                v4 = Integer.rotateLeft(v4, 13);
                v4 *= PRIME1;
                offset += 4;
            } while (offset <= limit);

            h32 = Integer.rotateLeft(v1, 1)
                    + Integer.rotateLeft(v2, 7)
                    + Integer.rotateLeft(v3, 12)
                    + Integer.rotateLeft(v4, 18);
        } else {
            h32 = SEED + PRIME5;
        }

        for (h32 += data.length; offset <= end - 4; offset += 4) {
            h32 += (int) INT_HANDLE.get(data, offset) * PRIME3;
            h32 = Integer.rotateLeft(h32, 17) * PRIME4;
        }

        while (offset < end) {
            h32 += (data[offset] & 255) * PRIME5;
            h32 = Integer.rotateLeft(h32, 11) * PRIME1;
            ++offset;
        }

        h32 ^= h32 >>> 15;
        h32 *= PRIME2;
        h32 ^= h32 >>> 13;
        h32 *= PRIME3;
        h32 ^= h32 >>> 16;
        return h32;
    }
}
