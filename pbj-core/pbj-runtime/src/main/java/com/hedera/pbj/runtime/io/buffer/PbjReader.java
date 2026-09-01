// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A buffer-backed reader for decoding data. Not thread safe.
 *
 * <p>PbjReader maintains an internal byte buffer and reads from a {@link ReadableSequentialData},
 * {@link InputStream}, or a byte array. When backed by a stream the internal buffer is refilled
 * on demand as data is consumed. A stream will never be read past its limit.
 *
 * <p>Errors are tracked internally rather than thrown immediately. Call {@link #error()} to check
 * for a pending error code, or {@link #throwOnError()} to surface it as a {@link ParseException}.
 * Once an error is set it is sticky — subsequent reads return default values (zero or empty).
 * EOF is not an error and will return 0 when error() is called.
 * ByteBuffer using a direct buffer is not supported
 */
public class PbjReader implements AutoCloseable {
    private byte[] buf;
    private int pos, end;
    private int relLimit, err;
    private long absoluteLimit = Long.MAX_VALUE, offset;
    private ReadableSequentialData rsd;
    private InputStream stream;
    private Exception cause;
    private boolean seenEOF, includeCause;
    private byte[] ownedBuf;

    private char[] charArray;
    private static final boolean useStacktrace =
            !"false".equalsIgnoreCase(System.getProperty("pbj.ReaderWriter.useStackTrace"));

    public static final int EOF = -1,
            CLOSED = -2,
            DATA_ENCODING = 1,
            BUFFER_UNDERFLOW = 2,
            PARSE = 3,
            ILLEGAL_ARGUMENT = 4,
            IO_ERROR = 5,
            UNSUPPORTED = 6, // used with WIRE_TYPE_GROUP_START, WIRE_TYPE_GROUP_END
            USAGE_ERROR = 9,
            UNKNOWN_FIELD = 10,
            BUFFER_OVERFLOW = 11,
            MAX_DEPTH_REACHED = 12,
            MALFORM_STRING = 13;

    private static final UnknownFieldException premadeUnknown;
    private static final BufferUnderflowException premadeUnderflow;
    private static final BufferOverflowException premadeOverflow;
    private static final RuntimeException premadeRuntime, premadeUnsupported;
    private static final DataEncodingException premadeDataEncoding;
    private static final IllegalArgumentException premadeIllegal;
    private static final ParseException premadeParseEmpty, premadeParseUnknown, premadeMaxDepth;

    static {
        premadeUnknown = new UnknownFieldException("");
        premadeUnderflow = new BufferUnderflowException();
        premadeOverflow = new BufferOverflowException();
        premadeRuntime = new RuntimeException();
        premadeUnsupported = new RuntimeException("Hit an unsupported feature");
        premadeDataEncoding = new DataEncodingException("");
        premadeIllegal = new IllegalArgumentException("");

        premadeParseEmpty = new ParseException("parse error");
        premadeParseUnknown = new ParseException("parse error", premadeUnknown);
        premadeMaxDepth = new ParseException("Reached maximum allowed depth");
    }

    private void construct(byte[] buffer, int position, int endPosition) {
        buf = buffer;
        pos = position;
        end = endPosition;
        relLimit = end;
        absoluteLimit = end;
        seenEOF = true;
        err = EOF;
    }

    /**
     * Resets this reader to read from the given byte array slice, discarding any previous state.
     *
     * @param buffer      the backing byte array
     * @param position    the inclusive start index within {@code buffer}
     * @param endPosition the exclusive end index within {@code buffer}
     */
    public void resetWith(byte[] buffer, int position, int endPosition) {
        err = 0;
        offset = 0;
        cause = null;
        includeCause = false;
        rsd = null;
        stream = null;
        construct(buffer, position, endPosition);
    }

    private void construct(ReadableSequentialData seq, InputStream inputStream) {
        if (seq != null) {
            rsd = seq;
            absoluteLimit = seq.limit();
            offset = seq.position();
        } else if (inputStream != null) {
            stream = inputStream;
        }
        ownedBuf = buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
    }

    private void resetWith(ReadableSequentialData seq, InputStream inputStream) {
        err = 0;
        cause = null;
        includeCause = false;
        if ((seq == null && inputStream == null) || (seq != null && inputStream != null)) {
            setError(USAGE_ERROR);
            return;
        }

        if (seq != null) {
            rsd = seq;
            stream = null;
            absoluteLimit = seq.limit();
            offset = seq.position();
        } else if (inputStream != null) {
            rsd = null;
            stream = inputStream;
            absoluteLimit = Long.MAX_VALUE;
            offset = 0;
        }
        if (ownedBuf == null) {
            ownedBuf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        }
        buf = ownedBuf;
        pos = 0;
        end = 0;
        relLimit = 0;
        seenEOF = false;
    }

    /** Creates a reader backed by the given {@link ReadableSequentialData}. */
    public PbjReader(ReadableSequentialData seq) {
        construct(seq, null);
    }
    /** Creates a reader backed by the given {@link InputStream}. */
    public PbjReader(InputStream inputStream) {
        construct(null, inputStream);
    }
    /** Creates a reader backed by the given byte array. */
    public PbjReader(byte[] data) {
        construct(data, 0, data.length);
    }
    /**
     * Creates a reader backed by the given byte array slice.
     *
     * @param data   the backing byte array
     * @param offset the inclusive start index
     * @param end    the exclusive end index
     */
    public PbjReader(byte[] data, int offset, int end) {
        construct(data, offset, end);
    }
    /** Creates a reader backed by the given {@link ByteBuffer}. */
    public PbjReader(ByteBuffer bb) {
        int position = bb.arrayOffset() + bb.position();
        construct(bb.array(), position, position + bb.remaining());
    }

    /** Creates a reader backed by the given {@link Bytes}. */
    public PbjReader(Bytes bytes) {
        bytes.resetPbjReader(this);
    }

    /** Resets this reader to read from the given {@link ReadableSequentialData}. */
    public void resetWith(ReadableSequentialData seq) {
        resetWith(seq, null);
    }
    /** Resets this reader to read from the given {@link InputStream}. */
    public void resetWith(InputStream inputStream) {
        resetWith(null, inputStream);
    }
    /** Resets this reader to read from the given byte array. */
    public void resetWith(byte[] data) {
        resetWith(data, 0, data.length);
    }
    /** Resets this reader to read from the given {@link ByteBuffer}. */
    public void resetWith(ByteBuffer bb) {
        int position = bb.arrayOffset() + bb.position();
        resetWith(bb.array(), position, position + bb.remaining());
    }

    private void bufferMore() {
        if (err != 0) return;
        offset += pos;
        if (pos < end && pos != 0) {
            int moveLen = end - pos;
            System.arraycopy(buf, pos, buf, 0, end - pos);
            end = moveLen;
        } else if (pos == end) {
            end = 0;
        }
        pos = 0;
        int rdlen = readFromInput(buf, end, buf.length - end);
        end += rdlen;
        relLimit = (int) Math.min(absoluteLimit - offset, end);
        if (rdlen == 0) {
            seenEOF = true;
            err = EOF;
        }
    }

    /**
     * Returns {@code true} if there are bytes remaining to be read.
     * For streaming readers, triggers a buffer refill if the local buffer is exhausted.
     *
     * @return {@code true} if at least one byte can be read
     */
    public boolean hasRemaining() {
        // small and likely to inline
        if (pos < relLimit) return true;
        if (offset + pos == absoluteLimit) return false;
        return hasRemainingInternal();
    }
    // still small, but less likely to hit this case in steaming, and only once when not streaming
    private boolean hasRemainingInternal() {
        if (seenEOF) return false;
        bufferMore();
        return pos < relLimit;
    }

    /**
     * Returns the absolute byte limit beyond which reading is not permitted.
     *
     * @return the absolute limit position
     */
    public long limit() {
        return absoluteLimit;
    }

    /**
     * Sets the absolute byte limit beyond which reading is not permitted.
     * If using a stream, it will not read past that limit
     *
     * @param limit the new limit position
     */
    public void limit(long limit) {
        absoluteLimit = limit;
        if (rsd != null) {
            rsd.limit(limit);
        }
        if (err > 0) return; // keep relLimit -1 in error state
        relLimit = (int) Math.min(absoluteLimit - offset, end);
    }

    /**
     * Returns the current absolute read position, accounting for any bytes already consumed
     * from the internal buffer plus any previously buffered data.
     *
     * @return the current read position
     */
    public long position() {
        return pos + offset;
    }

    /**
     * Returns the number of bytes remaining before the limit. This is not the byte length since a stream may return MAX_VALUE as its limit
     *
     * @return the number of bytes remaining
     */
    public long remaining() {
        return limit() - position();
    }

    /**
     * Skips over {@code count} bytes, advancing the read position without returning the data.
     * Sets {@link #BUFFER_UNDERFLOW} if there are fewer than {@code count} bytes remaining.
     *
     * @param count the number of bytes to skip
     */
    public void skip(int count) {
        if (count >= 0 && pos + count <= relLimit) {
            pos += count;
            return;
        }
        skipInternal(count);
    }

    private void skipInternal(int count) {
        if (seenEOF) {
            setError(BUFFER_UNDERFLOW);
            return;
        }

        int skippedInBuffer = relLimit - pos;
        count -= skippedInBuffer;
        pos = relLimit;
        offset += count;
        try {
            if (stream != null) {
                long remaining = count;
                while (remaining > 0) {
                    long skipped = stream.skip(remaining);
                    if (skipped > 0) {
                        remaining -= skipped;
                    } else {
                        // Docs suggest skup can return 0 w/o reaching EOF
                        int b = stream.read();
                        if (b == -1) break;
                        remaining--;
                    }
                }
                if (remaining != 0) {
                    setError(BUFFER_UNDERFLOW);
                }
            } else {
                rsd.skip(count);
            }
        } catch (IOException e) {
            setError(IO_ERROR);
        }
    }

    /**
     * Reads a base-128 varint and returns its value as an {@code int} (no zigzag decoding).
     * On a malformed varint, sets the error flag and returns {@code -1}; however,
     * {@code -1} is also a valid decoded value, so callers must check {@link #error()} to
     * distinguish an error from a legitimate result.
     *
     * @return the decoded integer value
     */
    public int readVarIntNoZZ() {
        return (int) readVarLongNoZZ();
    }

    /**
     * Reads a base-128 varint and returns its value as an {@code int}, with optional zigzag decoding.
     * On a malformed varint, sets the error flag and returns {@code -1}; however,
     * {@code -1} is also a valid decoded value, so callers must check {@link #error()} to
     * distinguish an error from a legitimate result.
     *
     * @param zigZag if {@code true}, decodes using zigzag: {@code (n >>> 1) ^ -(n & 1)}
     * @return the decoded integer value
     */
    public int readVarInt(boolean zigZag) {
        return (int) readVarLong(zigZag);
    }

    /**
     * Reads a base-128 varint and returns its zigzag-decoded value as an {@code int}.
     * Zigzag decoding maps the raw unsigned value {@code n} to {@code (n >>> 1) ^ -(n & 1)}.
     * On a malformed varint, sets the error flag and returns {@code -1}; however,
     * {@code -1} is also a valid decoded value, so callers must check {@link #error()} to
     * distinguish an error from a legitimate result.
     *
     * @return the decoded integer value
     */
    public int readVarIntZZ() {
        return (int) readVarLongZZ();
    }

    /**
     * Reads a base-128 varint and returns its value as a {@code long}.
     * On a malformed varint (more than 10 bytes), sets {@link #DATA_ENCODING} and returns
     * {@code -1}; however, {@code -1} is also a valid decoded value, so callers must check
     * {@link #error()} to distinguish an error from a legitimate result.
     *
     * @param zigZag if {@code true}, decodes using zigzag: {@code (n >>> 1) ^ -(n & 1)}
     * @return the decoded long value
     */
    public long readVarLong(boolean zigZag) {
        long value = readVarLongNoZZ();
        return zigZag ? (value >>> 1) ^ -(value & 1) : value;
    }

    /**
     * Reads a base-128 varint and returns its zigzag-decoded value as a {@code long}.
     * Zigzag decoding maps the raw unsigned value {@code n} to {@code (n >>> 1) ^ -(n & 1)}.
     * On a malformed varint, sets the error flag and returns {@code -1}; however,
     * {@code -1} is also a valid decoded value, so callers must check {@link #error()} to
     * distinguish an error from a legitimate result.
     *
     * @return the decoded long value
     */
    public long readVarLongZZ() {
        long value = readVarLongNoZZ();
        return (value >>> 1) ^ -(value & 1);
    }

    /**
     * Reads a base-128 varint and returns its value as a {@code long} (no zigzag decoding).
     * On a malformed varint (more than 10 bytes), sets {@link #DATA_ENCODING} and returns
     * {@code -1}; however, {@code -1} is also a valid decoded value, so callers must check
     * {@link #error()} to distinguish an error from a legitimate result.
     *
     * @return the decoded long value
     */
    public long readVarLongNoZZ() {
        if (pos + 10 <= relLimit) {
            long value = 0;
            for (int i = 0; i < 10; i++) {
                byte b = buf[pos++];
                value |= (long) (b & 0x7F) << (i * 7);
                if (b >= 0) {
                    return value;
                }
            }
            setError(DATA_ENCODING);
            return -1;
        }
        return readVarLongNoZZInternal();
    }

    private long readVarLongNoZZInternal() {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            byte b = readByte();
            value |= (long) (b & 0x7F) << (i * 7);
            if (b >= 0) {
                return value;
            }
        }
        setError(DATA_ENCODING);
        return -1;
    }

    /**
     * Reads a base-128 varint and returns the raw encoded bytes without decoding them.
     * Sets {@link #DATA_ENCODING} if the varint is malformed.
     *
     * @return the raw varint bytes, or {@link Bytes#EMPTY} on error
     */
    public Bytes readVarLongBytes() {
        byte[] bytes = new byte[10];
        if (pos + 10 <= relLimit) {
            for (int i = 0; i < 10; i++) {
                bytes[i] = buf[pos++];
                if (bytes[i] >= 0) {
                    return Bytes.wrap(bytes, 0, i + 1);
                }
            }
            setError(DATA_ENCODING);
            return Bytes.EMPTY;
        }
        return readVarLongBytesInternal(bytes);
    }

    private Bytes readVarLongBytesInternal(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            bytes[i] = readByte();
            if (bytes[i] >= 0) {
                return Bytes.wrap(bytes, 0, i + 1);
            }
        }
        setError(DATA_ENCODING);
        return Bytes.EMPTY;
    }

    /**
     * Records an error on this reader with no message. Once an error is recorded
     * subsequent reads return default values (zero or empty).
     *
     * @param errorKind one of the error-code constants ({@link #DATA_ENCODING}, {@link #BUFFER_UNDERFLOW}, etc.)
     */
    public void setError(int errorKind) {
        setError(errorKind, "");
    }

    /**
     * Records an error on this reader if no previous error is set. Once an error is recorded
     * subsequent reads return default values (zero or empty).
     *
     * @param errorKind one of the error-code constants ({@link #DATA_ENCODING}, {@link #BUFFER_UNDERFLOW}, etc.)
     * @param message   a detail message associated with the error
     */
    public void setError(int errorKind, String message) {
        if (err > 0) return; // if an error exists, don't overwrite
        err = errorKind;
        relLimit = -1;
        seenEOF = true;
        // TODO simplify when exceptions are not required
        includeCause = true;
        if (useStacktrace) {
            if (errorKind == UNKNOWN_FIELD) {
                cause = new UnknownFieldException(message);
            } else if (errorKind == BUFFER_UNDERFLOW) {
                cause = new BufferUnderflowException();
            } else if (errorKind == BUFFER_OVERFLOW) {
                cause = new BufferOverflowException();
            } else if (errorKind == PARSE) {
                cause = new RuntimeException(message);
                includeCause = false;
            } else {
                cause = new RuntimeException(message);
            }
        } else {
            if (errorKind == UNKNOWN_FIELD) {
                cause = premadeUnknown;
            } else if (errorKind == BUFFER_UNDERFLOW) {
                cause = premadeUnderflow;
            } else if (errorKind == BUFFER_OVERFLOW) {
                cause = premadeOverflow;
            } else if (errorKind == PARSE) {
                cause = premadeParseEmpty;
                includeCause = false;
            } else {
                cause = premadeRuntime;
            }
        }
    }

    /**
     * Returns {@code true} if no error is set; otherwise throws a {@link ParseException}.
     *
     * @return {@code true} if no error has occurred
     * @throws ParseException if an error is set
     */
    public boolean throwOnErrorOrTrue() throws ParseException {
        if (err <= 0) return true;
        throw throwOnErrorImpl();
    }

    /**
     * Throws a {@link ParseException} if an error is set; otherwise does nothing.
     *
     * @throws ParseException if {@link #error()} is non-zero
     */
    public void throwOnError() throws ParseException {
        if (err <= 0) return;
        throw throwOnErrorImpl();
    }

    private ParseException throwOnErrorImpl() throws ParseException {
        if (useStacktrace) {
            switch (err) {
                case DATA_ENCODING:
                    throw new DataEncodingException("throwOnError", cause);
                case ILLEGAL_ARGUMENT:
                    throw new IllegalArgumentException("throwOnError", cause);
                case UNSUPPORTED:
                    throw new RuntimeException("Hit an unsupported feature", cause);
                case MAX_DEPTH_REACHED:
                    throw new ParseException("Reached maximum allowed depth");
                case PARSE:
                default:
                    if (includeCause) throw new ParseException(cause);
                    ParseException ex = new ParseException("");
                    ex.setStackTrace(cause.getStackTrace());
                    throw ex;
            }
        } else {
            switch (err) {
                case DATA_ENCODING:
                    throw premadeDataEncoding;
                case ILLEGAL_ARGUMENT:
                    throw premadeIllegal;
                case UNSUPPORTED:
                    throw premadeUnsupported;
                case MAX_DEPTH_REACHED:
                    throw premadeMaxDepth;
                case PARSE:
                default:
                    if (includeCause) {
                        throw new ParseException(cause, true);
                    }
                    throw premadeParseEmpty;
            }
        }
    }

    /**
     * Like {@link #throwOnError()}, but throws an {@link IOException} for {@link #UNSUPPORTED}
     * errors instead of a {@link ParseException}. Intended for tests that expect checked I/O
     * exceptions.
     *
     * @throws ParseException if a parse error is set
     * @throws IOException    if an unsupported-feature error is set
     */
    public void throwOnError2() throws ParseException, IOException {
        if (err == UNSUPPORTED) {
            throw new IOException("Hit an unsupported feature", cause);
        }
        throwOnError();
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
     * Returns true if all operations has been successful
     *
     * @return {@code true} if no error has occurred
     */
    public boolean ok() {
        return err <= 0;
    }

    private int bufferedInternal(int count) {
        if (count <= buf.length) {
            bufferMore();
            if (pos + count <= relLimit) {
                int origPos = pos;
                pos += count;
                return origPos;
            }
        }
        return -1;
    }

    private int buffered(int count) {
        if (pos + count <= relLimit) {
            int origPos = pos;
            pos += count;
            return origPos;
        }
        return bufferedInternal(count);
    }

    private int readFromInput(@NonNull byte[] dst, int off, int len) {
        if (stream != null) {
            int total = 0;
            try {
                while (total < len) {
                    int n = stream.read(dst, off + total, len - total);
                    if (n < 0) break;
                    total += n;
                }
            } catch (IOException e) {
                setError(IO_ERROR);
            }
            return total;
        }
        long remaining = rsd.remaining();
        if (remaining <= 0) return 0;
        len = (int) Math.min(len, remaining); // a test requires not buffering past limit
        return (int) rsd.readBytes(dst, off, len);
    }

    private int readBytesInternalCopy(@NonNull byte[] dst, int dstOffset, int count) {
        if (err > 0) return -1;
        int copiedLen = Math.min(count, relLimit - pos);
        System.arraycopy(buf, pos, dst, dstOffset, copiedLen);
        pos += copiedLen;
        if (copiedLen == count || err != 0) return copiedLen;

        offset += pos;
        relLimit = pos = end = 0;
        int rdlen = readFromInput(dst, dstOffset + copiedLen, count - copiedLen);
        if (rdlen == 0) {
            seenEOF = true;
            err = EOF;
        }
        offset += rdlen;
        return copiedLen + rdlen;
    }

    /**
     * Reads up to {@code dst.length} bytes into the given array. If fewer bytes are available
     * the array is partially filled. The number of bytes copied is returned.
     * Returns {@code -1} on an error
     *
     * @param dst the destination array
     * @return the number of bytes read, or {@code -1} if a pre-existing error is set
     */
    public long readBytes(@NonNull final byte[] dst) {
        return readBytesInternalCopy(dst, 0, dst.length);
    }

    /**
     * Reads up to {@code length} bytes into the given array starting at {@code offset}. If fewer
     * bytes are available the array is partially filled. The number of bytes copied is returned.
     * Returns {@code -1} on an error
     *
     * @param dst    the destination array
     * @param offset the start index within {@code dst}
     * @param length the number of bytes to read
     * @return the number of bytes read, or {@code -1} if a pre-existing error is set
     */
    public long readBytes(@NonNull final byte[] dst, int offset, int length) {
        return readBytesInternalCopy(dst, offset, length);
    }

    /**
     * Reads bytes into the given {@link ByteBuffer}, advancing its position by the number of
     * bytes read. If fewer bytes are available than the buffer's remaining capacity, the buffer
     * is partially filled, and the number of bytes copied is returned.
     * Returns {@code -1} on an error
     *
     * @param dst the destination buffer; bytes are written starting at its current position
     * @return the number of bytes actually read, or {@code -1} if a pre-existing error is set
     */
    public long readBytes(@NonNull ByteBuffer dst) {
        int len = readBytesInternalCopy(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
        if (len > 0) { // handles error case (which sets len == -1)
            dst.position(dst.position() + len);
        }
        return len;
    }

    /**
     * Reads exactly {@code length} bytes and returns them as a {@link Bytes} instance.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@link Bytes#EMPTY} if fewer bytes are available.
     *
     * @param length the number of bytes to read
     * @return the read bytes, or {@link Bytes#EMPTY} on error
     */
    public @NonNull Bytes readBytes(int length) {
        if (length <= relLimit - pos && err <= 0) {
            byte[] dst = new byte[length];
            System.arraycopy(buf, pos, dst, 0, length);
            pos += length;
            return Bytes.wrap(dst);
        }
        return readBytesInternal(length);
    }

    @NonNull
    private Bytes readBytesInternal(int length) {
        if (length == 0 || err > 0) {
            return Bytes.EMPTY;
        } else if (length < 0) {
            setError(ILLEGAL_ARGUMENT);
            return Bytes.EMPTY;
        }
        byte[] dst = new byte[length];
        int copiedLen = readBytesInternalCopy(dst, 0, length);
        if (copiedLen < length) {
            setError(BUFFER_UNDERFLOW);
            return Bytes.EMPTY;
        }
        return Bytes.wrap(dst);
    }

    /**
     * Reads a 32-bit integer in big-endian byte order. Alias for {@link #readIntBE()}.
     *
     * @return the integer value
     */
    public int readInt() {
        return readIntBE();
    }

    /**
     * Reads a 32-bit integer in big-endian byte order.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@code 0} if fewer than 4 bytes are available.
     *
     * @return the integer value
     */
    public int readIntBE() {
        if (pos + 4 <= relLimit) {
            int v = 0;
            for (int i = 0; i < 4; i++) {
                v |= (buf[pos + 3 - i] & 255) << (i * 8);
            }
            pos += 4;
            return v;
        }
        return readIntBEInternal();
    }

    /**
     * Reads a 32-bit integer in little-endian byte order.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@code 0} if fewer than 4 bytes are available.
     *
     * @return the integer value
     */
    public int readIntLE() {
        if (pos + 4 <= relLimit) {
            int v = 0;
            for (int i = 0; i < 4; i++) {
                v |= (buf[pos + i] & 255) << (i * 8);
            }
            pos += 4;
            return v;
        }
        return readIntLEInternal();
    }

    private int readIntBEInternal() {
        bufferMore();
        if (pos + 4 > relLimit) {
            setError(BUFFER_UNDERFLOW);
            return 0;
        }
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (buf[pos + 3 - i] & 255) << (i * 8);
        }
        pos += 4;
        return v;
    }

    private int readIntLEInternal() {
        bufferMore();
        if (pos + 4 > relLimit) {
            setError(BUFFER_UNDERFLOW);
            return 0;
        }
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (buf[pos + i] & 255) << (i * 8);
        }
        pos += 4;
        return v;
    }

    /**
     * Reads a 64-bit integer in big-endian byte order. Alias for {@link #readLongBE()}.
     *
     * @return the long value
     */
    public long readLong() {
        return readLongBE();
    }

    /**
     * Reads a 64-bit integer in big-endian byte order.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@code 0} if fewer than 8 bytes are available.
     *
     * @return the long value
     */
    public long readLongBE() {
        if (pos + 8 <= relLimit) {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= (long) (buf[pos + 7 - i] & 255) << (i * 8);
            }
            pos += 8;
            return v;
        }
        return readLongBEInternal();
    }

    private long readLongBEInternal() {
        bufferMore();
        if (pos + 8 > relLimit) {
            setError(BUFFER_UNDERFLOW);
            return 0;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (buf[pos + 7 - i] & 255) << (i * 8);
        }
        pos += 8;
        return v;
    }

    /**
     * Reads a 64-bit integer in little-endian byte order.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@code 0} if fewer than 8 bytes are available.
     *
     * @return the long value
     */
    public long readLongLE() {
        if (pos + 8 <= relLimit) {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= (long) (buf[pos + i] & 255) << (i * 8);
            }
            pos += 8;
            return v;
        }
        return readLongLEInternal();
    }

    private long readLongLEInternal() {
        bufferMore();
        if (pos + 8 > relLimit) {
            setError(BUFFER_UNDERFLOW);
            return 0;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (buf[pos + i] & 255) << (i * 8);
        }
        pos += 8;
        return v;
    }

    /**
     * Reads a 32-bit float in big-endian byte order.
     *
     * @return the float value
     */
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads a 32-bit float in little-endian byte order.
     *
     * @return the float value
     */
    public float readFloatLE() {
        return Float.intBitsToFloat(readIntLE());
    }

    /**
     * Reads a 64-bit double in big-endian byte order.
     *
     * @return the double value
     */
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a 64-bit double in little-endian byte order.
     *
     * @return the double value
     */
    public double readDoubleLE() {
        return Double.longBitsToDouble(readLongLE());
    }

    /**
     * Returns an {@link InputStream} view of the remaining data in this reader.
     * For streaming readers, returns the underlying stream directly. For byte-array readers,
     * returns a {@link ByteArrayInputStream} over the buffered data.
     *
     * @return an {@code InputStream} over the unread bytes
     * @throws UnsupportedOperationException if the reader is in a partially-buffered streaming state
     */
    public InputStream asInputStream() {
        if (end == 0) return stream != null ? stream : rsd.asInputStream();
        if (seenEOF && offset == 0) {
            return new ByteArrayInputStream(buf, pos, end - pos);
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Reads a single byte and returns {@code true} if it is non-zero, {@code false} otherwise.
     *
     * @return the boolean value
     */
    public boolean readBoolean() {
        return readByte() != 0;
    }

    /**
     * Reads and returns a single signed byte, advancing the position by 1.
     * Sets {@link #BUFFER_UNDERFLOW} and returns {@code 0} if no bytes remain.
     *
     * @return the byte value
     */
    public byte readByte() {
        if (pos + 1 <= relLimit) return buf[pos++];
        return readByteInternal();
    }

    private byte readByteInternal() {
        if (pos + 1 > relLimit) {
            bufferMore();
            if (pos + 1 > relLimit) {
                setError(BUFFER_UNDERFLOW);
                return 0;
            }
        }
        return buf[pos++];
    }

    /**
     * Reads a length-prefixed UTF-8 string. The length is read as a base-128 varint followed
     * by that many UTF-8 bytes. Sets {@link #PARSE} and returns {@code ""} if the length exceeds
     * {@code maxSize}, is negative, or if the UTF-8 bytes are malformed.
     *
     * @param maxSize the maximum allowed string byte length
     * @return the decoded string, or {@code ""} on error
     */
    public String readString(final long maxSize) {
        final int length = readVarIntNoZZ();
        if (length > maxSize || length < 0) {
            setError(PbjReader.PARSE);
            return "";
        }

        int bufPos = buffered(length);
        byte[] data = null;
        if (bufPos >= 0) {
            data = buf;
        } else {
            // larger than internal buffer
            data = new byte[length];
            int copiedLen = readBytesInternalCopy(data, 0, length);
            if (copiedLen < length) {
                setError(BUFFER_UNDERFLOW);
                return "";
            }
            bufPos = 0;
        }

        if (charArray == null || length > charArray.length) {
            long power2Capacity = 2 << (63 - Long.numberOfLeadingZeros(Math.max(2048, length)));
            if (power2Capacity < 0 || power2Capacity > Integer.MAX_VALUE) {
                setError(ILLEGAL_ARGUMENT, "length larger than int max");
                return "";
            }
            charArray = new char[(int) power2Capacity];
        }

        int i = 0;
        // Ascii fast path
        {
            for (; i < length; i++) {
                byte b = data[bufPos + i];
                if ((b & 0x80) != 0) break;
                charArray[i] = (char) b;
            }
            if (i == length) {
                return new String(charArray, 0, length);
            }
        }
        int utf16Len = ProtoParserTools.fromUTF8(charArray, data, bufPos, i, length);
        if (utf16Len >= 0) {
            return new String(charArray, 0, utf16Len);
        }
        setError(PbjReader.PARSE);
        return "";
    }

    @Override
    public void close() {
        if (stream == null) return;
        try {
            stream.close();
        } catch (IOException ex) {
            setError(IO_ERROR, ex.getMessage());
        }
        if (err == 0) {
            err = CLOSED;
        }
    }
}
