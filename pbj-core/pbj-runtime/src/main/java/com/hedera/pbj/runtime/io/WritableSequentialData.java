package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A writable {@link SequentialData}. As with {@link SequentialData}, this may be backed by a stream,
 * array, buffer, or other form of sequential data.
 */
public interface WritableSequentialData extends SequentialData {

    /**
     * Writes the given byte at the current {@link #position()}, and then increments the {@link #position()}.
     *
     * @param b The byte to be written
     * @throws BufferOverflowException If this buffer's current {@link #position()} is not smaller than its
     *      {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    void writeByte(byte b) throws BufferOverflowException, UncheckedIOException;

    /**
     * Writes the given unsigned byte at the current {@link #position()}, and then increments the {@link #position()}.
     *
     * @param b The unsigned byte as an integer to be written Only the low 8 bits of the integer are used.
     * @throws BufferOverflowException If this buffer's current {@link #position()} is not smaller than its
     *      {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeUnsignedByte(final int b) throws BufferOverflowException, UncheckedIOException {
        writeByte((byte)b);
    }

    /**
     * Writes the entire content of the given source into the sequence. The {@link #position()} is then incremented by
     * {@code src.length}.
     *
     * @param src The source array to write
     * @throws BufferOverflowException If there is insufficient space before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeBytes(@NonNull final byte[] src) throws BufferOverflowException, UncheckedIOException {
        writeBytes(src, 0, src.length);
    }

    /**
     * Write {@code length} bytes from the given array, starting at the given offset in the array and at the current
     * {@link #position()} of this sequence. The {@link #position()} is then incremented by {@code length}.
     *
     * @param src The array from which bytes are to be read
     * @param offset The offset within the array of the first byte to be read; must be non-negative and no larger
     *                than {@code src.length}
     * @param length The number of bytes to be read from the given array; must be non-negative and no larger
     *                than {@code src.length - offset}
     * @throws BufferOverflowException If there is insufficient space before {@link #limit()}
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length} parameters do
     *          not hold
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeBytes(@NonNull final byte[] src, final int offset, final int length)
            throws BufferOverflowException, UncheckedIOException {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }

        for (int i = offset; i < (offset+length); i++) {
            writeByte(src[i]);
        }
    }

    /**
     * This method writes the entire content of the given {@link ByteBuffer}, all bytes between its current
     * {@link #position()} and {@link #limit()}. The {@link #position()} of this sequence is then incremented by number
     * of written bytes.
     *
     * @param src The source {@link ByteBuffer} to write, its {@link #position()} and {@link #limit()} is expected to
     *            be set correctly
     * @throws BufferOverflowException If there is insufficient space before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeBytes(@NonNull final ByteBuffer src) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < src.remaining()) {
            throw new BufferOverflowException();
        }

        while(src.hasRemaining()) {
            writeByte(src.get());
        }
    }

    /**
     * Writes the entire content of the given {@link BufferedData}, all bytes between its current
     * {@link #position()} and {@link #limit()}. The {@link #position()} of this sequence is then incremented by
     * the number of written bytes.
     *
     * @param src The source {@link BufferedData} to write
     * @throws BufferOverflowException If there is insufficient space before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeBytes(@NonNull final BufferedData src) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < src.remaining()) {
            throw new BufferOverflowException();
        }

        while(src.hasRemaining()) {
            writeByte(src.readByte());
        }
    }

    /**
     * This method writes the entire content of the given {@link RandomAccessData}. The
     * {@link #position()} is then incremented by {@code src.length()}.
     *
     * @param src The source {@link RandomAccessData} with bytes to be written to this sequence
     * @throws BufferOverflowException If there is insufficient space before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeBytes(@NonNull final RandomAccessData src) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < src.length()) {
            throw new BufferOverflowException();
        }

        for (int i = 0; i < src.length(); i++) {
            writeByte(src.getByte(i));
        }
    }

    /**
     * Writes the bytes from the given {@link java.io.InputStream} into this {@link WritableSequentialData}.
     * The {@link #position()} is then incremented by the number of bytes written, which is also returned.
     * If the end-of-stream was reached without reading data, then no change is made to the {@link #position()}
     * and 0 is returned. There is no guarantee that we will read from the stream completely, once we get to the
     * {@link #limit()}, we will read no more from the stream.
     *
     * @param src The source {@link java.io.InputStream} to read bytes from
     * @param maxLength The maximum number of bytes to read from the {@link java.io.InputStream}. If the
     *            stream does not have this many bytes, then only those bytes available, if any,
     *            are read. If maxLength is 0 or less, then nothing is read and 0 is returned.
     * @return The number of bytes read from the stream, or 0 if the end of stream was reached without reading bytes.
     * @throws IllegalArgumentException if {@code len} is negative
     * @throws UncheckedIOException if an I/O error occurs
     */
    default int writeBytes(@NonNull final InputStream src, final int maxLength) throws UncheckedIOException {
        // Check for a bad length or a null src
        Objects.requireNonNull(src);
        if (maxLength < 0) {
            throw new IllegalArgumentException("The length must be >= 0");
        }

        // If the length is zero, then we have nothing to read
        if (maxLength == 0) {
            return 0;
        }

        // We are going to read from the input stream up to either "len" or the number of bytes
        // remaining in this DataOutput, whichever is lesser.
        final long numBytesToRead = Math.min(maxLength, remaining());
        if (numBytesToRead == 0) {
            return 0;
        }

        // In this default implementation, we use a small buffer for reading from the stream.
        try {
            final var buf = new byte[8192];
            int totalBytesRead = 0;
            while (totalBytesRead < numBytesToRead) {
                final var maxBytesToRead = Math.toIntExact(Math.min(numBytesToRead - totalBytesRead, buf.length));
                final var numBytesRead = src.read(buf, 0, maxBytesToRead);
                if (numBytesRead == -1) {
                    return totalBytesRead;
                }

                totalBytesRead += numBytesRead;
                writeBytes(buf, 0, numBytesRead);
            }
            return totalBytesRead;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read from InputStream", ex);
        }
    }

