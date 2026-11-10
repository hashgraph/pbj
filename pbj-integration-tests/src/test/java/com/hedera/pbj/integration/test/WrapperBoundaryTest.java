// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.codec.EverythingProtoCodec;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Bytes left over inside the declared length of a google.protobuf wrapper (BoolValue, UInt32Value, ...) must never be
 * re-read as fields of the enclosing message.
 */
class WrapperBoundaryTest {
    private static final EverythingProtoCodec CODEC = new EverythingProtoCodec();

    /** A well-formed {@code int32Number = 42} (field 1) of the enclosing Everything message. */
    private static final byte[] TRAILING = {0x08, 0x2A};

    // fieldNumber, wireType of the wrapper's inner field 1, inner payload (hex)
    @ParameterizedTest
    @CsvSource({
        "1001, 0, 08 01", // Int32Value
        "1002, 0, 08 06", // UInt32Value
        "1004, 0, 08 01", // Int64Value
        "1005, 0, 08 01", // UInt64Value
        "1007, 5, 0D 00 00 80 3F", // FloatValue
        "1008, 1, 09 00 00 00 00 00 00 F0 3F", // DoubleValue
        "1009, 0, 08 01", // BoolValue
        "10010, 2, 0A 01 61", // BytesValue
        "10011, 2, 0A 01 61", // StringValue
        "100019, 0, 08 06", // UInt32Value in oneof
        "100024, 0, 08 01", // BoolValue in oneof
    })
    void trailingBytesInsideWrapperAreRejected(final int fieldNumber, final int ignored, final String innerHex) {
        final byte[] inner = hex(innerHex);
        final byte[] clean = wrapperField(fieldNumber, inner);
        // control: the canonical encoding still parses
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> CODEC.parse(Bytes.wrap(clean).toReadableSequentialData()));

        final byte[] padded = new byte[inner.length + TRAILING.length];
        System.arraycopy(inner, 0, padded, 0, inner.length);
        System.arraycopy(TRAILING, 0, padded, inner.length, TRAILING.length);
        final byte[] malicious = wrapperField(fieldNumber, padded);
        assertThrows(
                ParseException.class, () -> CODEC.parse(Bytes.wrap(malicious).toReadableSequentialData()));
    }

    @Test
    void innerFieldNumberZeroIsRejected() {
        // BoolValue whose inner tag is 0x00 (field 0, illegal)
        final byte[] malicious = wrapperField(1009, new byte[] {0x00, 0x01});
        assertThrows(
                ParseException.class, () -> CODEC.parse(Bytes.wrap(malicious).toReadableSequentialData()));
    }

    @Test
    void innerReservedWireTypeIsRejected() {
        // BoolValue whose inner tag is 0x0F (field 1, undefined wire type 7)
        final byte[] malicious = wrapperField(1009, new byte[] {0x0F, 0x01});
        assertThrows(
                ParseException.class, () -> CODEC.parse(Bytes.wrap(malicious).toReadableSequentialData()));
    }

    @Test
    void wrongInnerTagInsideWrapperIsRejected() {
        // BoolValue whose inner field number is 2 instead of 1
        final byte[] malicious = wrapperField(1009, new byte[] {0x10, 0x01});
        assertThrows(
                ParseException.class, () -> CODEC.parse(Bytes.wrap(malicious).toReadableSequentialData()));
    }

    @Test
    void wrapperLengthBeyondEnclosingLimitIsRejected() {
        final byte[] truncated = {(byte) 0xCA, 0x3E, 0x05, 0x08, 0x01}; // field 1009, declared length 5, only 2 bytes
        assertThrows(
                ParseException.class, () -> CODEC.parse(Bytes.wrap(truncated).toReadableSequentialData()));
    }

    @Test
    void canonicalWrapperStillParses() throws ParseException {
        final Everything parsed = CODEC.parse(
                Bytes.wrap(wrapperField(1009, new byte[] {0x08, 0x01})).toReadableSequentialData());
        assertEquals(Boolean.TRUE, parsed.boolBoxed());
    }

    private static byte[] wrapperField(final int fieldNumber, final byte[] inner) {
        final var out = new ByteArrayOutputStream();
        writeVarInt(out, ((long) fieldNumber << 3) | 2);
        writeVarInt(out, inner.length);
        out.writeBytes(inner);
        return out.toByteArray();
    }

    private static void writeVarInt(final ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static byte[] hex(final String s) {
        final String[] parts = s.trim().split("\\s+");
        final byte[] b = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            b[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return b;
    }
}
