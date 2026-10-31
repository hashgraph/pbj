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
    void everyVarintLengthReadsTheSameAtEveryDistanceFromTheLimit() {
        // the fast path needs 8 bytes left and handles up to 8-byte varints; the rest take the slow path
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
                // and cut short by the limit, at every byte
                for (int cut = 0; cut < varint.length; cut++) {
                    final ArrayCursor cursor = new ArrayCursor(Arrays.copyOf(varint, 20), 0, cut);
                    assertThrows(BufferUnderflowException.class, cursor::varint, "cut at " + cut);
                }
            }
        }
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
        assertArrayEquals(bytes(7, 8, 9), copy.toByteArray(), "bytes are a copy, as the stream parser returns");
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
