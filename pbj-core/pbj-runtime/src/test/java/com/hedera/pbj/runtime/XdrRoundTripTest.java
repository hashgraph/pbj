// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class XdrRoundTripTest {

    // ================================================================================================================
    // HELPERS

    private static BufferedData newBuffer() {
        return BufferedData.allocate(256);
    }

    // ================================================================================================================
    // int round-trip

    static Stream<Integer> intValues() {
        return Stream.of(0, 1, -1, 42, -42, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @MethodSource("intValues")
    void roundTrip_int(final int value) {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeInt(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readInt(buf));
    }

    // ================================================================================================================
    // hyper round-trip

    static Stream<Long> hyperValues() {
        return Stream.of(0L, 1L, -1L, 42L, -42L, Long.MIN_VALUE, Long.MAX_VALUE, 1099511627776L);
    }

    @ParameterizedTest
    @MethodSource("hyperValues")
    void roundTrip_hyper(final long value) {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeHyper(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readHyper(buf));
    }

    // ================================================================================================================
    // float round-trip

    static Stream<Float> floatValues() {
        return Stream.of(0.0f, 1.0f, -1.0f, -0.0f, Float.MAX_VALUE, Float.MIN_VALUE,
                Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("floatValues")
    void roundTrip_float(final float value) {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeFloat(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readFloat(buf));
    }

    @Test
    void roundTrip_float_nan() {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeFloat(buf, Float.NaN);
        buf.flip();
        assertTrue(Float.isNaN(XdrParserTools.readFloat(buf)));
    }

    // ================================================================================================================
    // double round-trip

    static Stream<Double> doubleValues() {
        return Stream.of(0.0, 1.0, -1.0, -0.0, Double.MAX_VALUE, Double.MIN_VALUE,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @ParameterizedTest
    @MethodSource("doubleValues")
    void roundTrip_double(final double value) {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeDouble(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readDouble(buf));
    }

    @Test
    void roundTrip_double_nan() {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeDouble(buf, Double.NaN);
        buf.flip();
        assertTrue(Double.isNaN(XdrParserTools.readDouble(buf)));
    }

    // ================================================================================================================
    // bool round-trip

    static Stream<Boolean> boolValues() {
        return Stream.of(true, false);
    }

    @ParameterizedTest
    @MethodSource("boolValues")
    void roundTrip_bool(final boolean value) throws ParseException {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeBool(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readBool(buf));
    }

    // ================================================================================================================
    // enum round-trip

    static Stream<Integer> enumValues() {
        return Stream.of(0, 1, 2, -1, Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @MethodSource("enumValues")
    void roundTrip_enum(final int value) {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeEnum(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readEnum(buf));
    }

    // ================================================================================================================
    // presence round-trip

    @ParameterizedTest
    @MethodSource("boolValues")
    void roundTrip_presence(final boolean value) throws ParseException {
        final BufferedData buf = newBuffer();
        XdrWriterTools.writePresence(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readPresence(buf));
    }

    // ================================================================================================================
    // string round-trip — all padding cases (lengths 0..8)

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void roundTrip_string_byLength(final int length) throws ParseException {
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append((char) ('A' + (i % 26)));
        final String value = sb.toString();

        final BufferedData buf = newBuffer();
        XdrWriterTools.writeString(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readString(buf, 256));
    }

    @Test
    void roundTrip_string_multibyte_utf8() throws ParseException {
        // "日本語" — 9 UTF-8 bytes
        final String value = "日本語";
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeString(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readString(buf, 256));
    }

    @Test
    void roundTrip_string_accented() throws ParseException {
        // "\u00E9" — 2 UTF-8 bytes
        final String value = "\u00E9";
        final BufferedData buf = newBuffer();
        XdrWriterTools.writeString(buf, value);
        buf.flip();
        assertEquals(value, XdrParserTools.readString(buf, 256));
    }

    // ================================================================================================================
    // opaque round-trip — all padding cases (lengths 0..8)

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void roundTrip_opaque_byLength(final int length) throws ParseException {
        final byte[] data = new byte[length];
        for (int i = 0; i < length; i++) data[i] = (byte) (i + 1);
        final Bytes value = Bytes.wrap(data);

        final BufferedData buf = newBuffer();
        XdrWriterTools.writeOpaque(buf, value);
        buf.flip();
        final Bytes parsed = XdrParserTools.readOpaque(buf, 256);
        assertEquals(value, parsed);
    }

    @Test
    void roundTrip_opaque_allByteValues() throws ParseException {
        final byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) data[i] = (byte) i;
        final Bytes value = Bytes.wrap(data);

        final BufferedData buf = BufferedData.allocate(512);
        XdrWriterTools.writeOpaque(buf, value);
        buf.flip();
        final Bytes parsed = XdrParserTools.readOpaque(buf, 512);
        assertEquals(value, parsed);
    }
}
