// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.PbjReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class is full of parse helper methods, they depend on a DataInput as input with position and limit set
 * correctly.
 * <p>
 * Methods that IDE things are unused are used in generated code by PBJ compiler.
 */
@SuppressWarnings({"DuplicatedCode", "unused"})
public final class ProtoParserTools {
    /**
     * The number of lower order bits from the "tag" byte that should be rotated out
     * to reveal the field number
     */
    public static final int TAG_FIELD_OFFSET = 3;

    /** Instance should never be created */
    private ProtoParserTools() {}

    /**
     * Add an item to a list returning a new list with the item or the same list with the item added. If the list is
     * Collections.EMPTY_LIST then a new list is created and returned with the item added.
     *
     * @param list The list to add item to or Collections.EMPTY_LIST
     * @param newItem The item to add
     * @return The list passed in if mutable or new list
     * @param <T> The type of items to store in list
     */
    public static <T> List<T> addToList(List<T> list, T newItem) {
        if (list == Collections.EMPTY_LIST) {
            list = new UnmodifiableArrayList<>();
        }
        list.add(newItem);
        return list;
    }

    /**
     * Add an entry to a map returning a new map with the entry or the same map with the entry added. If the map is
     * Collections.EMPTY_MAP then a new map is created and returned with the entry added.
     *
     * @param map The map to add entry to or Collections.EMPTY_MAP
     * @param key The key
     * @param value The value
     * @return The map passed in if mutable or new map
     * @param <K> The type of keys
     * @param <V> The type of values
     */
    public static <K, V> Map<K, V> addToMap(Map<K, V> map, final K key, final V value) {
        if (map == PbjMap.EMPTY) {
            map = new HashMap<>();
        }
        map.put(key, value);
        return map;
    }

    /**
     * Read a protobuf int32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readInt32(final ReadableSequentialData input) {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf int32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readInt32(PbjReader input) {
        return input.readVarIntNoZZ();
    }

    /**
     * Read a protobuf int64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readInt64(final ReadableSequentialData input) {
        return input.readVarLong(false);
    }

    /**
     * Read a protobuf int64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readInt64(PbjReader input) {
        return input.readVarLongNoZZ();
    }

    /**
     * Read a protobuf uint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readUint32(final ReadableSequentialData input) {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf uint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readUint32(PbjReader input) {
        return input.readVarIntNoZZ();
    }

    /**
     * Read a protobuf uint64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readUint64(final ReadableSequentialData input) {
        return input.readVarLong(false);
    }

    /**
     * Read a protobuf uint64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readUint64(PbjReader input) {
        return input.readVarLongNoZZ();
    }

    /**
     * Read a protobuf bool from input
     *
     * @param input The input data to read from
     * @return the read boolean
     * @throws IOException If a I/O error occurs
     */
    public static boolean readBool(final ReadableSequentialData input) throws IOException {
        final var i = input.readVarInt(false);
        if (i != 1 && i != 0) {
            throw new IOException("Bad protobuf encoding. Boolean was not 0 or 1");
        }
        return i == 1;
    }

    /**
     * Read a protobuf bool from input
     *
     * @param input The input data to read from
     * @return the read boolean
     */
    public static boolean readBool(PbjReader input) {
        final var i = input.readVarIntNoZZ();
        if (i != 1 && i != 0) {
            input.setError(PbjReader.DATA_ENCODING);
        }
        return i == 1;
    }

    /**
     * Read a protobuf enum from input
     *
     * @param input The input data to read from
     * @return the read enum protoc ordinal
     */
    public static int readEnum(final ReadableSequentialData input) {
        return input.readVarInt(false);
    }

    /**
     * Read a protobuf enum from input
     *
     * @param input The input data to read from
     * @return the read enum protoc ordinal
     */
    public static int readEnum(PbjReader input) {
        return input.readVarIntNoZZ();
    }

