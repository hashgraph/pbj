// SPDX-License-Identifier: Apache-2.0
/* WIP, not optimized, is passing tests. Some TODOs are
 * When using readBytes on a stream, skip buffering to internel buffer
 * Fix logic so the internal buffer never needs to be resized
 *      right now readBytes and limit are blocking this
 * Update ReadVarInt/readVarLong to latest implementation
 * Remove catches and throws
 * Simplify/remove methods if breaking compatibility
 * Remove asInputStream
 */

package com.hedera.pbj.runtime.io;

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
    private int relLimit;
    private long absoluteLimit, offset;
    private boolean seenEOF;
    private ReadableSequentialData input;

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
    }

    // TODO remove, atm exists for readBytes
    private void grow(int minCap) {
        int power2Capacity = 2 << (63 - Long.numberOfLeadingZeros(Math.max(buf.length, minCap)));
        byte[] newCopy = new byte[power2Capacity];
        System.arraycopy(buf, 0, newCopy, 0, end);
        buf = newCopy;
    }

    private void bufferMore() {
        bufferMore(1);
    }

    // used for streaming, todo remove relAmount
    private void bufferMore(int relAmount) {
        if (relAmount <= 0) {
            relAmount = 1;
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
        try {
            int rdlen = (int) input.readBytes(buf, end, remLen);
            end += rdlen;
            absoluteLimit = offset + end;
            relLimit = (int) (absoluteLimit - offset);
            if (rdlen == 0) {
                seenEOF = true;
                return;
            }
        } catch (UncheckedIOException e) {
            throw e;
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
        bufferMore();
        return pos < relLimit;
    }

    // todo remove
    public void ensure(int amount) throws BufferUnderflowException {
        if (amount < 0) {
            throw new IllegalArgumentException();
        }
        if (pos + amount <= relLimit) return;
        if (seenEOF) throw new BufferUnderflowException();
        bufferMore(amount);
        if (pos + amount <= relLimit) return;
        throw new BufferUnderflowException();
    }

    // returns the current limit, when streaming it'll buffer until it reaches EOF
    // TODO find out if tests work when streaming without buffering the entire input
    public long limit() {
        if (!seenEOF) {
            for (int i = 0; !seenEOF && i < 32; i++) {
                bufferMore();
            }
            if (seenEOF == false) {
                throw new RuntimeException(); // logic error
            }
        }
        return absoluteLimit;
    }

    // Set the limit so a stream is considered to not have more past the limit
    public void limit(long limit) {
        absoluteLimit = Math.max(offset + pos, Math.min(limit, offset + end));
        relLimit = (int) (absoluteLimit - offset);
    }

    public long position() {
        return pos + offset;
    }

    public void skip(long count) throws UncheckedIOException {
        ensure((int) count);
        pos += (int) count;
    }

    public int readVarInt(final boolean zigZag) {
        return (int) readVarLong(zigZag);
    }

    public long readVarLong(final boolean zigZag) throws BufferUnderflowException, UncheckedIOException {
        // TODO update the implementation, but measure if latest is still best for this code
        long value = 0;
        for (int i = 0; i < 10; i++) {
            final byte b = readByte();
            value |= (long) (b & 0x7F) << (i * 7);
            if (b >= 0) {
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw new DataEncodingException("Malformed var int");
    }

    public Bytes readVarLongBytes() throws BufferUnderflowException, UncheckedIOException {
        // TODO update the implementation, but measure if latest is still best for this code
        final byte[] bytes = new byte[10];
        for (int i = 0; i < 10; i++) {
            bytes[i] = readByte();
            if (bytes[i] >= 0) {
                return Bytes.wrap(bytes, 0, i + 1);
            }
        }
        throw new DataEncodingException("Malformed var int");
    }

    public long readBytes(@NonNull final byte[] dst) throws UncheckedIOException {
        // TODO see if we can skip the ensure and stream data directly into this
        ensure(dst.length);
        System.arraycopy(buf, pos, dst, 0, dst.length);
        pos += dst.length;
        return dst.length;
    }

    public @NonNull Bytes readBytes(final int length) throws BufferUnderflowException, UncheckedIOException {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        // TODO see if we can skip the ensure and stream data directly into this
        ensure(length);
        final var bytes = new byte[length];
        readBytes(bytes);
        return Bytes.wrap(bytes);
    }

    public long readBytes(@NonNull final ByteBuffer dst) throws UncheckedIOException {
        // This code isn't correct (for streaming), yet it pass tests
        // TODO fix logic when fixing other readBytes impl
        int len = dst.remaining();
        if (len == 0) return 0;
        if (relLimit - pos < len) {
            throw new BufferUnderflowException();
        }
        System.arraycopy(buf, pos, dst.array(), dst.arrayOffset() + dst.position(), len);
        dst.position(dst.position() + len);
        pos += len;
        return len;
    }

    public int readInt() throws BufferUnderflowException, UncheckedIOException {
        // TODO optimize
        ensure(4);
        final int b0 = readByte() & 255;
        final int b1 = readByte() & 255;
        final int b2 = readByte() & 255;
        final int b3 = readByte() & 255;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public long readLong() throws BufferUnderflowException, UncheckedIOException {
        // TODO optimize
        ensure(8);
        final byte b8 = readByte();
        final byte b7 = readByte();
        final byte b6 = readByte();
        final byte b5 = readByte();
        final byte b4 = readByte();
        final byte b3 = readByte();
        final byte b2 = readByte();
        final byte b1 = readByte();
        return (((long) b1 << 56)
                + ((long) (b2 & 255) << 48)
                + ((long) (b3 & 255) << 40)
                + ((long) (b4 & 255) << 32)
                + ((long) (b5 & 255) << 24)
                + ((b6 & 255) << 16)
                + ((b7 & 255) << 8)
                + (b8 & 255));
    }

    public float readFloat() throws BufferUnderflowException, UncheckedIOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws BufferUnderflowException, UncheckedIOException {
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
                bufferMore();
            }
            if (pos == relLimit) {
                throw new BufferUnderflowException();
            }
        }
        return buf[pos++];
    }
}
