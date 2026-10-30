// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.UnknownField;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.Utf8Tools;
import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A read position over a byte array, for the byte-array parsers that codecs generate with {@code pbj.fast_parse}.
 * {@code Codec.parse} uses one automatically when the input is array-backed and the codec has an array parser.
 *
 * <p>Reads are bounded by {@link #limit()}, which a nested message narrows to its own end. Methods called once per field
 * ({@link #at(byte)}, {@link #varint()}) are kept small so HotSpot inlines them; their slow paths are separate methods.
 * Every read checks the limit and throws {@link BufferUnderflowException} when the input is truncated, the same
 * exception the stream parser throws, so generated parsers wrap it in a {@link ParseException} the same way.
 */
public final class ArrayCursor {
    private byte[] in;
    /** Index in {@link #in} of the next byte to read. */
    private int p;
    /** Index in {@link #in} after the last byte that may be read. */
    private int limit;
    /** Index of the first byte, to report how many were consumed. */
    private int start;
    /**
     * The {@link Bytes} this cursor reads, if it was created from one. Like the stream parser over {@link Bytes},
     * {@link #bytes} then returns views of it instead of copies, since {@link Bytes} is immutable.
     */
    private @Nullable Bytes source;

    /**
     * Create a cursor over {@code in[offset, limit)}.
     *
     * @param in the bytes
     * @param offset index of the first byte to read
     * @param limit index after the last byte to read
     */
    public ArrayCursor(@NonNull final byte[] in, final int offset, final int limit) {
        this(in, offset, limit, null);
    }

    private ArrayCursor(@NonNull final byte[] in, final int offset, final int limit, @Nullable final Bytes source) {
        if (offset < 0 || limit < offset || limit > in.length) {
            throw new IndexOutOfBoundsException("offset " + offset + ", limit " + limit + ", length " + in.length);
        }
        this.in = in;
        this.p = offset;
        this.limit = limit;
        this.start = offset;
        this.source = source;
    }

    /**
     * A cursor over the remaining bytes of an array-backed input, or null if the input is not backed by an array.
     *
     * @param input the input
     * @return a cursor from the input's position to its limit, or null
     */
    public static @Nullable ArrayCursor of(@NonNull final ReadableSequentialData input) {
        if (input instanceof ByteArrayBufferedData data) {
            final int offset = data.arrayOffset() + Math.toIntExact(data.position());
            return new ArrayCursor(data.backingArray(), offset, offset + Math.toIntExact(data.remaining()));
        }
        return null;
    }

    /**
     * A cursor over all of a {@link Bytes}.
     *
     * @param bytes the bytes
     * @return a cursor over them
     */
    public static @NonNull ArrayCursor of(@NonNull final Bytes bytes) {
        return new ArrayCursor(
                bytes.backingArray(), bytes.backingOffset(), bytes.backingOffset() + (int) bytes.length(), bytes);
    }

    /**
     * Point this cursor at {@code in[offset, limit)}, so one cursor can be reused, for example by a view's
     * {@code wrap}.
     *
     * @param in the bytes
     * @param offset index of the first byte to read
     * @param limit index after the last byte to read
     * @return this cursor
     */
    public ArrayCursor reset(@NonNull final byte[] in, final int offset, final int limit) {
        if (offset < 0 || limit < offset || limit > in.length) {
            throw new IndexOutOfBoundsException("offset " + offset + ", limit " + limit + ", length " + in.length);
        }
        this.in = in;
        this.p = offset;
        this.limit = limit;
        this.start = offset;
        this.source = null;
        return this;
    }

    /**
     * Point this cursor at all of a {@link Bytes}, so one cursor can be reused.
     *
     * @param bytes the bytes
     * @return this cursor
     */
    public ArrayCursor reset(@NonNull final Bytes bytes) {
        reset(bytes.backingArray(), bytes.backingOffset(), bytes.backingOffset() + (int) bytes.length());
        this.source = bytes;
        return this;
    }

    /**
     * The array this cursor reads. Views use it to decode fields at the offsets they recorded; it must not be modified.
     *
     * @return the array
     */
    public byte[] array() {
        return in;
    }

    /**
     * Read an unsigned varint at an index already validated by a cursor, without bounds checks beyond the array's.
     *
     * @param in the bytes
     * @param offset the varint's first byte
     * @return the value
     */
    public static long varintAt(@NonNull final byte[] in, final int offset) {
        long value = 0;
        for (int shift = 0, i = offset; ; shift += 7) {
            final byte b = in[i++];
            value |= (long) (b & 0x7F) << shift;
            if (b >= 0) {
                return value;
            }
        }
    }

    /**
     * Read a little-endian 32-bit value at an index.
     *
     * @param in the bytes
     * @param q the value's first byte
     * @return the value
     */
    public static int fixed32At(@NonNull final byte[] in, final int q) {
        return (in[q] & 0xFF) | (in[q + 1] & 0xFF) << 8 | (in[q + 2] & 0xFF) << 16 | (in[q + 3] & 0xFF) << 24;
    }

    /**
     * Read a little-endian 64-bit value at an index.
     *
     * @param in the bytes
     * @param q the value's first byte
     * @return the value
     */
    public static long fixed64At(@NonNull final byte[] in, final int q) {
        return (fixed32At(in, q) & 0xFFFFFFFFL) | ((long) fixed32At(in, q + 4)) << 32;
    }

    /** @return the number of bytes read since this cursor was created */
    public int consumed() {
        return p - start;
    }

    /** @return the index of the next byte to read */
    public int position() {
        return p;
    }

    /** @return the index after the last byte that may be read */
    public int limit() {
        return limit;
    }

    /** @return true if there are bytes left before the limit */
    public boolean hasRemaining() {
        return p < limit;
    }

    /**
     * If the next byte is {@code tag}, consume it and return true. Generated parsers test for each field's one-byte tag
     * in field order, the order writers emit them in.
     *
     * @param tag the expected one-byte tag
     * @return true if it was the next byte
     */
    public boolean at(final byte tag) {
        final int q = p;
        if (q < limit && in[q] == tag) {
            p = q + 1;
            return true;
        }
        return false;
    }

    /**
     * If the next two bytes are a two-byte tag, consume them and return true.
     *
     * @param tag0 the tag's first byte
     * @param tag1 the tag's second byte
     * @return true if they were the next bytes
     */
    public boolean at(final byte tag0, final byte tag1) {
        final int q = p;
        if (q + 1 < limit && in[q] == tag0 && in[q + 1] == tag1) {
            p = q + 2;
            return true;
        }
        return false;
    }

    /**
     * Read an unsigned varint of up to 64 bits.
     *
     * @return the value
     * @throws BufferUnderflowException if the input ends inside the varint
     */
    public long varint() {
        final int q = p;
        if (q < limit) {
            final byte b = in[q];
            if (b >= 0) {
                p = q + 1;
                return b;
            }
        }
        return varintWide();
    }

    /** The multi-byte varint read: the XOR chain of {@code ByteArrayBufferedData}, with limit checks. */
    private long varintWide() {
        int q = p;
        if (limit - q >= 10) {
            long v = in[q++];
            if ((v ^= (long) in[q++] << 7) < 0L) {
                v ^= (~0L << 7);
            } else if ((v ^= (long) in[q++] << 14) >= 0L) {
                v ^= (~0L << 7) ^ (~0L << 14);
            } else if ((v ^= (long) in[q++] << 21) < 0L) {
                v ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21);
            } else if ((v ^= (long) in[q++] << 28) >= 0L) {
                v ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((v ^= (long) in[q++] << 35) < 0L) {
                v ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((v ^= (long) in[q++] << 42) >= 0L) {
                v ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((v ^= (long) in[q++] << 49) < 0L) {
                v ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
            } else {
                v ^= (long) in[q++] << 56;
                v ^= (~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56);
                if (v < 0L && in[q++] < 0L) {
                    throw new DataEncodingException("Malformed var int");
                }
            }
            p = q;
            return v;
        }
        // near the limit: one byte at a time
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            if (q >= limit) {
                throw new BufferUnderflowException();
            }
            final byte b = in[q++];
            value |= (long) (b & 0x7F) << shift;
            if (b >= 0) {
                p = q;
                return value;
            }
        }
        throw new DataEncodingException("Malformed var int");
    }

    /** @return a field tag, as the stream parser reads it ({@code readVarInt(false)}) */
    public int tag() {
        return (int) varint();
    }

    /** @return a zigzag-encoded varint, decoded */
    public long varintZigZag() {
        final long v = varint();
        return (v >>> 1) ^ -(v & 1);
    }

    /**
     * @return a bool, which must be encoded as 0 or 1
     * @throws IOException if it is neither
     */
    public boolean bool() throws IOException {
        final int i = (int) varint();
        if (i != 1 && i != 0) {
            throw new IOException("Bad protobuf encoding. Boolean was not 0 or 1");
        }
        return i == 1;
    }

    /** @return a little-endian 32-bit value */
    public int fixed32() {
        final int q = p;
        if (limit - q < Integer.BYTES) {
            throw new BufferUnderflowException();
        }
        p = q + Integer.BYTES;
        return (in[q] & 0xFF) | (in[q + 1] & 0xFF) << 8 | (in[q + 2] & 0xFF) << 16 | (in[q + 3] & 0xFF) << 24;
    }

    /** @return a little-endian 64-bit value */
    public long fixed64() {
        final int q = p;
        if (limit - q < Long.BYTES) {
            throw new BufferUnderflowException();
        }
        p = q + Long.BYTES;
        return (in[q] & 0xFFL)
                | (in[q + 1] & 0xFFL) << 8
                | (in[q + 2] & 0xFFL) << 16
                | (in[q + 3] & 0xFFL) << 24
                | (in[q + 4] & 0xFFL) << 32
                | (in[q + 5] & 0xFFL) << 40
                | (in[q + 6] & 0xFFL) << 48
                | (in[q + 7] & 0xFFL) << 56;
    }

    /**
     * Read a length prefix and check it against {@code maxSize} and the bytes left before the limit.
     *
     * @param maxSize the largest allowed length
     * @param fieldName the field's name, for the error message, or null for the stream parser's string and bytes message
     * @return the length
     * @throws ParseException if the length is greater than {@code maxSize}
     */
    public int length(final long maxSize, @Nullable final String fieldName) throws ParseException {
        final int length = (int) varint();
        if (length > maxSize) {
            throw new ParseException(
                    (fieldName == null ? "" : fieldName + " ") + "size " + length + " is greater than max " + maxSize);
        }
        if (length < 0 || length > limit - p) {
            throw new BufferUnderflowException();
        }
        return length;
    }

    /**
     * Read a string field's length and check its UTF-8 bytes without decoding them, for views. The position is left at
     * the start of the string. Malformed UTF-8 is an error, as in parsing.
     *
     * @param maxSize the largest allowed length
     * @param fieldName the field's name, for the error message
     * @return the length in bytes
     * @throws ParseException if the length is greater than {@code maxSize}
     * @throws MalformedProtobufException if the bytes are not valid UTF-8
     */
    public int stringLength(final long maxSize, @NonNull final String fieldName)
            throws ParseException, MalformedProtobufException {
        final int length = length(maxSize, fieldName);
        final int end = p + length;
        for (int i = p; i < end; i++) {
            if (in[i] < 0) {
                if (!Utf8Tools.isWellFormedUtf8(in, i, end)) {
                    throw new MalformedProtobufException("Malformed UTF-8 in " + fieldName);
                }
                break;
            }
        }
        return length;
    }

    /**
     * Read a string field's length and UTF-8 bytes. Malformed UTF-8 is an error, as in the stream parser.
     *
     * @param maxSize the largest allowed length
     * @return the string
     * @throws ParseException if the length is greater than {@code maxSize}
     * @throws MalformedProtobufException if the bytes are not valid UTF-8
     */
    public String string(final long maxSize) throws ParseException, MalformedProtobufException {
        final int length = length(maxSize, null);
        final int q = p;
        p = q + length;
        // the same strict decoding as the stream parser
        return Utf8Tools.decodeUtf8Strict(in, q, length);
    }

    /**
     * Read a bytes field's length and contents, as the stream parser returns them: a view if this cursor reads a
     * {@link Bytes}, which is immutable, otherwise a copy.
     *
     * @param maxSize the largest allowed length
     * @return the bytes
     * @throws ParseException if the length is greater than {@code maxSize}
     */
    public Bytes bytes(final long maxSize) throws ParseException {
        final int length = length(maxSize, null);
        if (length == 0) {
            return Bytes.EMPTY;
        }
        final int q = p;
        p = q + length;
        if (source != null) {
            return source.getBytes(q - source.backingOffset(), length);
        }
        return Bytes.wrap(Arrays.copyOfRange(in, q, q + length));
    }

    /**
     * Narrow the limit to a nested message of {@code length} bytes starting at the current position.
     *
     * @param length the nested message's length, already checked by {@link #length}
     * @return the limit to pass to {@link #popLimit} afterward
     */
    public int pushLimit(final int length) {
        final int old = limit;
        limit = p + length;
        return old;
    }

    /**
     * Check that a length read without {@link #length} fits before the limit.
     *
     * @param length the length
     * @return the length
     * @throws BufferUnderflowException if it is negative or longer than the bytes left
     */
    public int checkedLength(final int length) {
        if (length < 0 || length > limit - p) {
            throw new BufferUnderflowException();
        }
        return length;
    }

    /**
     * Restore an enclosing limit without checking that the nested range was read to its end, as the stream parser does
     * for {@code google.protobuf} wrapper types.
     *
     * @param oldLimit the value {@link #pushLimit} returned
     */
    public void restoreLimit(final int oldLimit) {
        limit = oldLimit;
    }

    /**
     * Check that the nested message was read exactly to its end, and restore the enclosing limit.
     *
     * @param oldLimit the value {@link #pushLimit} returned
     */
    public void popLimit(final int oldLimit) {
        if (p != limit) {
            throw new BufferOverflowException();
        }
        limit = oldLimit;
    }

    /**
     * Parse the rest of the input up to the limit with a codec's stream parser, for nested messages whose codec has no
     * array parser.
     *
     * @param codec the nested message's codec
     * @param strictMode as for {@link Codec#parse}
     * @param parseUnknownFields as for {@link Codec#parse}
     * @param maxDepth as for {@link Codec#parse}
     * @param maxSize as for {@link Codec#parse}
     * @return the parsed message
     * @param <T> the message type
     * @throws ParseException if parsing fails
     */
    public <T> T parseWith(
            @NonNull final Codec<T> codec,
            final boolean strictMode,
            final boolean parseUnknownFields,
            final int maxDepth,
            final int maxSize)
            throws ParseException {
        final BufferedData data = BufferedData.wrap(in, p, limit - p);
        final long before = data.position();
        final T value = codec.parse(data, strictMode, parseUnknownFields, maxDepth, maxSize);
        p += Math.toIntExact(data.position() - before);
        return value;
    }

    /**
     * Handle a field the generated parser has no case for, exactly as the stream parser's {@code defaultCase} does:
     * reject field 0 and bad wire types, throw in strict mode, collect it when {@code parseUnknownFields}, else skip it.
     *
     * @param tag the field's tag, already read
     * @param knownField true if the schema has this field number (the wire type is then wrong)
     * @param strictMode true to throw on unknown fields
     * @param parseUnknownFields true to collect unknown fields
     * @param unknownFields the fields collected so far, or null
     * @param maxSize the largest allowed length
     * @return the collected fields, or null
     * @throws IOException on a bad tag, or an unknown field in strict mode
     * @throws ParseException if a length is greater than {@code maxSize}
     */
    public @Nullable List<UnknownField> unknownField(
            final int tag,
            final boolean knownField,
            final boolean strictMode,
            final boolean parseUnknownFields,
            @Nullable List<UnknownField> unknownFields,
            final long maxSize)
            throws IOException, ParseException {
        final int field = tag >>> 3;
        final int wireType = tag & ProtoConstants.TAG_WIRE_TYPE_MASK;
        if (field == 0) {
            throw new IOException("Bad protobuf encoding. We read a field value of " + field);
        }
        if (wireType > 5) {
            throw new IOException("Cannot understand wire_type of " + wireType);
        }
        if (knownField) {
            throw new IOException("Bad tag [" + tag + "], field [" + field + "] wireType [" + wireType + "]");
        }
        if (strictMode) {
            throw new UnknownFieldException(field);
        }
        final int valueStart = p;
        switch (wireType) {
            case 0 -> varint();
            case 1 -> skip(Long.BYTES);
            case 2 -> {
                final int length = (int) varint();
                if (length < 0) {
                    throw new IOException("Encountered a field with negative length " + length);
                }
                if (length > maxSize) {
                    throw new ParseException("size " + length + " is greater than max " + maxSize);
                }
                skip(length);
            }
            case 5 -> skip(Integer.BYTES);
            case 3 -> throw new IOException("Wire type 'Group Start' is unsupported");
            default -> throw new IOException("Wire type 'Group End' is unsupported");
        }
        if (!parseUnknownFields) {
            return unknownFields;
        }
        final List<UnknownField> list = unknownFields == null ? new ArrayList<>(1) : unknownFields;
        list.add(new UnknownField(
                field, ProtoConstants.get(wireType), Bytes.wrap(Arrays.copyOfRange(in, valueStart, p))));
        return list;
    }

    /**
     * Skip bytes already checked to be before the limit, or throw if they are not.
     *
     * @param length the number of bytes to skip
     */
    public void skip(final int length) {
        if (length > limit - p) {
            throw new BufferUnderflowException();
        }
        p += length;
    }
}
