// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * A simple long bit set implementation that uses an array of longs to represent bits.
 */
public final class LongBitSet {
    private static final int BITS_PER_LONG = 64;
    private static final int SHIFT = 6; // log2(64)
    private static final long MASK = 0x3FL; // 63

    private final long[] bits;
    private final long maxBits;

    private static final VarHandle BITS_HANDLE;

    static {
        try {
            BITS_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public LongBitSet(long size) {
        // Round up to next power of 2
        long numLongs = size / BITS_PER_LONG;
        this.bits = new long[(int) numLongs];
        this.maxBits = size;
    }

    public void clear() {
        Arrays.fill(bits, 0L);
    }

    public void setBit(long index) {
        if (index < 0 || index >= maxBits) {
            throw new IndexOutOfBoundsException("index: " + index);
        }

        int longIndex = (int) (index >>> SHIFT);
        long bitMask = 1L << (index & MASK);

        bits[longIndex] |= bitMask;
    }

    public void setBitThreadSafe(long index) {
        if (index < 0 || index >= maxBits) {
            throw new IndexOutOfBoundsException("index: " + index);
        }

        int longIndex = (int) (index >>> SHIFT);
        long bitMask = 1L << (index & MASK);

        long current;
        do {
            current = (long) BITS_HANDLE.getVolatile(bits, longIndex);
            if ((current & bitMask) != 0) {
                return; // Already set
            }
        } while (!BITS_HANDLE.compareAndSet(bits, longIndex, current, current | bitMask));
    }

    public long cardinality() {
        long count = 0;
        for (long value : bits) {
            count += Long.bitCount(value);
        }
        return count;
    }
}
