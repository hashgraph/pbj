// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.ProtoArrayWriterTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.Utf8Tools;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A reusable buffer that protobuf messages are written into back to front, from the end of the buffer toward its
 * start. A nested message is written before its length prefix and tag, so its length is simply the number of bytes
 * written since it began: there is no measuring pass. Fields and list elements are written in reverse order, so the
 * result reads front to back in field order, byte-identical to the forward writers.
 *
 * <p>Write with {@code Codec.writeReverse(item, buffer)}, then read the result with {@link #bytes()} (a view, valid until
 * the buffer is reset or written to again), {@link #toBytes()} or {@link #copyTo}. Call {@link #reset()} before reusing
 * the buffer. Once it has grown to fit the largest message, writing allocates nothing.
 *
 * <p>Not thread safe.
 */
public final class ReverseBuffer {
    private static final VarHandle LONG_LITTLE_ENDIAN =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private byte[] buf;
    /** Index of the first written byte: {@code buf[pos..]} is the output. */
    private int pos;

    /**
     * Create a buffer.
     *
     * @param initialCapacity the initial capacity in bytes; the buffer grows as needed
     */
    public ReverseBuffer(final int initialCapacity) {
        buf = new byte[Math.max(16, initialCapacity)];
        pos = buf.length;
    }

    /** Discard the output, ready to write another message. */
    public void reset() {
        pos = buf.length;
    }

    /** @return the number of bytes written since the last reset */
    public int length() {
        return buf.length - pos;
    }

    /**
     * The number of bytes written so far, to pass to {@link #lengthSince} after writing a nested message. It stays
     * valid when the buffer grows.
     *
     * @return the current mark
     */
    public int mark() {
        return buf.length - pos;
    }

    /**
     * Write the length of everything written since {@code mark}, as a varint, before it.
     *
     * @param mark a value from {@link #mark()}
     */
    public void lengthSince(final int mark) {
        varint(buf.length - pos - mark);
    }

    /**
     * Make room for at least {@code n} more bytes before the output, so that many bytes can then be written with the
     * unchecked methods. Generated reverse writers call it once for a run of fields whose largest encoded size they
     * know, instead of checking before every tag and value.
     *
     * @param n the number of bytes
     */
    public void ensure(final int n) {
        if (pos < n) {
            grow(n);
        }
    }

    private void grow(final int n) {
        final int length = buf.length - pos;
        final int capacity = Math.max(buf.length * 2, length + n + 16);
        final byte[] bigger = new byte[capacity];
        System.arraycopy(buf, pos, bigger, capacity - length, length);
        buf = bigger;
        pos = capacity - length;
    }

    /**
     * Write one byte, such as a one-byte tag.
     *
     * @param b the byte
     */
    public void writeByte(final int b) {
        if (pos == 0) {
            grow(1);
        }
        buf[--pos] = (byte) b;
    }

    /**
     * Write a two-byte tag, given in wire order.
     *
     * @param b0 the tag's first byte
     * @param b1 the tag's second byte
     */
    public void writeBytes(final int b0, final int b1) {
        ensure(2);
        buf[--pos] = (byte) b1;
        buf[--pos] = (byte) b0;
    }

    /**
     * Write an unsigned varint.
     *
     * @param value the value
     */
    public void varint(final long value) {
        if ((value >>> 7) == 0 && pos > 0) {
            buf[--pos] = (byte) value;
            return;
        }
        varintWide(value);
    }

    private void varintWide(final long value) {
        if (pos >= Long.BYTES && (value >>> 56) == 0) {
            pos -= varintStore(buf, pos, value);
            return;
        }
        final int size = ProtoWriterTools.sizeOfUnsignedVarInt64(value);
        ensure(size);
        pos -= size;
        ProtoArrayWriterTools.writeUnsignedVarInt(buf, pos, value);
    }

    /**
     * Write a varint of 2 to 8 bytes, ending just before {@code end}, with one 8-byte store. The store also covers
     * the bytes below the varint, which are free space in this buffer: the output is only ever extended downward, so
     * nothing written is overwritten. A forward writer would need a guard against writing past its end instead.
     *
     * @param buf the buffer
     * @param end the index after the varint's last byte, at least 8
     * @param value the value, at least 128 and less than 2<sup>56</sup>
     * @return the varint's size in bytes
     */
    private static int varintStore(final byte[] buf, final int end, final long value) {
        final int size = (70 - Long.numberOfLeadingZeros(value)) / 7;
        // each 7-bit group in its own byte, with the continuation bit on every byte but the last
        final long groups = (value & 0x7FL)
                | (value << 1 & 0x7F00L)
                | (value << 2 & 0x7F0000L)
                | (value << 3 & 0x7F000000L)
                | (value << 4 & 0x7F00000000L)
                | (value << 5 & 0x7F0000000000L)
                | (value << 6 & 0x7F000000000000L)
                | (value << 7 & 0x7F00000000000000L);
        final long word = groups | (0x8080808080808080L >>> ((9 - size) << 3));
        // little-endian, so the varint's first byte is at end - size and its last at end - 1
        LONG_LITTLE_ENDIAN.set(buf, end - Long.BYTES, word << ((8 - size) << 3));
        return size;
    }

    /**
     * Write a little-endian 32-bit value.
     *
     * @param value the value
     */
    public void fixed32(final int value) {
        ensure(Integer.BYTES);
        pos -= Integer.BYTES;
        ProtoArrayWriterTools.writeFixed32NoTag(buf, pos, value);
    }

    /**
     * Write a little-endian 64-bit value.
     *
     * @param value the value
     */
    public void fixed64(final long value) {
        ensure(Long.BYTES);
        pos -= Long.BYTES;
        ProtoArrayWriterTools.writeFixed64NoTag(buf, pos, value);
    }

    /**
     * Write a string's UTF-8 bytes, then its length before them.
     *
     * @param value the string
     */
    public void string(final String value) {
        final int length = ProtoWriterTools.sizeOfStringNoTag(value);
        ensure(length);
        pos -= length;
        Utf8Tools.encodeUtf8(buf, pos, value);
        varint(length);
    }

    /**
     * Write a bytes value, then its length before it.
     *
     * @param value the bytes
     */
    public void bytes(@NonNull final Bytes value) {
        final int length = (int) value.length();
        ensure(length);
        pos -= length;
        value.writeTo(buf, pos);
        varint(length);
    }

    /**
     * Write one byte without checking for room: call {@link #ensure} first.
     *
     * @param b the byte
     */
    public void writeByteUnchecked(final int b) {
        buf[--pos] = (byte) b;
    }

    /**
     * Write a two-byte tag, given in wire order, without checking for room: call {@link #ensure} first.
     *
     * @param b0 the tag's first byte
     * @param b1 the tag's second byte
     */
    public void writeBytesUnchecked(final int b0, final int b1) {
        buf[--pos] = (byte) b1;
        buf[--pos] = (byte) b0;
    }

    /**
     * Write an unsigned varint without checking for room: call {@link #ensure} first, for up to 10 bytes.
     *
     * @param value the value
     */
    public void varintUnchecked(final long value) {
        if ((value >>> 7) == 0) {
            buf[--pos] = (byte) value;
            return;
        }
        if (pos >= Long.BYTES && (value >>> 56) == 0) {
            pos -= varintStore(buf, pos, value);
            return;
        }
        pos -= ProtoWriterTools.sizeOfUnsignedVarInt64(value);
        ProtoArrayWriterTools.writeUnsignedVarInt(buf, pos, value);
    }

    /**
     * Write a little-endian 32-bit value without checking for room: call {@link #ensure} first.
     *
     * @param value the value
     */
    public void fixed32Unchecked(final int value) {
        pos -= Integer.BYTES;
        ProtoArrayWriterTools.writeFixed32NoTag(buf, pos, value);
    }

    /**
     * Write a little-endian 64-bit value without checking for room: call {@link #ensure} first.
     *
     * @param value the value
     */
    public void fixed64Unchecked(final long value) {
        pos -= Long.BYTES;
        ProtoArrayWriterTools.writeFixed64NoTag(buf, pos, value);
    }

    /**
     * Reserve {@code length} bytes before the output for a forward writer, and return where they start.
     *
     * @param length the number of bytes the forward writer will write
     * @return the index in {@link #array()} to write them at
     */
    public int reserve(final int length) {
        ensure(length);
        pos -= length;
        return pos;
    }

    /** @return the backing array, for forward writers given an index by {@link #reserve} */
    public byte[] array() {
        return buf;
    }

    /** @return a view of the output, valid until the buffer is reset or written to */
    public @NonNull Bytes bytes() {
        return Bytes.wrap(buf, pos, buf.length - pos);
    }

    /** @return a copy of the output */
    public @NonNull Bytes toBytes() {
        return Bytes.wrap(Arrays.copyOfRange(buf, pos, buf.length));
    }

    /**
     * Copy the output into an array.
     *
     * @param dst the destination
     * @param offset where in {@code dst} to start
     * @return the number of bytes copied
     */
    public int copyTo(@NonNull final byte[] dst, final int offset) {
        final int length = buf.length - pos;
        System.arraycopy(buf, pos, dst, offset, length);
        return length;
    }
}
