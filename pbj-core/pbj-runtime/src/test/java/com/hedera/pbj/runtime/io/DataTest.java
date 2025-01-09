// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class DataTest {

    static Stream<Byte> bytesTestCases() {
        return Stream.of(
                        Byte.MIN_VALUE,
                        Byte.MIN_VALUE + 1,
                        -100,
                        -66,
                        -7,
                        -1,
                        0,
                        1,
                        9,
                        51,
                        101,
                        Byte.MAX_VALUE - 1,
                        Byte.MAX_VALUE)
                .map(Number::byteValue);
    }

    @ParameterizedTest
    @MethodSource("bytesTestCases")
    void byteTest(Byte value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeByte,
                (dout, v) -> dout.writeByte((int) v),
                BufferedData::writeByte,
                ReadableStreamingData::readByte,
                java.io.DataInputStream::readByte,
                BufferedData::readByte);
    }

    static Stream<Integer> unsignedBytesTestCases() {
        return Stream.of(0, 1, 9, 51, 101, 127, 128, 255).map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedBytesTestCases")
    void unsignedByteTest(Integer value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeUnsignedByte,
                java.io.DataOutputStream::writeByte,
                BufferedData::writeUnsignedByte,
                ReadableStreamingData::readUnsignedByte,
                java.io.DataInputStream::readUnsignedByte,
                ReadableSequentialData::readUnsignedByte);
    }

    static Stream<Integer> intsTestCases() {
        return Stream.of(
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE + 1,
                        -536870912,
                        -4194304,
                        -32768,
                        -100,
                        -66,
                        -7,
                        -1,
                        0,
                        1,
                        9,
                        51,
                        101,
                        32768,
                        4194304,
                        536870912,
                        Integer.MAX_VALUE - 1,
                        Integer.MAX_VALUE)
                .map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void intTest(Integer value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeInt,
                java.io.DataOutputStream::writeInt,
                BufferedData::writeInt,
                ReadableStreamingData::readInt,
                java.io.DataInputStream::readInt,
                BufferedData::readInt);
        doTest(
                value,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt(Integer.reverseBytes(v)),
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readInt(ByteOrder.LITTLE_ENDIAN),
                d -> Integer.reverseBytes(d.readInt()),
                d -> d.readInt(ByteOrder.LITTLE_ENDIAN));
    }

    static Stream<Long> unsignedIntsTestCases() {
        return Stream.of(0, 1, 9, 51, 127, Integer.MAX_VALUE * 2L).map(Number::longValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedIntsTestCases")
    void unsignedIntTest(Long value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeUnsignedInt,
                (dout, v) -> dout.writeInt(v.intValue()),
                BufferedData::writeUnsignedInt,
                ReadableStreamingData::readUnsignedInt,
                (dout) -> Integer.toUnsignedLong(dout.readInt()),
                BufferedData::readUnsignedInt);
        doTest(
                value,
                (d, v) -> d.writeUnsignedInt(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt(Integer.reverseBytes(v.intValue())),
                (d, v) -> d.writeUnsignedInt(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readUnsignedInt(ByteOrder.LITTLE_ENDIAN),
                d -> Integer.toUnsignedLong(Integer.reverseBytes(d.readInt())),
                d -> d.readUnsignedInt(ByteOrder.LITTLE_ENDIAN));
    }

    static Stream<Long> longsTestCases() {
        return Stream.of(
                        Long.MIN_VALUE,
                        Long.MIN_VALUE + 1,
                        Integer.MIN_VALUE - 1L,
                        Integer.MIN_VALUE,
                        Integer.MIN_VALUE + 1,
                        -9007199254740992L,
                        -35184372088832L,
                        -137438953472L,
                        -536870912,
                        -4194304,
                        -65536,
                        -65535,
                        -65534,
                        -32768,
                        -100,
                        -66,
                        -7,
                        -1,
                        0,
                        1,
                        9,
                        51,
                        101,
                        1023,
                        1024,
                        1025,
                        32768,
                        4194304,
                        536870912,
                        137438953472L,
                        35184372088832L,
                        9007199254740992L,
                        Integer.MAX_VALUE - 1L,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE + 1L,
                        Long.MAX_VALUE - 1L,
                        Long.MAX_VALUE)
                .map(Number::longValue);
    }

    @ParameterizedTest
    @MethodSource("longsTestCases")
    void longTest(Long value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeLong,
                java.io.DataOutputStream::writeLong,
                BufferedData::writeLong,
                ReadableStreamingData::readLong,
                java.io.DataInputStream::readLong,
                BufferedData::readLong);
        doTest(
                value,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeLong(Long.reverseBytes(v)),
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readLong(ByteOrder.LITTLE_ENDIAN),
                d -> Long.reverseBytes(d.readLong()),
                d -> d.readLong(ByteOrder.LITTLE_ENDIAN));
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void bytesVarIntTest(int value) throws IOException {
        // zigZag indicates if we want to use zigZag encoding
        for (boolean zigZag : new boolean[] {false, true}) {
            // extendData indicates if we want to enlarge the byte array
            // to force the code to use the Bytes.getVarInt() implementation
            // that doesn't delegate to its super method.
            for (boolean extendData : new boolean[] {false, true}) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                WritableStreamingData dout = new WritableStreamingData(bout);
                dout.writeVarInt(value, zigZag);
                byte[] writtenData = bout.toByteArray();
                byte[] testData = extendData ? Arrays.copyOf(writtenData, 32) : writtenData;

                Bytes bytes = Bytes.wrap(testData);

                int varInt = bytes.getVarInt(0, zigZag);
                assertEquals(value, varInt);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("longsTestCases")
    void bytesVarLongTest(long value) throws IOException {
        // zigZag indicates if we want to use zigZag encoding
        for (boolean zigZag : new boolean[] {false, true}) {
            // extendData indicates if we want to enlarge the byte array
            // to force the code to use the Bytes.getVarLong() implementation
            // that doesn't delegate to its super method.
            for (boolean extendData : new boolean[] {false, true}) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                WritableStreamingData dout = new WritableStreamingData(bout);
                dout.writeVarLong(value, zigZag);
                byte[] writtenData = bout.toByteArray();
                byte[] testData = extendData ? Arrays.copyOf(writtenData, 32) : writtenData;

                Bytes bytes = Bytes.wrap(testData);

                long varLong = bytes.getVarLong(0, zigZag);
                assertEquals(value, varLong);
            }
        }
    }

    static Stream<Float> floatsTestCases() {
        return Stream.of(
                        Float.MIN_VALUE,
                        Integer.MIN_VALUE - 1L,
                        -100,
                        -66,
                        -7,
                        -1,
                        0,
                        1,
                        9,
                        51,
                        101,
                        Integer.MAX_VALUE + 1L,
                        Float.MAX_VALUE)
                .map(Number::floatValue);
    }

    @ParameterizedTest
    @MethodSource("floatsTestCases")
    void floatTest(Float value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeFloat,
                java.io.DataOutputStream::writeFloat,
                BufferedData::writeFloat,
                ReadableStreamingData::readFloat,
                java.io.DataInputStream::readFloat,
                BufferedData::readFloat);
        doTest(
                value,
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt(Integer.reverseBytes(Float.floatToIntBits(v))),
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readFloat(ByteOrder.LITTLE_ENDIAN),
                d -> Float.intBitsToFloat(Integer.reverseBytes(d.readInt())),
                d -> d.readFloat(ByteOrder.LITTLE_ENDIAN));
    }

    static Stream<Double> doublesTestCases() {
        return Stream.of(
                        Double.MIN_VALUE,
                        Integer.MIN_VALUE - 1L,
                        -100,
                        -66,
                        -7,
                        -1,
                        0,
                        1,
                        9,
                        51,
                        101,
                        Integer.MAX_VALUE + 1L,
                        Double.MAX_VALUE)
                .map(Number::doubleValue);
    }

    @ParameterizedTest
    @MethodSource("doublesTestCases")
    void doubleTest(Double value) throws IOException {
        doTest(
                value,
                WritableStreamingData::writeDouble,
                java.io.DataOutputStream::writeDouble,
                BufferedData::writeDouble,
                ReadableStreamingData::readDouble,
                java.io.DataInputStream::readDouble,
                BufferedData::readDouble);
        doTest(
                value,
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeLong(Long.reverseBytes(Double.doubleToLongBits(v))),
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readDouble(ByteOrder.LITTLE_ENDIAN),
                d -> Double.longBitsToDouble(Long.reverseBytes(d.readLong())),
                d -> d.readDouble(ByteOrder.LITTLE_ENDIAN));
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void varIntTest(final int value) throws IOException {
        { // without zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarInt(value, false);
            byte[] writtenData = bout.toByteArray();
            // write to byte array with CodedOutputStream
            ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            var c = CodedOutputStream.newInstance(bout2);
            c.writeInt32NoTag(value);
            c.flush();
            byte[] writtenData2 = bout2.toByteArray();
            assertArrayEquals(writtenData2, writtenData);
            // read back with CodedInputStream
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            var cin = CodedInputStream.newInstance(bin);
            var readValue = cin.readInt32();
            assertEquals(value, readValue);
            // read back with DataInputStream
            bin.reset();
            ReadableStreamingData din = new ReadableStreamingData(bin);
            long readValue2 = din.readVarInt(false);
            assertEquals(value, readValue2);
        }
        { // with zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarInt(value, true);
            byte[] writtenData = bout.toByteArray();
            // write to byte array with CodedOutputStream
            ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            var c = CodedOutputStream.newInstance(bout2);
            c.writeSInt32NoTag(value);
            c.flush();
            byte[] writtenData2 = bout2.toByteArray();
            assertArrayEquals(writtenData2, writtenData);
            // read back with CodedInputStream
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            var cin = CodedInputStream.newInstance(bin);
            var readValue = cin.readSInt32();
            assertEquals(value, readValue);
            // read back with DataInputStream
            bin.reset();
            ReadableStreamingData din = new ReadableStreamingData(bin);
            long readValue2 = din.readVarInt(true);
            assertEquals(value, readValue2);
        }
    }

    @ParameterizedTest
    @MethodSource("longsTestCases")
    void varLongTest(long value) throws IOException {
        { // without zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarLong(value, false);
            byte[] writtenData = bout.toByteArray();
            // write to byte array with CodedOutputStream
            ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            var c = CodedOutputStream.newInstance(bout2);
            c.writeInt64NoTag(value);
            c.flush();
            byte[] writtenData2 = bout2.toByteArray();
            assertArrayEquals(writtenData2, writtenData);
            // read back with CodedInputStream
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            var cin = CodedInputStream.newInstance(bin);
            var readValue = cin.readInt64();
            assertEquals(value, readValue);
            // read back with DataInputStream
            bin.reset();
            ReadableStreamingData din = new ReadableStreamingData(bin);
            long readValue2 = din.readVarLong(false);
            assertEquals(value, readValue2);
        }
        { // with zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarLong(value, true);
            byte[] writtenData = bout.toByteArray();
            // write to byte array with CodedOutputStream
            ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            var c = CodedOutputStream.newInstance(bout2);
            c.writeSInt64NoTag(value);
            c.flush();
            byte[] writtenData2 = bout2.toByteArray();
            assertArrayEquals(writtenData2, writtenData);
            // read back with CodedInputStream
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            var cin = CodedInputStream.newInstance(bin);
            var readValue = cin.readSInt64();
            assertEquals(value, readValue);
            // read back with DataInputStream
            bin.reset();
            ReadableStreamingData din = new ReadableStreamingData(bin);
            long readValue2 = din.readVarLong(true);
            assertEquals(value, readValue2);
        }
    }

    @ParameterizedTest
    @MethodSource("longsTestCases")
    void compatInt32Int64(final long num) {
        { // write as int
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            final WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarInt((int) num, false);
            final byte[] writtenData = bout.toByteArray();
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            ReadableStreamingData din = new ReadableStreamingData(bin);
            final int readValue1 = din.readVarInt(false);
            assertEquals((int) num, readValue1);
            bin = new ByteArrayInputStream(writtenData);
            din = new ReadableStreamingData(bin);
            final long readValue2 = din.readVarLong(false);
            assertEquals((int) num, readValue2);
        }
        { // write as long
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            final WritableStreamingData dout = new WritableStreamingData(bout);
            dout.writeVarLong(num, false);
            final byte[] writtenData = bout.toByteArray();
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            ReadableStreamingData din = new ReadableStreamingData(bin);
            final int readValue1 = din.readVarInt(false);
            assertEquals((int) num, readValue1);
            bin = new ByteArrayInputStream(writtenData);
            din = new ReadableStreamingData(bin);
            final long readValue2 = din.readVarLong(false);
            assertEquals(num, readValue2);
        }
    }

    // ==============================================================================================================
    // Generic test case used by all tests :-)

    static <T> void doTest(
            T value,
            IoWrite<WritableStreamingData, T> dataOutputWriteMethod,
            IoWrite<java.io.DataOutputStream, T> javaDataOutputWriteMethod,
            IoWrite<BufferedData, T> dataBufferWriteMethod,
            IoRead<ReadableStreamingData, T> dataInputReadMethod,
            IoRead<java.io.DataInputStream, T> javaDataInputReadMethod,
            IoRead<BufferedData, T> dataBufferReadMethod)
            throws IOException {
        try {
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            WritableStreamingData dout = new WritableStreamingData(bout);
            dataOutputWriteMethod.write(dout, value);
            byte[] writtenData = bout.toByteArray();
            // write to byte array with Java IO DataOutputStream
            ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
            java.io.DataOutputStream dout2 = new java.io.DataOutputStream(bout2);
            javaDataOutputWriteMethod.write(dout2, value);
            byte[] writtenData2 = bout2.toByteArray();
            // compare written arrays
            assertArrayEquals(writtenData, writtenData2);
            // read back with DataInputStream
            ByteArrayInputStream bin = new ByteArrayInputStream(writtenData);
            ReadableStreamingData din = new ReadableStreamingData(bin);
            T readValue = dataInputReadMethod.read(din);
            assertEquals(value, readValue);
            // read back with Java IO DataOutputStream
            bin.reset();
            java.io.DataInputStream din2 = new java.io.DataInputStream(bin);
            T readValue2 = javaDataInputReadMethod.read(din2);
            assertEquals(value, readValue2);
            // write with BufferedData
            BufferedData db = BufferedData.allocate(writtenData.length);
            dataBufferWriteMethod.write(db, value);
            db.reset();
            // check bytes in buffer
            byte[] writtenData3 = new byte[writtenData.length];
            db.readBytes(writtenData3);
            assertArrayEquals(writtenData, writtenData3);
            // read with DataBuffer
            db.reset();
            T readValue3 = dataBufferReadMethod.read(db);
            assertEquals(value, readValue3);
            // read into Bytes and check all data is valid
            db.reset();
            final var readBytes = db.readBytes(writtenData.length);
            for (int i = 0; i < writtenData.length; i++) {
                assertEquals(writtenData[i], readBytes.getByte(i));
            }
            // read subset into Bytes and check all data is valid
            if (writtenData.length > 3) {
                db.reset();
                // read 1 byte, so we are doing a starting not at 0
                db.readByte();
                // read length -2 so subset
                final var readBytes2 = db.readBytes(writtenData.length - 2);
                for (int i = 0; i < writtenData.length - 2; i++) {
                    assertEquals(writtenData[i + 1], readBytes2.getByte(i));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    public interface IoWrite<T, U> {
        void write(T t, U u) throws IOException;
    }

    public interface IoRead<T, U> {
        U read(T t) throws IOException;
    }
}
