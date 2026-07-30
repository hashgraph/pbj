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
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public class PbjWriter implements AutoCloseable {
    private byte[] buf;
    private int pos, cap;
    private int offset, err;
    private String errMessage;
    RuntimeException cause;
    private OutputStream output;
    private boolean reuseable;

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

    public PbjWriter(@NonNull OutputStream output) {
        this.output = output;
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
        reuseable = true;
    }

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

    public PbjWriter(byte[] buffer, int pos, int cap) {
        this.buf = buffer;
        this.pos = pos;
        this.cap = cap;
    }

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

    public PbjWriter() {
        buf = new byte[16 << 10]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
        reuseable = true;
    }

    // reserveSize is minimum size, this may grow
    public PbjWriter(int reserveSize) {
        buf = new byte[Math.max(reserveSize, 16 << 10)]; // 16k is friendly to x86-64 L1 cache
        cap = buf.length;
        reuseable = true;
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

    public void writeBoolean(boolean b) {
        writeByte((byte) (b ? 1 : 0));
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

    public void writeBytes(@NonNull final BufferedData src) throws BufferOverflowException, UncheckedIOException {
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

    public void writeBytes(@NonNull byte[] src) {
        writeBytes(src, 0, src.length);
    }

    public void writeBytes(@NonNull byte[] src, int srcOffset, int length) {
        if (length <= 0) return;
        if (pos + length <= cap && (output == null || length < 2048)) {
            System.arraycopy(src, srcOffset, buf, pos, length);
            pos += length;
            return;
        }
        writeBytesInternal(src, srcOffset, length);
    }

    private void writeBytesInternal(byte[] src, int srcOffset, int length) {
        flushOrGrow(length);
        if (output != null && length >= 2048) {
            try {
                output.write(src, srcOffset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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

    public void writeFloat(float value) {
        writeFloatBE(value);
    }

    public void writeFloatBE(float value) {
        writeIntBE(Float.floatToRawIntBits(value));
    }

    public void writeFloatLE(float value) {
        writeIntLE(Float.floatToRawIntBits(value));
    }

    public void writeDouble(double value) {
        writeDoubleBE(value);
    }

    public void writeDoubleBE(double value) {
        writeLongBE(Double.doubleToRawLongBits(value));
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
        long v = zigZag ? ((long) value << 1) ^ ((long) value >> 63) : value;
        writeVarLongNoZZ(v);
    }

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

    @Override
    public void close() {
        if (output == null) return;
        flush();
        try {
            output.close();
        } catch (IOException ignored) {
        }
        err = Closed;
    }

    private void flushOrGrow(int minLength) {
        if (output != null) {
            if (minLength > cap) {
                throw new UncheckedIOException(new IOException("minLength is greater than capacity"));
            }
            try {
                output.write(buf, 0, pos);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            offset += pos;
            pos = 0;
        } else if (reuseable) {
            int power2Capacity = (int) 2L << (63 - Long.numberOfLeadingZeros(Math.max(buf.length, pos + minLength)));
            var newBuf = new byte[power2Capacity];
            System.arraycopy(buf, 0, newBuf, 0, pos);
            buf = newBuf;
            cap = buf.length;
        }
        // A possible else case is using a byte array and trying to reserve (or grow) past the length of it
        // reserving shouldn't cause a throw so this else is ignored
    }

    public void reset() {
        flush();
        pos = 0;
        offset = 0;
        err = 0;
        errMessage = null;
        cause = null;
    }

    public void resetWithNull() {
        resetWith((OutputStream) null);
    }

    public void resetWith(@NonNull OutputStream out) {
        reset();
        if (!reuseable) {
            setError(UsageError, "resetWith on non-reuseable PbjWriter");
            return;
        }
        output = out;
    }

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

    public byte[] internalArray() {
        return buf;
    }

    public Bytes internalArrayWrapped() {
        return Bytes.wrap(buf, 0, pos);
    }

    public Bytes toByteArrayWrapped() {
        if (output != null) {
            setError(UsageError, "toByteArrayWrapped used on a streaming object");
            return Bytes.EMPTY;
        }
        return Bytes.wrap(toByteArray());
    }

    public Bytes takeBytes() {
        if (output != null) {
            setError(UsageError, "takeBytes used on a streaming object");
            return Bytes.EMPTY;
        }
        if (!reuseable) {
            setError(UsageError, "takeBytes used when object didn't create the array");
            return Bytes.EMPTY;
        }
        Bytes bytes = Bytes.wrap(buf, 0, pos);
        buf = null;
        err = Closed;
        return bytes;
    }

    public byte[] toByteArray() {
        if (output != null) {
            setError(UsageError, "toByteArray used on a streaming object");
            return null;
        }
        var bytes = new byte[pos];
        System.arraycopy(buf, 0, bytes, 0, pos);
        return bytes;
    }

    public PbjReader toPbjReader() {
        return new PbjReader(buf, 0, pos);
    }

    public void setError(int errorKind, String message) {
        if (err > 0) return;
        err = errorKind;
        errMessage = message;
        cause = new RuntimeException(message);
    }

    public int error() {
        return err;
    }

    public void throwOnError() {
        if (err > 0) {
            throw cause;
        }
    }

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
            writeAt(pos, (byte) utf8Len);
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
        writeAt(pos, (byte) ((utf8Len & 0x7F) | 0x80));
        writeAt(pos + 1, (byte) (utf8Len >>> 7));
    }

    public void skip(int count) {
        pos += count;
    }
}
