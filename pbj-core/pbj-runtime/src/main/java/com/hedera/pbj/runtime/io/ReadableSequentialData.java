package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * {@link SequentialData} which may be read. This interface is suitable for reading data from a stream or buffer.
 *
 * <p>As data is read from the stream or buffer, the {@link #position()} is incremented. The limit is the maximum
 * {@link #position()} within the sequence from which data can be read. Implementations of this class must provide an
 * implementation of {@link #readByte()} that reads a byte from the current {@link #position()} and increments the
 * {@link #position()} by 1. All other read methods have a default implementation based on {@link #readByte()}.
 * Implementations of this interface may choose to reimplement those methods to be more efficient as needed.
 */
public interface ReadableSequentialData extends SequentialData {

    /**
     * Reads the signed byte at current {@link #position()}, and then increments the {@link #position()}.
     *
     * @return The signed byte at current {@link #position()}
     * @throws BufferUnderflowException If the current {@link #position()} is not smaller than its limit
     * @throws DataAccessException if an I/O error occurs
     */
    byte readByte();

    /**
     * Reads the unsigned byte at current {@link #position()}, and then increments the {@link #position()}. That is, it
     * reads a single byte, but returns an integer in the range 0 to 255.
     *
     * @return The unsigned byte at current {@link #position()}
     * @throws BufferUnderflowException If the current {@link #position()} is not smaller than its limit
     * @throws DataAccessException if an I/O error occurs
     */
    default int readUnsignedByte() {
        return Byte.toUnsignedInt(readByte());
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination array up to the size of {@code dst}
     * array. There must be at least {@code dst.length} bytes remaining in the sequence.
     *
     * @param dst The destination array
     * @throws BufferUnderflowException If there are fewer than {@code dst.length} bytes remaining in this sequence
     * @throws DataAccessException if an I/O error occurs
     */
    default void readBytes(@NonNull final byte[] dst) {
        readBytes(dst, 0, dst.length);
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination array, up to the size of {@code dst}
     * array. There must be at least {@code length} bytes remaining in the sequence.
     *
     * @param dst The array into which bytes are to be written
     * @param offset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                no larger than {@code dst.length}
     * @param length The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                no larger than {@code dst.length - offset}
     * @throws BufferUnderflowException If there are fewer than {@code length} bytes remaining to be read
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     * not hold
     * @throws DataAccessException if an I/O error occurs
     */
    default void readBytes(@NonNull final byte[] dst, final int offset, final int length) {
        if ((limit() - position()) < length) {
            throw new BufferUnderflowException();
        }
        if (offset < 0 || (offset + length) > dst.length) {
            throw new IndexOutOfBoundsException("offset="+offset+" (offset + length)="+(offset + length)+
                    "  dst.length="+ dst.length);
        }
        for (int i = offset; i < (offset+length); i++) {
            dst[i] = readByte();
        }
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination {@link ByteBuffer} up to remaining bytes
     * in the {@link ByteBuffer}. There must be at least {@code dst.remaining()} bytes remaining in the sequence.
     *
     * @param dst The destination {@link ByteBuffer}
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this sequence
     * @throws DataAccessException if an I/O error occurs
     */
    default void readBytes(@NonNull final ByteBuffer dst) {
        if ((limit() - position()) < dst.remaining()) {
            throw new BufferUnderflowException();
        }

        for (int i = 0; i < dst.remaining(); i++) {
            dst.put(readByte());
        }
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination {@link BufferedData} up to
     * remaining bytes in the {@link BufferedData}. There must be at least {@code dst.remaining()} bytes
     * remaining in the sequence.
     *
     * @param dst The destination {@link BufferedData}
     * @throws BufferUnderflowException If there are fewer than {@code dst.remaining()} bytes remaining in this sequence
     * @throws DataAccessException if an I/O error occurs
     */
    default void readBytes(@NonNull final BufferedData dst) {
        if ((limit() - position()) < dst.remaining()) {
            throw new BufferUnderflowException();
        }
        
        for (int i = 0; i < dst.remaining(); i++) {
            dst.writeByte(readByte());
        }
    }

    /**
     * Read {@code length} bytes from this sequence, returning them as a {@link Bytes} buffer of
     * the read data. The returned bytes will be immutable.
     *
     * @param length The non-negative length in bytes to read
     * @return new {@link Bytes} containing the read data
     * @throws BufferUnderflowException If length is more than remaining bytes
     * @throws DataAccessException if an I/O error occurs
     */
    default @NonNull Bytes readBytes(final int length) {
        final var bytes = new byte[length];
        readBytes(bytes);
        return Bytes.wrap(bytes);
    }

    /**
     * Return a "view" on the underlying sequence of bytes, starting at the current {@link #position()} and extending
     * {@code length} bytes. The returned bytes may change over time if the underlying data is updated! The
     * {@link #position()} of this sequence will be incremented by {@code length} bytes. The {@link #position()}
     * of the returned sequence will be 0 and its {@link #limit()} will be {@code length}.
     *
     * <p>If the sequence is a stream, then the returned sequence will be a buffer of the bytes captured by
     * the stream, and will be effectively immutable. If the sequence is a buffer, then the returned sequence
     * will be a dynamic view of the underlying buffer.
     *
     * @param length The non-negative length in bytes to read
     * @return new {@link RandomAccessData} containing a view on the read data
     * @throws BufferUnderflowException If length is more than remaining bytes
     * @throws DataAccessException if an I/O error occurs
     */
    default @NonNull ReadableSequentialData view(final int length) {
        final var bytes = new byte[length];
        readBytes(bytes);
        return BufferedData.wrap(bytes);
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into an int value according to the
     * Java standard big-endian byte order, and then increments the {@link #position()} by four.
     *
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default int readInt() {
        if ((limit() - position()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | ((b4 & 0xFF));
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into an int value according to
     * specified byte order, and then increments the {@link #position()} by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default int readInt(@NonNull final ByteOrder byteOrder) {
        if ((limit() - position()) < Integer.BYTES) {
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
     * Reads the next four bytes at the current {@link #position()}, composing them into an unsigned int value according
     * to the Java standard big-endian byte order, and then increments the {@link #position()} by four.
     *
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readUnsignedInt() {
        if ((limit() - position()) < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        return ((b1 & 0xFFL) << 24) | ((b2 & 0xFFL) << 16) | ((b3 & 0xFFL) << 8) | ((b4 & 0xFFL));
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into an unsigned int value according
     * to specified byte order, and then increments the {@link #position()} by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readUnsignedInt(@NonNull final ByteOrder byteOrder) {
        if ((limit() - position()) < Integer.BYTES) {
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
     * Reads the next eight bytes at the current {@link #position()}, composing them into a long value according to the
     * Java standard big-endian byte order, and then increments the {@link #position()} by eight.
     *
     * @return The long value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readLong() {
        if ((limit() - position()) < Long.BYTES) {
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
     * Reads the next eight bytes at the current {@link #position()}, composing them into a long value according to
     * specified byte order, and then increments the {@link #position()} by eight.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The long value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readLong(@NonNull final ByteOrder byteOrder) {
        if ((limit() - position()) < Long.BYTES) {
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
     * Reads the next four bytes at the current {@link #position()}, composing them into a float value according to the
     * Java standard big-endian byte order, and then increments the {@link #position()} by four.
     *
     * @return The float value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into a float value according to
     * specified byte order, and then increments the {@link #position()} by four.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The float value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default float readFloat(@NonNull final ByteOrder byteOrder) {
        return Float.intBitsToFloat(readInt(byteOrder));
    }

    /**
     * Reads the next eight bytes at the current {@link #position()}, composing them into a double value according to
     * the Java standard big-endian byte order, and then increments the {@link #position()} by eight.
     *
     * @return The double value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads the next eight bytes at the current {@link #position()}, composing them into a double value according to
     * specified byte order, and then increments the {@link #position()} by eight.
     *
     * @param byteOrder the byte order, aka endian to use
     * @return The double value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default double readDouble(@NonNull final ByteOrder byteOrder) {
        return Double.longBitsToDouble(readLong(byteOrder));
    }

    /**
     * Read a 32bit protobuf varint at current {@link #position()}. An integer var int can be 1 to 5 bytes.
     *
     * @return integer read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws DataAccessException if an I/O error occurs
     */
    default int readVarInt(final boolean zigZag) {
        return (int)readVarLong(zigZag);
    }

    /**
     * Read a 64bit protobuf varint at current {@link #position()}. A long var int can be 1 to 10 bytes.
     *
     * @return long read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws DataAccessException if an I/O error occurs
     * @throws DataEncodingException if the variable long cannot be decoded
     */
    default long readVarLong(final boolean zigZag) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new DataEncodingException("Malformed Varint");
    }
}
