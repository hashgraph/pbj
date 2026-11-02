package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_32_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_64_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG;
import static com.hedera.pbj.runtime.ProtoWriterTools.TAG_TYPE_BITS;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.List;
import java.util.function.BiFunction;

///
/// Static helper methods to write data in protobuf format to random access outputs.
///
public class ProtoRandomAccessWriterTools {

    ///
    /// Write a protobuf tag to the output at the specified offset. Field wire format is calculated
    /// based on field type.
    ///
    /// @param out The data output to write to
    /// @param offset The offset in the output to start writing at
    /// @param field The field to write the tag for
    /// @return The number of bytes written
    ///
    public static int writeTag(final RandomAccessData out, final long offset, final FieldDefinition field) {
        return writeTag(out, offset, field, ProtoWriterTools.wireType(field));
    }

    ///
    /// Write a protobuf tag to the output at the specified offset.
    ///
    /// @param out The data output to write to
    /// @param offset The offset in the output to start writing at
    /// @param field The field to write the tag for
    /// @param wireType The field wire type to include in tag
    /// @return The number of bytes written
    ///
    public static int writeTag(
            final RandomAccessData out, final long offset, final FieldDefinition field, final ProtoConstants wireType) {
        return out.putVarLong(offset, ((long) field.number() << TAG_TYPE_BITS) | wireType.ordinal());
    }

    public static int writeInteger(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final int value) {
        return writeInteger(out, offset, field, value, true);
    }

    ///
    /// Write an integer to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The int value to write
    /// @param skipDefault Default value results in no-op for non-oneOf
    ///
    public static int writeInteger(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final int value,
            final boolean skipDefault) {
        assert switch (field.type()) {
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
                    default -> false;
                }
                : "Not an integer type " + field;
        assert !field.repeated() : "Use writeIntegerList with repeated types";

        if (skipDefault && !field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        switch (field.type()) {
            case INT32 -> {
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                bytesWritten += out.putVarInt(offset + bytesWritten, value, false);
            }
            case UINT32 -> {
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                bytesWritten += out.putVarLong(offset + bytesWritten, Integer.toUnsignedLong(value), false);
            }
            case SINT32 -> {
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                bytesWritten += out.putVarInt(offset + bytesWritten, value, true);
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_FIXED_32_BIT);
                bytesWritten += out.putInt(offset + bytesWritten, value, ByteOrder.LITTLE_ENDIAN);
            }
            default -> throw new RuntimeException("Unsupported field type: " + field);

        }
        return bytesWritten;
    }

    public static int writeLong(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final long value) {
        return writeLong(out, offset, field, value, true);
    }

    ///
    /// Write a long to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The long value to write
    /// @param skipDefault Default value results in no-op for non-oneOf
    /// @return The number of bytes written
    ///
    public static int writeLong(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final long value,
            final boolean skipDefault) {
        assert switch (field.type()) {
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
                    default -> false;
                }
                : "Not a long type " + field;
        assert !field.repeated() : "Use writeLongList with repeated types";
        if (skipDefault && !field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        switch (field.type()) {
            case INT64, UINT64 -> {
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                bytesWritten += out.putVarLong(offset + bytesWritten, value, false);
            }
            case SINT64 -> {
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                bytesWritten += out.putVarLong(offset + bytesWritten, value, true);
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_FIXED_64_BIT);
                bytesWritten += out.putLong(offset + bytesWritten, value, ByteOrder.LITTLE_ENDIAN);
            }
            default -> throw new RuntimeException("Unsupported field type: " + field);
        }
        return bytesWritten;
    }

