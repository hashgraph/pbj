package com.hedera.pbj.runtime.io.buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

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
        assertEquals("", Bytes.EMPTY.toString());
    }

    @Test
    @DisplayName("Non bytes String is not null")
    void toStringWorks1() {
        String s = new String("This is my String!");
        assertEquals("54686973206973206d7920537472696e6721", Bytes.wrap(s).toString());
    }

    @Test
    void testReplicate() {
        byte[] arr = new byte[] { 0, 1, 2 };

        // Only wrap the last two elements:
        Bytes bytes = Bytes.wrap(arr, 1, 2);
        Bytes replicatedBytes = bytes.replicate();

        assertEquals(2, replicatedBytes.length());
        assertEquals(arr[1], replicatedBytes.getByte(0));
        assertEquals(arr[2], replicatedBytes.getByte(1));

        // Also verify if the replica is really a replica and not a view
        arr[0] = 100;
        arr[1] = 90;
        arr[2] = 80;

        // First check if the original wrapped Bytes object sees the changes and keeps its length intact:
        assertEquals(2, bytes.length());
        assertEquals(90, bytes.getByte(0));
        assertEquals(80, bytes.getByte(1));

        // Now ensure the replica still has the original values defined at the very top
        assertEquals(1, replicatedBytes.getByte(0));
        assertEquals(2, replicatedBytes.getByte(1));
    }

    // ================================================================================================================
    // Verify Bytes.wrap(byte[])

    @SuppressWarnings("CommentedOutCode")
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
            final byte[] byteArray = {0, 1, 2, 3, 4};
            final Bytes bytes = Bytes.wrap(byteArray);
            assertArrayEquals(byteArray, bytes.toByteArray());
            assertNotEquals(byteArray, bytes.toByteArray());
        }

        @Test
        @DisplayName("Getting bytes as byte array offset zero, partial")
        void toByteArrayNon0Partial() {
            // Given a Bytes instance
            final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4};
            final Bytes bytes = Bytes.wrap(byteArray, 10, 5);
            final byte[] res = new byte[] {0, 1, 2, 3};
            assertArrayEquals(res, bytes.toByteArray(0, 4));
            assertNotEquals(byteArray, bytes.toByteArray(0, 4));
        }

        @Test
        @DisplayName("Getting bytes as byte array offset not zero")
        void toByteArrayNon0() {
            // Given a Bytes instance
            final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4};
            final Bytes bytes = Bytes.wrap(byteArray, 10, 5);
            final byte[] res = new byte[] {0, 1, 2, 3, 4};
            assertArrayEquals(res, bytes.toByteArray());
            assertNotEquals(byteArray, bytes.toByteArray(0, 5));
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
        final RandomAccessData bytes = Bytes.wrap(new byte[]{0b0000_0000, 0b0000_0001, (byte) 0b1000_0000, (byte) 0b1111_1111});
        // Then reading them as unsigned bytes returns the expected values
        assertEquals(0, bytes.getUnsignedByte(0));
        assertEquals(1, bytes.getUnsignedByte(1));
        assertEquals(0b1000_0000, bytes.getUnsignedByte(2));
        assertEquals(0b1111_1111, bytes.getUnsignedByte(3));
    }

    @Test
    @DisplayName("Write to OutputStream")
    void writeToOutputStream() throws IOException {
        byte[] byteArray = {0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);
        byte[] res = new byte[6];
        try (BufferedOutputStream out = new BufferedOutputStream(new ByteArrayOutputStream())) {
            bytes.writeTo(out);
            bytes.getBytes(0, res, 0, 6);
        }
        assertArrayEquals(byteArray, res);
    }

    @Test
    @DisplayName("Write to OutputStream non 0 offset")
    void writeToOutputStreamNo0Offs() throws IOException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);
        byte[] res = new byte[6];
        try (BufferedOutputStream out = new BufferedOutputStream(new ByteArrayOutputStream())) {
            bytes.writeTo(out);
            bytes.getBytes(0, res, 0, 6);
        }
        byte[] exp = {0, 1, 2, 3, 4, 5};
        assertArrayEquals(exp, res);
    }

    @Test
    @DisplayName("Write to OutputStream non 0 offset partial")
    void writeToOutputStreamNo0OffsPartial() throws IOException {
        byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);
        byte[] res = new byte[5];
        try (BufferedOutputStream out = new BufferedOutputStream(new ByteArrayOutputStream())) {
            bytes.writeTo(out, 0, 5);
            bytes.getBytes(0, res, 0, 5);
        }
        byte[] comp = {0, 1, 2, 3, 4};
        assertArrayEquals(comp, res);
    }
    @Test
    @DisplayName("Write to OutputStream")
    void writeToWritableSequentialData() throws IOException {
        byte[] byteArray = {0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);
        byte[] res = new byte[6];
        try (WritableStreamingData out = new WritableStreamingData(new ByteArrayOutputStream())) {
            bytes.writeTo(out);
            bytes.getBytes(0, res, 0, 6);
        }
        assertArrayEquals(byteArray, res);
    }

    @Test
    @DisplayName("Write to OutputStream non 0 offset")
    void writeToWritableSequentialDataNo0Offs() throws IOException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);
        byte[] res = new byte[6];
        try (WritableStreamingData out = new WritableStreamingData(new ByteArrayOutputStream())) {
            bytes.writeTo(out);
            bytes.getBytes(0, res, 0, 6);
        }
        byte[] exp = {0, 1, 2, 3, 4, 5};
        assertArrayEquals(exp, res);
    }

    @Test
    @DisplayName("Write to OutputStream non 0 offset partial")
    void writeToWritableSequentialData0OffsPartial() throws IOException {
        byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);
        byte[] res = new byte[5];
        try (WritableStreamingData out = new WritableStreamingData(new ByteArrayOutputStream())) {
            bytes.writeTo(out, 0, 5);
            bytes.getBytes(0, res, 0, 5);
        }
        byte[] comp = {0, 1, 2, 3, 4};
        assertArrayEquals(comp, res);
    }

    @Test
    @DisplayName("Write to MessageDigest")
    void writeToMessageDigest() throws NoSuchAlgorithmException {
        byte[] byteArray = {0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);
        byte[] res;

        MessageDigest md = MessageDigest.getInstance("MD5");
        bytes.writeTo(md);
        res = md.digest();
        byte[] exp = {-47, 90, -27, 57, 49, -120, 15, -41, -73, 36, -35, 120, -120, -76, -76, -19};
        assertArrayEquals(exp, res);
    }

    @Test
    @DisplayName("Write to MessageDigest no 0 Offset")
    void writeToMessageDigestNo0Offset() throws NoSuchAlgorithmException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);
        byte[] res;

        MessageDigest md = MessageDigest.getInstance("MD5");
        bytes.writeTo(md);
        res = md.digest();
        byte[] exp = {-47, 90, -27, 57, 49, -120, 15, -41, -73, 36, -35, 120, -120, -76, -76, -19};
        assertArrayEquals(exp, res);
    }

    @Test
    @DisplayName("Write to MessageDigest no 0 offset, partial")
    void writeToMessageDigestNo0OffsetPartial() throws NoSuchAlgorithmException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5, 6};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 7);
        byte[] res;

        MessageDigest md = MessageDigest.getInstance("MD5");
        bytes.writeTo(md, 0, 6);
        res = md.digest();
        byte[] exp = {-47, 90, -27, 57, 49, -120, 15, -41, -73, 36, -35, 120, -120, -76, -76, -19};
        assertArrayEquals(exp, res);
    }

    /**
     * Mock a Signature object for testing.
     *
     * @return a mock Signature object
     */
    private static Signature mockSignature() throws InvalidKeyException {
        final Signature signature = Mockito.mock(Signature.class);
        // Signature.update() throws unless we call initVerify() first
        signature.initVerify(Mockito.mock(PublicKey.class));
        Mockito.verify(signature).initVerify(Mockito.any(PublicKey.class));
        return signature;
    }

    @Test
    @DisplayName("Update Signature")
    void updateSignature() throws SignatureException, InvalidKeyException {
        byte[] byteArray = {0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);

        final Signature signature = mockSignature();
        bytes.updateSignature(signature);
        Mockito.verify(signature).update(byteArray, 0, 6);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Update Signature no 0 Offset")
    void updateSignatureNo0Offset() throws InvalidKeyException, SignatureException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);

        final Signature signature = mockSignature();
        bytes.updateSignature(signature);
        Mockito.verify(signature).update(byteArray, 10, 6);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Update Signature no 0 Offset, partial")
    void updateSignatureNo0OffsetPartial() throws InvalidKeyException, SignatureException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5, 9, 9, 9, 9, 9};
        final Bytes bytes = Bytes.wrap(byteArray, 5, 6);

        final Signature signature = mockSignature();
        bytes.updateSignature(signature, 1, 4);
        Mockito.verify(signature).update(byteArray, 6, 4);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Check updateSignature() index bounds")
    void updateSignatureBoundsChecks() throws InvalidKeyException {
        byte[] byteArray = {1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);

        final Signature signature = mockSignature();
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.updateSignature(signature, 3, 10));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.updateSignature(signature, 0, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.updateSignature(signature, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> bytes.updateSignature(signature, 0, -5));
        assertThrows(IllegalArgumentException.class, () -> bytes.updateSignature(signature, -1, 2));
        Mockito.verifyNoMoreInteractions(signature);
        assertDoesNotThrow(() -> bytes.updateSignature(signature, 0, 5));
        assertDoesNotThrow(() -> bytes.updateSignature(signature, 5, 0));
    }

    @Test
    @DisplayName("Verify Signature")
    void verifySignature() throws SignatureException, InvalidKeyException {
        byte[] byteArray = {0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);

        final Signature signature = mockSignature();
        bytes.verifySignature(signature);
        Mockito.verify(signature).verify(byteArray, 0, 6);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Verify Signature no 0 Offset")
    void verifySignatureNo0Offset() throws InvalidKeyException, SignatureException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray, 10, 6);

        final Signature signature = mockSignature();
        bytes.verifySignature(signature);
        Mockito.verify(signature).verify(byteArray, 10, 6);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Verify Signature no 0 Offset, partial")
    void verifySignatureNo0OffsetPartial() throws InvalidKeyException, SignatureException {
        final byte[] byteArray = {9, 9, 9, 9, 9, 0, 1, 2, 3, 4, 5, 9, 9, 9, 9, 9};
        final Bytes bytes = Bytes.wrap(byteArray, 5, 6);

        final Signature signature = mockSignature();
        bytes.verifySignature(signature, 1, 4);
        Mockito.verify(signature).verify(byteArray, 6, 4);
        Mockito.verifyNoMoreInteractions(signature);
    }

    @Test
    @DisplayName("Check verifySignature() index bounds")
    void verifySignatureBoundsChecks() throws InvalidKeyException {
        byte[] byteArray = {1, 2, 3, 4, 5};
        final Bytes bytes = Bytes.wrap(byteArray);

        final Signature signature = mockSignature();
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.verifySignature(signature, 3, 10));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.verifySignature(signature, 0, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> bytes.verifySignature(signature, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> bytes.verifySignature(signature, 0, -5));
        assertThrows(IllegalArgumentException.class, () -> bytes.verifySignature(signature, -1, 2));
        Mockito.verifyNoMoreInteractions(signature);
        assertDoesNotThrow(() -> bytes.verifySignature(signature, 0, 5));
        assertDoesNotThrow(() -> bytes.verifySignature(signature, 5, 0));
    }

    @Test
    @DisplayName("Tests the signature verification without a mock")
    void realSignatureTest() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        final Bytes bytes = Bytes.wrap(new byte[]{1, 2, 3, 4, 5});
        final KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        // sign the data
        final Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        bytes.updateSignature(signer);
        final Bytes signature = Bytes.wrap(signer.sign());
        // verify the signature
        final Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(keyPair.getPublic());
        bytes.updateSignature(verifier);
        assertTrue(signature.verifySignature(verifier));
        // test a bad signature
        final Signature verifier2 = Signature.getInstance("SHA256withRSA");
        verifier2.initVerify(keyPair.getPublic());
        Bytes.wrap(new byte[]{123, 1, 2, 3}).updateSignature(verifier2);
        assertFalse(signature.verifySignature(verifier2));
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
    void malformedVarTest() {
        final byte arr[] = new byte[11];
        Arrays.fill(arr, (byte) 255);
        Bytes bytes = Bytes.wrap(arr);
        assertThrows(DataEncodingException.class, () -> bytes.getVarLong(0, false));
        assertThrows(DataEncodingException.class, () -> bytes.getVarInt(0, false));
    }

    @Nested
    class BytesRandomAccessDataTest extends RandomAccessTestBase {
        @NonNull
        @Override
        protected ReadableSequentialData emptySequence() {
            return Bytes.EMPTY.toReadableSequentialData();
        }

        @NonNull
        @Override
        protected ReadableSequentialData fullyUsedSequence() {
            return new RandomAccessSequenceAdapter(Bytes.wrap(new byte[] {0, 1, 2, 4}), 4);
        }

        @NonNull
        @Override
        protected ReadableSequentialData sequence(@NonNull byte[] arr) {
            return Bytes.wrap(arr).toReadableSequentialData();
        }

        @NonNull
        @Override
        protected RandomAccessData randomAccessData(@NonNull byte[] bytes) {
            return Bytes.wrap(bytes);
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            "", "", 0
            "a", "", 1
            "", "a", -1
            "a", "a", 0
            "aa", "a", 1
            "a", "aa", -1
            "ab", "ba", 0
            "abc", "cab", 0
            """)
    @DisplayName("Comparing Bytes by length")
    void compareByLength(String arr1, String arr2, int expected) {
        var bytes1 = Bytes.wrap(arr1);
        var bytes2 = Bytes.wrap(arr2);
        assertEquals(expected, Bytes.SORT_BY_LENGTH.compare(bytes1, bytes2));
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("Comparing Bytes by unsignedValue")
    void compareByUnsignedBytes(byte[] arr1, byte[] arr2, int expected) {
        var bytes1 = Bytes.wrap(arr1);
        var bytes2 = Bytes.wrap(arr2);
        assertEquals(expected, Bytes.SORT_BY_UNSIGNED_VALUE.compare(bytes1, bytes2));
    }

    static Stream<Arguments> compareByUnsignedBytes() {
        return Stream.of(
                Arguments.of(new byte[0], new byte[0], 0),
                Arguments.of(new byte[0], new byte[]{1}, -1),
                Arguments.of(new byte[]{1}, new byte[0], 1),
                Arguments.of(new byte[]{1}, new byte[]{2}, -1),
                Arguments.of(new byte[]{2}, new byte[]{1}, 1),
                Arguments.of(new byte[]{-1}, new byte[]{2}, 253),
                Arguments.of(new byte[]{2}, new byte[]{-1}, -253),
                Arguments.of(new byte[]{-1}, new byte[]{-2}, 1),
                Arguments.of(new byte[]{-2}, new byte[]{-1}, -1),
                Arguments.of(new byte[]{-2, -1}, new byte[]{-2, -1}, 0),
                Arguments.of(new byte[]{-2}, new byte[]{-2, -1}, -1),
                Arguments.of(new byte[]{-2, -1}, new byte[]{-1, -2}, -1)
        );
    }

    @ParameterizedTest
    @MethodSource
    @DisplayName("Comparing Bytes by signedValue")
    void compareBySignedBytes(byte[] arr1, byte[] arr2, int expected) {
        var bytes1 = Bytes.wrap(arr1);
        var bytes2 = Bytes.wrap(arr2);
        assertEquals(expected, Bytes.SORT_BY_SIGNED_VALUE.compare(bytes1, bytes2));
    }

    static Stream<Arguments> compareBySignedBytes() {
        return Stream.of(
                Arguments.of(new byte[0], new byte[0], 0),
                Arguments.of(new byte[0], new byte[]{1}, -1),
                Arguments.of(new byte[]{1}, new byte[0], 1),
                Arguments.of(new byte[]{1}, new byte[]{2}, -1),
                Arguments.of(new byte[]{2}, new byte[]{1}, 1),
                Arguments.of(new byte[]{-1}, new byte[]{2}, -3),
                Arguments.of(new byte[]{2}, new byte[]{-1}, 3),
                Arguments.of(new byte[]{-1}, new byte[]{-2}, 1),
                Arguments.of(new byte[]{-2}, new byte[]{-1}, -1),
                Arguments.of(new byte[]{-2, -1}, new byte[]{-2, -1}, 0),
                Arguments.of(new byte[]{-2}, new byte[]{-2, -1}, -1),
                Arguments.of(new byte[]{-2, -1}, new byte[]{-1, -2}, -1)
        );
    }

    @Test
    @DisplayName("Appends two Bytes objects")
    void appendBytes() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes b2 = Bytes.wrap(new byte[]{4, 5, 6});
        Bytes appended = b1.append(b2);
        byte[] res = new byte[7];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6}, res);
    }

    @Test
    @DisplayName("Appends two Bytes objects, one empty")
    void appendEmptyBytes() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        Bytes appended = b1.append(Bytes.EMPTY);
        byte[] res = new byte[4];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3}, res);
    }

    @Test
    @DisplayName("Appends RandomAccessData")
    void appendRandomAccessData() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 1, 2, 3});
        RandomAccessData rad = BufferedData.wrap(new byte[]{4, 5, 6});
        Bytes appended = b1.append(rad);
        byte[] res = new byte[7];
        appended.getBytes(0, res);
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4, 5, 6}, res);
    }

    @Test
    @DisplayName("Changed toString")
    void changedToString() {
        Bytes b1 = Bytes.wrap(new byte[]{0, 0, (byte)0xFF});
        assertEquals("0000ff", b1.toString());
    }

    @Test
    @DisplayName("Changed toString2")
    void changedToString2() {
        Bytes b1 = Bytes.wrap(new byte[]{(byte)0x0f, 0, (byte)0x0a});
        assertEquals("0f000a", b1.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "ab", "abc", "abc123", "✅" })
    @DisplayName("Overridden asUtf8String")
    void asUtf8StringTest(final String value) {
        final Bytes bytes = Bytes.wrap(value.getBytes(StandardCharsets.UTF_8));
        assertThat(bytes.asUtf8String()).isEqualTo(value);
    }

    private <T> void testWriteToFromOffset(
            final T dst,
            final BiConsumer<Bytes, T> offset1Length1Writer,
            final Function<T, Integer> dstSizeGetter,
            final Function<T, Byte> dstByteAtZeroOffsetGetter) {
        // Wrap the {2, 3, 4} sub-array:
        final Bytes bytes = Bytes.wrap(new byte[] {1, 2, 3, 4, 5}, 1, 3);

        assertThat(dstSizeGetter.apply(dst)).isEqualTo(0);

        // Write the offset 1 length 1 to dst, i.e. write the element `3` from the wrapped sub-array
        offset1Length1Writer.accept(bytes, dst);

        assertThat(dstSizeGetter.apply(dst)).isEqualTo(1);
        assertThat(dstByteAtZeroOffsetGetter.apply(dst)).isEqualTo((byte) 3);
    }

    @Test
    void writeToByteBufferTest() {
        final ByteBuffer bb = ByteBuffer.allocate(1);

        testWriteToFromOffset(
                bb,
                (b, d) -> b.writeTo(d, 1, 1),
                ByteBuffer::position,
                d -> d.get(0));
    }

    @Test
    void writeToOutputStreamTest() {
        final List<Integer> data = new ArrayList<>();
        final OutputStream os = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                data.add(b);
            }
        };

        testWriteToFromOffset(
                os,
                (b, d) -> b.writeTo(d, 1, 1),
                d -> data.size(),
                d -> data.get(0).byteValue());
    }

    @Test
    void writeToWritableSequentialDataTest() {
        final BufferedData bd = BufferedData.allocate(1);

        testWriteToFromOffset(
                (WritableSequentialData) bd,
                (b, d) -> b.writeTo(d, 1, 1),
                d -> (int) bd.position(),
                d -> bd.getByte(0));
    }

    @Test
    void writeToMessageDigestDataTest() throws NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance("SHA-384");
        final AtomicInteger ai = new AtomicInteger();

        testWriteToFromOffset(
                md,
                (b, d) -> {
                    b.writeTo(d, 1, 1);
                    ai.set(md.digest()[0]);
                },
                d -> ai.get() == 0 ? 0 : 1,
                d -> (byte) (ai.get() + 121)
        );
    }
}
