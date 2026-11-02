// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.ProtoWriteBuffer.LengthReservation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The runtime behind generated message writers, with a minimal hand-written writer: length prefixes, the stack of open
 * messages, auto-close, packed fields, and the checks that make misuse throw.
 */
final class ProtoWriteBufferTest {

    /**
     * A writer of a message with a repeated bytes field 1 and a repeated nested message field 2 of its own type, like a
     * generated one, so tests can build any tree.
     */
    static final class NodeWriter extends MessageWriter {
        private NodeWriter child;
        private PackedIntWriter packed;
        private final int perTypeReserve;

        NodeWriter(final int perTypeReserve) {
            this.perTypeReserve = perTypeReserve;
        }

        NodeWriter start(final ProtoWriteBuffer out) {
            startTopLevel(out);
            return this;
        }

        NodeWriter payload(final byte[] value) {
            checkRepeatedField(1);
            out.tagBytes(0x0A, value, 0, value.length);
            return this;
        }

        NodeWriter addChild() {
            checkRepeatedField(2);
            if (child == null) {
                child = new NodeWriter(perTypeReserve);
            }
            openChild(child, perTypeReserve, 0x12);
            return child;
        }

        PackedIntWriter numbers() {
            checkField(3);
            if (packed == null) {
                packed = new PackedIntWriter(PackedIntWriter.INT32);
            }
            openPacked(packed, 0x1A);
            return packed;
        }

        NodeWriter single(final int value) {
            checkField(4);
            checkOneof(1L, "choice");
            out.tagVarint(0x20, value);
            return this;
        }

        NodeWriter other(final int value) {
            checkField(5);
            checkOneof(1L, "choice");
            out.tagVarint(0x28, value);
            return this;
        }
    }

    /** The expected encoding of a field: tag, varint length, then the bytes. */
    private static byte[] delimited(final int tag, final byte[] body) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        long length = body.length;
        while ((length & ~0x7FL) != 0) {
            out.write((int) ((length & 0x7F) | 0x80));
            length >>>= 7;
        }
        out.write((int) length);
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static byte[] concat(final byte[]... parts) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static byte[] bytes(final ProtoWriteBuffer buffer) {
        return Arrays.copyOf(buffer.array(), buffer.length());
    }

    /** Lengths around every varint size boundary, and one over 2 MB. */
    private static final int[] LENGTHS = {0, 1, 127, 128, 129, 16_383, 16_384, 16_385, 2_097_151, 2_097_152, 2_500_000};

