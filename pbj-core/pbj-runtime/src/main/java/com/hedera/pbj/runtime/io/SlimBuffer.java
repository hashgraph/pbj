// SPDX-License-Identifier: Apache-2.0
/* WIP, not optimized, is passing tests. Some TODOs are
 * Fix logic so the internal buffer never needs to be resized
 *      right now limit is the only place where it grows (grows until it can fit everything)
 * Update ReadVarInt/readVarLong to latest implementation
 * Remove catches and throws
 * Simplify/remove methods if breaking compatibility
 * Remove asInputStream
 */

package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A {@link SlimBuffer} which is meant to be easy for the JIT to optimize. This is suitable for reading data from a stream or buffer.
 * Once read, data cannot be re-read. The {@link #position()}, once incremented, cannot be reset or decremented.
 * The public methods are meant to be compatible with ReadableSequentialData
 */
public class SlimBuffer {
    private byte[] buf;
    private int pos, end; // TODO remove 'end' if keeping limit
    private int relLimit, err;
    private long absoluteLimit = -1, offset;
    private boolean seenEOF;
    private ReadableSequentialData input;
    public static final int EOF = -1,
            DataEncoding = 1,
            BufferUnderflow = 2,
            Parse = 3,
            IllegalArgument = 4,
            IOError = 5;

    public static final int Unsupported = 6; // used with WIRE_TYPE_GROUP_START, WIRE_TYPE_GROUP_END

    public SlimBuffer(ReadableSequentialData input) {
        this(input, null);
    }

    /**
     * Streams data of unknown length into its private buffer.
     * It will read more data then necessary when there's space in the internal buffer
     * @param input the input stream
     * @param scratchBuffer a bufer to be used internally, allowed to be null. ATM it may be thrown way, there's a todo to fix it
     */
    public SlimBuffer(ReadableSequentialData input, byte[] scratchBuffer) {
        this.input = input;
        if (scratchBuffer != null && scratchBuffer.length >= 4096) {
            buf = scratchBuffer;
        } else {
            buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        }
    }

    /**
     * Non-Streaming, uses the buffer as input
     * @param completeBuffer the input
     */
    public SlimBuffer(byte[] completeBuffer) {
        buf = completeBuffer;
        end = buf.length;
        relLimit = buf.length;
        absoluteLimit = buf.length;
        seenEOF = true;
        err = EOF;
    }

    // this should only be reachable from bufferToEOF called in limit()
    private void grow(int minCap) {
        int power2Capacity = 2 << (63 - Long.numberOfLeadingZeros(Math.max(buf.length, minCap)));
        byte[] newCopy = new byte[power2Capacity];
        System.arraycopy(buf, 0, newCopy, 0, end);
        buf = newCopy;
    }

    private void bufferToEOF() {
        // bufferMore will at min double everytime, 2 pow 64 would be all the bits
        for (int i = 0; !seenEOF && i < 64; i++) {
            bufferMore(1);
        }
    }

    // used for streaming, todo remove relAmount?
    private void bufferMore(int relAmount) {
        if (err != 0) return;
        if (absoluteLimit != -1 && offset + pos + relAmount > absoluteLimit) {
            setError(BufferUnderflow);
            return;
        }
        offset += pos;
        relLimit = (int) (absoluteLimit - offset);
        if (pos < end && pos != 0) {
            int moveLen = end - pos;
            System.arraycopy(buf, pos, buf, 0, end - pos);
            end = moveLen;
        } else if (pos == end) {
            end = 0;
        }
        pos = 0;
        int remLen = buf.length - end;
        if (remLen < relAmount) {
            grow(relAmount);
            remLen = buf.length - end;
        }
        int rdlen = (int) input.readBytes(buf, end, remLen);
        end += rdlen;
        relLimit = end;
        if (rdlen == 0) {
            seenEOF = true;
            err = EOF;
            return;
        }
    }

    // small and likely to inline
    public boolean hasMore() {
        if (pos < relLimit) return true;
        return hasMoreInternal();
    }
    // still small, but less likely to hit this case in steaming, and only once when not streaming
    private boolean hasMoreInternal() {
        if (seenEOF) return false;
        bufferMore(1);
        return pos < relLimit;
    }

    // returns the current limit, when streaming it'll buffer until it reaches EOF
    // TODO find out if tests work when streaming without buffering the entire input
    public long limit() {
        if (!seenEOF) {
            bufferToEOF();
            absoluteLimit = offset + end;
        }
        return absoluteLimit;
    }

    // Set the limit so a stream is considered to not have more past the limit
    public void limit(long limit) {
        absoluteLimit = Math.max(offset + pos, Math.min(limit, offset + end));
        if (err >= 0) return;
        relLimit = (int) (absoluteLimit - offset);
    }

    public long position() {
        return pos + offset;
    }

    public void skip(long count) {
        if (count <= 0) return; // should negative be an error?
        if (pos + count <= relLimit) {
            pos += (int) count;
            return;
        }
        if (seenEOF) {
            setError(BufferUnderflow);
            return;
        }

        count -= relLimit - pos;
        pos = relLimit;
        offset += count;
        input.skip(count);
    }

    public int readVarInt(boolean zigZag) {
        return (int) readVarLong(zigZag);
    }

    public long readVarLong(boolean zigZag) {
        // TODO update the implementation, but measure if latest is still best for this code
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
        // TODO update the implementation, but measure if latest is still best for this code
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
        if (errorKind > 0 && err > 0) {
            int q = 1;
        }
        // if an error exists, don't overwrite
        if (err > 0) return;
        err = errorKind;
        relLimit = -1;
        seenEOF = true;
    }

    public void upgradeErrorToParse() {
        if (err > 0) {
            err = Parse;
        }
    }

    public boolean throwOnError2() throws ParseException {
        throwOnError();
        return true;
    }

    public void throwOnError() throws ParseException {
        if (err <= 0) return;
        switch (err) {
            case DataEncoding:
                throw new DataEncodingException("");
            case BufferUnderflow:
                throw new BufferUnderflowException();
            case Parse:
                throw new ParseException("");
            default: {
                throw new DataEncodingException(""); // TODO fix
            }
        }
    }

    private int readBytesInternal(@NonNull byte[] dst, int dstOff, int dstLen) {
        if (err > 0) return -1;
        System.arraycopy(buf, pos, dst, dstOff, relLimit - pos);
        int copiedLen = relLimit - pos;
        pos = relLimit;
        if (seenEOF) return copiedLen;

        offset += pos;
        relLimit = pos = end = 0;
        long rdlen = 0;
        try {
            rdlen = input.readBytes(dst, dstOff + copiedLen, dstLen - copiedLen);
        } catch (UncheckedIOException e) {
            // NO OP
        }
        if (rdlen == 0) {
            seenEOF = true;
            err = EOF;
        }
        offset += rdlen;
        return (int) (copiedLen + rdlen);
    }

    public long readBytes(@NonNull byte[] dst) {
        if (dst.length <= relLimit - pos) {
            System.arraycopy(buf, pos, dst, 0, dst.length);
            pos += dst.length;
            return dst.length;
        }
        return readBytesInternal(dst, 0, dst.length);
    }

    // This may crash depending on usage. Specifically, if using an input stream and dst does not have an array
    public long readBytes(@NonNull ByteBuffer dst) {
        int remaining = dst.remaining();
        if (remaining <= relLimit - pos) {
            dst.put(buf, pos, remaining);
            pos += remaining;
            return remaining;
        }
        int len = readBytesInternal(dst.array(), dst.arrayOffset() + dst.position(), dst.remaining());
        dst.position(dst.position() + len);
        return len;
    }

    public @NonNull Bytes readBytes(int length) {
        if (length < 0) {
            setError(IllegalArgument);
            return Bytes.EMPTY;
        }
        if (length == 0) return Bytes.EMPTY;
        var dst = new byte[length];
        if (length <= relLimit - pos) {
            System.arraycopy(buf, pos, dst, 0, length);
            pos += length;
            return Bytes.wrap(dst);
        }
        int copiedLen = readBytesInternal(dst, 0, length);
        if (copiedLen < length) {
            setError(BufferUnderflow);
            return Bytes.EMPTY;
        }
        return Bytes.wrap(dst);
    }

    public int readIntNew() {
        if (pos + 4 <= relLimit) {
            int v = 0;
            for (int i = 0; i < 4; i++) {
                v |= (buf[pos + i] & 255) << (i * 8);
            }
            pos += 4;
            return v;
        }
        return readIntInternal();
    }

    private int readIntInternal() {
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

    public void ensure(int amount) throws BufferUnderflowException {
        if (amount < 0) {
            setError(IllegalArgument);
            return;
        }
        if (pos + amount <= relLimit) return;
        if (seenEOF) {
            setError(BufferUnderflow);
            return;
        }
        bufferMore(amount);
        if (pos + amount <= relLimit) return;
        setError(BufferUnderflow);
    }

    public int readInt() throws BufferUnderflowException, UncheckedIOException {
        ensure(4);
        if (err > 0) return 0;

        int b0 = readByte() & 255;
        int b1 = readByte() & 255;
        int b2 = readByte() & 255;
        int b3 = readByte() & 255;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public long readLong() throws BufferUnderflowException, UncheckedIOException {
        ensure(8);
        if (err > 0) return 0;

        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= (long) (buf[pos + i] & 255) << (i * 8);
        }
        pos += 8;
        return v;
    }

    public long readLongNew() {
        if (pos + 8 <= relLimit) {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v |= (long) (buf[pos + i] & 255) << (i * 8);
            }
            pos += 8;
            return v;
        }
        return readLongInternal();
    }

    private long readLongInternal() {
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

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    // TODO delete
    public InputStream asInputStream() {
        if (end == 0) return input.asInputStream();
        throw new UnsupportedOperationException();
    }

    private byte readByte() {
        if (pos == relLimit) {
            if (relLimit == end) {
                bufferMore(1);
            }
            if (pos == relLimit) {
                setError(BufferUnderflow);
            }
        }
        return buf[pos++];
    }

    // small version, the if statement is extremely likely and should be inlined
    private byte readByteNew() {
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