    /**
     * Write a string as UTF8 bytes to this {@link WritableSequentialData}.
     *
     * @param value The string to write, can not be null
     */
    default void writeUTF8(@NonNull final String value) {
        writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * {@link #position()}, and then increments the {@link #position()} by four.
     *
     * @param value The int value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeInt(final int value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Integer.BYTES) {
            throw new BufferOverflowException();
        }
        writeByte((byte)(value >>> 24));
        writeByte((byte)(value >>> 16));
        writeByte((byte)(value >>>  8));
        writeByte((byte)(value));
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * {@link #position()}, and then increments the {@link #position()} by four.
     *
     * @param value The int value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeInt(final int value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeInt(value);
        } else {
            if (remaining() < Integer.BYTES) {
                throw new BufferOverflowException();
            }
            writeByte((byte) (value));
            writeByte((byte) (value >>> 8));
            writeByte((byte) (value >>> 16));
            writeByte((byte) (value >>> 24));
        }
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * {@link #position()}, and then increments the {@link #position()} by four.
     *
     * @param value The int value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeUnsignedInt(final long value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Integer.BYTES) {
            throw new BufferOverflowException();
        }
        writeByte((byte)(value >>> 24));
        writeByte((byte)(value >>> 16));
        writeByte((byte)(value >>>  8));
        writeByte((byte)(value));
    }

    /**
     * Writes four bytes containing the given int value, in the standard Java big-endian byte order, at the current
     * {@link #position()}, and then increments the {@link #position()} by four.
     *
     * @param value The int value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeUnsignedInt(final long value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeUnsignedInt(value);
        } else {
            if (remaining() < Integer.BYTES) {
                throw new BufferOverflowException();
            }
            writeByte((byte) (value));
            writeByte((byte) (value >>> 8));
            writeByte((byte) (value >>> 16));
            writeByte((byte) (value >>> 24));
        }
    }

    /**
     * Writes eight bytes containing the given long value, in the standard Java big-endian  byte order at the current
     * {@link #position()}, and then increments the {@link #position()} by eight.
     *
     * @param value The long value to be written
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeLong(final long value) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < Long.BYTES) {
            throw new BufferOverflowException();
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
     * Writes eight bytes containing the given long value, in the specified byte order at the current  {@link #position()}, and
     * then increments the {@link #position()} by eight.
     *
     * @param value The long value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeLong(final long value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            writeLong(value);
        } else {
            if (remaining() < Long.BYTES) {
                throw new BufferOverflowException();
            }
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
     * {@link #position()}, and then increments the {@link #position()} by four.
     *
     * @param value The float value to be written
     * @throws BufferOverflowException If there are fewer than four bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeFloat(final float value) throws BufferOverflowException, UncheckedIOException {
        writeInt(Float.floatToIntBits(value));
    }

    /**
     * Writes four bytes containing the given float value, in the specified byte order at the current {@link #position()}, and then
     * increments the {@link #position()} by four.
     *
     * @param value The float value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than four bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeFloat(final float value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        writeInt(Float.floatToIntBits(value), byteOrder);
    }

    /**
     * Writes eight bytes containing the given double value, in the standard Java big-endian byte order at the current
     * {@link #position()}, and then increments the {@link #position()} by eight.
     *
     * @param value The double value to be written
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeDouble(final double value) throws BufferOverflowException, UncheckedIOException {
        writeLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes eight bytes containing the given double value, in the specified byte order at the current {@link #position()}, and
     * then increments the {@link #position()} by eight.
     *
     * @param value The double value to be written
     * @param byteOrder the byte order, aka endian to use
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeDouble(final double value, @NonNull final ByteOrder byteOrder)
            throws BufferOverflowException, UncheckedIOException {
        writeLong(Double.doubleToLongBits(value), byteOrder);
    }

    /**
     * Write a 32bit protobuf varint at current {@link #position()}.
     *
     * <p>Non-negative integer var int can be 1 to 5 bytes. Negative integer with zigZag=false is
     * always 10 bytes. Negative integer with zigZag=true can be 1 to 5 bytes.
     *
     * @param value integer to write in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeVarInt(final int value, final boolean zigZag)
            throws BufferOverflowException, UncheckedIOException {
        writeVarLong(value, zigZag);
    }

    /**
     * Write a 64bit protobuf varint at current {@link #position()}. A long var int can be 1 to 10 bytes.
     *
     * @param value long to write in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferOverflowException If there are fewer than eight bytes remaining before {@link #limit()}
     * @throws UncheckedIOException if an I/O error occurs
     */
    default void writeVarLong(long value, final boolean zigZag)
            throws BufferOverflowException, UncheckedIOException {
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
