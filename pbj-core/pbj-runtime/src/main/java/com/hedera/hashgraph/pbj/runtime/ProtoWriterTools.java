package com.hedera.hashgraph.pbj.runtime;

import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.hashgraph.pbj.runtime.io.DataOutput;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import static com.hedera.hashgraph.pbj.runtime.ProtoConstants.*;
import static com.hedera.hashgraph.pbj.runtime.Utf8Tools.encodeUtf8;
import static com.hedera.hashgraph.pbj.runtime.Utf8Tools.encodedLength;

/**
 * Static helper methods for Writers
 */
@SuppressWarnings({"DuplicatedCode", "OptionalUsedAsFieldOrParameterType"})
public final class ProtoWriterTools {

    /** The number of leading bits of the tag that are used to store field type, the rest is field number */
    private static final int TAG_TYPE_BITS = 3;

    /** Instance should never be created */
    private ProtoWriterTools() {}

    // ================================================================================================================
    // COMMON METHODS

    /**
     * Write a protobuf tag to the output
     *
     * @param out The data output to write to
     * @param field The field to include in tag
     * @param wireType The field wire type to include in tag
     * @throws IOException if an I/O error occurs
     */
    private static void writeTag(final DataOutput out, final FieldDefinition field, final int wireType) throws IOException {
        out.writeVarInt((field.number() << TAG_TYPE_BITS) | wireType, false);
    }

    /** Create a unsupported field type exception */
    private static RuntimeException unsupported() {
        return new RuntimeException("Unsupported field type. Bug in ProtoOutputStream, shouldn't happen.");
    }


    // ================================================================================================================
    // STANDARD WRITE METHODS

