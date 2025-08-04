package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.UnsafeUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains a collection of methods for hashing basic data types.
 * Hashes are not cryptographically secure, and are intended to be used when
 * implementing {@link Object#hashCode()} or similar functionality.
 */
public final class NonCryptographicHashing {
    // This class is not meant to be instantiated.
    private NonCryptographicHashing() {}

    /**
     * Generates a non-cryptographic 64-bit hash for 1 long.
     *
     * @param x0 a single long
     * @return a non-cryptographic long hash
     */
    public static long hash64(final long x0) {
        return perm64(x0);
    }

    /**
     * Generates a non-cryptographic 64-bit hash for a byte array.
     *
     * @param bytes
     * 		a byte array
     * @return a non-cryptographic long hash
     */
    public static long hash64(@NonNull final byte[] bytes) {
        return hash64(bytes, 0, bytes.length);
    }

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
    public static long hash64(@NonNull final byte[] bytes, int start, int length) {
        long hash = perm64(length);
        for (int i = start; i < length; i += 8) {
            hash = perm64(hash ^ byteArrayToLong(bytes, i));
        }
        return hash;
    }

    /**
     * <p>
     * A permutation (invertible function) on 64 bits.
     * The constants were found by automated search, to
     * optimize avalanche. Avalanche means that for a
     * random number x, flipping bit i of x has about a
     * 50 percent chance of flipping bit j of perm64(x).
     * For each possible pair (i,j), this function achieves
     * a probability between 49.8 and 50.2 percent.
     * </p>
     *
     * <p>
     * Leemon wrote this, it's magic and does magic things. Like holy molly does
     * this algorithm resolve some nasty hash collisions for troublesome data sets.
     * Don't mess with this method.
     *
     * <p>
     * Warning: there currently exist production use cases that will break if this hashing algorithm is changed.
     * If modifications to this hashing algorithm are ever required, we will need to "fork" this class and leave
     * the old algorithm intact.
     */
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

    /**
     * Return a long derived from the 8 bytes data[position]...data[position+7], big endian. If the byte array is not
     * long enough, zeros are substituted for the missing bytes.
     *
     * @param data     an array of bytes
     * @param position the first byte in the array to use
     * @return the 8 bytes starting at position, converted to a long, big endian
     */
    public static long byteArrayToLong(final byte[] data, final int position) {
        if (data.length > position + 8) {
            return UnsafeUtils.getLong(data, position);
        } else {
            // There isn't enough data to fill the long, so pad with zeros.
            long result = 0;
            for (int offset = 0; offset < 8; offset++) {
                final int index = position + offset;
                if (index >= data.length) {
                    break;
                }
                result += (data[index] & 0xffL) << (8 * (7 - offset));
            }
            return result;
        }
    }
}
