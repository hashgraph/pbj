// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * RFC 4506 binary compliance tests. Verifies exact byte-level encoding against
 * hand-computed reference sequences from the RFC specification.
 */
class XdrRfcComplianceTest {

    // ================================================================================================================
    // HELPER

    private static byte[] toByteArray(final BufferedData buf) {
        buf.flip();
        final byte[] result = new byte[(int) buf.remaining()];
        buf.readBytes(result);
        return result;
    }

    private static BufferedData write(final int capacity) {
        return BufferedData.allocate(capacity);
    }

    // ================================================================================================================
    // RFC 4506 §4.1 — Integer

    @Test
    void rfc_integer_example() {
        // 262144 = 0x00040000
        final BufferedData buf = write(4);
        XdrWriterTools.writeInt(buf, 262144);
        assertArrayEquals(new byte[] {0x00, 0x04, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.4 — Boolean

    @Test
    void rfc_bool_true() {
        final BufferedData buf = write(4);
        XdrWriterTools.writeBool(buf, true);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x01}, toByteArray(buf));
    }

    @Test
    void rfc_bool_false() {
        final BufferedData buf = write(4);
        XdrWriterTools.writeBool(buf, false);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.5 — Hyper Integer

    @Test
    void rfc_hyper_example() {
        // 1099511627776 = 0x0000010000000000
        final BufferedData buf = write(8);
        XdrWriterTools.writeHyper(buf, 1099511627776L);
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.6 — Float

    @Test
    void rfc_float_example() {
        final BufferedData buf = write(4);
        XdrWriterTools.writeFloat(buf, 0.0f);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.7 — Double

    @Test
    void rfc_double_zero() {
        final BufferedData buf = write(8);
        XdrWriterTools.writeDouble(buf, 0.0);
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.9 — Fixed-length opaque
    // Fixed opaque: raw bytes + 0-3 zero padding (no length prefix)

    @Test
    void rfc_fixedOpaque_aligned() {
        // opaque[4] = {0x01, 0x02, 0x03, 0x04} — aligned, no padding
        final BufferedData buf = write(4);
        buf.writeBytes(new byte[] {0x01, 0x02, 0x03, 0x04});
        assertArrayEquals(new byte[] {0x01, 0x02, 0x03, 0x04}, toByteArray(buf));
    }

    @Test
    void rfc_fixedOpaque_unaligned() {
        // opaque[5] = {0x01..0x05} + 3 zero pad bytes
        final BufferedData buf = write(8);
        buf.writeBytes(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05});
        XdrWriterTools.writePadding(buf, 5);
        assertArrayEquals(
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    @Test
    void rfc_fixedOpaque_1byte() {
        // opaque[1] = {0xFF} + 3 zero pad bytes
        final BufferedData buf = write(4);
        buf.writeByte((byte) 0xFF);
        XdrWriterTools.writePadding(buf, 1);
        assertArrayEquals(new byte[] {(byte) 0xFF, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.10 — Variable-length opaque

    @Test
    void rfc_varOpaque_empty() {
        final BufferedData buf = write(4);
        XdrWriterTools.writeOpaque(buf, Bytes.EMPTY);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    @Test
    void rfc_varOpaque_5bytes() {
        // {0x01..0x05} → 00 00 00 05 + data + 3 zero pad
        final BufferedData buf = write(12);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05}));
        assertArrayEquals(
                new byte[] {
                    0x00, 0x00, 0x00, 0x05,
                    0x01, 0x02, 0x03, 0x04, 0x05,
                    0x00, 0x00, 0x00
                },
                toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.11 — String

    @Test
    void rfc_string_sillyprog() {
        // "sillyprog" — 9 bytes, pad 3
        final BufferedData buf = write(16);
        XdrWriterTools.writeString(buf, "sillyprog");
        final byte[] result = toByteArray(buf);
        // length prefix = 9
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x09}, java.util.Arrays.copyOfRange(result, 0, 4));
        // data bytes
        assertArrayEquals("sillyprog".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.util.Arrays.copyOfRange(result, 4, 13));
        // 3 padding bytes, all zero
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00}, java.util.Arrays.copyOfRange(result, 13, 16));
        assertEquals(16, result.length);
    }

    // ================================================================================================================
    // RFC 4506 §4.13 — Variable-length array

    @Test
    void rfc_varArray_3ints() {
        // int<5> = {1, 2, 3} → 00 00 00 03 + 3 ints
        final BufferedData buf = write(16);
        XdrWriterTools.writeInt(buf, 3); // count
        XdrWriterTools.writeInt(buf, 1);
        XdrWriterTools.writeInt(buf, 2);
        XdrWriterTools.writeInt(buf, 3);
        assertArrayEquals(
                new byte[] {
                    0x00, 0x00, 0x00, 0x03,
                    0x00, 0x00, 0x00, 0x01,
                    0x00, 0x00, 0x00, 0x02,
                    0x00, 0x00, 0x00, 0x03
                },
                toByteArray(buf));
    }

    @Test
    void rfc_varArray_empty() {
        // int<5> = {} → 00 00 00 00
        final BufferedData buf = write(4);
        XdrWriterTools.writeInt(buf, 0); // count = 0
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.14 — Struct (fields encoded sequentially)

    @Test
    void rfc_struct_sequential() {
        // struct { int a=1; int b=2; } → 00 00 00 01 00 00 00 02
        final BufferedData buf = write(8);
        XdrWriterTools.writeInt(buf, 1); // field a
        XdrWriterTools.writeInt(buf, 2); // field b
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02}, toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.15 — Discriminated union

    @Test
    void rfc_union_arm0() {
        // union switch(0) { case 0: int=42 } → 00 00 00 00 00 00 00 2A
        final BufferedData buf = write(8);
        XdrWriterTools.writeInt(buf, 0);  // discriminant
        XdrWriterTools.writeInt(buf, 42); // arm value
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2A}, toByteArray(buf));
    }

    @Test
    void rfc_union_arm1() {
        // union switch(1) { case 1: string="hi" }
        // → 00 00 00 01 | 00 00 00 02 | 68 69 | 00 00
        final BufferedData buf = write(12);
        XdrWriterTools.writeInt(buf, 1);     // discriminant
        XdrWriterTools.writeString(buf, "hi"); // arm value: len=2, "hi", pad 2
        assertArrayEquals(
                new byte[] {
                    0x00, 0x00, 0x00, 0x01,
                    0x00, 0x00, 0x00, 0x02,
                    0x68, 0x69,
                    0x00, 0x00
                },
                toByteArray(buf));
    }

    // ================================================================================================================
    // RFC 4506 §4.19 — Optional-data (pointer)

    @Test
    void rfc_optional_present() {
        // *int = 42 → 00 00 00 01 | 00 00 00 2A
        final BufferedData buf = write(8);
        XdrWriterTools.writePresence(buf, true);
        XdrWriterTools.writeInt(buf, 42);
        assertArrayEquals(
                new byte[] {0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x2A}, toByteArray(buf));
    }

    @Test
    void rfc_optional_absent() {
        // *int = absent → 00 00 00 00
        final BufferedData buf = write(4);
        XdrWriterTools.writePresence(buf, false);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x00, 0x00}, toByteArray(buf));
    }

    // ================================================================================================================
    // 4-byte alignment invariant

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 10, 33, 100, 255})
    void alignment_totalBytesMultipleOf4(final int dataLength) {
        final BufferedData buf = BufferedData.allocate(dataLength + 8);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(new byte[dataLength]));
        assertEquals(0, buf.position() % 4, "XDR output must be 4-byte aligned");
    }

    // ================================================================================================================
    // Padding bytes are zero

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 6, 7})
    void padding_bytesAreZero(final int dataLength) {
        final BufferedData buf = BufferedData.allocate(dataLength + 8);
        XdrWriterTools.writeOpaque(buf, Bytes.wrap(new byte[dataLength]));
        final byte[] bytes = toByteArray(buf);
        final int paddingStart = 4 + dataLength; // after length prefix + data
        for (int i = paddingStart; i < bytes.length; i++) {
            assertEquals(0, bytes[i], "Padding byte at offset " + i + " must be zero");
        }
    }
}
