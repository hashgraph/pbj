// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.UnknownField;
import com.hedera.pbj.runtime.UnknownFieldException;
import com.hedera.pbj.runtime.io.DataEncodingException;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ArrayCursorTest {

    private static ArrayCursor cursor(final byte[] bytes) {
        return new ArrayCursor(bytes, 0, bytes.length);
    }

    private static byte[] bytes(final int... values) {
        final byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    @Test
    void varintsMatchBufferedData() {
        final Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            final int bits = 1 + random.nextInt(64);
            final long value = bits == 64 ? random.nextLong() : random.nextLong() & ((1L << bits) - 1);
            final BufferedData out = BufferedData.allocate(32);
            out.writeVarLong(value, false);
            out.writeVarLong(value, true);
            final byte[] encoded = new byte[(int) out.position()];
            out.getBytes(0, encoded);
            // both the fast path (10+ bytes left) and the near-limit path
            for (final int padding : new int[] {0, 12}) {
                final byte[] padded = new byte[encoded.length + padding];
                System.arraycopy(encoded, 0, padded, 0, encoded.length);
                final ArrayCursor cursor = new ArrayCursor(padded, 0, encoded.length + padding);
                assertEquals(value, cursor.varint());
                assertEquals(value, cursor.varintZigZag());
                assertEquals(encoded.length, cursor.consumed());
            }
        }
    }

    @Test
    void truncatedInputThrows() {
        assertThrows(BufferUnderflowException.class, () -> cursor(bytes(0x80)).varint());
        assertThrows(BufferUnderflowException.class, () -> cursor(bytes()).varint());
        assertThrows(
                BufferUnderflowException.class, () -> cursor(bytes(1, 2, 3)).fixed32());
        assertThrows(BufferUnderflowException.class, () -> cursor(bytes(1, 2, 3, 4, 5, 6, 7))
                .fixed64());
        // a length past the end
        assertThrows(
                BufferUnderflowException.class, () -> cursor(bytes(5, 'a', 'b')).string(100));
        // reads stop at the limit, not the end of the array
        final ArrayCursor limited = new ArrayCursor(bytes(0x96, 0x01), 0, 1);
        assertThrows(BufferUnderflowException.class, limited::varint);
    }

    @Test
    void overLongVarintsThrow() {
        // ten bytes, all with the continuation bit: no varint is that long
        final byte[] tenContinuations = bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
        assertThrows(DataEncodingException.class, () -> BufferedData.wrap(tenContinuations)
                .readVarLong(false));
        // exactly ten bytes left, and more after them: both take the fast path
        for (final int padding : new int[] {0, 5}) {
            final byte[] padded = new byte[tenContinuations.length + padding];
            System.arraycopy(tenContinuations, 0, padded, 0, tenContinuations.length);
            assertThrows(DataEncodingException.class, () -> cursor(padded).varint());
            assertThrows(DataEncodingException.class, () -> cursor(padded).tag());
        }
        // fewer than ten bytes left, all with the continuation bit: the input ends inside the varint
        assertThrows(BufferUnderflowException.class, () -> new ArrayCursor(tenContinuations, 0, 9).varint());
    }

    /** @return {@code value} as an unsigned varint */
    private static byte[] encodeVarint(final long value) {
        final byte[] out = new byte[10];
        int i = 0;
        long v = value;
        while ((v & ~0x7FL) != 0) {
            out[i++] = (byte) ((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out[i++] = (byte) v;
        return Arrays.copyOf(out, i);
    }

    @Test
    void bytesReadThroughTheSequentialViewOfBytesAreViews() throws Exception {
        // a length-prefixed bytes value, after one byte the caller has already read
        final byte[] array = {9, 3, 1, 2, 3};
        final Bytes source = Bytes.wrap(array);
        final var input = source.toReadableSequentialData();
        input.skip(1);
        final ArrayCursor cursor = ArrayCursor.of(input);
        final Bytes value = cursor.bytes(100);
        assertEquals(Bytes.wrap(new byte[] {1, 2, 3}), value);
        // like the stream parser over Bytes, which is immutable, the value shares the source's array
        assertTrue(value.backingArray() == array, "a view of the source, not a copy");
    }

    @Test
    void everyVarintLengthReadsTheSameAtEveryDistanceFromTheLimit() {
        // the fast path needs ten bytes left in the array, whatever the limit; near the array's end the slow path
        for (int length = 1; length <= 10; length++) {
            for (final long value : new long[] {
                length == 1 ? 0 : 1L << (7 * (length - 1)),
                length == 10 ? -1L : (1L << (7 * length)) - 1,
                length == 10 ? Long.MIN_VALUE : 1L << (7 * (length - 1)) | 0x55
            }) {
                final byte[] varint = encodeVarint(value);
                assertEquals(length, varint.length, "length of " + value);
                for (int after = 0; after <= 12; after++) {
                    final byte[] padded = Arrays.copyOf(varint, varint.length + after);
                    final ArrayCursor cursor = cursor(padded);
                    assertEquals(value, cursor.varint(), "value " + value + " with " + after + " bytes after it");
                    assertEquals(varint.length, cursor.position(), "end of " + value);
                }
                // ending at the limit, with more of the array after it, as for the last field of a nested message
                final ArrayCursor atLimit = new ArrayCursor(Arrays.copyOf(varint, 20), 0, varint.length);
                assertEquals(value, atLimit.varint(), "value " + value + " ending at the limit");
                assertEquals(varint.length, atLimit.position(), "end of " + value + " at the limit");
                // and cut short by the limit, at every byte, with the rest of it, and more, in the array
                for (int cut = 0; cut < varint.length; cut++) {
                    final ArrayCursor cursor = new ArrayCursor(Arrays.copyOf(varint, 20), 0, cut);
                    assertThrows(BufferUnderflowException.class, cursor::varint, "cut at " + cut);
                }
            }
        }
    }

    @Test
    void varintsReadTheSameNearTheLimitAndNearTheArraysEnd() {
        for (int length = 2; length <= 10; length++) {
            final long value = length == 10 ? Long.MIN_VALUE | 0x55 : (1L << (7 * (length - 1))) | 0x55;
            final byte[] varint = encodeVarint(value);
            for (int before = 0; before <= 3; before++) {
                for (int after = 0; after <= 12; after++) {
                    final byte[] in = new byte[before + varint.length + after];
                    System.arraycopy(varint, 0, in, before, varint.length);
                    // the limit at the varint's end, with "after" more bytes in the array; and at the array's end
                    for (final int limit : new int[] {before + varint.length, in.length}) {
                        final ArrayCursor cursor = new ArrayCursor(in, before, limit);
                        assertEquals(value, cursor.varint(), length + " bytes, limit " + limit + " of " + in.length);
                        assertEquals(before + varint.length, cursor.position());
                    }
                    // a limit inside the varint is an underflow, whatever follows in the array
                    final ArrayCursor cut = new ArrayCursor(in, before, before + varint.length - 1);
                    assertThrows(BufferUnderflowException.class, cut::varint, length + " bytes, cut");
                }
            }
        }
    }

    @Test
    void aBadTenthByteIsAnUnderflowWhenPastTheLimit() {
        final byte[] in = new byte[20];
        Arrays.fill(in, 0, 9, (byte) 0x80);
        in[9] = 2; // bit 63 clear: malformed
        assertThrows(DataEncodingException.class, () -> new ArrayCursor(in, 0, 10).varint());
        assertThrows(BufferUnderflowException.class, () -> new ArrayCursor(in, 0, 9).varint());
        in[9] = 1;
        assertEquals(Long.MIN_VALUE, new ArrayCursor(in, 0, 10).varint());
    }

    @Test
    void tenthVarintByteIsCheckedLikeTheStreamParser() {
        // nine continuation bytes, then every possible tenth byte: only bit 63 is left for the tenth byte, so the
        // cursor must accept and reject exactly what the stream parser does
        for (int tenth = 0; tenth < 256; tenth++) {
            final byte[] varint = new byte[10];
            Arrays.fill(varint, 0, 9, (byte) 0x80);
            varint[9] = (byte) tenth;
            Long expected;
            try {
                expected = BufferedData.wrap(varint).readVarLong(false);
            } catch (final RuntimeException e) {
                expected = null;
            }
            for (final int padding : new int[] {0, 5}) {
                final ArrayCursor cursor = cursor(Arrays.copyOf(varint, varint.length + padding));
                if (expected == null) {
                    assertThrows(DataEncodingException.class, cursor::varint, "tenth byte " + tenth);
                } else {
                    assertEquals(expected, cursor.varint(), "tenth byte " + tenth);
                }
            }
        }
    }

    @Test
    void runsOfDelimitedFieldsAreCountedWithoutReading() {
        // three field-1 messages of 0, 2 and 200 bytes, then field 2, then field 1 again
        final byte[] big = new byte[200];
        final byte[] in = concat(
                new byte[] {0x0A, 0},
                new byte[] {0x0A, 2, 7, 7},
                new byte[] {0x0A, (byte) 0xC8, 1},
                big,
                new byte[] {0x12, 0},
                new byte[] {0x0A, 0});
        final ArrayCursor cursor = cursor(in);
        assertEquals(3, cursor.run((byte) 0x0A));
        assertEquals(0, cursor.position(), "nothing read");
        assertEquals(0, cursor.run((byte) 0x12));
        // cut short: a length past the limit, or a length that does not end, stops the count
        assertEquals(1, new ArrayCursor(in, 0, 5).run((byte) 0x0A));
        assertEquals(2, new ArrayCursor(in, 0, 7).run((byte) 0x0A));
        assertEquals(
                0,
                cursor(new byte[] {0x0A, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 1})
                        .run((byte) 0x0A));
        assertEquals(0, cursor(new byte[0]).run((byte) 0x0A));
        // long runs are not counted: too many fields, or too many bytes
        final byte[] many = new byte[2 * (ArrayCursor.MAX_RUN + 1)];
        for (int i = 0; i < many.length; i += 2) {
            many[i] = 0x0A;
        }
        assertEquals(0, cursor(many).run((byte) 0x0A));
        assertEquals(ArrayCursor.MAX_RUN, new ArrayCursor(many, 0, 2 * ArrayCursor.MAX_RUN).run((byte) 0x0A));
        final byte[] large = concat(new byte[] {0x0A, (byte) 0xFF, (byte) 0xFF, 0x03}, new byte[0xFFFF]);
        assertEquals(1, cursor(large).run((byte) 0x0A));
        assertEquals(0, cursor(concat(large, new byte[] {0x0A, 0})).run((byte) 0x0A));
    }

    private static byte[] concat(final byte[]... parts) {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (final byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    @Test
    void delimitedFieldsAreMatchedByMask() {
        final int fields2And8 = (1 << 2) | (1 << 8);
        final ArrayCursor cursor = cursor(new byte[] {0x12, 0x42, 0x1A, 0x10, (byte) 0x82, 0x01});
        assertEquals(0x12, cursor.atField(fields2And8));
        assertEquals(1, cursor.position());
        assertEquals(0x42, cursor.atField(fields2And8));
        // field 3, wire type 2: not in the mask; field 2, wire type 0; a two-byte tag
        assertEquals(0, cursor.atField(fields2And8));
        cursor.skip(1);
        assertEquals(0, cursor.atField(fields2And8));
        cursor.skip(1);
        assertEquals(0, cursor.atField(-1));
        assertEquals(4, cursor.position());
        assertEquals(0, new ArrayCursor(new byte[] {0x12}, 0, 0).atField(fields2And8));
    }

    @Test
    void fixedValuesAreLittleEndian() {
        assertEquals(0x04030201, cursor(bytes(1, 2, 3, 4)).fixed32());
        assertEquals(0x0807060504030201L, cursor(bytes(1, 2, 3, 4, 5, 6, 7, 8)).fixed64());
    }

    @Test
    void stringsAndBytes() throws Exception {
        final byte[] utf8 = "héllo".getBytes(StandardCharsets.UTF_8);
        final byte[] encoded = new byte[utf8.length + 1];
        encoded[0] = (byte) utf8.length;
        System.arraycopy(utf8, 0, encoded, 1, utf8.length);
        assertEquals("héllo", cursor(encoded).string(100));
        assertEquals("ab", cursor(bytes(2, 'a', 'b')).string(100));
        assertThrows(
                MalformedProtobufException.class, () -> cursor(bytes(1, 0xFF)).string(100));
        final ParseException tooBig = assertThrows(
                ParseException.class, () -> cursor(bytes(3, 1, 2, 3)).string(2));
        assertEquals("size 3 is greater than max 2", tooBig.getMessage());
        final byte[] source = bytes(3, 7, 8, 9);
        final Bytes copy = cursor(source).bytes(10);
        source[1] = 0;
        assertArrayEquals(bytes(7, 8, 9), copy.toByteArray(), "bytes from an array are a copy");
        // from a Bytes, which is immutable, they are views, as the stream parser returns them
        final Bytes input = Bytes.wrap(bytes(0, 0, 3, 7, 8, 9)).slice(2, 4);
        final Bytes view = ArrayCursor.of(input).bytes(10);
        assertEquals(Bytes.wrap(bytes(7, 8, 9)), view);
        assertTrue(view.backingArray() == input.backingArray(), "a view of the input");
        assertEquals(Bytes.EMPTY, cursor(bytes(0)).bytes(10));
    }

    @Test
    void boolsMustBeZeroOrOne() throws Exception {
        assertTrue(cursor(bytes(1)).bool());
        assertFalse(cursor(bytes(0)).bool());
        assertThrows(IOException.class, () -> cursor(bytes(2)).bool());
    }

    @Test
    void tagsInOrder() {
        final ArrayCursor cursor = cursor(bytes(0x08, 0x82, 0x01));
        assertFalse(cursor.at((byte) 0x10));
        assertTrue(cursor.at((byte) 0x08));
        assertFalse(cursor.at((byte) 0x82, (byte) 0x02));
        assertTrue(cursor.at((byte) 0x82, (byte) 0x01));
        assertFalse(cursor.hasRemaining());
        assertFalse(cursor.at((byte) 0x08));
    }

    @Test
    void nestedLimits() throws Exception {
        final ArrayCursor cursor = cursor(bytes(2, 1, 1, 9));
        final int length = cursor.length(10, "inner");
        final int outer = cursor.pushLimit(length);
        assertEquals(1, cursor.varint());
        // one byte of the nested message left unread
        assertThrows(BufferOverflowException.class, () -> cursor.popLimit(outer));
        assertEquals(1, cursor.varint());
        assertFalse(cursor.hasRemaining());
        cursor.popLimit(outer);
        assertEquals(9, cursor.varint());
        final ParseException tooBig = assertThrows(
                ParseException.class, () -> cursor(bytes(5, 0, 0, 0, 0, 0)).length(4, "inner"));
        assertEquals("inner size 5 is greater than max 4", tooBig.getMessage());
    }

    @Test
    void unknownFieldsAreSkippedCollectedOrRejected() throws Exception {
        // field 9 varint 150, then field 10 length-delimited "ab"
        final byte[] input = bytes(0x96, 0x01, 0x02, 'a', 'b');
        ArrayCursor cursor = cursor(input);
        assertNull(cursor.unknownField(9 << 3, false, false, false, null, 100));
        assertNull(cursor.unknownField(10 << 3 | 2, false, false, false, null, 100));
        assertFalse(cursor.hasRemaining());

        cursor = cursor(input);
        List<UnknownField> fields = cursor.unknownField(9 << 3, false, false, true, null, 100);
        fields = cursor.unknownField(10 << 3 | 2, false, false, true, fields, 100);
        assertEquals(2, fields.size());
        assertArrayEquals(bytes(0x96, 0x01), fields.get(0).bytes().toByteArray());
        assertArrayEquals(bytes(0x02, 'a', 'b'), fields.get(1).bytes().toByteArray());

        assertThrows(
                UnknownFieldException.class, () -> cursor(input).unknownField(9 << 3, false, true, false, null, 1));
        assertThrows(IOException.class, () -> cursor(input).unknownField(9 << 3, true, false, false, null, 1));
        assertThrows(IOException.class, () -> cursor(input).unknownField(0, false, false, false, null, 1));
        assertThrows(IOException.class, () -> cursor(input).unknownField(9 << 3 | 6, false, false, false, null, 1));
        // wire types above 5 are errors in strict mode too
        for (final int wireType : new int[] {6, 7}) {
            assertThrows(IOException.class, () -> cursor(input)
                    .unknownField(9 << 3 | wireType, false, true, false, null, 1));
        }
        assertThrows(ParseException.class, () -> cursor(bytes(5, 1, 2, 3, 4, 5))
                .unknownField(1 << 3 | 2, false, false, false, null, 4));
    }

    @Test
    void cursorsOverArrayInputsOnly() {
        final BufferedData heap = BufferedData.wrap(bytes(1, 2, 3, 4));
        heap.position(1);
        heap.limit(3);
        final ArrayCursor cursor = ArrayCursor.of(heap);
        assertEquals(2, cursor.varint());
        assertEquals(3, cursor.varint());
        assertFalse(cursor.hasRemaining());
        assertNull(ArrayCursor.of(BufferedData.allocateOffHeap(4)));
        final Bytes slice = Bytes.wrap(bytes(9, 8, 7, 6)).slice(1, 2);
        final ArrayCursor overSlice = ArrayCursor.of(slice);
        assertEquals(8, overSlice.varint());
        assertEquals(7, overSlice.varint());
        assertFalse(overSlice.hasRemaining());
    }
}
