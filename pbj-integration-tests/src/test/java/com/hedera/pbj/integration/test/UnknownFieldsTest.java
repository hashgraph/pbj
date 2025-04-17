// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.UnknownField;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytesAndString;
import org.junit.jupiter.api.Test;

public class UnknownFieldsTest {
    private static final String TEST_STRING = "test string";

    @Test
    void testUnknownFields() throws Exception {
        // write MessageWithBytesAndString
        MessageWithBytesAndString bytesAndString = new MessageWithBytesAndString(Bytes.wrap("test bytes"), TEST_STRING);
        final BufferedData bd = BufferedData.allocate(512);
        MessageWithBytesAndString.PROTOBUF.write(bytesAndString, bd);

        // then read it as MessageWithBytes with unknown fields
        bd.flip();
        final MessageWithBytes bytes = MessageWithBytes.PROTOBUF.parse(bd, false, true, 16);

        assertFalse(bytes.getUnknownFields().isEmpty());
        assertEquals(1, bytes.getUnknownFields().size());
        assertTrue(bytes.getUnknownFields().containsKey(2));
        UnknownField uf = bytes.getUnknownFields().get(2);

        assertEquals(ProtoConstants.WIRE_TYPE_DELIMITED, uf.wireType());
        assertEquals(1, uf.bytes().size());
        Bytes ub = uf.bytes().get(0);
        assertEquals(TEST_STRING.length(), ub.getVarInt(0, false));
        assertEquals(TEST_STRING, ub.asUtf8String(1, ub.length() - 1));
    }
}
