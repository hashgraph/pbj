// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.grpc.common.compression.ZstdGrpcTransformer;
import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GrpcDeframerTest {
    private final List<Bytes> messages = new ArrayList<>();

    private GrpcDeframer deframer(final int maxMessageSizeBytes) {
        return new GrpcDeframer(
                maxMessageSizeBytes, GrpcCompression.getDecompressor(GrpcCompression.IDENTITY), messages::add);
    }

    private static Bytes text(final String s) {
        return Bytes.wrap(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] concat(final byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    @Test
    void readsMessagesSplitAtEveryPosition() {
        final byte[] stream = concat(
                GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("hello")),
                GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, Bytes.EMPTY),
                GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("world!")));
        for (int split = 0; split <= stream.length; split++) {
            messages.clear();
            final GrpcDeframer deframer = deframer(100);
            deframer.add(stream, 0, split);
            deframer.add(stream, split, stream.length - split);
            assertEquals(List.of(text("hello"), Bytes.EMPTY, text("world!")), messages, "split at " + split);
            assertFalse(deframer.isMidMessage());
        }
    }

    @Test
    void readsOneByteAtATime() {
        final byte[] stream = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("abc"));
        final GrpcDeframer deframer = deframer(100);
        for (int i = 0; i < stream.length; i++) {
            deframer.add(stream, i, 1);
            assertEquals(i < stream.length - 1, deframer.isMidMessage());
        }
        assertEquals(List.of(text("abc")), messages);
    }

    @Test
    void decompressesCompressedMessages() {
        final byte[] frame = GrpcFramer.frameMessage(text("compress me"), "gzip");
        assertEquals(GrpcFramer.COMPRESSED_FLAG, frame[0]);
        final GrpcDeframer deframer =
                new GrpcDeframer(100, GrpcCompression.determineDecompressor(List.of("gzip")), messages::add);
        deframer.add(frame, 0, frame.length);
        assertEquals(List.of(text("compress me")), messages);
    }

    @Test
    void rejectsMessagesLargerThanTheMaximum() {
        final byte[] frame = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("12345"));
        final GrpcException e =
                assertThrows(GrpcException.class, () -> deframer(4).add(frame, 0, frame.length));
        assertEquals(GrpcStatus.INVALID_ARGUMENT, e.status());
    }

    @Test
    void rejectsUnknownCompressionFlags() {
        final byte[] frame = GrpcFramer.frame(2, text("x"));
        final GrpcException e =
                assertThrows(GrpcException.class, () -> deframer(100).add(frame, 0, frame.length));
        assertEquals(GrpcStatus.UNIMPLEMENTED, e.status());
    }

    @Test
    void resetDiscardsAPartialMessage() {
        final byte[] frame = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("partial"));
        final GrpcDeframer deframer = deframer(100);
        deframer.add(frame, 0, 7);
        assertTrue(deframer.isMidMessage());
        deframer.reset();
        assertFalse(deframer.isMidMessage());
        final byte[] next = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, text("next"));
        deframer.add(next, 0, next.length);
        assertEquals(List.of(text("next")), messages);
    }

    @Test
    void framesWithBigEndianLength() {
        final byte[] payload = new byte[0x010203];
        final byte[] frame = GrpcFramer.frame(GrpcFramer.TRAILERS_FLAG, Bytes.wrap(payload));
        assertArrayEquals(new byte[] {(byte) 0x80, 0x00, 0x01, 0x02, 0x03}, java.util.Arrays.copyOf(frame, 5));
        assertEquals(5 + payload.length, frame.length);
    }

    @Test
    void enforcesTheDecodedMessageLimitForGzipAndZstd() {
        final Bytes payload = Bytes.wrap(new byte[8 * 1024 * 1024]);
        final ZstdGrpcTransformer zstd = new ZstdGrpcTransformer();
        for (final GrpcCompression.GrpcTransformer transformer :
                List.of((GrpcCompression.GrpcTransformer) GrpcCompression.getDecompressor("gzip"), zstd)) {
            final byte[] frame = GrpcFramer.frame(GrpcFramer.COMPRESSED_FLAG, transformer.compress(payload));
            assertTrue(frame.length < 10 * 1024);
            final GrpcDeframer deframer = new GrpcDeframer(10 * 1024, transformer, messages::add);
            final GrpcException failure = assertThrows(GrpcException.class, () -> deframer.add(frame, 0, frame.length));
            assertEquals(GrpcStatus.RESOURCE_EXHAUSTED, failure.status());
            assertTrue(messages.isEmpty());
        }
    }

    @Test
    void rejectsTheCompressedFlagWithIdentityIncludingAnEmptyPayload() {
        for (final Bytes payload : List.of(Bytes.EMPTY, text("x"))) {
            final byte[] frame = GrpcFramer.frame(GrpcFramer.COMPRESSED_FLAG, payload);
            final GrpcException failure =
                    assertThrows(GrpcException.class, () -> deframer(100).add(frame, 0, frame.length));
            assertEquals(GrpcStatus.INTERNAL, failure.status());
        }
        assertTrue(messages.isEmpty());
    }

    @Test
    void headerOnlyDoesNotAllocateAPayloadArray() throws ReflectiveOperationException {
        final int declaredLength = 4 * 1024 * 1024;
        final byte[] prefix = ByteBuffer.allocate(GrpcDeframer.PREFIX_LENGTH)
                .put((byte) 0)
                .putInt(declaredLength)
                .array();
        final GrpcDeframer deframer = deframer(declaredLength);
        final var payloadField = GrpcDeframer.class.getDeclaredField("entityBytes");
        payloadField.setAccessible(true);
        for (int position = 0; position < prefix.length; position++) {
            deframer.add(prefix, position, 1);
            assertNull(payloadField.get(deframer));
        }
        deframer.add(new byte[0], 0, 0);
        assertNull(payloadField.get(deframer));
        assertTrue(deframer.isMidMessage());
        assertTrue(messages.isEmpty());
        deframer.reset();
        assertNull(payloadField.get(deframer));
        assertFalse(deframer.isMidMessage());
    }

    @Test
    void firstPayloadByteAllocatesTheDeclaredSize() {
        final int declaredLength = 4 * 1024 * 1024;
        final byte[] prefix =
                ByteBuffer.allocate(5).put((byte) 0).putInt(declaredLength).array();
        final GrpcDeframer deframer = deframer(declaredLength);
        deframer.add(prefix, 0, prefix.length);
        assertTrue(deframer.isMidMessage());
        final AtomicInteger payloadCapacity = new AtomicInteger();
        deframer.add(new GrpcDeframer.Input() {
            private boolean available = true;

            @Override
            public int available() {
                return available ? 1 : 0;
            }

            @Override
            public int read() {
                throw new AssertionError("Payload should use the bulk read");
            }

            @Override
            public void read(final byte[] destination, final int offset, final int length) {
                payloadCapacity.set(destination.length);
                destination[offset] = 42;
                available = false;
            }
        });
        assertEquals(declaredLength, payloadCapacity.get());
        assertTrue(deframer.isMidMessage());
        assertTrue(messages.isEmpty());
    }

    @Test
    void fragmentedPayloadsUseOneArrayOfTheirDeclaredSize() {
        for (final int length : new int[] {1, 8192, 65536, 65537, 2 * 1024 * 1024}) {
            assertSinglePayloadArray(length, 997);
        }
        assertSinglePayloadArray(256 * 1024, 192 * 1024);
    }

    /**
     * Feeds one message in chunks, checks that every read uses the same exact-size array, and verifies its contents.
     */
    private static void assertSinglePayloadArray(final int payloadLength, final int chunkLength) {
        final byte[] payload = new byte[payloadLength];
        new Random(payloadLength).nextBytes(payload);
        final List<Bytes> received = new ArrayList<>();
        final GrpcDeframer deframer = new GrpcDeframer(payloadLength, (flag, bytes) -> received.add(bytes));
        final byte[] prefix = ByteBuffer.allocate(GrpcDeframer.PREFIX_LENGTH)
                .put((byte) 0)
                .putInt(payloadLength)
                .array();
        deframer.add(prefix, 0, prefix.length);

        final AtomicReference<byte[]> payloadArray = new AtomicReference<>();
        for (int position = 0; position < payloadLength; position += chunkLength) {
            final int start = position;
            final int length = Math.min(chunkLength, payloadLength - position);
            deframer.add(new GrpcDeframer.Input() {
                private int consumed = 0;

                @Override
                public int available() {
                    return length - consumed;
                }

                @Override
                public int read() {
                    throw new AssertionError("Payload should use the bulk read");
                }

                @Override
                public void read(final byte[] destination, final int offset, final int count) {
                    if (payloadArray.get() == null) {
                        payloadArray.set(destination);
                        assertEquals(payloadLength, destination.length);
                    } else {
                        assertSame(payloadArray.get(), destination);
                    }
                    System.arraycopy(payload, start + consumed, destination, offset, count);
                    consumed += count;
                }
            });
        }
        assertEquals(List.of(Bytes.wrap(payload)), received);
        assertFalse(deframer.isMidMessage());
    }

    @Test
    void readsFragmentedPayloadsWithoutLosingBytesOrReusingCompletedArrays() {
        final byte[] payload = new byte[256 * 1024 + 17];
        new Random(12345).nextBytes(payload);
        final byte[] frame = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, Bytes.wrap(payload));
        final GrpcDeframer deframer = deframer(payload.length);
        for (int position = 0; position < frame.length; ) {
            final int chunk = Math.min(997, frame.length - position);
            deframer.add(frame, position, chunk);
            position += chunk;
        }
        assertEquals(List.of(Bytes.wrap(payload)), messages);
        Arrays.fill(frame, (byte) 0);
        assertEquals(Bytes.wrap(payload), messages.getFirst());
        final byte[] nextPayload = new byte[payload.length];
        new Random(54321).nextBytes(nextPayload);
        final byte[] nextFrame = GrpcFramer.frame(GrpcFramer.UNCOMPRESSED_FLAG, Bytes.wrap(nextPayload));
        deframer.add(nextFrame, 0, nextFrame.length);
        assertEquals(List.of(Bytes.wrap(payload), Bytes.wrap(nextPayload)), messages);
        assertFalse(deframer.isMidMessage());
    }
}
