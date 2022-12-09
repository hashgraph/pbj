package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

/**
 * <p>A high level interface to represent a way to write data as tokens, each method assumes there are enough space
 * available to write fully or a exception is thrown. It is designed so that it can be backed by a stream or a
 * buffer.</p>
 *
 * <p>It is simple to implement in basic form with just the writeByte() write method needing implementing as all other
 * write methods have default implementations. Though it will work, it should not be used like that in performance
 * critical cases as specialized write methods can be many times more efficient.</p>
 */
@SuppressWarnings("DuplicatedCode")
public interface DataOutput extends PositionedData {

    /**
     * Writes the given byte at the current position, and then increments the position.
     *
     * @param b The byte to be written
     * @throws BufferOverflowException If this buffer's current position is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    void writeByte(byte b) throws IOException;

    /**
     * Writes the given unsigned byte at the current position, and then increments the position.
     *
     * @param b The unsigned byte in a integer to be written
     * @throws BufferOverflowException If this buffer's current position is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    default void writeUnsignedByte(int b) throws IOException {
        writeByte((byte)b);
    }

    /**
     * Write {@code length} bytes from the given array, starting at the given offset in the array and at the current
     * position.  The position is then incremented by {@code length}.
     *
     * @param src The array from which bytes are to be read
     * @param offset The offset within the array of the first byte to be read; must be non-negative and no larger
     *                than {@code src.length}
     * @param length The number of bytes to be read from the given array; must be non-negative and no larger
     *                than {@code src.length - offset}
     * @throws BufferOverflowException If there is insufficient space before limit
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     *          not hold
     * @throws IOException if an I/O error occurs
     */
    default void writeBytes(byte[] src, int offset, int length) throws IOException {
        if ((getLimit() - getPosition()) < length) {
            throw new BufferUnderflowException();
        }
        if (offset < 0 || (offset + length) >= src.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = offset; i < (offset+length); i++) {
            writeByte(src[i]);
        }
    }

    /**
     * This method writes the entire content of the given source byte array.  The position is then incremented by
     * {@code src.length}.
     *
     * @param src The source array to write
     * @throws BufferOverflowException If there is insufficient space before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeBytes(byte[] src) throws IOException {
        writeBytes(src, 0, src.length);
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * position, and then increments the position by four.
     *
     * @param value The int value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default void writeInt(int value) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        writeByte((byte)(value >>> 24));
        writeByte((byte)(value >>> 16));
        writeByte((byte)(value >>>  8));
        writeByte((byte)(value));
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * position, and then increments the position by four.
     *
     * @param value The int value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default void writeInt(int value, ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeInt(value);
        } else {
            writeByte((byte) (value));
            writeByte((byte) (value >>> 8));
            writeByte((byte) (value >>> 16));
            writeByte((byte) (value >>> 24));
        }
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * position, and then increments the position by four.
     *
     * @param value The int value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default void writeUnsignedInt(long value) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        writeByte((byte)(value >>> 24));
        writeByte((byte)(value >>> 16));
        writeByte((byte)(value >>>  8));
        writeByte((byte)(value));
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * position, and then increments the position by four.
     *
     * @param value The int value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    default void writeUnsignedInt(long value, ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeUnsignedInt(value);
        } else {
            writeByte((byte) (value));
            writeByte((byte) (value >>> 8));
            writeByte((byte) (value >>> 16));
            writeByte((byte) (value >>> 24));
        }
    }

    /**
     * Writes eight bytes containing the given long value, in the standard Java big-endian  byte order at the current
     * position, and then increments the position by eight.
     *
     * @param value The long value to be written
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeLong(long value) throws IOException {
        if ((getLimit() - getPosition()) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        writeByte((byte)(value >>> 56));
        writeByte((byte)(value >>> 48));
        writeByte((byte)(value >>> 40));
        writeByte((byte)(value >>> 32));
        writeByte((byte)(value >>> 24));
        writeByte((byte)(value >>> 16));
        writeByte((byte)(value >>>  8));
        writeByte((byte)(value));
    }

    /**
     * Writes eight bytes containing the given long value, in the specified byte order at the current  position, and
     * then increments the position by eight.
     *
     * @param value The long value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeLong(long value, ByteOrder byteOrder) throws IOException {
        if ((getLimit() - getPosition()) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeLong(value);
        } else {
            writeByte((byte) (value));
            writeByte((byte) (value >>> 8));
            writeByte((byte) (value >>> 16));
            writeByte((byte) (value >>> 24));
            writeByte((byte) (value >>> 32));
            writeByte((byte) (value >>> 40));
            writeByte((byte) (value >>> 48));
            writeByte((byte) (value >>> 56));
        }
    }

    /**
     * Writes four bytes containing the given float value, in the standard Java big-endian byte order at the current
     * position, and then increments the position by four.
     *
     * @param value The float value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeFloat(float value) throws IOException {
        writeInt(Float.floatToIntBits(value));
    }

    /**
     * Writes four bytes containing the given float value, in the specified byte order at the current position, and then
     * increments the position by four.
     *
     * @param value The float value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeFloat(float value, ByteOrder byteOrder) throws IOException {
        writeInt(Float.floatToIntBits(value), byteOrder);
    }

    /**
     * Writes eight bytes containing the given double value, in the standard Java big-endian byte order at the current
     * position, and then increments the position by eight.
     *
     * @param value The double value to be written
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeDouble(double value) throws IOException {
        writeLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes eight bytes containing the given double value, in the specified byte order at the current position, and
     * then increments the position by eight.
     *
     * @param value The double value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before limit
     * @throws IOException if an I/O error occurs
     */
    default void writeDouble(double value, ByteOrder byteOrder) throws IOException {
        writeLong(Double.doubleToLongBits(value), byteOrder);
    }

    /**
     * Write a 32bit protobuf varint at current position. An integer var int can be 1 to 5 bytes.
     *
     * @param value integer to write in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    default void writeVarInt(int value, boolean zigZag) throws IOException {
        writeVarLong(value, zigZag);
    }

    /**
     * Write a 64bit protobuf varint at current position. An long var int can be 1 to 10 bytes.
     *
     * @param value long to write in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    default void writeVarLong(long value, boolean zigZag) throws IOException {
        if (zigZag) {
            value = (value << 1) ^ (value >> 63);
        }
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeByte((byte) value);
                return;
            } else {
                writeByte((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }
}
