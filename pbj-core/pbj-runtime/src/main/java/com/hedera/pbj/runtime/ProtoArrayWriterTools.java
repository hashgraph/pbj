package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.FieldType.FIXED32;
import static com.hedera.pbj.runtime.FieldType.FIXED64;
import static com.hedera.pbj.runtime.FieldType.INT32;
import static com.hedera.pbj.runtime.FieldType.INT64;
import static com.hedera.pbj.runtime.FieldType.SFIXED32;
import static com.hedera.pbj.runtime.FieldType.SFIXED64;
import static com.hedera.pbj.runtime.FieldType.SINT32;
import static com.hedera.pbj.runtime.FieldType.SINT64;
import static com.hedera.pbj.runtime.FieldType.UINT32;
import static com.hedera.pbj.runtime.FieldType.UINT64;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_32_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_64_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG;
import static com.hedera.pbj.runtime.ProtoWriterTools.FIXED32_SIZE;
import static com.hedera.pbj.runtime.ProtoWriterTools.FIXED64_SIZE;
import static com.hedera.pbj.runtime.ProtoWriterTools.TAG_TYPE_BITS;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfBoolean;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfBytes;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfDouble;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfFloat;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfString;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfStringNoTag;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfTag;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfUnsignedVarInt32;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfUnsignedVarInt64;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfVarInt32;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Suite of static utility methods to assist in writing protobuf messages into Java byte arrays. Its number one focus
 * is performance.
 */
@SuppressWarnings({"DuplicatedCode", "ForLoopReplaceableByForEach"})
public final class ProtoArrayWriterTools {
    /** Table to determine the length of a varint based on the number of leading zeros */
    private static final int[] VAR_INT_LENGTHS = new int[65];
    static {
        for (int i = 0; i <= 64; ++i) VAR_INT_LENGTHS[i] = ((63 - i) / 7);
    }
    /** VarHandle to write little-endian integers to byte arrays */
    private static final VarHandle INTEGER_LITTLE_ENDIAN = MethodHandles.byteArrayViewVarHandle(int[].class,
            ByteOrder.LITTLE_ENDIAN);
    /** VarHandle to write little-endian longs to byte arrays */
    private static final VarHandle LONG_LITTLE_ENDIAN = MethodHandles.byteArrayViewVarHandle(long[].class,
            ByteOrder.LITTLE_ENDIAN);

    /**
     * Write an unsigned varint to the output.
     *
     * @param output The byte array to write to
     * @param offset The offset to start writing at
     * @param value The value to write
     * @return The number of bytes written
     */
    @SuppressWarnings("fallthrough")
    public static int writeUnsignedVarInt(@NonNull byte[] output, final int offset, final long value) {
        int length = VAR_INT_LENGTHS[Long.numberOfLeadingZeros(value)];
        output[offset+length] = (byte)(value >>> (length * 7));
        switch (length - 1) {
            case 8:
                output[offset+8] = (byte)((value >>> 56) | 0x80);
                // Deliberate fallthrough
            case 7:
                output[offset+7] = (byte)((value >>> 49) | 0x80);
                // Deliberate fallthrough
            case 6:
                output[offset+6] = (byte)((value >>> 42) | 0x80);
                // Deliberate fallthrough
            case 5:
                output[offset+5] = (byte)((value >>> 35) | 0x80);
                // Deliberate fallthrough
            case 4:
                output[offset+4] = (byte)((value >>> 28) | 0x80);
                // Deliberate fallthrough
            case 3:
                output[offset+3] = (byte)((value >>> 21) | 0x80);
                // Deliberate fallthrough
            case 2:
                output[offset+2] = (byte)((value >>> 14) | 0x80);
                // Deliberate fallthrough
            case 1:
                output[offset+1] = (byte)((value >>> 7) | 0x80);
                // Deliberate fallthrough
            case 0:
                output[offset] = (byte)(value | 0x80);
        }
        return length;
    }

    /**
     * Write a signed zigzag encoded varint to the output.
     *
     * @param output The byte array to write to
     * @param offset The offset to start writing at
     * @param value The value to write
     * @return The number of bytes written
     */
    public static int writeSignedVarInt(@NonNull byte[] output, final int offset, final long value) {
        final long zigZag = (value << 1) ^ (value >> 63);
        return writeUnsignedVarInt(output, offset, zigZag);
    }

