// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.InnerEverything;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import org.junit.jupiter.api.Test;

public class MaxSizeTest {
    @Test
    void testBytesMaxSize() throws Exception {
        final Bytes bytes = Bytes.wrap("test string long enough to hold Integer.MAX_VALUE as VarInt");
        final MessageWithBytes msg =
                MessageWithBytes.newBuilder().bytesField(bytes).build();
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
        final MessageWithString msg =
                MessageWithString.newBuilder().aTestString(string).build();
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

    @Test
    void testCustomMaxSize() throws Exception {
        final MessageWithString msg = MessageWithString.newBuilder()
                .aTestString("A reasonably long string.")
                .build();
        final BufferedData data = BufferedData.allocate(MessageWithString.PROTOBUF.measureRecord(msg));
        MessageWithString.PROTOBUF.write(msg, data);

        // First try the default maxSize:
        data.resetPosition();
        assertEquals(msg, MessageWithString.PROTOBUF.parse(data, false, false, Integer.MAX_VALUE));

        // Then try a custom, very-huge maxSize:
        data.resetPosition();
        assertEquals(msg, MessageWithString.PROTOBUF.parse(data, false, false, Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Then try a custom, large-enough maxSize:
        data.resetPosition();
        assertEquals(msg, MessageWithString.PROTOBUF.parse(data, false, false, Integer.MAX_VALUE, 666));

        // Finally, try a small, not-sufficient maxSize:
        data.resetPosition();
        assertThrows(
                ParseException.class, () -> MessageWithString.PROTOBUF.parse(data, false, false, Integer.MAX_VALUE, 6));
    }

    @Test
    void testNestedMaxSize() throws Exception {
        // This message, an inner nested message within it, as well as a field in that inner message all exceed the
        // DEFAULT_MAX_SIZE:
        final Everything everything = Everything.newBuilder()
                .innerEverything(
                        InnerEverything.newBuilder().bytesField(Bytes.wrap("1".repeat(Codec.DEFAULT_MAX_SIZE + 1))))
                .build();
        final Bytes bytes = Everything.PROTOBUF.toBytes(everything);

        // Try negative cases first:
        assertThrows(ParseException.class, () -> Everything.PROTOBUF.parse(bytes.toReadableSequentialData()));
        assertThrows(
                ParseException.class,
                () -> Everything.PROTOBUF.parse(
                        bytes.toReadableSequentialData(), false, false, Codec.DEFAULT_MAX_DEPTH, 256));
        assertThrows(
                ParseException.class,
                () -> Everything.PROTOBUF.parse(
                        bytes.toReadableSequentialData(),
                        false,
                        false,
                        Codec.DEFAULT_MAX_DEPTH,
                        Codec.DEFAULT_MAX_SIZE));
        // +1 still shouldn't work because the outer and the inner objects are still larger:
        assertThrows(
                ParseException.class,
                () -> Everything.PROTOBUF.parse(
                        bytes.toReadableSequentialData(),
                        false,
                        false,
                        Codec.DEFAULT_MAX_DEPTH,
                        Codec.DEFAULT_MAX_SIZE + 1));

        // Now try supplying a large enough maxSize to parse it:
        final Everything parsedEverything = Everything.PROTOBUF.parse(
                bytes.toReadableSequentialData(), false, false, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE * 2);

        assertEquals(everything, parsedEverything);
    }
}
