package com.hedera.hashgraph.pbj.runtime;

import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
import com.hedera.hashgraph.pbj.runtime.io.DataOutput;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.hedera.hashgraph.pbj.runtime.ProtoConstants.*;

/**
 * Static helper methods for Writers
 */
@SuppressWarnings({"DuplicatedCode", "OptionalUsedAsFieldOrParameterType"})
public final class ProtoWriterTools {

    private static final int TAG_TYPE_BITS = 3;

    /** Instance should never be created */
    private ProtoWriterTools() {}

    // ================================================================================================================
    // BUFFER CACHE

    private static ConcurrentLinkedDeque<DataBuffer> BUFFER_CACHE = new ConcurrentLinkedDeque<>();

    private static DataBuffer checkoutBuffer() {
        try {
            DataBuffer buffer =  BUFFER_CACHE.removeFirst();
            buffer.reset();
            return buffer;
        } catch (NoSuchElementException e) {
            return DataBuffer.allocate(12*1024*1024,false);
        }
    }
    
    private static void returnBuffer(DataBuffer buffer) {
        BUFFER_CACHE.add(buffer);
    }
    

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
    public static void writeTag(final DataOutput out, final FieldDefinition field, final int wireType) throws IOException {
        out.writeVarInt((field.number() << TAG_TYPE_BITS) | wireType, false);
    }

    // ================================================================================================================
    // OPTIONAL VERSIONS OF WRITE METHODS
    public static void writeOptionalInteger(DataOutput out, FieldDefinition field, Optional<Integer> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeInteger(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }
    public static void writeOptionalLong(DataOutput out, FieldDefinition field, Optional<Long> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeLong(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }
    public static void writeOptionalFloat(DataOutput out, FieldDefinition field, Optional<Float> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeFloat(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }
    public static void writeOptionalDouble(DataOutput out, FieldDefinition field, Optional<Double> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeDouble(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }
    public static void writeOptionalBoolean(DataOutput out, FieldDefinition field, Optional<Boolean> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeBoolean(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }
    public static void writeOptionalString(DataOutput out, FieldDefinition field, Optional<String> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeString(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }

    public static void writeOptionalBytes(DataOutput out, FieldDefinition field, Optional<Bytes> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var buffer = checkoutBuffer();
            writeBytes(buffer,
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            buffer.flip();
            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }

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
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

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
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for long. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

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

    public static void writeBoolean(DataOutput out, FieldDefinition field, boolean value) throws IOException {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use writeBooleanList with repeated types";

        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf()) {
            writeTag(out, field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            out.writeByte(value ? (byte)1 : 0);
        }
    }

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

    public static void writeString(DataOutput out, FieldDefinition field, String value) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use writeStringList with repeated types";
        writeStringNoTag(out, field, value);
    }

    private static void writeStringNoTag(DataOutput out, FieldDefinition field, String value) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value == null || value.isBlank())) {
            return;
        }

        writeTag(out, field, WIRE_TYPE_DELIMITED);
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeVarInt(bytes.length, false);
        out.writeBytes(bytes);
    }

    public static void writeBytes(DataOutput out, FieldDefinition field, Bytes value) throws IOException {
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use writeBytesList with repeated types";
        writeBytes(out, field, value, true);
    }

    /**
     * @param skipZeroLength this is true for normal single bytes and false for repeated lists
     */
    private static void writeBytes(DataOutput out, FieldDefinition field, Bytes value, boolean skipZeroLength) throws IOException {
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

    public static <T> void writeMessage(DataOutput out, FieldDefinition field, T message, ProtoWriter<T> writer) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use writeMessageList with repeated types";
        writeMessageNoTag(out, field, message, writer);
    }

    private static <T> void writeMessageNoTag(DataOutput out, FieldDefinition field, T message, ProtoWriter<T> writer) throws IOException {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            out.writeVarInt(0, false);
        } else if (message != null) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            // TODO need size estimation for performance
            final var buffer = checkoutBuffer();
            writer.write(message, buffer);
            buffer.flip();

            out.writeVarInt((int)buffer.getRemaining(), false);
            if (buffer.getRemaining() > 0) {
                out.writeBytes(buffer);
            }
            returnBuffer(buffer);
        }
    }

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

        final var buffer = checkoutBuffer();
        switch (field.type()) {
            case INT32 -> {
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    buffer.writeVarInt(i, false);
                }
                buffer.flip();

                out.writeVarInt((int)buffer.getRemaining(), false);
                out.writeBytes(buffer);
            }
            case UINT32 -> {
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    buffer.writeVarLong(Integer.toUnsignedLong(i), false);
                }
                buffer.flip();

                out.writeVarInt((int)buffer.getRemaining(), false);
                out.writeBytes(buffer);
            }
            case SINT32 -> {
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    buffer.writeVarInt(i, true);
                }
                buffer.flip();