    /**
     * Write a protobuf tag to the output.
     *
     * @param offset The offset to start writing at
     * @param field The field to include in tag
     * @param wireType The field wire type to include in tag
     * @return The number of bytes written
     */
    public static int writeTag(@NonNull byte[] output, final int offset, @NonNull final FieldDefinition field,
            @NonNull final ProtoConstants wireType) {
        return writeUnsignedVarInt(output, offset, ((long)field.number() << TAG_TYPE_BITS) | wireType.ordinal());
    }


    /**
     * Write a string to data output, assuming the field is non-repeated.
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing, the field must be non-repeated
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static int writeString(@NonNull byte[] output, final int offset, @NonNull final FieldDefinition field,
            final String value, final boolean skipDefault) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        return writeStringNoChecks(output, offset, field, value, skipDefault);
    }

    /**
     * Write an integer to data output - no validation checks.
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the string value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    private static int writeStringNoChecks(@NonNull byte[] output, final int offset, @NonNull final FieldDefinition field,
            final String value, final boolean skipDefault) throws IOException {
        int bytesWritten = 0;
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value == null || value.isEmpty())) {
            return 0;
        }
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfStringNoTag(value));
        bytesWritten += Utf8Tools.encodeUtf8(output, offset + bytesWritten, value);
        return bytesWritten;
    }

    /**
     * Write an optional string to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the field definition for the string field
     * @param value the optional string value to write
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static int writeOptionalString(@NonNull byte[] output, final int offset, @NonNull final FieldDefinition field,
            @Nullable final String value) throws IOException {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfString(newField, value));
            bytesWritten += writeStringNoChecks(output, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    /**
     * Write a boolean to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the boolean value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeBoolean(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final boolean value, final boolean skipDefault) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use writeBooleanList with repeated types";
        int bytesWritten = 0;
        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf() || !skipDefault) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            output[offset + bytesWritten] = value ? (byte) 1 : 0;
            bytesWritten ++;
        }
        return bytesWritten;
    }

    /**
     * Write an optional boolean to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional boolean value to write
     * @return the number of bytes written
     */
    public static int writeOptionalBoolean(@NonNull final byte[] output, final int offset, @NonNull FieldDefinition field, @Nullable Boolean value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfBoolean(newField, value));
            bytesWritten += writeBoolean(output, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    /**
     * Write an Int32 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeInt32(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final int value, boolean skipDefault) {
        assert field.type() == INT32 : "Not an Int32 type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, value);
        return bytesWritten;
    }

    /**
     * Write an UInt32 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeUInt32(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final int value, boolean skipDefault) {
        assert field.type() == UINT32 : "Not a UInt32 type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, Integer.toUnsignedLong(value));
        return bytesWritten;
    }


    /**
     * Write an SInt32 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writSInt32(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final int value, boolean skipDefault) {
        assert field.type() == SINT32 : "Not a SInt32 type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeSignedVarInt(output, offset + bytesWritten, value);
        return bytesWritten;
    }

    /**
     * Write a SFixed32 or Fixed32 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeFixed32(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final int value, boolean skipDefault) {
        assert field.type() == FieldType.FIXED32 || field.type() == FieldType.SFIXED32
                : "Not a Fixed32 or SFixed32 type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_FIXED_32_BIT);
        INTEGER_LITTLE_ENDIAN.set(output, offset + bytesWritten, value);
        bytesWritten += Integer.BYTES;
        return bytesWritten;
    }

    /**
     * Write an optional signed integer to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional integer value to write
     * @return the number of bytes written
     */
    public static int writeOptionalInt32Value(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Integer value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten,
                    sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(value));
            bytesWritten += writeInt32(output, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    /**
     * Write an optional unsigned integer to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional integer value to write
     * @return the number of bytes written
     */
    public static int writeOptionalUInt32Value(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Integer value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten,
                    sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfVarInt32(value));
            bytesWritten += writeUInt32(output, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    /**
     * Write an Int64 or UInt64 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeInt64(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final long value, boolean skipDefault) {
        assert field.type() == INT64 || field.type() == UINT64
                : "Not an Int64 or UInt64 type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0L) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, value);
        return bytesWritten;
    }

    /**
     * Write an SInt64 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeSInt64(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final long value, boolean skipDefault) {
        assert field.type() == SINT64 : "Not a SInt64 type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0L) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeSignedVarInt(output, offset + bytesWritten, value);
        return bytesWritten;
    }

    /**
     * Write a SFixed64 or Fixed64 to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the int value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     */
    public static int writeFixed64(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            final long value, boolean skipDefault) {
        assert field.type() == FIXED64 || field.type() == SFIXED64
                : "Not a Fixed64 or SFixed64 type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0L) return 0;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_FIXED_32_BIT);
        LONG_LITTLE_ENDIAN.set(output, offset + bytesWritten, value);
        bytesWritten += Long.BYTES;
        return bytesWritten;
    }

    /**
     * Write an optional signed or unsinged long to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     * @return the number of bytes written
     */
    public static int writeOptionalInt64Value(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Long value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten,
                    sizeOfTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG) + sizeOfUnsignedVarInt64(value));
            bytesWritten += writeInt64(output, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    /**
     * Write a float to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the float value to write
     * @return the number of bytes written
     */
    public static int writeFloat(@NonNull final byte[] output, final int offset, FieldDefinition field, float value) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert !field.repeated() : "Use writeFloatList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_FIXED_32_BIT);
        INTEGER_LITTLE_ENDIAN.set(output, offset + bytesWritten, Float.floatToIntBits(value));
        bytesWritten += Integer.BYTES;
        return bytesWritten;
    }

    /**
     * Write a double to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the double value to write
     * @return the number of bytes written
     */
    public static int writeDouble(@NonNull final byte[] output, final int offset, FieldDefinition field, double value) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert !field.repeated() : "Use writeDoubleList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_FIXED_64_BIT);
        LONG_LITTLE_ENDIAN.set(output, offset + bytesWritten, Double.doubleToLongBits(value));
        bytesWritten += Long.BYTES;
        return bytesWritten;
    }

    /**
     * Write an optional float to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     * @return the number of bytes written
     */
    public static int writeOptionalFloat(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Float value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfFloat(newField, value));
            bytesWritten += writeFixed32(output, offset + bytesWritten, newField, Float.floatToIntBits(value), true);
        }
        return bytesWritten;
    }

    /**
     * Write an optional double to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     * @return the number of bytes written
     */
    public static int writeOptionalDouble(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Double value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfDouble(newField, value));
            bytesWritten += writeFixed64(output, offset + bytesWritten, newField, Double.doubleToLongBits(value), true);
        }
        return bytesWritten;
    }

    /**
     * Write an optional bytes to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the optional long value to write
     * @return the number of bytes written
     */
    public static int writeOptionalBytes(
            @NonNull final byte[] output, final int offset, FieldDefinition field, @Nullable Bytes value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            final int size = sizeOfBytes(newField, value);
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten,size);
            if (size > 0) {
                bytesWritten += writeTag(output, offset+ bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten,size);
                bytesWritten += value.writeTo(output, offset + bytesWritten);
            }
        }
        return bytesWritten;
    }

    /**
     * Write an enum to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param enumValue the enum value to write
     * @return the number of bytes written
     */
    public static int writeEnum(@NonNull final byte[] output, final int offset, @NonNull final FieldDefinition field,
            @Nullable final EnumWithProtoMetadata enumValue) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert !field.repeated() : "Use writeEnumList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && (enumValue == null || enumValue.protoOrdinal() == 0)) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += writeUnsignedVarInt(output, offset, enumValue == null ? 0 : enumValue.protoOrdinal());
        return bytesWritten;
    }


    /**
     * Write a message to data output, assuming the corresponding field is non-repeated.
     *
     * @param <T> type of message
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing, the field must not be repeated
     * @param message the message to write
     * @param codec the codec for the given message type
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static <T> int writeMessage(@NonNull final byte[] output, final int offset,
            @NonNull final FieldDefinition field, final T message, final Codec<T> codec)
            throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use writeMessageList with repeated types";
        return writeMessageNoChecks(output, offset, field, message, codec);
    }


    /**
     * Write a message to data output - no validation checks.
     *
     * @param <T> type of message
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param message the message to write
     * @param codec the codec for the given message type
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    private static <T> int writeMessageNoChecks(@NonNull final byte[] output, final int offset,
            @NonNull final FieldDefinition field, final T message, final Codec<T> codec)
            throws IOException {
        // When not a oneOf don't write default value
        int bytesWritten = 0;
        if (field.oneOf() && message == null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            bytesWritten += writeUnsignedVarInt(output,offset + bytesWritten,0);
        } else if (message != null) {
            bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
            final int size = codec.measureRecord(message);
            bytesWritten += writeUnsignedVarInt(output,offset + bytesWritten,size);
            if (size > 0) {
                bytesWritten += codec.write(message, output, offset + bytesWritten);
            }
        }
        return bytesWritten;
    }


    /**
     * Write a bytes to data output, assuming the corresponding field is non-repeated, and field type
     * is any delimited: bytes, string, or message.
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing, the field must not be repeated
     * @param value the bytes value to write
     * @param skipDefault default value results in no-op for non-oneOf
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static int writeBytes(@NonNull final byte[] output, final int offset,
            @NonNull final FieldDefinition field,
            final Bytes value,
            boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use writeBytesList with repeated types";
        return writeBytesNoChecks(output, offset, field, value, skipDefault);
    }

    /**
     * Write a bytes to data output - no validation checks.
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param value the bytes value to write
     * @param skipZeroLength this is true for normal single bytes and false for repeated lists
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    private static int writeBytesNoChecks(@NonNull final byte[] output, final int offset,
            @NonNull final FieldDefinition field,
            final Bytes value,
            final boolean skipZeroLength)
            throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (skipZeroLength && (value.length() == 0))) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, value.length());
        bytesWritten += value.writeTo(output, offset + bytesWritten);
        return bytesWritten;
    }


    // ================================================================================================================
    // LIST VERSIONS OF WRITE METHODS

    /**
     * Write a list of integers to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of integers value to write
     * @return the number of bytes written
     */
    public static int writeInt32List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Integer> list) {
        assert field.type() !=  INT32 : "Not a long type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            final int val = list.get(i);
            size += sizeOfVarInt32(val);
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, list.get(i));
        }
        return bytesWritten;
    }

    /**
     * Write a list of integers to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of integers value to write
     * @return the number of bytes written
     */
    public static int writeUInt32List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Integer> list) {
        assert field.type() !=  UINT32 : "Not a long type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            final int val = list.get(i);
            size += sizeOfUnsignedVarInt64(Integer.toUnsignedLong(val));
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, Integer.toUnsignedLong(list.get(i)));
        }
        return bytesWritten;
    }

    /**
     * Write a list of integers to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of integers value to write
     * @return the number of bytes written
     */
    public static int writeSInt32List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Integer> list) {
        assert field.type() !=  SINT32 : "Not a long type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            final int val = list.get(i);
            size += sizeOfUnsignedVarInt64(((long) val << 1) ^ ((long) val >> 63));
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeSignedVarInt(output, offset + bytesWritten, Integer.toUnsignedLong(list.get(i)));
        }
        return bytesWritten;
    }

    /**
     * Write a list of integers to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of integers value to write
     * @return the number of bytes written
     */
    public static int writeFixed32List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Integer> list) {
        assert field.type() !=  FIXED32 && field.type() != SFIXED32 : "Not a long type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, (long) list.size() * FIXED32_SIZE);
        for (int i = 0; i < listSize; i++) {
            INTEGER_LITTLE_ENDIAN.set(output, offset + bytesWritten, list.get(i));
            bytesWritten += Integer.BYTES;
        }
        return bytesWritten;
    }

    /**
     * Write a list of longs to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of longs value to write
     * @return the number of bytes written
     */
    public static int writeInt64List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Long> list) {
        assert field.type() !=  INT64 && field.type() != UINT64 : "Not a long type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            final long val = list.get(i);
            size += sizeOfUnsignedVarInt64(val);
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, list.get(i));
        }
        return bytesWritten;
    }

    /**
     * Write a list of longs to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of longs value to write
     * @return the number of bytes written
     */
    public static int writeSInt64List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Long> list) {
        assert field.type() !=  SINT64 : "Not a SINT64 type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            final long val = list.get(i);
            size += sizeOfUnsignedVarInt64(val);
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeSignedVarInt(output, offset + bytesWritten, list.get(i));
        }
        return bytesWritten;
    }

    /**
     * Write a list of longs to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of longs value to write
     * @return the number of bytes written
     */
    public static int writeFixed64List(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Long> list) {
        assert field.type() !=  FIXED64 && field.type() != SFIXED64 : "Not a fixed long type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) return 0;
        final int listSize = list.size();
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, (long) list.size() * FIXED64_SIZE);
        for (int i = 0; i < listSize; i++) {
            LONG_LITTLE_ENDIAN.set(output, offset + bytesWritten, list.get(i));
            bytesWritten += Long.BYTES;
        }
        return bytesWritten;
    }

    /**
     * Write a list of floats to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of floats value to write
     * @return the number of bytes written
     */
    public static int writeFloatList(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Float> list) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert field.repeated() : "Use writeFloat with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int size = list.size() * FIXED32_SIZE;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            INTEGER_LITTLE_ENDIAN.set(output, offset + bytesWritten, Float.floatToRawIntBits(list.get(i)));
            bytesWritten += Integer.BYTES;
        }
        return bytesWritten;
    }

    /**
     * Write a list of doubles to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of doubles value to write
     * @return the number of bytes written
     */
    public static int writeDoubleList(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Double> list) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert field.repeated() : "Use writeDouble with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int size = list.size() * FIXED64_SIZE;
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            LONG_LITTLE_ENDIAN.set(output, offset + bytesWritten, Double.doubleToLongBits(list.get(i)));
            bytesWritten += Long.BYTES;
        }
        return bytesWritten;
    }

    /**
     * Write a list of booleans to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of booleans value to write
     * @return the number of bytes written
     */
    public static int writeBooleanList(@NonNull final byte[] output, final int offset, FieldDefinition field, List<Boolean> list) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use writeBoolean with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        // write
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, list.size());
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final boolean b = list.get(i);
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, b ? 1 : 0);
        }
        return bytesWritten;
    }

    /**
     * Write a list of enums to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of enums value to write
     * @return the number of bytes written
     */
    public static int writeEnumList(
            @NonNull final byte[] output, final int offset, FieldDefinition field, List<? extends EnumWithProtoMetadata> list) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use writeEnum with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            size += sizeOfUnsignedVarInt32(list.get(i).protoOrdinal());
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(output, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, size);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, list.get(i).protoOrdinal());
        }
        return bytesWritten;
    }

    /**
     * Write a list of strings to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of strings value to write
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static int writeStringList(@NonNull final byte[] output, final int offset, FieldDefinition field, List<String> list)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use writeString with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int listSize = list.size();
        int bytesWritten = 0;
        for (int i = 0; i < listSize; i++) {
            final String value = list.get(i);
            bytesWritten += writeTag(output, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            bytesWritten += writeUnsignedVarInt(output, offset + bytesWritten, sizeOfStringNoTag(value));
            bytesWritten += Utf8Tools.encodeUtf8(output, offset + bytesWritten, value);
        }
        return bytesWritten;
    }

    /**
     * Write a list of messages to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of messages value to write
     * @param codec the codec for the message type
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     * @param <T> type of message
     */
    public static <T> int writeMessageList(
            @NonNull final byte[] output, final int offset, FieldDefinition field, List<T> list, Codec<T> codec) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use writeMessage with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int listSize = list.size();
        int bytesWritten = 0;
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeMessageNoChecks(output, offset + bytesWritten, field, list.get(i), codec);
        }
        return bytesWritten;
    }

    /**
     * Write a list of bytes objects to data output
     *
     * @param output the byte array to write to
     * @param offset the offset to start writing at
     * @param field the descriptor for the field we are writing
     * @param list the list of bytes objects value to write
     * @return the number of bytes written
     * @throws IOException If a I/O error occurs
     */
    public static int writeBytesList(
            @NonNull final byte[] output, final int offset, FieldDefinition field, List<? extends Bytes> list)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a message type " + field;
        assert field.repeated() : "Use writeBytes with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int listSize = list.size();
        int bytesWritten = 0;
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeBytesNoChecks(output, offset + bytesWritten, field, list.get(i), false);
        }
        return bytesWritten;
    }

}