    /**
     * Read a protobuf sint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readSignedInt32(final ReadableSequentialData input) {
        return input.readVarInt(true);
    }

    /**
     * Read a protobuf sint32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readSignedInt32(PbjReader input) {
        return input.readVarIntZZ();
    }

    /**
     * Read a protobuf uint64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedInt64(final ReadableSequentialData input) {
        return input.readVarLong(true);
    }

    /**
     * Read a protobuf sint64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedInt64(PbjReader input) {
        return input.readVarLongZZ();
    }

    /**
     * Read a protobuf sfixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readSignedFixed32(final ReadableSequentialData input) {
        return input.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf sfixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readSignedFixed32(PbjReader input) {
        return input.readIntLE();
    }

    /**
     * Read a protobuf fixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readFixed32(final ReadableSequentialData input) {
        return input.readInt(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf fixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readFixed32(PbjReader input) {
        return input.readIntLE();
    }

    /**
     * Read a protobuf float from input
     *
     * @param input The input data to read from
     * @return the read float
     */
    public static float readFloat(final ReadableSequentialData input) {
        return input.readFloat(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf float from input
     *
     * @param input The input data to read from
     * @return the read float
     */
    public static float readFloat(PbjReader input) {
        return input.readFloatLE();
    }

    /**
     * Read a protobuf sfixed64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedFixed64(final ReadableSequentialData input) {
        return input.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a protobuf sfixed64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedFixed64(final PbjReader input) {
        return input.readLongLE();
    }

    /**
     * Read a fixed 64, which is a fixed size encoded long
     *
     * @param input the input to read from
     * @return read long
     */
    public static long readFixed64(final ReadableSequentialData input) {
        return input.readLong(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a fixed 64, which is a fixed size encoded long
     *
     * @param input the input to read from
     * @return read long
     */
    public static long readFixed64(PbjReader input) {
        return input.readLongLE();
    }

    /**
     * Read a double from input data
     *
     * @param input the input to read from
     * @return read double
     */
    public static double readDouble(final ReadableSequentialData input) {
        return input.readDouble(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Read a double from input data
     *
     * @param input the input to read from
     * @return read double
     */
    public static double readDouble(PbjReader input) {
        return input.readDoubleLE();
    }

    /**
     * Read a String field from data input
     *
     * @param input the input to read from
     * @return Read string
     */
    public static String readString(final ReadableSequentialData input) throws IOException {
        try {
            return readString(input, Long.MAX_VALUE);
        } catch (ParseException ex) {
            throw new UncheckedParseException(ex);
        }
    }

    public static String readString(final PbjReader input) {
        return readString(input, Long.MAX_VALUE);
    }

    /**
     * Read a String field from data input
     *
     * @param input the input to read from
     * @param maxSize the maximum allowed size
     * @return Read string
     * @throws ParseException if the length is greater than maxSize
     */
    public static String readString(final ReadableSequentialData input, final long maxSize)
            throws IOException, ParseException {
        final int length = input.readVarInt(false);
        if (length > maxSize) {
            throw new ParseException("size " + length + " is greater than max " + maxSize);
        }
        if (input.remaining() < length) {
            throw new BufferUnderflowException();
        }
        final ByteBuffer bb = ByteBuffer.allocate(length);
        final long bytesRead = input.readBytes(bb);
        if (bytesRead != length) {
            throw new BufferUnderflowException();
        }
        bb.rewind();

        try {
            // Shouldn't use `new String()` because we want to error out on malformed UTF-8 bytes.
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bb)
                    .toString();
        } catch (CharacterCodingException e) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered", e);
        }
    }

    /**
     * Read a String field from data input
     *
     * @param input the input to read from
     * @param maxSize the maximum allowed size
     * @return Read string
     */
    public static String readString(final PbjReader input, final long maxSize) {
        return input.readString(maxSize);
    }

    private static int fromUTF8Tail(char[] dst, int di, byte[] src, int i, int endPos) {
        while (i < endPos) {
            int a = src[i];
            if ((a & 0x80) == 0) {
                dst[di++] = (char) a;
                i++;
                continue;
            }
            if (i + 1 >= endPos) return -1;

            int b = src[i + 1];
            if ((a & 0xE0) == 0xC0) {
                if ((b & 0xC0) == 0x80) {
                    dst[di++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
                    i += 2;
                    continue;
                } else {
                    return -1; // Bad encoding
                }
            }

            if (i + 2 >= endPos) return -1;

            int c = src[i + 2];
            int codepoint = -1;
            if ((a & 0xF0) == 0xE0) {
                if ((b & 0xC0) == 0x80 && (c & 0xC0) == 0x80) {
                    codepoint = ((a & 0xF) << 12) | ((b & 0x3F) << 6) | (c & 0x3F);
                    i += 3;
                } else {
                    return -1; // Bad encoding
                }
            } else {
                if (i + 3 >= endPos) return -1;
                int d = src[i + 3];
                if ((a & 0xF8) == 0xF0 && (b & 0xC0) == 0x80 && (c & 0xC0) == 0x80 && (d & 0xC0) == 0x80) {
                    codepoint = ((a & 7) << 18) | ((b & 0x3F) << 12) | ((c & 0x3F) << 6) | (d & 0x3F);
                    i += 4;
                } else {
                    return -1; // Bad encoding
                }
            }

            if (codepoint <= 0xFFFF) {
                if (codepoint < 0 || (codepoint >= 0xD800 && codepoint < 0xE000)) return -1; // [D800, E000) is illegal
                dst[di++] = (char) codepoint;
                continue;
            }

            if (codepoint > 0x10FFFF) return -1; // Illegal range
            int v = codepoint - 0x10000;
            dst[di + 0] = (char) (0xD800 + ((v >> 10) & 0x3FF));
            dst[di + 1] = (char) (0xDC00 + (v & 0x3FF));
            di += 2;
        }
        return di;
    }

    /**
     * Decodes UTF-8 bytes from {@code src} into the {@code char[]} destination, supporting all
     * four UTF-8 byte widths (1–4 bytes). Codepoints above U+FFFF are written as surrogate pairs.
     * Prefer using the simpler {@link #readString} function.
     *
     * <p>Decoding starts at {@code src[offset + pos]} and continues until {@code src[offset + length]}.
     * The main loop keeps a 4-byte lookahead (exits when fewer than 5 bytes remain), delegating
     * the final bytes to {@link #fromUTF8Tail}. Any unrecognised byte sequence returns {@code -1}.
     *
     * @param dst    the destination char array, written starting at index {@code pos}
     * @param src    the source byte array containing UTF-8 data
     * @param offset the base index within {@code src} where the UTF-8 region begins
     * @param pos    bytes already decoded by the ASCII fast path; doubles as the read offset
     *               (relative to {@code offset}) and the initial write index in {@code dst}
     * @param length the total byte length of the UTF-8 region in {@code src} starting at {@code offset}
     * @return the total number of {@code char}s written to {@code dst}, or {@code -1} if the
     *         input contains an illegal byte sequence (surrogate range or out-of-range codepoint)
     */
    public static int fromUTF8(char[] dst, byte[] src, int offset, int pos, int length) {
        int i = offset + pos;
        int di = pos;
        while (i + 4 < offset + length) {
            int a = src[i];
            if ((a & 0x80) == 0) {
                dst[di++] = (char) a;
                i++;
                continue;
            }
            int b = src[i + 1];
            if ((a & 0xE0) == 0xC0 && (b & 0xC0) == 0x80) {
                dst[di++] = (char) (((a & 0x1F) << 6) | (b & 0x3F));
                i += 2;
                continue;
            }
            int c = src[i + 2];
            int codepoint = -1;
            if ((a & 0xF0) == 0xE0 && (b & 0xC0) == 0x80 && (c & 0xC0) == 0x80) {
                codepoint = ((a & 0xF) << 12) | ((b & 0x3F) << 6) | (c & 0x3F);
                i += 3;
            } else {
                int d = src[i + 3];
                if ((a & 0xF8) == 0xF0 && (b & 0xC0) == 0x80 && (c & 0xC0) == 0x80 && (d & 0xC0) == 0x80) {
                    codepoint = ((a & 7) << 18) | ((b & 0x3F) << 12) | ((c & 0x3F) << 6) | (d & 0x3F);
                    i += 4;
                }
            }

            if (codepoint <= 0xFFFF) {
                if (codepoint < 0 || (codepoint >= 0xD800 && codepoint < 0xE000)) return -1; // [D800, E000) is illegal
                dst[di++] = (char) codepoint;
                continue;
            }

            if (codepoint > 0x10FFFF) return -1; // illegal range
            int v = codepoint - 0x10000;
            dst[di + 0] = (char) (0xD800 + ((v >> 10) & 0x3FF));
            dst[di + 1] = (char) (0xDC00 + (v & 0x3FF));
            di += 2;
        }
        return i == offset + length ? di : fromUTF8Tail(dst, di, src, i, offset + length);
    }

    /**
     * Read a Bytes field from data input
     *
     * @param input the input to read from
     * @return read Bytes object, this can be a copy or a direct reference to inputs data. So it has same life span
     * of InputData
     */
    public static Bytes readBytes(final ReadableSequentialData input) {
        try {
            return readBytes(input, Long.MAX_VALUE);
        } catch (ParseException ex) {
            throw new UncheckedParseException(ex);
        }
    }

    /**
     * Read a Bytes field from data input, or throw ParseException if the Bytes in the input
     * is longer than the maxSize.
     *
     * @param input the input to read from
     * @param maxSize the maximum allowed size
     * @return read Bytes object, this can be a copy or a direct reference to inputs data. So it has same life span
     * of InputData
     * @throws ParseException if the length is greater than maxSize
     */
    public static Bytes readBytes(final ReadableSequentialData input, final long maxSize) throws ParseException {
        final int length = input.readVarInt(false);
        if (length > maxSize) {
            throw new ParseException("size " + length + " is greater than max " + maxSize);
        }
        if (input.remaining() < length) {
            throw new BufferUnderflowException();
        }
        Bytes bytes = input.readBytes(length);
        if (bytes.length() != length) {
            throw new BufferUnderflowException();
        }
        return bytes;
    }

    /**
     * Read a Bytes field from data input, setting an error on {@code input} if the Bytes in the input
     * is longer than the maxSize.
     *
     * @param input the input to read from
     * @param maxSize the maximum allowed size
     * @return read Bytes object, this can be a copy or a direct reference to inputs data. So it has same life span
     * of InputData
     */
    public static Bytes readBytes(final PbjReader input, final long maxSize) {
        final int length = input.readVarIntNoZZ();
        if (length > maxSize || length < 0) {
            input.setError(PbjReader.PARSE);
            return Bytes.EMPTY;
        }
        return input.readBytes(length);
    }

    /**
     * Reads a requested length-delimited protobuf field from the input and returns it as a
     * {@link Bytes} object. If the requested field is repeated or not length-delimited, this
     * method throws an {@link IllegalArgumentException}. .
     *
     * <p>The input must contain valid protobuf encoded bytes. If the field is not found in
     * the input {@code null} is returned. If the field occurs multiple time in the input, bytes
     * for the first occurrence are returned.
     *
     * <p>The returned Bytes object, if not null, will not contain the tag or the length.
     *
     * @param input The input to read from
     * @param field Field definition to extract bytes for
     * @return Field bytes without tag or length, or {@code null} if the field is not found
     *      in the input
     * @throws IOException If an I/O error occurred
     * @throws ParseException If there is a mismatch between the requested field and the field
     *      in the input with the same field ID
     */
    @Nullable
    public static Bytes extractFieldBytes(
            @NonNull final ReadableSequentialData input, @NonNull final FieldDefinition field)
            throws IOException, ParseException {
        Objects.requireNonNull(input);
        Objects.requireNonNull(field);
        if (field.repeated()) {
            throw new IllegalArgumentException("Cannot extract field bytes for a repeated field: " + field);
        }
        if (ProtoWriterTools.wireType(field) != ProtoConstants.WIRE_TYPE_DELIMITED) {
            throw new IllegalArgumentException("Cannot extract field bytes for a non-length-delimited field: " + field);
        }
        while (input.hasRemaining()) {
            final int tag;
            // hasRemaining() doesn't work very well for streaming data, it returns false only when
            // the end of input is already reached using a read operation. Let's catch an underflow
            // (actually, EOF) exception here and exit cleanly. Underflow exception in any other
            // place means malformed input and should be rethrown
            try {
                tag = input.readVarInt(false);
            } catch (final BufferUnderflowException e) {
                // No more fields
                break;
            }
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            final ProtoConstants wireType = ProtoConstants.get(tag & ProtoConstants.TAG_WIRE_TYPE_MASK);
            if (fieldNum == field.number()) {
                if (wireType != ProtoConstants.WIRE_TYPE_DELIMITED) {
                    throw new ParseException("Unexpected wire type: " + tag);
                }
                final int length = input.readVarInt(false);
                return input.readBytes(length);
            } else {
                skipField(input, wireType);
            }
        }
        return null;
    }

    /**
     * Reads a requested length-delimited protobuf field from the input and returns it as a
     * {@link Bytes} object. If the requested field is repeated or not length-delimited, this
     * method throws an {@link IllegalArgumentException}. .
     *
     * <p>The input must contain valid protobuf encoded bytes. If the field is not found in
     * the input {@code null} is returned. If the field occurs multiple time in the input, bytes
     * for the first occurrence are returned.
     *
     * <p>The returned Bytes object, if not null, will not contain the tag or the length.
     *
     * @param input The input to read from
     * @param field Field definition to extract bytes for
     * @return Field bytes without tag or length, or {@code null} if the field is not found
     *      in the input
     */
    @Nullable
    public static Bytes extractFieldBytes(@NonNull final PbjReader input, @NonNull final FieldDefinition field) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(field);
        if (field.repeated()) {
            throw new IllegalArgumentException("Cannot extract field bytes for a repeated field: " + field);
        }
        if (ProtoWriterTools.wireType(field) != ProtoConstants.WIRE_TYPE_DELIMITED) {
            throw new IllegalArgumentException("Cannot extract field bytes for a non-length-delimited field: " + field);
        }
        while (input.hasRemaining()) {
            final int tag = input.readVarIntNoZZ();
            final int fieldNum = tag >> TAG_FIELD_OFFSET;
            final ProtoConstants wireType = ProtoConstants.get(tag & ProtoConstants.TAG_WIRE_TYPE_MASK);
            if (fieldNum == field.number()) {
                if (wireType != ProtoConstants.WIRE_TYPE_DELIMITED) {
                    input.setError(PbjReader.PARSE);
                }
                final int length = input.readVarIntNoZZ();
                return input.readBytes(length);
            } else {
                skipField(input, wireType);
            }
        }
        return null;
    }

    /**
     * Extract the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @param maxSize the maximum allowed size for repeated/length-encoded fields
     * @return the extracted bytes
     * @throws IOException For unsupported wire types
     * @throws ParseException if the length of a repeated/length-encoded field is greater than maxSize
     */
    public static Bytes extractField(
            final ReadableSequentialData input, final ProtoConstants wireType, final long maxSize)
            throws IOException, ParseException {
        return switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> input.readBytes(8);
            case WIRE_TYPE_FIXED_32_BIT -> input.readBytes(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> input.readVarLongBytes();
            case WIRE_TYPE_DELIMITED -> {
                final Bytes lenBytes = input.readVarLongBytes();
                final int length = lenBytes.getVarInt(0, false);
                if (length < 0) {
                    throw new IOException("Encountered a field with negative length " + length);
                }
                if (length > maxSize) {
                    throw new ParseException("size " + length + " is greater than max " + maxSize);
                }
                yield Bytes.merge(lenBytes, input.readBytes(length));
            }
            case WIRE_TYPE_GROUP_START -> throw new IOException("Wire type 'Group Start' is unsupported");
            case WIRE_TYPE_GROUP_END -> throw new IOException("Wire type 'Group End' is unsupported");
            default -> throw new IOException("Unhandled wire type while trying to skip a field " + wireType);
        };
    }

    /**
     * Extract the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @param maxSize the maximum allowed size for repeated/length-encoded fields
     * @return the extracted bytes
     */
    public static Bytes extractField(final PbjReader input, final ProtoConstants wireType, final long maxSize) {
        return switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> input.readBytes(8);
            case WIRE_TYPE_FIXED_32_BIT -> input.readBytes(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> input.readVarLongBytes();
            case WIRE_TYPE_DELIMITED -> {
                final Bytes lenBytes = input.readVarLongBytes();
                final int length = lenBytes.getVarInt(0, false);
                if (length < 0) {
                    input.setError(PbjReader.IO_ERROR);
                    yield Bytes.EMPTY;
                }
                if (length > maxSize) {
                    input.setError(PbjReader.PARSE);
                    yield Bytes.EMPTY;
                }
                yield Bytes.merge(lenBytes, input.readBytes(length));
            }
            case WIRE_TYPE_GROUP_START -> {
                input.setError(PbjReader.UNSUPPORTED);
                yield Bytes.EMPTY;
            }
            case WIRE_TYPE_GROUP_END -> {
                input.setError(PbjReader.UNSUPPORTED);
                yield Bytes.EMPTY;
            }
            default -> {
                input.setError(PbjReader.IO_ERROR);
                yield Bytes.EMPTY;
            }
        };
    }

    /**
     * Skip over the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @throws IOException For unsupported wire types
     */
    public static void skipField(final ReadableSequentialData input, final ProtoConstants wireType) throws IOException {
        try {
            skipField(input, wireType, Long.MAX_VALUE);
        } catch (ParseException ex) {
            throw new UncheckedParseException(ex);
        }
    }

    /**
     * Skip over the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     */
    public static void skipField(final PbjReader input, final ProtoConstants wireType) {
        skipField(input, wireType, Long.MAX_VALUE);
    }

    /**
     * Skip over the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @param maxSize the maximum allowed size for repeated/length-encoded fields
     * @throws IOException For unsupported wire types
     * @throws ParseException if the length of a repeated/length-encoded field is greater than maxSize
     */
    public static void skipField(final ReadableSequentialData input, final ProtoConstants wireType, final long maxSize)
            throws IOException, ParseException {
        switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> input.skip(8);
            case WIRE_TYPE_FIXED_32_BIT -> input.skip(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> input.readVarLong(false);
            case WIRE_TYPE_DELIMITED -> {
                final int length = input.readVarInt(false);
                if (length < 0) {
                    throw new IOException("Encountered a field with negative length " + length);
                }
                if (length > maxSize) {
                    throw new ParseException("size " + length + " is greater than max " + maxSize);
                }
                input.skip(length);
            }
            case WIRE_TYPE_GROUP_START -> throw new IOException("Wire type 'Group Start' is unsupported");
            case WIRE_TYPE_GROUP_END -> throw new IOException("Wire type 'Group End' is unsupported");
            default -> throw new IOException("Unhandled wire type while trying to skip a field " + wireType);
        }
    }

    /**
     * Skip over the bytes in a stream for a given wire type. Assumes you have already read tag.
     *
     * @param input The input to move ahead
     * @param wireType The wire type of field to skip
     * @param maxSize the maximum allowed size for repeated/length-encoded fields
     */
    public static void skipField(final PbjReader input, final ProtoConstants wireType, final long maxSize) {
        switch (wireType) {
            case WIRE_TYPE_FIXED_64_BIT -> input.skip(8);
            case WIRE_TYPE_FIXED_32_BIT -> input.skip(4);
            // The value for "zigZag" when calling varint doesn't matter because we are just reading past
            // the varint, we don't care how to interpret it (zigzag is only used for interpretation of
            // the bytes, not how many of them there are)
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> input.readVarLongNoZZ();
            case WIRE_TYPE_DELIMITED -> {
                final int length = input.readVarIntNoZZ();
                if (length < 0) {
                    input.setError(PbjReader.IO_ERROR);
                }
                if (length > maxSize) {
                    input.setError(PbjReader.PARSE);
                }
                input.skip(length);
            }
            case WIRE_TYPE_GROUP_START -> input.setError(PbjReader.UNSUPPORTED);
            case WIRE_TYPE_GROUP_END -> input.setError(PbjReader.UNSUPPORTED);
            default -> input.setError(PbjReader.IO_ERROR);
        }
    }

    /**
     * Read the next field number from the input
     *
     * @param input The input data to read from
     * @return the read tag
     */
    public static int readNextFieldNumber(final ReadableSequentialData input) {
        final int tag = input.readVarInt(false);
        return tag >> TAG_FIELD_OFFSET;
    }

    /**
     * Read the next field number from the input
     *
     * @param input The input data to read from
     * @return the read tag
     */
    public static int readNextFieldNumber(final PbjReader input) {
        final int tag = input.readVarIntNoZZ();
        return tag >> TAG_FIELD_OFFSET;
    }
}
