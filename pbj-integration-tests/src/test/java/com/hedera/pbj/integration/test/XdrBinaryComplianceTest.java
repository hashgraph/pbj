// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.api.Test;

/**
 * RFC 4506 XDR binary compliance tests.
 *
 * <p>Each test encodes a message with known field values and asserts the exact byte sequence
 * produced by the generated XDR codec matches the hand-computed expected encoding. This
 * verifies that the generated codecs faithfully implement the XDR wire format.
 */
class XdrBinaryComplianceTest {

    /**
     * Encodes a TimestampTest with two non-default scalar values and verifies the exact
     * byte sequence: presence=1 + hyper for seconds, presence=1 + int for nanos.
     */
    @Test
    void timestampTest_knownEncoding() {
        final TimestampTest msg = new TimestampTest(1234L, 567);
        final Bytes xdr = TimestampTest.XDR.toBytes(msg);
        final byte[] expected = new byte[] {
            0x00, 0x00, 0x00, 0x01,                                     // presence=1 for seconds
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04, (byte) 0xD2,     // hyper 1234
            0x00, 0x00, 0x00, 0x01,                                     // presence=1 for nanos
            0x00, 0x00, 0x02, 0x37                                      // int 567
        };
        assertArrayEquals(expected, xdr.toByteArray());
        assertEquals(expected.length, TimestampTest.XDR.measureRecord(msg));
        assertEquals(0, xdr.length() % 4);
    }

    /**
     * Encodes a TimestampTest with all default (zero) values and verifies that the XDR
     * output contains only presence=0 flags with no value bytes, resulting in 8 bytes total.
     */
    @Test
    void defaultFields_presenceZero() {
        final TimestampTest msg = new TimestampTest(0L, 0);
        final Bytes xdr = TimestampTest.XDR.toBytes(msg);
        final byte[] expected = new byte[] {
            0x00, 0x00, 0x00, 0x00,  // presence=0 for seconds
            0x00, 0x00, 0x00, 0x00   // presence=0 for nanos
        };
        assertArrayEquals(expected, xdr.toByteArray());
        assertEquals(8, xdr.length());
        assertEquals(8, TimestampTest.XDR.measureRecord(msg));
        assertEquals(0, xdr.length() % 4);
    }

    /**
     * Encodes a MessageWithString with a 5-byte string value ("hello") and verifies:
     * presence=1 (4 bytes) + length=5 (4 bytes) + UTF-8 data (5 bytes) + padding (3 bytes) = 16 bytes.
     */
    @Test
    void stringField_paddedToFourBytesBoundary() {
        final MessageWithString msg = new MessageWithString("hello");
        final Bytes xdr = MessageWithString.XDR.toBytes(msg);
        final byte[] expected = new byte[] {
            0x00, 0x00, 0x00, 0x01,                             // presence=1 for aTestString
            0x00, 0x00, 0x00, 0x05,                             // length = 5
            0x68, 0x65, 0x6C, 0x6C, 0x6F,                      // "hello" UTF-8
            0x00, 0x00, 0x00                                    // 3 padding bytes
        };
        assertArrayEquals(expected, xdr.toByteArray());
        assertEquals(16, xdr.length());
        assertEquals(16, MessageWithString.XDR.measureRecord(msg));
        assertEquals(0, xdr.length() % 4);
    }

    /**
     * Encodes a MessageWithString with an empty (default) string and verifies
     * that only a presence=0 flag is written (4 bytes total).
     */
    @Test
    void emptyString_presenceZero() {
        final MessageWithString msg = new MessageWithString("");
        final Bytes xdr = MessageWithString.XDR.toBytes(msg);
        final byte[] expected = new byte[] {
            0x00, 0x00, 0x00, 0x00   // presence=0 for aTestString
        };
        assertArrayEquals(expected, xdr.toByteArray());
        assertEquals(4, xdr.length());
        assertEquals(4, MessageWithString.XDR.measureRecord(msg));
        assertEquals(0, xdr.length() % 4);
    }

    /**
     * Verifies that measureRecord always returns the same length as toBytes for a range
     * of TimestampTest values with varying field presence.
     */
    @Test
    void measureRecord_matchesToBytesLength_timestampTest() {
        final TimestampTest[] cases = {
            new TimestampTest(0L, 0),
            new TimestampTest(1L, 0),
            new TimestampTest(0L, 1),
            new TimestampTest(Long.MAX_VALUE, Integer.MAX_VALUE),
            new TimestampTest(Long.MIN_VALUE, Integer.MIN_VALUE),
            new TimestampTest(1234L, 567),
        };
        for (final TimestampTest msg : cases) {
            final Bytes xdr = TimestampTest.XDR.toBytes(msg);
            assertEquals(
                    xdr.length(),
                    TimestampTest.XDR.measureRecord(msg),
                    "measureRecord must match toBytes length for " + msg);
            assertEquals(0, xdr.length() % 4, "XDR size must be a multiple of 4 for " + msg);
        }
    }
}
