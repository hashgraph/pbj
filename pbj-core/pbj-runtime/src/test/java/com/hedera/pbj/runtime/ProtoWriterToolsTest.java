package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.FieldType.BOOL;
import static com.hedera.pbj.runtime.FieldType.BYTES;
import static com.hedera.pbj.runtime.FieldType.DOUBLE;
import static com.hedera.pbj.runtime.FieldType.ENUM;
import static com.hedera.pbj.runtime.FieldType.FIXED32;
import static com.hedera.pbj.runtime.FieldType.FIXED64;
import static com.hedera.pbj.runtime.FieldType.FLOAT;
import static com.hedera.pbj.runtime.FieldType.INT32;
import static com.hedera.pbj.runtime.FieldType.INT64;
import static com.hedera.pbj.runtime.FieldType.MESSAGE;
import static com.hedera.pbj.runtime.FieldType.SFIXED32;
import static com.hedera.pbj.runtime.FieldType.SFIXED64;
import static com.hedera.pbj.runtime.FieldType.SINT32;
import static com.hedera.pbj.runtime.FieldType.SINT64;
import static com.hedera.pbj.runtime.FieldType.STRING;
import static com.hedera.pbj.runtime.FieldType.UINT32;
import static com.hedera.pbj.runtime.FieldType.UINT64;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_DELIMITED;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_32_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_FIXED_64_BIT;
import static com.hedera.pbj.runtime.ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG;
import static com.hedera.pbj.runtime.ProtoParserTools.readNextFieldNumber;
import static com.hedera.pbj.runtime.ProtoWriterTools.TAG_TYPE_BITS;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfBooleanList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfBytesList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfDelimited;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfDoubleList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfEnumList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfFloatList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfIntegerList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfLongList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfMessageList;
import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfStringList;
import static com.hedera.pbj.runtime.ProtoWriterTools.wireType;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeBytes;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeInteger;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeLong;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeMessage;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalBoolean;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalBytes;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalDouble;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalFloat;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalInteger;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalLong;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeOptionalString;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeString;
import static com.hedera.pbj.runtime.ProtoWriterTools.writeTag;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import test.proto.Apple;


import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;

class ProtoWriterToolsTest {

    public static final int SIZE_OF_TAG = 1;
    public static final int SIZE_OF_LENGTH_VAR = 1;
    public static final int MAX_VAR_INT_SIZE = 6;
    public static final int MAX_VAR_FLOAT_SIZE = 5;
    public static final int MAX_VAR_DOUBLE_SIZE = 9;

    static {
        // to test logic branches unreachable otherwise
        ProtoWriterTools.class.getClassLoader().setClassAssertionStatus(ProtoWriterTools.class.getName(), false);
    }

    private final RandomString randomString = new RandomString(10);
    private BufferedData bufferedData;
    private final RandomGenerator rng = RandomGenerator.getDefault();

    @BeforeEach
    void setUp() {
        bufferedData = BufferedData.allocate(100);
    }

