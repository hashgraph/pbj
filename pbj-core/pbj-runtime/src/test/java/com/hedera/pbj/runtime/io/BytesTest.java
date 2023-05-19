package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class BytesTest {

    // ================================================================================================================
    // Verify EMPTY_BYTES

    @Test
    @DisplayName("Empty bytes have length of 0")
    void emptyBytes() {
        assertEquals(0, Bytes.EMPTY.length());
    }

    @Test
    @DisplayName("Empty bytes have no data")
    void emptyBytesThrowsOnGetByte() {
        assertThrows(BufferUnderflowException.class, () -> Bytes.EMPTY.getByte(0));
    }

    @Test
    @DisplayName("Empty bytes String is not null")
    void toStringWorks() {
        assertEquals("Bytes[]", Bytes.EMPTY.toString());
    }

    // ================================================================================================================
    // Verify Bytes.wrap(byte[])

    @Nested
    @DisplayName("Tests wrapping of byte arrays")
    final class ByteWrappingTest {
        static Stream<byte[]> byteArraysTestCases() {
            return Stream.of(
                    new byte[0],
                    new byte[] { 0 },
                    new byte[] { Byte.MIN_VALUE, -100, -66, -7, -1, 0, 1, 9, 12, 51, 101, Byte.MAX_VALUE }
            );
        }

        @Test
        @DisplayName("Wrapping a null byte array throws")
        void nullArrayThrows() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> Bytes.wrap((byte[]) null));
        }

        @Test
        @DisplayName("Getting a byte with a negative offset throws")
        void getByteWithNegativeOffsetThrows() {
            // Given a Bytes instance
            final RandomAccessData bytes = Bytes.wrap(new byte[] { 1, 2, 3, 4 });
            // When getting a byte with a negative offset
            // Then an IndexOutOfBoundsException is thrown
            assertThrows(IndexOutOfBoundsException.class, () -> bytes.getByte(-1));
        }

        @Test
        @DisplayName("Getting bytes as byte array")
        void toByteArray() {
            // Given a Bytes instance
            byte[] byteArray = {0, 1, 2, 3, 4};
            final Bytes bytes = Bytes.wrap(byteArray);
            assertArrayEquals(byteArray, bytes.toByteArray());
            assertNotEquals(byteArray, bytes.toByteArray());
        }

