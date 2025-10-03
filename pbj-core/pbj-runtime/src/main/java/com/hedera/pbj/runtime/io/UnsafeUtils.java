// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import sun.misc.Unsafe;

/**
 * A set of utility methods on top of sun.misc.Unsafe
 */
public class UnsafeUtils {

    private static final Unsafe UNSAFE;

    /**
     * Field offset of the byte[] class
     */
    private static final int BYTE_ARRAY_BASE_OFFSET;

    /**
     * Direct byte buffer "address" field offset. This is not the address of the buffer,
     * but the offset of the field, which contains the address of the buffer
     */
    private static final long DIRECT_BYTEBUFFER_ADDRESS_OFFSET;

    static {
        try {
            final Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            final Field addressField = Buffer.class.getDeclaredField("address");
            DIRECT_BYTEBUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(addressField);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private UnsafeUtils() {}

    /**
     * Get byte array element at a given offset. Identical to arr[offset].
     */
    public static byte getArrayByte(final byte[] arr, final int offset) {
        if (arr.length <= offset) {
            throw new IndexOutOfBoundsException();
        }
        return getArrayByteNoChecks(arr, offset);
    }

    /**
     * Get byte array element at a given offset. Identical to arr[offset], but faster,
     * because no array bounds checks are performed.
     *
     * <p><b>Use with caution!</b>
     */
    public static byte getArrayByteNoChecks(final byte[] arr, final int offset) {
        return UNSAFE.getByte(arr, BYTE_ARRAY_BASE_OFFSET + offset);
    }

    /**
     * Get heap byte buffer element at a given offset. Identical to buf.get(offset). May only
     * be called for Java heap byte buffers.
     */
    public static byte getHeapBufferByte(final ByteBuffer buf, final int offset) {
        if (buf.limit() < offset + 1) {
            throw new BufferUnderflowException();
        }
        return getHeapBufferByteNoChecks(buf, offset);
    }

    /**
     * Get heap byte buffer element at a given offset. Identical to buf.get(offset), but faster,
     * because no buffer range checks are performed. May only be called for Java heap byte buffers.
     *
     * <p><b>Use with caution!</b>
     */
    public static byte getHeapBufferByteNoChecks(final ByteBuffer buf, final int offset) {
        return UNSAFE.getByte(buf.array(), BYTE_ARRAY_BASE_OFFSET + offset);
    }

    /**
     * Get direct byte buffer element at a given offset. Identical to buf.get(offset). May only
     * be called for direct byte buffers
     */
    public static byte getDirectBufferByte(final ByteBuffer buf, final int offset) {
        if (buf.limit() < offset + 1) {
            throw new BufferUnderflowException();
        }
        return getDirectBufferByteNoChecks(buf, offset);
    }

    /**
     * Get direct byte buffer element at a given offset. Identical to buf.get(offset), but faster,
     * because no buffer range checks are performed. May only be called for direct byte buffers.
     */
    public static byte getDirectBufferByteNoChecks(final ByteBuffer buf, final int offset) {
        final long address = UNSAFE.getLong(buf, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        return UNSAFE.getByte(null, address + offset);
    }

    /**
     * Reads an integer from the given array starting at the given offset. Array bytes are
     * interpreted in based on the supplied byte order.
     *
     * @param arr       The byte array
     * @param offset    The offset to read an integer at
     * @param byteOrder The byte order to use when interpreting the bytes
     * @return The integer number
     * @throws java.nio.BufferOverflowException If array length is less than offset + integer bytes
     */
    public static int getInt(final byte[] arr, final int offset, final ByteOrder byteOrder) {
        if (arr.length < offset + Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final int value = UNSAFE.getInt(arr, BYTE_ARRAY_BASE_OFFSET + offset);
        return byteOrder != ByteOrder.nativeOrder() ? Integer.reverseBytes(value) : value;
    }

    /**
     * Reads a long from the given array starting at the given offset. Array bytes are
     * interpreted in based on the supplied byte order.
     *
     * @param arr       The byte array
     * @param offset    The offset to read a long at
     * @param byteOrder The byte order to use when interpreting the bytes
     * @return The long number
     * @throws java.nio.BufferOverflowException If array length is less than offset + long bytes
     */
    public static long getLong(final byte[] arr, final int offset, final ByteOrder byteOrder) {
        if (arr.length < offset + Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final long value = UNSAFE.getLong(arr, BYTE_ARRAY_BASE_OFFSET + offset);
        return byteOrder != ByteOrder.nativeOrder() ? Long.reverseBytes(value) : value;
    }

    /**
     * Copies heap byte buffer bytes to a given byte array. May only be called for heap
     * byte buffers
     */
    public static void getHeapBufferToArray(
            final ByteBuffer buffer, final long offset, final byte[] dst, final int dstOffset, final int length) {
        UNSAFE.copyMemory(
                buffer.array(), BYTE_ARRAY_BASE_OFFSET + offset, dst, BYTE_ARRAY_BASE_OFFSET + dstOffset, length);
    }

    /**
     * Copies direct byte buffer bytes to a given byte array. May only be called for direct
     * byte buffers
     */
    public static void getDirectBufferToArray(
            final ByteBuffer buffer, final long offset, final byte[] dst, final int dstOffset, final int length) {
        final long address = UNSAFE.getLong(buffer, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        UNSAFE.copyMemory(null, address + offset, dst, BYTE_ARRAY_BASE_OFFSET + dstOffset, length);
    }

    /**
     * Copies direct byte buffer bytes to another direct byte buffer. May only be called for
     * direct byte buffers
     */
    public static void getDirectBufferToDirectBuffer(
            final ByteBuffer buffer, final long offset, final ByteBuffer dst, final int dstOffset, final int length) {
        final long address = UNSAFE.getLong(buffer, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        final long dstAddress = UNSAFE.getLong(dst, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        UNSAFE.copyMemory(null, address + offset, null, dstAddress, length);
    }

    /**
     * Copies a byte array to a direct byte buffer. May only be called for direct byte buffers
     */
    public static void putByteArrayToDirectBuffer(
            final ByteBuffer buffer, final long offset, final byte[] src, final int srcOffset, final int length) {
        final long address = UNSAFE.getLong(buffer, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET + srcOffset, null, address + offset, length);
    }
}
