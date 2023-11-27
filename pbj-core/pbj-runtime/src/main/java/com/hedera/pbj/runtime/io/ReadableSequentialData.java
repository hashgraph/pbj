package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import com.hedera.pbj.runtime.io.stream.EOFException;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link SequentialData} which may be read. This interface is suitable for reading data from a stream or buffer.
 * Once read, data cannot be re-read. The {@link #position()}, once incremented, cannot be reset or decremented.
 *
 * <p>As data is read from the stream or buffer, the {@link #position()} is incremented. The limit is the maximum
 * {@link #position()} within the sequence from which data can be read. Implementations of this class must provide an
 * implementation of {@link #readByte()} that reads a byte from the current {@link #position()} and increments the
 * {@link #position()} by 1. All other read methods have a default implementation based on {@link #readByte()}.
 * Implementations of this interface may choose to reimplement those methods to be more efficient as needed.
 */
public interface ReadableSequentialData extends SequentialData {

    /**
     * Reads the signed byte at current {@link #position()}, and then increments the {@link #position()} by 1.
     *
     * @return The signed byte at the current {@link #position()}
     * @throws BufferUnderflowException If there are no bytes remaining in this sequence
     * @throws DataAccessException If an I/O error occurs
     */
    byte readByte();

    /**
     * Reads the unsigned byte at the current {@link #position()}, and then increments the {@link #position()} by 1.
     * That is, it reads a single byte, but returns an integer in the range 0 to 255.
     *
     * @return The unsigned byte at current {@link #position()}
     * @throws BufferUnderflowException If there are no bytes remaining in this sequence
     * @throws DataAccessException If an I/O error occurs
     */
    default int readUnsignedByte() {
        return Byte.toUnsignedInt(readByte());
    }

    /**
     * Read bytes starting at current {@link #position()} into the {@code dst} array, up to the size of the {@code dst}
     * array. If {@code dst} is larger than the remaining bytes in the sequence, only the remaining bytes are read.
     * The total number of bytes actually read are returned. The bytes will be placed starting at index 0 of the array.
     * The {@link #position()} will be incremented by the number of bytes read. If no bytes are available in the
     * sequence, then 0 is returned.
     *
     * <p>The {@code dst} array may be partially written to at the time that any of the declared exceptions are thrown.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param dst The destination array. Cannot be null.
     * @throws NullPointerException if {@code dst} is null
     * @throws DataAccessException If an I/O error occurs
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long readBytes(@NonNull final byte[] dst) {
        return readBytes(dst, 0, dst.length);
    }

    /**
     * Read bytes starting at the current {@link #position()} into the {@code dst} array, up to {@code maxLength}
     * number of bytes. If {@code maxLength} is larger than the remaining bytes in the sequence, only the remaining
     * bytes are read. The total number of bytes actually read are returned. The bytes will be placed starting at index
     * {@code offset} of the array. The {@link #position()} will be incremented by the number of bytes read. If no
     * bytes are available in the sequence, then 0 is returned.
     *
     * <p>The {@code dst} array may be partially written to at the time that any of the declared exceptions are thrown.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param dst The array into which bytes are to be written
     * @param offset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                no larger than {@code dst.length - maxLength}.
     * @param maxLength The maximum number of bytes to be written to the given {@code dst} array; must be non-negative
     *                and no larger than {@code dst.length - offset}
     * @throws NullPointerException If {@code dst} is null
     * @throws IndexOutOfBoundsException If {@code offset} is out of bounds of {@code dst} or if
     *                                  {@code offset + maxLength} is not less than {@code dst.length}
     * @throws IllegalArgumentException If {@code maxLength} is negative
     * @throws DataAccessException If an I/O error occurs
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var length = Math.min(maxLength, remaining());
        final var maxIndex = offset + length;
        long bytesRead = 0;
        for (int i = offset; i < maxIndex; i++) {
            if (!hasRemaining()) return (long) i - offset;
            try {
                dst[i] = readByte();
                bytesRead++;
            } catch (EOFException e) {
                return bytesRead;
            }
        }
        return length;
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination {@link ByteBuffer}, up to
     * {@link ByteBuffer#remaining()} number of bytes. If {@link ByteBuffer#remaining()} is larger than the remaining
     * bytes in the sequence, only the remaining bytes are read. The total number of bytes actually read are returned.
     * The bytes will be placed starting at index {@link ByteBuffer#position()} of the buffer and the
     * {@link #position()} will be incremented by the number of bytes read. If no bytes are available in the sequence,
     * then 0 is returned.
     *
     * <p>The {@code dst} buffer may be partially written to at the time that any of the declared exceptions are thrown.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param dst The destination {@link ByteBuffer}
     * @throws NullPointerException If {@code dst} is null
     * @throws DataAccessException If an I/O error occurs
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long readBytes(@NonNull final ByteBuffer dst) {
        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var len = dst.remaining();
        for (int i = 0; i < len; i++) {
            if (!hasRemaining()) return i;
            dst.put(readByte());
        }
        return len;
    }

    /**
     * Read bytes starting at current {@link #position()} into the destination {@link BufferedData}, up to
     * {@link BufferedData#remaining()} number of bytes. If {@link BufferedData#remaining()} is larger than the
     * remaining bytes in the sequence, only the remaining bytes are read. The total number of bytes actually read are
     * returned. The bytes will be placed starting at index {@link BufferedData#position()} of the buffer. The
     * {@link #position()} will be incremented by the number of bytes read. If no bytes are available in the sequence,
     * then 0 is returned.
     *
     * <p>The {@code dst} buffer may be partially written to at the time that any of the declared exceptions are thrown.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param dst The destination {@link BufferedData}
     * @throws DataAccessException If an I/O error occurs
     * @return The number of bytes read actually read and placed into {@code dst}
     */
    default long readBytes(@NonNull final BufferedData dst) {
        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var len = dst.remaining();
        for (int i = 0; i < len; i++) {
            if (!hasRemaining()) return i;
            dst.writeByte(readByte());
        }
        return len;
    }

    /**
     * Read {@code length} bytes from this sequence, returning them as a {@link Bytes} buffer of
     * the read data. The returned bytes will be immutable. The {@link #position()} of this sequence will be
     * incremented by {@code length} bytes.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param length The non-negative length in bytes to read
     * @return new {@link Bytes} containing the read data
     * @throws IllegalArgumentException If {@code length} is negative
     * @throws BufferUnderflowException If there are not {@code length} bytes remaining in this sequence
     * @throws DataAccessException If an I/O error occurs
     */
    default @NonNull Bytes readBytes(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative length not allowed");
        }

        if (remaining() < length) {
            throw new BufferUnderflowException();
        }

        final var bytes = new byte[length];
        readBytes(bytes, 0, length);
        return Bytes.wrap(bytes);
    }

    /**
     * Return a "view" on the underlying sequence of bytes, starting at the current {@link #position()} and extending
     * {@code length} bytes. The returned bytes may change over time if the underlying data is updated! The
     * {@link #position()} of this sequence will be incremented by {@code length} bytes. The {@link #position()}
     * of the returned sequence will be 0 and its {@link #limit()} and {@link #capacity()} will be {@code length}.
     *
     * <p>If the sequence is a stream, then the returned sequence will be a buffer of the bytes captured by
     * the stream, and will be effectively immutable. If the sequence is a buffer, then the returned sequence
     * will be a dynamic view of the underlying buffer.
     *
     * <p>Bytes are read from the sequence one at a time. If there are not {@code length} bytes remaining in this
     * sequence, then a {@link BufferUnderflowException} will be thrown. The {@link #position()} will be
     * incremented by the number of bytes read prior to the exception.
     *
     * @param length The non-negative length in bytes to read
     * @return new {@link RandomAccessData} containing a view on the read data
     * @throws IllegalArgumentException If length is less than 0
     * @throws BufferUnderflowException If there are no bytes remaining in this sequence and a byte is read
     * @throws DataAccessException If an I/O error occurs
     */
    default @NonNull ReadableSequentialData view(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        final var bytes = new byte[length];
        final var numReadBytes = readBytes(bytes);
        if (numReadBytes != length) {
            throw new BufferUnderflowException();
        }

        return Bytes.wrap(bytes).toReadableSequentialData();
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
        // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
        //noinspection DuplicatedCode
        if (remaining() < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        final byte b1 = readByte();
        final byte b2 = readByte();
        final byte b3 = readByte();
        final byte b4 = readByte();
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into an int value according to
     * specified byte order, and then increments the {@link #position()} by four.
     *
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default int readInt(@NonNull final ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            if (remaining() < Integer.BYTES) {
                throw new BufferUnderflowException();
            }
            final byte b4 = readByte();
            final byte b3 = readByte();
            final byte b2 = readByte();
            final byte b1 = readByte();
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        } else {
            return readInt();
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
        return (readInt()) & 0xFFFFFFFFL;
    }

    /**
     * Reads the next four bytes at the current {@link #position()}, composing them into an unsigned int value according
     * to specified byte order, and then increments the {@link #position()} by four.
     *
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The int value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than four bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readUnsignedInt(@NonNull final ByteOrder byteOrder) {
        return (readInt(byteOrder)) & 0xFFFFFFFFL;
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
        // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
        //noinspection DuplicatedCode
        if (remaining() < Long.BYTES) {
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
                (b8 & 255));
    }

    /**
     * Reads the next eight bytes at the current {@link #position()}, composing them into a long value according to
     * specified byte order, and then increments the {@link #position()} by eight.
     *
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
     * @return The long value at the current {@link #position()}
     * @throws BufferUnderflowException If there are fewer than eight bytes remaining
     * @throws DataAccessException if an I/O error occurs
     */
    default long readLong(@NonNull final ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            if (remaining() < Long.BYTES) {
                throw new BufferUnderflowException();
            }
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
                    (b8 & 255));
        } else {
            return readLong();
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
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
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
     * @param byteOrder the byte order, aka endian to use. Should never be null. If it is null, BIG_ENDIAN is used.
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
     * @throws BufferUnderflowException If the end of the sequence is reached before the final variable byte fragment
     *                                  is read
     * @throws DataAccessException if an I/O error occurs
     */
    default int readVarInt(final boolean zigZag) {
        long pos = position();
        long limit = limit();
        if (limit == pos) {
            return (int) readVarIntLongSlow(zigZag);
        }
        else if (limit - pos < 10) {
            return (int) readVarIntLongSlow(zigZag);
        }

        int x;
        if ((x = readByte()) >= 0) {
            return  zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if ((x ^= (readByte() << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (readByte() << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (readByte() << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = readByte();
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && readByte() < 0
                    && readByte() < 0
                    && readByte() < 0
                    && readByte() < 0
                    && readByte() < 0) {
                return (int) readVarIntLongSlow(zigZag);
            }
        }
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * Read a 64bit protobuf varint at current {@link #position()}. A long var int can be 1 to 10 bytes.
     *
     * @return long read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferUnderflowException If the end of the sequence is reached before the final variable byte fragment
     *                                  is read
     * @throws DataAccessException if an I/O error occurs
     * @throws DataEncodingException if the variable long cannot be decoded
     */
    default long readVarLong(final boolean zigZag) {
        long pos = position();
        long limit = limit();
        if (limit == pos) {
            return readVarIntLongSlow(zigZag);
        }
        else if (limit - pos < 10) {
            return readVarIntLongSlow(zigZag);
        }

        long x;
        int y;
        if ((y = readByte()) >= 0) {
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if ((y ^= (readByte() << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (readByte() << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (readByte() << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) readByte() << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) readByte() << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) readByte() << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) readByte() << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) readByte() << 56);
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56);
            if (x < 0L) {
                if (readByte() < 0L) {
                    throw new DataEncodingException("Malformed VarLong");
                }
            }
        }
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * Read a 64bit protobuf varint at current {@link #position()}. A long var int can be 1 to 10 bytes.
     * This is the slow path function.
     *
     * @return long read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferUnderflowException If the end of the sequence is reached before the final variable byte fragment
     *                                  is read
     * @throws DataAccessException if an I/O error occurs
     * @throws DataEncodingException if the variable long cannot be decoded
     */
    default long readVarIntLongSlow(final boolean zigZag) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? (result >>> 1) ^ -(result & 1) : result;
            }
        }
        throw new DataEncodingException("Malformed Varlong");
    }

    /**
     * Convenience method to get a InputStream on this ReadableSequentialData
     *
     * @return A new InputStream that reads data from this ReadableSequentialData
     */
    default InputStream asInputStream() {
        return new ReadableSequentialDataInputStream(this);
    }

    /**
     * InputStream that reads from a ReadableSequentialData
     */
    class ReadableSequentialDataInputStream extends InputStream {
        final ReadableSequentialData sequentialData;

        public ReadableSequentialDataInputStream(@NonNull final ReadableSequentialData sequentialData) {
            this.sequentialData = sequentialData;
        }

        @Override
        public int read() {
            try {
                return sequentialData.readByte();
            } catch (BufferUnderflowException e) {
                return -1;
            }
        }
        /*
         These should work but readBytes() & readBytes(byte[], int, int) do not handle EOF correctly

        @Override
        public int read(byte[] b) {
            return (int) sequentialData.readBytes(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return (int) sequentialData.readBytes(b, off, len);
        }
        */
    }
}
