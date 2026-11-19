// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link ProtoArrayWriterTools#writeVarInt} writes the same bytes as the unrolled writer and never past the end. */
final class ProtoArrayWriterToolsVarIntTest {
    private static final byte UNTOUCHED = (byte) 0xA5;

    /** Values of every varint length, 1 to 10 bytes, including both ends of each length. */
    static Stream<Long> values() {
        final List<Long> values = new ArrayList<>();
        final Random random = new Random(20261029L);
        for (int bits = 7; bits <= 63; bits += 7) {
            final long max = (1L << bits) - 1;
            values.add(max);
            values.add(max + 1);
            for (int i = 0; i < 20; i++) {
                values.add(random.nextLong(max >>> 7, max) | (max >>> 7));
            }
        }
        values.addAll(List.of(0L, 1L, 127L, 128L, Long.MAX_VALUE, -1L, Long.MIN_VALUE, -128L));
        for (int i = 0; i < 50; i++) {
            values.add(random.nextLong());
        }
        return values.stream();
    }

    private static byte[] expected(final long value) {
        final byte[] out = new byte[10];
        final int length = ProtoArrayWriterTools.writeUnsignedVarInt(out, 0, value);
        return Arrays.copyOf(out, length);
    }

    @ParameterizedTest
    @MethodSource("values")
    void writesTheSameBytesAsTheUnrolledWriter(final long value) {
        final byte[] expected = expected(value);
        for (final int offset : new int[] {0, 3, 17}) {
            final byte[] out = new byte[offset + 32];
            Arrays.fill(out, UNTOUCHED);
            final int length = ProtoArrayWriterTools.writeVarInt(out, offset, out.length, value);
            assertEquals(expected.length, length);
            assertArrayEquals(expected, Arrays.copyOfRange(out, offset, offset + length));
            for (int i = 0; i < offset; i++) {
                assertEquals(UNTOUCHED, out[i], "wrote before offset");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("values")
    void neverWritesAtOrPastEnd(final long value) {
        final byte[] expected = expected(value);
        for (int slack = 0; slack <= Long.BYTES; slack++) {
            final int offset = 5;
            final int end = offset + expected.length + slack;
            final byte[] out = new byte[end + 16];
            Arrays.fill(out, UNTOUCHED);
            final int length = ProtoArrayWriterTools.writeVarInt(out, offset, end, value);
            assertEquals(expected.length, length);
            assertArrayEquals(expected, Arrays.copyOfRange(out, offset, offset + length));
            for (int i = end; i < out.length; i++) {
                assertEquals(UNTOUCHED, out[i], "wrote at " + i + ", end " + end + ", value " + value);
            }
        }
    }
}
