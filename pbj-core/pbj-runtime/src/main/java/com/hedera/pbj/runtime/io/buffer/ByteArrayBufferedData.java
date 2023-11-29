package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * BufferedData subclass for instances backed by a byte array. Provides slightly more optimized
 * versions of several methods to get / read / write bytes using {@link System#arraycopy} and
 * direct array reads / writes.
 */
final class ByteArrayBufferedData extends BufferedData {

    // Backing byte array
    private final byte[] array;

    // This data buffer's offset into the backing array. See ByteBuffer.arrayOffset() for details
    private final int arrayOffset;

    ByteArrayBufferedData(final ByteBuffer buffer) {
        super(buffer);
        if (!buffer.hasArray()) {
            throw new IllegalArgumentException("Cannot create a ByteArrayBufferedData over a buffer with no array");
        }
        this.array = buffer.array();
        this.arrayOffset = buffer.arrayOffset();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[");
        for (int i = 0; i < buffer.limit(); i++) {
            int v = array[arrayOffset + i] & 0xFF;
            sb.append(v);
            if (i < (buffer.limit() - 1)) {
                sb.append(',');
            }
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(final long offset, @NonNull final byte[] bytes) {
        if (offset < 0 || offset >= length()) {
            throw new IndexOutOfBoundsException();
        }

        final int len = bytes.length;
        if (length() - offset < len) {
            return false;
        }

        final int fromThisIndex = Math.toIntExact(arrayOffset + offset);
        final int fromToIndex = fromThisIndex + len;
        return Arrays.equals(array, fromThisIndex, fromToIndex, bytes, 0, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(final boolean zigZag) {
        int tempPos = buffer.position() + arrayOffset;
        int lastPos = buffer.limit() + arrayOffset;

        if (lastPos == tempPos) {
            throw new BufferUnderflowException();
        }

        int x;
        if ((x = array[tempPos++]) >= 0) {
            buffer.position(tempPos - arrayOffset);
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (lastPos - tempPos < 9) {
            return (int) readVarIntLongSlow(zigZag);
        } else if ((x ^= (array[tempPos++] << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (array[tempPos++] << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (array[tempPos++] << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = array[tempPos++];
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && array[tempPos++] < 0
                    && array[tempPos++] < 0
                    && array[tempPos++] < 0
                    && array[tempPos++] < 0
                    && array[tempPos++] < 0) {
                return (int) readVarIntLongSlow(zigZag);
            }
        }
        buffer.position(tempPos - arrayOffset);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(final boolean zigZag) {
        int tempPos = buffer.position() + arrayOffset;
        int lastPos = buffer.limit() + arrayOffset;

        if (lastPos == tempPos) {
            throw new BufferUnderflowException();
        }

        long x;
        int y;
        if ((y = array[tempPos++]) >= 0) {
            buffer.position(tempPos - arrayOffset);
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (lastPos - tempPos < 9) {
            return readVarIntLongSlow(zigZag);
        } else if ((y ^= (array[tempPos++] << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (array[tempPos++] << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (array[tempPos++] << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) array[tempPos++] << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) array[tempPos++] << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) array[tempPos++] << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) array[tempPos++] << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) array[tempPos++] << 56);
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
                if (array[tempPos++] < 0L) {
                    return readVarIntLongSlow(zigZag);
                }
            }
        }
        buffer.position(tempPos - arrayOffset);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getByte(final long offset) {
        validateCanRead(offset, 0);
        return array[Math.toIntExact(arrayOffset + offset)];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        validateLen(maxLength);
        final long len = Math.min(maxLength, length() - offset);
        validateCanRead(offset, len);
        if (len == 0) {
            return 0;
        }
        System.arraycopy(array, Math.toIntExact(arrayOffset + offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        if (!dst.hasArray()) {
            return super.getBytes(offset, dst);
        }
        final long len = Math.min(length() - offset, dst.remaining());
        final byte[] dstArr = dst.array();
        final int dstPos = dst.position();
        final int dstArrOffset = dst.arrayOffset();
        System.arraycopy(
                array, Math.toIntExact(arrayOffset + offset), dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes getBytes(final long offset, final long len) {
        validateLen(len);
        if (len == 0) {
            return Bytes.EMPTY;
        }
        if (length() - offset < len) {
            throw new BufferUnderflowException();
        }
        final byte[] res = new byte[Math.toIntExact(len)];
        System.arraycopy(array, Math.toIntExact(arrayOffset + offset), res, 0, res.length);
        return Bytes.wrap(res);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() {
        if (remaining() == 0) {
            throw new BufferUnderflowException();
        }
        final int pos = buffer.position();
        final byte res = array[arrayOffset + pos];
        buffer.position(pos + 1);
        return res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readBytes(@NonNull byte[] dst, int offset, int maxLength) {
        validateLen(maxLength);
        final var len = Math.toIntExact(Math.min(maxLength, remaining()));
        if (len == 0) {
            return 0;
        }
        final int pos = buffer.position();
        System.arraycopy(array, arrayOffset + pos, dst, offset, len);
        buffer.position(pos + len);
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        if (!dst.hasArray()) {
            return super.readBytes(dst);
        }
        final long len = Math.min(remaining(), dst.remaining());
        final int pos = buffer.position();
        final byte[] dstArr = dst.array();
        final int dstPos = dst.position();
        final int dstArrOffset = dst.arrayOffset();
        System.arraycopy(array, arrayOffset + pos, dstArr, dstArrOffset + dstPos, Math.toIntExact(len));
        buffer.position(Math.toIntExact(pos + len));
        dst.position(Math.toIntExact(dstPos + len));
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Bytes readBytes(final int len) {
        validateLen(len);
        final int pos = buffer.position();
        validateCanRead(pos, len);
        if (len == 0) {
            return Bytes.EMPTY;
        }
        final byte[] res = new byte[len];
        System.arraycopy(array, arrayOffset + pos, res, 0, len);
        buffer.position(pos + len);
        return Bytes.wrap(res);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(final byte b) {
        validateCanWrite(1);
        final int pos = buffer.position();
        array[arrayOffset + pos] = b;
        buffer.position(pos + 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int len) {
        validateLen(len);
        validateCanWrite(len);
        final int pos = buffer.position();
        System.arraycopy(src, offset, array, arrayOffset + pos, len);
        buffer.position(pos + len);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final ByteBuffer src) {
        if (!src.hasArray()) {
            super.writeBytes(src);
            return;
        }
        final long len = src.remaining();
        validateCanWrite(len);
        final int pos = buffer.position();
        final byte[] srcArr = src.array();
        final int srcArrOffset = src.arrayOffset();
        final int srcPos = src.position();
        System.arraycopy(srcArr, srcArrOffset + srcPos, array, arrayOffset + pos, Math.toIntExact(len));
        src.position(Math.toIntExact(srcPos + len));
        buffer.position(Math.toIntExact(pos + len));
    }
}
