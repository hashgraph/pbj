// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.PbjReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for {@link Codec#parse(com.hedera.pbj.runtime.io.ReadableSequentialData, boolean, boolean, int, int)},
 * {@link Codec#measure(com.hedera.pbj.runtime.io.ReadableSequentialData)}, and
 * {@link Codec#fastEquals(Object, com.hedera.pbj.runtime.io.ReadableSequentialData)}.
 * <p>
 * These methods wrap the caller's input in a {@link PbjReader}, which buffers up to 16KB ahead of what has
 * actually been consumed. The tests here use a buffer with more than 16KB of trailing data after the
 * encoded message, to confirm that buffering does not leak into the reported message length or the
 * original input's position.
 */
class CodecParseMethodTest {

    private static final LengthPrefixedCodec CODEC = new LengthPrefixedCodec();

    private static BufferedData messageWithTrailingFiller(final byte[] payload, final byte[] filler) {
        final byte[] backing = new byte[4 + payload.length + filler.length];
        final BufferedData writable = BufferedData.wrap(backing);
        writable.writeInt(payload.length);
        writable.writeBytes(payload);
        writable.writeBytes(filler);
        return BufferedData.wrap(backing);
    }

    private static byte[] filler(final int size) {
        final byte[] filler = new byte[size];
        Arrays.fill(filler, (byte) 0x7F);
        return filler;
    }

    @Test
    void measureIsNotInflatedByTrailingData() throws ParseException {
        final byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] filler = filler(20_000); // larger than PbjReader's 16KB internal buffer
        final BufferedData data = messageWithTrailingFiller(payload, filler);

        final int measured = CODEC.measure(data);

        assertEquals(4 + payload.length, measured);
    }

    @Test
    void measureLeavesInputPositionedAtEndOfMessage() throws ParseException {
        final byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] filler = filler(20_000);
        final BufferedData data = messageWithTrailingFiller(payload, filler);

        CODEC.measure(data);

        assertEquals(4 + payload.length, data.position());
        final byte[] remaining = new byte[filler.length];
        data.readBytes(remaining);
        assertArrayEquals(filler, remaining);
    }

    @Test
    void parseLeavesInputPositionedAtEndOfMessage() throws ParseException {
        final byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] filler = filler(20_000);
        final BufferedData data = messageWithTrailingFiller(payload, filler);

        final LengthPrefixed parsed = CODEC.parse(data);

        assertArrayEquals(payload, parsed.payload());
        assertEquals(4 + payload.length, data.position());
        final byte[] remaining = new byte[filler.length];
        data.readBytes(remaining);
        assertArrayEquals(filler, remaining);
    }

    @Test
    void fastEqualsLeavesInputPositionedAtEndOfMessage() throws ParseException {
        final byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] filler = filler(20_000);
        final BufferedData data = messageWithTrailingFiller(payload, filler);

        assertTrue(CODEC.fastEquals(new LengthPrefixed(payload), data));

        assertEquals(4 + payload.length, data.position());
    }

    private record LengthPrefixed(byte[] payload) {
        @Override
        public boolean equals(final Object obj) {
            return obj instanceof LengthPrefixed other && Arrays.equals(payload, other.payload);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(payload);
        }
    }

    private static final class LengthPrefixedCodec extends Codec<LengthPrefixed> {

        @NonNull
        @Override
        protected LengthPrefixed parseImpl(
                @NonNull final PbjReader input,
                final boolean strictMode,
                final boolean parseUnknownFields,
                final int maxDepth,
                final int maxSize)
                throws ParseException {
            final int length = input.readInt();
            final byte[] payload = new byte[length];
            if (input.readBytes(payload) != length) {
                throw new ParseException("Failed to read payload bytes");
            }
            return new LengthPrefixed(payload);
        }

        @Override
        protected void writeImpl(@NonNull final LengthPrefixed item, @NonNull final WritableSequentialData output)
                throws IOException {
            output.writeInt(item.payload().length);
            output.writeBytes(item.payload());
        }

        @Override
        public int measure(@NonNull final PbjReader input) throws ParseException {
            final long start = input.position();
            parse(input);
            return (int) (input.position() - start);
        }

        @Override
        public int measureRecord(final LengthPrefixed item) {
            return 4 + item.payload().length;
        }

        @Override
        public boolean fastEquals(@NonNull final LengthPrefixed item, @NonNull final PbjReader input)
                throws ParseException {
            return item.equals(parse(input));
        }

        @Override
        public LengthPrefixed getDefaultInstance() {
            return new LengthPrefixed(new byte[0]);
        }
    }
}
