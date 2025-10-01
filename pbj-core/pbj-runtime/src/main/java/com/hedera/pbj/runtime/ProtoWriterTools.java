// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.ProtoConstants.*;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Static helper methods for Writers
 */
@SuppressWarnings({"DuplicatedCode", "ForLoopReplaceableByForEach"})
public final class ProtoWriterTools {

    /** The number of leading bits of the tag that are used to store field type, the rest is field number */
    static final int TAG_TYPE_BITS = 3;

    /** Instance should never be created */
    private ProtoWriterTools() {}

    // ================================================================================================================
    // COMMON METHODS

    /**
     * Returns wire format to use for the given field based on its type.
     *
     * @param field The field
     * @return The wire format
     */
    public static ProtoConstants wireType(final FieldDefinition field) {
        return switch (field.type()) {
            case FLOAT -> WIRE_TYPE_FIXED_32_BIT;
            case DOUBLE -> WIRE_TYPE_FIXED_64_BIT;
            case INT32, INT64, SINT32, SINT64, UINT32, UINT64 -> WIRE_TYPE_VARINT_OR_ZIGZAG;
            case FIXED32, SFIXED32 -> WIRE_TYPE_FIXED_32_BIT;
            case FIXED64, SFIXED64 -> WIRE_TYPE_FIXED_64_BIT;
            case BOOL -> WIRE_TYPE_VARINT_OR_ZIGZAG;
            case BYTES, MESSAGE, STRING, MAP -> WIRE_TYPE_DELIMITED;
            case ENUM -> WIRE_TYPE_VARINT_OR_ZIGZAG;
        };
    }

    /**
     * Write a protobuf tag to the output. Field wire format is calculated based on field type.
     *
     * @param out The data output to write to
     * @param field The field to write the tag for
     */
    public static void writeTag(final WritableSequentialData out, final FieldDefinition field) {
        writeTag(out, field, wireType(field));
    }

    /**
     * Write a protobuf tag to the output.
     *
     * @param out The data output to write to
     * @param field The field to include in tag
     * @param wireType The field wire type to include in tag
     */
    public static void writeTag(
            final WritableSequentialData out, final FieldDefinition field, final ProtoConstants wireType) {
        out.writeVarInt((field.number() << TAG_TYPE_BITS) | wireType.ordinal(), false);
    }

    /** Create an unsupported field type exception */
    static RuntimeException unsupported() {
        return new RuntimeException("Unsupported field type, shouldn't happen.");
    }

    // ================================================================================================================
    // STANDARD WRITE METHODS

    /**
     * Write a integer to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     */
    public static void writeInteger(WritableSequentialData out, FieldDefinition field, int value) {
        writeInteger(out, field, value, true);
    }

