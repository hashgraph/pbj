package com.hedera.hashgraph.pbj.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.hedera.hashgraph.pbj.runtime.ProtoConstants.*;

/**
 * 
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ProtoOutputStream {
    private static final String FIELD_ASSERT_MSG = "Field %s doesn't belong to the expected schema";

    private final OutputStream out;
    private final Predicate<FieldDefinition> fieldChecker;

    public ProtoOutputStream(Predicate<FieldDefinition> fieldChecker, OutputStream out) {
        this.out = Objects.requireNonNull(out);
        this.fieldChecker = Objects.requireNonNull(fieldChecker);
    }

    // === OPTIONAL VERSIONS OF WRITE METHODS
    public void writeOptionalInteger(FieldDefinition field, Optional<Integer> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeInteger(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalLong(FieldDefinition field, Optional<Long> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeLong(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalFloat(FieldDefinition field, Optional<Float> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeFloat(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalDouble(FieldDefinition field, Optional<Double> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeDouble(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalBoolean(FieldDefinition field, Optional<Boolean> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeBoolean(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalEnum(FieldDefinition field, Optional<? extends EnumWithProtoOrdinal> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeEnum(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalString(FieldDefinition field, Optional<String> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeString(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public void writeOptionalBytes(FieldDefinition field, Optional<ByteBuffer> value) throws IOException {
        if (value != null && value.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeBytes(
                    new FieldDefinition("value",field.type(),false,1),
                    value.get());
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }
    public <T> void writeOptionalMessage(FieldDefinition field, Optional<T> message, ProtoWriter<T> writer) throws IOException {
        if (message != null && message.isPresent()) {
            // TODO this will work for now could be more optimal
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            final ProtoOutputStream messageProtoOutputStream = new ProtoOutputStream(fd -> true,baos);
            messageProtoOutputStream.writeMessage(
                    new FieldDefinition("value",field.type(),false,1),
                    message.get(), writer);
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
            writeMessage(field,message.get(),writer);
        }
    }

    public void writeInteger(FieldDefinition field, int value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert switch(field.type()) {
            case INT_32, UINT_32, SINT_32, FIXED_32, SFIXED_32 -> true;
            default -> false;
        } : "Not an integer type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeIntegerList with repeated types";

        if (!field.oneOf() && value == 0) {
            return;
        }

        switch (field.type()) {
            case INT_32 -> {
                writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                writeVarint(value, false);
            }
            case UINT_32 -> {
                writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                writeVarint(Integer.toUnsignedLong(value), false);
            }
            case SINT_32 -> {
                writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                writeVarint(value, true);
            }
            case SFIXED_32, FIXED_32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(field, WIRE_TYPE_FIXED_32_BIT);
                writeIntToStream(value);
            }
            default ->
                throw new RuntimeException(
                        "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

    public void writeLong(FieldDefinition field, long value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert switch(field.type()) {
            case INT_64, UINT_64, SINT_64, FIXED_64, SFIXED_64 -> true;
            default -> false;
        } : "Not a long type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeLongList with repeated types";

        if (!field.oneOf() && value == 0) {
            return;
        }

        switch (field.type()) {
            case INT_64, UINT_64 -> {
                writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                writeVarint(value, false);
            }
            case SINT_64 -> {
                writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
                writeVarint(value, true);
            }
            case SFIXED_64, FIXED_64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(field, WIRE_TYPE_FIXED_64_BIT);
                writeLongToStream(value);
            }
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for long. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

    private void writeIntToStream(int value) throws IOException {
        out.write(value & 0x000000FF);
        out.write((value & 0x0000FF00) >> 8);
        out.write((value & 0x00FF0000) >> 16);
        out.write((value & 0xFF000000) >> 24);
    }

    private void writeLongToStream(long value) throws IOException {
        out.write((int) (value & 0x00000000000000FF));
        out.write((int) ((value & 0x000000000000FF00) >> 8));
        out.write((int) ((value & 0x0000000000FF0000) >> 16));
        out.write((int) ((value & 0x00000000FF000000) >> 24));
        out.write((int) ((value & 0x000000FF00000000L) >> 32));
        out.write((int) ((value & 0x0000FF0000000000L) >> 40));
        out.write((int) ((value & 0x00FF000000000000L) >> 48));
        out.write((int) ((value & 0xFF00000000000000L) >> 56));
    }

    public void writeFloat(FieldDefinition field, float value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.FLOAT : "Not a float type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeFloatList with repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return;
        }

        writeTag(field, WIRE_TYPE_FIXED_32_BIT);
        writeIntToStream(Float.floatToRawIntBits(value));
    }

    public void writeDouble(FieldDefinition field, double value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.DOUBLE : "Not a double type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeDoubleList with repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && value == 0) {
            return;
        }

        writeTag(field, WIRE_TYPE_FIXED_64_BIT);
        writeLongToStream(Double.doubleToRawLongBits(value));
    }

    public void writeBoolean(FieldDefinition field, boolean value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeBooleanList with repeated types";

        // In the case of oneOf we write the value even if it is default value of false
        if (value || field.oneOf()) {
            writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
            out.write(value ? 1 : 0);
        }
    }

    public void writeEnum(FieldDefinition field, EnumWithProtoOrdinal enumValue) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeEnumList with repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && (enumValue == null || enumValue.protoOrdinal() == 0)) {
            return;
        }

        writeTag(field, WIRE_TYPE_VARINT_OR_ZIGZAG);
        writeVarint(enumValue.protoOrdinal(), false);
    }

    public void writeString(FieldDefinition field, String value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeStringList with repeated types";
        _writeString(field, value);
    }

    private void _writeString(FieldDefinition field, String value) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (value == null || value.isBlank())) {
            return;
        }

        writeTag(field, WIRE_TYPE_DELIMITED);
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarint(bytes.length, false);
        out.write(bytes);
    }

    public void writeBytes(FieldDefinition field, ByteBuffer value) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.BYTES : "Not a byte[] type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeBytesList with repeated types";
        final byte[] bytes = new byte[value.capacity()];
        value.get(0, bytes); // TODO be nice to avoid copy here but not sure how, could reuse byte[]
        _writeBytes(field, bytes, true);
    }

    /**
     * @param skipZeroLength this is true for normal single bytes and false for repeated lists
     */
    public void _writeBytes(FieldDefinition field, byte[] value, boolean skipZeroLength) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && (skipZeroLength && value.length == 0)) {
            return;
        }
        writeTag(field, WIRE_TYPE_DELIMITED);
        writeVarint(value.length, false);
        out.write(value);
    }

    public <T> void writeMessage(FieldDefinition field, T message, ProtoWriter<T> writer) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert !field.repeated() : "Use ProtoOutputStream#writeMessageList with repeated types";
        _writeMessage(field, message, writer);
    }

    public <T> void _writeMessage(FieldDefinition field, T message, ProtoWriter<T> writer) throws IOException {
        // When not a oneOf don't write default value
        if (field.oneOf() && message == null) {
            writeTag(field, WIRE_TYPE_DELIMITED);
            writeVarint(0, false);
        } else if (message != null) {
            writeTag(field, WIRE_TYPE_DELIMITED);
            final var baos = new ByteArrayOutputStream();
            writer.write(message, baos);
            writeVarint(baos.size(), false);
            if (baos.size() > 0) {
                out.write(baos.toByteArray());
            }
        }
    }

    public void writeIntegerList(FieldDefinition field, List<Integer> list) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert switch(field.type()) {
            case INT_32, UINT_32, SINT_32, FIXED_32, SFIXED_32 -> true;
            default -> false;
        } : "Not an integer type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeInteger with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final var buffer = new ByteArrayOutputStream();
        switch (field.type()) {
            case INT_32 -> {
                writeTag(field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    writeVarint(i, false, buffer);
                }
                writeVarint(buffer.size(), false);
                out.write(buffer.toByteArray());
            }
            case UINT_32 -> {
                writeTag(field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    writeVarint(Integer.toUnsignedLong(i), false, buffer);
                }
                writeVarint(buffer.size(), false);
                out.write(buffer.toByteArray());
            }
            case SINT_32 -> {
                writeTag(field, WIRE_TYPE_DELIMITED);
                for (final int i : list) {
                    writeVarint(i, true, buffer);
                }
                writeVarint(buffer.size(), false);
                out.write(buffer.toByteArray());
            }
            case SFIXED_32, FIXED_32 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(field, WIRE_TYPE_DELIMITED);
                writeVarint(list.size() * 4L, false, out);
                for (final int i : list) {
                    writeIntToStream(i);
                }
            }
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

    public void writeLongList(FieldDefinition field, List<Long> list) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert switch(field.type()) {
            case INT_64, UINT_64, SINT_64, FIXED_64, SFIXED_64 -> true;
            default -> false;
        } : "Not a long type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeLong with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final var buffer = new ByteArrayOutputStream();
        switch (field.type()) {
            case INT_64, UINT_64 -> {
                writeTag(field, WIRE_TYPE_DELIMITED);
                for (final long i : list) {
                    writeVarint(i, false, buffer);
                }
                writeVarint(buffer.size(), false);
                out.write(buffer.toByteArray());
            }
            case SINT_64 -> {
                writeTag(field, WIRE_TYPE_DELIMITED);
                for (final long i : list) {
                    writeVarint(i, true, buffer);
                }
                writeVarint(buffer.size(), false);
                out.write(buffer.toByteArray());
            }
            case SFIXED_64, FIXED_64 -> {
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTag(field, WIRE_TYPE_DELIMITED);
                writeVarint(list.size() * 8L, false, out);
                for (final long i : list) {
                    writeLongToStream(i);
                }
            }
            default ->
                    throw new RuntimeException(
                            "Unsupported field type for integer. Bug in ProtoOutputStream, shouldn't happen.");
        }
    }

    public void writeBooleanList(FieldDefinition field, List<Boolean> list) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.BOOL : "Not a boolean type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeBoolean with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final var buffer = new ByteArrayOutputStream();
        writeTag(field, WIRE_TYPE_DELIMITED);
        for (final boolean b : list) {
            writeVarint(b ? 1 : 0, false, buffer);
        }
        writeVarint(buffer.size(), false);
        out.write(buffer.toByteArray());
    }

    public void writeEnumList(FieldDefinition field, List<? extends EnumWithProtoOrdinal> list) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.ENUM : "Not an enum type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeEnum with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        final var buffer = new ByteArrayOutputStream();
        writeTag(field, WIRE_TYPE_DELIMITED);
        for (final EnumWithProtoOrdinal enumValue : list) {
            writeVarint(enumValue.protoOrdinal(), false, buffer);
        }
        writeVarint(buffer.size(), false);
        out.write(buffer.toByteArray());
    }

    public void writeStringList(FieldDefinition field, List<String> list) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.STRING : "Not a string type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeString with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final String value : list) {
            _writeString(field, value);
        }
    }

    public <T> void writeMessageList(FieldDefinition field, List<T> list, ProtoWriter<T> writer) throws IOException {
        assert fieldChecker.test(field) : FIELD_ASSERT_MSG.formatted(field);
        assert field.type() == FieldType.MESSAGE : "Not a message type " + field;
        assert field.repeated() : "Use ProtoOutputStream#writeMessage with non-repeated types";

        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final T value : list) {
            _writeMessage(field, value, writer);
        }
    }

    public void writeBytesList(FieldDefinition field, List<ByteBuffer> list) throws IOException {
        // When not a oneOf don't write default value
        if (!field.oneOf() && list.isEmpty()) {
            return;
        }

        for (final ByteBuffer value : list) {
            // TODO be nice to avoid copy here but not sure how, could reuse byte[] if _writeBytes supported length to read from byte[]
            final byte[] bytes = new byte[value.capacity()];
            value.get(0, bytes);
            _writeBytes(field, bytes, false);
        }
    }

    private void writeTag(FieldDefinition field, int wireType) throws IOException {
        final int tag = (field.number() << 3) | wireType;
        writeVarint(tag, false);
    }

    private void writeVarint(long value, boolean zigZag) throws IOException {
        writeVarint(value, zigZag, out);
    }

    private void writeVarint(long value, boolean zigZag, OutputStream stream) throws IOException {
        if (zigZag) {
            value = (value << 1) ^ (value >> 63);
        }

        // Small performance optimization for small values.
        if (value < 128 && value >= 0) {
            stream.write((int) value);
            return;
        }

        // We will send 7 bits of data with each byte we write. The high-order bit of
        // each byte indicates whether there are subsequent bytes coming. So we need
        // to know how many bytes we need to send. We do this by figuring out the position
        // of the highest set bit (counting from the left. So the first bit on the far
        // right is at position 1, the bit at the far left is at position 64).
        // Then, we calculate how many bytes it will take if we are sending 7 bits at
        // a time, being careful to round up when not aligned at a 7-bit boundary.
        int numLeadingZeros = Long.numberOfLeadingZeros(value);
        int bitPos = 64 - numLeadingZeros;
        int numBytesToSend = bitPos / 7 + (bitPos % 7 == 0 ? 0 : 1);

        // For all bytes except the last one, we need to mask off the last
        // 7 bits of the value and combine that with a byte with the leading
        // bit set. Then we shift the value 7 bits to the right.
        for (int i = 0; i < numBytesToSend - 1; i++) {
            stream.write((int) (0x80 | (0x7F & value)));
            value >>>= 7;
        }

        // And now we can send whatever is left as the last byte, knowing that
        // the high order bit will never be set.
        stream.write((int) value);
    }
}
