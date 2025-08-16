// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

/**
 * Ultra-fast XXH3-inspired combiner for 64-bit field values.
 * <p>
 * <b>Goal:</b>
 * <ul>
 * <li>Combine a sequence of 0..N 64-bit values (produced from fields) into a single 64-bit hash.</li>
 *   <li>Skip default-valued fields by not adding them to the sequence.</li>
 *   <li>No per-call allocations: only local vars, static methods, and compile-time constants.</li>
 *   <li>"Close to" XXH3-64 distribution; not bit-for-bit identical for arbitrary lengths.</li>
 *   <li>Very codegen-friendly: just keep local {acc, totalBytes, carry, haveCarry, pairIndex} and call static methods.</li>
 * </ul>
 * <p>
 * <b>Pattern:</b>
 * <ul>
 *   <li>Accumulator starts at 0, totalBytes at 0.</li>
 *   <li>For each non-default field, convert to a 64-bit value v. If you have no carry, set carry=v and haveCarry=true;
 *   otherwise combine the pair (carry, v) with mixPair() and clear carry.</li>
 *   <li>After all fields, if a single leftover carry exists, fold it with mixTail8().</li>
 *   <li>Finalize with finish().</li>
 * </ul>
 * <p>
 * If the sequence is empty (no non-defaults), finish() returns the canonical empty-hash for XXH3 with seed=0.
 * <p>
 * <b>Notes:</b>
 * <ul>
 *   <li>This uses the same core mix and avalanche functions as XXH3, with a simple rotating secret schedule.</li>
 *   <li>For best throughput, unroll in your generator (avoid loops if you can), but the call overhead is tiny.</li>
 * </ul>
 * <pre>
 * {@code
 * // Pseudo-example showing how your generator should produce code for a model:
 * final class ExampleModel {
 *     long a; // default 0
 *     long b; // default 0
 *     int  c; // default 0
 *     double d; // default 0.0
 *     // ...
 *
 *     // Convert each field to a 64-bit value deterministically (little-endian representation in spirit).
 *     // Only include the field if it is non-default for that field.
 *     public long hashCode64() {
 *         long $xx_acc = 0L;
 *         long $xx_total = 0L;
 *         long $xx_carry = 0L;
 *         boolean $xx_haveCarry = false;
 *         int $xx_pairIndex = 0;
 *
 *         // Field a (long)
 *         if (a != 0L) {
 *             final long v = a; // already a 64-bit value
 *             if (!$xx_haveCarry) { $xx_carry = v; $xx_haveCarry = true; $xx_total += 8; }
 *             else { $xx_acc += XXH3FieldHash.mixPair($xx_carry, v, $xx_pairIndex++); $xx_haveCarry = false; $xx_total += 8; }
 *         }
 *
 *         // Field b (long)
 *         if (b != 0L) {
 *             final long v = b;
 *             if (!$xx_haveCarry) { $xx_carry = v; $xx_haveCarry = true; $xx_total += 8; }
 *             else { $xx_acc += XXH3FieldHash.mixPair($xx_carry, v, $xx_pairIndex++); $xx_haveCarry = false; $xx_total += 8; }
 *         }
 *
 *         // Field c (int) -> widen to 64-bits in a stable way
 *         if (c != 0) {
 *             final long v = ((long) c) & 0xFFFFFFFFL; // LE semantics if desired
 *             if (!$xx_haveCarry) { $xx_carry = v; $xx_haveCarry = true; $xx_total += 8; }
 *             else { $xx_acc += XXH3FieldHash.mixPair($xx_carry, v, $xx_pairIndex++); $xx_haveCarry = false; $xx_total += 8; }
 *         }
 *
 *         // Field d (double)
 *         if (Double.doubleToRawLongBits(d) != 0L) {
 *             final long v = Double.doubleToRawLongBits(d);
 *             if (!$xx_haveCarry) { $xx_carry = v; $xx_haveCarry = true; $xx_total += 8; }
 *             else { $xx_acc += XXH3FieldHash.mixPair($xx_carry, v, $xx_pairIndex++); $xx_haveCarry = false; $xx_total += 8; }
 *         }
 *
 *         // ... repeat for all fields ...
 *
 *         if ($xx_haveCarry) {
 *             $xx_acc += XXH3FieldHash.mixTail8($xx_carry, $xx_pairIndex);
 *         }
 *         return XXH3FieldHash.finish($xx_acc, $xx_total);
 *     }
 * }
 * }
 * </pre>
 */
public final class XXH3FieldHash {

    private XXH3FieldHash() {}

    // XXH3 constants (seed=0), inlined to avoid touching an instance.
    // We use only the first 16 for the rotating pair schedule.
    private static final long SECRET_00 = 0xbe4ba423396cfeb8L;
    private static final long SECRET_01 = 0x1cad21f72c81017cL;
    private static final long SECRET_02 = 0xdb979083e96dd4deL;
    private static final long SECRET_03 = 0x1f67b3b7a4a44072L;
    private static final long SECRET_04 = 0x78e5c0cc4ee679cbL;
    private static final long SECRET_05 = 0x2172ffcc7dd05a82L;
    private static final long SECRET_06 = 0x8e2443f7744608b8L;
    private static final long SECRET_07 = 0x4c263a81e69035e0L;
    private static final long SECRET_08 = 0xcb00c391bb52283cL;
    private static final long SECRET_09 = 0xa32e531b8b65d088L;
    private static final long SECRET_10 = 0x4ef90da297486471L;
    private static final long SECRET_11 = 0xd8acdea946ef1938L;
    private static final long SECRET_12 = 0x3f349ce33f76faa8L;
    private static final long SECRET_13 = 0x1d4f0bc7c7bbdcf9L;
    private static final long SECRET_14 = 0x3159b4cd4be0518aL;
    private static final long SECRET_15 = 0x647378d9c97e9fc8L;

