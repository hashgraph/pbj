// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

public class Xxh3Lz4 {
    private static final XXHashFactory JAVA_FACTORY = XXHashFactory.fastestJavaInstance();
    private static final XXHashFactory NATIVE_FACTORY = XXHashFactory.nativeInstance();
    private static final XXHash64 JAVA_HASH_64 = JAVA_FACTORY.hash64();
    private static final XXHash64 NATIVE_HASH_64 = NATIVE_FACTORY.hash64();

    public static long xxh_64bits_java(final byte[] bytes, int start, int length) {
        return JAVA_HASH_64.hash(bytes, start, length, 0);
    }

    public static long xxh_64bits_native(final byte[] bytes, int start, int length) {
        return NATIVE_HASH_64.hash(bytes, start, length, 0);
    }
}
