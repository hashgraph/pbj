// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.FieldType.BOOL;
import static com.hedera.pbj.runtime.FieldType.BYTES;
import static com.hedera.pbj.runtime.FieldType.FIXED32;
import static com.hedera.pbj.runtime.FieldType.FIXED64;
import static com.hedera.pbj.runtime.FieldType.INT32;
import static com.hedera.pbj.runtime.FieldType.MESSAGE;
import static com.hedera.pbj.runtime.FieldType.STRING;
import static com.hedera.pbj.runtime.ProtoConstants.TAG_WIRE_TYPE_MASK;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_32_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_64_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_GROUP_END;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_GROUP_START;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG;
import static com.hedera.pbj.runtime.ProtoParserTools.readNextFieldNumber;
import static com.hedera.pbj.runtime.ProtoParserTools.readString;
import static com.hedera.pbj.runtime.ProtoParserTools.skipField;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeInteger;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeLong;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeString;
import static com.hedera.pbj.runtime.ProtoWriterToolsTest.createFieldDefinition;
import static com.hedera.pbj.runtime.ProtoWriterToolsTest.randomVarSizeString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.runtime.test.UncheckedThrowingFunction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import test.proto.Apple;

class ProtoParserToolsTest {

    private final RandomGenerator rng = RandomGenerator.getDefault();

