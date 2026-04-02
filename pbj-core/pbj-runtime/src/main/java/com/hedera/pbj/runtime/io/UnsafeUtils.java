// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A set of utility methods for faster access to memory of arrays and buffers.
 * This class used to rely on sun.misc.Unsafe, but has since been migrated to use modern Java 22+ APIs.
 */
public class UnsafeUtils {

    private static final VarHandle BYTE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle INT_LITTLE_ENDIAN_BYTE_ARRAY_VIEW_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, java.nio.ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_BIG_ENDIAN_BYTE_ARRAY_VIEW_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, java.nio.ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_LITTLE_ENDIAN_BYTE_ARRAY_VIEW_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, java.nio.ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG_BIG_ENDIAN_BYTE_ARRAY_VIEW_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, java.nio.ByteOrder.BIG_ENDIAN);

    private UnsafeUtils() {}

    /**
     * Get byte array element at a given offset. Identical to arr[offset], but faster,
     * because no array bounds checks are performed.
     *
     * <p><b>Use with caution!</b>
     */
    public static byte getArrayByteNoChecks(final byte[] arr, final int offset) {
        return (byte) BYTE_ARRAY_HANDLE.get(arr, offset);
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
        return (byte) BYTE_ARRAY_HANDLE.get(buf.array(), offset);
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
        return MemorySegment.ofBuffer(buf.duplicate().clear()).get(ValueLayout.JAVA_BYTE, offset);
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
        return byteOrder == ByteOrder.BIG_ENDIAN
                ? (int) INT_BIG_ENDIAN_BYTE_ARRAY_VIEW_HANDLE.get(arr, offset)
                : (int) INT_LITTLE_ENDIAN_BYTE_ARRAY_VIEW_HANDLE.get(arr, offset);
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
        return byteOrder == ByteOrder.BIG_ENDIAN
                ? (long) LONG_BIG_ENDIAN_BYTE_ARRAY_VIEW_HANDLE.get(arr, offset)
                : (long) LONG_LITTLE_ENDIAN_BYTE_ARRAY_VIEW_HANDLE.get(arr, offset);
    }

    /**
     * Copies heap byte buffer bytes to a given byte array. May only be called for heap
     * byte buffers
     */
    public static void getHeapBufferToArray(
            final ByteBuffer buffer, final long offset, final byte[] dst, final int dstOffset, final int length) {
        MemorySegment.copy(
                MemorySegment.ofBuffer(buffer.duplicate().clear()),
                offset,
                MemorySegment.ofArray(dst),
                dstOffset,
                length);
    }

    /**
     * Copies direct byte buffer bytes to a given byte array. May only be called for direct
     * byte buffers
     */
    public static void getDirectBufferToArray(
            final ByteBuffer buffer, final long offset, final byte[] dst, final int dstOffset, final int length) {
        MemorySegment.copy(
                MemorySegment.ofBuffer(buffer.duplicate().clear()),
                offset,
                MemorySegment.ofArray(dst),
                dstOffset,
                length);
    }

    /**
     * Copies direct byte buffer bytes to another direct byte buffer. May only be called for
     * direct byte buffers
     */
    public static void getDirectBufferToDirectBuffer(
            final ByteBuffer buffer, final long offset, final ByteBuffer dst, final int dstOffset, final int length) {
        MemorySegment.copy(
                MemorySegment.ofBuffer(buffer.duplicate().clear()),
                offset,
                MemorySegment.ofBuffer(dst.duplicate().clear()),
                dstOffset,
                length);
    }

    /**
     * Copies a byte array to a direct byte buffer. May only be called for direct byte buffers
     */
    public static void putByteArrayToDirectBuffer(
            final ByteBuffer buffer, final long offset, final byte[] src, final int srcOffset, final int length) {
        MemorySegment.copy(
                MemorySegment.ofArray(src),
                srcOffset,
                MemorySegment.ofBuffer(buffer.duplicate().clear()),
                offset,
                length);
    }
}
