// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.ProtoArrayWriterTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.Utf8Tools;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * A reusable buffer that protobuf messages are written into back to front, from the end of the buffer toward its
 * start. A nested message is written before its length prefix and tag, so its length is simply the number of bytes
 * written since it began: there is no measuring pass. Fields and list elements are written in reverse order, so the
 * result reads front to back in field order, byte-identical to the forward writers.
 *
 * <p>Write with {@code Codec.writeReverse(item, buffer)}, then read the result with {@link #toBytes()} or
 * {@link #copyTo}, which copy it, or {@link #unsafeBytes()}, which doesn't (a view, valid only until the buffer is reset
 * or written to again). Call {@link #reset()} before reusing
 * the buffer. Once it has grown to fit the largest message, writing allocates nothing.
 *
 * <p>Not thread safe.
 */
public final class ReverseBuffer {
    private byte[] buf;
    /** Index of the first written byte: {@code buf[pos..]} is the output. */
    private int pos;

    /**
     * Create a buffer.
     *
     * @param initialCapacity the initial capacity in bytes; the buffer grows as needed
     */
    public ReverseBuffer(final int initialCapacity) {
        buf = new byte[capacityFor(Math.max(MIN_CAPACITY, initialCapacity))];
        pos = buf.length;
    }

    /** The smallest capacity, in bytes. */
    private static final int MIN_CAPACITY = 16;

    /** The largest capacity that is a power of two and an array length. */
    private static final int MAX_POWER_OF_TWO_CAPACITY = 1 << 30;

    /**
     * The capacity for at least {@code needed} bytes: the next power of two, so a buffer that grows by doubling only
     * ever has power-of-two sizes. Above {@value #MAX_POWER_OF_TWO_CAPACITY} it is exactly {@code needed}.
     */
    private static int capacityFor(final int needed) {
        if (needed > MAX_POWER_OF_TWO_CAPACITY) {
            return needed;
        }
        return Math.max(MIN_CAPACITY, Integer.highestOneBit(needed - 1) << 1);
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
        // double, or more if one write needs more, staying a power of two
        final int capacity = capacityFor(Math.addExact(length, n));
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
        final int size = ProtoWriterTools.sizeOfUnsignedVarInt64(value);
        ensure(size);
        pos -= size;
        ProtoArrayWriterTools.writeUnsignedVarInt(buf, pos, value);
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

    /**
     * The output, without copying it. This is unsafe: unlike other {@link Bytes}, it is not immutable. It shares this
     * buffer's array, so it changes when the buffer is reset and written to again, and a buffer that grows leaves it
     * behind. Use it right away, or use {@link #toBytes()} for a copy.
     *
     * @return a view of the output, valid until the buffer is reset or written to
     */
    public @NonNull Bytes unsafeBytes() {
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