    @Test
    void testReadInt32() {
        testRead(
                rng::nextInt,
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readInt32,
                // in this case the size may up to 10 bytes in case of negative numbers,
                // because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadInt64() {
        testRead(
                rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readInt64,
                // in this case the size may be 10 bytes, because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadUint32() {
        testRead(
                () -> rng.nextInt(0, Integer.MAX_VALUE),
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readUint32,
                // the size may vary from 1 to 5 bytes
                Integer.BYTES + 1);
    }

    @Test
    void testReadUint64() {
        testRead(
                rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readUint64,
                // the size may vary from 1 to 10 bytes
                Long.BYTES + 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void testReadBool(final int value) {
        testRead(
                () -> value != 0,
                (d, v) -> d.writeVarInt(value, false),
                input -> {
                    try {
                        return ProtoParserTools.readBool(input);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                1);
    }

    @Test
    void testBadBool() {
        final int value = 3;
        testRead(
                () -> value != 0,
                (d, v) -> d.writeVarInt(value, false),
                input -> {
                    assertThrows(IOException.class, () -> ProtoParserTools.readBool(input));
                    return true;
                },
                1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void testReadEnum(int value) {
        testRead(() -> value, (d, v) -> d.writeVarInt(value, false), ProtoParserTools::readEnum, 1);
    }

    @Test
    void testReadSignedInt32() {
        testRead(rng::nextInt, (d, v) -> d.writeVarInt(v, true), ProtoParserTools::readSignedInt32, Integer.BYTES + 1);
    }

    @Test
    void testReadSignedInt64() {
        testRead(rng::nextLong, (d, v) -> d.writeVarLong(v, true), ProtoParserTools::readSignedInt64, Long.BYTES + 2);
    }

    @Test
    void testReadSignedFixedInt32() {
        testRead(
                rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadFixedInt32() {
        testRead(
                rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadSginedFixed64() {
        testRead(
                rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFixed64() {
        testRead(
                rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFloat() {
        testRead(
                rng::nextFloat,
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFloat,
                Long.BYTES);
    }

    @Test
    void testReadDouble() {
        testRead(
                rng::nextDouble,
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readDouble,
                Long.BYTES);
    }

    @Test
    void testReadString() {
        final int length = rng.nextInt(0, 100);
        final RandomString randomString = new RandomString(length);

        testRead(
                randomString::nextString,
                (d, v) -> {
                    d.writeVarInt(length, false); // write the size first
                    d.writeUTF8(v);
                },
                new UncheckedThrowingFunction<>(ProtoParserTools::readString),
                length + 1);
    }

    @Test
    void testReadString_maxSize() throws IOException {
        final int length = 1;
        final int maxSize = 1024;
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final BufferedData data = BufferedData.allocate(length + 16);
        data.writeVarInt(maxSize + 1, false); // write the size first
        data.writeBytes(byteArray);
        final ReadableStreamingData streamingData = new ReadableStreamingData(data.toInputStream());
        assertThrows(ParseException.class, () -> ProtoParserTools.readString(streamingData, maxSize));
    }

    @Test
    void testReadString_incomplete() throws IOException {
        final int length = rng.nextInt(1, 100);
        final RandomString randomString = new RandomString(length);
        final BufferedData data = BufferedData.allocate(length + 1);
        data.writeVarInt(length, false); // write the size first
        final String expectedValue = randomString.nextString();
        data.writeUTF8(expectedValue);
        final byte[] bytes = data.toInputStream().readAllBytes();
        final byte[] incompleteCopy = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, incompleteCopy, 0, bytes.length - 1);
        final ReadableStreamingData streamingData = new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy));
        assertThrows(BufferUnderflowException.class, () -> ProtoParserTools.readString(streamingData));
    }

    @Test
    void testReadBytes() {
        final int length = rng.nextInt(0, 100);
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final Bytes bytes = Bytes.wrap(byteArray);

        testRead(
                () -> bytes,
                (d, v) -> {
                    d.writeVarInt(length, false); // write the size first
                    d.writeBytes(v);
                },
                ProtoParserTools::readBytes,
                length + 1);
    }

    @Test
    void testReadBytes_maxSize() throws IOException {
        final int length = 1;
        final int maxSize = 1024;
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final BufferedData data = BufferedData.allocate(length + 16);
        data.writeVarInt(maxSize + 1, false); // write the size first
        data.writeBytes(byteArray);
        final ReadableStreamingData streamingData = new ReadableStreamingData(data.toInputStream());
        assertThrows(ParseException.class, () -> ProtoParserTools.readBytes(streamingData, maxSize));
    }

    @Test
    void testReadBytes_incomplete() throws IOException {
        final int length = rng.nextInt(0, 100);
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final BufferedData data = BufferedData.allocate(length + 1);
        data.writeVarInt(length, false); // write the size first
        data.writeBytes(byteArray);
        final byte[] bytes = data.toInputStream().readAllBytes();
        final byte[] incompleteCopy = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, incompleteCopy, 0, bytes.length - 1);
        assertThrows(
                BufferUnderflowException.class,
                () -> ProtoParserTools.readString(new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy))));
        assertThrows(
                BufferUnderflowException.class,
                () -> ProtoParserTools.readBytes(new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy))));
    }

    @Test
    void testReadNextFieldNumber() throws IOException {
        BufferedData bufferedData = BufferedData.allocate(100);
        final FieldDefinition definition = createFieldDefinition(MESSAGE);
        final String appleStr = randomVarSizeString();
        final Apple apple = Apple.newBuilder().setVariety(appleStr).build();

        writeMessage(
                bufferedData,
                definition,
                apple,
                new CodecWrapper<>((data, out) -> out.writeBytes(data.toByteArray()), Apple::getSerializedSize));
        bufferedData.flip();

        assertEquals(definition.number(), readNextFieldNumber(bufferedData));
    }

    @Test
    void testSkipField() throws IOException {
        final String valToRead = randomVarSizeString();
        final BufferedData data = BufferedData.allocate(1000);
        writeLong(data, createFieldDefinition(FIXED64), rng.nextLong());
        writeInteger(data, createFieldDefinition(FIXED32), rng.nextInt());
        int value = rng.nextInt(0, Integer.MAX_VALUE);
        writeInteger(data, createFieldDefinition(INT32), value);
        writeString(data, createFieldDefinition(STRING), randomVarSizeString());
        writeString(data, createFieldDefinition(STRING), valToRead);

        data.flip();

        skipTag(data);
        skipField(data, WIRE_TYPE_FIXED_64_BIT);
        skipTag(data);
        skipField(data, WIRE_TYPE_FIXED_32_BIT);
        skipTag(data);
        skipField(data, WIRE_TYPE_VARINT_OR_ZIGZAG);
        skipTag(data);
        skipField(data, WIRE_TYPE_DELIMITED);
        skipTag(data);
        assertEquals(valToRead, readString(data));
    }

    @Test
    void testSkipField_maxSize() throws IOException {
        final int length = 1;
        final int maxSize = 1024;
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final BufferedData data = BufferedData.allocate(length + 16);
        data.writeVarInt(maxSize + 1, false); // write the size first
        data.writeBytes(byteArray);
        final ReadableStreamingData streamingData = new ReadableStreamingData(data.toInputStream());
        assertThrows(
                ParseException.class, () -> ProtoParserTools.skipField(streamingData, WIRE_TYPE_DELIMITED, maxSize));
    }

    @ParameterizedTest
    @EnumSource(names = {"WIRE_TYPE_GROUP_START", "WIRE_TYPE_GROUP_END"})
    void testSkipUnsupported(ProtoConstants unsupportedType) {
        final BufferedData data = BufferedData.allocate(100);
        assertThrows(IOException.class, () -> skipField(data, unsupportedType));
    }

    @Test
    void testExtractBytesNullInput() {
        final FieldDefinition field = createFieldDefinition(BYTES);
        assertThrows(NullPointerException.class, () -> ProtoParserTools.extractFieldBytes(null, field));
    }

    @Test
    void testExtractBytesNullField() {
        final ReadableSequentialData input = Bytes.EMPTY.toReadableSequentialData();
        assertThrows(NullPointerException.class, () -> ProtoParserTools.extractFieldBytes(input, null));
    }

    @Test
    void testExtractBytesRepeatedField() {
        final ReadableSequentialData input = Bytes.EMPTY.toReadableSequentialData();
        final FieldDefinition field = new FieldDefinition("field", FieldType.BYTES, true, true, false, 1);
        assertThrows(IllegalArgumentException.class, () -> ProtoParserTools.extractFieldBytes(input, field));
    }

    private static final FieldDefinition INT32_F =
            new FieldDefinition("int32field", FieldType.INT32, false, true, false, 1);
    private static final int INT32_V = 101;

    private static final FieldDefinition FIXED_F =
            new FieldDefinition("fixed32field", FieldType.FIXED32, false, true, false, 2);
    private static final int FIXED32_V = 102;

    private static final FieldDefinition STRING_F =
            new FieldDefinition("stringfield", FieldType.STRING, false, true, false, 3);
    private static final String STRING_V = "StringValue";

    private static final FieldDefinition BYTES_F =
            new FieldDefinition("bytesfield", FieldType.BYTES, false, true, false, 4);
    private static final Bytes BYTES_V = Bytes.wrap(STRING_V.getBytes(StandardCharsets.UTF_8));

    private static final FieldDefinition MESSAGE_F =
            new FieldDefinition("messagefield", FieldType.MESSAGE, false, true, false, 5);
    private static final TestMessage MESSAGE_V = new TestMessage(STRING_V);

    private static final FieldDefinition DOUBLE_F =
            new FieldDefinition("doublefield", FieldType.DOUBLE, false, true, false, 6);
    private static final double DOUBLE32_V = 103.0;

    private static final FieldDefinition UNKNOWN_F =
            new FieldDefinition("nofield", FieldType.BYTES, false, true, false, 10);

    private static final FieldDefinition BOOL_F =
            new FieldDefinition("boolfield", BOOL, false, true, false, 11);
    private static final boolean BOOL_V = true;


    private static Bytes prepareExtractBytesTestInput() throws IOException {
        try (final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                final WritableStreamingData out = new WritableStreamingData(bout)) {
            ProtoWriterTools.writeInteger(out, INT32_F, INT32_V);
            ProtoWriterTools.writeInteger(out, FIXED_F, FIXED32_V);
            ProtoWriterTools.writeString(out, STRING_F, STRING_V);
            ProtoWriterTools.writeBytes(out, BYTES_F, BYTES_V);
            ProtoWriterTools.writeMessage(out, MESSAGE_F, MESSAGE_V, TestMessageCodec.INSTANCE);
            ProtoWriterTools.writeDouble(out, DOUBLE_F, DOUBLE32_V);
            return Bytes.wrap(bout.toByteArray());
        }
    }

    @Test
    void testExtractBytesStringField() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final Bytes bytes = ProtoParserTools.extractFieldBytes(input, STRING_F);
        assertNotNull(bytes);
        assertEquals(STRING_V, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
    }

    @Test
    void testExtractFieldBytesInvalidType() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        // should throw because INT32 is not a delimited type
        assertThrows(IllegalArgumentException.class, () -> ProtoParserTools.extractFieldBytes(input, INT32_F));
    }

    @Test
    void testExtractBytesBytesField() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final Bytes bytes = ProtoParserTools.extractFieldBytes(input, BYTES_F);
        assertNotNull(bytes);
        assertEquals(BYTES_V, bytes);
    }

    @Test
    void testExtractBytesMessageField() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final Bytes bytes = ProtoParserTools.extractFieldBytes(input, MESSAGE_F);
        assertNotNull(bytes);
        final TestMessage value = TestMessageCodec.INSTANCE.parse(bytes.toReadableSequentialData());
        assertNotNull(value);
        assertEquals(MESSAGE_V, value);
    }

    @Test
    void testExtractBytesUnknownField() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final Bytes bytes = ProtoParserTools.extractFieldBytes(input, UNKNOWN_F);
        assertNull(bytes);
    }

    @Test
    void testExtractField32Bit() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final var res = ProtoParserTools.extractField(input, WIRE_TYPE_FIXED_32_BIT, 32);
        assertNotNull(res);
    }
    @Test
    void testExtractField64Bit() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final var res = ProtoParserTools.extractField(input, WIRE_TYPE_FIXED_64_BIT, 32);
        assertNotNull(res);
    }
    @Test
    void testExtractFieldVarInt() throws IOException, ParseException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        final var res = ProtoParserTools.extractField(input, WIRE_TYPE_VARINT_OR_ZIGZAG, 32);
        assertNotNull(res);
    }
    @Test
    void testExtractFieldGroupStartUnsupported() throws IOException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        assertThrows(IOException.class, () -> ProtoParserTools.extractField(input, WIRE_TYPE_GROUP_START, 32));
    }
    @Test
    void testExtractFieldGroupEndUnsupported() throws IOException {
        final ReadableSequentialData input = prepareExtractBytesTestInput().toReadableSequentialData();
        assertThrows(IOException.class, () -> ProtoParserTools.extractField(input, WIRE_TYPE_GROUP_END, 32));
    }

