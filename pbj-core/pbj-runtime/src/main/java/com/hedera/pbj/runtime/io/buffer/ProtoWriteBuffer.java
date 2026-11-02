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
import java.util.Objects;

/**
 * A reusable buffer that generated message writers ({@code Xxx.Writer}, from {@code pbj.writers}) encode protobuf
 * messages into, front to back, as the caller supplies field values: no model objects are built. Start a top-level
 * message with {@code new Xxx.Writer().start(buffer)}, write its fields in field-number order, and finish it with
 * {@code end()}. The result is byte-identical to {@code Xxx.PROTOBUF.toBytes(object)} for the same values.
 *
 * <p>A nested message's length comes before its bytes, so when a nested message opens, the buffer reserves a guess at
 * the size of its length prefix, and when it ends, writes the length there. When the guess was wrong the message's
 * bytes are moved by the difference, so the output is always canonical. How the guess is made is set by
 * {@link LengthReservation}; the default remembers, for each nested field, the size of the last length written there.
 *
 * <p>The buffer owns its array and doubles it when full. Read the result with {@link #bytes()} (a view, valid until the
 * buffer is written to again or reset), {@link #toBytes()} (a copy) or {@link #copyTo}. The bytes of any finished nested
 * message are {@code [writer.offset(), writer.offset() + writer.length())} of {@link #array()}, so a block item can be
 * hashed or sent as soon as it ends: see {@link MessageWriter#offset()}. Call {@link #reset()} before writing the next
 * message. Once the buffer has grown to fit the largest message, writing allocates nothing.
 *
 * <p>Methods documented as <em>for generated writers</em> are public only so that generated code in other modules can
 * call them. They do not check their arguments, and calling them directly can corrupt the output.
 *
 * <p>Not thread safe: use one buffer, and one tree of writers, per thread.
 */
public final class ProtoWriteBuffer {
    /** Writes little-endian longs into byte arrays, for the single-store varint and fixed64 values. */
    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    /** Writes little-endian ints into byte arrays, for fixed32 values. */
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    /** Free bytes {@link #ensure} keeps past the bytes asked for, so the common cases need no exact room check. */
    static final int SLACK = Long.BYTES;

    /** The size of a varint of up to 56 bits, by its number of leading zeros. */
    private static final byte[] VARINT_SIZES = new byte[65];

    static {
        for (int zeros = 0; zeros <= 64; zeros++) {
            VARINT_SIZES[zeros] = (byte) Math.max(1, (64 - zeros + 6) / 7);
        }
    }

    /** How the buffer guesses the size of a nested message's length prefix before the message is written. */
    public enum LengthReservation {
        /**
         * The size of the length last written for the same field of the same parent, or the per-type guess the first
         * time. Messages written repeatedly at the same place have similar sizes, so this rarely moves bytes.
         */
        ADAPTIVE,
        /**
         * One byte for messages whose largest encoding is under 128 bytes (only bounded fields), otherwise two, as
         * computed from the schema.
         */
        PER_TYPE,
        /** Always one byte: moves the bytes of every nested message of 128 bytes or more. For comparison. */
        ONE_BYTE
    }

    /** The output is {@code buf[0, pos)}; everything after {@code pos} belongs to the buffer. */
    byte[] buf;

    int pos;

    /** {@code buf.length - SLACK}: there is room for {@code n} more bytes while {@code pos + n <= limit}. */
    int limit;

    private final LengthReservation reservation;
    /** Whether {@link #reservation} is {@link LengthReservation#ADAPTIVE}. */
    final boolean adaptive;
    /** Whether {@link #reservation} is {@link LengthReservation#ONE_BYTE}. */
    final boolean oneByte;
    /** The writer of the top-level message written last, which may still be open, or null. */
    MessageWriter top;
    /** The number of times a nested message's bytes were moved because its length prefix was a different size. */
    private long shifts;