    /**
     * Write a integer to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     */
    public static void writeInteger(WritableSequentialData out, FieldDefinition field, int value, boolean skipDefault) {
        assert switch (field.type()) {
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
                    default -> false;
                }
                : "Not an integer type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";

        if (skipDefault && !field.oneOf() && value == 0) {
            return;
        }
        switch (field.type()) {
            case INT32 -> {
                writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                out.writeVarInt(value, false);
            }
            case UINT32 -> {
                writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                out.writeVarLong(Integer.toUnsignedLong(value), false);
            }
            case SINT32 -> {
                writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                out.writeVarInt(value, true);
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_FIXED_32_BIT);
                out.writeInt(value, ByteOrder.LITTLE_ENDIAN);
            }
            default -> throw unsupported();
        }
    }

    /**
     * Write a long to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the long value to write
     */
    public static void writeLong(WritableSequentialData out, FieldDefinition field, long value) {
        writeLong(out, field, value, true);
    }

    /**
     * Write a long to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the long value to write
     * @param skipDefault default value results in no-op for non-oneOf
     */
    public static void writeLong(WritableSequentialData out, FieldDefinition field, long value, boolean skipDefault) {
        assert switch (field.type()) {
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
                    default -> false;
                }
                : "Not a long type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) {
            return;
        }
        switch (field.type()) {
            case INT64, UINT64 -> {
                writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                out.writeVarLong(value, false);
            }
            case SINT64 -> {
                writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                out.writeVarLong(value, true);
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_FIXED_64_BIT);
                out.writeLong(value, ByteOrder.LITTLE_ENDIAN);
            }
            default -> throw unsupported();
        }
    }

    /**
     * Write a float to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the float value to write
     */
    public static void writeFloat(WritableSequentialData out, FieldDefinition field, float value) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert !field.repeated() : "Use writeFloatList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_FIXED_32_BIT);
        out.writeFloat(value, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Write a double to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the double value to write
     */
    public static void writeDouble(WritableSequentialData out, FieldDefinition field, double value) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert !field.repeated() : "Use writeDoubleList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_FIXED_64_BIT);
        out.writeDouble(value, ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Write a boolean to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the boolean value to write
     */
    public static void writeBoolean(WritableSequentialData out, FieldDefinition field, boolean value) {
        writeBoolean(out, field, value, true);
    }

    /**
     * Write a boolean to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the boolean value to write
     * @param skipDefault default value results in no-op for non-oneOf
     */
    public static void writeBoolean(
            WritableSequentialData out, FieldDefinition field, boolean value, boolean skipDefault) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use writeBooleanList with repeated types";
        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf() || !skipDefault) {
            writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            out.writeByte(value ? (byte) 1 : 0);
        }
    }

    /**
     * Write a enum to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param enumValue the enum value to write
     */
    public static void writeEnum(WritableSequentialData out, FieldDefinition field, EnumWithProtoMetadata enumValue) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert !field.repeated() : "Use writeEnumList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && (enumValue == null || enumValue.protoOrdinal() == 0)) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        out.writeVarInt(enumValue.protoOrdinal(), false);
    }

    /**
     * Write a string to data output, assuming the field is non-repeated.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeString(final WritableSequentialData out, final FieldDefinition field, final String value)
            throws IOException {
        writeString(out, field, value, true);
    }

    /**
     * Write a string represented by a byte[] to data output, assuming the field is non-repeated.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeString(final WritableSequentialData out, final FieldDefinition field, final byte[] value)
            throws IOException {
        writeString(out, field, value, true);
    }

    /**
     * Write a string to data output, assuming the field is non-repeated.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @throws IOException If a I/O error occurs
     */
    public static void writeString(
            final WritableSequentialData out, final FieldDefinition field, final String value, boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        writeStringNoChecks(out, field, value, skipDefault);
    }

    /**
     * Write a string to data output, assuming the field is non-repeated.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @throws IOException If a I/O error occurs
     */
    public static void writeString(
            final WritableSequentialData out, final FieldDefinition field, final byte[] value, boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        writeStringNoChecks(out, field, value, skipDefault);
    }

    /**
     * Write a string to data output, assuming the field is repeated. Usually this method is called multiple
     * times, one for every repeated value. If all values are available immediately, {@link #writeStringList(
     * WritableSequentialData, FieldDefinition, List)} should be used instead.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOneRepeatedString(
            final WritableSequentialData out, final FieldDefinition field, final byte[] value) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "writeOneRepeatedString can only be used with repeated fields";
        writeStringNoChecks(out, field, value);
    }

    /**
     * Write a integer to data output - no validation checks.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    private static void writeStringNoChecks(
            final WritableSequentialData out, final FieldDefinition field, final byte[] value) throws IOException {
        writeStringNoChecks(out, field, value, true);
    }

    /**
     * Write a integer to data output - no validation checks.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @throws IOException If a I/O error occurs
     */
    private static void writeStringNoChecks(
            final WritableSequentialData out, final FieldDefinition field, final String value, boolean skipDefault)
            throws IOException {
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value == null || value.isEmpty())) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(sizeOfStringNoTag(value), false);
        Utf8Tools.encodeUtf8(value, out);
    }

    /**
     * Write a integer to data output - no validation checks.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @throws IOException If a I/O error occurs
     */
    private static void writeStringNoChecks(
            final WritableSequentialData out, final FieldDefinition field, final byte[] value, boolean skipDefault)
            throws IOException {
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value == null || value.length == 0)) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(value.length, false);
        out.writeBytes(value);
    }

    /**
     * Write a bytes to data output, assuming the corresponding field is non-repeated, and field type
     * is any delimited: bytes, string, or message.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must not be repeated
     * @param value the bytes value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeBytes(
            final WritableSequentialData out, final FieldDefinition field, final RandomAccessData value)
            throws IOException {
        writeBytes(out, field, value, true);
    }

    /**
     * Write a bytes to data output, assuming the corresponding field is non-repeated, and field type
     * is any delimited: bytes, string, or message.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must not be repeated
     * @param value the bytes value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @throws IOException If a I/O error occurs
     */
    public static void writeBytes(
            final WritableSequentialData out,
            final FieldDefinition field,
            final RandomAccessData value,
            boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use writeBytesList with repeated types";
        writeBytesNoChecks(out, field, value, skipDefault);
    }

    /**
     * Write a bytes to data output, assuming the corresponding field is repeated, and field type
     * is any delimited: bytes, string, or message. Usually this method is called multiple times, one
     * for every repeated value. If all values are available immediately, {@link #writeBytesList(
     * WritableSequentialData, FieldDefinition, List)} should be used instead.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be repeated
     * @param value the bytes value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOneRepeatedBytes(
            final WritableSequentialData out, final FieldDefinition field, final RandomAccessData value)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert field.repeated() : "writeOneRepeatedBytes can only be used with repeated fields";
        writeBytesNoChecks(out, field, value, true);
    }

    /**
     * Write a bytes to data output - no validation checks.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the bytes value to write
     * @param skipZeroLength this is true for normal single bytes and false for repeated lists
     * @throws IOException If a I/O error occurs
     */
    private static void writeBytesNoChecks(
            final WritableSequentialData out,
            final FieldDefinition field,
            final RandomAccessData value,
            final boolean skipZeroLength)
            throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (skipZeroLength && (value.length() == 0))) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(Math.toIntExact(value.length()), false);
        final long posBefore = out.position();
        out.writeBytes(value);
        final long bytesWritten = out.position() - posBefore;
        if (bytesWritten != value.length()) {
            throw new IOException("Wrote less bytes [" + bytesWritten + "] than expected [" + value.length() + "]");
        }
    }

    /**
     * Write a message to data output, assuming the corresponding field is non-repeated.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must not be repeated
     * @param message the message to write
     * @param codec the codec for the given message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> void writeMessage(
            final WritableSequentialData out, final FieldDefinition field, final T message, final Codec<T> codec)
            throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use writeMessageList with repeated types";
        writeMessageNoChecks(out, field, message, codec);
    }

    /**
     * Write a message to data output, assuming the corresponding field is repeated. Usually this method is
     * called multiple times, one for every repeated value. If all values are available immediately, {@link
     * #writeMessageList(WritableSequentialData, FieldDefinition, List, Codec)}  should
     * be used instead.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing, the field must be repeated
     * @param message the message to write
     * @param codec the codec for the given message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> void writeOneRepeatedMessage(
            final WritableSequentialData out, final FieldDefinition field, final T message, final Codec<T> codec)
            throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "writeOneRepeatedMessage can only be used with repeated fields";
        writeMessageNoChecks(out, field, message, codec);
    }

    /**
     * Write a message to data output - no validation checks.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param message the message to write
     * @param codec the codec for the given message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    private static <T> void writeMessageNoChecks(
            final WritableSequentialData out, final FieldDefinition field, final T message, final Codec<T> codec)
            throws IOException {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            out.writeVarInt(0, false);
        } else if (message != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final int size = codec.measureRecord(message);
            out.writeVarInt(size, false);
            if (size > 0) {
                codec.write(message, out);
            }
        }
    }

    public static <K, V> void writeMap(
            final WritableSequentialData out,
            final FieldDefinition field,
            @NonNull final PbjMap<K, V> map,
            final ProtoWriter<K> kWriter,
            final ProtoWriter<V> vWriter,
            final ToIntFunction<K> sizeOfK,
            final ToIntFunction<V> sizeOfV)
            throws IOException {
        // https://protobuf.dev/programming-guides/proto3/#maps
        // On the wire, a map is equivalent to:
        //    message MapFieldEntry {
        //      key_type key = 1;
        //      value_type value = 2;
        //    }
        //    repeated MapFieldEntry map_field = N;
        if (map.isEmpty()) {
            return;
        }
        final int size = map.size();
        for (int i = 0; i < size; i++) {
            K k = map.getSortedKeys().get(i);
            V v = map.get(k);
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final int sizeK = sizeOfK.applyAsInt(k);
            final int sizeV = sizeOfV.applyAsInt(v);
            out.writeVarInt(sizeK + sizeV, false);
            kWriter.write(k, out);
            vWriter.write(v, out);
        }
    }

    // ================================================================================================================
    // OPTIONAL VERSIONS OF WRITE METHODS

    /**
     * Write an optional integer to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional integer value to write
     */
    public static void writeOptionalInteger(
            WritableSequentialData out, FieldDefinition field, @Nullable Integer value) {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfInteger(newField, value), false);
            writeInteger(out, newField, value);
        }
    }

    /**
     * Write an optional long to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     */
    public static void writeOptionalLong(WritableSequentialData out, FieldDefinition field, @Nullable Long value) {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfLong(newField, value), false);
            writeLong(out, newField, value);
        }
    }

    /**
     * Write an optional float to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional float value to write
     */
    public static void writeOptionalFloat(WritableSequentialData out, FieldDefinition field, @Nullable Float value) {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfFloat(newField, value), false);
            writeFloat(out, newField, value);
        }
    }

    /**
     * Write an optional double to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional double value to write
     */
    public static void writeOptionalDouble(WritableSequentialData out, FieldDefinition field, @Nullable Double value) {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfDouble(newField, value), false);
            writeDouble(out, newField, value);
        }
    }

    /**
     * Write an optional boolean to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional boolean value to write
     */
    public static void writeOptionalBoolean(
            WritableSequentialData out, FieldDefinition field, @Nullable Boolean value) {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfBoolean(newField, value), false);
            writeBoolean(out, newField, value);
        }
    }

    /**
     * Write an optional string to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalString(WritableSequentialData out, FieldDefinition field, @Nullable String value)
            throws IOException {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfString(newField, value), false);
            writeString(out, newField, value);
        }
    }

    /**
     * Write an optional string represented by a byte[] to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalString(WritableSequentialData out, FieldDefinition field, @Nullable byte[] value)
            throws IOException {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            final int size = sizeOfString(newField, value, true);
            out.writeVarInt(size, false);
            if (size > 0) {
                writeString(out, newField, value);
            }
        }
    }

    /**
     * Write an optional bytes to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional bytes value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalBytes(WritableSequentialData out, FieldDefinition field, @Nullable Bytes value)
            throws IOException {
        if (value != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            final int size = sizeOfBytes(newField, value);
            out.writeVarInt(size, false);
            if (size > 0) {
                writeBytes(out, newField, value);
            }
        }
    }

    // ================================================================================================================
    // LIST VERSIONS OF WRITE METHODS

    /**
     * Write a list of integers to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of integers value to write
     */
    public static void writeIntegerList(WritableSequentialData out, FieldDefinition field, List<Integer> list) {
        assert switch (field.type()) {
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
                    default -> false;
                }
                : "Not an integer type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final int listSize = list.size();
        switch (field.type()) {
            case INT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += sizeOfVarInt32(val);
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    out.writeVarInt(val, false);
                }
            }
            case UINT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += sizeOfUnsignedVarInt64(Integer.toUnsignedLong(val));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    out.writeVarLong(Integer.toUnsignedLong(val), false);
                }
            }
            case SINT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += sizeOfUnsignedVarInt64(((long) val << 1) ^ ((long) val >> 63));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    out.writeVarInt(val, true);
                }
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong((long) list.size() * FIXED32_SIZE, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    out.writeInt(val, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default -> throw unsupported();
        }
    }

    /**
     * Write a list of longs to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of longs value to write
     */
    public static void writeLongList(WritableSequentialData out, FieldDefinition field, List<Long> list) {
        assert switch (field.type()) {
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
                    default -> false;
                }
                : "Not a long type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final int listSize = list.size();
        switch (field.type()) {
            case INT64, UINT64 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += sizeOfUnsignedVarInt64(val);
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    out.writeVarLong(val, false);
                }
            }
            case SINT64 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += sizeOfUnsignedVarInt64((val << 1) ^ (val >> 63));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    out.writeVarLong(val, true);
                }
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong((long) list.size() * FIXED64_SIZE, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    out.writeLong(val, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default -> throw unsupported();
        }
    }

    /**
     * Write a list of floats to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of floats value to write
     */
    public static void writeFloatList(WritableSequentialData out, FieldDefinition field, List<Float> list) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert field.repeated() : "Use writeFloat with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int size = list.size() * FIXED32_SIZE;
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            out.writeFloat(list.get(i), ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * Write a list of doubles to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of doubles value to write
     */
    public static void writeDoubleList(WritableSequentialData out, FieldDefinition field, List<Double> list) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert field.repeated() : "Use writeDouble with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int size = list.size() * FIXED64_SIZE;
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            out.writeDouble(list.get(i), ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * Write a list of booleans to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of booleans value to write
     */
    public static void writeBooleanList(WritableSequentialData out, FieldDefinition field, List<Boolean> list) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use writeBoolean with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        // write
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(list.size(), false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final boolean b = list.get(i);
            out.writeVarInt(b ? 1 : 0, false);
        }
    }

    /**
     * Write a list of enums to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of enums value to write
     */
    public static void writeEnumList(
            WritableSequentialData out, FieldDefinition field, List<? extends EnumWithProtoMetadata> list) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use writeEnum with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            size += sizeOfUnsignedVarInt32(list.get(i).protoOrdinal());
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        for (int i = 0; i < listSize; i++) {
            out.writeVarInt(list.get(i).protoOrdinal(), false);
        }
    }

    /**
     * Write a list of strings to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of strings value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeStringList(WritableSequentialData out, FieldDefinition field, List<byte[]> list)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use writeString with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final byte[] value = list.get(i);
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            if (value == null) {
                out.writeVarInt(0, false);
            } else {
                out.writeVarInt(value.length, false);
                out.writeBytes(value);
            }
        }
    }

    /**
     * Write a list of messages to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of messages value to write
     * @param codec the codec for the message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> void writeMessageList(
            WritableSequentialData out, FieldDefinition field, List<T> list, Codec<T> codec) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use writeMessage with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            writeMessageNoChecks(out, field, list.get(i), codec);
        }
    }

    /**
     * Write a list of bytes objects to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of bytes objects value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeBytesList(
            WritableSequentialData out, FieldDefinition field, List<? extends RandomAccessData> list)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a message type " + field;
        assert field.repeated() : "Use writeBytes with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            writeBytesNoChecks(out, field, list.get(i), false);
        }
    }

    /**
     * Write a generic delimited field by delegating to a supplied `writer` to write the actual elements.
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param size the size of all the elements together, in bytes
     * @param writer the Consumer that accepts the `out` and writes the actual elements
     * @param <T> the type of the data output that extends WritableSequentialData
     */
    public static <T extends WritableSequentialData> void writeDelimited(
            final T out, final FieldDefinition field, final int size, final Consumer<T> writer) {
        writeTag(out, field);
        out.writeVarInt(size, false);
        writer.accept(out);
    }

    // ================================================================================================================
    // SIZE OF METHODS

    /** Size of a fixed length 32 bit value in bytes */
    static final int FIXED32_SIZE = 4;

    /** Size of a fixed length 64 bit value in bytes */
    static final int FIXED64_SIZE = 8;

    /** Size of a max length varint value in bytes */
    private static final int MAX_VARINT_SIZE = 10;

    /**
     * Get the number of bytes that would be needed to encode an {@code int32} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfVarInt32(final int value) {
        if (value >= 0) {
            return sizeOfUnsignedVarInt32(value);
        } else {
            // Must sign-extend.
            return MAX_VARINT_SIZE;
        }
    }

    /**
     * Get the number of bytes that would be needed to encode an {@code int32} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfVarInt64(final long value) {
        return sizeOfUnsignedVarInt64(value);
    }

    /**
     * Get the number of bytes that would be needed to encode a {@code uint32} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfUnsignedVarInt32(final int value) {
        return sizeOfUnsignedVarInt64(Integer.toUnsignedLong(value));
    }

    /**
     * Get number of bytes that would be needed to encode a {@code uint64} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    static int sizeOfUnsignedVarInt64(long value) {
        // handle popular special case up front
        if ((value & (~0L << 7)) == 0L) return 1;
        final int clz = Long.numberOfLeadingZeros(value);
        return ((Long.SIZE * 9 + (1 << 6)) - (clz * 9)) >>> 6;
    }

    /**
     * Get number of bytes that would be needed to encode a field tag. Field wire type is
     * calculated based on field type using {@link #wireType(FieldDefinition)} method.
     *
     * @param field The field part of tag
     * @return the number of bytes for encoded value
     */
    public static int sizeOfTag(final FieldDefinition field) {
        return sizeOfTag(field, wireType(field));
    }

    /**
     * Get number of bytes that would be needed to encode a field tag.
     *
     * @param field The field part of tag
     * @param wireType The wire type part of tag
     * @return the number of bytes for encoded value
     */
    public static int sizeOfTag(final FieldDefinition field, final ProtoConstants wireType) {
        return sizeOfVarInt32((field.number() << TAG_TYPE_BITS) | wireType.ordinal());
    }

    /**
     * Get number of bytes that would be needed to encode an optional integer field
     *
     * @param field descriptor of field
     * @param value optional integer value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalInteger(FieldDefinition field, @Nullable Integer value) {
        if (value != null) {
            final int intValue = value;
            int size = sizeOfInteger(field.type().optionalFieldDefinition, intValue);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional long field
     *
     * @param field descriptor of field
     * @param value optional long value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalLong(FieldDefinition field, @Nullable Long value) {
        if (value != null) {
            final long longValue = value;
            final int size = sizeOfLong(field.type().optionalFieldDefinition, longValue);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional float field
     *
     * @param field descriptor of field
     * @param value optional float value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalFloat(FieldDefinition field, @Nullable Float value) {
        if (value != null) {
            final int size = value == 0 ? 0 : 1 + FIXED32_SIZE;
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional double field
     *
     * @param field descriptor of field
     * @param value optional double value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalDouble(FieldDefinition field, @Nullable Double value) {
        if (value != null) {
            final int size = value == 0 ? 0 : 1 + FIXED64_SIZE;
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional boolean field
     *
     * @param field descriptor of field
     * @param value optional boolean value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalBoolean(FieldDefinition field, @Nullable Boolean value) {
        if (value != null) {
            final int size = !value ? 0 : 2;
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional string field
     *
     * @param field descriptor of field
     * @param value optional string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalString(FieldDefinition field, @Nullable String value) {
        if (value != null) {
            final int size = sizeOfString(field.type().optionalFieldDefinition, value);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional string field represented by a byte[]
     *
     * @param field descriptor of field
     * @param value optional string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalString(FieldDefinition field, @Nullable byte[] value) {
        if (value != null) {
            final int size = sizeOfString(field.type().optionalFieldDefinition, value, true);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an optional bytes field
     *
     * @param field descriptor of field
     * @param value optional bytes value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalBytes(FieldDefinition field, @Nullable RandomAccessData value) {
        if (value != null) {
            final int size = sizeOfBytes(field.type().optionalFieldDefinition, value);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
        }
        return 0;
    }

    /**
     * Get number of bytes that would be needed to encode an integer field
     *
     * @param field descriptor of field
     * @param value integer value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfInteger(FieldDefinition field, int value) {
        return sizeOfInteger(field, value, true);
    }

    /**
     * Get number of bytes that would be needed to encode an integer field
     *
     * @param field descriptor of field
     * @param value integer value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfInteger(FieldDefinition field, int value, boolean skipDefault) {
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        return switch (field.type()) {
            case INT32 -> sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(value);
            case UINT32 -> sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt32(value);
            case SINT32 ->
                sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG)
                        + sizeOfUnsignedVarInt64(((long) value << 1) ^ ((long) value >> 63));
            case SFIXED32, FIXED32 -> sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED32_SIZE;
            default -> throw unsupported();
        };
    }

    /**
     * Get number of bytes that would be needed to encode a long field
     *
     * @param field descriptor of field
     * @param value long value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfLong(FieldDefinition field, long value) {
        return sizeOfLong(field, value, true);
    }

    /**
     * Get number of bytes that would be needed to encode a long field
     *
     * @param field descriptor of field
     * @param value long value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfLong(FieldDefinition field, long value, boolean skipDefault) {
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        return switch (field.type()) {
            case INT64, UINT64 -> sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64(value);
            case SINT64 ->
                sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64((value << 1) ^ (value >> 63));
            case SFIXED64, FIXED64 -> sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED64_SIZE;
            default -> throw unsupported();
        };
    }

    /**
     * Get number of bytes that would be needed to encode a float field
     *
     * @param field descriptor of field
     * @param value float value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfFloat(FieldDefinition field, float value) {
        if (!field.oneOf() && value == 0) return 0;
        return sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED32_SIZE;
    }

    /**
     * Get number of bytes that would be needed to encode a double field
     *
     * @param field descriptor of field
     * @param value double value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfDouble(FieldDefinition field, double value) {
        if (!field.oneOf() && value == 0) return 0;
        return sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED64_SIZE;
    }

    /**
     * Get number of bytes that would be needed to encode a boolean field
     *
     * @param field descriptor of field
     * @param value boolean value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBoolean(FieldDefinition field, boolean value) {
        return sizeOfBoolean(field, value, true);
    }

    /**
     * Get number of bytes that would be needed to encode a boolean field
     *
     * @param field descriptor of field
     * @param value boolean value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBoolean(FieldDefinition field, boolean value, boolean skipDefault) {
        return (value || field.oneOf() || !skipDefault) ? sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + 1 : 0;
    }

    /**
     * Get number of bytes that would be needed to encode an enum field
     *
     * @param field descriptor of field
     * @param enumValue enum value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfEnum(FieldDefinition field, EnumWithProtoMetadata enumValue) {
        if (!field.oneOf() && (enumValue == null || enumValue.protoOrdinal() == 0)) {
            return 0;
        }
        return sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(enumValue.protoOrdinal());
    }

    /**
     * Get number of bytes that would be needed to encode a string field
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfString(FieldDefinition field, String value) {
        return sizeOfString(field, value, true);
    }

    /**
     * Get number of bytes that would be needed to encode a string field
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfString(FieldDefinition field, byte[] value) {
        return value == null || value.length == 0 ? 0 : sizeOfDelimited(field, value.length);
    }

    /**
     * Get number of bytes that would be needed to encode a string field
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfString(FieldDefinition field, String value, boolean skipDefault) {
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value == null || value.isEmpty())) {
            return 0;
        }
        return sizeOfDelimited(field, sizeOfStringNoTag(value));
    }

    /**
     * Get number of bytes that would be needed to encode a string field
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfString(FieldDefinition field, byte[] value, boolean skipDefault) {
        if (skipDefault && !field.oneOf() && (value == null || value.length == 0)) {
            return 0;
        }
        return sizeOfDelimited(field, value.length);
    }

    /**
     * Get number of bytes that would be needed to encode a string, without field tag
     *
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    static int sizeOfStringNoTag(String value) {
        // When not a oneOf don't write default value
        if ((value == null || value.isEmpty())) {
            return 0;
        }
        try {
            return Utf8Tools.encodedLength(value);
        } catch (IOException e) { // fall back to JDK
            return value.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    /**
     * Get number of bytes that would be needed to encode a string, without field tag
     *
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    static int sizeOfStringNoTag(byte[] value) {
        return value == null ? 0 : value.length;
    }

    /**
     * Get number of bytes that would be needed to encode a bytes field
     *
     * @param field descriptor of field
     * @param value bytes value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBytes(FieldDefinition field, RandomAccessData value) {
        return sizeOfBytes(field, value, true);
    }

    /**
     * Get number of bytes that would be needed to encode a bytes field
     *
     * @param field descriptor of field
     * @param value bytes value to get encoded size for
     * @param skipDefault default value results in zero size
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBytes(FieldDefinition field, RandomAccessData value, boolean skipDefault) {
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value.length() == 0)) {
            return 0;
        }
        return sizeOfDelimited(field, (int) value.length());
    }

    /**
     * Get number of bytes that would be needed to encode a message field
     *
     * @param field descriptor of field
     * @param message message value to get encoded size for
     * @param codec the protobuf codec for message type
     * @return the number of bytes for encoded value
     * @param <T> The type of the message
     */
    public static <T> int sizeOfMessage(FieldDefinition field, T message, Codec<T> codec) {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + 1;
        } else if (message != null) {
            final int size = codec.measureRecord(message);
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
        } else {
            return 0;
        }
    }

    /**
     * Get number of bytes that would be needed to encode an integer list field
     *
     * @param field descriptor of field
     * @param list integer list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfIntegerList(FieldDefinition field, List<Integer> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        final int listSize = list.size();
        switch (field.type()) {
            case INT32 -> {
                for (int i = 0; i < listSize; i++) {
                    size += sizeOfVarInt32(list.get(i));
                }
            }
            case UINT32 -> {
                for (int i = 0; i < listSize; i++) {
                    size += sizeOfUnsignedVarInt32(list.get(i));
                }
            }
            case SINT32 -> {
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += sizeOfUnsignedVarInt64((val << 1) ^ (val >> 63));
                }
            }
            case SFIXED32, FIXED32 -> size += FIXED32_SIZE * list.size();
            default -> throw unsupported();
        }
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a long list field
     *
     * @param field descriptor of field
     * @param list long list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfLongList(FieldDefinition field, List<Long> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        final int listSize = list.size();
        switch (field.type()) {
            case INT64, UINT64 -> {
                for (int i = 0; i < listSize; i++) {
                    size += sizeOfUnsignedVarInt64(list.get(i));
                }
            }
            case SINT64 -> {
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += sizeOfUnsignedVarInt64((val << 1) ^ (val >> 63));
                }
            }
            case SFIXED64, FIXED64 -> size += FIXED64_SIZE * list.size();
            default -> throw unsupported();
        }
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a float list field
     *
     * @param field descriptor of field
     * @param list float list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfFloatList(FieldDefinition field, List<Float> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = FIXED32_SIZE * list.size();
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a double list field
     *
     * @param field descriptor of field
     * @param list double list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfDoubleList(FieldDefinition field, List<Double> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = FIXED64_SIZE * list.size();
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a boolean list field
     *
     * @param field descriptor of field
     * @param list boolean list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBooleanList(FieldDefinition field, List<Boolean> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = list.size();
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode an enum list field
     *
     * @param field descriptor of field
     * @param list enum list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfEnumList(FieldDefinition field, List<? extends EnumWithProtoMetadata> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            size += sizeOfUnsignedVarInt64(list.get(i).protoOrdinal());
        }
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a string list field
     *
     * @param field descriptor of field
     * @param list string list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfStringList(FieldDefinition field, List<byte[]> list) {
        int size = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            size += sizeOfDelimited(field, sizeOfStringNoTag(list.get(i)));
        }
        return size;
    }

    /**
     * Get number of bytes that would be needed to encode a message list field
     *
     * @param field descriptor of field
     * @param list message list value to get encoded size for
     * @param codec the protobuf codec for message type
     * @return the number of bytes for encoded value
     * @param <T> type for message
     */
    public static <T> int sizeOfMessageList(FieldDefinition field, List<T> list, Codec<T> codec) {
        int size = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            size += sizeOfMessage(field, list.get(i), codec);
        }
        return size;
    }

    /**
     * Get number of bytes that would be needed to encode a bytes list field
     *
     * @param field descriptor of field
     * @param list bytes list value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBytesList(FieldDefinition field, List<? extends RandomAccessData> list) {
        int size = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final long valueLength = list.get(i).length();
            size += sizeOfDelimited(field, Math.toIntExact(valueLength));
        }
        return size;
    }

    /**
     * Get number of bytes that would be needed to encode a field of wire type delimited
     *
     * @param field The field definition of the field to be measured
     * @param length The length of the delimited field data in bytes
     * @return the number of bytes for encoded value
     */
    public static int sizeOfDelimited(final FieldDefinition field, final int length) {
        return Math.toIntExact(sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(length) + length);
    }
}
