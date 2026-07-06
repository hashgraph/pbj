// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link WritableSequentialData} backed by an internal byte buffer, meant to be easy for the JIT to optimize.
 * Bytes are accumulated in a private buffer and flushed to an underlying {@link WritableSequentialData} when the buffer fills.
 */
public class SlimWriter {
    private byte[] buf;
    private int pos, cap;
    private int offset; // absolute position of buf[0] (total bytes already flushed)
    private WritableSequentialData output;
    private boolean expandable;

    public static final int BufferOverflow = 11, IOError = 5;

    /**
     * Creates a SlimWriter that accumulates writes into an internal buffer and flushes to {@code output}.
     *
     * @param output the destination to flush into
     */
    public SlimWriter(@NonNull WritableSequentialData output) {
        this.output = output;
        this.expandable = false;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
    }

    public SlimWriter() {
        this.output = null;
        this.expandable = true;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
    }

    public SlimWriter(int reserveSize) {
        this.output = null;
        this.expandable = true; // should the reserveSize be the max?
        buf = new byte[Math.max(reserveSize, 16 << 10)]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
    }

    public void reserveRel(int len) {
        if (pos + len <= cap) return;
        flushOrGrow(len);
    }

    public void placehold(int len) {
        pos += len;
    }

    public int position() {
        return offset + pos;
    }

    public void writeAt(int pos, byte value) {
        buf[pos - offset] = value;
    }

    // converts a 1 byte varint placeholder to a 2 byte varint. pos is the absolute position of the placeholder.
    public void reinsertVarInt(int position) {
        int relPos = position - offset;
        int len = this.pos - relPos - 1;
        System.arraycopy(buf, relPos + 1, buf, relPos + 2, len);
        buf[relPos] = (byte) ((len & 0x7F) | 0x80);
        buf[relPos + 1] = (byte) (len >>> 7);
        this.pos++;
    }

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

    public void writeBytes(@NonNull byte[] src) {
        writeBytes(src, 0, src.length);
    }

    public void writeBytes(@NonNull byte[] src, int srcOffset, int length) {
        if (length <= 0) return;
        if (pos + length <= cap && (!expandable || length < 2048)) {
            System.arraycopy(src, srcOffset, buf, pos, length);
            pos += length;
            return;
        }
        writeBytesInternal(src, srcOffset, length);
    }

    private void writeBytesInternal(byte[] src, int srcOffset, int length) {
        flushOrGrow(length);
        if (!expandable && length >= 2048) {
            output.writeBytes(src, srcOffset, length);
            offset += length;
            return;
        }
        System.arraycopy(src, srcOffset, buf, pos, length);
        pos += length;
    }

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
        if (expandable) {
            flushOrGrow(len);
            src.getBytes(0, buf, pos, len);
            pos += len;
        } else {
            // Maybe the below can be improved. This path seems rare
            int srcOffset = 0;
            int remaining = len;
            while (remaining > 0) {
                if (pos == cap) {
                    output.writeBytes(buf, 0, pos);
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

    public void writeInt(int value) {
        writeIntBE(value);
    }

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

    public void writeFloatLE(float value) {
        writeIntLE(Float.floatToRawIntBits(value));
    }

    public void writeDoubleLE(double value) {
        writeLongLE(Double.doubleToRawLongBits(value));
    }

    public void writeLong(long value) {
        writeLongBE(value);
    }

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

    public void writeVarIntZZ(int value) {
        // Delegate to writeVarLong so negative INT32 values are sign-extended to 64 bits,
        // producing the required 10-byte varint encoding per the protobuf spec.
        writeVarLongZZ(value);
    }

    public void writeVarLongZZ(long value) {
        writeVarLongNoZZ((value << 1) ^ (value >> 63));
    }

    public void writeVarInt(int value, boolean zigZag) {
        // Delegate to writeVarLong so negative INT32 values are sign-extended to 64 bits,
        // producing the required 10-byte varint encoding per the protobuf spec.
        writeVarLong(value, zigZag);
    }

    public void writeVarLong(long value, boolean zigZag) {
        long v = zigZag ? (value << 1) ^ (value >> 63) : value;
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

    private void writeVarLongInternal(long v) {
        flushOrGrow(10);
        while ((v & ~0x7FL) != 0) {
            buf[pos++] = (byte) (((int) v & 0x7F) | 0x80);
            v >>>= 7;
        }
        buf[pos++] = (byte) v;
    }

    public void writeVarIntNoZZ(int value) {
        writeVarLongNoZZ(value);
    }

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

    public void flush() {
        if (output == null || pos == 0) return;
        output.writeBytes(buf, 0, pos);
        offset += pos;
        pos = 0;
    }

    private void flushOrGrow(int minLength) {
        if (expandable) {
            int power2Capacity =
                    (int) Math.min(Integer.MAX_VALUE, 2L << (63 - Long.numberOfLeadingZeros(pos + minLength)));
            var newBuf = new byte[power2Capacity];
            System.arraycopy(buf, 0, newBuf, 0, pos);
            buf = newBuf;
            cap = buf.length;
        } else {
            output.writeBytes(buf, 0, pos);
            offset += pos;
            pos = 0;
            if (minLength > cap) {
                throw new IllegalArgumentException("minLength is greater than capacity");
            }
        }
    }

    public void reset() {
        flush();
        pos = 0;
        offset = 0;
    }

    public byte[] toByteArray() {
        if (output != null) throw new RuntimeException("toByteArray used on a streaming object");
        var bytes = new byte[pos];
        System.arraycopy(buf, 0, bytes, 0, pos);
        return bytes;
    }

    public SlimBuffer toSlimBuffer() {
        return new SlimBuffer(buf, 0, pos);
    }
}
