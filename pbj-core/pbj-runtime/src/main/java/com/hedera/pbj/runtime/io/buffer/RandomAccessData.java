package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.SequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.BufferOverflowException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Represents data which may be accessed out of order in some random manner. Unlike {@link SequentialData},
 * this interface is only backed by a buffer of some kind: an array, a {@link ByteBuffer}, a memory-mapped file, etc.
 * Unlike {@link BufferedSequentialData}, it does not define any kind of "position" cursor, just a "length" representing
 * the valid range of indexes and methods for reading data at any of those indexes.
 */
@SuppressWarnings("unused")
public interface RandomAccessData {

    /**
     * Get the number of bytes of data stored
     *
     * @return number of bytes of data stored
     */
    long length();

    /**
     * Gets the signed byte at the given {@code offset}.
     *
     * @param offset The offset into data to get a byte from.
     * @return The signed byte at given {@code offset}
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    byte getByte(final long offset);

    /**
     * Gets the unsigned byte at given {@code offset}.
     *
     * @param offset The offset into data to get an unsigned byte from.
     * @return The unsigned byte at given {@code offset}
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default int getUnsignedByte(final long offset) {
        return Byte.toUnsignedInt(getByte(offset));
    }

    /**
     * Get bytes starting at the given {@code offset} and write them into the {@code dst} array, up to the size of
     * the {@code dst} array. If {@code dst} is larger than the number of bytes between {@code offset} and
     * {@link #length()}, only the maximum available bytes are read. The total number of bytes actually read are
     * returned. The bytes will be placed starting at index 0 of the {@code dst} array. If the number of bytes
     * between {@code offset} and {@link #length()} is 0, then 0 is returned.
     *
     * @param offset The offset into data to begin reading bytes
     * @param dst The array into which bytes are to be written
     * @throws NullPointerException if {@code dst} is null
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long getBytes(final long offset, @NonNull final byte[] dst) {
        return getBytes(offset, dst, 0, dst.length);
    }

    /**
     * Get bytes starting at the given {@code offset} into the {@code dst} array, up to {@code maxLength} number of
     * bytes. If {@code maxLength} is larger than the number of bytes between {@code offset} and {@link #length()},
     * only the maximum available bytes are read. The total number of bytes actually read are returned. The bytes will
     * be placed starting at index {@code offset} of the {@code dst} array. If the number of bytes between
     * {@code offset} and {@link #length()} is 0, then 0 is returned.
     *
     * @param offset The offset into data to begin reading bytes
     * @param dst The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative
     *                and no larger than {@code dst.length - maxLength}.
     * @param maxLength The maximum number of bytes to be written to the given {@code dst} array; must be non-negative
     *                and no larger than {@code dst.length - offset}
     * @throws NullPointerException if {@code dst} is null
     * @throws IndexOutOfBoundsException If {@code dstOffset} is out of bounds of {@code dst},
     *             or if {@code dstOffset + maxLength} is not less than {@code dst.length},
     *             or if {@code offset} is out of bounds of this RandomAccessData.
     * @throws IllegalArgumentException If {@code maxLength} is negative
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        final var len = Math.min(maxLength, length() - offset);
        for (int i = 0; i < len; i++) {
            dst[dstOffset + i] = getByte(offset + i);
        }
        return len;
    }

    /**
     * Get bytes starting at the given {@code offset} into the destination {@link ByteBuffer}, up to
     * {@link ByteBuffer#remaining()} number of bytes. If {@link ByteBuffer#remaining()} is larger than the number
     * of bytes between {@code offset} and {@link #length()}, only the maximum available bytes are read. The total
     * number of bytes actually read are returned. The bytes will be placed starting at index
     * {@link ByteBuffer#position()} of the destination buffer. If the number of bytes between {@code offset} and
     * {@link #length()} is 0, then 0 is returned.
     *
     * @param offset The offset into data to begin reading bytes
     * @param dst The destination {@link ByteBuffer}
     * @throws NullPointerException if {@code dst} is null
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        final long length = length();
        checkOffset(offset, length);

        long index = offset;
        while (dst.hasRemaining() && index < length) {
            dst.put(getByte(index++));
        }
        return index - offset;
    }

    /**
     * Get bytes starting at given {@code offset} into the destination {@link BufferedData}, up to
     * {@link BufferedData#remaining()} number of bytes. If {@link BufferedData#remaining()} is larger than the
     * number of bytes between {@code offset} and {@link #length()}, only the remaining bytes are read. The total
     * number of bytes actually read are returned. The bytes will be placed starting at index
     * {@link BufferedData#position()} of the buffer. If the number of bytes between {@code offset} and
     * {@link #length()} is 0, then 0 is returned.
     *
     * @param offset The offset into data to begin reading bytes
     * @param dst The destination {@link BufferedData}
     * @throws NullPointerException if {@code dst} is null
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long getBytes(final long offset, @NonNull final BufferedData dst) {
        final long length = length();
        checkOffset(offset, length);

        long index = offset;
        while (dst.hasRemaining() && index < length) {
            dst.writeByte(getByte(index++));
        }
        return index - offset;
    }

    /**
     * Get {@code length} bytes starting at the given {@code offset} from this buffer. The returned bytes will
     * be immutable. The returned {@link Bytes} will have exactly {@code length} bytes.
     *
     * @param offset The offset into data to begin reading bytes
     * @param length The non-negative length in bytes to read
     * @return new {@link Bytes} containing the read data
     * @throws IllegalArgumentException If {@code length} is negative
     * @throws BufferUnderflowException If there are not {@code length} bytes between {@code offset} and
     *                                  {@link #length()}
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    @NonNull
    default Bytes getBytes(final long offset, final long length) {
        checkOffset(offset, length());

        if (length < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        if (length > length() - offset) {
            throw new BufferUnderflowException();
        }

        // If we find the result is empty, we can take a shortcut
        if (length == 0) {
            return Bytes.EMPTY;
        }

        // Otherwise we need to read the data into a new buffer and return it
        final var buf = new byte[Math.toIntExact(length)];
        if (getBytes(offset, buf) != length) {
            throw new BufferUnderflowException();
        }
        return Bytes.wrap(buf);
    }

    /**
     * Get {@code length} bytes starting at the given {@code offset} from this buffer. The returned bytes will
     * be immutable. The returned {@link RandomAccessData} will have exactly {@code length} bytes.
     *
     * @param offset The offset into data to begin reading bytes
     * @param length The non-negative length in bytes to read
     * @return new {@link RandomAccessData} containing the read data
     * @throws IllegalArgumentException If {@code length} is negative
     * @throws BufferUnderflowException If there are not {@code length} bytes between {@code offset} and
     *                                  {@link #length()}
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    @NonNull
    default RandomAccessData slice(final long offset, final long length) {
        return getBytes(offset, length);
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into an int value according to the Java
     * standard big-endian byte order.
     *
     * @param offset The offset into data to get an integer from.
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default int getInt(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
        //noinspection DuplicatedCode
        final byte b1 = getByte(offset);
        final byte b2 = getByte(offset + 1);
        final byte b3 = getByte(offset + 2);
        final byte b4 = getByte(offset + 3);
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into an int value according to specified byte
     * order.
     *
     * @param offset The offset into data to get an integer from.
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
        if ((length() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            checkOffset(offset, length());
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            final byte b4 = getByte(offset);
            final byte b3 = getByte(offset + 1);
            final byte b2 = getByte(offset + 2);
            final byte b1 = getByte(offset + 3);
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        } else {
            return getInt(offset);
        }
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into an unsigned int value according to the
     * Java standard big-endian byte order.
     *
     * @param offset The offset into data to get an unsigned integer from.
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default long getUnsignedInt(final long offset) {
        return (getInt(offset)) & 0xFFFFFFFFL;
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into an unsigned int value according to
     * specified byte order.
     *
     * @param offset The offset into data to get an unsigned integer from.
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default long getUnsignedInt(final long offset, @NonNull final ByteOrder byteOrder) {
        return (getInt(offset, byteOrder)) & 0xFFFFFFFFL;
    }

    /**
     * Gets eight bytes at the given {@code offset}, composing them into a long value according to the Java
     * standard big-endian byte order.
     *
     * @param offset The offset into data to get a signed long from.
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default long getLong(final long offset) {
        checkOffset(offset, length());
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
        //noinspection DuplicatedCode
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
                (b8 & 255));
    }

    /**
     * Gets eight bytes at the given {@code offset}, composing them into a long value according to specified byte
     * order.
     *
     * @param offset The offset into data to get a signed long from.
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The long value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
        if ((length() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            checkOffset(offset, length());
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
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
                    (b8 & 255));
        } else {
            return getLong(offset);
        }
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into a float value according to the Java
     * standard big-endian byte order.
     *
     * @param offset The offset into data to get a float from.
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default float getFloat(final long offset) {
        return Float.intBitsToFloat(getInt(offset));
    }

    /**
     * Gets four bytes at the given {@code offset}, composing them into a float value according to specified byte
     * order.
     *
     * @param offset The offset into data to get a float from.
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The float value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
        return Float.intBitsToFloat(getInt(offset, byteOrder));
    }

    /**
     * Gets eight bytes at the given {@code offset}, composing them into a double value according to the Java
     * standard big-endian byte order.
     *
     * @param offset The offset into data to get a double from.
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default double getDouble(final long offset) {
        return Double.longBitsToDouble(getLong(offset));
    }

    /**
     * Gets eight bytes at the given {@code offset}, composing them into a double value according to specified byte
     * order.
     *
     * @param offset The offset into data to get a double from.
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes from {@code offset} to the end of the buffer
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
        return Double.longBitsToDouble(getLong(offset, byteOrder));
    }

    /**
     * Get a 32bit protobuf varint at the given {@code offset}.
     *
     * @param offset The offset into data to get a varint from.
     * @return integer get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferUnderflowException If the end of the buffer is encountered before the last segment of the varint
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default int getVarInt(final long offset, final boolean zigZag) {
        return (int) getVarLong(Math.toIntExact(offset), zigZag);
    }

    /**
     * Get a 64bit protobuf varint at given {@code offset}.
     *
     * @param offset The offset into data to get a varlong from.
     * @return long get in var long format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()},
     *         or the end of the buffer is encountered before the last segment of the varlong
     * @throws DataEncodingException if the var long is malformed
     */
    default long getVarLong(final long offset, final boolean zigZag) {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            final byte b = getByte(offset + i);
            value |= (long) (b & 0x7F) << (i * 7);
            if (b >= 0) {
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw new DataEncodingException("Malformed var int");
    }

    /**
     * Get the contents of this entire buffer as a string, assuming bytes contained are a UTF8 encoded string.
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
     * @throws BufferUnderflowException if {@code len} is greater than {@link #length()} - {@code offset}
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    @NonNull
    default String asUtf8String(final long offset, final long len) {
        if (len == 0) {
            return "";
        }

        checkOffset(offset, length());
        if (len > length() - offset) {
            throw new BufferUnderflowException();
        }

        final var data = new byte[Math.toIntExact(len)];
        getBytes(offset, data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Check if the beginning of this buffer matches the given prefix bytes. An empty buffer matches an empty prefix.
     *
     * @param prefix the prefix bytes to compare with
     * @return true if prefix bytes match the beginning of the bytes from the buffer
     * @throws NullPointerException if prefix is null
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
     * @throws NullPointerException if prefix is null
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default boolean contains(final long offset, @NonNull final byte[] bytes) {
        checkOffset(offset, length());

        // If the number of bytes between offset and length is shorter than the bytes we're matching, then there
        // is NO WAY we could have a match, so we need to return false.
        if (length() - offset < bytes.length) {
            return false;
        }

        // Check each byte one at a time until we find a mismatch or, we get to the end, and all bytes match.
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != getByte(offset + i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the beginning of our bytes data matches the given prefix.
     *
     * @param prefix the prefix to compare with
     * @return true if prefix match the beginning of our bytes
     * @throws NullPointerException if prefix is null
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
     * @throws NullPointerException if data is null
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    default boolean contains(final long offset, @NonNull final RandomAccessData data) {
        // If this data is EMPTY, return true if only the incoming data is EMPTY too.
        if (length() == 0) {
            return data.length() == 0;
        }

        checkOffset(offset, length());

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

    /**
     * A helper method for efficient copy of our data into an OutputStream without creating a defensive copy
     * of the data. The implementation relies on a well-behaved OutputStream that doesn't modify the buffer data.
     *
     * @param outStream the OutputStream to copy into
     */
    void writeTo(@NonNull final OutputStream outStream);

    /**
     * A helper method for efficient copy of our data into an OutputStream without creating a defensive copy
     * of the data. The implementation relies on a well-behaved OutputStream that doesn't modify the buffer data.
     *
     * @param outStream The OutputStream to copy into.
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    void writeTo(@NonNull final OutputStream outStream, final int offset, final int length);

    /**
     * Throws {@code IndexOutOfBoundsException} if the given {@code offset} is negative
     * or greater than/equal to the given {@code length}.
     *
     * @param offset an offset in this RandomAccessData that the caller wants to access
     * @param length the maximum offset plus one (1) that is allowed to be accessed.
     *               It may be equal to the total length of the underlying buffer,
     *               or be less than the total length when a limit value is used.
     */
    default void checkOffset(final long offset, final long length) {
        if (offset < 0 || offset >= length) {
            throw new IndexOutOfBoundsException("offset " + offset + " is out of bounds for length " + length);
        }
    }

    /**
     * Throws {@code IndexOutOfBoundsException} if the given {@code offset} is negative
     * or greater than/equal to the given {@code length}, and throws {@code BufferUnderflowException}
     * if the {@code offset + dataLength - 1} exceeds the given {@code length}.
     * <p>
     * checkOffsetToRead(offset, length, 1) is equivalent to checkOffset(offset, length)
     * because the dataLength condition is always satisfied for dataLength == 1 byte
     * as long as the initial offset is within the bounds.
     *
     * @param offset an offset in this RandomAccessData that the caller wants to access
     * @param length the maximum offset plus one (1) that is allowed to be accessed.
     *               It may be equal to the total length of the underlying buffer,
     *               or be less than the total length when a limit value is used.
     * @param dataLength the length of the data to read
     */
    default void checkOffsetToRead(final long offset, final long length, final long dataLength) {
        if (offset < 0 || offset > length || (offset == length && dataLength != 0)) {
            throw new IndexOutOfBoundsException("offset " + offset + " is out of bounds for length " + length);
        }
        if (offset + dataLength - 1 >= length) {
            throw new BufferUnderflowException();
        }
    }

    /**
     * Throws {@code IndexOutOfBoundsException} if the given {@code offset} is negative
     * or greater than/equal to the given {@code length}, and throws {@code BufferOverflowException}
     * if the {@code offset + dataLength - 1} exceeds the given {@code length}.
     * <p>
     * checkOffsetToWrite(offset, length, 1) is equivalent to checkOffset(offset, length)
     * because the dataLength condition is always satisfied for dataLength == 1 byte
     * as long as the initial offset is within the bounds.
     *
     * @param offset an offset in this RandomAccessData that the caller wants to access
     * @param length the maximum offset plus one (1) that is allowed to be accessed.
     *               It may be equal to the total length of the underlying buffer,
     *               or be less than the total length when a limit value is used.
     * @param dataLength the length of the data to write
     */
    default void checkOffsetToWrite(final long offset, final long length, final long dataLength) {
        if (offset < 0 || offset > length || (offset == length && dataLength != 0)) {
            throw new IndexOutOfBoundsException("offset " + offset + " is out of bounds for length " + length);
        }
        if (offset + dataLength - 1 >= length) {
            throw new BufferOverflowException();
        }
    }
}
