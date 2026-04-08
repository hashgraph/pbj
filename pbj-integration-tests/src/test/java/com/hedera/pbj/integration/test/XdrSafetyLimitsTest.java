// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.api.Test;

/**
 * Tests safety limit enforcement in the XDR codec, including maxDepth.
 */
class XdrSafetyLimitsTest {

    /**
     * maxDepth=-1 must throw ParseException immediately.
     */
    @Test
    void xdrParse_maxDepthNegative_throws() {
        TimestampTest msg = new TimestampTest(1234L, 567);
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        assertThrows(ParseException.class, () ->
                TimestampTest.XDR.parse(xdr.toReadableSequentialData(), false, false, -1, Integer.MAX_VALUE));
    }

    /**
     * A reasonable maxDepth (e.g. 64) must succeed and return the original message.
     */
    @Test
    void xdrParse_normalDepth_succeeds() throws Exception {
        TimestampTest msg = new TimestampTest(1234L, 567);
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        TimestampTest parsed = TimestampTest.XDR.parse(xdr.toReadableSequentialData(), false, false, 64, Integer.MAX_VALUE);
        assertEquals(msg, parsed);
    }

    /**
     * maxDepth=1 should succeed for a flat message (no nested messages).
     */
    @Test
    void xdrParse_maxDepthOne_succeedsForFlatMessage() throws Exception {
        TimestampTest msg = new TimestampTest(9999L, 1);
        Bytes xdr = TimestampTest.XDR.toBytes(msg);
        TimestampTest parsed = TimestampTest.XDR.parse(xdr.toReadableSequentialData(), false, false, 1, Integer.MAX_VALUE);
        assertEquals(msg, parsed);
    }
}
