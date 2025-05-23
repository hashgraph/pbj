// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
            list = new ArrayList<>();
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
     * Read a protobuf int64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readInt64(final ReadableSequentialData input) {
        return input.readVarLong(false);
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
     * Read a protobuf uint64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readUint64(final ReadableSequentialData input) {
        return input.readVarLong(false);
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
     * Read a protobuf enum from input
     *
     * @param input The input data to read from
     * @return the read enum protoc ordinal
     */
    public static int readEnum(final ReadableSequentialData input) {
        return input.readVarInt(false);
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
     * Read a protobuf uint64(long) from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedInt64(final ReadableSequentialData input) {
        return input.readVarLong(true);
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
     * Read a protobuf fixed32 from input
     *
     * @param input The input data to read from
     * @return the read int
     */
    public static int readFixed32(final ReadableSequentialData input) {
        return input.readInt(ByteOrder.LITTLE_ENDIAN);
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
     * Read a protobuf sfixed64 from input
     *
     * @param input The input data to read from
     * @return the read long
     */
    public static long readSignedFixed64(final ReadableSequentialData input) {
        return input.readLong(ByteOrder.LITTLE_ENDIAN);
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
     * Read a double from input data
     *
     * @param input the input to read from
     * @return read double
     */
    public static double readDouble(final ReadableSequentialData input) {
        return input.readDouble(ByteOrder.LITTLE_ENDIAN);
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
     * Read the next field number from the input
     *
     * @param input The input data to read from
     * @return the read tag
     */
    public static int readNextFieldNumber(final ReadableSequentialData input) {
        final int tag = input.readVarInt(false);
        return tag >> TAG_FIELD_OFFSET;
    }
}
