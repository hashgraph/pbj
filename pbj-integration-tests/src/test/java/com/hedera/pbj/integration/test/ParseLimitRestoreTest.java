// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithMaps;
import org.junit.jupiter.api.Test;

/**
 * Generated parsers narrow the input's limit to each nested message and don't restore it themselves when a nested
 * parse fails; {@code Codec.parse} restores the caller's limit instead. Callers must see the same limit as before.
 */
final class ParseLimitRestoreTest {

    /** A TransactionID whose nested Timestamp, the first field, holds a field with the invalid wire type 7. */
    private static byte[] corruptNestedMessage() {
        final TransactionID id = TransactionID.newBuilder()
                .transactionValidStart(new Timestamp(1_700_000_000L, 123))
                .accountID(AccountID.newBuilder().accountNum(1001).build())
                .build();
        final byte[] bytes = TransactionID.PROTOBUF.toBytes(id).toByteArray();
        // bytes[0] is the tag of field 1 (the Timestamp), bytes[1] its length, bytes[2] the Timestamp's first tag
        assertEquals(0x0A, bytes[0]);
        bytes[2] = 0x0F; // field 1, wire type 7
        return bytes;
    }

    @Test
    void limitIsRestoredWhenANestedParseFails() {
        final byte[] message = corruptNestedMessage();
        // the message followed by bytes the caller owns, which the caller's limit excludes
        final byte[] padded = new byte[message.length + 16];
        System.arraycopy(message, 0, padded, 0, message.length);
        final BufferedData input = BufferedData.wrap(padded);
        input.limit(message.length);
        assertThrows(ParseException.class, () -> TransactionID.PROTOBUF.parse(input));
        assertEquals(message.length, input.limit());
    }

    @Test
    void limitIsRestoredWhenAMapEntryFails() {
        // mapInt32ToString = 1, an entry whose second field has the invalid wire type 7
        final byte[] message = {0x0A, 0x04, 0x08, 0x01, 0x0F, 0x00};
        final byte[] padded = new byte[message.length + 16];
        System.arraycopy(message, 0, padded, 0, message.length);
        final BufferedData input = BufferedData.wrap(padded);
        input.limit(message.length);
        assertThrows(ParseException.class, () -> MessageWithMaps.PROTOBUF.parse(input));
        assertEquals(message.length, input.limit());
    }

    @Test
    void limitIsRestoredWhenParsingFromBytesFails() {
        final Bytes bytes = Bytes.wrap(corruptNestedMessage());
        assertThrows(ParseException.class, () -> TransactionID.PROTOBUF.parse(bytes));
        // and the same bytes still parse to the same failure, so nothing shared was changed
        assertThrows(ParseException.class, () -> TransactionID.PROTOBUF.parse(bytes));
    }
}
