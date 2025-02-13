// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.MessageWithMessage;
import org.junit.jupiter.api.Test;

public class MaxDepthTest {
    @Test
    void testMaxDepth_depth0() throws Exception {
        MessageWithMessage msg;

        msg = MessageWithMessage.newBuilder().build();
        BufferedData bd = BufferedData.allocate(MessageWithMessage.PROTOBUF.measureRecord(msg));
        MessageWithMessage.PROTOBUF.write(msg, bd);

        // None should throw
        MessageWithMessage.PROTOBUF.parse(bd, false, 0);
        MessageWithMessage.PROTOBUF.parse(bd, false, 1);
        MessageWithMessage.PROTOBUF.parse(bd, false, 2);
    }

    @Test
    void testMaxDepth_depth1_actually0() throws Exception {
        MessageWithMessage msg;

        msg = MessageWithMessage.newBuilder()
                // NOTE: this is a "default" message, and its serialized size is zero,
                // so parse() wouldn't be called to read it, and hence the actual depth is still 0
                .message(MessageWithMessage.newBuilder().build())
                .build();
        BufferedData bd = BufferedData.allocate(MessageWithMessage.PROTOBUF.measureRecord(msg));
        MessageWithMessage.PROTOBUF.write(msg, bd);

        // None should throw
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 0);
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 1);
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 2);
    }

    @Test
    void testMaxDepth_depth2_actually1() throws Exception {
        MessageWithMessage msg;

        msg = MessageWithMessage.newBuilder()
                .message(MessageWithMessage.newBuilder()
                        // NOTE: this is a "default" message, and its serialized size is zero,
                        // so parse() wouldn't be called to read it, and hence the actual depth is only 1
                        .message(MessageWithMessage.newBuilder().build())
                        .build())
                .build();
        BufferedData bd = BufferedData.allocate(MessageWithMessage.PROTOBUF.measureRecord(msg));
        MessageWithMessage.PROTOBUF.write(msg, bd);

        // 0 should throw
        bd.reset();
        assertThrows(ParseException.class, () -> MessageWithMessage.PROTOBUF.parse(bd, false, 0));
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 1);
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 2);
    }

    @Test
    void testMaxDepth_depth3_actually2() throws Exception {
        MessageWithMessage msg;

        msg = MessageWithMessage.newBuilder()
                .message(MessageWithMessage.newBuilder()
                        .message(MessageWithMessage.newBuilder()
                                // NOTE: this is a "default" message, and its serialized size is zero,
                                // so parse() wouldn't be called to read it, and hence the actual depth is only 2
                                .message(MessageWithMessage.newBuilder().build())
                                .build())
                        .build())
                .build();
        BufferedData bd = BufferedData.allocate(MessageWithMessage.PROTOBUF.measureRecord(msg));
        MessageWithMessage.PROTOBUF.write(msg, bd);

        // 0 and 1 should throw
        bd.reset();
        assertThrows(ParseException.class, () -> MessageWithMessage.PROTOBUF.parse(bd, false, 0));
        bd.reset();
        assertThrows(ParseException.class, () -> MessageWithMessage.PROTOBUF.parse(bd, false, 1));
        bd.reset();
        MessageWithMessage.PROTOBUF.parse(bd, false, 2);
    }
}
