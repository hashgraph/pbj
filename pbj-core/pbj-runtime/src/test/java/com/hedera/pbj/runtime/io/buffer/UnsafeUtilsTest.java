package com.hedera.pbj.runtime.io.buffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

final class UnsafeUtilsTest {
    @Test
    @DisplayName("Supports Unsafe Operations")
    void supportsUnsafeOperations() {
        assertTrue(UnsafeUtils.hasUnsafeByteBufferOperations());
    }

    @Test
    @DisplayName("WriteReadByte")
    void writeReadByte() {
        // This test instantiates the UnsafeUtils backed class and does a write and read on it.
        assertTrue(UnsafeUtils.hasUnsafeByteBufferOperations());
        BufferedData byteBuffer = BufferedData.allocateOffHeap(1);
        byteBuffer.writeByte((byte)1);
        byteBuffer.resetPosition();
        byte b = byteBuffer.readByte();
        assertEquals((byte)1, b);
    }
}
