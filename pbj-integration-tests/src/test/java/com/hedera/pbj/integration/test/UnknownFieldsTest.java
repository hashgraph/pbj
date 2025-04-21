// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.UnknownField;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytesAndString;
import org.junit.jupiter.api.Test;

public class UnknownFieldsTest {
    private static final Bytes TEST_BYTES = Bytes.wrap("test bytes");
    private static final String TEST_STRING = "test string";

    @Test
    void testUnknownFields() throws Exception {
        // write MessageWithBytesAndString
        MessageWithBytesAndString bytesAndString = new MessageWithBytesAndString(TEST_BYTES, TEST_STRING);
        final BufferedData bd = BufferedData.allocate(512);
        MessageWithBytesAndString.PROTOBUF.write(bytesAndString, bd);

        // then read it as MessageWithBytes with unknown fields
        bd.flip();
        final MessageWithBytes bytes = MessageWithBytes.PROTOBUF.parse(bd, false, true, 16);

        assertFalse(bytes.getUnknownFields().isEmpty());
        assertEquals(1, bytes.getUnknownFields().size());
        UnknownField uf = bytes.getUnknownFields().get(0);

        assertEquals(2, uf.field());
        assertEquals(ProtoConstants.WIRE_TYPE_DELIMITED, uf.wireType());
        assertEquals(TEST_STRING.length(), uf.bytes().getVarInt(0, false));
        assertEquals(TEST_STRING, uf.bytes().asUtf8String(1, uf.bytes().length() - 1));
    }

    @Test
    void testUnknownFieldsRoundTrip() throws Exception {
        // write MessageWithBytesAndString
        MessageWithBytesAndString msg1 = new MessageWithBytesAndString(TEST_BYTES, TEST_STRING);
        final Bytes bytes1 = MessageWithBytesAndString.PROTOBUF.toBytes(msg1);

        // then read it as MessageWithBytes with unknown fields
        final MessageWithBytes msg2 =
                MessageWithBytes.PROTOBUF.parse(bytes1.toReadableSequentialData(), false, true, 16);
        assertEquals(1, msg2.getUnknownFields().size());

        // now write it again as MessageWithBytes - it doesn't know about the string, but it has unknown fields!
        final Bytes bytes2 = MessageWithBytes.PROTOBUF.toBytes(msg2);

        // now read it as MessageWithBytesAndString, w/o even enabling unknown fields
        final MessageWithBytesAndString msg3 =
                MessageWithBytesAndString.PROTOBUF.parse(bytes2.toReadableSequentialData());

        assertEquals(msg1, msg3);
    }
}
