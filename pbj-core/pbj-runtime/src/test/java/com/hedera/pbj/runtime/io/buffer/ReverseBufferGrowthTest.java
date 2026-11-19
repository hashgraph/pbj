// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link ReverseBuffer} capacities are powers of two, and growing doubles them, keeping the output intact. */
final class ReverseBufferGrowthTest {

    @Test
    void initialCapacityIsRoundedUpToAPowerOfTwo() {
        assertEquals(16, new ReverseBuffer(0).array().length);
        assertEquals(16, new ReverseBuffer(16).array().length);
        assertEquals(32, new ReverseBuffer(17).array().length);
        assertEquals(128, new ReverseBuffer(100).array().length);
    }

    @Test
    void growingOneByteAtATimeDoubles() {
        final ReverseBuffer buffer = new ReverseBuffer(16);
        final byte[] expected = new byte[100];
        for (int i = 0; i < expected.length; i++) {
            buffer.writeByte(i);
            expected[expected.length - 1 - i] = (byte) i;
            final int length = buffer.array().length;
            assertEquals(Integer.highestOneBit(length), length, "capacity is a power of two");
            assertEquals(Math.max(16, Integer.highestOneBit(i) << 1), length, "capacity after " + (i + 1) + " bytes");
        }
        assertArrayEquals(expected, buffer.toBytes().toByteArray());
    }

    @Test
    void growingForALargeReservationFitsIt() {
        final ReverseBuffer buffer = new ReverseBuffer(16);
        buffer.writeByte(7);
        final int start = buffer.reserve(1000);
        assertEquals(1024, buffer.array().length);
        buffer.array()[start] = 9;
        assertEquals(1001, buffer.length());
        assertEquals(9, buffer.toBytes().getByte(0));
        assertEquals(7, buffer.toBytes().getByte(1000));
    }
}
