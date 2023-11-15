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

    private static final boolean NEED_CHANGE_BYTE_ORDER;

    private static final int BYTE_ARRAY_BASE_OFFSET;

    private static final long DIRECT_BYTEBUFFER_ADDRESS_OFFSET;

    static {
        try {
            final Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafeField.get(null);
            NEED_CHANGE_BYTE_ORDER = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
            final Field addressField = Buffer.class.getDeclaredField("address");
            DIRECT_BYTEBUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(addressField);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private UnsafeUtils() {
    }

    public static byte getByte(final byte[] arr, final int offset) {
        if (arr.length < offset + 1) {
            throw new BufferUnderflowException();
        }
        return UNSAFE.getByte(arr, BYTE_ARRAY_BASE_OFFSET + offset);
    }

    public static byte getByteHeap(final ByteBuffer buf, final int offset) {
        if (buf.limit() < offset + 1) {
            throw new BufferUnderflowException();
        }
        return UNSAFE.getByte(buf.array(), BYTE_ARRAY_BASE_OFFSET + offset);
    }

    public static byte getByteDirect(final ByteBuffer buf, final int offset) {
        if (buf.limit() < offset + 1) {
            throw new BufferUnderflowException();
        }
        final long address = UNSAFE.getLong(buf, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        return UNSAFE.getByte(null, address + offset);
    }

    /**
     * Reads an integer from the given array starting at the given offset. Array bytes are
     * interpreted in BIG_ENDIAN order.
     *
     * @param arr The byte array
     * @param offset The offset to read an integer at
     * @return The integer number
     * @throws java.nio.BufferOverflowException If array length is less than offset + integer bytes
     */
    public static int getInt(final byte[] arr, final int offset) {
        if (arr.length < offset + Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final int value = UNSAFE.getInt(arr, BYTE_ARRAY_BASE_OFFSET + offset);
        return NEED_CHANGE_BYTE_ORDER ? Integer.reverseBytes(value) : value;
    }

    /**
     * Reads a long from the given array starting at the given offset. Array bytes are
     * interpreted in BIG_ENDIAN order.
     *
     * @param arr The byte array
     * @param offset The offset to read a long at
     * @return The long number
     * @throws java.nio.BufferOverflowException If array length is less than offset + long bytes
     */
    public static long getLong(final byte[] arr, final int offset) {
        if (arr.length < offset + Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final long value = UNSAFE.getLong(arr, BYTE_ARRAY_BASE_OFFSET + offset);
        return NEED_CHANGE_BYTE_ORDER ? Long.reverseBytes(value) : value;
    }

    public static void getHeapBytes(final ByteBuffer buffer, final long offset, final byte[] dst, final int length) {
        UNSAFE.copyMemory(buffer.array(), BYTE_ARRAY_BASE_OFFSET + offset, dst, BYTE_ARRAY_BASE_OFFSET, length);
    }

    public static void getDirectBytes(final ByteBuffer buffer, final long offset, final byte[] dst, final int length) {
        final long address = UNSAFE.getLong(buffer, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        UNSAFE.copyMemory(null, address + offset, dst, BYTE_ARRAY_BASE_OFFSET, length);
    }

    public static void getDirectBytes(final ByteBuffer buffer, final long offset, final ByteBuffer dst, final int length) {
        final long srcAddress = UNSAFE.getLong(buffer, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        final long dstAddress = UNSAFE.getLong(dst, DIRECT_BYTEBUFFER_ADDRESS_OFFSET);
        UNSAFE.copyMemory(null, srcAddress + offset, null, dstAddress, length);
    }
}
