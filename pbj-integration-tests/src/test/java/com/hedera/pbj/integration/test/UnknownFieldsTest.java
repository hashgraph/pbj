// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.ProtoConstants;
import com.hedera.pbj.runtime.UnknownField;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytesAndString;
import com.hedera.pbj.test.proto.pbj.MessageWithBytesWrapper;
import org.junit.jupiter.api.Test;
import pbj.integration.tests.pbj.integration.tests.MessageWithEverythingUnknownFields;

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

    @Test
    void testEverythingRoundTrip() throws Exception {
        final Bytes everythingBytes = Everything.PROTOBUF.toBytes(EverythingTestData.EVERYTHING);

        // First, parse w/o enabling the unknown fields parsing:
        final MessageWithEverythingUnknownFields msgWithUnknownFieldsDisabled =
                MessageWithEverythingUnknownFields.PROTOBUF.parse(everythingBytes);

        // Everything doesn't know about this field, so it should've been initialized with the default int32 value:
        assertEquals(0, msgWithUnknownFieldsDisabled.knownField());
        assertEquals(0, msgWithUnknownFieldsDisabled.getUnknownFields().size());

        // Now let's enable parsing unknown fields:
        final MessageWithEverythingUnknownFields msg = MessageWithEverythingUnknownFields.PROTOBUF.parse(
                everythingBytes.toReadableSequentialData(), false, true, 16);

        assertEquals(0, msg.knownField());
        assertEquals(65, msg.getUnknownFields().size());

        // This is what EverythingTestData initializes the very first int32Number field with, and it has the lowest
        // field number, so it should be the first field in the list:
        assertEquals(1, msg.getUnknownFields().get(0).field());
        assertEquals(
                ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG,
                msg.getUnknownFields().get(0).wireType());
        assertEquals(1234, msg.getUnknownFields().get(0).bytes().getVarInt(0, false));

        // Now let's round-trip back to Everything using the MessageWithEverythingUnknownFields codec to write bytes:
        final Bytes roundTripBytes = MessageWithEverythingUnknownFields.PROTOBUF.toBytes(msg);
        final Everything everything = Everything.PROTOBUF.parse(roundTripBytes);

        // and ensure it's equal to what we started with:
        assertEquals(EverythingTestData.EVERYTHING, everything);
    }

    @Test
    public void testUnknownFieldsInInnerMessage() throws Exception {
        // write MessageWithBytesAndString
        MessageWithBytesAndString messageWithBytesAndString = new MessageWithBytesAndString(TEST_BYTES, TEST_STRING);
        final Bytes messageWithBytesAndStringBytes =
                MessageWithBytesAndString.PROTOBUF.toBytes(messageWithBytesAndString);

        // then read it as MessageWithBytes with unknown fields
        final MessageWithBytes messageWithBytes = MessageWithBytes.PROTOBUF.parse(
                messageWithBytesAndStringBytes.toReadableSequentialData(), false, true, 16);

        final MessageWithBytesWrapper messageWithBytesWrapper = new MessageWithBytesWrapper(
                new OneOf<>(MessageWithBytesWrapper.MessageValidOneOfType.MESSAGE_WITH_BYTES, messageWithBytes));
        assertFalse(
                messageWithBytesWrapper.messageWithBytes().getUnknownFields().isEmpty());
        assertEquals(1, messageWithBytesWrapper.getUnknownFields().size());

        // write to bytes to simulate sending over the wire
        final Bytes messageWithBytesWrapperBytes = MessageWithBytesWrapper.PROTOBUF.toBytes(messageWithBytesWrapper);

        // parse bytes back as a receiving user would and confirm unknown fields exist in inner message
        final MessageWithBytesWrapper parsedWrapper = MessageWithBytesWrapper.PROTOBUF.parse(
                messageWithBytesWrapperBytes.toReadableSequentialData(), false, true, 16);
        MessageWithBytes parsedBytes = parsedWrapper.messageWithBytes();
        assertFalse(parsedBytes.getUnknownFields().isEmpty());
        assertEquals(1, parsedBytes.getUnknownFields().size());

        // now confirm that user can retrieve unknown fields when using expanded message MessageWithBytesAndString
        final Bytes messageWithBytesBytes = MessageWithBytes.PROTOBUF.toBytes(parsedBytes);
        final MessageWithBytesAndString messageWithBytesAndStringParsed =
                MessageWithBytesAndString.PROTOBUF.parse(messageWithBytesBytes.toReadableSequentialData());
        assertTrue(messageWithBytesAndStringParsed.getUnknownFields().isEmpty());
        assertEquals(messageWithBytesAndString, messageWithBytesAndStringParsed);
    }
}
