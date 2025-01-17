// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class UnsafeUtilsTest {

    // RandomAccessData.getInt()
    private static int getInt(final byte[] arr, final int offset) {
        final byte b1 = arr[offset];
        final byte b2 = arr[offset + 1];
        final byte b3 = arr[offset + 2];
        final byte b4 = arr[offset + 3];
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    // RandomAccessData.getLong()
    private static long getLong(final byte[] arr, final int offset) {
        final byte b1 = arr[offset];
        final byte b2 = arr[offset + 1];
        final byte b3 = arr[offset + 2];
        final byte b4 = arr[offset + 3];
        final byte b5 = arr[offset + 4];
        final byte b6 = arr[offset + 5];
        final byte b7 = arr[offset + 6];
        final byte b8 = arr[offset + 7];
        return (((long) b1 << 56)
                + ((long) (b2 & 255) << 48)
                + ((long) (b3 & 255) << 40)
                + ((long) (b4 & 255) << 32)
                + ((long) (b5 & 255) << 24)
                + ((b6 & 255) << 16)
                + ((b7 & 255) << 8)
                + (b8 & 255));
    }

    // Tests that UnsafeUtils.getInt() and RandomAccessData.getInt() produce the same results
    @Test
    void getIntTest() {
        final int SIZE = 1000;
        final byte[] src = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            src[i] = (byte) (i % 111);
        }
        for (int i = 0; i < SIZE + 1 - Integer.BYTES; i++) {
            assertEquals(getInt(src, i), UnsafeUtils.getInt(src, i));
        }
    }

    // Tests that UnsafeUtils.getLong() and RandomAccessData.getLong() produce the same results
    @Test
    void getLongTest() {
        final int SIZE = 1000;
        final byte[] src = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            src[i] = (byte) (i % 111);
        }
        for (int i = 0; i < SIZE + 1 - Long.BYTES; i++) {
            assertEquals(getLong(src, i), UnsafeUtils.getLong(src, i));
        }
    }
}
