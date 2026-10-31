// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.ProtoArrayWriterTools;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link ReverseBuffer} writes varints of up to 8 bytes with one 8-byte store that also covers free space below the
 * varint. The bytes must match the reference writer for every length, and the output already written must be intact,
 * whether or not there are 8 bytes of room below the varint.
 */
final class ReverseBufferVarintTest {

    /** Values at both ends of every varint length, and negative ones, ten bytes long. */
    private static List<Long> values() {
        final List<Long> values = new ArrayList<>();
        for (int bits = 7; bits < 64; bits += 7) {
            values.add((1L << bits) - 1);
            values.add(1L << bits);
            values.add((1L << bits) | 0x55);
        }
        values.addAll(List.of(0L, 1L, 127L, 128L, Long.MAX_VALUE, -1L, Long.MIN_VALUE, -128L));
        final Random random = new Random(1019);
        for (int i = 0; i < 1000; i++) {
            values.add(random.nextLong() >>> random.nextInt(64));
        }
        return values;
    }

    private static byte[] reference(final long value) {
        final byte[] out = new byte[10];
        final int size = ProtoArrayWriterTools.writeUnsignedVarInt(out, 0, value);
        return Arrays.copyOf(out, size);
    }

    /** The trailing output written before the varint, which the varint's store must not touch. */
    private static final byte[] AFTER = {0x11, 0x22, 0x33};

    @Test
    void varintsMatchTheReferenceWriterWithAndWithoutRoomBelow() {
        for (final long value : values()) {
            final byte[] expected = reference(value);
            // capacity 16, minus what is written first: from no room at all below the varint to plenty
            for (int before = 0; before <= 13; before++) {
                for (final boolean unchecked : new boolean[] {false, true}) {
                    final ReverseBuffer buffer = new ReverseBuffer(16);
                    for (int i = 0; i < before; i++) {
                        buffer.writeByte(0x7E);
                    }
                    for (int i = AFTER.length - 1; i >= 0; i--) {
                        buffer.writeByte(AFTER[i]);
                    }
                    if (unchecked) {
                        buffer.ensure(10);
                        buffer.varintUnchecked(value);
                    } else {
                        buffer.varint(value);
                    }
                    final byte[] out = buffer.toBytes().toByteArray();
                    final String what =
                            "value " + value + " with " + before + " bytes before" + (unchecked ? ", unchecked" : "");
                    assertEquals(Bytes.wrap(expected), Bytes.wrap(Arrays.copyOf(out, expected.length)), what);
                    assertEquals(expected.length + AFTER.length + before, out.length, what);
                    assertEquals(
                            Bytes.wrap(AFTER),
                            Bytes.wrap(Arrays.copyOfRange(out, expected.length, expected.length + AFTER.length)),
                            what);
                }
            }
        }
    }

    @Test
    void lengthsOfNestedMessagesUseTheSameVarints() {
        for (final long length : new long[] {127, 128, 300, 16_383, 16_384, 2_097_152}) {
            final ReverseBuffer buffer = new ReverseBuffer(16);
            final int mark = buffer.mark();
            final byte[] body = new byte[(int) length];
            Arrays.fill(body, (byte) 0x42);
            buffer.bytes(Bytes.wrap(body));
            // bytes() writes the body then its length; check the length prefix is the reference varint
            final byte[] out = buffer.toBytes().toByteArray();
            final byte[] prefix = reference(length);
            assertEquals(Bytes.wrap(prefix), Bytes.wrap(Arrays.copyOf(out, prefix.length)), "length " + length);
            assertEquals(prefix.length + length, buffer.length() - mark);
        }
    }
}
