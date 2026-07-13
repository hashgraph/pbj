// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.EOFException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A {@link SlimBuffer} which is meant to be easy for the JIT to optimize. This is suitable for reading data from a stream or buffer.
 * Once read, data cannot be re-read. The {@link #position()}, once incremented, cannot be reset or decremented.
 * The public methods are meant to be compatible with ReadableSequentialData
 */
public class SlimBuffer {
    private byte[] buf;
    private int pos, end;
    private int relLimit, err;
    private long absoluteLimit = Long.MAX_VALUE, offset;
    private ReadableSequentialData input;
    private InputStream input2;
    private Exception cause;
    private boolean seenEOF, includeCause;

    public char[] charArray = new char[4096]; // will grow

    public static final int EOF = -1,
            DataEncoding = 1,
            BufferUnderflow = 2,
            Parse = 3,
            IllegalArgument = 4,
            IOError = 5,
            Unsupported = 6, // used with WIRE_TYPE_GROUP_START, WIRE_TYPE_GROUP_END
            UsageError = 9,
            UnknownField = 10,
            BufferOverflow = 11,
            MaxDepthReached = 12;

    /**
     * Streams data of unknown length into its private buffer.
     * It will read more data then necessary when there's space in the internal buffer
     * @param input the input stream
     */
    public SlimBuffer(ReadableSequentialData input) {
        this.input = input;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        absoluteLimit = input.limit();
        offset = input.position();
    }

    public SlimBuffer(InputStream input) {
        this.input2 = input;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
    }

    public SlimBuffer(byte[] completeBuffer) {
        this(completeBuffer, 0, completeBuffer.length);
    }

    public SlimBuffer(byte[] data, int offset, int end) {
        buf = data;
        pos = offset;
        this.end = end;
        relLimit = end;
        absoluteLimit = end;
        seenEOF = true;
        err = EOF;
    }

    public SlimBuffer(ByteBuffer completeBuffer) {
        byte[] data = completeBuffer.array();
        int pos = completeBuffer.arrayOffset() + completeBuffer.position();
        int end = pos + completeBuffer.remaining();
        this(data, pos, end);
    }

    public SlimBuffer(Bytes completeBuffer) {
        this(completeBuffer.toByteArray(), 0, (int) completeBuffer.length());
    }

    public void bufferToEOF() {
        while (err == 0) {
            offset += pos;
            int power2Capacity = 2 << (63 - Long.numberOfLeadingZeros(buf.length));
            var newBuf = new byte[power2Capacity];
            if (pos < end) {
                int moveLen = end - pos;
                System.arraycopy(buf, pos, newBuf, 0, moveLen);
                end = moveLen;
            } else {
                end = 0;
            }
            buf = newBuf;
            pos = 0;
            int remLen = buf.length - end;
            int rdlen = readFromInput(buf, end, remLen);
            end += rdlen;
            relLimit = (int) Math.min(absoluteLimit - offset, end);
            if (rdlen == 0) {
                seenEOF = true;
                err = EOF;
            }
        }
    }

    private void bufferMore(int relAmount) {
        if (err != 0) return;
        if (absoluteLimit != -1 && offset + pos + relAmount > absoluteLimit) {
            setError(BufferUnderflow);
            return;
        }
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

    public boolean hasRemaining() {
        return hasMore();
    }

    // small and likely to inline
    public boolean hasMore() {
        if (pos < relLimit) return true;
        if (offset + pos == absoluteLimit) return false;
        return hasMoreInternal();
    }
    // still small, but less likely to hit this case in steaming, and only once when not streaming
    private boolean hasMoreInternal() {
        if (seenEOF) return false;
        bufferMore(1);
        return pos < relLimit;
    }

    public long limit() {
        return absoluteLimit;
    }

    public void limit(long limit) {
        absoluteLimit = limit;
        if (input != null) {
            input.limit(limit);
        }
        if (err > 0) return; // keep relLimit -1 in error state
        relLimit = (int) Math.min(absoluteLimit - offset, end);
    }

    public long position() {
        return pos + offset;
    }

    public void resetPosition() {
        pos = 0;
        err = seenEOF ? EOF : UsageError;
        absoluteLimit = end;
        relLimit = end;
    }

    // To write test easier
    public SlimBuffer reset() {
        resetPosition();
        return this;
    }

    public void skip(long count) {
        if (count >= 0 && pos + count <= relLimit) {
            pos += (int) count;
            return;
        }
        skipInternal(count);
    }

    private void skipInternal(long count) {
        if (seenEOF) {
            setError(BufferUnderflow);
            return;
        }

        int skippedInBuffer = relLimit - pos;
        count -= skippedInBuffer;
        pos = relLimit;
        offset += count;
        if (input2 != null) {
            try {
                long remaining = count;
                while (remaining > 0) {
                    long skipped = input2.skip(remaining);
                    if (skipped <= 0) break;
                    remaining -= skipped;
                }
                if (remaining != 0) {
                    setError(BufferUnderflow);
                }
            } catch (IOException e) {
                setError(IOError);
            }
        } else {
            input.skip(count); // may throw
        }
    }

    public int readVarInt(boolean zigZag) {
        return (int) readVarLong(zigZag);
    }

    public long readVarLong(boolean zigZag) {
        long value = 0;
        for (int i = 0; i < 10; i++) {
            byte b = readByte();
            value |= (long) (b & 0x7F) << (i * 7);
            if (b >= 0) {
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        setError(DataEncoding);
        return -1;
    }

    public Bytes readVarLongBytes() {
        byte[] bytes = new byte[10];
        for (int i = 0; i < 10; i++) {
            bytes[i] = readByte();
            if (bytes[i] >= 0) {
                return Bytes.wrap(bytes, 0, i + 1);
            }
        }
        setError(DataEncoding);
        return Bytes.EMPTY;
    }

    public void setError(int errorKind) {
        if (err > 0) return; // if an error exists, don't overwrite
        err = errorKind;
        relLimit = -1;
        seenEOF = true;
        // TODO remove when CN doesn't need these. Only the else should exist (while debugging)
        includeCause = true;
        if (errorKind == UnknownField) {
            cause = new UnknownFieldException("");
        } else if (errorKind == BufferUnderflow) {
            cause = new BufferUnderflowException();
        } else if (errorKind == BufferOverflow) {
            cause = new BufferOverflowException();
        } else if (errorKind == Parse) {
            cause = new RuntimeException();
            includeCause = false;
        } else {
            cause = new RuntimeException(); // comment this out if you're not debugging
        }
    }

    public void upgradeErrorToParse() {
        if (err > 0 && err != MaxDepthReached) { // MaxDepthReached already throws parse, and requires specific text
            err = Parse;
        }
    }

    public boolean throwOnErrorOrTrue() throws ParseException {
        throwOnError();
        return true;
    }

    public void throwOnError() throws ParseException {
        if (err <= 0) return;
        switch (err) {
            case DataEncoding:
                throw new DataEncodingException("throwOnError", cause);
            case IllegalArgument:
                throw new IllegalArgumentException("throwOnError", cause);
            case Unsupported:
                throw new RuntimeException("Hit an unsupported feature", cause);
            case MaxDepthReached:
                throw new ParseException("Reached maximum allowed depth");
            case Parse:
            default:
                if (includeCause) throw new ParseException(cause);
                var ex = new ParseException("");
                ex.setStackTrace(cause.getStackTrace());
                throw ex;
        }
    }

    // Only used in test that expect IO Exceptions
    public void throwOnError2() throws ParseException, IOException {
        if (err <= 0) return;
        switch (err) {
            case DataEncoding:
                throw new DataEncodingException("throwOnError", cause);
            case IllegalArgument:
                throw new IllegalArgumentException("throwOnError", cause);
            case Unsupported:
                throw new IOException("Hit an unsupported feature", cause);
            case MaxDepthReached:
                throw new ParseException("Reached maximum allowed depth", cause);
            case Parse:
            default:
                if (includeCause) throw new ParseException(cause);
                var ex = new ParseException("");
                ex.setStackTrace(cause.getStackTrace());
                throw ex;
        }
    }

    public int error() {
        return err;
    }

    public byte[] array() {
        return buf;
    }

    private int bufferedInternal(int count) {
        if (count <= buf.length) {
            bufferMore(count);
            if (pos + count <= relLimit) {
                int origPos = pos;
                pos += count;
                return origPos;
            }
        }
        return -1;
    }

    public int buffered(int count) {
        if (pos + count <= relLimit) {
            int origPos = pos;
            pos += count;
            return origPos;
        }
        return bufferedInternal(count);
    }

    private int readFromInput(@NonNull byte[] dst, int off, int len) {
        if (input2 != null) {
            int total = 0;
            try {
                while (total < len) {
                    int n = input2.read(dst, off + total, len - total);
                    if (n < 0) break;
                    total += n;
                }
            } catch (IOException e) {
                setError(IOError);
            }
            return total;
        }
        try {
            long remaining = input.remaining();
            if (remaining <= 0) return 0;
            len = (int) Math.min(len, remaining); // a test requires not buffering past limit
            return (int) input.readBytes(dst, off, len);
        } catch (EOFException | UncheckedIOException e) {
            return 0;
        }
    }

    private int readBytesInternalCopy(@NonNull byte[] dst, int dstOffset, int count) {
        if (err > 0) return -1;
        int copiedLen = Math.min(count, relLimit - pos);
        System.arraycopy(buf, pos, dst, dstOffset, copiedLen);
        pos += copiedLen;
        if (copiedLen == count || err != 0) return copiedLen;

        offset += pos;
        relLimit = pos = end = 0;
        long rdlen = readFromInput(dst, dstOffset + copiedLen, count - copiedLen);
        if (rdlen == 0) {
            seenEOF = true;
            err = EOF;
        }
        offset += rdlen;
        return (int) (copiedLen + rdlen);
    }

    public long readBytes(@NonNull final byte[] dst) {
        return readBytesInternalCopy(dst, 0, dst.length);
    }

    public long readBytes(@NonNull ByteBuffer dst) {
        int len = readBytesInternalCopy(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
        if (len > 0) { // handles error case (which sets len == -1)
            dst.position(dst.position() + len);
        }
        return len;
    }

    public @NonNull Bytes readBytes(int length) {
        if (length <= relLimit - pos && err <= 0) {
            var dst = new byte[length];
            System.arraycopy(buf, pos, dst, 0, length);
            pos += length;
            return Bytes.wrap(dst);
        }
        return readBytes2(length);
    }

    public @NonNull Bytes readBytes2(int length) {
        if (length == 0 || err > 0) {
            return Bytes.EMPTY;
        } else if (length < 0) {
            setError(IllegalArgument);
            return Bytes.EMPTY;
        }
        var dst = new byte[length];
        int copiedLen = readBytesInternalCopy(dst, 0, length);
        if (copiedLen < length) {
            setError(BufferUnderflow);
            return Bytes.EMPTY;
        }
        return Bytes.wrap(dst);
    }

    public int readInt() {
        return readIntBE();
    }

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
        bufferMore(4);
        if (pos + 4 > relLimit) {
            setError(BufferUnderflow);
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
        bufferMore(4);
        if (pos + 4 > relLimit) {
            setError(BufferUnderflow);
            return 0;
        }
        int v = 0;
        for (int i = 0; i < 4; i++) {
            v |= (buf[pos + i] & 255) << (i * 8);
        }
        pos += 4;
        return v;
    }

    public long readLong() {
        return readLongBE();
    }

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
        bufferMore(8);
        if (pos + 8 > relLimit) {
            setError(BufferUnderflow);
            return 0;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (buf[pos + 7 - i] & 255) << (i * 8);
        }
        pos += 8;
        return v;
    }

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
        bufferMore(8);
        if (pos + 8 > relLimit) {
            setError(BufferUnderflow);
            return 0;
        }
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (buf[pos + i] & 255) << (i * 8);
        }
        pos += 8;
        return v;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public float readFloatLE() {
        return Float.intBitsToFloat(readIntLE());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public double readDoubleLE() {
        return Double.longBitsToDouble(readLongLE());
    }

    public InputStream asInputStream() {
        if (end == 0) return input2 != null ? input2 : input.asInputStream();
        if (seenEOF && offset == 0) {
            return new ByteArrayInputStream(buf, pos, end);
        }
        throw new UnsupportedOperationException();
    }

    private byte readByte() {
        if (pos + 1 <= relLimit) return buf[pos++];
        return readByteInternal();
    }

    private byte readByteInternal() {
        if (pos + 1 > relLimit) {
            bufferMore(1);
            if (pos + 1 > relLimit) {
                setError(BufferUnderflow);
                return 0;
            }
        }
        return buf[pos++];
    }
}
