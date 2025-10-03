// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UnsafeUtilsTest {

    private static final int ARRAY_SIZE = 1000;
    private byte[] array;
    private RandomAccessData data;

    @BeforeEach
    void setup(){
        array = new byte[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (byte) (i % 111);
        }
        data = new RandomAccessArray(array);
    }

    /**
     * Tests that UnsafeUtils.getInt() and RandomAccessData.getInt() produce the same results
     */
    @Test
    void getIntTest() {
        for (int i = 0; i < ARRAY_SIZE + 1 - Integer.BYTES; i++) {
            assertEquals(data.getInt(i), UnsafeUtils.getInt(array, i, ByteOrder.BIG_ENDIAN),
                    "getInt() without a ByteOrder should default to BIG_ENDIAN");
            assertEquals(data.getInt(i, ByteOrder.BIG_ENDIAN), UnsafeUtils.getInt(array, i, ByteOrder.BIG_ENDIAN));
            assertEquals(data.getInt(i, ByteOrder.LITTLE_ENDIAN), UnsafeUtils.getInt(array, i, ByteOrder.LITTLE_ENDIAN));
            assertNotEquals(data.getInt(i, ByteOrder.BIG_ENDIAN), UnsafeUtils.getInt(array, i, ByteOrder.LITTLE_ENDIAN));
            assertNotEquals(data.getInt(i, ByteOrder.LITTLE_ENDIAN), UnsafeUtils.getInt(array, i, ByteOrder.BIG_ENDIAN));
        }
    }

    /**
     * Tests that UnsafeUtils.getLong() and RandomAccessData.getLong() produce the same results
     */
    @Test
    void getLongTest() {
        for (int i = 0; i < ARRAY_SIZE + 1 - Long.BYTES; i++) {
            assertEquals(data.getLong(i), UnsafeUtils.getLong(array, i, ByteOrder.BIG_ENDIAN),
                    "getLong() without a ByteOrder should default to BIG_ENDIAN");
            assertEquals(data.getLong(i, ByteOrder.BIG_ENDIAN), UnsafeUtils.getLong(array, i, ByteOrder.BIG_ENDIAN));
            assertEquals(data.getLong(i, ByteOrder.LITTLE_ENDIAN), UnsafeUtils.getLong(array, i, ByteOrder.LITTLE_ENDIAN));
            assertNotEquals(data.getLong(i, ByteOrder.LITTLE_ENDIAN), UnsafeUtils.getLong(array, i, ByteOrder.BIG_ENDIAN));
            assertNotEquals(data.getLong(i, ByteOrder.BIG_ENDIAN), UnsafeUtils.getLong(array, i, ByteOrder.LITTLE_ENDIAN));
        }
    }

}