    /**
     * Create a buffer that reserves length prefixes {@link LengthReservation#ADAPTIVE adaptively}.
     *
     * @param initialCapacity the initial capacity in bytes; the buffer grows as needed
     */
    public ProtoWriteBuffer(final int initialCapacity) {
        this(initialCapacity, LengthReservation.ADAPTIVE);
    }

    /**
     * Create a buffer.
     *
     * @param initialCapacity the initial capacity in bytes; the buffer grows as needed
     * @param reservation how to guess the size of nested messages' length prefixes
     */
    public ProtoWriteBuffer(final int initialCapacity, @NonNull final LengthReservation reservation) {
        buf = new byte[Math.max(64, initialCapacity)];
        limit = buf.length - SLACK;
        this.reservation = reservation;
        adaptive = reservation == LengthReservation.ADAPTIVE;
        oneByte = reservation == LengthReservation.ONE_BYTE;
    }

    /**
     * Discard the output, ready to write another message. Any message still being written is abandoned, and its
     * writers can't be used until they are started again.
     */
    public void reset() {
        if (top != null) {
            top.abandon();
            top = null;
        }
        pos = 0;
    }

    /** @return the number of bytes written since the last reset */
    public int length() {
        return pos;
    }

    /**
     * @return the backing array; the output is {@code [0, length())}. It is replaced when the buffer grows, so get it
     *     again after writing.
     */
    public byte[] array() {
        return buf;
    }

    /** @return a view of the output, valid until the buffer is written to or reset */
    public @NonNull Bytes bytes() {
        return Bytes.wrap(buf, 0, pos);
    }

    /**
     * A view of part of the output, such as the bytes of a finished nested message.
     *
     * @param offset where the bytes start
     * @param length the number of bytes
     * @return a view of {@code [offset, offset + length)}, valid until the buffer is written to or reset
     */
    public @NonNull Bytes bytes(final int offset, final int length) {
        if (offset < 0 || length < 0 || offset + length > pos) {
            throw new IndexOutOfBoundsException(
                    "[" + offset + ", " + (offset + length) + ") is not within the output [0, " + pos + ")");
        }
        return Bytes.wrap(buf, offset, length);
    }

    /** @return a copy of the output */
    public @NonNull Bytes toBytes() {
        return Bytes.wrap(Arrays.copyOf(buf, pos));
    }

    /**
     * Copy the output into an array.
     *
     * @param dst the destination
     * @param offset where in {@code dst} to start
     * @return the number of bytes copied
     */
    public int copyTo(@NonNull final byte[] dst, final int offset) {
        System.arraycopy(buf, 0, dst, offset, pos);
        return pos;
    }

    /**
     * @return how many times a nested message's bytes have been moved, since the buffer was created, because its length
     *     prefix needed a different number of bytes than reserved
     */
    public long shiftCount() {
        return shifts;
    }

    /** @return how this buffer guesses the size of nested messages' length prefixes */
    public @NonNull LengthReservation lengthReservation() {
        return reservation;
    }

    // ------------------------------------------------------------------------------------- nested messages' lengths

    /**
     * The number of bytes to reserve for a nested message's length prefix.
     *
     * @param perType the guess from the message's type, 1 or 2
     * @param last the size of the length last written for this field, or 0 if none has been
     * @return the number of bytes to reserve
     */
    int reserveSize(final int perType, final int last) {
        if (adaptive) {
            return last != 0 ? last : perType;
        }
        return oneByte ? 1 : perType;
    }

