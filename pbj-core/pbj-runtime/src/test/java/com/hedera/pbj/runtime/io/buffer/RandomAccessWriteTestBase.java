// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
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

    @Test
    void bufferedDataWriteTest() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        final BufferedData data = BufferedData.wrap(new byte[] {15, 16, 17});
        assertEquals(3, data.writeTo(out, 5));
        for (int i = 0; i < 3; i++) {
            assertEquals(15 + i, out.getByte(5 + i));
        }
    }

    @Test
    void bufferedDataWriteOverflowsTest() {
        final RandomAccessData out = randomAccessData(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
        final BufferedData data = BufferedData.wrap(new byte[] {15, 16, 17});
        assertThrows(BufferOverflowException.class, () -> data.writeTo(out, 8));
    }
}
