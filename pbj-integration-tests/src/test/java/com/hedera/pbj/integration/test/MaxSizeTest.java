// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class MaxSizeTest {
    @Test
    void testBytesMaxSize() throws Exception {
        final Bytes bytes = Bytes.wrap("test string long enough to hold Integer.MAX_VALUE as VarInt");
        final MessageWithBytes msg = MessageWithBytes.newBuilder().bytesField(bytes).build();
        final BufferedData data = BufferedData.allocate(MessageWithBytes.PROTOBUF.measureRecord(msg));
        MessageWithBytes.PROTOBUF.write(msg, data);

        // That's where the Bytes length is stored
        data.position(1);
        // Specify a very large size for the Bytes
        // We assume that PBJ's default maxSize is < Integer.MAX_VALUE.
        data.writeVarInt(Integer.MAX_VALUE, false);
        data.reset();

        assertThrows(ParseException.class, () -> MessageWithBytes.PROTOBUF.parse(data));
    }

    @Test
    void testStringMaxSize() throws Exception {
        final String string = "test string long enough to hold Integer.MAX_VALUE as VarInt";
        final MessageWithString msg = MessageWithString.newBuilder().aTestString(string).build();
        final BufferedData data = BufferedData.allocate(MessageWithString.PROTOBUF.measureRecord(msg));
        MessageWithString.PROTOBUF.write(msg, data);

        // That's where the string length is stored
        data.position(1);
        // Specify a very large size for the string
        // We assume that PBJ's default maxSize is < Integer.MAX_VALUE.
        data.writeVarInt(Integer.MAX_VALUE, false);
        data.reset();

        assertThrows(ParseException.class, () -> MessageWithString.PROTOBUF.parse(data));
    }
}