    ///
    ///Write a float to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The float value to write
    /// @return The number of bytes written
    ///
    public static int writeFloat(
            final RandomAccessData out, final long offset, final FieldDefinition field, final float value) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert !field.repeated() : "Use writeFloatList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_FIXED_32_BIT);
        bytesWritten += out.putFloat(offset + bytesWritten, value, ByteOrder.LITTLE_ENDIAN);
        return bytesWritten;
    }

    ///
    /// Write a double to random access output at a given offset
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The double value to write
    /// @return The number of bytes written
    ///
    public static int writeDouble(
            final RandomAccessData out, final long offset, final FieldDefinition field, final double value) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert !field.repeated() : "Use writeDoubleList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_FIXED_64_BIT);
        bytesWritten += out.putDouble(offset + bytesWritten, value, ByteOrder.LITTLE_ENDIAN);
        return bytesWritten;
    }

    public static int writeBoolean(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final boolean value) {
        return writeBoolean(out, offset, field, value, true);
    }

    ///
    /// Write a boolean to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The boolean value to write
    /// @param skipDefault Default value results in no-op for non-oneOf
    /// @return The number of bytes written
    ///
    public static int writeBoolean(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final boolean value,
            final boolean skipDefault) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use writeBooleanList with repeated types";
        int bytesWritten = 0;
        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf() || !skipDefault) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            bytesWritten += out.putByte(offset + bytesWritten, value ? (byte) 1 : 0);
        }
        return bytesWritten;
    }

    ///
    /// Write a enum protoOrdinal to random access output at the given offset.
    ///
    /// @param out The data output to write to
    /// @param offset The offset in the output to write at
    /// @param field The descriptor for the field we are writing
    /// @param protoOrdinal The enum value to write
    /// @return The number of bytes written
    ///
    public static int writeEnumProtoOrdinal(
            final RandomAccessData out, final long offset, final FieldDefinition field, final int protoOrdinal) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert !field.repeated() : "Use writeEnumList with repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && protoOrdinal == 0) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        bytesWritten += out.putVarLong(offset + bytesWritten, protoOrdinal);
        return bytesWritten;
    }

    ///
    /// Write a string to random access output at the given offset, assuming the field
    /// is non-repeated.
    ///
    /// @param out The data output to write to
    /// @param offset The offset in the output to write at
    /// @param field The descriptor for the field we are writing, the field must be non-repeated
    /// @param value The string value to write
    /// @param skipDefault Default value results in no-op for non-oneOf
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    ///
    public static int writeString(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final String value,
            boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        return writeStringNoChecks(out, offset, field, value, skipDefault);
    }

    private static int writeStringNoChecks(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final String value,
            boolean skipDefault)
            throws IOException {
        // When not a oneOf don't write default value
        if (skipDefault && !field.oneOf() && (value == null || value.isEmpty())) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarLong(offset + bytesWritten, ProtoWriterTools.sizeOfStringNoTag(value));
        bytesWritten += Utf8Tools.encodeUtf8(value, out, offset + bytesWritten);
        return bytesWritten;
    }

    ///
    /// Write a bytes to random access output at a given offset, assuming the corresponding
    /// field is non-repeated, and field type is any delimited: bytes, string, or message.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing, the field must not be repeated
    /// @param value The bytes value to write
    /// @param skipDefault Default value results in no-op for non-oneOf
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    ///
    public static long writeBytes(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final RandomAccessData value,
            boolean skipDefault)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use writeBytesList with repeated types";
        return writeBytesNoChecks(out, offset, field, value, skipDefault);
    }

    ///
    /// Write a bytes to random access output - no validation checks.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The bytes value to write
    /// @param skipZeroLength This is true for normal single bytes and false for repeated lists
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    ///
    private static long writeBytesNoChecks(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final RandomAccessData value,
            final boolean skipZeroLength)
            throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (skipZeroLength && (value.length() == 0))) {
            return 0;
        }
        long bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarInt(offset + bytesWritten, Math.toIntExact(value.length()), false);
        long valueBytesWritten = out.putBytes(offset + bytesWritten, value);
        if (valueBytesWritten != value.length()) {
            throw new IOException(
                    "Wrote less bytes [" + valueBytesWritten + "] than expected [" + value.length() + "]");
        }
        bytesWritten += valueBytesWritten;
        return bytesWritten;
    }

    ///
    /// Write a message to random access output at a given offset, assuming the corresponding
    /// field is non-repeated.
    ///
    /// @param out The output to write to
    /// @param offset The offset in the output to write at
    /// @param field The descriptor for the field we are writing, the field must not be repeated
    /// @param message The message to write
    /// @param codec The codec for the given message type
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    /// @param <T> Type of message
    ///
    public static <T> long writeMessage(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final T message,
            final Codec<T> codec)
            throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use writeMessageList with repeated types";
        return writeMessageNoChecks(out, offset, field, message, codec);
    }

    private static <T> long writeMessageNoChecks(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final T message,
            final Codec<T> codec)
            throws IOException {
        long bytesWritten = 0;
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            bytesWritten += out.putVarLong(offset + bytesWritten, 0);
        } else if (message != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final int size = codec.measureRecord(message);
            bytesWritten += out.putVarLong(offset + bytesWritten, size);
            if (size > 0) {
                bytesWritten += codec.write(message, out, offset + bytesWritten);
            }
        }
        return bytesWritten;
    }

    // ================================================================================================================
    // OPTIONAL VERSIONS OF WRITE METHODS

    ///
    /// Write an optional integer to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional integer value to write
    /// @return The number of bytes written
    ///
    public static int writeOptionalInteger(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Integer value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfInteger(newField, value), false);
            bytesWritten += writeInteger(out, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional long to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional long value to write
    /// @return The number of bytes written
    ///
    public static int writeOptionalLong(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Long value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfLong(newField, value), false);
            bytesWritten += writeLong(out, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional float to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional float value to write
    /// @return The number of bytes written
    ///
    public static int writeOptionalFloat(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Float value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfFloat(newField, value), false);
            bytesWritten += writeFloat(out, offset + bytesWritten, newField, value);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional double to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional double value to write
    /// @return The number of bytes written
    ///
    public static int writeOptionalDouble(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Double value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfDouble(newField, value), false);
            bytesWritten += writeDouble(out, offset + bytesWritten, newField, value);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional boolean to random access output at a given offset.
    ///
    /// @param out The data output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional boolean value to write
    /// @return The number of bytes written
    ///
    public static int writeOptionalBoolean(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Boolean value) {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfBoolean(newField, value), false);
            bytesWritten += writeBoolean(out, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional string to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional string value to write
    /// @throws IOException If a I/O error occurs
    /// @return The number of bytes written
    ///
    public static int writeOptionalString(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable String value)
            throws IOException {
        int bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfString(newField, value), false);
            bytesWritten += writeString(out, offset + bytesWritten, newField, value, true);
        }
        return bytesWritten;
    }

    ///
    /// Write an optional bytes to random access output at a given offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param value The optional bytes value to write
    /// @throws IOException If a I/O error occurs
    /// @return The number of bytes written
    ///
    public static long writeOptionalBytes(
            final RandomAccessData out, final long offset, final FieldDefinition field, final @Nullable Bytes value)
            throws IOException {
        long bytesWritten = 0;
        if (value != null) {
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            final var newField = field.type().optionalFieldDefinition;
            final int size = ProtoWriterTools.sizeOfBytes(newField, value);
            bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
            if (size > 0) {
                bytesWritten += writeBytes(out, offset + bytesWritten, newField, value, true);
            }
        }
        return bytesWritten;
    }

    // ================================================================================================================
    // LIST VERSIONS OF WRITE METHODS

    ///
    /// Write a list of integers to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of integer values to write
    /// @return The number of bytes written
    ///
    public static int writeIntegerList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Integer> list) {
        assert switch (field.type()) {
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> true;
                    default -> false;
                }
                : "Not an integer type " + field;
        assert field.repeated() : "Use writeInteger with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }

        int bytesWritten = 0;
        final int listSize = list.size();
        switch (field.type()) {
            case INT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += ProtoWriterTools.sizeOfVarInt32(val);
                }
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    bytesWritten += out.putVarInt(offset + bytesWritten, val, false);
                }
            }
            case UINT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += ProtoWriterTools.sizeOfUnsignedVarInt64(Integer.toUnsignedLong(val));
                }
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    bytesWritten += out.putVarLong(offset + bytesWritten, Integer.toUnsignedLong(val), false);
                }
            }
            case SINT32 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    size += ProtoWriterTools.sizeOfUnsignedVarInt64(((long) val << 1) ^ ((long) val >> 63));
                }
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    bytesWritten += out.putVarInt(offset + bytesWritten, val, true);
                }
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarLong(offset + bytesWritten, (long) list.size() * ProtoWriterTools.FIXED32_SIZE, false);
                for (int i = 0; i < listSize; i++) {
                    final int val = list.get(i);
                    bytesWritten += out.putInt(offset + bytesWritten, val, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default -> throw new RuntimeException("Unsupported field type: " + field);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of longs to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of long values to write
    /// @return The number of bytes written
    ///
    public static int writeLongList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Long> list) {
        assert switch (field.type()) {
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> true;
                    default -> false;
                }
                : "Not a long type " + field;
        assert field.repeated() : "Use writeLong with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }

        int bytesWritten = 0;
        final int listSize = list.size();
        switch (field.type()) {
            case INT64, UINT64 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += ProtoWriterTools.sizeOfUnsignedVarInt64(val);
                }
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    bytesWritten += out.putVarLong(offset + bytesWritten, val, false);
                }
            }
            case SINT64 -> {
                int size = 0;
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    size += ProtoWriterTools.sizeOfUnsignedVarInt64((val << 1) ^ (val >> 63));
                }
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    bytesWritten += out.putVarLong(offset + bytesWritten, val, true);
                }
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
                bytesWritten += out.putVarLong(offset + bytesWritten, (long) list.size() * ProtoWriterTools.FIXED64_SIZE, false);
                for (int i = 0; i < listSize; i++) {
                    final long val = list.get(i);
                    bytesWritten += out.putLong(offset + bytesWritten, val, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default -> throw new RuntimeException("Unsupported field type: " + field);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of floats to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of float values to write
    /// @return The number of bytes written
    ///
    public static int writeFloatList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Float> list) {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert field.repeated() : "Use writeFloat with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int bytesWritten = 0;
        final int size = list.size() * ProtoWriterTools.FIXED32_SIZE;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            bytesWritten += out.putFloat(offset + bytesWritten, list.get(i), ByteOrder.LITTLE_ENDIAN);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of doubles to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of double values to write
    /// @return The number of bytes written
    ///
    public static int writeDoubleList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Double> list) {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert field.repeated() : "Use writeDouble with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int bytesWritten = 0;
        final int size = list.size() * ProtoWriterTools.FIXED64_SIZE;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            bytesWritten += out.putDouble(offset + bytesWritten, list.get(i), ByteOrder.LITTLE_ENDIAN);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of booleans to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of boolean values to write
    /// @return The number of bytes written
    ///
    public static int writeBooleanList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Boolean> list) {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use writeBoolean with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarInt(offset + bytesWritten, list.size(), false);
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final boolean b = list.get(i);
            bytesWritten += out.putVarInt(offset + bytesWritten, b ? 1 : 0, false);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of enums to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of enum values to write
    /// @return The number of bytes written
    ///
    public static int writeEnumListProtoOrdinals(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<Integer> list) {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use writeEnum with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        final int listSize = list.size();
        int size = 0;
        for (int i = 0; i < listSize; i++) {
            size += ProtoWriterTools.sizeOfUnsignedVarInt32(list.get(i));
        }
        int bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
        bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
        for (int i = 0; i < listSize; i++) {
            bytesWritten += out.putVarInt(offset + bytesWritten, list.get(i), false);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of strings to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of string values to write
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    ///
    public static long writeStringList(
            final RandomAccessData out, final long offset, final FieldDefinition field, final List<String> list)
            throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use writeString with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        long bytesWritten = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            final String value = list.get(i);
            bytesWritten += writeTag(out, offset + bytesWritten, field, WIRE_TYPE_DELIMITED);
            bytesWritten += out.putVarInt(offset + bytesWritten, ProtoWriterTools.sizeOfStringNoTag(value), false);
            bytesWritten += Utf8Tools.encodeUtf8(value, out, offset + bytesWritten);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of messages to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of messages to write
    /// @param codec The codec for the message type
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    /// @param <T> Type of message
    ///
    public static <T> long writeMessageList(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final List<T> list,
            final Codec<T> codec)
            throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use writeMessage with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        long bytesWritten = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeMessageNoChecks(out, offset + bytesWritten, field, list.get(i), codec);
        }
        return bytesWritten;
    }

    ///
    /// Write a list of bytes objects to random access output at a specified offset.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param list The list of bytes objects to write
    /// @return The number of bytes written
    /// @throws IOException If a I/O error occurs
    ///
    public static long writeBytesList(
            final RandomAccessData out,
            final long offset,
            final FieldDefinition field,
            final List<? extends RandomAccessData> list)
            throws IOException {
        assert field.type() == FieldType.BYTES : "Not a message type " + field;
        assert field.repeated() : "Use writeBytes with non-repeated types";
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return 0;
        }
        long bytesWritten = 0;
        final int listSize = list.size();
        for (int i = 0; i < listSize; i++) {
            bytesWritten += writeBytesNoChecks(out, offset + bytesWritten, field, list.get(i), false);
        }
        return bytesWritten;
    }

    ///
    /// Write a generic delimited field to random access output at a specified offset by
    /// delegating to a supplied \`writer\` to write the actual elements.
    ///
    /// @param out The output to write to
    /// @param offset The offset to write at
    /// @param field The descriptor for the field we are writing
    /// @param size The size of all the elements together, in bytes
    /// @param writer The Consumer that accepts the \`out\` and writes the actual elements
    /// @param <T> The type of the data output that extends RandomAccessData
    ///
    public static <T extends RandomAccessData> long writeDelimited(
            final T out,
            final long offset,
            final FieldDefinition field,
            final int size,
            final BiFunction<T, Long, Long> writer) {
        long bytesWritten = 0;
        bytesWritten += writeTag(out, offset + bytesWritten, field);
        bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
        bytesWritten += writer.apply(out, offset + bytesWritten);
        return bytesWritten;
    }


}
