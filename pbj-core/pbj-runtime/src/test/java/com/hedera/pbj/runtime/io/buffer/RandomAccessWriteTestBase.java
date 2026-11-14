// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public abstract class RandomAccessWriteTestBase {

    @NonNull
    protected abstract RandomAccessData randomAccessData(@NonNull final byte[] bytes);

    @Test
    void testPutBytesBytes() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertEquals(3, out.putBytes(1, new byte[] {11, 12, 13}));
        for (int i = 0; i < 3; i++) {
            assertEquals(11 + i, out.getByte(1 + i));
        }
    }

    @Test
    void testPutBytesByteBuffer() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertEquals(4, out.putBytes(2, ByteBuffer.wrap(new byte[] {22, 23, 24, 25})));
        for (int i = 0; i < 4; i++) {
            assertEquals(22 + i, out.getByte(2 + i));
        }
    }

    @Test
    void testPutBytesByteBufferWithPosition() {
        final byte[] arr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        final RandomAccessData out = randomAccessData(arr);
        final ByteBuffer data = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        // When writing a byte buffer, its position is respected
        data.position(5);
        out.putBytes(1, data);
        assertArrayEquals(new byte[] {0, 5, 6, 7, 8, 9, 0, 0, 0, 0}, arr);
    }

    @Test
    void testPutBytesBufferedDataWithPosition() {
        final byte[] arr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        final RandomAccessData out = randomAccessData(arr);
        final BufferedData data = BufferedData.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        // When writing arbitrary RandomAccessData, it's written in full, current
        // BufferedData's position is ignored
        data.position(5);
        out.putBytes(3, data);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 1, 2, 3, 4, 5, 0}, arr);
    }

    @Test
    void testPutBytesBufferedDataWithPositionOverflows() {
        final byte[] arr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};
        final RandomAccessData out = randomAccessData(arr);
        final BufferedData data = BufferedData.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        assertThrows(BufferOverflowException.class, () -> out.putBytes(3, data));
        assertDoesNotThrow(() -> out.putBytes(1, data));
    }

    @Test
    void testPutBytesMemoryDataWithPosition() {
        final byte[] arr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        final RandomAccessData out = randomAccessData(arr);
        final BufferedData data = BufferedData.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        // When writing arbitrary RandomAccessData, it's written in full, current
        // BufferedData's position is ignored
        data.position(2);
        out.putBytes(3, data);
        assertArrayEquals(new byte[] {0, 0, 0, 0, 1, 2, 3, 4, 5, 0}, arr);
    }

    @Test
    void testPutBytesMemoryDataWithPositionOverflows() {
        final byte[] arr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0};
        final RandomAccessData out = randomAccessData(arr);
        final MemoryData data = MemoryData.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        assertThrows(BufferOverflowException.class, () -> out.putBytes(3, data));
        assertDoesNotThrow(() -> out.putBytes(1, data));
    }

    @Test
    void testPutBytesOverflows() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertThrows(BufferOverflowException.class, () -> out.putBytes(8, new byte[] {1, 1, 1, 1}));
        assertThrows(BufferOverflowException.class, () -> out.putBytes(8, ByteBuffer.wrap(new byte[] {1, 1, 1, 1})));
    }

    @Test
    void testPutBytesOverflowsWithSlice() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        assertEquals(4, out.putBytes(4, new byte[] {1, 1, 1, 1}));
        assertEquals(4, out.slice(6, 4).putBytes(0, new byte[] {1, 1, 1, 1}));
        assertThrows(BufferOverflowException.class, () -> out.slice(7, 3).putBytes(0, new byte[] {1, 1, 1, 1}));
    }

    @Test
    void bytesWriteTest() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        final Bytes data = Bytes.wrap(new byte[] {15, 16, 17});
        assertEquals(3, data.writeTo(out, 5));
        for (int i = 0; i < 3; i++) {
            assertEquals(15 + i, out.getByte(5 + i));
        }
    }

    @Test
    void bytesWriteOverflowsTest() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        final Bytes data = Bytes.wrap(new byte[] {15, 16, 17});
        assertThrows(BufferOverflowException.class, () -> data.writeTo(out, 8));
    }

    abstract class BufferedSequentialDataWriteTest {

        private BufferedSequentialData data;

        protected abstract BufferedSequentialData bufferedSequentialData(final byte[] arr);

        @BeforeEach
        void setupData() {
            data = bufferedSequentialData(new byte[] {15, 16, 17});
        }

        @Test
        void writeTest() {
            final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            assertEquals(3, data.writeTo(out, 5));
            for (int i = 0; i < 3; i++) {
                assertEquals(15 + i, out.getByte(5 + i));
            }
        }

        @Test
        void writeWithPosTest() {
            final byte[] arr = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            final RandomAccessData out = randomAccessData(arr);
            data.position(1);
            assertEquals(2, data.writeTo(out, 5));
            assertArrayEquals(new byte[] {0, 1, 2, 3, 4, 16, 17, 7, 8, 9}, arr);
        }

        @Test
        void writeWithLimitTest() {
            final byte[] arr = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
            final RandomAccessData out = randomAccessData(arr);
            data.limit(1);
            assertEquals(1, data.writeTo(out, 4));
            assertArrayEquals(new byte[] {0, 1, 2, 3, 15, 5, 6, 7, 8, 9}, arr);
        }

        @Test
        void writeOverflowsTest() {
            final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            assertThrows(BufferOverflowException.class, () -> data.writeTo(out, 8));
        }

        @Test
        void writeWithPosOverflowsTest() {
            final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            data.position(1);
            assertDoesNotThrow(() -> data.writeTo(out, 8));
        }

        @Test
        void writeWithLimitOverflowsTest() {
            final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
            data.limit(2);
            assertDoesNotThrow(() -> data.writeTo(out, 8));
        }
    }

    @Nested
    class BufferedDataWriteTest extends BufferedSequentialDataWriteTest {

        @Override
        protected BufferedSequentialData bufferedSequentialData(byte[] arr) {
            return BufferedData.wrap(arr);
        }
    }

    @Nested
    class MemoryDataWriteTest extends BufferedSequentialDataWriteTest {

        @Override
        protected BufferedSequentialData bufferedSequentialData(byte[] arr) {
            return MemoryData.wrap(arr);
        }
    }
}
