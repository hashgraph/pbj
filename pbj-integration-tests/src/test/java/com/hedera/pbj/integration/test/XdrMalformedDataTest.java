// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.api.Test;

/**
 * Tests that truncated, partial, or otherwise malformed XDR data causes a parse exception.
 */
class XdrMalformedDataTest {

    /**
     * Completely empty input must throw an exception when parsing a non-empty message type.
     */
    @Test
    void emptyInput_throwsException() {
        assertThrows(Exception.class, () -> TimestampTest.XDR.parse(Bytes.EMPTY.toReadableSequentialData()));
    }

    /**
     * Truncated input (only 4 bytes: presence=1 for seconds but no hyper data follows)
     * must throw an exception.
     */
    @Test
    void truncatedData_throwsException() {
        TimestampTest msg = new TimestampTest(1234L, 567);
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        // Keep only the first 4 bytes (presence flag for seconds)
        byte[] truncated = new byte[4];
        xdr.getBytes(0, truncated, 0, 4);
        assertThrows(
                Exception.class,
                () -> TimestampTest.XDR.parse(Bytes.wrap(truncated).toReadableSequentialData()));
    }

    /**
     * Partial hyper: presence=1 for seconds but only 4 of the 8 hyper bytes follow.
     * Must throw an exception.
     */
    @Test
    void partialHyper_throwsException() {
        // presence=1 (4 bytes) + only 4 bytes of an 8-byte hyper
        byte[] partialHyper = new byte[] {
            0x00, 0x00, 0x00, 0x01, // presence = 1 (seconds present)
            0x00, 0x00, 0x00, 0x00 // only 4 bytes of the 8-byte hyper
        };
        assertThrows(
                Exception.class,
                () -> TimestampTest.XDR.parse(Bytes.wrap(partialHyper).toReadableSequentialData()));
    }

    /**
     * A presence flag with a value other than 0 or 1 is invalid per RFC 4506.
     * Must throw an exception.
     */
    @Test
    void invalidPresenceFlag_throwsException() {
        // presence=2 (invalid — only 0 and 1 are valid)
        byte[] invalidPresence = new byte[] {0x00, 0x00, 0x00, 0x02 // invalid presence flag
        };
        assertThrows(
                Exception.class,
                () -> TimestampTest.XDR.parse(Bytes.wrap(invalidPresence).toReadableSequentialData()));
    }

    /**
     * Canonical encoding violation: presence=1 with a zero (default) value must throw ParseException.
     */
    @Test
    void canonicalViolation_presenceTrueWithZeroValue_throwsParseException() {
        // presence=1 (4 bytes) + hyper=0 (8 bytes) — violates canonical encoding
        byte[] nonCanonical = new byte[] {
            0x00, 0x00, 0x00, 0x01, // presence = 1
            0x00, 0x00, 0x00, 0x00, // hyper = 0 (high 4 bytes)
            0x00, 0x00, 0x00, 0x00 // hyper = 0 (low 4 bytes)
        };
        assertThrows(
                Exception.class,
                () -> TimestampTest.XDR.parse(Bytes.wrap(nonCanonical).toReadableSequentialData()));
    }
}
