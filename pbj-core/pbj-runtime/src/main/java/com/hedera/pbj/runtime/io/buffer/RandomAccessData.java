package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.SequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Represents data which may be accessed out of order in some random manner. Unliked {@link SequentialData},
 * this interface is only backed by a buffer of some kind: an array, a {@link ByteBuffer}, a memory-mapped file, etc.
 * Unlike {@link BufferedSequentialData}, it does not define any kind of "position" cursor, just a "length" representing
 * the valid range of indexes and methods for reading data at any of those indexes.
 */
public interface RandomAccessData {

    // ================================================================================================================
    // Static Methods

    /**
     * Create a new {@link RandomAccessData} over the contents of the given byte array. This does not copy data it just
     * wraps so any changes to arrays contents will be visible in the returned result.
     *
     * @param byteArray The byte array to wrap
     * @return new {@link RandomAccessData} with same contents as byte array
     */
    @NonNull
    static RandomAccessData wrap(@NonNull final byte[] byteArray) {
        return new Bytes(byteArray);
    }

    /**
     * Create a new {@link RandomAccessData} with the contents of a UTF8 encoded String.
     *
     * @param string The UFT8 encoded string to wrap
     * @return new {@link RandomAccessData} with string contents UTF8 encoded
     */
    @NonNull
    static RandomAccessData wrap(@NonNull final String string) {
        return wrap(string.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================================================================
    // Bytes Methods

    /**
     * Get the number of bytes of data stored
     *
     * @return number of bytes of data stored
     */
    long length();

    /**
     * Gets the byte at given {@code offset}. The offset must be non-negative and smaller than the limit.
     *
     * @param offset The offset into data to get byte at. Must be non-negative and smaller than the limit.
     * @return The byte at given {@code offset}
     * @throws IndexOutOfBoundsException If the given {@code offset} is not smaller than its limit, or is negative
     */
    byte getByte(final long offset);

    /**
     * Gets the byte at given {@code offset} as unsigned. The offset must be non-negative and smaller than the limit.
     *
     * @param offset The offset into data to get byte at. Must be non-negative and smaller than the limit.
     * @return The byte at given {@code offset}
     * @throws IndexOutOfBoundsException If the given {@code offset} is not smaller than its limit, or is negative
     */
    default int getUnsignedByte(final long offset) {
        return Byte.toUnsignedInt(getByte(offset));
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative
     *                and no larger than {@code dst.length}
     * @param length The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                no larger than {@code dst.length - offset}
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining to be read or any of
     *                                  the preconditions on the {@code offset} and {@code length} parameters do
     *                                  not hold
     */
    default void getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int length) {
        if ((offset + length) > length()) {
            throw new BufferUnderflowException();
        }
        if (dstOffset < 0 || (dstOffset + length) >= dst.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = getByte(offset + i);
        }
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination array
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining in this buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default void getBytes(final long offset, @NonNull final byte[] dst) {
        getBytes(offset, dst, 0, dst.length);
    }

    /**
     * Get bytes starting at given {@code offset} into dst {@link ByteBuffer} up to remaining bytes in
     * {@link ByteBuffer}.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination {@link ByteBuffer}
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default void getBytes(final long offset, @NonNull final ByteBuffer dst) {
        if ((offset + dst.remaining()) > length()) {
            throw new BufferUnderflowException();
        }
        long index = offset;
        while(dst.hasRemaining()) {
            dst.put(getByte(index++));
        }
    }

    /**
     * Get bytes starting at given {@code offset} into dst {@link BufferedData} up to remaining bytes in
     * {@link BufferedData}.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination {@link BufferedData}
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default void getBytes(final long offset, @NonNull final BufferedData dst) {
        if ((offset + dst.remaining()) > length()) {
            throw new BufferUnderflowException();
        }
        long index = offset;
        while(dst.hasRemaining()) {
            dst.writeByte(getByte(index++));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an int value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default int getInt(final long offset) {
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset);
        final byte b2 = getByte(offset + 1);
        final byte b3 = getByte(offset + 2);
        final byte b4 = getByte(offset + 3);
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an int value according to specified byte
     * order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset);
            final byte b3 = getByte(offset + 1);
            final byte b2 = getByte(offset + 2);
            final byte b1 = getByte(offset + 3);
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an unsigned int value according to the
     * Java standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default long getUnsignedInt(final long offset) {
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset);
        final byte b2 = getByte(offset + 1);
        final byte b3 = getByte(offset + 2);
        final byte b4 = getByte(offset + 3);
        return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an unsigned int value according to
     * specified byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default long getUnsignedInt(final long offset, @NonNull final ByteOrder byteOrder) {
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset);
            final byte b3 = getByte(offset + 1);
            final byte b2 = getByte(offset + 2);
            final byte b1 = getByte(offset + 3);
            return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
        }
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a long value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @param offset The offset into data to get long at
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative
     */
    default long getLong(final long offset) {
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset);
        final byte b2 = getByte(offset + 1);
        final byte b3 = getByte(offset + 2);
        final byte b4 = getByte(offset + 3);
        final byte b5 = getByte(offset + 4);
        final byte b6 = getByte(offset + 5);
        final byte b7 = getByte(offset + 6);
        final byte b8 = getByte(offset + 7);
        return (((long)b1 << 56) +
                ((long)(b2 & 255) << 48) +
                ((long)(b3 & 255) << 40) +
                ((long)(b4 & 255) << 32) +
                ((long)(b5 & 255) << 24) +
                ((b6 & 255) << 16) +
                ((b7 & 255) <<  8) +
                ((b8 & 255)));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a long value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param offset The offset into data to get long at
     * @param byteOrder the byte order, aka endian to use
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    default long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getLong(offset);
        } else {
            final byte b8 = getByte(offset);
            final byte b7 = getByte(offset + 1);
            final byte b6 = getByte(offset + 2);
            final byte b5 = getByte(offset + 3);
            final byte b4 = getByte(offset + 4);
            final byte b3 = getByte(offset + 5);
            final byte b2 = getByte(offset + 6);
            final byte b1 = getByte(offset + 7);
            return (((long) b1 << 56) +
                    ((long) (b2 & 255) << 48) +
                    ((long) (b3 & 255) << 40) +
                    ((long) (b4 & 255) << 32) +
                    ((long) (b5 & 255) << 24) +
                    ((b6 & 255) << 16) +
                    ((b7 & 255) << 8) +
                    ((b8 & 255)));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into a float value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get float at
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    default float getFloat(final long offset) {
        return Float.intBitsToFloat(getInt(offset));
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into a float value according to specified byte
     * order, and then increments the position by four.
     *
     * @param offset The offset into data to get float at
     * @param byteOrder the byte order, aka endian to use
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     */
    default float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
        return Float.intBitsToFloat(getInt(offset, byteOrder));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a double value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @param offset The offset into data to get double at
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    default double getDouble(final long offset) {
        return Double.longBitsToDouble(getLong(offset));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a double value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param offset The offset into data to get dpuble at
     * @param byteOrder the byte order, aka endian to use
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     */
    default double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
        return Double.longBitsToDouble(getLong(offset, byteOrder));
    }

    /**
     * Get a 32bit protobuf varint at given {@code offset}. An integer var int can be 1 to 5 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return integer get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     */
    default int getVarInt(final long offset, final boolean zigZag) {
        return (int)getVarLong(offset, zigZag);
    }

    /**
     * Get a 64bit protobuf varint at given {@code offset}. A long var int can be 1 to 10 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return long get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     */
    default long getVarLong(final long offset, final boolean zigZag) {
        long result = 0;
        long index = offset;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = getByte(index++);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new RuntimeException("Malformed Varint");
    }

    /**
     * Get the contents of this entire buffer as a string, assuming bytes contained are UTF8 encoded string.
     *
     * @return data converted to string
     */
    @NonNull
    default String asUtf8String() {
        return asUtf8String(0, length());
    }

    /**
     * Get the contents of a subset of this buffer as a UTF-8 encoded string.
     *
     * @param offset the offset into the buffer to start reading bytes from
     * @param len the number of bytes to read
     * @return data converted to string
     */
    @NonNull
    default String asUtf8String(final long offset, final long len) {
        final var data = new byte[Math.toIntExact(len)];
        getBytes(offset, data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Check if the beginning of this buffer matches the given prefix bytes.
     *
     * @param prefix the prefix bytes to compare with
     * @return true if prefix bytes match the beginning of our bytes
     */
    default boolean matchesPrefix(@NonNull final byte[] prefix) {
        return contains(0, prefix);
    }

    /**
     * Check if the bytes of this buffer beginning at the given {@code offset} contain the given bytes.
     *
     * @param offset the offset into this buffer to start comparing bytes at
     * @param bytes the bytes to compare with
     * @return true if bytes match the beginning of our bytes
     */
    default boolean contains(final long offset, @NonNull final byte[] bytes) {
        if (length() - offset < bytes.length) {
            return false;
        }

        for (long i = offset; i < bytes.length; i++) {
            if (bytes[Math.toIntExact(i)] != getByte(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if the beginning of our bytes data matches the given prefix bytes.
     *
     * @param prefix the prefix bytes to compare with
     * @return true if prefix bytes match the beginning of our bytes
     */
    default boolean matchesPrefix(@NonNull final RandomAccessData prefix) {
        return contains(0, prefix);
    }

    /**
     * Check if the bytes of this buffer beginning at the given {@code offset} contain the given data.
     *
     * @param offset the offset into this buffer to start comparing bytes at
     * @param data the bytes to compare with
     * @return true if prefix bytes match the beginning of our bytes
     */
    default boolean contains(final long offset, @NonNull final RandomAccessData data) {
        if (length() - offset < data.length()) {
            return false;
        }

        for (long i = offset; i < data.length(); i++) {
            if (data.getByte(i) != getByte(i)) {
                return false;
            }
        }

        return true;
    }
}
