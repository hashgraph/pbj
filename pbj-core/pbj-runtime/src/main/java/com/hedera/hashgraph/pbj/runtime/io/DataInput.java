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
        // Protobuf encodes smaller integers with fewer bytes than larger integers. It takes a full byte
        // to encode 7 bits of information. So, if all 64 bits of a long are in use (for example, if the
        // leading bit is 1, or even all bits are 1) then it will take 10 bytes to transmit what would
        // have otherwise been 8 bytes of data!
        //
        // Thus, at most, reading a varint should involve reading 10 bytes of data.
        //
        // The leading bit of each byte is a continuation bit. If set, another byte will follow.
        // If we read 10 bytes in sequence with a continuation bit set, then we have a malformed
        // byte stream.
        // The bytes come least to most significant 7 bits. So the first byte we read represents
        // the lowest 7 bytes, then the next byte is the next highest 7 bytes, etc.

        // Keeps track of the number of bytes that have been read. If we read 10 in a row all with
        // the leading continuation bit set, then throw a malformed protobuf exception.
        int numBytesRead = 0;
        // The final value.
        long value = 0;
        // The amount to shift the bits we read by before AND with the value
        long shift = 0;
        // The byte to read from the stream
        int b;

        while ((b = readByte()) != -1) {
            // Keep track of the number of bytes read
            numBytesRead++;
            // Checks whether the continuation bit is set
            final boolean continuationBitSet = (b & 0b1000_0000) != 0;
            // Strip off the continuation bit by keeping only the data bits
            b &= 0b0111_1111;
            // Shift the data bits left into position to AND with the value
            final long toBeAdded = (long) b << shift;
            value |= toBeAdded;
            // Increment the shift for the next data bits (if there are more bits)
            shift += 7;

            if (continuationBitSet) {
                // msb is set, so there is another byte following this one. If we've just read our 10th byte,
                // then we have a malformed protobuf stream
                if (numBytesRead == 10) {
                    throw new IOException("Malformed var int format");
                }
            } else {
                break;
            }
        }
        return zigZag ? ((value >>> 1) ^ -(value & 1)) : value;
    }
}