//        @Test
//        @DisplayName("Getting a byte with to large of an offset throws")
//        void getByteWithLargeOffsetThrows() {
//            // Given a Bytes instance
//            final RandomAccessData bytes = Bytes.wrap(new byte[] { 1, 2, 3, 4 });
//            // When getting a byte from an offset that is too large
//            // Then an IndexOutOfBoundsException is thrown
//            assertThrows(IndexOutOfBoundsException.class, () -> bytes.getByte(4));
//            assertThrows(IndexOutOfBoundsException.class, () -> bytes.getByte(5));
//            assertThrows(IndexOutOfBoundsException.class, () -> bytes.getByte(Integer.MAX_VALUE));
//        }

        @ParameterizedTest
        @MethodSource("byteArraysTestCases")
        @DisplayName("Wrapped byte arrays are used")
        void wrappedBytesAreUsed(final byte[] value) {
            // Given a byte array, when it is wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);

            // Then the length of the Bytes matches and each byte matches
            assertEquals(value.length, bytes1.length());
            for (int i = 0; i < value.length; i++) {
                assertEquals(value[i], bytes1.getByte(i));
            }
        }

        @ParameterizedTest
        @MethodSource("byteArraysTestCases")
        @DisplayName("Two instances wrapping the same bytes are equal")
        void equality(final byte[] value) {
            // Given two byte arrays with the same bytes, when wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);
            final RandomAccessData bytes2 = Bytes.wrap(Arrays.copyOf(value, value.length));
            // Then they have the same length
            assertEquals(bytes1.length(), bytes2.length());
            // And they have the same bytes
            for (int i = 0; i < value.length; i++) {
                assertEquals(bytes1.getByte(i), bytes2.getByte(i));
            }
            // And they are equal
            assertEquals(bytes1, bytes2);
            // And they have the same hash code
            assertEquals(bytes1.hashCode(), bytes2.hashCode());
        }

        @ParameterizedTest
        @MethodSource("byteArraysTestCases")
        @DisplayName("Two instances wrapping different bytes are not equal")
        void notEqual(final byte[] value) {
            // Given two byte arrays with different bytes, when wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);
            final RandomAccessData bytes2 = Bytes.wrap(new byte[]{ 1, 39, 28, 92 });
            // Then they have different lengths
            assertNotEquals(bytes1.length(), bytes2.length());
            // And they are not equal
            assertNotEquals(bytes1, bytes2);
            // And they have different hash codes
            assertNotEquals(bytes1.hashCode(), bytes2.hashCode());
        }
    }

    // ================================================================================================================
    // Verify Bytes.wrap(String)

    @Nested
    @DisplayName("Tests wrapping of strings")
    final class StringWrappingTest {
        static Stream<String> stringTestCases() {
            return Stream.of(
                    "",
                    "This is a test of the emergency broadcast system",
                    "Some crazy unicode characters here 🤪"
            );
        }

        @Test
        @DisplayName("Wrapping a null string throws")
        void wrapNullString() {
            //noinspection DataFlowIssue
            assertThrows(NullPointerException.class, () -> Bytes.wrap((String) null));
        }

        @ParameterizedTest
        @MethodSource("stringTestCases")
        @DisplayName("Wrapped strings are used")
        void wrappedStringsAreUsed(final String value) {
            // Given a String, when it is wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);

            // Then the length of the Bytes matches (when interpreted as UTF-8) and each byte matches
            final var expected = value.getBytes(StandardCharsets.UTF_8);
            assertEquals(expected.length, bytes1.length());
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], bytes1.getByte(i));
            }
        }

        @ParameterizedTest
        @MethodSource("stringTestCases")
        @DisplayName("Two instances wrapping the same Strings are equal")
        void equality(final String value) {
            // Given two byte arrays with the same bytes, when wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);
            final RandomAccessData bytes2 = Bytes.wrap(value);
            // Then they have the same length
            assertEquals(bytes1.length(), bytes2.length());
            // And they have the same bytes
            for (int i = 0; i < value.length(); i++) {
                assertEquals(bytes1.getByte(i), bytes2.getByte(i));
            }
            // And they are equal
            assertEquals(bytes1, bytes2);
            // And they have the same hash code
            assertEquals(bytes1.hashCode(), bytes2.hashCode());
        }

        @ParameterizedTest
        @MethodSource("stringTestCases")
        @DisplayName("Two instances wrapping different Strings are not equal")
        void notEqual(final String value) {
            // Given two byte arrays with different bytes, when wrapped
            final RandomAccessData bytes1 = Bytes.wrap(value);
            final RandomAccessData bytes2 = Bytes.wrap("Bogus String");
            // Then they have different lengths
            assertNotEquals(bytes1.length(), bytes2.length());
            // And they are not equal
            assertNotEquals(bytes1, bytes2);
            // And they have different hash codes
            assertNotEquals(bytes1.hashCode(), bytes2.hashCode());
        }
    }

    @Test
    @DisplayName("Get Unsigned Bytes")
    void getUnsignedBytes() {
        // Given a Bytes instance with bytes that are within the range of signed bytes and some that are
        // outside the range of signed bytes but within the range of unsigned bytes
        final RandomAccessData bytes = Bytes.wrap(new byte[] { 0b0000_0000, 0b0000_0001, (byte) 0b1000_0000, (byte) 0b1111_1111 });

        // Then reading them as unsigned bytes returns the expected values
        assertEquals(0, bytes.getUnsignedByte(0));
        assertEquals(1, bytes.getUnsignedByte(1));
        assertEquals(0b1000_0000, bytes.getUnsignedByte(2));
        assertEquals(0b1111_1111, bytes.getUnsignedByte(3));
    }


    // asUtf8String throws with null (no offset here? That's wierd. Should have offset, or we should have non-offset
    // versions of everything else Or at least "getBytes").

    // matches prefix....



