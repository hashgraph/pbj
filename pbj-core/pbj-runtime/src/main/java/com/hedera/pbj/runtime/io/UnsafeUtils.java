package com.hedera.pbj.runtime.io;

import java.lang.reflect.Field;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import sun.misc.Unsafe;

/**
 * A set of utility methods on top of sun.misc.Unsafe
 */
public class UnsafeUtils {

    private static final Unsafe UNSAFE;

    private static final boolean NEED_CHANGE_BYTE_ORDER;

    private static final int BYTE_ARRAY_BASE_OFFSET;

    static {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            NEED_CHANGE_BYTE_ORDER = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private UnsafeUtils() {
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
}
