package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <p>A alternative to byte array that is immutable</p>
 *
 * <p>It is simple to implement in basic form with just the {@code getLength()} and {@code getByte(int offset)} method 
 * needing implementing as all other get methods have public implementations. Though it will work, it should not be 
 * used like that in performance critical cases as specialized get methods can be many times more efficient.</p>
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public abstract class Bytes {

    /** Single instance of an empty Bytes we can use anywhere we need an empty Bytes */
    public static final Bytes EMPTY_BYTES = new Bytes() {
        @Override
        public int getLength() {
            return 0;
        }

        @Override
        public byte getByte(int offset) {
            throw new BufferUnderflowException();
        }
    };

    // ================================================================================================================
    // Static Methods

    /**
     * Create a new Bytes over the contents of the given byte array. This does not copy data it just wraps so any
     * changes to arrays contents will be effected here.
     *
     * @param byteArray The byte array to wrap
     * @return new Bytes with same contents as byte array
     */
    public static Bytes wrap(byte[] byteArray) {
        // For now use ByteOverByteBuffer, could have better array based implementation later
        return new ByteOverByteBuffer(byteArray);
    }

    // ================================================================================================================
    // Object Methods

    /**
     * toString that outputs data in buffer in bytes.
     *
     * @return nice debug output of buffer contents
     */
    @Override
    public String toString() {
        // build string
        StringBuilder sb = new StringBuilder();
        sb.append("Bytes[");
        try {
            for (int i = 0; i < getLength(); i++) {
                int v = getByte(i) & 0xFF;
                sb.append(v);
                if (i < (getLength()-1)) sb.append(',');
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Equals, important that it works for all subclasses of Bytes as well. As any 2 Bytes classes with same contents of
     * bytes are equal
     *
     * @param o the other Bytes object to compare to for equality
     * @return true if o instance of Bytes and contents match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Bytes)) return false;
        Bytes that = (Bytes) o;
        final int length = getLength();
        if (length != that.getLength()) {
            return false;
        }
        if (length == 0) return true;
        try {
            for (int i = 0; i < length; i++) {
                if (getByte(i) != that.getByte(i)) {
                    return false;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * Compute hash code for Bytes based on all bytes of content
     *
     * @return unique for any given content
     */
    @Override
    public int hashCode() {
        int h = 1;
        try {
            for (int i = getLength() - 1; i >= 0; i--) {
                h = 31 * h + (int) getByte(i);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return h;
    }

    // ================================================================================================================
    // Bytes Methods

    /**
     * Get the number of bytes of data stored
     * 
     * @return number of bytes of data stored
     */
    public abstract int getLength();
    
    /**
     * Gets the byte at given {@code offset}.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset} TODO should this be a int, to match InputStream?
     * @throws BufferUnderflowException If the given {@code offset} is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    public abstract byte getByte(int offset) throws IOException ;

    /**
     * Gets the byte at given {@code offset} as unsigned.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset}
     * @throws BufferUnderflowException If the given {@code offset} is not smaller than its limit
     * @throws IOException if an I/O error occurs
     */
    public int getUnsignedByte(int offset) throws IOException  {
        return Byte.toUnsignedInt(getByte(offset));
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                no larger than {@code dst.length}
     * @param length The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                no larger than {@code dst.length - offset}
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining to be get
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     * not hold
     * @throws IOException if an I/O error occurs
     */
    public void getBytes(int offset, byte[] dst, int dstOffset, int length) throws IOException {
        if ((offset + length) > getLength()) {
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
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code }dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination array
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining in this buffer
     * @throws IOException if an I/O error occurs
     */
    public void getBytes(int offset, byte[] dst) throws IOException {
        getBytes(offset, dst, 0, dst.length);
    }

    /**
     * Get bytes starting at given {@code offset} into dst ByteBuffer up to remaining bytes in ByteBuffer.
     *
     * @param offset The offset into data to get bytes at
     * @param dst The destination ByteBuffer
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this buffer
     * @throws IOException if an I/O error occurs
     */
    public void getBytes(int offset, ByteBuffer dst) throws IOException {
        if ((offset + dst.remaining()) > getLength()) {
            throw new BufferUnderflowException();
        }
        while(dst.hasRemaining()) {
            dst.put(getByte(offset++));
        }
    }

    /**
     * Gets the next four bytes at the given {@code offset}, composing them into an int value according to the Java
     * standard big-endian byte order, and then increments the position by four.
     *
     * @param offset The offset into data to get int at
     * @return The int value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws IOException if an I/O error occurs
     */
    public int getInt(int offset) throws IOException {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public int getInt(int offset, ByteOrder byteOrder) throws IOException {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public long getUnsignedInt(int offset) throws IOException {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public long getUnsignedInt(int offset, ByteOrder byteOrder) throws IOException {
        if ((getLength() - offset) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getInt(offset);
        } else {
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public long getLong(int offset) throws IOException {
        if ((getLength() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = getByte(offset++);
        final byte b2 = getByte(offset++);
        final byte b3 = getByte(offset++);
        final byte b4 = getByte(offset++);
        final byte b5 = getByte(offset++);
        final byte b6 = getByte(offset++);
        final byte b7 = getByte(offset++);
        final byte b8 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public long getLong(int offset, ByteOrder byteOrder) throws IOException {
        if ((getLength() - offset) < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            return getLong(offset);
        } else {
            final byte b8 = getByte(offset++);
            final byte b7 = getByte(offset++);
            final byte b6 = getByte(offset++);
            final byte b5 = getByte(offset++);
            final byte b4 = getByte(offset++);
            final byte b3 = getByte(offset++);
            final byte b2 = getByte(offset++);
            final byte b1 = getByte(offset);
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
     * @throws IOException if an I/O error occurs
     */
    public float getFloat(int offset) throws IOException {
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
     * @throws IOException if an I/O error occurs
     */
    public float getFloat(int offset, ByteOrder byteOrder) throws IOException {
        return Float.intBitsToFloat(getInt(offset, byteOrder));
    }

    /**
     * Gets the next eight bytes at the given {@code offset}, composing them into a double value according to the Java
     * standard big-endian byte order, and then increments the position by eight.
     *
     * @param offset The offset into data to get double at
     * @return The double value at the given {@code offset}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws IOException if an I/O error occurs
     */
    public double getDouble(int offset) throws IOException {
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
     * @throws IOException if an I/O error occurs
     */
    public double getDouble(int offset, ByteOrder byteOrder) throws IOException {
        return Double.longBitsToDouble(getLong(offset, byteOrder));
    }

    /**
     * Get a 32bit protobuf varint at given {@code offset}. An integer var int can be 1 to 5 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return integer get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    public int getVarInt(int offset, boolean zigZag) throws IOException {
        return (int)getVarLong(offset, zigZag);
    }

    /**
     * Get a 64bit protobuf varint at given {@code offset}. An long var int can be 1 to 10 bytes.
     *
     * @param offset The offset into data to get varint at
     * @return long get in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    public long getVarLong(int offset, boolean zigZag) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = getByte(offset++);
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new IOException("Malformed Varint");
    }
}