    /**
     * Write the length of a nested message, now complete, before it, when the bytes reserved for it are not the
     * length's size: move the message's bytes by the difference first.
     *
     * @param start where the message's bytes start, after the reserved bytes
     * @param reserved the number of bytes reserved for the length
     * @param length the message's length, {@code pos - start}
     * @return where the message's bytes start now
     */
    int moveAndWriteLength(final int start, final int reserved, final int length) {
        final int size = varintSize(length);
        int bodyStart = start;
        if (size != reserved) {
            final int delta = size - reserved;
            if (delta > 0) {
                ensure(delta);
            }
            System.arraycopy(buf, start, buf, start + delta, length);
            pos += delta;
            bodyStart += delta;
            shifts++;
        }
        // the length, at most five bytes as it is an int
        int p = bodyStart - size;
        int v = length;
        while ((v & ~0x7F) != 0) {
            buf[p++] = (byte) (v | 0x80);
            v >>>= 7;
        }
        buf[p] = (byte) v;
        return bodyStart;
    }

    /** The number of bytes of the unsigned varint encoding of a non-negative int. */
    static int varintSize(final int value) {
        return VARINT_SIZES[Long.numberOfLeadingZeros(value)];
    }

    // ------------------------------------------------------------------------------------ for generated writers
    // Each field a generated writer writes is one call: it makes room, then writes the tag and the value. Tags are
    // given as their int value, which is negative for field numbers from 2^28, and written as unsigned varints.

    /**
     * For generated writers: the write position, where the next byte goes.
     *
     * @return the number of bytes written since the last reset
     */
    public int position() {
        return pos;
    }

    /**
     * For generated writers: record that {@code n} bytes have been written into {@link #array()} at
     * {@link #position()}, by a codec writing an embedded object, after {@link #ensure}.
     *
     * @param n the number of bytes written
     */
    public void advance(final int n) {
        pos += n;
    }

    /**
     * For generated writers: make room for {@code n} more bytes, before a codec writes an embedded object into
     * {@link #array()}.
     *
     * @param n the number of bytes
     */
    public void ensure(final int n) {
        if (n > limit - pos) {
            grow(n);
        }
    }

    void grow(final int n) {
        final long needed = (long) pos + n + SLACK;
        if (needed > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException("A ProtoWriteBuffer can't hold more than 2 GB");
        }
        buf = Arrays.copyOf(buf, (int) Math.max(Math.min((long) buf.length * 2, Integer.MAX_VALUE - 8), needed));
        limit = buf.length - SLACK;
    }

    /**
     * For generated writers: a field with a varint value: {@code int32}, {@code int64}, {@code uint32},
     * {@code uint64}, {@code sint32} and {@code sint64} (already zigzag encoded), {@code bool}, enums, and lengths.
     *
     * <p>This and the other {@code tag…} methods are kept small, with only the common case inline, so the JIT can
     * inline them into large generated methods without running out of its budget for inlining.
     *
     * @param tag the field's tag
     * @param value the value, as an unsigned 64-bit number
     */
    public void tagVarint(final int tag, final long value) {
        final int p = pos;
        if (((tag & ~0x7F) | (value & ~0x7FL)) == 0 && p < limit) {
            // a one-byte tag and value, as for bools, enums and small numbers
            final byte[] b = buf;
            b[p] = (byte) tag;
            b[p + 1] = (byte) value;
            pos = p + 2;
        } else {
            tagVarintWide(tag, value);
        }
    }

    private void tagVarintWide(final int tag, final long value) {
        if (limit - pos < 15) {
            grow(15);
        }
        final byte[] b = buf;
        int p = pos;
        if ((tag & ~0x7F) == 0) {
            b[p++] = (byte) tag;
        } else {
            p = tagWide(b, p, tag);
        }
        if ((value & ~0x7FL) == 0) {
            b[p] = (byte) value;
            pos = p + 1;
        } else {
            pos = varintWide(b, p, value);
        }
    }

    /**
     * For generated writers: a field with a 32-bit value: {@code fixed32}, {@code sfixed32}, and {@code float} bits.
     *
     * @param tag the field's tag
     * @param value the value
     */
    public void tagFixed32(final int tag, final int value) {
        final int p = pos;
        if ((tag & ~0x7F) == 0 && p < limit) {
            buf[p] = (byte) tag;
            INT_LE.set(buf, p + 1, value);
            pos = p + 1 + Integer.BYTES;
        } else {
            tagFixed32Wide(tag, value);
        }
    }

