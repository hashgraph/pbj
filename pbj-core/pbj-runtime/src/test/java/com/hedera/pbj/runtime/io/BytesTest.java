package com.hedera.pbj.runtime.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteOrder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class BytesTest {
    static Stream<byte[]> byteArraysTestCases() {
        return Stream.of(
                new byte[0],
                new byte[]{0},
                new byte[]{Byte.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Byte.MAX_VALUE}
        );
    }

    @ParameterizedTest
    @MethodSource("byteArraysTestCases")
    void simpleTests(byte[] value) {
        final Bytes bytes1 = Bytes.wrap(value);
        final Bytes bytes2 = Bytes.wrap(value);
        assertEquals(value.length, bytes1.getLength());
        for (int i = 0; i < value.length; i++) {
            assertEquals(value[i], bytes1.getByte(i));
            assertEquals(bytes1.getByte(i), bytes2.getByte(i));
        }
        assertEquals(bytes1.hashCode(), bytes2.hashCode());
        assertEquals(bytes1,bytes2);
        final Bytes bytes3 = Bytes.wrap(new byte[]{1,39,28,92});
        assertNotEquals(bytes1, bytes3);
        assertNotEquals(bytes1.hashCode(), bytes3.hashCode());
    }


    static Stream<Byte> bytesTestCases() {
        return Stream.of(Byte.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Byte.MAX_VALUE).map(Number::byteValue);
    }

    @ParameterizedTest
    @MethodSource("bytesTestCases")
    void byteTest(Byte value) {
        final int length = Byte.BYTES;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeByte(value);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getByte(0));
    }

    static Stream<Integer> unsignedBytesTestCases() {
        return Stream.of(0,1,9,51,101,127,128,255).map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedBytesTestCases")
    void unsignedByteTest(Integer value) {
        final int length = Byte.BYTES;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeUnsignedByte(value);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getUnsignedByte(0));
    }

    static Stream<Integer> intsTestCases() {
        return Stream.of(Integer.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE).map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void intTest(Integer value) {
        final int length = Integer.BYTES*2;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeInt(value);
        db.writeInt(value, ByteOrder.LITTLE_ENDIAN);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getInt(0));
        assertEquals(value, bytes.getInt(Integer.BYTES, ByteOrder.LITTLE_ENDIAN));
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void varIntTest(Integer value) {
        DataBuffer db = DataBuffer.allocate(20, false);
        db.writeVarInt(value, false);
        final int varInt1Size = (int)db.getPosition();
        db.writeVarInt(value, true);
        db.flip();
        final Bytes bytes = db.readBytes((int)db.getRemaining());
        assertEquals(value, bytes.getVarInt(0, false));
        assertEquals(value, bytes.getVarInt(varInt1Size, true));
    }

    static Stream<Long> unsignedIntsTestCases() {
        return Stream.of(0,1,9,51,127,Integer.MAX_VALUE*2L).map(Number::longValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedIntsTestCases")
    void unsignedIntTest(Long value) {
        final int length = Integer.BYTES*2;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeUnsignedInt(value);
        db.writeUnsignedInt(value, ByteOrder.LITTLE_ENDIAN);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getUnsignedInt(0));
        assertEquals(value, bytes.getUnsignedInt(Integer.BYTES, ByteOrder.LITTLE_ENDIAN));
    }

    static Stream<Long> longsTestCases() {
        return Stream.of(Long.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Long.MAX_VALUE).map(Number::longValue);
    }
    @ParameterizedTest
    @MethodSource("longsTestCases")
    void longTest(Long value) {
        final int length = Long.BYTES*2;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeLong(value);
        db.writeLong(value, ByteOrder.LITTLE_ENDIAN);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getLong(0));
        assertEquals(value, bytes.getLong(Long.BYTES, ByteOrder.LITTLE_ENDIAN));
    }

    @ParameterizedTest
    @MethodSource("longsTestCases")
    void varLongTest(Long value) {
        DataBuffer db = DataBuffer.allocate(20, false);
        db.writeVarLong(value, false);
        final int varInt1Size = (int)db.getPosition();
        db.writeVarLong(value, true);
        db.flip();
        final Bytes bytes = db.readBytes((int)db.getRemaining());
        assertEquals(value, bytes.getVarLong(0, false));
        assertEquals(value, bytes.getVarLong(varInt1Size, true));
    }

    static Stream<Float> floatsTestCases() {
        return Stream.of(Float.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Float.MAX_VALUE).map(Number::floatValue);
    }
    @ParameterizedTest
    @MethodSource("floatsTestCases")
    void floatTest(Float value) {
        final int length = Float.BYTES*2;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeFloat(value);
        db.writeFloat(value, ByteOrder.LITTLE_ENDIAN);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getFloat(0));
        assertEquals(value, bytes.getFloat(Float.BYTES, ByteOrder.LITTLE_ENDIAN));
    }

    static Stream<Double> doublesTestCases() {
        return Stream.of(Double.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Double.MAX_VALUE).map(Number::doubleValue);
    }
    @ParameterizedTest
    @MethodSource("doublesTestCases")
    void doubleTest(Double value) {
        final int length = Double.BYTES * 2;
        DataBuffer db = DataBuffer.allocate(length, false);
        db.writeDouble(value);
        db.writeDouble(value, ByteOrder.LITTLE_ENDIAN);
        db.reset();
        final Bytes bytes = db.readBytes(length);
        assertEquals(value, bytes.getDouble(0));
        assertEquals(value, bytes.getDouble(Double.BYTES, ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void matchesPrefixByteArrayTest() {
        Bytes primary = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        assertTrue(primary.matchesPrefix(new byte[]{0x01}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09}));

        assertFalse(primary.matchesPrefix(new byte[]{0x02}));
        assertFalse(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x02}));
        assertFalse(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00}));
    }
    @Test
    void matchesPrefixBytesTest() {
        Bytes primary = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        Bytes prefixGood1 = new ByteOverByteBuffer(new byte[]{0x01});
        assertTrue(primary.matchesPrefix(prefixGood1));
        Bytes prefixGood2 = new ByteOverByteBuffer(new byte[]{0x01,0x02});
        assertTrue(primary.matchesPrefix(prefixGood2));
        Bytes prefixGood3 = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x04,});
        assertTrue(primary.matchesPrefix(prefixGood3));
        Bytes prefixGood4 = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        assertTrue(primary.matchesPrefix(prefixGood4));

        Bytes prefixBad1 = new ByteOverByteBuffer(new byte[]{0x02});
        assertFalse(primary.matchesPrefix(prefixBad1));
        Bytes prefixBad2 = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x02});
        assertFalse(primary.matchesPrefix(prefixBad2));
        Bytes prefixBad3 = new ByteOverByteBuffer(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00});
        assertFalse(primary.matchesPrefix(prefixBad3));
    }
}
