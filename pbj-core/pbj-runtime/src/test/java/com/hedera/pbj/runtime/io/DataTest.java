package com.hedera.pbj.runtime.io;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataTest {

    static Stream<Byte> bytesTestCases() {
        return Stream.of(Byte.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Byte.MAX_VALUE).map(Number::byteValue);
    }

    @ParameterizedTest
    @MethodSource("bytesTestCases")
    void byteTest(Byte value) throws IOException {
        doTest(value,
                DataOutputStream::writeByte,
                (dout, v) -> dout.writeByte((int)v),
                DataBuffer::writeByte,
                DataInputStream::readByte,
                java.io.DataInputStream::readByte,
                DataBuffer::readByte
        );
    }

    static Stream<Integer> unsignedBytesTestCases() {
        return Stream.of(0,1,9,51,101,127,128,255).map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedBytesTestCases")
    void unsignedByteTest(Integer value) throws IOException {
        doTest(value,
                DataOutputStream::writeUnsignedByte,
                java.io.DataOutputStream::writeByte,
                DataBuffer::writeUnsignedByte,
                DataInputStream::readUnsignedByte,
                java.io.DataInputStream::readUnsignedByte,
                DataInput::readUnsignedByte
        );
    }

    static Stream<Integer> intsTestCases() {
        return Stream.of(Integer.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE).map(Number::intValue);
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void intTest(Integer value) throws IOException {
        doTest(value,
                DataOutputStream::writeInt,
                java.io.DataOutputStream::writeInt,
                DataBuffer::writeInt,
                DataInputStream::readInt,
                java.io.DataInputStream::readInt,
                DataBuffer::readInt
        );
        doTest(value,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt(Integer.reverseBytes(v)),
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readInt(ByteOrder.LITTLE_ENDIAN),
                d -> Integer.reverseBytes(d.readInt()),
                d -> d.readInt(ByteOrder.LITTLE_ENDIAN)
        );
    }

    static Stream<Long> unsignedIntsTestCases() {
        return Stream.of(0,1,9,51,127,Integer.MAX_VALUE*2L).map(Number::longValue);
    }

    @ParameterizedTest
    @MethodSource("unsignedIntsTestCases")
    void unsignedIntTest(Long value) throws IOException {
        doTest(value,
                DataOutputStream::writeUnsignedInt,
                (dout, v) -> dout.writeInt(v.intValue()),
                DataBuffer::writeUnsignedInt,
                DataInputStream::readUnsignedInt,
                (dout) -> Integer.toUnsignedLong(dout.readInt()),
                DataBuffer::readUnsignedInt
        );
        doTest(value,
                (d, v) -> d.writeUnsignedInt(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt(Integer.reverseBytes(v.intValue())),
                (d, v) -> d.writeUnsignedInt(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readUnsignedInt(ByteOrder.LITTLE_ENDIAN),
                d -> Integer.toUnsignedLong(Integer.reverseBytes(d.readInt())),
                d -> d.readUnsignedInt(ByteOrder.LITTLE_ENDIAN)
        );
    }

    static Stream<Long> longsTestCases() {
        return Stream.of(Long.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Long.MAX_VALUE).map(Number::longValue);
    }
    @ParameterizedTest
    @MethodSource("longsTestCases")
    void longTest(Long value) throws IOException {
        doTest(value,
                DataOutputStream::writeLong,
                java.io.DataOutputStream::writeLong,
                DataBuffer::writeLong,
                DataInputStream::readLong,
                java.io.DataInputStream::readLong,
                DataBuffer::readLong
        );
        doTest(value,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeLong(Long.reverseBytes(v)),
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readLong(ByteOrder.LITTLE_ENDIAN),
                d -> Long.reverseBytes(d.readLong()),
                d -> d.readLong(ByteOrder.LITTLE_ENDIAN)
        );
    }

    static Stream<Float> floatsTestCases() {
        return Stream.of(Float.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Float.MAX_VALUE).map(Number::floatValue);
    }
    @ParameterizedTest
    @MethodSource("floatsTestCases")
    void floatTest(Float value) throws IOException {
        doTest(value,
                DataOutputStream::writeFloat,
                java.io.DataOutputStream::writeFloat,
                DataBuffer::writeFloat,
                DataInputStream::readFloat,
                java.io.DataInputStream::readFloat,
                DataBuffer::readFloat
        );
        doTest(value,
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeInt( Integer.reverseBytes(Float.floatToIntBits(v))),
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readFloat(ByteOrder.LITTLE_ENDIAN),
                d -> Float.intBitsToFloat(Integer.reverseBytes(d.readInt())),
                d -> d.readFloat(ByteOrder.LITTLE_ENDIAN)
        );
    }
    static Stream<Double> doublesTestCases() {
        return Stream.of(Double.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Double.MAX_VALUE).map(Number::doubleValue);
    }
    @ParameterizedTest
    @MethodSource("doublesTestCases")
    void doubleTest(Double value) throws IOException {
        doTest(value,
                DataOutputStream::writeDouble,
                java.io.DataOutputStream::writeDouble,
                DataBuffer::writeDouble,
                DataInputStream::readDouble,
                java.io.DataInputStream::readDouble,
                DataBuffer::readDouble
        );
        doTest(value,
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                (d, v) -> d.writeLong( Long.reverseBytes(Double.doubleToLongBits(v))),
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                d -> d.readDouble(ByteOrder.LITTLE_ENDIAN),
                d -> Double.longBitsToDouble(Long.reverseBytes(d.readLong())),
                d -> d.readDouble(ByteOrder.LITTLE_ENDIAN)
        );
    }

    @ParameterizedTest
    @MethodSource("intsTestCases")
    void varIntTest(int value) throws IOException {
        { // without zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
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
            DataInputStream din = new DataInputStream(bin);
            long readValue2 = din.readVarInt(false);
            assertEquals(value, readValue2);
        }
        { // with zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
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
            DataInputStream din = new DataInputStream(bin);
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
            DataOutputStream dout = new DataOutputStream(bout);
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
            DataInputStream din = new DataInputStream(bin);
            long readValue2 = din.readVarLong(false);
            assertEquals(value, readValue2);
        }
        { // with zigzag
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
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
            DataInputStream din = new DataInputStream(bin);
            long readValue2 = din.readVarLong(true);
            assertEquals(value, readValue2);
        }
    }

    // ==============================================================================================================
    // Generic test case used by all tests :-)

    static <T> void doTest(T value,
                           IoWrite<DataOutputStream,T> dataOutputWriteMethod,
                           IoWrite<java.io.DataOutputStream,T> javaDataOutputWriteMethod,
                           IoWrite<DataBuffer,T> dataBufferWriteMethod,
                           IoRead<DataInputStream,T> dataInputReadMethod,
                           IoRead<java.io.DataInputStream,T> javaDataInputReadMethod,
                           IoRead<DataBuffer,T> dataBufferReadMethod
    ) throws IOException {
        try {
            // write to byte array with DataIO DataOutputStream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream dout = new DataOutputStream(bout);
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
            DataInputStream din = new DataInputStream(bin);
            T readValue = dataInputReadMethod.read(din);
            assertEquals(value, readValue);
            // read back with Java IO DataOutputStream
            bin.reset();
            java.io.DataInputStream din2 = new java.io.DataInputStream(bin);
            T readValue2 = javaDataInputReadMethod.read(din2);
            assertEquals(value, readValue2);
            // write with DataBuffer
            DataBuffer db = new DataBuffer(writtenData.length);
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
            final Bytes readBytes = db.readBytes(writtenData.length);
            for (int i = 0; i < writtenData.length; i++) {
                assertEquals(writtenData[i], readBytes.getByte(i));
            }
            // read subset into Bytes and check all data is valid
            if (writtenData.length > 3) {
                db.reset();
                // read 1 byte, so we are doing a starting not at 0
                db.readByte();
                // read length -2 so subset
                final Bytes readBytes2 = db.readBytes(writtenData.length - 2);
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
