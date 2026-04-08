// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.api.Test;

/**
 * Tests that XDR output contains only the expected bytes (field data and presence flags)
 * with no extra or unknown fields.
 */
class XdrUnknownFieldsTest {

    /**
     * A non-default TimestampTest(seconds=1, nanos=2) should serialize to exactly:
     * - 4 bytes presence flag for seconds (=1)
     * - 8 bytes hyper for seconds (=1L)
     * - 4 bytes presence flag for nanos (=1)
     * - 4 bytes int for nanos (=2)
     * = 20 bytes total
     */
    @Test
    void xdrDoesNotWriteUnknownFields() {
        TimestampTest msg = new TimestampTest(1L, 2);
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        assertEquals(20, xdr.length(), "XDR output must contain only field data, no extra bytes");
    }

    /**
     * A default object (all zero/null values) should serialize only the presence flags
     * (absence flags, all zero) with no field data.
     * - 4 bytes presence=0 for seconds
     * - 4 bytes presence=0 for nanos
     * = 8 bytes total
     */
    @Test
    void defaultObject_xdrContainsOnlyPresenceFlags() {
        TimestampTest msg = TimestampTest.DEFAULT;
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        assertEquals(8, xdr.length(), "XDR default object must write only presence flags (8 bytes)");
    }

    /**
     * Verify the presence flags for the default object are all zeros.
     */
    @Test
    void defaultObject_presenceFlagsAreZero() {
        TimestampTest msg = TimestampTest.DEFAULT;
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        // All 8 bytes must be zero (two absent presence flags)
        byte[] bytes = new byte[8];
        xdr.getBytes(0, bytes, 0, 8);
        for (int i = 0; i < 8; i++) {
            assertEquals(0, bytes[i], "Byte " + i + " of XDR default object must be zero");
        }
    }
}
