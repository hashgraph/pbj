// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import com.google.common.hash.Hashing;

/**
 * Guava hashing functions using the Guava library. So they can be easily used in JMH benchmarks or other tests.
 * <p>
 * This class provides methods to compute MurmurHash3 and SipHash24 hashes using Guava's Hashing utilities.
 * </p>
 */
public final class Guava {

    public static int murmurhash3_x86_32(byte[] data, int offset, int len) {
        return Hashing.murmur3_32_fixed().hashBytes(data, offset, len).asInt();
    }

    public static int sipHash24(byte[] data, int offset, int len) {
        return Hashing.sipHash24().hashBytes(data, offset, len).asInt();
    }

    public static int farmHash(byte[] data, int offset, int len) {
        return Hashing.farmHashFingerprint64().hashBytes(data, offset, len).asInt();
    }

    public static void main(String[] args) {
        byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        farmHash(data, 0, data.length);
    }
}
