package com.hedera.pbj.intergration.test;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.test.UncheckedThrowingFunction;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ParserNeverWrapsTest {

    private static class BytesWritableSequentialData implements WritableSequentialData {
        private final byte[] bytes;

        private long position = 0;

        private long limit;

        public BytesWritableSequentialData(byte[] bytes) {
            this.bytes = bytes;
            this.limit = bytes.length;
        }

        @Override
        public long capacity() {
            return bytes.length;
        }

        @Override
        public long position() {
            return position;
        }

        @Override
        public long limit() {
            return limit;
        }

        @Override
        public void limit(long limit) {
            this.limit = limit;
        }

        @Override
        public long skip(long count) {
            // This doesn't matter in this test
            position += count;
            return count;
        }

        @Override
        public void writeByte(byte b) throws DataAccessException {
            bytes[(int) position++] = b;
        }
    }

    private static record WrapTestData<T>(
            Supplier<WritableSequentialData> wSeq,
            Function<Codec<T>, T> parser,
            Runnable resetter,
            Supplier<byte[]> getter,
            BiConsumer<Integer, byte[]> setter
    ) {
        static <T> WrapTestData<T> createByteArrayBufferedData(int size) {
            // The current implementation creates ByteArrayBufferedData:
            final BufferedData seq = BufferedData.allocate(size);
            return new WrapTestData<T>(
                    () -> seq,
                    new UncheckedThrowingFunction<>(codec -> codec.parse(seq)),
                    seq::reset,
                    () -> seq.getBytes(0, size).toByteArray(),
                    (pos, bytes) -> {
                        seq.position(pos);
                        seq.writeBytes(bytes);
                    }
            );
        }

        static <T> WrapTestData<T> createDirectBufferedData(int size) {
            final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
            final BufferedData seq = BufferedData.wrap(byteBuffer);
            return new WrapTestData<T>(
                    () -> seq,
                    new UncheckedThrowingFunction<>(codec -> codec.parse(seq)),
                    seq::reset,
                    () -> seq.getBytes(0, size).toByteArray(),
                    (pos, bytes) -> {
                        seq.position(pos);
                        seq.writeBytes(bytes);
                    }
            );
        }

        static <T> WrapTestData<T> createBytes(int size) {
            final byte[] byteArray = new byte[size];
            final Bytes bytes = Bytes.wrap(byteArray);

            final BytesWritableSequentialData seq = new BytesWritableSequentialData(byteArray);

            return new WrapTestData<T>(
                    () -> seq, // Only write once.
                    new UncheckedThrowingFunction<>(codec -> codec.parse(bytes)),
                    () -> {}, // reset() not supported and not needed for Bytes
                    () -> byteArray, // return the actual array this Bytes object is wrapping
                    (pos, arr) -> {
                        for (int i = 0; i < arr.length; i++) {
                            byteArray[pos + i] = arr[i];
                        }
                    }
            );
        }
    }

    static Stream<Function<Integer, WrapTestData>> provideWrapTestArguments() {
        return Stream.of(
                WrapTestData::createByteArrayBufferedData,
                WrapTestData::createDirectBufferedData,
                WrapTestData::createBytes
        );
    }

    @ParameterizedTest
    @MethodSource("provideWrapTestArguments")
    void testNoWrap(Function<Integer, WrapTestData> config) throws IOException {
        final String randomString = UUID.randomUUID().toString();
        final byte[] originalBytes = randomString.getBytes(StandardCharsets.UTF_8);

        final MessageWithBytes originalMessage = MessageWithBytes.newBuilder()
                .bytesField(Bytes.wrap(originalBytes))
                .build();
        final int size = MessageWithBytes.PROTOBUF.measureRecord(originalMessage);
        final WrapTestData<MessageWithBytes> data = config.apply(size);
        MessageWithBytes.PROTOBUF.write(originalMessage, data.wSeq().get());

        int index = search(data.getter().get(), originalBytes);

        assertNotEquals(-1, index, "Cannot find original bytes in the BufferedData");

        data.resetter().run();
        MessageWithBytes msg1 = data.parser().apply(MessageWithBytes.PROTOBUF);

        // Make sure we parsed the original bytes the first time
        assertArrayEquals(originalBytes, msg1.bytesField().toByteArray());

        // Now modify the BufferedData and replace the original bytes completely
        final String anotherRandomString = UUID.randomUUID().toString();
        assertNotEquals(randomString, anotherRandomString);
        final byte[] modifiedBytes = anotherRandomString.getBytes(StandardCharsets.UTF_8);

        // We assume that the UUID uses 7-bit ASCII characters,
        // so the size of the bytes array is the same for any random UUID.
        assertEquals(originalBytes.length, modifiedBytes.length);
        assertEquals(size - modifiedBytes.length, index);

        data.setter().accept(index, modifiedBytes);

        data.resetter().run();
        MessageWithBytes msg2 = data.parser().apply(MessageWithBytes.PROTOBUF);

        // Make sure we parsed the new bytes
        assertArrayEquals(modifiedBytes, msg2.bytesField().toByteArray());

        // Now check if the msg1 that we parsed from the same BufferedData before the modification
        // still has the very original bytes, and not the modified bytes.
        // In other words, ensure that the PROTOBUF.parse() didn't just wrap
        // the original bytes array from the BufferedData, but instead created a copy.
        assertArrayEquals(originalBytes, msg1.bytesField().toByteArray());
    }

    /** A brute-force search implementation because it's just a test. */
    private static int search(byte[] bytes, byte[] pattern) {
        for (int i = 0; i < bytes.length - pattern.length + 1; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    break;
                }
                if (j == pattern.length - 1) {
                    return i;
                }
            }
        }
        return -1;
    }
}
