package com.hedera.pbj.intergration.test;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FieldsNonNullTest {
    @Test
    void testBytesNeverNull() {
        MessageWithBytes msg;

        msg = MessageWithBytes.DEFAULT;
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());

        msg = new MessageWithBytes(null);
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());

        msg = new MessageWithBytes.Builder(null).build();
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());

        msg = MessageWithBytes.newBuilder().bytesField(null).build();
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());
    }

    @Test
    void testStringNeverNull() {
        MessageWithString msg;

        msg = MessageWithString.DEFAULT;
        assertNotNull(msg.aTestString());
        assertEquals("", msg.aTestString());

        msg = new MessageWithString(null);
        assertNotNull(msg.aTestString());
        assertEquals("", msg.aTestString());

        msg = new MessageWithString.Builder(null).build();
        assertNotNull(msg.aTestString());
        assertEquals("", msg.aTestString());

        msg = MessageWithString.newBuilder().aTestString(null).build();
        assertNotNull(msg.aTestString());
        assertEquals("", msg.aTestString());
    }
}