    private static void skipTag(BufferedData data) {
        data.readVarInt(false);
    }

    private static <T> void testRead(
            final Supplier<? extends T> valueSupplier,
            final BiConsumer<BufferedData, ? super T> valueWriter,
            final Function<? super BufferedData, T> reader,
            final int size) {
        final T value = valueSupplier.get();
        final BufferedData data = BufferedData.allocate(size);
        valueWriter.accept(data, value);
        data.flip();
        assertEquals(value, reader.apply(data));
    }

    private static final class TestMessage {

        private final String value;

        public TestMessage(final String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TestMessage other)) {
                return false;
            }
            return Objects.equals(value, other.value);
        }
    }

    private static final class TestMessageCodec implements Codec<TestMessage> {

        public static final TestMessageCodec INSTANCE = new TestMessageCodec();

        public static final FieldDefinition VALUE_FIELD =
                new FieldDefinition("value", FieldType.STRING, false, true, false, 1);

        @NonNull
        @Override
        public TestMessage parse(
                @NonNull final ReadableSequentialData in,
                final boolean strictMode,
                final boolean parseUnknownFields,
                final int maxDepth)
                throws ParseException {
            String value = null;
            while (in.hasRemaining()) {
                final int tag = in.readVarInt(false);
                final int fieldNum = tag >> ProtoParserTools.TAG_FIELD_OFFSET;
                final int wireType = tag & TAG_WIRE_TYPE_MASK;
                if ((fieldNum == VALUE_FIELD.number())
                        && (wireType == ProtoWriterTools.wireType(VALUE_FIELD).ordinal())) {
                    final int length = in.readVarInt(false);
                    final byte[] valueBytes = new byte[length];
                    if (in.readBytes(valueBytes) != length) {
                        throw new ParseException("Failed to read value bytes");
                    }
                    value = new String(valueBytes, StandardCharsets.UTF_8);
                } else {
                    throw new ParseException("Unknown field: " + tag);
                }
            }
            return new TestMessage(value);
        }

        @Override
        public void write(@NonNull final TestMessage item, @NonNull final WritableSequentialData out)
                throws IOException {
            final String value = item.getValue();
            if (value != null) {
                ProtoWriterTools.writeString(out, VALUE_FIELD, value);
            }
        }

        @Override
        public int measure(@NonNull ReadableSequentialData input) throws ParseException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int measureRecord(@NonNull final TestMessage item) {
            final String value = item.getValue();
            if (value != null) {
                return ProtoWriterTools.sizeOfString(VALUE_FIELD, value);
            }
            return 0;
        }

        @Override
        public boolean fastEquals(@NonNull TestMessage item, @NonNull ReadableSequentialData input)
                throws ParseException {
            throw new UnsupportedOperationException();
        }

        @Override
        public TestMessage getDefaultInstance() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void addToList() {
        var empty_list = ProtoParserTools.addToList(Collections.emptyList(),"foo");
        assertEquals(1,empty_list.size());
        var existing_list = ProtoParserTools.addToList(empty_list,"foo");
        assertEquals(2,existing_list.size());
    }

    @Test
    void addToMap() {
        Map<String,String> empty_map = ProtoParserTools.addToMap(PbjMap.EMPTY,"foo","bar");
        assertEquals(1,empty_map.size());
        assertEquals("bar",empty_map.get("foo"));
        Map<String, String> existing_map = ProtoParserTools.addToMap(empty_map,"baz","quxx");
        assertEquals(2,empty_map.size());
        assertEquals("quxx",empty_map.get("baz"));
    }
}