    @ParameterizedTest
    @EnumSource(LengthReservation.class)
    void nestedLengthsAtEveryVarintBoundary(final LengthReservation reservation) {
        for (final int perType : new int[] {1, 2}) {
            for (final int length : LENGTHS) {
                final byte[] payload = new byte[length];
                Arrays.fill(payload, (byte) 7);
                // a nested message whose body is one payload field: its length is the payload's plus tag and length
                final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1, reservation);
                final NodeWriter root = new NodeWriter(perType).start(buffer);
                final NodeWriter child = root.addChild().payload(payload);
                final int childLength = child.end();
                root.end();
                final byte[] childBody = delimited(0x0A, payload);
                assertEquals(childBody.length, childLength);
                assertArrayEquals(delimited(0x12, childBody), bytes(buffer), "length " + length);
                // the child's range is its body
                assertArrayEquals(
                        childBody, Arrays.copyOfRange(buffer.array(), child.offset(), child.offset() + child.length()));
            }
        }
    }

    @Test
    void adaptiveReservationLearnsTheSizeOfEachField() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16, LengthReservation.ADAPTIVE);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        final byte[] big = new byte[300];
        // the first element needs a two-byte length where one was guessed; the rest are guessed right
        for (int i = 0; i < 10; i++) {
            root.addChild().payload(big);
        }
        root.end();
        assertEquals(1, buffer.shiftCount());
        final ProtoWriteBuffer oneByte = new ProtoWriteBuffer(16, LengthReservation.ONE_BYTE);
        final NodeWriter other = new NodeWriter(1).start(oneByte);
        for (int i = 0; i < 10; i++) {
            other.addChild().payload(big);
        }
        other.end();
        assertEquals(10, oneByte.shiftCount());
        assertArrayEquals(bytes(buffer), bytes(oneByte));
    }

    @Test
    void deepNestingBeyondTheInitialStack() {
        final int depth = 200;
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        final NodeWriter root = new NodeWriter(2).start(buffer);
        // each level is a fresh writer, so a chain of child writers 200 deep
        NodeWriter writer = root;
        for (int i = 0; i < depth; i++) {
            writer = writer.addChild();
        }
        writer.payload("leaf".getBytes(StandardCharsets.UTF_8));
        // ending the root ends every level inside it
        root.end();
        byte[] expected = delimited(0x0A, "leaf".getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < depth; i++) {
            expected = delimited(0x12, expected);
        }
        assertArrayEquals(expected, bytes(buffer));
    }

    @Test
    void growsInTheMiddleOfNestedMessages() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        final NodeWriter child = root.addChild();
        final byte[] chunk = new byte[1000];
        for (int i = 0; i < 100; i++) {
            child.payload(chunk);
        }
        root.end();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int i = 0; i < 100; i++) {
            body.writeBytes(delimited(0x0A, chunk));
        }
        assertArrayEquals(delimited(0x12, body.toByteArray()), bytes(buffer));
    }

    @Test
    void closedChildrenKeepTheirRangesAsLaterSiblingsAreWrittenAndTheBufferGrows() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1, LengthReservation.ONE_BYTE);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        final int[][] ranges = new int[50][];
        for (int i = 0; i < ranges.length; i++) {
            // growing sizes, so later siblings shift and the buffer grows
            final NodeWriter child = root.addChild().payload(new byte[i * 37]);
            child.end();
            ranges[i] = new int[] {child.offset(), child.length()};
        }
        root.end();
        for (int i = 0; i < ranges.length; i++) {
            assertArrayEquals(
                    delimited(0x0A, new byte[i * 37]),
                    Arrays.copyOfRange(buffer.array(), ranges[i][0], ranges[i][0] + ranges[i][1]),
                    "child " + i);
            assertEquals(
                    delimited(0x0A, new byte[i * 37]).length,
                    buffer.bytes(ranges[i][0], ranges[i][1]).length());
        }
    }

    @Test
    void callingTheParentEndsTheChild() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        final NodeWriter child = root.addChild().payload(new byte[] {1});
        // writing a field of the parent ends the open child first
        root.single(2);
        assertFalse(child.isOpen());
        assertEquals(3, child.length());
        assertThrows(IllegalStateException.class, () -> child.payload(new byte[] {3}));
        root.end();
        assertArrayEquals(
                concat(delimited(0x12, delimited(0x0A, new byte[] {1})), new byte[] {0x20, 2}), bytes(buffer));
    }

    @Test
    void packedFieldsAreLeftOutWhenEmpty() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        root.numbers().end();
        root.end();
        assertEquals(0, buffer.length());
        // with elements, and ended by the next field
        buffer.reset();
        final NodeWriter again = new NodeWriter(1).start(buffer);
        again.numbers().add(1).add(-1).add(300);
        again.single(5).end();
        final byte[] values = {1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 1, (byte) 0xAC, 2};
        assertArrayEquals(concat(delimited(0x1A, values), new byte[] {0x20, 5}), bytes(buffer));
    }

    @Test
    void misuseThrows() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final NodeWriter unstarted = new NodeWriter(1);
        assertTrue(assertThrows(IllegalStateException.class, () -> unstarted.payload(new byte[0]))
                .getMessage()
                .contains("not been started"));
        assertThrows(IllegalStateException.class, unstarted::end);
        assertThrows(IllegalStateException.class, unstarted::offset);

        final NodeWriter root = new NodeWriter(1).start(buffer);
        // a second top-level message can't start while one is open
        assertThrows(IllegalStateException.class, () -> new NodeWriter(1).start(buffer));
        root.addChild();
        // fields out of order
        root.single(1);
        assertTrue(assertThrows(IllegalStateException.class, () -> root.payload(new byte[0]))
                .getMessage()
                .contains("Field 1 written after field 4"));
        // a second oneof alternative
        assertThrows(IllegalStateException.class, () -> root.other(2));
        // the offset is known only once the message has ended
        assertThrows(IllegalStateException.class, root::offset);
        root.end();
        // use after end
        assertTrue(assertThrows(IllegalStateException.class, () -> root.single(1))
                .getMessage()
                .contains("ended"));
        assertThrows(IllegalStateException.class, root::end);
        // a top-level message after another, in the same buffer
        final int first = buffer.length();
        final NodeWriter second = new NodeWriter(1).start(buffer).single(9);
        second.end();
        assertEquals(first, second.offset());
    }

    @Test
    void repeatedSingularFieldThrows() {
        final NodeWriter root = new NodeWriter(1).start(new ProtoWriteBuffer(16));
        root.numbers().end();
        assertThrows(IllegalStateException.class, root::numbers);
    }

    @Test
    void resetAbandonsOpenMessages() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(16);
        final NodeWriter root = new NodeWriter(1).start(buffer);
        final NodeWriter child = root.addChild();
        buffer.reset();
        assertEquals(0, buffer.length());
        assertFalse(root.isOpen());
        assertThrows(IllegalStateException.class, () -> child.payload(new byte[0]));
        assertThrows(IllegalStateException.class, root::end);
        // the writers can start again
        new NodeWriter(1).start(buffer).end();
        root.start(buffer).single(1).end();
        assertArrayEquals(new byte[] {0x20, 1}, bytes(buffer));
    }

    @Test
    void tagsOfEverySize() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        // field 1, 16, 2048, 262144 and the largest, 2^29 - 1, with wire type 0: one to five bytes
        buffer.tagVarint(1 << 3, 0);
        buffer.tagVarint(16 << 3, 0);
        buffer.tagVarint(2048 << 3, 0);
        buffer.tagVarint(262144 << 3, 0);
        buffer.tagVarint(((1 << 29) - 1) << 3, 0);
        assertArrayEquals(
                new byte[] {
                    0x08,
                    0,
                    (byte) 0x80,
                    1,
                    0,
                    (byte) 0x80,
                    (byte) 0x80,
                    1,
                    0,
                    (byte) 0x80,
                    (byte) 0x80,
                    (byte) 0x80,
                    1,
                    0,
                    (byte) 0xF8,
                    -1,
                    -1,
                    -1,
                    0x0F,
                    0
                },
                bytes(buffer));
    }

    @Test
    void varintsOfEverySize() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        final long[] values = new long[64 * 3];
        for (int bit = 0; bit < 64; bit++) {
            values[bit * 3] = 1L << bit;
            values[bit * 3 + 1] = (1L << bit) - 1;
            values[bit * 3 + 2] = -(1L << bit);
        }
        final ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (final long value : values) {
            buffer.tagVarint(0x08, value);
            expected.write(0x08);
            long v = value;
            while ((v & ~0x7FL) != 0) {
                expected.write((int) ((v & 0x7F) | 0x80));
                v >>>= 7;
            }
            expected.write((int) v);
        }
        assertArrayEquals(expected.toByteArray(), bytes(buffer));
    }

    @Test
    void stringsAndCheckedUtf8() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        buffer.tagString(0x0A, "héllo ✓");
        final byte[] utf8 = "héllo ✓".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(delimited(0x0A, utf8), bytes(buffer));
        ProtoWriteBuffer.checkUtf8(utf8, 0, utf8.length);
        assertThrows(IllegalArgumentException.class, () -> ProtoWriteBuffer.checkUtf8(new byte[] {(byte) 0xFF}, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ProtoWriteBuffer.checkUtf8(utf8, 1, utf8.length));
    }

    @Test
    void outputAccessors() {
        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1);
        new NodeWriter(1).start(buffer).single(3).end();
        final byte[] expected = {0x20, 3};
        assertEquals(Bytes.wrap(expected), buffer.bytes());
        assertEquals(Bytes.wrap(expected), buffer.toBytes());
        assertEquals(Bytes.wrap(new byte[] {3}), buffer.bytes(1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.bytes(1, 2));
        final byte[] copy = new byte[3];
        assertEquals(2, buffer.copyTo(copy, 1));
        assertArrayEquals(new byte[] {0, 0x20, 3}, copy);
        assertEquals(LengthReservation.ADAPTIVE, buffer.lengthReservation());
    }
}
