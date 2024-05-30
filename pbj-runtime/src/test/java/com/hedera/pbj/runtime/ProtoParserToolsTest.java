package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.FieldType.FIXED32;
import static com.hedera.pbj.runtime.FieldType.FIXED64;
import static com.hedera.pbj.runtime.FieldType.INT32;
import static com.hedera.pbj.runtime.FieldType.MESSAGE;
import static com.hedera.pbj.runtime.FieldType.STRING;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_32_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_64_BIT;
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
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.test.UncheckedThrowingFunction;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import test.proto.Apple;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

class ProtoParserToolsTest {

    private final RandomGenerator rng = RandomGenerator.getDefault();

    @Test
    void testReadInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readInt32,
                // in this case the size may up to 10 bytes in case of negative numbers,
                // because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadInt64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readInt64,
                // in this case the size may be 10 bytes, because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadUint32() {
        testRead(() ->
                rng.nextInt(0, Integer.MAX_VALUE),
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readUint32,
                // the size may vary from 1 to 5 bytes
                Integer.BYTES + 1);
    }

    @Test
    void testReadUint64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readUint64,
                // the size may vary from 1 to 10 bytes
                Long.BYTES + 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void testReadBool(final int value) {
        testRead(() -> value != 0, (d, v) -> d.writeVarInt(value, false), input -> {
            try {
                return ProtoParserTools.readBool(input);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void testReadEnum(int value) {
        testRead(() -> value, (d, v) -> d.writeVarInt(value, false), ProtoParserTools::readEnum, 1);
    }


    @Test
    void testReadSignedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeVarInt(v, true),
                ProtoParserTools::readSignedInt32,
                Integer.BYTES + 1);
    }

    @Test
    void testReadSignedInt64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, true),
                ProtoParserTools::readSignedInt64,
                Long.BYTES + 2);
    }

    @Test
    void testReadSignedFixedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadFixedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadSginedFixed64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFixed64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFloat() {
        testRead(rng::nextFloat,
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFloat,
                Long.BYTES);
    }

    @Test
    void testReadDouble() {
        testRead(rng::nextDouble,
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readDouble,
                Long.BYTES);
    }

    @Test
    void testReadString() {
        final int length = rng.nextInt(0, 100);
        final RandomString randomString = new RandomString(length);

        testRead(randomString::nextString,
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
        final int length = rng.nextInt(0, 100);
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

        testRead(() -> bytes,
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
        final ReadableStreamingData streamingData = new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy));
        assertThrows(BufferUnderflowException.class, () -> ProtoParserTools.readString(streamingData));
    }


    @Test
    void testReadNextFieldNumber() throws IOException {
        BufferedData bufferedData = BufferedData.allocate(100);
        final FieldDefinition definition = createFieldDefinition(MESSAGE);
        final String appleStr = randomVarSizeString();
        final Apple apple = Apple.newBuilder().setVariety(appleStr).build();

        writeMessage(bufferedData, definition, apple, (data, out) -> out.writeBytes(data.toByteArray()), Apple::getSerializedSize);
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
        assertThrows(ParseException.class, () -> ProtoParserTools.skipField(streamingData, WIRE_TYPE_DELIMITED, maxSize));
    }

    @ParameterizedTest
    @EnumSource(names = {"WIRE_TYPE_GROUP_START", "WIRE_TYPE_GROUP_END"})
    void testSkipUnsupported(ProtoConstants unsupportedType) {
        final BufferedData data = BufferedData.allocate(100);
        assertThrows(IOException.class, () -> skipField(data, unsupportedType));
    }

    private static void skipTag(BufferedData data) {
        data.readVarInt(false);
    }

    private static <T> void testRead(final Supplier<? extends T> valueSupplier,
                                     final BiConsumer<BufferedData, ? super T> valueWriter,
                                     final Function<? super BufferedData, T> reader,
                                     final int size) {
        final T value = valueSupplier.get();
        final BufferedData data = BufferedData.allocate(size);
        valueWriter.accept(data, value);
        data.flip();
        assertEquals(value, reader.apply(data));
    }

}