    // INIT_ACC_1 from xxh3 (publicly visible value in the provided code)
    private static final long INIT_ACC_1 = 0x9E3779B185EBCA87L;

    // Canonical hash for empty input with seed=0 (matches XXH3_64.DEFAULT_INSTANCE.hash0)
    // Computed as avalanche64(0 ^ (SECRET_07 ^ SECRET_08)).
    private static final long EMPTY_HASH =
            XXH3_64.avalanche64(SECRET_07 ^ SECRET_08);

    // Small fixed table for rotating secret pairs. index = (pairIndex & 7) << 1
    private static final long[] SECRET_PAIRS = {
            SECRET_00, SECRET_01,
            SECRET_02, SECRET_03,
            SECRET_04, SECRET_05,
            SECRET_06, SECRET_07,
            SECRET_08, SECRET_09,
            SECRET_10, SECRET_11,
            SECRET_12, SECRET_13,
            SECRET_14, SECRET_15
    };

    private static long mix2(long lo, long hi, long s0, long s1) {
        // Equivalent to the XXH3 "mix2Accs" core: mix(lo ^ s0, hi ^ s1)
        return XXH3_64.mix(lo ^ s0, hi ^ s1);
    }

    /**
     * Mix a pair of 64-bit values into the accumulator with a rotating secret schedule.
     *
     * @param lo        first value in the pair
     * @param hi        second value in the pair
     * @param pairIndex 0-based index of the pair (increment by 1 each time you call mixPair)
     * @return updated accumulator
     */
    public static long mixPair(long lo, long hi, int pairIndex) {
        final int base = (pairIndex & 7) << 1; // 0..14
        final long s0 = SECRET_PAIRS[base];
        final long s1 = SECRET_PAIRS[base + 1];
        return XXH3_64.mix(lo ^ s0, hi ^ s1);
    }

    /**
     * Mix a single 64-bit leftover ("tail") value into the accumulator.
     * Uses rrmxmx with a rotating secret; very fast and high quality.
     *
     * @param v         tail value (one leftover long)
     * @param pairIndex the next pair index (i.e., the count of full pairs already processed)
     * @return updated accumulator
     */
    public static long mixTail8(long v, int pairIndex) {
        final int base = (pairIndex & 7) << 1;
        final long s0 = SECRET_PAIRS[base];
        // rrmxmx gives excellent avalanching for a single 64-bit word with a "length" tweak of 8
        return XXH3_64.rrmxmx(v ^ s0, 8);
    }

    /**
     * Finish the hash. If totalBytes==0 returns the canonical XXH3 empty hash (seed=0).
     * Otherwise applies a simple length bias like XXH3 and finishes with avalanche3.
     *
     * @param acc         running accumulator from mixPair/mixTail8
     * @param totalBytes  total output bytes you've conceptually written (8 per non-default field)
     * @return final 64-bit hash
     */
    public static long finish(long acc, long totalBytes) {
        if (totalBytes == 0) {
            return EMPTY_HASH;
        }
        // XXH3-like finish: add a length bias and avalanche3
        return XXH3_64.avalanche3(acc + totalBytes * INIT_ACC_1);
    }

    /**
     * Convert int primitive to a 64-bit value in a stable way using little-endian semantics,
     * suitable for combining with mixPair or mixTail8.
     *
     * @param value the value to convert
     * @return a 64-bit representation of the value
     */
    public static long toLong(final int value) {
        // Convert an int to a long in a stable way (little-endian semantics)
        return ((long) value) & 0xFFFFFFFFL; // zero-extend to 64 bits
    }

    /**
     * Convert double primitive to a 64-bit value in a stable way using little-endian semantics,
     * suitable for combining with mixPair or mixTail8.
     *
     * @param value the value to convert
     * @return a 64-bit representation of the value
     */
    public static long toLong(final double value) {
        // Convert a double to a long in a stable way (little-endian semantics)
        return Double.doubleToRawLongBits(value);
    }

    /**
     * Convert float primitive to a 64-bit value in a stable way using little-endian semantics,
     * suitable for combining with mixPair or mixTail8.
     *
     * @param value the value to convert
     * @return a 64-bit representation of the value
     */
    public static long toLong(final float value) {
        // Convert a float to a long in a stable way (little-endian semantics)
        return Float.floatToRawIntBits(value) & 0xFFFFFFFFL; // zero-extend to 64 bits
    }

    /**
     * Convert boolean primitive to a 64-bit value in a stable way, suitable for combining with mixPair or mixTail8.
     *
     * @param value the value to convert
     * @return a 64-bit representation of the value
     */
    public static long toLong(final boolean value) {
        // Convert a boolean to a long in a stable way (little-endian semantics)
        return value ? 1L : 0L; // 1 for true, 0 for false
    }

    /**
     * Convert a byte array to a 64-bit value in a stable way using XXH3 64 hashing,
     * suitable for combining with mixPair or mixTail8.
     *
     * @param value the byte array to convert
     * @return a 64-bit representation of the byte array
     */
    public static long toLong(final byte[] value) {
        // Convert a byte to a long in a stable way (little-endian semantics)
        return XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(value,0,value.length);
    }

    /**
     * Convert a String to a 64-bit value in a stable way using XXH3 64 hashing of UTF16 bytes,
     * suitable for combining with mixPair or mixTail8.
     *
     * @param value the String to convert
     * @return a 64-bit representation of the String
     */
    public static long toLong(final String value) {
        return XXH3_64.DEFAULT_INSTANCE.hashCharsToLong(value);
    }
}