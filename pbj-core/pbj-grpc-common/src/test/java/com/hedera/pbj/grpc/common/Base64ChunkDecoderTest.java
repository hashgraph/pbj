// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.grpc.GrpcException;
import com.hedera.pbj.runtime.grpc.GrpcStatus;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class Base64ChunkDecoderTest {
    private static byte[] ascii(final String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void decodesAcrossEveryChunkBoundary() {
        final byte[] original = ascii("The quick brown fox jumps over the lazy dog");
        final byte[] encoded = Base64.getEncoder().encode(original);
        for (int split = 0; split <= encoded.length; split++) {
            final var decoder = new Base64ChunkDecoder();
            final var out = new ByteArrayOutputStream();
            out.writeBytes(decoder.decode(encoded, 0, split));
            out.writeBytes(decoder.decode(encoded, split, encoded.length - split));
            decoder.finish();
            assertArrayEquals(original, out.toByteArray(), "split at " + split);
        }
    }

    @Test
    void decodesPaddingInTheMiddleOfTheStream() {
        // Each frame may be encoded separately, so padding can appear between frames
        final byte[] encoded = ascii(Base64.getEncoder().encodeToString(ascii("a"))
                + Base64.getEncoder().encodeToString(ascii("bc"))
                + Base64.getEncoder().encodeToString(ascii("def")));
        for (int split = 0; split <= encoded.length; split++) {
            final var decoder = new Base64ChunkDecoder();
            final var out = new ByteArrayOutputStream();
            out.writeBytes(decoder.decode(encoded, 0, split));
            out.writeBytes(decoder.decode(encoded, split, encoded.length - split));
            decoder.finish();
            assertEquals("abcdef", out.toString(StandardCharsets.US_ASCII), "split at " + split);
        }
    }

    @Test
    void ignoresWhitespace() {
        final byte[] encoded = ascii("YW\r\nJj ZA==\n");
        final var decoder = new Base64ChunkDecoder();
        assertEquals("abcd", new String(decoder.decode(encoded, 0, encoded.length), StandardCharsets.US_ASCII));
        assertDoesNotThrow(decoder::finish);
    }

    @Test
    void rejectsInvalidCharacters() {
        final byte[] encoded = ascii("YW*j");
        final var e =
                assertThrows(GrpcException.class, () -> new Base64ChunkDecoder().decode(encoded, 0, encoded.length));
        assertEquals(GrpcStatus.INVALID_ARGUMENT, e.status());
    }

    @Test
    void rejectsATruncatedStream() {
        final byte[] encoded = ascii("YWJjZ");
        final var decoder = new Base64ChunkDecoder();
        assertEquals(3, decoder.decode(encoded, 0, encoded.length).length);
        final var e = assertThrows(GrpcException.class, decoder::finish);
        assertEquals(GrpcStatus.INVALID_ARGUMENT, e.status());
    }
}
