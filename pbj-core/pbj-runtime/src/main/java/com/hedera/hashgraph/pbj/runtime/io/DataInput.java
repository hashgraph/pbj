package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

/**
 * <p>A high level interface to represent a way to read data as tokens, each method assumes there are enough bytes
 * available to read it fully or a exception is thrown. It is designed so that it can be backed by a stream or a
 * buffer.</p>
 *
 * <p>It is simple to implement in basic form with just the readByte() read method needing implementing as all other
 * read methods have default implementations. Though it will work, it should not be used like that in performance
 * critical cases as specialized read methods can be many times more efficient.</p>
 */
@SuppressWarnings("DuplicatedCode")
public interface DataInput {

    /**
     * Current read position relative to origin
     *
     * @return number of bytes that have been read since the origin
     */
    long getPosition();

    /**
     * Limit that we can read up to, relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     *
     * @return maximum position that can be read from origin
     */
    long getLimit();

    /**
     * Set limit that can be read up to, relative to origin. If less than position then set to position, meaning there
     * are no bytes left available to be read.
     *
     * @param limit The new limit relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     */
    void setLimit(long limit);

    /**
     * If there are bytes remaining to be read between current position and limit. There will be at least one byte
     * available to read.
     *
     * @return true if (limit - position) > 0
     */
    boolean hasRemaining();

    /**
     * Reads the byte at current position, and then increments the position.
     *
     * @return The byte at current position TODO should this be a int, to match InputStream?
     * @throws BufferUnderflowException If the current position is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    byte readByte() throws IOException ;

    /**
     * Reads the byte at current position as unsigned, and then increments the position.
     *
     * @return The byte at current position
     * @throws BufferUnderflowException If the current position is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    default int readUnsignedByte() throws IOException  {
        return Byte.toUnsignedInt(readByte());
    }

    /**
     * Read bytes starting at current position into dst array up to the size of {@code dst} array.
     *
     * @param dst The array into which bytes are to be written
     * @param offset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                no larger than {@code dst.length}
     * @param length The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                no larger than {@code dst.length - offset}
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining to be read
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     * not hold
     * @throws IOException if an I/O error occurs
     */
    default void readBytes(byte[] dst, int offset, int length) throws IOException {
        if ((getLimit() - getPosition()) < length) {
            throw new BufferUnderflowException();
        }
        if (offset < 0 || (offset + length) >= dst.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = offset; i < (offset+length); i++) {
            dst[i] = readByte();
        }
    }

    /**
     * Read bytes starting at current position into dst array up to the size of {@code }dst} array.
     *
     * @param dst The destination array
     * @return This buffer
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining in this buffer
     * @throws IOException if an I/O error occurs
     */
    default void readBytes(byte[] dst) throws IOException {
        readBytes(dst, 0, dst.length);
    }

    /**
     * Reads the next four bytes at the current position, composing them into an int value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @return The int value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default int readInt() throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
    }

    /**
     * Reads the next four bytes at the current position, composing them into an int value according to specified byte
     * order, and then increments the position by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default int readInt(ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return readInt();
        } else {
            final byte b4 = readByte();
            final byte b3 = readByte();
            final byte b2 = readByte();
            final byte b1 = readByte();
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
        }
    }

    /**
     * Reads the next four bytes at the current position, composing them into an unsigned int value according to the
     * Java standard big-endian byte order, and then increments the position by four.
     *
     * @return The int value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default long readUnsignedInt() throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
    }

    /**
     * Reads the next four bytes at the current position, composing them into an unsigned int value according to
     * specified byte order, and then increments the position by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default long readUnsignedInt(ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return readInt();
        } else {
            final byte b4 = readByte();
            final byte b3 = readByte();
            final byte b2 = readByte();
            final byte b1 = readByte();
            return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
        }
    }

    /**
     * Reads the next eight bytes at the current position, composing them into a long value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @return The long value at the current position
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default long readLong() throws IOException {
        if ((getLimit() - getPosition()) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        final byte b5 = readByte();
        final byte b6 = readByte();
        final byte b7 = readByte();
        final byte b8 = readByte();
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
     * Reads the next eight bytes at the current position, composing them into a long value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The long value at the current position
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default long readLong(ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return readLong();
        } else {
            final byte b8 = readByte();
            final byte b7 = readByte();
            final byte b6 = readByte();
            final byte b5 = readByte();
            final byte b4 = readByte();
            final byte b3 = readByte();
            final byte b2 = readByte();
            final byte b1 = readByte();
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
     * Reads the next four bytes at the current position, composing them into a float value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @return The float value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads the next four bytes at the current position, composing them into a float value according to specified byte
     * order, and then increments the position by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The float value at the current position
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default float readFloat(ByteOrder byteOrder) throws IOException {
        return Float.intBitsToFloat(readInt(byteOrder));
    }

    /**
     * Reads the next eight bytes at the current position, composing them into a double value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @return The double value at the current position
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads the next eight bytes at the current position, composing them into a double value according to specified byte
     * order, and then increments the position by eight.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The double value at the current position
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default double readDouble(ByteOrder byteOrder) throws IOException {
        return Double.longBitsToDouble(readLong(byteOrder));
    }

    /**
     * Read a 32bit protobuf varint at current position. An integer var int can be 1 to 5 bytes.
     *
     * @return integer read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    default int readVarInt(boolean zigZag) throws IOException {
        return (int)readVarLong(zigZag);
    }

    /**
     * Read a 64bit protobuf varint at current position. An long var int can be 1 to 10 bytes.
     *
     * @return long read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    default long readVarLong(boolean zigZag) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new IOException("Malformed Varint");
    }
}
