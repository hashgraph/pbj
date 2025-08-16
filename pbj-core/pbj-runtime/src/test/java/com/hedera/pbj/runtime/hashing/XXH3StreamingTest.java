package com.hedera.pbj.runtime.hashing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.hashing.XXH3_64.HashingWritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class XXH3StreamingTest {
    /** VarHandle for reading and writing longs in little-endian byte order. */
    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    /** VarHandle for reading and writing integers in little-endian byte order. */
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    @Test
    @DisplayName("Test for 32-bit integer an float handling in XXH3 streaming")
    public void testLimitAndCapacity() {
        final var hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
        assertEquals(Long.MAX_VALUE, hashingStream.limit());
        assertEquals(Long.MAX_VALUE, hashingStream.capacity());
        hashingStream.writeInt(123456789);
        assertEquals(Integer.BYTES, hashingStream.position());
        assertEquals(Long.MAX_VALUE, hashingStream.limit());
        assertEquals(Long.MAX_VALUE, hashingStream.capacity());
        hashingStream.reset();
        assertEquals(0, hashingStream.position());
    }

    /**
     * Test for 32-bit integer and float handling in XXH3 streaming.
     */
    @Test
    @DisplayName("Test for 32-bit integer an float handling in XXH3 streaming")
    public void test32Bit() {
        final int value = 123456789;
        final byte[] bytes = new byte[4];
        INT_HANDLE.set(bytes, 0, value);
        final long simpleHash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(bytes, 0, 4);
        final var hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
        hashingStream.writeInt(value);
        assertEquals(simpleHash, hashingStream.computeHash());
        assertEquals(Integer.BYTES, hashingStream.position());
        hashingStream.reset();
        hashingStream.writeInt(value, ByteOrder.LITTLE_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeInt(value, ByteOrder.BIG_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeUnsignedInt(value);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeUnsignedInt(value, ByteOrder.LITTLE_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeUnsignedInt(value, ByteOrder.BIG_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeVarInt(value, true);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeVarInt(value, false);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // Convert int to float and write it
        final float floatValue = Float.intBitsToFloat(value);
        hashingStream.writeFloat(floatValue);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeFloat(floatValue, ByteOrder.LITTLE_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeFloat(floatValue, ByteOrder.BIG_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
    }

    @Test
    @DisplayName("Test for 64-bit long handling in XXH3 streaming")
    public void test64Bit() {
        final long value = 1234567890123456789L;
        final byte[] bytes = new byte[8];
        LONG_HANDLE.set(bytes, 0, value);
        final long simpleHash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(bytes, 0, 8);
        final var hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
        hashingStream.writeLong(value);
        assertEquals(simpleHash, hashingStream.computeHash());
        assertEquals(Long.BYTES, hashingStream.position());
        hashingStream.reset();
        hashingStream.writeLong(value, ByteOrder.LITTLE_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeLong(value, ByteOrder.BIG_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeVarLong(value, true);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeVarLong(value, false);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // Convert long to double and write it
        final double doubleValue = Double.longBitsToDouble(value);
        hashingStream.writeDouble(doubleValue);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeDouble(doubleValue, ByteOrder.LITTLE_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeDouble(doubleValue, ByteOrder.BIG_ENDIAN);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
    }

    @Test
    @DisplayName("Test for byte methods in XXH3 streaming")
    public void testByteMethods() {
        final byte[] bytes = new byte[128];
        new Random(91824819480L).nextBytes(bytes);
        final long simpleHash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(bytes, 0, bytes.length);
        final HashingWritableSequentialData hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
        // byte arrays
        hashingStream.writeBytes(bytes, 0, bytes.length);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        hashingStream.writeBytes(bytes);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        for (byte aByte : bytes) {
            hashingStream.writeByte(aByte);
        }
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // BufferedData
        BufferedData bufferedData = BufferedData.wrap(bytes);
        hashingStream.writeBytes(bufferedData);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // ByteBuffer
        java.nio.ByteBuffer byteBuffer = java.nio.ByteBuffer.wrap(bytes);
        hashingStream.writeBytes(byteBuffer);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // RandomAccessData
        RandomAccessData randomAccessData = Bytes.wrap(bytes);
        hashingStream.writeBytes(randomAccessData);
        assertEquals(simpleHash, hashingStream.computeHash());
        hashingStream.reset();
        // === subsets ===
        int offset = 10;
        int length = 50;
        final long simpleSubsetHash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(bytes, offset, length);
        // byte arrays
        hashingStream.writeBytes(bytes, offset, length);
        assertEquals(simpleSubsetHash, hashingStream.computeHash());
        hashingStream.reset();
        for (int i = offset; i < offset + length; i++) {
            hashingStream.writeByte(bytes[i]);
        }
        assertEquals(simpleSubsetHash, hashingStream.computeHash());
        hashingStream.reset();
        // BufferedData
        BufferedData bufferedSubsetData = BufferedData.wrap(bytes, offset, length);
        hashingStream.writeBytes(bufferedSubsetData);
        assertEquals(simpleSubsetHash, hashingStream.computeHash());
        hashingStream.reset();
        // ByteBuffer
        java.nio.ByteBuffer byteSubsetBuffer = java.nio.ByteBuffer.wrap(bytes, offset, length);
        hashingStream.writeBytes(byteSubsetBuffer);
        assertEquals(simpleSubsetHash, hashingStream.computeHash());
        hashingStream.reset();
        // RandomAccessData
        RandomAccessData randomSubsetAccessData = Bytes.wrap(bytes, offset, length);
        hashingStream.writeBytes(randomSubsetAccessData);
        assertEquals(simpleSubsetHash, hashingStream.computeHash());
        hashingStream.reset();
    }
}
