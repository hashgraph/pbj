// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void partialPayloadAllocationFollowsReceivedBytesInsteadOfTheAdvertisedLength() {
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
        assertTrue(payloadCapacity.get() <= 8192);
        assertTrue(deframer.isMidMessage());
        assertTrue(messages.isEmpty());
    }

    @Test
    void growsIncrementallyWithoutLosingBytesOrReusingCompletedPayloads() {
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
        assertFalse(deframer.isMidMessage());
    }
}