    private void tagFixed32Wide(final int tag, final int value) {
        if (limit - pos < 9) {
            grow(9);
        }
        final int p = tagWide(buf, pos, tag);
        INT_LE.set(buf, p, value);
        pos = p + Integer.BYTES;
    }

    /**
     * For generated writers: a field with a 64-bit value: {@code fixed64}, {@code sfixed64}, and {@code double} bits.
     *
     * @param tag the field's tag
     * @param value the value
     */
    public void tagFixed64(final int tag, final long value) {
        final int p = pos;
        if ((tag & ~0x7F) == 0 && p < limit) {
            buf[p] = (byte) tag;
            LONG_LE.set(buf, p + 1, value);
            pos = p + 1 + Long.BYTES;
        } else {
            tagFixed64Wide(tag, value);
        }
    }

    private void tagFixed64Wide(final int tag, final long value) {
        if (limit - pos < 13) {
            grow(13);
        }
        final int p = tagWide(buf, pos, tag);
        LONG_LE.set(buf, p, value);
        pos = p + Long.BYTES;
    }

    /**
     * For generated writers: a string field: its tag, length and UTF-8 bytes.
     *
     * @param tag the field's tag
     * @param value the string
     */
    public void tagString(final int tag, @NonNull final String value) {
        tagString(tag, value, ProtoWriterTools.sizeOfStringNoTag(value));
    }

    /**
     * For generated writers: a string field: its tag, length and UTF-8 bytes.
     *
     * @param tag the field's tag
     * @param value the string
     * @param utf8Length the length of its UTF-8 encoding, {@code ProtoWriterTools.sizeOfStringNoTag(value)}
     */
    public void tagString(final int tag, @NonNull final String value, final int utf8Length) {
        final int p = tagLength(tag, utf8Length);
        pos = p + Utf8Tools.encodeUtf8(buf, p, value);
    }

    /**
     * For generated writers: a bytes field, or a string or message field from encoded bytes: tag, length and bytes.
     *
     * @param tag the field's tag
     * @param value the bytes
     */
    public void tagBytes(final int tag, @NonNull final Bytes value) {
        final int p = tagLength(tag, (int) value.length());
        pos = p + value.writeTo(buf, p);
    }

