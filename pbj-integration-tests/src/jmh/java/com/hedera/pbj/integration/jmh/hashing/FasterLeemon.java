// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Performs a non-cryptographic 64-bit hash function based on the Leemon algorithm.
 */
public final class FasterLeemon {
    /**
     * Generates a non-cryptographic 64-bit hash for a byte array.
     *
     * @param bytes
     * 		a byte array
     * @param start
     *         the start index in the byte array
     * @param length
     *         the number of bytes to hash
     * @return a non-cryptographic long hash
     */
    public static long hash64(@NonNull final byte[] bytes, final int start, final int length) {
        long hash = 0;
        int i = start;
        for (; i < start + length - 7; i += 8) {
            hash = perm64(hash ^ UnsafeUtils.getLongNoChecksLittleEndian(bytes, i));
        }

        long tail = 0xFF;
        for (; i < start + length; i++) {
            tail <<= 8;
            tail |= bytes[i];
        }
        hash = perm64(hash ^ tail);

        return hash;
    }

    private static long perm64(long x) {
        // This is necessary so that 0 does not hash to 0.
        // As a side effect, this constant will hash to 0.
        // It was randomly generated (not using Java),
        // so that it will occur in practice less often than more
        // common numbers like 0 or -1 or Long.MAX_VALUE.
        x ^= 0x5e8a016a5eb99c18L;

        // Shifts: {30, 27, 16, 20, 5, 18, 10, 24, 30}
        x += x << 30;
        x ^= x >>> 27;
        x += x << 16;
        x ^= x >>> 20;
        x += x << 5;
        x ^= x >>> 18;
        x += x << 10;
        x ^= x >>> 24;
        x += x << 30;
        return x;
    }

    // Sample vectorized version commented out for now, as it requires JDK 21+ and the vector API is still incubating.
    //    /**
    //     * Vectorized version for processing multiple long values in parallel.
    //     * This can be useful when hashing multiple values or for internal operations.
    //     */
    //    private static LongVector perm64Vector(LongVector v) {
    //        // Apply the XOR constant
    //        v = v.lanewise(VectorOperators.XOR, XOR_CONSTANT);
    //
    //        // Perform the permutation operations using vector operations
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 30));
    //        v = v.lanewise(VectorOperators.XOR, v.lanewise(VectorOperators.LSHR, 27));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 16));
    //        v = v.lanewise(VectorOperators.XOR, v.lanewise(VectorOperators.LSHR, 20));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 5));
    //        v = v.lanewise(VectorOperators.XOR, v.lanewise(VectorOperators.LSHR, 18));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 10));
    //        v = v.lanewise(VectorOperators.XOR, v.lanewise(VectorOperators.LSHR, 24));
    //        v = v.add(v.lanewise(VectorOperators.LSHL, 30));
    //
    //        return v;
    //    }
}