//
//
//
//
//    static Stream<Byte> bytesTestCases() {
//        return Stream.of(Byte.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Byte.MAX_VALUE).map(Number::byteValue);
//    }
//
//    @ParameterizedTest
//    @MethodSource("bytesTestCases")
//    void byteTest(Byte value) {
//        final int length = Byte.BYTES;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeByte(value);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getByte(0));
//    }
//
//    static Stream<Integer> unsignedBytesTestCases() {
//        return Stream.of(0,1,9,51,101,127,128,255).map(Number::intValue);
//    }
//
//    @ParameterizedTest
//    @MethodSource("unsignedBytesTestCases")
//    void unsignedByteTest(Integer value) {
//        final int length = Byte.BYTES;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeUnsignedByte(value);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getUnsignedByte(0));
//    }
//
//    static Stream<Integer> intsTestCases() {
//        return Stream.of(Integer.MIN_VALUE,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE).map(Number::intValue);
//    }
//
//    @ParameterizedTest
//    @MethodSource("intsTestCases")
//    void intTest(Integer value) {
//        final int length = Integer.BYTES*2;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeInt(value);
//        db.writeInt(value, ByteOrder.LITTLE_ENDIAN);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getInt(0));
//        assertEquals(value, bytes.getInt(Integer.BYTES, ByteOrder.LITTLE_ENDIAN));
//    }
//
//    @ParameterizedTest
//    @MethodSource("intsTestCases")
//    void varIntTest(Integer value) {
//        DataBuffer db = DataBuffer.allocate(20, false);
//        db.writeVarInt(value, false);
//        final int varInt1Size = (int)db.position();
//        db.writeVarInt(value, true);
//        db.flip();
//        final Bytes bytes = db.readBytes((int)db.remaining());
//        assertEquals(value, bytes.getVarInt(0, false));
//        assertEquals(value, bytes.getVarInt(varInt1Size, true));
//    }
//
//    static Stream<Long> unsignedIntsTestCases() {
//        return Stream.of(0,1,9,51,127,Integer.MAX_VALUE*2L).map(Number::longValue);
//    }
//
//    @ParameterizedTest
//    @MethodSource("unsignedIntsTestCases")
//    void unsignedIntTest(Long value) {
//        final int length = Integer.BYTES*2;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeUnsignedInt(value);
//        db.writeUnsignedInt(value, ByteOrder.LITTLE_ENDIAN);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getUnsignedInt(0));
//        assertEquals(value, bytes.getUnsignedInt(Integer.BYTES, ByteOrder.LITTLE_ENDIAN));
//    }
//
//    static Stream<Long> longsTestCases() {
//        return Stream.of(Long.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Long.MAX_VALUE).map(Number::longValue);
//    }
//    @ParameterizedTest
//    @MethodSource("longsTestCases")
//    void longTest(Long value) {
//        final int length = Long.BYTES*2;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeLong(value);
//        db.writeLong(value, ByteOrder.LITTLE_ENDIAN);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getLong(0));
//        assertEquals(value, bytes.getLong(Long.BYTES, ByteOrder.LITTLE_ENDIAN));
//    }
//
//    @ParameterizedTest
//    @MethodSource("longsTestCases")
//    void varLongTest(Long value) {
//        DataBuffer db = DataBuffer.allocate(20, false);
//        db.writeVarLong(value, false);
//        final int varInt1Size = (int)db.position();
//        db.writeVarLong(value, true);
//        db.flip();
//        final Bytes bytes = db.readBytes((int)db.remaining());
//        assertEquals(value, bytes.getVarLong(0, false));
//        assertEquals(value, bytes.getVarLong(varInt1Size, true));
//    }
//
//    static Stream<Float> floatsTestCases() {
//        return Stream.of(Float.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Float.MAX_VALUE).map(Number::floatValue);
//    }
//    @ParameterizedTest
//    @MethodSource("floatsTestCases")
//    void floatTest(Float value) {
//        final int length = Float.BYTES*2;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeFloat(value);
//        db.writeFloat(value, ByteOrder.LITTLE_ENDIAN);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getFloat(0));
//        assertEquals(value, bytes.getFloat(Float.BYTES, ByteOrder.LITTLE_ENDIAN));
//    }
//
//    static Stream<Double> doublesTestCases() {
//        return Stream.of(Double.MIN_VALUE, Integer.MIN_VALUE-1L,-100,-66,-7,-1,0,1,9,51,101,Integer.MAX_VALUE+1L,Double.MAX_VALUE).map(Number::doubleValue);
//    }
//
//    @ParameterizedTest
//    @MethodSource("doublesTestCases")
//    void doubleTest(Double value) {
//        final int length = Double.BYTES * 2;
//        DataBuffer db = DataBuffer.allocate(length, false);
//        db.writeDouble(value);
//        db.writeDouble(value, ByteOrder.LITTLE_ENDIAN);
//        db.reset();
//        final Bytes bytes = db.readBytes(length);
//        assertEquals(value, bytes.getDouble(0));
//        assertEquals(value, bytes.getDouble(Double.BYTES, ByteOrder.LITTLE_ENDIAN));
//    }

    @Test
    void matchesPrefixByteArrayTest() {
        RandomAccessData primary = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        assertTrue(primary.matchesPrefix(new byte[]{0x01}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,}));
        assertTrue(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09}));

        assertFalse(primary.matchesPrefix(new byte[]{0x02}));
        assertFalse(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x02}));
        assertFalse(primary.matchesPrefix(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00}));
    }

    @Test
    void matchesPrefixEmpty_issue37() {
        final var bytes1 = Bytes.wrap(new byte[0]);
        final var bytes2 = Bytes.wrap(new byte[0]);
        assertTrue(bytes1.matchesPrefix(bytes2));
    }

    @Test
    void matchesPrefixBytesTest() {
        RandomAccessData primary = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        RandomAccessData prefixGood1 = Bytes.wrap(new byte[]{0x01});
        assertTrue(primary.matchesPrefix(prefixGood1));
        RandomAccessData prefixGood2 = Bytes.wrap(new byte[]{0x01,0x02});
        assertTrue(primary.matchesPrefix(prefixGood2));
        RandomAccessData prefixGood3 = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,});
        assertTrue(primary.matchesPrefix(prefixGood3));
        RandomAccessData prefixGood4 = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09});
        assertTrue(primary.matchesPrefix(prefixGood4));

        RandomAccessData prefixBad1 = Bytes.wrap(new byte[]{0x02});
        assertFalse(primary.matchesPrefix(prefixBad1));
        RandomAccessData prefixBad2 = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x02});
        assertFalse(primary.matchesPrefix(prefixBad2));
        RandomAccessData prefixBad3 = Bytes.wrap(new byte[]{0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x00});
        assertFalse(primary.matchesPrefix(prefixBad3));
    }
}