                out.writeVarInt((int)buffer.getRemaining(), false);
                out.writeBytes(buffer);
            }
            case SFIXED32, FIXED32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong(list.size() * 4L, false);
                for (final int i : list) {
                    out.writeInt(i, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
        returnBuffer(buffer);
    }

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

        final var buffer = checkoutBuffer();
        switch (field.type()) {
            case INT64, UINT64 -> {
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                for (final long i : list) {
                    buffer.writeVarLong(i, false);
                }
                buffer.flip();

                out.writeVarInt((int)buffer.getRemaining(), false);
                out.writeBytes(buffer);
            }
            case SINT64 -> {
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                for (final long i : list) {
                    buffer.writeVarLong(i, true);
                }
                buffer.flip();

                out.writeVarInt((int)buffer.getRemaining(), false);
                out.writeBytes(buffer);
            }
            case SFIXED64, FIXED64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(out, field, WIRE_TYPE_DELIMITED);
                out.writeVarLong(list.size() * 8L, false);
                for (final long i : list) {
                    out.writeLong(i, ByteOrder.LITTLE_ENDIAN);
                }
            }
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
        returnBuffer(buffer);
    }
    public static void writeFloatList(DataOutput out, FieldDefinition field, List<Float> list) throws IOException {
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert field.repeated() : "Use writeFloat with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        writeTag(out, field, WIRE_TYPE_DELIMITED);

        final var buffer = checkoutBuffer();
        for (final Float i : list) {
            buffer.writeFloat(i, ByteOrder.LITTLE_ENDIAN);
        }
        buffer.flip();

        out.writeVarInt((int)buffer.getRemaining(), false);
        out.writeBytes(buffer);
        returnBuffer(buffer);
    }

    public static void writeDoubleList(DataOutput out, FieldDefinition field, List<Double> list) throws IOException {
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert field.repeated() : "Use writeDouble with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        writeTag(out, field, WIRE_TYPE_DELIMITED);

        final var buffer = checkoutBuffer();
        for (final Double i : list) {
            buffer.writeDouble(i, ByteOrder.LITTLE_ENDIAN);
        }
        buffer.flip();

        out.writeVarInt((int)buffer.getRemaining(), false);
        out.writeBytes(buffer);
        returnBuffer(buffer);
    }

    public static void writeBooleanList(DataOutput out, FieldDefinition field, List<Boolean> list) throws IOException {
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use writeBoolean with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        writeTag(out, field, WIRE_TYPE_DELIMITED);

        final var buffer = checkoutBuffer();
        for (final boolean b : list) {
            buffer.writeVarInt(b ? 1 : 0, false);
        }
        buffer.flip();

        out.writeVarInt((int)buffer.getRemaining(), false);
        out.writeBytes(buffer);
        returnBuffer(buffer);
    }

    public static void writeEnumList(DataOutput out, FieldDefinition field, List<? extends EnumWithProtoOrdinal> list) throws IOException {
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use writeEnum with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        writeTag(out, field, WIRE_TYPE_DELIMITED);

        final var buffer = checkoutBuffer();
        for (final EnumWithProtoOrdinal enumValue : list) {
            buffer.writeVarInt(enumValue.protoOrdinal(), false);
        }
        buffer.flip();

        out.writeVarInt((int)buffer.getRemaining(), false);
        out.writeBytes(buffer);
        returnBuffer(buffer);
    }

    public static void writeStringList(DataOutput out, FieldDefinition field, List<String> list) throws IOException {
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use writeString with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final String value : list) {
            writeTag(out, field, WIRE_TYPE_DELIMITED);
            final var bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeVarInt(bytes.length, false);
            out.writeBytes(bytes);
        }
    }

    public static <T> void writeMessageList(DataOutput out, FieldDefinition field, List<T> list, ProtoWriter<T> writer) throws IOException {
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use writeMessage with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final T value : list) {
            writeMessageNoTag(out, field, value, writer);
        }
    }

    public static void writeBytesList(DataOutput out, FieldDefinition field, List<Bytes> list) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final Bytes value : list) {
            // TODO be nice to avoid copy here but not sure how, could reuse byte[] if _writeBytes supported length to read from byte[]
            writeBytes(out, field, value, false);
        }
    }
}
