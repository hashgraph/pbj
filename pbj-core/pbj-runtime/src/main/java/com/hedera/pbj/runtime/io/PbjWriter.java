// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static java.lang.Character.MAX_SURROGATE;
import static java.lang.Character.MIN_SURROGATE;
import static java.lang.Character.isSurrogatePair;
import static java.lang.Character.toCodePoint;

import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 * A buffer-backed writer for encoding protobuf data. Not thread safe.
 *
 * <p>PbjWriter maintains an internal byte buffer and writes to an {@link OutputStream},
 * {@link WritableSequentialData}, or a fixed byte array. When backed by an output stream the
 * buffer is flushed automatically when full. When not backed by a stream the buffer grows as
 * needed (unless constructed with {@code mayGrow = false}).
 *
 * <p>Errors are tracked internally rather than thrown immediately. Call {@link #error()} to check
 * for a pending error code, or {@link #throwOnError()} to surface it as an exception. Once an
 * error is set it is sticky — subsequent write calls become no-ops.
 *
 * <p>Implements {@link AutoCloseable}: closing flushes pending bytes to the underlying stream.
 */
public class PbjWriter implements AutoCloseable {
    private byte[] buf;
    private int pos, cap;
    private int offset, err;
    private RuntimeException cause;
    private OutputStream output;
    private boolean reuseable;
    private boolean mayGrow = true;

    private static final boolean useStacktrace =
            !"false".equalsIgnoreCase(System.getProperty("pbj.ReaderWriter.useStackTrace"));
    public static final int EOF = PbjReader.EOF,
            DataEncoding = PbjReader.DataEncoding,
            BufferUnderflow = PbjReader.BufferUnderflow,
            Parse = PbjReader.Parse,
            IllegalArgument = PbjReader.IllegalArgument,
            IOError = PbjReader.IOError,
            Unsupported = PbjReader.Unsupported,
            UsageError = PbjReader.UsageError,
            UnknownField = PbjReader.UnknownField,
            BufferOverflow = PbjReader.BufferOverflow,
            MaxDepthReached = PbjReader.MaxDepthReached,
            // For PbjWriter
            Closed = PbjReader.Closed,
            MalformString = PbjReader.MalformString;

    private static final RuntimeException premadeRuntimeException;

    static {
        premadeRuntimeException = new RuntimeException("Stacktrace not enabled in PbjWriter");
    }

    /**
     * Creates a writer that streams output to the given {@link OutputStream}.
     * An internal 16 KB buffer is used; the buffer is flushed to the stream when full.
     *
     * @param output the output stream to write to
     */
    public PbjWriter(@NonNull OutputStream output) {
        this.output = output;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
        reuseable = true;
    }

    /**
     * Creates a writer backed by a {@link ByteBuffer}.
     *
     * <p>If the buffer has a backing array it is used directly. Otherwise an internal 16 KB
     * streaming buffer is used and bytes are forwarded to the {@link ByteBuffer} on flush.
     *
     * @param buffer the target byte buffer
     */
    public PbjWriter(ByteBuffer buffer) {
        if (buffer.hasArray()) {
            buf = buffer.array();
            pos = buffer.arrayOffset() + buffer.position();
            cap = buffer.arrayOffset() + buffer.limit();
        } else {
            this.output = new OutputStream() {
                @Override
                public void write(int b) {
                    buffer.put((byte) b);
                }

                @Override
                public void write(@NonNull byte[] b, int off, int len) {
                    buffer.put(b, off, len);
                }
            };
            buf = new byte[16 << 10];
            cap = buf.length;
            reuseable = true;
        }
    }

    /**
     * Creates a writer that writes directly into the given byte array starting at {@code pos},
     * writing up to the end of the array. No flushing or growing occurs.
     *
     * @param buffer the backing byte array
     * @param pos    the starting write position within the array
     */
    public PbjWriter(byte[] buffer, int pos) {
        this.buf = buffer;
        this.pos = pos;
        this.cap = buffer.length;
    }

    /**
     * Creates a writer that streams output to the given {@link WritableSequentialData}.
     * An internal 16 KB buffer is used; the buffer is flushed to the target when full.
     *
     * @param output the writable sequential data target
     */
    public PbjWriter(@NonNull WritableSequentialData output) {
        this(new OutputStream() {
            @Override
            public void write(int b) {
                output.writeByte((byte) b);
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) {
                output.writeBytes(b, off, len);
            }
        });
    }

    /**
     * Creates a standalone, growable writer with an initial 16 KB internal buffer.
     * No backing output stream is attached; use {@link #toByteArray()} to retrieve the written bytes.
     */
    public PbjWriter() {
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
        reuseable = true;
    }

    /**
     * Creates a growable (or fixed-size) standalone writer with the specified initial capacity.
     *
     * @param reserveSize the initial buffer capacity in bytes; if {@code mayGrow} is {@code true}
     *                    the capacity is at least 16 KB regardless of this value
     * @param mayGrow     {@code true} to allow the internal buffer to grow automatically;
     *                    {@code false} to keep the buffer fixed at {@code reserveSize} bytes
     */
    public PbjWriter(int reserveSize, boolean mayGrow) {
        if (mayGrow) buf = new byte[Math.max(reserveSize, 16 << 10)]; // 16k is friendly to x86-64 L1 cache
        else {
            buf = new byte[reserveSize];
        }
        cap = buf.length;
        reuseable = true;
        this.mayGrow = mayGrow;
    }

    /**
     * Ensures that at least {@code len} bytes of space are available starting at the current
     * position, flushing or growing the internal buffer if necessary.
     *
     * @param len the number of bytes to reserve
     */
    public void reserveRel(int len) {
        if (pos + len <= cap) return;
        flushOrGrow(len);
    }

    /**
     * Advances the write position by {@code len} bytes without writing any data.
     * Used to reserve placeholder space that will be filled in later via {@link #writeAtUnsafe}.
     *
     * @param len the number of bytes to skip over
     */
    public void placehold(int len) {
        pos += len;
    }

    /**
     * Returns the current absolute write position, accounting for any bytes already flushed
     * to the underlying output stream.
     *
     * @return the current write position as a non-negative integer
     */
    public int position() {
        return offset + pos;
    }

    /**
     * Overwrites a single byte at the given absolute position without advancing the write cursor.
     * Used to patch in placeholder bytes reserved earlier via {@link #placehold}.
     *
     * @param pos   the absolute write position to patch
     * @param value the byte value to write
     */
    public void writeAtUnsafe(int pos, byte value) {
        buf[pos - offset] = value;
    }

    /**
     * Expands a 1-byte varint placeholder at the given absolute position into a 2-byte varint
     * and shifts all subsequent bytes forward by one. The placeholder must have been reserved
     * via {@link #placehold(int)}.
     *
     * @param position the absolute position of the 1-byte placeholder
     */
    public void reinsertVarInt(int position) {
        int relPos = position - offset;
        int len = this.pos - relPos - 1;
        System.arraycopy(buf, relPos + 1, buf, relPos + 2, len);
        buf[relPos] = (byte) ((len & 0x7F) | 0x80);
        buf[relPos + 1] = (byte) (len >>> 7);
        this.pos++;
    }

    /**
     * Writes a boolean as a single byte ({@code 1} for {@code true}, {@code 0} for {@code false}).
     *
     * @param b the boolean value to write
     */
    public void writeBoolean(boolean b) {
        writeByte((byte) (b ? 1 : 0));
    }

    /**
     * Writes a single signed byte.
     *
     * @param b the byte to write
     */
    public void writeByte(byte b) {
        if (pos < cap) {
            buf[pos++] = b;
            return;
        }
        writeByteInternal(b);
    }

    private void writeByteInternal(byte b) {
        flushOrGrow(1);
        buf[pos++] = b;
    }

    /**
     * Writes two bytes in sequence.
     *
     * @param b1 the first byte
     * @param b2 the second byte
     */
    public void writeByte2(byte b1, byte b2) {
        if (pos + 2 <= cap) {
            buf[pos] = b1;
            buf[pos + 1] = b2;
            pos += 2;
            return;
        }
        writeByte2Internal(b1, b2);
    }

    private void writeByte2Internal(byte b1, byte b2) {
        flushOrGrow(2);
        buf[pos] = b1;
        buf[pos + 1] = b2;
        pos += 2;
    }

    /**
     * Writes three bytes in sequence.
     *
     * @param b1 the first byte
     * @param b2 the second byte
     * @param b3 the third byte
     */
    public void writeByte3(byte b1, byte b2, byte b3) {
        if (pos + 3 <= cap) {
            buf[pos] = b1;
            buf[pos + 1] = b2;
            buf[pos + 2] = b3;
            pos += 3;
            return;
        }
        writeByte3Internal(b1, b2, b3);
    }

    private void writeByte3Internal(byte b1, byte b2, byte b3) {
        flushOrGrow(3);
        buf[pos] = b1;
        buf[pos + 1] = b2;
        buf[pos + 2] = b3;
        pos += 3;
    }

    /**
     * Writes four bytes in sequence.
     *
     * @param b1 the first byte
     * @param b2 the second byte
     * @param b3 the third byte
     * @param b4 the fourth byte
     */
    public void writeByte4(byte b1, byte b2, byte b3, byte b4) {
        if (pos + 4 <= cap) {
            buf[pos] = b1;
            buf[pos + 1] = b2;
            buf[pos + 2] = b3;
            buf[pos + 3] = b4;
            pos += 4;
            return;
        }
        writeByte4Internal(b1, b2, b3, b4);
    }

    private void writeByte4Internal(byte b1, byte b2, byte b3, byte b4) {
        flushOrGrow(4);
        buf[pos] = b1;
        buf[pos + 1] = b2;
        buf[pos + 2] = b3;
        buf[pos + 3] = b4;
        pos += 4;
    }

    /**
     * Writes all remaining bytes from the given {@link BufferedData}, advancing its position.
     *
     * @param src the source buffer; all {@link BufferedData#remaining()} bytes are written
     */
    public void writeBytes(@NonNull final BufferedData src) {
        int len = (int) src.remaining();
        if (len <= 0) return;
        long srcPos = src.position();
        if (pos + len <= cap) {
            src.getBytes(srcPos, buf, pos, len);
            src.skip(len);
            pos += len;
            return;
        }
        writeBytesBDInternal(src, len, srcPos);
    }

    private void writeBytesBDInternal(BufferedData src, int len, long srcPos) {
        if (output == null) {
            flushOrGrow(len); // to grow at least to pos + len
            src.getBytes(srcPos, buf, pos, len);
            src.skip(len);
            pos += len;
        } else {
            int remaining = len;
            while (remaining > 0) {
                if (pos == cap) {
                    try {
                        output.write(buf, 0, pos);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    offset += pos;
                    pos = 0;
                }
                int chunk = Math.min(remaining, cap - pos);
                src.getBytes(srcPos, buf, pos, chunk);
                pos += chunk;
                srcPos += chunk;
                remaining -= chunk;
            }
            src.skip(len);
        }
    }

    /**
     * Writes all bytes from the given array.
     *
     * @param src the source byte array
     */
    public void writeBytes(@NonNull byte[] src) {
        writeBytes(src, 0, src.length);
    }

    /**
     * Writes {@code length} bytes from the given array starting at {@code offset}.
     *
     * @param src    the source byte array
     * @param offset the start index within {@code src}
     * @param length the number of bytes to write
     */
    public void writeBytes(@NonNull byte[] src, int offset, int length) {
        if (length <= 0) return;
        if (pos + length <= cap && (output == null || length < 2048)) {
            System.arraycopy(src, offset, buf, pos, length);
            pos += length;
            return;
        }
        writeBytesInternal(src, offset, length);
    }

    private void writeBytesInternal(byte[] src, int srcOffset, int length) {
        if (output != null && length >= 2048) {
            if (pos > 0) {
                try {
                    output.write(buf, 0, pos);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                offset += pos;
                pos = 0;
            }
            try {
                output.write(src, srcOffset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            offset += length;
            return;
        }
        flushOrGrow(length);
        System.arraycopy(src, srcOffset, buf, pos, length);
        pos += length;
    }

    /**
     * Writes all bytes from the given {@link RandomAccessData}.
     *
     * @param src the source data
     */
    public void writeBytes(@NonNull RandomAccessData src) {
        int len = (int) src.length();
        if (len <= 0) return;
        if (pos + len <= cap) {
            src.getBytes(0, buf, pos, len);
            pos += len;
            return;
        }
        writeBytesRAInternal(src, len);
    }

    private void writeBytesRAInternal(RandomAccessData src, int len) {
        if (output == null) {
            flushOrGrow(len);
            src.getBytes(0, buf, pos, len);
            pos += len;
        } else {
            // Maybe the below can be improved. This path seems rare
            int srcOffset = 0;
            int remaining = len;
            while (remaining > 0) {
                if (pos == cap) {
                    try {
                        output.write(buf, 0, pos);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    offset += pos;
                    pos = 0;
                }
                int chunk = Math.min(remaining, cap - pos);
                src.getBytes(srcOffset, buf, pos, chunk);
                pos += chunk;
                srcOffset += chunk;
                remaining -= chunk;
            }
        }
    }

    /**
     * Writes a 32-bit integer in big-endian byte order. Alias for {@link #writeIntBE(int)}.
     *
     * @param value the integer to write
     */
    public void writeInt(int value) {
        writeIntBE(value);
    }

    /**
     * Writes a 32-bit integer in big-endian byte order (most significant byte first).
     *
     * @param value the integer to write
     */
    public void writeIntBE(int value) {
        if (pos + 4 <= cap) {
            buf[pos] = (byte) (value >>> 24);
            buf[pos + 1] = (byte) (value >>> 16);
            buf[pos + 2] = (byte) (value >>> 8);
            buf[pos + 3] = (byte) value;
            pos += 4;
            return;
        }
        writeIntBEInternal(value);
    }

    private void writeIntBEInternal(int value) {
        flushOrGrow(4);
        buf[pos] = (byte) (value >>> 24);
        buf[pos + 1] = (byte) (value >>> 16);
        buf[pos + 2] = (byte) (value >>> 8);
        buf[pos + 3] = (byte) value;
        pos += 4;
    }

    /**
     * Writes a 32-bit integer in little-endian byte order (least significant byte first).
     *
     * @param value the integer to write
     */
    public void writeIntLE(int value) {
        if (pos + 4 <= cap) {
            buf[pos] = (byte) value;
            buf[pos + 1] = (byte) (value >>> 8);
            buf[pos + 2] = (byte) (value >>> 16);
            buf[pos + 3] = (byte) (value >>> 24);
            pos += 4;
            return;
        }
        writeIntLEInternal(value);
    }

    private void writeIntLEInternal(int value) {
        flushOrGrow(4);
        buf[pos] = (byte) value;
        buf[pos + 1] = (byte) (value >>> 8);
        buf[pos + 2] = (byte) (value >>> 16);
        buf[pos + 3] = (byte) (value >>> 24);
        pos += 4;
    }

    /**
     * Writes a 64-bit integer in little-endian byte order (least significant byte first).
     *
     * @param value the long to write
     */
    public void writeLongLE(long value) {
        if (pos + 8 <= cap) {
            buf[pos] = (byte) value;
            buf[pos + 1] = (byte) (value >>> 8);
            buf[pos + 2] = (byte) (value >>> 16);
            buf[pos + 3] = (byte) (value >>> 24);
            buf[pos + 4] = (byte) (value >>> 32);
            buf[pos + 5] = (byte) (value >>> 40);
            buf[pos + 6] = (byte) (value >>> 48);
            buf[pos + 7] = (byte) (value >>> 56);
            pos += 8;
            return;
        }
        writeLongLEInternal(value);
    }

    private void writeLongLEInternal(long value) {
        flushOrGrow(8);
        buf[pos] = (byte) value;
        buf[pos + 1] = (byte) (value >>> 8);
        buf[pos + 2] = (byte) (value >>> 16);
        buf[pos + 3] = (byte) (value >>> 24);
        buf[pos + 4] = (byte) (value >>> 32);
        buf[pos + 5] = (byte) (value >>> 40);
        buf[pos + 6] = (byte) (value >>> 48);
        buf[pos + 7] = (byte) (value >>> 56);
        pos += 8;
    }

    /**
     * Writes a 32-bit float in big-endian byte order. Alias for {@link #writeFloatBE(float)}.
     *
     * @param value the float to write
     */
    public void writeFloat(float value) {
        writeFloatBE(value);
    }

    /**
     * Writes a 32-bit float in big-endian byte order using {@link Float#floatToRawIntBits}.
     *
     * @param value the float to write
     */
    public void writeFloatBE(float value) {
        writeIntBE(Float.floatToRawIntBits(value));
    }

    /**
     * Writes a 32-bit float in little-endian byte order using {@link Float#floatToRawIntBits}.
     *
     * @param value the float to write
     */
    public void writeFloatLE(float value) {
        writeIntLE(Float.floatToRawIntBits(value));
    }

    /**
     * Writes a 64-bit double in big-endian byte order. Alias for {@link #writeDoubleBE(double)}.
     *
     * @param value the double to write
     */
    public void writeDouble(double value) {
        writeDoubleBE(value);
    }

    /**
     * Writes a 64-bit double in big-endian byte order using {@link Double#doubleToRawLongBits}.
     *
     * @param value the double to write
     */
    public void writeDoubleBE(double value) {
        writeLongBE(Double.doubleToRawLongBits(value));
    }

    /**
     * Writes a 64-bit double in little-endian byte order using {@link Double#doubleToRawLongBits}.
     *
     * @param value the double to write
     */
    public void writeDoubleLE(double value) {
        writeLongLE(Double.doubleToRawLongBits(value));
    }

    /**
     * Writes a 64-bit integer in big-endian byte order. Alias for {@link #writeLongBE(long)}.
     *
     * @param value the long to write
     */
    public void writeLong(long value) {
        writeLongBE(value);
    }

    /**
     * Writes a 64-bit integer in big-endian byte order (most significant byte first).
     *
     * @param value the long to write
     */
    public void writeLongBE(long value) {
        if (pos + 8 <= cap) {
            buf[pos] = (byte) (value >>> 56);
            buf[pos + 1] = (byte) (value >>> 48);
            buf[pos + 2] = (byte) (value >>> 40);
            buf[pos + 3] = (byte) (value >>> 32);
            buf[pos + 4] = (byte) (value >>> 24);
            buf[pos + 5] = (byte) (value >>> 16);
            buf[pos + 6] = (byte) (value >>> 8);
            buf[pos + 7] = (byte) value;
            pos += 8;
            return;
        }
        writeLongBEInternal(value);
    }

    private void writeLongBEInternal(long value) {
        flushOrGrow(8);
        buf[pos] = (byte) (value >>> 56);
        buf[pos + 1] = (byte) (value >>> 48);
        buf[pos + 2] = (byte) (value >>> 40);
        buf[pos + 3] = (byte) (value >>> 32);
        buf[pos + 4] = (byte) (value >>> 24);
        buf[pos + 5] = (byte) (value >>> 16);
        buf[pos + 6] = (byte) (value >>> 8);
        buf[pos + 7] = (byte) value;
        pos += 8;
    }

    /**
     * Writes an {@code int} as a zigzag-encoded varint. Negative values are sign-extended to
     * 64 bits before encoding, producing the 10-byte wire format required by the protobuf spec.
     *
     * @param value the signed int to encode
     */
    public void writeVarIntZZ(int value) {
        writeVarLongZZ(value);
    }

    /**
     * Writes a {@code long} as a zigzag-encoded varint. The value is mapped via
     * {@code (value << 1) ^ (value >> 63)} before varint encoding so that small negative
     * numbers require few bytes.
     *
     * @param value the signed long to encode
     */
    public void writeVarLongZZ(long value) {
        writeVarLongNoZZ((value << 1) ^ (value >> 63));
    }

    /**
     * Writes an {@code int} as a base-128 varint with optional zigzag encoding.
     *
     * @param value  the value to encode
     * @param zigZag if {@code true}, applies zigzag encoding before varint encoding
     */
    public void writeVarInt(int value, boolean zigZag) {
        long v = zigZag ? ((long) value << 1) ^ ((long) value >> 63) : value;
        writeVarLongNoZZ(v);
    }

    /**
     * Writes a {@code long} as a base-128 varint with optional zigzag encoding.
     *
     * @param value  the value to encode
     * @param zigZag if {@code true}, applies zigzag encoding before varint encoding
     */
    public void writeVarLong(long value, boolean zigZag) {
        long v = zigZag ? (value << 1) ^ (value >> 63) : value;
        writeVarLongNoZZ(v);
    }

    private void writeVarLongInternal(long v) {
        flushOrGrow(10);
        while ((v & ~0x7FL) != 0) {
            buf[pos++] = (byte) (((int) v & 0x7F) | 0x80);
            v >>>= 7;
        }
        buf[pos++] = (byte) v;
    }

    /**
     * Writes an {@code int} as a base-128 varint without zigzag encoding.
     * The value is zero-extended to 64 bits before encoding.
     *
     * @param value the value to encode
     */
    public void writeVarIntNoZZ(int value) {
        writeVarLongNoZZ(value);
    }

    /**
     * Writes a {@code long} as a base-128 varint without zigzag encoding.
     * Each 7-bit group is written as a byte with the high bit set if more bytes follow.
     *
     * @param v the value to encode
     */
    public void writeVarLongNoZZ(long v) {
        if (pos + 10 <= cap) {
            while ((v & ~0x7FL) != 0) {
                buf[pos++] = (byte) (((int) v & 0x7F) | 0x80);
                v >>>= 7;
            }
            buf[pos++] = (byte) v;
            return;
        }
        writeVarLongInternal(v);
    }

    /**
     * Flushes all buffered bytes to the underlying output stream and resets the internal buffer.
     *
     * <p>After the flush the internal write position is reset to zero, and the absolute byte
     * offset is advanced by the number of bytes written. This is a no-op when no output stream
     * is attached (standalone writers). Any pending error is surfaced before writing.
     *
     * @throws RuntimeException     if a prior error was recorded on this writer
     * @throws UncheckedIOException if the underlying stream throws an {@link java.io.IOException}
     */
    public void flush() {
        throwOnError();
        if (output == null) return;
        try {
            output.write(buf, 0, pos);
            output.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        offset += pos;
        pos = 0;
    }

    /**
     * Flushes any remaining bytes to the underlying output stream and closes it.
     * If no output stream is attached this method does nothing.
     */
    @Override
    public void close() {
        if (output == null) return;
        flush();
        try {
            output.close();
        } catch (IOException ex) {
            setError(IOError, ex.getMessage());
        }
        err = Closed;
    }

    private void flushOrGrow(int minLength) {
        if (output != null) {
            if (minLength > cap) {
                setError(IOError, "minLength is greater than capacity");
                return;
            }
            try {
                output.write(buf, 0, pos);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            offset += pos;
            pos = 0;
        } else if (reuseable && mayGrow) {
            int power2Capacity = (int) 2L << (63 - Long.numberOfLeadingZeros(Math.max(buf.length, pos + minLength)));
            byte[] newBuf = new byte[power2Capacity];
            System.arraycopy(buf, 0, newBuf, 0, pos);
            buf = newBuf;
            cap = buf.length;
        }
        // A possible else case is using a byte array and trying to reserve (or grow) past the length of it
        // reserving shouldn't cause a throw so this else is ignored
    }

    /**
     * Flushes any buffered output and resets this writer's position, offset, and error state,
     * leaving it ready for reuse while keeping the same output destination.
     */
    public void reset() {
        flush();
        pos = 0;
        offset = 0;
        err = 0;
        cause = null;
    }

    /**
     * Resets this writer and detaches it from any output stream, allowing subsequent use as
     * a standalone in-memory writer.
     */
    public void resetWithNull() {
        resetWith((OutputStream) null);
    }

    /**
     * Resets this writer and redirects output to a new {@link OutputStream}.
     * Only valid on writers that were originally created with an output stream.
     * Sets the error code to {@link #UsageError} if called on a non-reuseable writer.
     *
     * @param out the new output stream
     */
    public void resetWith(OutputStream out) {
        reset();
        if (!reuseable) {
            setError(UsageError, "resetWith on non-reuseable PbjWriter");
            return;
        }
        output = out;
    }

    /**
     * Resets this writer and redirects output to a new {@link WritableSequentialData}.
     * Only valid on writers that were originally created with an output stream.
     *
     * @param out the new writable sequential data target
     */
    public void resetWith(@NonNull WritableSequentialData out) {
        resetWith(new OutputStream() {
            @Override
            public void write(int b) {
                out.writeByte((byte) b);
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) {
                out.writeBytes(b, off, len);
            }
        });
    }

    /**
     * Returns the raw internal byte array. The valid data occupies indices {@code [0, position())}.
     * Intended for low-level inspection; prefer {@link #toByteArray()} for a correctly sized copy
     *
     * @return the internal buffer array
     */
    public byte[] internalArray() {
        return buf;
    }

    /**
     * Returns a zero-copy {@link Bytes} view wrapping the internal buffer from index 0 up to
     * the current position. The backing array is shared, so the returned {@code Bytes} must
     * not be retained beyond the next write operation.
     *
     * @return a {@code Bytes} view of the current contents
     */
    public Bytes internalArrayWrapped() {
        return Bytes.wrap(buf, 0, pos);
    }

    /**
     * Returns a {@link Bytes} wrapping a fresh copy of the written bytes.
     * Only valid on standalone (non-streaming) writers; sets {@link #UsageError} and returns
     * {@link Bytes#EMPTY} if called on a streaming writer.
     *
     * @return the written bytes wrapped in a {@code Bytes} instance
     */
    public Bytes toByteArrayWrapped() {
        if (output != null) {
            setError(UsageError, "toByteArrayWrapped used on a streaming object");
            return Bytes.EMPTY;
        }
        return Bytes.wrap(toByteArray());
    }

    /**
     * Returns a newly allocated byte array containing exactly the bytes written so far.
     * Only valid on standalone (non-streaming) writers; sets {@link #UsageError} and returns
     * {@code null} if called on a streaming writer.
     *
     * @return a copy of the written bytes, or {@code null} on error
     */
    public byte[] toByteArray() {
        if (output != null) {
            setError(UsageError, "toByteArray used on a streaming object");
            return null;
        }
        byte[] bytes = new byte[pos];
        System.arraycopy(buf, 0, bytes, 0, pos);
        return bytes;
    }

    /**
     * Creates a {@link PbjReader} backed by the current internal buffer contents.
     * Useful for reading back data written to a standalone writer without copying.
     * Only valid on standalone (non-streaming) writers; sets {@link #UsageError} and returns
     * {@code null} if called on a streaming writer.
     *
     * @return a new {@code PbjReader} positioned at the start of the written bytes, or {@code null} on error
     */
    public PbjReader toPbjReader() {
        if (output != null) {
            setError(UsageError, "toPbjReader on a streaming object");
            return null;
        }
        return new PbjReader(buf, 0, pos);
    }

    /**
     * Records an error on this writer if no previous error is set. Once an error is recorded
     * subsequent writes become no-ops.
     *
     * @param errorKind one of the error-code constants ({@link #IOError}, {@link #UsageError}, etc.)
     * @param message   a message returned with an exception
     */
    public void setError(int errorKind, String message) {
        if (err > 0) return;
        err = errorKind;
        if (useStacktrace) {
            cause = new RuntimeException(message);
        } else {
            cause = premadeRuntimeException;
        }
    }

    /**
     * Returns the current error code, or {@code 0} if no error has occurred.
     *
     * @return a positive error-code constant, or {@code 0} for no error
     */
    public int error() {
        return err > 0 ? err : 0;
    }

    /**
     * Throws the recorded error as a {@link RuntimeException} if an error is set.
     *
     * @throws RuntimeException if {@link #error()} is non-zero
     */
    public void throwOnError() {
        if (err > 0) {
            throw cause;
        }
    }

    /**
     * Returns the exception that was recorded when the current error was set, or {@code null}
     * if no error has occurred.
     *
     * @return the recorded exception, or {@code null}
     */
    public RuntimeException getCause() {
        return cause;
    }

    /**
     * Writes a UTF-8 encoded string without a preceding length varint.
     * Surrogate pairs are encoded as a 4-byte UTF-8 sequence. An unpaired surrogate sets
     * the error code to {@link #MalformString}.
     *
     * @param str the string to encode
     */
    public void writeStringNoTag(String str) {
        int inLength = str.length();
        for (int i = 0; i < inLength; ++i) {
            char c = str.charAt(i);
            if (c < 0x80) {
                writeByte((byte) c);
            } else if (c < 0x800) {
                writeByte2((byte) (0xC0 | (c >>> 6)), (byte) (0x80 | (0x3F & c)));
            } else if (c < MIN_SURROGATE || MAX_SURROGATE < c) {
                writeByte3((byte) (0xE0 | (c >>> 12)), (byte) (0x80 | (0x3F & (c >>> 6))), (byte) (0x80 | (0x3F & c)));
            } else {
                char low;
                if (i + 1 == inLength || !isSurrogatePair(c, (low = str.charAt(++i)))) {
                    setError(MalformString, "Unpaired surrogate at index " + i + " of " + inLength);
                    return;
                }
                int codePoint = toCodePoint(c, low);
                writeByte4(
                        (byte) ((0xF << 4) | (codePoint >>> 18)),
                        (byte) (0x80 | (0x3F & (codePoint >>> 12))),
                        (byte) (0x80 | (0x3F & (codePoint >>> 6))),
                        (byte) (0x80 | (0x3F & codePoint)));
            }
        }
    }

    /**
     * Writes a UTF-8 encoded string preceded by its length as a base-128 varint, as required
     * by the protobuf wire format for {@code TYPE_STRING} fields.
     *
     * @param str the string to encode
     */
    public void writeStringWithTag(String str) {
        int inLength = str.length();
        if (inLength > 0x7F) {
            writeUTF8_2byte(str);
            return;
        }
        // fast path 1 byte tag case
        reserveRel(0x7F * 4 + 2); // worse case size
        int pos = position();
        placehold(1);
        writeStringNoTag(str);
        int endPos = position();
        int utf8Len = endPos - pos - 1;
        if (utf8Len <= 0x7F) {
            writeAtUnsafe(pos, (byte) utf8Len);
        } else {
            reinsertVarInt(pos);
        }
    }

    private void writeUTF8_2byte(String str) {
        // buffer is 16k, string is UTF16, so worst case is len*3.
        // 5460 was picked bc its (16k - 2byte tag) / 3 byte worse case
        if (str.length() > 5460) {
            // Can't fit in buffer, todo check if we'll grow anyways
            // I don't think anything hits this case?
            // These two lines counts the length then write the length, making this 2 pass
            writeVarIntNoZZ(ProtoWriterTools.sizeOfStringNoTag(str));
            writeStringNoTag(str);
            return;
        }
        reserveRel(str.length() * 3 + 2);
        int pos = position();
        placehold(2);
        writeStringNoTag(str);
        int utf8Len = position() - pos - 2;
        writeAtUnsafe(pos, (byte) ((utf8Len & 0x7F) | 0x80));
        writeAtUnsafe(pos + 1, (byte) (utf8Len >>> 7));
    }

    /**
     * Advances the write position by {@code count} bytes without writing any data.
     *
     * @param count the number of bytes to skip
     */
    public void skip(int count) {
        pos += count;
    }
}
