// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XdrWriterToolsTest {

    // ================================================================================================================
    // HELPER

    private static byte[] toBytes(final BufferedData buf) {
        buf.flip();
        final byte[] result = new byte[(int) buf.remaining()];
        buf.readBytes(result);
        return result;
    }

    // ================================================================================================================
    // writeInt

    @Test
    void writeInt_positive() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeInt(buf, 42);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x2A}, toBytes(buf));
    }

    @Test
    void writeInt_negative() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeInt(buf, -1);
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, toBytes(buf));
    }

    @Test
    void writeInt_zero() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeInt(buf, 0);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeInt_minValue() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeInt(buf, Integer.MIN_VALUE);
        assertArrayEquals(new byte[] {(byte) 0x80, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeInt_maxValue() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeInt(buf, Integer.MAX_VALUE);
        assertArrayEquals(new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, toBytes(buf));
    }

    // ================================================================================================================
    // writeHyper

    @Test
    void writeHyper_positive() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeHyper(buf, 42L);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A}, toBytes(buf));
    }

    @Test
    void writeHyper_negative() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeHyper(buf, -1L);
        assertArrayEquals(
                new byte[] {
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF
                },
                toBytes(buf));
    }

    @Test
    void writeHyper_minValue() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeHyper(buf, Long.MIN_VALUE);
        assertArrayEquals(
                new byte[] {(byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeHyper_maxValue() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeHyper(buf, Long.MAX_VALUE);
        assertArrayEquals(
                new byte[] {0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                toBytes(buf));
    }

    // ================================================================================================================
    // writeFloat

    @Test
    void writeFloat_one() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeFloat(buf, 1.0f);
        assertArrayEquals(new byte[] {0x3F, (byte) 0x80, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeFloat_negativeZero() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeFloat(buf, -0.0f);
        assertArrayEquals(new byte[] {(byte) 0x80, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeFloat_nan() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeFloat(buf, Float.NaN);
        assertArrayEquals(new byte[] {0x7F, (byte) 0xC0, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeFloat_infinity() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeFloat(buf, Float.POSITIVE_INFINITY);
        assertArrayEquals(new byte[] {0x7F, (byte) 0x80, 0x00, 0x00}, toBytes(buf));
    }

    // ================================================================================================================
    // writeDouble

    @Test
    void writeDouble_one() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeDouble(buf, 1.0);
        assertArrayEquals(
                new byte[] {0x3F, (byte) 0xF0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeDouble_negativeZero() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeDouble(buf, -0.0);
        assertArrayEquals(
                new byte[] {(byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    // ================================================================================================================
    // writeBool

    @Test
    void writeBool_true() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeBool(buf, true);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x01}, toBytes(buf));
    }

    @Test
    void writeBool_false() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeBool(buf, false);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    // ================================================================================================================
    // writeEnum

    @Test
    void writeEnum_zero() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeEnum(buf, 0);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeEnum_negative() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeEnum(buf, -1);
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, toBytes(buf));
    }

    // ================================================================================================================
    // writePresence

    @Test
    void writePresence_true() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePresence(buf, true);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x01}, toBytes(buf));
    }

    @Test
    void writePresence_false() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePresence(buf, false);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    // ================================================================================================================
    // writeString - verify length prefix + padding

    @Test
    void writeString_empty() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "");
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeString_len1() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "A");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x01, 0x41, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeString_len2() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "AB");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x02, 0x41, 0x42, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeString_len3() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "ABC");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x03, 0x41, 0x42, 0x43, 0x00}, toBytes(buf));
    }

    @Test
    void writeString_len4() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "ABCD");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x04, 0x41, 0x42, 0x43, 0x44}, toBytes(buf));
    }

    @Test
    void writeString_len5() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "ABCDE");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x05, 0x41, 0x42, 0x43, 0x44, 0x45, 0x00, 0x00, 0x00},
                toBytes(buf));
    }

    @Test
    void writeString_utf8() {
        // "\u00E9" is é — 2 UTF-8 bytes (0xC3 0xA9), so length = 2, padding = 2
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "\u00E9");
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x02, (byte) 0xC3, (byte) 0xA9, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeString_multibyte() {
        // "日本語" — 3 chars × 3 UTF-8 bytes = 9 bytes; padding = 3
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeString(buf, "日本語");
        final byte[] result = toBytes(buf);
        // length prefix = 9
        assertEquals(0x00, result[0]);
        assertEquals(0x00, result[1]);
        assertEquals(0x00, result[2]);
        assertEquals(0x09, result[3]);
        // total length = 4 + 9 + 3 = 16
        assertEquals(16, result.length);
        // padding bytes at end must be zero
        assertEquals(0x00, result[13]);
        assertEquals(0x00, result[14]);
        assertEquals(0x00, result[15]);
    }

    // ================================================================================================================
    // writeOpaque - verify length prefix + padding

    @Test
    void writeOpaque_empty() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeOpaque(buf, Bytes.EMPTY);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeOpaque_len1() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(new byte[] {0x42}));
        // 4-byte len + 1 data byte + 3 zero pad bytes
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x01, 0x42, 0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writeOpaque_len4() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(new byte[] {0x01, 0x02, 0x03, 0x04}));
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x04, 0x01, 0x02, 0x03, 0x04}, toBytes(buf));
    }

    @Test
    void writeOpaque_len33() {
        final BufferedData buf = BufferedData.allocate(64);
        final byte[] data = new byte[33];
        for (int i = 0; i < 33; i++) data[i] = (byte) (i + 1);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(data));
        final byte[] result = toBytes(buf);
        // 4 (len) + 33 (data) + 3 (pad) = 40
        assertEquals(40, result.length);
        // length prefix = 33 = 0x21
        assertEquals(0x00, result[0]);
        assertEquals(0x00, result[1]);
        assertEquals(0x00, result[2]);
        assertEquals(0x21, result[3]);
        // padding must be zero
        assertEquals(0x00, result[37]);
        assertEquals(0x00, result[38]);
        assertEquals(0x00, result[39]);
    }

    // ================================================================================================================
    // writePadding

    @Test
    void writePadding_0() {
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 0);
        assertEquals(0, buf.position());
    }

    @Test
    void writePadding_1() {
        // dataLength=1 → 3 padding bytes
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 1);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writePadding_2() {
        // dataLength=2 → 2 padding bytes
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 2);
        assertArrayEquals(new byte[] {0x00, 0x00}, toBytes(buf));
    }

    @Test
    void writePadding_3() {
        // dataLength=3 → 1 padding byte
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 3);
        assertArrayEquals(new byte[] {0x00}, toBytes(buf));
    }

    @Test
    void writePadding_4() {
        // dataLength=4 → 0 padding bytes
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 4);
        assertEquals(0, buf.position());
    }

    @Test
    void writePadding_5() {
        // dataLength=5 → 3 padding bytes
        final BufferedData buf = BufferedData.allocate(64);
        XdrWriterTools.writePadding(buf, 5);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00}, toBytes(buf));
    }

    // ================================================================================================================
    // sizeOfString

    @Test
    void sizeOfString_empty() {
        assertEquals(4, XdrWriterTools.sizeOfString(""));
    }

    @Test
    void sizeOfString_len1() {
        assertEquals(8, XdrWriterTools.sizeOfString("A"));
    }

    @Test
    void sizeOfString_len4() {
        assertEquals(8, XdrWriterTools.sizeOfString("ABCD"));
    }

    @Test
    void sizeOfString_len5() {
        assertEquals(12, XdrWriterTools.sizeOfString("ABCDE"));
    }

    // ================================================================================================================
    // sizeOfOpaque

    @Test
    void sizeOfOpaque_empty() {
        assertEquals(4, XdrWriterTools.sizeOfOpaque(Bytes.EMPTY));
    }

    @Test
    void sizeOfOpaque_len33() {
        assertEquals(40, XdrWriterTools.sizeOfOpaque(Bytes.wrap(new byte[33])));
    }

    // ================================================================================================================
    // paddingSize formula

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 33, 100})
    void paddingSize_formula(final int len) {
        final int expected = (4 - (len % 4)) % 4;
        assertEquals(expected, XdrWriterTools.paddingSize(len));
    }
}