    /**
     * Write a integer to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeInteger(DataOutput out, FieldDefinition field, int value) throws IOException {
        assert switch(field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
            default -> false;
        } : "Not an integer type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";

        if (!field.oneOf() && value == 0) {
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeLong(DataOutput out, FieldDefinition field, long value) throws IOException {
        assert switch(field.type()) {
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
            default -> false;
        } : "Not a long type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (!field.oneOf() && value == 0) {
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeFloat(DataOutput out, FieldDefinition field, float value) throws IOException {
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeDouble(DataOutput out, FieldDefinition field, double value) throws IOException {
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeBoolean(DataOutput out, FieldDefinition field, boolean value) throws IOException {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use writeBooleanList with repeated types";
        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf()) {
            writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            out.writeByte(value ? (byte)1 : 0);
        }
    }

    /**
     * Write a enum to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param enumValue the enum value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeEnum(DataOutput out, FieldDefinition field, EnumWithProtoOrdinal enumValue) throws IOException {
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
     * Write a string to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeString(DataOutput out, FieldDefinition field, String value) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        writeStringNoChecks(out, field, value);
    }

    /**
     * Write a integer to data output - No validation checks
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @throws IOException If a I/O error occurs
     */
    private static void writeStringNoChecks(DataOutput out, FieldDefinition field, String value) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value == null || value.isBlank())) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(sizeOfStringNoTag(field,value), false);
        encodeUtf8(value,out);
    }

    /**
     * Write a bytes to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the bytes value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeBytes(DataOutput out, FieldDefinition field, Bytes value) throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use writeBytesList with repeated types";
        writeBytesNoChecks(out, field, value, true);
    }

    /**
     * Write a bytes to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the bytes value to write
     * @param skipZeroLength this is true for normal single bytes and false for repeated lists
     * @throws IOException If a I/O error occurs
     */
    private static void writeBytesNoChecks(DataOutput out, FieldDefinition field, Bytes value, boolean skipZeroLength) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (skipZeroLength && (value.getLength() == 0))) {
            return;
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(value.getLength(), false);
        final long posBefore = out.getPosition();
        out.writeBytes(value);
        final long bytesWritten = out.getPosition() - posBefore;
        if (bytesWritten != value.getLength()) {
            throw new IOException("Wrote less bytes [" + bytesWritten + "] than expected [" + value.getLength() + "]");
        }
    }

    /**
     * Write a message to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param message the message to write
     * @param writer method reference to writer for the given message type
     * @param sizeOf method reference to sizeOf measure method for the given message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> void writeMessage(DataOutput out, FieldDefinition field, T message, ProtoWriter<T> writer, ToIntFunction<T> sizeOf) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use writeMessageList with repeated types";
        writeMessageNoChecks(out, field, message, writer, sizeOf);
    }

    /**
     * Write a message to data output - No checks
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param message the message to write
     * @param writer method reference to writer for the given message type
     * @param sizeOf method reference to sizeOf measure method for the given message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    private static <T> void writeMessageNoChecks(DataOutput out, FieldDefinition field, T message, ProtoWriter<T> writer, ToIntFunction<T> sizeOf) throws IOException {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            out.writeVarInt(0, false);
        } else if (message != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final int size = sizeOf.applyAsInt(message);
            out.writeVarInt(size, false);
            if (size > 0) {
                writer.write(message, out);
            }
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalInteger(DataOutput out, FieldDefinition field, Optional<Integer> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfInteger(newField, value.get()), false);
            writeInteger(out,newField,value.get());
        }
    }

    /**
     * Write an optional long to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalLong(DataOutput out, FieldDefinition field, Optional<Long> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfLong(newField, value.get()), false);
            writeLong(out,newField,value.get());
        }
    }

    /**
     * Write an optional float to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional float value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalFloat(DataOutput out, FieldDefinition field, Optional<Float> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfFloat(newField, value.get()), false);
            writeFloat(out,newField,value.get());
        }
    }

    /**
     * Write an optional double to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional double value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalDouble(DataOutput out, FieldDefinition field, Optional<Double> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfDouble(newField, value.get()), false);
            writeDouble(out,newField,value.get());
        }
    }

    /**
     * Write an optional boolean to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param value the optional boolean value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeOptionalBoolean(DataOutput out, FieldDefinition field, Optional<Boolean> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfBoolean(newField, value.get()), false);
            writeBoolean(out,newField,value.get());
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
    public static void writeOptionalString(DataOutput out, FieldDefinition field, Optional<String> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            out.writeVarInt(sizeOfString(newField, value.get()), false);
            writeString(out,newField,value.get());
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
    public static void writeOptionalBytes(DataOutput out, FieldDefinition field, Optional<Bytes> value) throws IOException {
        if (value != null && value.isPresent()) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            final int size = sizeOfBytes(newField, value.get());
            out.writeVarInt(size, false);
            if (size > 0) {
                writeBytes(out,newField, value.get());
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeIntegerList(DataOutput out, FieldDefinition field, List<Integer> list) throws IOException {
        assert switch(field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
            default -> false;
        } : "Not an integer type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        switch (field.type()) {
            case INT32 -> {
                int size = 0;
                for (final int i : list) {
                    size += sizeOfVarInt32(i);
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (final int i : list) {
                    out.writeVarInt(i, false);
                }
            }
            case UINT32 -> {
                int size = 0;
                for (final int i : list) {
                    size += sizeOfUnsignedVarInt64(Integer.toUnsignedLong(i));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (final int i : list) {
                    out.writeVarLong(Integer.toUnsignedLong(i), false);
                }
            }
            case SINT32 -> {
                int size = 0;
                for (final int i : list) {
                    size += sizeOfUnsignedVarInt64(((long)i << 1) ^ ((long)i >> 63));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (final int i : list) {
                    out.writeVarInt(i, true);
                }
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong((long)list.size() * FIXED32_SIZE, false);
                for (final int i : list) {
                    out.writeInt(i, ByteOrder.LITTLE_ENDIAN);
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeLongList(DataOutput out, FieldDefinition field, List<Long> list) throws IOException {
        assert switch(field.type()) {
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
            default -> false;
        } : "Not a long type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        switch (field.type()) {
            case INT64, UINT64 -> {
                int size = 0;
                for (final long i : list) {
                    size += sizeOfUnsignedVarInt64(i);
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (final long i : list) {
                    out.writeVarLong(i, false);
                }
            }
            case SINT64 -> {
                int size = 0;
                for (final long i : list) {
                    size += sizeOfUnsignedVarInt64((i << 1) ^ (i >> 63));
                }
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarInt(size, false);
                for (final long i : list) {
                    out.writeVarLong(i, true);
                }
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong((long)list.size() * FIXED64_SIZE, false);
                for (final long i : list) {
                    out.writeLong(i, ByteOrder.LITTLE_ENDIAN);
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
     * @throws IOException If a I/O error occurs
     */
    public static void writeFloatList(DataOutput out, FieldDefinition field, List<Float> list) throws IOException {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert field.repeated() : "Use writeFloat with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int size = list.size() * FIXED32_SIZE;
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        for (final Float i : list) {
            out.writeFloat(i, ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * Write a list of doubles to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of doubles value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeDoubleList(DataOutput out, FieldDefinition field, List<Double> list) throws IOException {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert field.repeated() : "Use writeDouble with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        final int size = list.size() * FIXED64_SIZE;
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        for (final Double i : list) {
            out.writeDouble(i, ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * Write a list of booleans to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of booleans value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeBooleanList(DataOutput out, FieldDefinition field, List<Boolean> list) throws IOException {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use writeBoolean with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        // write
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(list.size(), false);
        for (final boolean b : list) {
            out.writeVarInt(b ? 1 : 0, false);
        }
    }

    /**
     * Write a list of enums to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of enums value to write
     * @throws IOException If a I/O error occurs
     */
    public static void writeEnumList(DataOutput out, FieldDefinition field, List<? extends EnumWithProtoOrdinal> list) throws IOException {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use writeEnum with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        int size = 0;
        for (final EnumWithProtoOrdinal enumValue : list) {
            size += sizeOfUnsignedVarInt32(enumValue.protoOrdinal());
        }
        writeTag(out, field, WIRE_TYPE_DELIMITED);
        out.writeVarInt(size, false);
        for (final EnumWithProtoOrdinal enumValue : list) {
            out.writeVarInt(enumValue.protoOrdinal(), false);
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
    public static void writeStringList(DataOutput out, FieldDefinition field, List<String> list) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use writeString with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        for (final String value : list) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            out.writeVarInt(sizeOfStringNoTag(field,value), false);
            encodeUtf8(value,out);
        }
    }

    /**
     * Write a list of messages to data output
     *
     * @param out The data output to write to
     * @param field the descriptor for the field we are writing
     * @param list the list of messages value to write
     * @param writer method reference to writer method for message type
     * @param sizeOf method reference to size of method for message type
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> void writeMessageList(DataOutput out, FieldDefinition field, List<T> list, ProtoWriter<T> writer, ToIntFunction<T> sizeOf) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use writeMessage with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        for (final T value : list) {
            writeMessageNoChecks(out, field, value, writer, sizeOf);
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
    public static void writeBytesList(DataOutput out, FieldDefinition field, List<Bytes> list) throws IOException {
        assert field.type() == FieldType.BYTES : "Not a message type " + field;
        assert field.repeated() : "Use writeBytes with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }
        for (final Bytes value : list) {
            writeBytesNoChecks(out, field, value, false);
        }
    }

    // ================================================================================================================
    // SIZE OF METHODS

    /** Size of a fixed length 32 bit value in bytes */
    private static final int FIXED32_SIZE = 4;

    /** Size of a fixed length 64 bit value in bytes */
    private static final int FIXED64_SIZE = 8;

    /** Size of a max length varint value in bytes */
    private static final int MAX_VARINT_SIZE = 10;

    /**
     * Get the number of bytes that would be needed to encode an {@code int32} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    private static int sizeOfVarInt32(final int value) {
        if (value >= 0) {
            return sizeOfUnsignedVarInt32(value);
        } else {
            // Must sign-extend.
            return MAX_VARINT_SIZE;
        }
    }

    /**
     * Get the number of bytes that would be needed to encode a {@code uint32} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    private static int sizeOfUnsignedVarInt32(final int value) {
        if ((value & (~0 << 7)) == 0) return 1;
        if ((value & (~0 << 14)) == 0) return 2;
        if ((value & (~0 << 21)) == 0) return 3;
        if ((value & (~0 << 28)) == 0) return 4;
        return 5;
    }

    /**
     * Get number of bytes that would be needed to encode a {@code uint64} field
     *
     * @param value The int value to get encoded size for
     * @return the number of bytes for encoded value
     */
    private static int sizeOfUnsignedVarInt64(long value) {
        // handle two popular special cases up front ...
        if ((value & (~0L << 7)) == 0L) return 1;
        if (value < 0L) return 10;
        // ... leaving us with 8 remaining, which we can divide and conquer
        int n = 2;
        if ((value & (~0L << 35)) != 0L) {
            n += 4;
            value >>>= 28;
        }
        if ((value & (~0L << 21)) != 0L) {
            n += 2;
            value >>>= 14;
        }
        if ((value & (~0L << 14)) != 0L) {
            n += 1;
        }
        return n;
    }

    /**
     * Get number of bytes that would be needed to encode a field tag
     *
     * @param field The field part of tag
     * @param wireType The wire type part of tag
     * @return the number of bytes for encoded value
     */
    private static int sizeOfTag(final FieldDefinition field, final int wireType) {
        return sizeOfVarInt32((field.number() << TAG_TYPE_BITS) | wireType);
    }

    /**
     * Get number of bytes that would be needed to encode an optional integer field
     *
     * @param field descriptor of field
     * @param value optional integer value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfOptionalInteger(FieldDefinition field, Optional<Integer> value) {
        if (value != null && value.isPresent()) {
            final int intValue = value.get();
            int size = sizeOfInteger(field.type().optionalFieldDefinition, intValue);
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalLong(FieldDefinition field, Optional<Long> value) {
        if (value != null && value.isPresent()) {
            final long longValue = value.get();
            final int size =  sizeOfLong(field.type().optionalFieldDefinition, longValue);
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalFloat(FieldDefinition field, Optional<Float> value) {
        if (value != null && value.isPresent()) {
            final int size = value.get() == 0 ? 0 : 1 + FIXED32_SIZE;
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalDouble(FieldDefinition field, Optional<Double> value) {
        if (value != null && value.isPresent()) {
            final int size = value.get() == 0 ? 0 : 1 + FIXED64_SIZE;
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalBoolean(FieldDefinition field, Optional<Boolean> value) {
        if (value != null && value.isPresent()) {
            final int size = !value.get() ? 0 : 2;
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalString(FieldDefinition field, Optional<String> value) {
        if (value != null && value.isPresent()) {
            final int size = sizeOfString(field.type().optionalFieldDefinition,value.get());
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
    public static int sizeOfOptionalBytes(FieldDefinition field, Optional<Bytes> value) {
        if (value != null && value.isPresent()) {
            final int size = sizeOfBytes(field.type().optionalFieldDefinition, value.get());
            return sizeOfTag(field,WIRE_TYPE_DELIMITED) + sizeOfUnsignedVarInt32(size) + size;
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
        if (!field.oneOf() && value == 0) return 0;
        return switch (field.type()) {
            case INT32 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(value);
            case UINT32 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt32(value);
            case SINT32 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64(((long)value << 1) ^ ((long)value >> 63));
            case SFIXED32, FIXED32 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED32_SIZE;
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
        if (!field.oneOf() && value == 0) return 0;
        return switch (field.type()) {
            case INT64, UINT64 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64(value);
            case SINT64 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64((value << 1) ^ (value >> 63));
            case SFIXED64, FIXED64 -> sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED64_SIZE;
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
        return sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED32_SIZE;
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
        return sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + FIXED64_SIZE;
    }

    /**
     * Get number of bytes that would be needed to encode a boolean field
     *
     * @param field descriptor of field
     * @param value boolean value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBoolean(FieldDefinition field, boolean value) {
        return (value || field.oneOf()) ? sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + 1 : 0;
    }


    /**
     * Get number of bytes that would be needed to encode an enum field
     *
     * @param field descriptor of field
     * @param enumValue enum value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfEnum(FieldDefinition field, EnumWithProtoOrdinal enumValue) {
        if (!field.oneOf() && (enumValue == null || enumValue.protoOrdinal() == 0)) {
            return 0;
        }
        return sizeOfTag(field,WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(enumValue.protoOrdinal());
    }

    /**
     * Get number of bytes that would be needed to encode a string field
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfString(FieldDefinition field, String value) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value == null || value.isBlank())) {
            return 0;
        }
        final int size = sizeOfStringNoTag(field, value);
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(size) + size;
    }

    /**
     * Get number of bytes that would be needed to encode a string, without field tag
     *
     * @param field descriptor of field
     * @param value string value to get encoded size for
     * @return the number of bytes for encoded value
     */
    private static int sizeOfStringNoTag(FieldDefinition field, String value) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value == null || value.isBlank())) {
            return 0;
        }
        try {
            return encodedLength(value);
        } catch (IOException e) { // fall back to JDK
            return value.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    /**
     * Get number of bytes that would be needed to encode a bytes field
     *
     * @param field descriptor of field
     * @param value bytes value to get encoded size for
     * @return the number of bytes for encoded value
     */
    public static int sizeOfBytes(FieldDefinition field, Bytes value) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value.getLength() == 0)) {
            return 0;
        }
        return sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(value.getLength()) + value.getLength();
    }

    /**
     * Get number of bytes that would be needed to encode a message field
     *
     * @param field descriptor of field
     * @param message message value to get encoded size for
     * @param sizeOf method reference to sizeOf measure function for message type
     * @return the number of bytes for encoded value
     * @param <T> The type of the message
     */
    public static <T> int sizeOfMessage(FieldDefinition field, T message, ToIntFunction<T> sizeOf) {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            return sizeOfTag(field, WIRE_TYPE_DELIMITED) + 1;
        } else if (message != null) {
            final int size = sizeOf.applyAsInt(message);
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
        switch (field.type()) {
            case INT32 -> {
                for (final int i : list) {
                    size += sizeOfVarInt32(i);
                }
            }
            case UINT32 -> {
                for (final int i : list) {
                    size += sizeOfUnsignedVarInt32(i);
                }
            }
            case SINT32 -> {
                for (final int i : list) {
                    size += sizeOfUnsignedVarInt64(((long)i << 1) ^ ((long)i >> 63));
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
        switch (field.type()) {
            case INT64, UINT64 -> {
                for (final long i : list) {
                    size += sizeOfUnsignedVarInt64(i);
                }
            }
            case SINT64 -> {
                for (final long i : list) {
                    size += sizeOfUnsignedVarInt64((i << 1) ^ (i >> 63));
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
    public static int sizeOfEnumList(FieldDefinition field, List<? extends EnumWithProtoOrdinal> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        for (final EnumWithProtoOrdinal enumValue : list) {
            size += sizeOfUnsignedVarInt64(enumValue.protoOrdinal());
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
    public static int sizeOfStringList(FieldDefinition field, List<String> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;

        for (final String value : list) {
            size += sizeOfTag(field, WIRE_TYPE_DELIMITED);
            final int strSize = sizeOfStringNoTag(field, value);
            size += sizeOfVarInt32(strSize) + strSize;
        }
        return size;
    }

    /**
     * Get number of bytes that would be needed to encode a message list field
     *
     * @param field descriptor of field
     * @param list message list value to get encoded size for
     * @param sizeOf method reference to sizeOf measure function for message type
     * @return the number of bytes for encoded value
     * @param <T> type for message
     */
    public static <T> int sizeOfMessageList(FieldDefinition field, List<T> list, ToIntFunction<T> sizeOf) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        for (final T value : list) {
            size += sizeOfMessage(field,value,sizeOf);
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
    public static int sizeOfBytesList(FieldDefinition field, List<Bytes> list) {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int size = 0;
        for (final Bytes value : list) {
            size += sizeOfTag(field, WIRE_TYPE_DELIMITED) + sizeOfVarInt32(value.getLength()) + value.getLength();
        }
        return size;
    }
}
