// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class SlimBuffer {
    byte[] buf = new byte[16 << 10];
    int pos, end;
    int relLimit;
    long absoluteLimit, offset;
    boolean seenEOF;
    private ReadableSequentialData input;

    public SlimBuffer(ReadableSequentialData input) {
        this.input = input;
    }

    private void grow(int minCap) {
        int newCap = Math.max(buf.length * 2, 1 << 63 - Long.numberOfLeadingZeros((minCap << 1) - 1));
        byte[] newCopy = new byte[newCap];
        System.arraycopy(buf, 0, newCopy, 0, end);
        buf = newCopy;
    }

    private void bufferMore() {
        bufferMore(1);
    }

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
            if (rdlen <= 0) {
                int q = 1;
            }
        } catch (UncheckedIOException e) {
            throw e;
        }
    }

    public boolean hasMore() {
        if (!seenEOF && pos == end) {
            bufferMore();
        }
        while (true) {
            if (pos < relLimit) return true;
            if (seenEOF || relLimit < end) return false;
            bufferMore();
        }
    }

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
        ensure(dst.length);
        System.arraycopy(buf, pos, dst, 0, dst.length);
        pos += dst.length;
        return dst.length;
    }

    public @NonNull Bytes readBytes(final int length) throws BufferUnderflowException, UncheckedIOException {
        if (length < 0) {
            throw new IllegalArgumentException();
        }
        ensure(length);
        final var bytes = new byte[length];
        readBytes(bytes);
        return Bytes.wrap(bytes);
    }

    public long readBytes(@NonNull final ByteBuffer dst) throws UncheckedIOException {
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
        ensure(4);
        final int b0 = readByte() & 255;
        final int b1 = readByte() & 255;
        final int b2 = readByte() & 255;
        final int b3 = readByte() & 255;
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public long readLong() throws BufferUnderflowException, UncheckedIOException {
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
