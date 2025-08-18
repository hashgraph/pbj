// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import com.dynatrace.hash4j.hashing.Hasher32;
import com.dynatrace.hash4j.hashing.Hasher64;
import com.dynatrace.hash4j.hashing.Hashing;

public class Hash4j {
    private static final Hasher64 XXH_3_64 = Hashing.xxh3_64(0);
    private static final Hasher64 FARM_HASH_NA = Hashing.farmHashNa();
    private static final Hasher64 FARM_HASH_UO = Hashing.farmHashUo();
    private static final Hasher32 MURMUR3 = Hashing.murmur3_32();

    public static long hash_xxh3_64(final byte[] bytes, int start, int length) {
        return XXH_3_64.hashBytesToLong(bytes, start, length);
    }

    public static long hash_farm_hash(final byte[] bytes, int start, int length) {
        return FARM_HASH_NA.hashBytesToLong(bytes, start, length);
    }

    public static long hash_farm_hash_uo(final byte[] bytes, int start, int length) {
        return FARM_HASH_UO.hashBytesToLong(bytes, start, length);
    }

    public static int hash_murmur_3_32(final byte[] bytes, int start, int length) {
        return MURMUR3.hashBytesToInt(bytes, start, length);
    }
}