    @Test
    void testWireType() {
        assertEquals(WIRE_TYPE_FIXED_32_BIT, wireType(createFieldDefinition(FLOAT)));
        assertEquals(WIRE_TYPE_FIXED_64_BIT, wireType(createFieldDefinition(DOUBLE)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(INT32)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(INT64)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(SINT32)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(SINT64)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(UINT32)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(UINT64)));


        assertEquals(WIRE_TYPE_FIXED_32_BIT, wireType(createFieldDefinition(FIXED32)));
        assertEquals(WIRE_TYPE_FIXED_32_BIT, wireType(createFieldDefinition(SFIXED32)));
        assertEquals(WIRE_TYPE_FIXED_64_BIT, wireType(createFieldDefinition(FIXED64)));
        assertEquals(WIRE_TYPE_FIXED_64_BIT, wireType(createFieldDefinition(SFIXED64)));
        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(BOOL)));

        assertEquals(WIRE_TYPE_DELIMITED, wireType(createFieldDefinition(BYTES)));
        assertEquals(WIRE_TYPE_DELIMITED, wireType(createFieldDefinition(MESSAGE)));
        assertEquals(WIRE_TYPE_DELIMITED, wireType(createFieldDefinition(STRING)));

        assertEquals(WIRE_TYPE_VARINT_OR_ZIGZAG, wireType(createFieldDefinition(ENUM)));
    }

    @Test
    void testWriteTag() {
        FieldDefinition definition = createFieldDefinition(FLOAT);
        writeTag(bufferedData, definition);
        bufferedData.flip();
        assertFixed32Tag(definition);
    }

    @Test
    void testWriteTagSpecialWireType() {
        FieldDefinition definition = createFieldDefinition(DOUBLE);
        writeTag(bufferedData, definition, WIRE_TYPE_FIXED_64_BIT);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_FIXED_64_BIT.ordinal(), bufferedData.readVarInt(false));
    }

    @Test
    void testWriteInteger_int32() {
        FieldDefinition definition = createFieldDefinition(INT32);
        final int valToWrite = rng.nextInt();
        writeInteger(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteInteger_uint32() {
        FieldDefinition definition = createFieldDefinition(UINT32);
        final int valToWrite = rng.nextInt(0, Integer.MAX_VALUE);
        writeInteger(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarLong(false));
    }

    @Test
    void testWriteInteger_sint32() {
        FieldDefinition definition = createFieldDefinition(SINT32);
        final int valToWrite = rng.nextInt();
        writeInteger(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarInt(true));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"SFIXED32", "FIXED32"})
    void testWriteInteger_fixed32(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        final int valToWrite = rng.nextInt();
        writeInteger(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertFixed32Tag(definition);
        assertEquals(valToWrite, bufferedData.readInt(ByteOrder.LITTLE_ENDIAN));
    }
    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {
            "DOUBLE", "FLOAT", "INT64", "UINT64", "SINT64",
            "FIXED64", "SFIXED64", "BOOL",
            "STRING", "BYTES", "ENUM", "MESSAGE"})
    void testWriteInteger_unsupported(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertThrows(RuntimeException.class, () -> writeInteger(bufferedData, definition, rng.nextInt()));
    }

    @Test
    void testWriteLong_int64() {
        FieldDefinition definition = createFieldDefinition(INT64);
        final long valToWrite = rng.nextLong();
        writeLong(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarLong(false));
    }

    @Test
    void testWriteLong_uint64() {
        FieldDefinition definition = createFieldDefinition(UINT64);
        final long valToWrite = rng.nextLong(0, Long.MAX_VALUE);
        writeLong(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarLong(false));
    }

    @Test
    void testWriteLong_sint64() {
        FieldDefinition definition = createFieldDefinition(SINT64);
        final int valToWrite = rng.nextInt();
        writeLong(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(valToWrite, bufferedData.readVarLong(true));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"SFIXED64", "FIXED64"})
    void testWriteLong_fixed64(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        final long valToWrite = rng.nextLong();
        writeLong(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_FIXED_64_BIT.ordinal(), bufferedData.readVarInt(false));
        assertEquals(valToWrite, bufferedData.readLong(ByteOrder.LITTLE_ENDIAN));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {
            "DOUBLE", "FLOAT", "INT32", "UINT32", "SINT32",
            "FIXED32", "SFIXED32", "BOOL",
            "STRING", "BYTES", "ENUM", "MESSAGE"})
    void testWriteLong_unsupported(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertThrows(RuntimeException.class, () -> writeLong(bufferedData, definition, rng.nextInt()));
    }

    @Test
    void testWriteFloat() {
        FieldDefinition definition = createFieldDefinition(FLOAT);
        final float valToWrite = rng.nextFloat();
        ProtoWriterTools.writeFloat(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertFixed32Tag(definition);
        assertEquals(valToWrite, bufferedData.readFloat(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void testWriteDouble() {
        FieldDefinition definition = createFieldDefinition(DOUBLE);
        final double valToWrite = rng.nextDouble();
        ProtoWriterTools.writeDouble(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_FIXED_64_BIT.ordinal(), bufferedData.readVarInt(false));
        assertEquals(valToWrite, bufferedData.readDouble(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void testWriteBoolean_true() {
        FieldDefinition definition = createFieldDefinition(BOOL);
        ProtoWriterTools.writeBoolean(bufferedData, definition, true);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(1, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteBoolean_false() {
        FieldDefinition definition = createFieldDefinition(BOOL);
        ProtoWriterTools.writeBoolean(bufferedData, definition, false);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteEnum() {
        FieldDefinition definition = createFieldDefinition(ENUM);
        EnumWithProtoMetadata enumWithProtoMetadata = mock(EnumWithProtoMetadata.class);
        int expectedOrdinal = rng.nextInt();
        when(enumWithProtoMetadata.protoOrdinal()).thenReturn(expectedOrdinal);
        when(enumWithProtoMetadata.protoName()).thenReturn(randomString.nextString());
        ProtoWriterTools.writeEnum(bufferedData, definition, enumWithProtoMetadata);
        bufferedData.flip();
        assertVarIntTag(definition);
        assertEquals(expectedOrdinal, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteString() throws IOException {
        FieldDefinition definition = createFieldDefinition(STRING);
        String valToWrite = randomString.nextString();
        writeString(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED.ordinal(), bufferedData.readVarInt(false));
        int length = bufferedData.readVarInt(false);
        assertEquals(valToWrite, new String(bufferedData.readBytes(length).toByteArray()));
    }

    @Test
    void testWriteBytes() throws IOException {
        FieldDefinition definition = createFieldDefinition(BYTES);
        Bytes valToWrite = Bytes.wrap(randomString.nextString());
        writeBytes(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED.ordinal(), bufferedData.readVarInt(false));
        int length = bufferedData.readVarInt(false);
        assertEquals(valToWrite, bufferedData.readBytes(length));
    }

    @Test
    void testWriteMessage() throws IOException {
        FieldDefinition definition = createFieldDefinition(MESSAGE);
        String appleStr = randomString.nextString();
        Apple apple = Apple.newBuilder().setVariety(appleStr).build();
        writeMessage(bufferedData, definition, apple, (data, out) -> out.writeBytes(data.toByteArray()), Apple::getSerializedSize);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED.ordinal(), bufferedData.readVarInt(false));
        int length = bufferedData.readVarInt(false);
        assertEquals(appleStr, Apple.parseFrom(bufferedData.readBytes(length).toByteArray()).getVariety());
    }

    @Test
    void testWriteOneOfMessage() throws IOException {
        FieldDefinition definition = createOneOfFieldDefinition(MESSAGE);
        writeMessage(bufferedData, definition, null, (data, out) -> out.writeBytes(data.toByteArray()), Apple::getSerializedSize);
        bufferedData.flip();
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED.ordinal(), bufferedData.readVarInt(false));
        int length = bufferedData.readVarInt(false);
        assertEquals(0, length);

    }

    @Test
    void testWriteOptionalInteger() {
        FieldDefinition definition = createOptionalFieldDefinition(INT32);
        final int valToWrite = randomLargeInt();
        writeOptionalInteger(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(MAX_VAR_INT_SIZE, bufferedData.readVarInt(false));
        assertVarIntTag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteOptionalInteger_null() {
        FieldDefinition definition = createOptionalFieldDefinition(INT32);
        writeOptionalInteger(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalLong() {
        FieldDefinition definition = createOptionalFieldDefinition(INT64);
        final long valToWrite = randomLargeInt();
        writeOptionalLong(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(MAX_VAR_INT_SIZE, bufferedData.readVarInt(false));
        assertVarIntTag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteOptionalLong_null() {
        FieldDefinition definition = createOptionalFieldDefinition(INT64);
        writeOptionalLong(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalFloat() {
        FieldDefinition definition = createOptionalFieldDefinition(FLOAT);
        final float valToWrite = randomLargeInt();
        writeOptionalFloat(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(MAX_VAR_FLOAT_SIZE, bufferedData.readVarInt(false));
        assertFixed32Tag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite, bufferedData.readFloat(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void testWriteOptionalFloat_null() {
        FieldDefinition definition = createOptionalFieldDefinition(FLOAT);
        writeOptionalFloat(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalDouble() {
        FieldDefinition definition = createOptionalFieldDefinition(DOUBLE);
        final double valToWrite = randomLargeInt();
        writeOptionalDouble(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(MAX_VAR_DOUBLE_SIZE, bufferedData.readVarInt(false));
        assertFixed64Tag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite, bufferedData.readDouble(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void testWriteOptionalDouble_null() {
        FieldDefinition definition = createOptionalFieldDefinition(DOUBLE);
        writeOptionalFloat(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalBoolean_true() {
        FieldDefinition definition = createOptionalFieldDefinition(BOOL);
        writeOptionalBoolean(bufferedData, definition, true);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        // size of boolean = 1 byte and size of a tag = 1 byte
        assertEquals(2, bufferedData.readVarInt(false));
        assertVarIntTag(definition.type().optionalFieldDefinition);
        assertEquals(1, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteOptionalBoolean_false() {
        FieldDefinition definition = createOptionalFieldDefinition(BOOL);
        writeOptionalBoolean(bufferedData, definition, false);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(0, bufferedData.readVarInt(false));
    }

    @Test
    void testWriteOptionalBoolean_null() {
        FieldDefinition definition = createOptionalFieldDefinition(BOOL);
        writeOptionalBoolean(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalString() throws IOException {
        FieldDefinition definition = createOptionalFieldDefinition(STRING);
        String valToWrite = randomString.nextString();
        writeOptionalString(bufferedData, definition, valToWrite);
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(valToWrite.length() + SIZE_OF_TAG + SIZE_OF_LENGTH_VAR, bufferedData.readVarInt(false));
        assertTypeDelimitedTag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite.length(), bufferedData.readVarInt(false));
        assertEquals(valToWrite, new String(bufferedData.readBytes(valToWrite.length()).toByteArray()));
    }

    @Test
    void testWriteOptionalString_null() throws IOException {
        FieldDefinition definition = createOptionalFieldDefinition(STRING);
        writeOptionalString(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }

    @Test
    void testWriteOptionalBytes() throws IOException {
        FieldDefinition definition = createOptionalFieldDefinition(STRING);
        byte[] valToWrite = new byte[10];
        rng.nextBytes(valToWrite);
        writeOptionalBytes(bufferedData, definition, Bytes.wrap(valToWrite));
        bufferedData.flip();
        assertTypeDelimitedTag(definition);
        assertEquals(valToWrite.length + SIZE_OF_TAG + SIZE_OF_LENGTH_VAR, bufferedData.readVarInt(false));
        assertTypeDelimitedTag(definition.type().optionalFieldDefinition);
        assertEquals(valToWrite.length, bufferedData.readVarInt(false));
        assertArrayEquals(valToWrite, bufferedData.readBytes(valToWrite.length).toByteArray());
    }

    @Test
    void testWriteOptionalBytes_null() throws IOException {
        FieldDefinition definition = createOptionalFieldDefinition(STRING);
        writeOptionalBytes(bufferedData, definition, null);
        bufferedData.flip();
        assertEquals(0, bufferedData.length());
    }


    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"INT32", "UINT32"})
    void testSizeOfIntegerList_int32(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 1 * 2 /* size of two unsigned var longs in the range [0, 128) */,
                sizeOfIntegerList(definition, asList(rng.nextInt(0, 127), rng.nextInt(0, 128))));
    }

    @Test
    void testSizeOfIntegerList_sint32() {
        FieldDefinition definition = createFieldDefinition(SINT32);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 1 * 2 /* size of two unsigned var longs in the range (-64, 64) */,
                sizeOfIntegerList(definition, asList(rng.nextInt(-63, 0), rng.nextInt(0, 64))));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"SFIXED32", "FIXED32"})
    void testSizeOfIntegerList_fixed(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + Integer.BYTES * 2 /* size of two unsigned var longs in the range [0, 128) */,
                sizeOfIntegerList(definition, asList(rng.nextInt(), rng.nextInt())));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"DOUBLE", "FLOAT", "INT64", "UINT64", "SINT64",
            "FIXED64", "SFIXED64", "BOOL", "STRING", "BYTES", "ENUM", "MESSAGE"})
    void testSizeOfIntegerList_notSupported(FieldType type) {
        assertThrows(RuntimeException.class, () -> sizeOfIntegerList(createFieldDefinition(type), asList(rng.nextInt(), rng.nextInt())));
    }

    @Test
    void testSizeOfIntegerList_empty() {
        assertEquals(0, sizeOfIntegerList(createFieldDefinition(INT64), Collections.emptyList()));
    }

    @Test
    void testSizeOfOneOfIntegerList_empty() {
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR,
                sizeOfLongList(createOneOfFieldDefinition(INT64), emptyList()));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"INT64", "UINT64"})
    void testSizeOfLongList_int64(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 1 * 2 /* size of two unsigned var longs in the range [0, 128) */,
                sizeOfLongList(definition, asList(rng.nextLong(0, 127), rng.nextLong(0, 128))));
    }

    @Test
    void testSizeOfLongList_sint64() {
        FieldDefinition definition = createFieldDefinition(SINT64);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 1 * 2 /* size of two unsigned var longs in the range (-64, 64) */,
                sizeOfLongList(definition, asList(rng.nextLong(-63, 0), rng.nextLong(0, 64))));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"SFIXED64", "FIXED64"})
    void testSizeOfLongList_fixed(FieldType type) {
        FieldDefinition definition = createFieldDefinition(type);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + Long.BYTES * 2 /* size of two unsigned var longs in the range [0, 128) */,
                sizeOfLongList(definition, asList(rng.nextLong(), rng.nextLong())));
    }

    @ParameterizedTest
    @EnumSource(value = FieldType.class, names = {"DOUBLE", "FLOAT", "INT32", "UINT32", "SINT32",
            "FIXED32", "SFIXED32", "BOOL", "STRING", "BYTES", "ENUM", "MESSAGE"})
    void testSizeOfLongList_notSupported(FieldType type) {
        assertThrows(RuntimeException.class, () -> sizeOfLongList(createFieldDefinition(type), asList(rng.nextLong(), rng.nextLong())));
    }

    @Test
    void testSizeOfLongList_empty() {
        assertEquals(0, sizeOfLongList(createFieldDefinition(INT64), Collections.emptyList()));
    }

    @Test
    void testSizeOfOneOfLongList_empty() {
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR,
                sizeOfLongList(createOneOfFieldDefinition(INT64), emptyList()));
    }

    @Test
    void testSizeOfFloatList() {
        FieldDefinition definition = createFieldDefinition(FLOAT);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 2 * Float.BYTES,
                sizeOfFloatList(definition, asList(rng.nextFloat(), rng.nextFloat())));
    }

    @Test
    void testSizeOfFloatList_empty() {
        assertEquals(0, sizeOfFloatList(createFieldDefinition(FLOAT), Collections.emptyList()));
    }

    @Test
    void testSizeOfOneOfFloatList_empty() {
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR,
                sizeOfFloatList(createOneOfFieldDefinition(FLOAT), emptyList()));
    }

    @Test
    void testSizeOfDoubleList() {
        FieldDefinition definition = createFieldDefinition(DOUBLE);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 2 * Double.BYTES,
                sizeOfDoubleList(definition, asList(rng.nextDouble(), rng.nextDouble())));
    }

    @Test
    void testSizeOfDoubleList_empty() {
        assertEquals(0, sizeOfDoubleList(createFieldDefinition(DOUBLE), Collections.emptyList()));
    }

    @Test
    void testSizeOfOneOfDoubleList_empty() {
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR,
                sizeOfDoubleList(createOneOfFieldDefinition(DOUBLE), emptyList()));
    }


    @Test
    void testSizeOfBooleanList(){
        FieldDefinition definition = createFieldDefinition(BOOL);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + 2, sizeOfBooleanList(definition, Arrays.asList(true, false)));
    }

    @Test
    void testSizeOfBooleanList_empty(){
        assertEquals(0, sizeOfBooleanList(createFieldDefinition(BOOL), Collections.emptyList()));
    }

    @Test
    void testSizeOfOneOfBooleanList_empty() {
        FieldDefinition definition = createOneOfFieldDefinition(BOOL);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR, sizeOfBooleanList(definition, emptyList()));
    }

    @Test
    void testSizeOfEnumList() {
        FieldDefinition definition = createFieldDefinition(ENUM);
        EnumWithProtoMetadata enum1 = mock(EnumWithProtoMetadata.class);
        EnumWithProtoMetadata enum2 = mock(EnumWithProtoMetadata.class);
        when(enum1.protoOrdinal()).thenReturn(rng.nextInt(1, 16));
        when(enum2.protoName()).thenReturn(randomString.nextString());
        when(enum1.protoOrdinal()).thenReturn(rng.nextInt(1, 16));
        when(enum2.protoName()).thenReturn(randomString.nextString());
        List<EnumWithProtoMetadata> enums = asList(enum1, enum2);
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR + enums.size(), sizeOfEnumList(definition, enums));
    }

    @Test
    void testSizeOfEnumList_empty() {
        assertEquals(0, sizeOfEnumList(createFieldDefinition(ENUM), emptyList()));
    }

    @Test
    void testSizeOfOneOfEnumList_empty() {
        assertEquals(SIZE_OF_TAG + SIZE_OF_LENGTH_VAR, sizeOfEnumList(createOneOfFieldDefinition(ENUM), emptyList()));
    }

    @Test
    void testSizeOfStringList() {
        FieldDefinition definition = createFieldDefinition(STRING);
        String str1 = randomVarSizeString();
        String str2 = randomVarSizeString();

        assertEquals(SIZE_OF_LENGTH_VAR * 2 + SIZE_OF_TAG * 2 + str1.length() + str2.length(),
                sizeOfStringList(definition, asList(str1, str2)));
    }

    @Test
    void testSizeOfStringList_empty() {
        assertEquals(0,
                sizeOfStringList(createOneOfFieldDefinition(STRING), emptyList()));
    }

    @Test
    void testSizeOfMessageList() {
        final FieldDefinition definition = createFieldDefinition(MESSAGE);
        final String appleStr1 = randomVarSizeString();
        final Apple apple1 = Apple.newBuilder().setVariety(appleStr1).build();
        final String appleStr2 = randomVarSizeString();
        final Apple apple2 = Apple.newBuilder().setVariety(appleStr2).build();

        assertEquals(
                SIZE_OF_LENGTH_VAR * 2 + SIZE_OF_TAG * 2 + appleStr1.length() + appleStr2.length(),
                sizeOfMessageList(definition, Arrays.asList(apple1, apple2), v -> v.getVariety().length()));
    }

    @Test
    void testSizeOfMessageList_empty() {
        assertEquals(
                0,
                sizeOfMessageList(createFieldDefinition(MESSAGE), emptyList(), v -> rng.nextInt()));
    }

    @Test
    void testSizeOfBytesList() {
        FieldDefinition definition = createFieldDefinition(BYTES);
        Bytes bytes1 = Bytes.wrap(randomVarSizeString());
        Bytes bytes2 = Bytes.wrap(randomVarSizeString());

        assertEquals(
                SIZE_OF_LENGTH_VAR * 2 + SIZE_OF_TAG * 2
                        + bytes1.length() + bytes2.length(),
                sizeOfBytesList(definition, asList(bytes1, bytes2)));
    }

    @Test
    void testSizeOfBytesList_empty() {
        assertEquals(0, sizeOfBytesList(createFieldDefinition(BYTES), emptyList()));
    }

    @Test
    void testSizeOfDelimited() {
        final FieldDefinition definition = createFieldDefinition(BYTES);
        final int length = rng.nextInt(1, 64);

        assertEquals(SIZE_OF_LENGTH_VAR + SIZE_OF_TAG + length, sizeOfDelimited(definition, length));
    }

    @Test
    void testReadNextFieldNumber() throws IOException {
        final FieldDefinition definition = createFieldDefinition(MESSAGE);
        final String appleStr = randomVarSizeString();
        final Apple apple = Apple.newBuilder().setVariety(appleStr).build();

        writeMessage(bufferedData, definition, apple, (data, out) -> out.writeBytes(data.toByteArray()), Apple::getSerializedSize);
        bufferedData.flip();

        assertEquals(definition.number(), readNextFieldNumber(bufferedData));
    }

    private String randomVarSizeString() {
        return new RandomString(rng.nextInt(1, 64)).nextString();
    }

    private void assertVarIntTag(FieldDefinition definition) {
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_VARINT_OR_ZIGZAG.ordinal(), bufferedData.readVarInt(false));
    }

    private void assertFixed32Tag(FieldDefinition definition) {
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_FIXED_32_BIT.ordinal(), bufferedData.readVarInt(false));
    }

    private void assertFixed64Tag(FieldDefinition definition) {
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_FIXED_64_BIT.ordinal(), bufferedData.readVarInt(false));
    }

    private void assertTypeDelimitedTag(FieldDefinition definition) {
        assertEquals((definition.number() << TAG_TYPE_BITS) | WIRE_TYPE_DELIMITED.ordinal(), bufferedData.readVarInt(false));
    }

    private FieldDefinition createFieldDefinition(FieldType fieldType) {
        return new FieldDefinition(randomString.nextString(), fieldType, false, rng.nextInt(1, 16));
    }

    private FieldDefinition createOptionalFieldDefinition(FieldType fieldType) {
        return new FieldDefinition(randomString.nextString(), fieldType, false, true, false, rng.nextInt(1, 16));
    }

    private FieldDefinition createOneOfFieldDefinition(FieldType fieldType) {
        return new FieldDefinition(randomString.nextString(), fieldType, false, false, true, rng.nextInt(1, 16));
    }

    /**
     * @return random int that turns into a var int of max size
     */
    private int randomLargeInt() {
        return rng.nextInt(Integer.MAX_VALUE >> 3, Integer.MAX_VALUE);
    }

}