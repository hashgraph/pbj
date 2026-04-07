// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

class XdrParserToolsTest {

    // ================================================================================================================
    // HELPER

    private static BufferedData bufOf(final byte... bytes) {
        final BufferedData buf = BufferedData.allocate(bytes.length);
        buf.writeBytes(bytes);
        buf.flip();
        return buf;
    }

    // ================================================================================================================
    // readInt

    @Test
    void readInt_positive() {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2A);
        assertEquals(42, XdrParserTools.readInt(buf));
    }

    @Test
    void readInt_negative() {
        final BufferedData buf = bufOf((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertEquals(-1, XdrParserTools.readInt(buf));
    }

    @Test
    void readInt_minValue() {
        final BufferedData buf = bufOf((byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(Integer.MIN_VALUE, XdrParserTools.readInt(buf));
    }

    @Test
    void readInt_maxValue() {
        final BufferedData buf = bufOf((byte) 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertEquals(Integer.MAX_VALUE, XdrParserTools.readInt(buf));
    }

    // ================================================================================================================
    // readHyper

    @Test
    void readHyper_positive() {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2A);
        assertEquals(42L, XdrParserTools.readHyper(buf));
    }

    @Test
    void readHyper_minValue() {
        final BufferedData buf = bufOf(
                (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(Long.MIN_VALUE, XdrParserTools.readHyper(buf));
    }

    // ================================================================================================================
    // readFloat

    @Test
    void readFloat_one() {
        final BufferedData buf = bufOf((byte) 0x3F, (byte) 0x80, (byte) 0x00, (byte) 0x00);
        assertEquals(1.0f, XdrParserTools.readFloat(buf));
    }

    @Test
    void readFloat_nan() {
        final BufferedData buf = bufOf((byte) 0x7F, (byte) 0xC0, (byte) 0x00, (byte) 0x00);
        assertTrue(Float.isNaN(XdrParserTools.readFloat(buf)));
    }

    // ================================================================================================================
    // readDouble

    @Test
    void readDouble_one() {
        final BufferedData buf = bufOf(
                (byte) 0x3F, (byte) 0xF0, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(1.0, XdrParserTools.readDouble(buf));
    }

    // ================================================================================================================
    // readBool

    @Test
    void readBool_true() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01);
        assertTrue(XdrParserTools.readBool(buf));
    }

    @Test
    void readBool_false() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertFalse(XdrParserTools.readBool(buf));
    }

    @Test
    void readBool_invalid() {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02);
        assertThrows(ParseException.class, () -> XdrParserTools.readBool(buf));
    }

    @Test
    void readBool_invalid_max() {
        final BufferedData buf = bufOf((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readBool(buf));
    }

    // ================================================================================================================
    // readEnum

    @Test
    void readEnum() {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x03);
        assertEquals(3, XdrParserTools.readEnum(buf));
    }

    // ================================================================================================================
    // readPresence

    @Test
    void readPresence_true() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01);
        assertTrue(XdrParserTools.readPresence(buf));
    }

    @Test
    void readPresence_false() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertFalse(XdrParserTools.readPresence(buf));
    }

    @Test
    void readPresence_invalid_2() {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02);
        assertThrows(ParseException.class, () -> XdrParserTools.readPresence(buf));
    }

    @Test
    void readPresence_invalid_max() {
        final BufferedData buf = bufOf((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readPresence(buf));
    }

    // ================================================================================================================
    // readAndValidatePadding — all-zero padding accepted

    @Test
    void readAndValidatePadding_allZero() {
        // 3 pad bytes for dataLength=1
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertDoesNotThrow(() -> XdrParserTools.readAndValidatePadding(buf, 1));
    }

    @Test
    void readAndValidatePadding_nonZeroFirst() {
        final BufferedData buf = bufOf((byte) 0xFF, (byte) 0x00, (byte) 0x00);
        assertThrows(ParseException.class, () -> XdrParserTools.readAndValidatePadding(buf, 1));
    }

    @Test
    void readAndValidatePadding_nonZeroLast() {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readAndValidatePadding(buf, 1));
    }

    @Test
    void readAndValidatePadding_noPadNeeded() {
        // dataLength=4, 0 pad bytes — empty buffer, no reads expected
        final BufferedData buf = BufferedData.allocate(0);
        assertDoesNotThrow(() -> XdrParserTools.readAndValidatePadding(buf, 4));
    }

    // ================================================================================================================
    // readString — valid cases

    @Test
    void readString_empty() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals("", XdrParserTools.readString(buf, 256));
    }

    @Test
    void readString_len1() throws ParseException {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals("A", XdrParserTools.readString(buf, 256));
    }

    @Test
    void readString_len4() throws ParseException {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x04,
                (byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x44);
        assertEquals("ABCD", XdrParserTools.readString(buf, 256));
    }

    @Test
    void readString_len5() throws ParseException {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05,
                (byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x44,
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals("ABCDE", XdrParserTools.readString(buf, 256));
    }

    @Test
    void readString_utf8() throws ParseException {
        // len=2 + UTF-8 bytes for é (0xC3 0xA9) + 2 padding zeros
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02,
                (byte) 0xC3, (byte) 0xA9, (byte) 0x00, (byte) 0x00);
        assertEquals("\u00E9", XdrParserTools.readString(buf, 256));
    }

    @Test
    void readString_exceedsMaxSize() {
        // length = 10 but maxSize = 5
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A,
                (byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x44, (byte) 0x45,
                (byte) 0x46, (byte) 0x47, (byte) 0x48, (byte) 0x49, (byte) 0x4A,
                (byte) 0x00, (byte) 0x00);
        assertThrows(ParseException.class, () -> XdrParserTools.readString(buf, 5));
    }

    @Test
    void readString_nonZeroPadding() {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readString(buf, 256));
    }

    // ================================================================================================================
    // readOpaque — valid cases

    @Test
    void readOpaque_empty() throws ParseException {
        final BufferedData buf = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        assertEquals(Bytes.EMPTY, XdrParserTools.readOpaque(buf, 256));
    }

    @Test
    void readOpaque_len1() throws ParseException {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        final Bytes result = XdrParserTools.readOpaque(buf, 256);
        assertEquals(1, result.length());
        assertEquals((byte) 0xFF, result.getByte(0));
    }

    @Test
    void readOpaque_len33() throws ParseException {
        final byte[] data = new byte[33];
        for (int i = 0; i < 33; i++) data[i] = (byte) (i + 1);
        final byte[] input = new byte[4 + 33 + 3];
        // length prefix = 33 = 0x21
        input[3] = 0x21;
        System.arraycopy(data, 0, input, 4, 33);
        // padding bytes 37, 38, 39 remain 0
        final BufferedData buf = BufferedData.allocate(input.length);
        buf.writeBytes(input);
        buf.flip();
        final Bytes result = XdrParserTools.readOpaque(buf, 256);
        assertEquals(33, result.length());
    }

    @Test
    void readOpaque_exceedsMaxSize() {
        // length=10, maxSize=5
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0A,
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                (byte) 0x06, (byte) 0x07, (byte) 0x08, (byte) 0x09, (byte) 0x0A,
                (byte) 0x00, (byte) 0x00);
        assertThrows(ParseException.class, () -> XdrParserTools.readOpaque(buf, 5));
    }

    @Test
    void readOpaque_nonZeroPadding() {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x42, (byte) 0xFF, (byte) 0x00, (byte) 0x00);
        assertThrows(ParseException.class, () -> XdrParserTools.readOpaque(buf, 256));
    }


    // ================================================================================================================
    // readString / readOpaque — negative length

    @Test
    void readString_negativeLength() {
        // 0xFF FF FF FF = -1 as a signed int
        final BufferedData buf = bufOf(
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readString(buf, 256));
    }

    @Test
    void readOpaque_negativeLength() {
        // 0xFF FF FF FF = -1 as a signed int
        final BufferedData buf = bufOf(
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
        assertThrows(ParseException.class, () -> XdrParserTools.readOpaque(buf, 256));
    }

    // ================================================================================================================
    // Position advancement

    @Test
    void readString_positionAdvancedPastPadding() throws ParseException {
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00);
        XdrParserTools.readString(buf, 256);
        assertEquals(8, buf.position());
    }

    @Test
    void readOpaque_positionAdvancedPastPadding() throws ParseException {
        // 5-byte opaque: 4 (len) + 5 (data) + 3 (pad) = 12
        final BufferedData buf = bufOf(
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x05,
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04, (byte) 0x05,
                (byte) 0x00, (byte) 0x00, (byte) 0x00);
        XdrParserTools.readOpaque(buf, 256);
        assertEquals(12, buf.position());
    }

    @Test
    void readAndValidatePadding_advancesCorrectly() throws ParseException {
        // readAndValidatePadding(in, 1) → reads 3 bytes
        final BufferedData buf1 = bufOf((byte) 0x00, (byte) 0x00, (byte) 0x00);
        XdrParserTools.readAndValidatePadding(buf1, 1);
        assertEquals(3, buf1.position());

        // readAndValidatePadding(in, 4) → reads 0 bytes
        final BufferedData buf2 = BufferedData.allocate(4);
        XdrParserTools.readAndValidatePadding(buf2, 4);
        assertEquals(0, buf2.position());
    }
}