    /**
     * For generated writers: a bytes field, or a string field from UTF-8 already checked: tag, length and bytes.
     *
     * @param tag the field's tag
     * @param value the array holding the bytes
     * @param offset where they start
     * @param length how many there are
     */
    public void tagBytes(final int tag, @NonNull final byte[] value, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, value.length);
        final int p = tagLength(tag, length);
        System.arraycopy(value, offset, buf, p, length);
        pos = p + length;
    }

    /** Make room for a delimited field, write its tag and length, and return where its bytes go. */
    private int tagLength(final int tag, final int length) {
        if (limit - pos < 10 + length) {
            grow(10 + length);
        }
        final byte[] b = buf;
        int p = pos;
        if ((tag & ~0x7F) == 0) {
            b[p++] = (byte) tag;
        } else {
            p = tagWide(b, p, tag);
        }
        if ((length & ~0x7F) == 0) {
            b[p] = (byte) length;
            return p + 1;
        }
        return varintWide(b, p, length);
    }

    /**
     * For generated writers: an unknown field, kept by a model object: its tag, then its encoded value as it is.
     *
     * @param tag the field's tag
     * @param value the encoded value, with its length if it is delimited
     */
    public void tagRaw(final int tag, @NonNull final Bytes value) {
        ensure(5 + (int) value.length());
        tagUnchecked(tag);
        pos += value.writeTo(buf, pos);
    }

    /**
     * For generated writers: check that the bytes given for a string field are well-formed UTF-8, before writing them
     * with {@link #tagBytes(int, byte[], int, int)}.
     *
     * @param utf8 the bytes
     * @param offset where they start
     * @param length how many there are
     * @throws IllegalArgumentException if the bytes are not well-formed UTF-8, which parsing would reject
     * @throws IndexOutOfBoundsException if the range is not within {@code utf8}
     */
    public static void checkUtf8(@NonNull final byte[] utf8, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, utf8.length);
        if (!Utf8Tools.isWellFormedUtf8(utf8, offset, offset + length)) {
            throw new IllegalArgumentException("A string field's bytes must be well-formed UTF-8");
        }
    }

    /**
     * For generated writers: a message field from a model object, with its tag, length and contents, written with its
     * codec. Used for messages from other modules, whose codecs generated code can't call directly.
     *
     * @param tag the field's tag
     * @param value the message
     * @param codec its codec
     * @param <T> the message type
     */
    public <T> void tagMessage(
            final int tag, @NonNull final T value, @NonNull final com.hedera.pbj.runtime.Codec<T> codec) {
        final int size = codec.measureRecord(value);
        tagVarint(tag, size);
        ensure(size);
        final int written = codec.write(value, buf, pos);
        if (written != size) {
            throw new IllegalStateException("Wrote " + written + " bytes, but measured " + size);
        }
        pos += written;
    }

    // ------------------------------------------------------------------------ packed repeated scalars, in one call
    // Each writes a packed field's tag, length and values; generated writers leave out fields with no values.

    /**
     * For generated writers: packed {@code int32} or enum values, as sign-extended varints.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedInt32(final int tag, @NonNull final int[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        int length = 0;
        for (int i = offset; i < offset + count; i++) {
            length += ProtoWriterTools.sizeOfVarInt32(values[i]);
        }
        tagVarint(tag, length);
        ensure(length);
        for (int i = offset; i < offset + count; i++) {
            varintUnchecked(values[i]);
        }
    }

    /**
     * For generated writers: packed {@code uint32} values.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedUInt32(final int tag, @NonNull final int[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        int length = 0;
        for (int i = offset; i < offset + count; i++) {
            length += ProtoWriterTools.sizeOfUnsignedVarInt32(values[i]);
        }
        tagVarint(tag, length);
        ensure(length);
        for (int i = offset; i < offset + count; i++) {
            varintUnchecked(Integer.toUnsignedLong(values[i]));
        }
    }

    /**
     * For generated writers: packed {@code sint32} values, zigzag encoded.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedSInt32(final int tag, @NonNull final int[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        int length = 0;
        for (int i = offset; i < offset + count; i++) {
            length += ProtoWriterTools.sizeOfUnsignedVarInt32((values[i] << 1) ^ (values[i] >> 31));
        }
        tagVarint(tag, length);
        ensure(length);
        for (int i = offset; i < offset + count; i++) {
            varintUnchecked(Integer.toUnsignedLong((values[i] << 1) ^ (values[i] >> 31)));
        }
    }

    /**
     * For generated writers: packed {@code int64} or {@code uint64} values.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedInt64(final int tag, @NonNull final long[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        int length = 0;
        for (int i = offset; i < offset + count; i++) {
            length += ProtoWriterTools.sizeOfUnsignedVarInt64(values[i]);
        }
        tagVarint(tag, length);
        ensure(length);
        for (int i = offset; i < offset + count; i++) {
            varintUnchecked(values[i]);
        }
    }

    /**
     * For generated writers: packed {@code sint64} values, zigzag encoded.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedSInt64(final int tag, @NonNull final long[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        int length = 0;
        for (int i = offset; i < offset + count; i++) {
            length += ProtoWriterTools.sizeOfUnsignedVarInt64((values[i] << 1) ^ (values[i] >> 63));
        }
        tagVarint(tag, length);
        ensure(length);
        for (int i = offset; i < offset + count; i++) {
            varintUnchecked((values[i] << 1) ^ (values[i] >> 63));
        }
    }

    /**
     * For generated writers: packed {@code fixed32} or {@code sfixed32} values.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedFixed32(final int tag, @NonNull final int[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        tagVarint(tag, (long) count * Integer.BYTES);
        ensure(count * Integer.BYTES);
        for (int i = offset; i < offset + count; i++) {
            fixed32Unchecked(values[i]);
        }
    }

    /**
     * For generated writers: packed {@code fixed64} or {@code sfixed64} values.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedFixed64(final int tag, @NonNull final long[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        tagVarint(tag, (long) count * Long.BYTES);
        ensure(count * Long.BYTES);
        for (int i = offset; i < offset + count; i++) {
            fixed64Unchecked(values[i]);
        }
    }

    /**
     * For generated writers: packed {@code float} values, with their raw bits, as the model writes them.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedFloat(final int tag, @NonNull final float[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        tagVarint(tag, (long) count * Integer.BYTES);
        ensure(count * Integer.BYTES);
        for (int i = offset; i < offset + count; i++) {
            fixed32Unchecked(Float.floatToRawIntBits(values[i]));
        }
    }

    /**
     * For generated writers: packed {@code double} values, with NaNs canonical, as the model writes them.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedDouble(final int tag, @NonNull final double[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        tagVarint(tag, (long) count * Long.BYTES);
        ensure(count * Long.BYTES);
        for (int i = offset; i < offset + count; i++) {
            fixed64Unchecked(Double.doubleToLongBits(values[i]));
        }
    }

    /**
     * For generated writers: packed {@code bool} values.
     *
     * @param tag the field's tag
     * @param values the values
     * @param offset the first value
     * @param count the number of values
     */
    public void packedBool(final int tag, @NonNull final boolean[] values, final int offset, final int count) {
        Objects.checkFromIndexSize(offset, count, values.length);
        tagVarint(tag, count);
        ensure(count);
        for (int i = offset; i < offset + count; i++) {
            buf[pos++] = values[i] ? (byte) 1 : 0;
        }
    }

    // ------------------------------------------------------------------------------------------ unchecked writes
    // For this package's writers, after ensure has made room.

    /** Write a tag, an unsigned varint of up to five bytes. */
    void tagUnchecked(final int tag) {
        if ((tag & ~0x7F) == 0) {
            buf[pos++] = (byte) tag;
        } else {
            pos = tagWide(buf, pos, tag);
        }
    }

    /**
     * Write a tag of one to five bytes.
     *
     * @return the position after it
     */
    static int tagWide(final byte[] b, final int p, final int tag) {
        if ((tag & ~0x7F) == 0) {
            b[p] = (byte) tag;
            return p + 1;
        }
        if ((tag & ~0x3FFF) == 0) {
            b[p] = (byte) (tag | 0x80);
            b[p + 1] = (byte) (tag >>> 7);
            return p + 2;
        }
        return varintWide(b, p, Integer.toUnsignedLong(tag));
    }

    /** Write one byte. */
    void writeByteUnchecked(final int b) {
        buf[pos++] = (byte) b;
    }

    /** Write an unsigned varint of up to ten bytes. */
    void varintUnchecked(final long value) {
        if ((value >>> 7) == 0) {
            buf[pos++] = (byte) value;
            return;
        }
        pos = varintWide(buf, pos, value);
    }

    /**
     * Write an unsigned varint of two to ten bytes.
     *
     * @return the position after it
     */
    static int varintWide(final byte[] b, final int p, final long value) {
        // exact-size stores: a single 8-byte store into the slack was slower on AMD EPYC, for writing block items
        return p + ProtoArrayWriterTools.writeVarInt(b, p, b.length, value);
    }

    /** Write a little-endian 32-bit value. */
    void fixed32Unchecked(final int value) {
        INT_LE.set(buf, pos, value);
        pos += Integer.BYTES;
    }

    /** Write a little-endian 64-bit value. */
    void fixed64Unchecked(final long value) {
        LONG_LE.set(buf, pos, value);
        pos += Long.BYTES;
    }
}
