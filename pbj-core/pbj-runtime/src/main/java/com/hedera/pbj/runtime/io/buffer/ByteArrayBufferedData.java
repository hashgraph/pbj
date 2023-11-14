package com.hedera.pbj.runtime.io.buffer;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

final class ByteArrayBufferedData extends BufferedData {

    private final byte[] array;

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
        // build string
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
    public long readVarLong(boolean zigZag) {
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


}
