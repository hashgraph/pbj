// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class WritableMessageDigestTest {

    private static MessageDigest buildDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void basicTest() {
        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);

        assertEquals(Long.MAX_VALUE, wmd.capacity());
        assertEquals(0, wmd.position());
        assertEquals(Long.MAX_VALUE, wmd.limit());
    }

    @ParameterizedTest
    @ValueSource(bytes = {(byte) -128, (byte) -63, (byte) 0, (byte) 1, (byte) 65, (byte) 127})
    void testWriteByte(byte b) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(b);

        final WritableMessageDigest wmd = new WritableMessageDigest(buildDigest());
        wmd.writeByte(b);

        assertArrayEquals(ctrlDigest.digest(), wmd.digest());
    }

    private static final byte[] FULL_RANGE_ARRAY = new byte[256];

    static {
        for (int i = -128; i < 128; i++) {
            FULL_RANGE_ARRAY[i + 128] = (byte) i;
        }
    }

    private static Stream<byte[]> provideByteArrays() {
        return Stream.of(
                new byte[] {},
                new byte[] {0},
                new byte[] {66, 77},
                new byte[] {(byte) -128, (byte) -63, (byte) 0, (byte) 1, (byte) 65, (byte) 127},
                FULL_RANGE_ARRAY);
    }

    private static Stream<Arguments> provideByteArrayArguments() {
        return provideByteArrays().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("provideByteArrays")
    void testWriteByteArray(byte[] array) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array);

        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes(array);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }

    private static Stream<Arguments> provideByteArrayWithOffsetAndLengthArguments() {
        return provideByteArrays()
                .map(ba -> Arguments.of(
                        ba, ba.length > 1 ? 1 : 0, ba.length > 2 ? ba.length / 2 : (ba.length > 0 ? 1 : 0)));
    }

    @ParameterizedTest
    @MethodSource("provideByteArrayWithOffsetAndLengthArguments")
    void testWriteByteArrayWithOffsetAndLength(byte[] array, int offset, int length) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array, offset, length);

        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes(array, offset, length);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }

    @ParameterizedTest
    @MethodSource("provideByteArrays")
    void testWriteByteBuffer(byte[] array) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array);

        final ByteBuffer testBB = ByteBuffer.wrap(array);
        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes(testBB);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }

    @ParameterizedTest
    @MethodSource("provideByteArrays")
    void testWriteDirectByteBuffer(byte[] array) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array);

        final ByteBuffer testBB = ByteBuffer.allocateDirect(array.length);
        testBB.put(array);
        testBB.flip();
        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes(testBB);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }

    @ParameterizedTest
    @MethodSource("provideByteArrays")
    void testWriteBufferedData(byte[] array) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array);

        final BufferedData bd = BufferedData.wrap(array);
        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes(bd);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }

    @ParameterizedTest
    @MethodSource("provideByteArrays")
    void testWriteRandomAccessData(byte[] array) {
        final MessageDigest ctrlDigest = buildDigest();
        ctrlDigest.update(array);

        final Bytes bytes = Bytes.wrap(array);
        final MessageDigest testDigest = buildDigest();
        final WritableMessageDigest wmd = new WritableMessageDigest(testDigest);
        wmd.writeBytes((RandomAccessData) bytes);

        assertArrayEquals(ctrlDigest.digest(), testDigest.digest());
    }
}
