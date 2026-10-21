// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.grpc.GrpcCompression;
import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
}
