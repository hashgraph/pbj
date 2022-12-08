package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;

@SuppressWarnings("DuplicatedCode")
public interface DataOutput {

    /**
     * Current write position relative to origin
     *
     * @return number of bytes that have been written since the origin
     */
    long getPosition();

    /**
     * Limit that we can write up to, relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     *
     * @return maximum position that can be written from origin
     */
    long getLimit();

    /**
     * Set limit that can be written up to, relative to origin. If less than position then set to position, meaning there
     * are no bytes left available to be written.
     *
     * @param limit The new limit relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     */
    void setLimit(long limit);

    /**
     * If there are bytes remaining to be written between current position and limit
     *
     * @return true if (limit - position) > 0
     */
    boolean hasRemaining();

    /**
     * Writes the given byte at the current position, and then increments the position.
     *
     * @param b The byte to be written
     * @throws BufferOverflowException If this buffer's current position is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    void writeByte(byte b) throws IOException;

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
}
