package com.hedera.pbj.integration.test;

import com.hedera.hapi.node.base.FeeSchedule;
import com.hedera.hapi.node.base.TransactionFeeSchedule;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import com.hedera.pbj.test.proto.pbj.MessageWithString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void testRepeatedNeverNull() {
        FeeSchedule msg;

        msg = FeeSchedule.DEFAULT;
        assertNotNull(msg.transactionFeeSchedule());
        assertTrue(msg.transactionFeeSchedule().isEmpty());

        msg = new FeeSchedule(null, null);
        assertNotNull(msg.transactionFeeSchedule());
        assertTrue(msg.transactionFeeSchedule().isEmpty());

        msg = new FeeSchedule.Builder(null, null).build();
        assertNotNull(msg.transactionFeeSchedule());
        assertTrue(msg.transactionFeeSchedule().isEmpty());

        msg = FeeSchedule.newBuilder().transactionFeeSchedule((TransactionFeeSchedule[])null).build();
        assertNotNull(msg.transactionFeeSchedule());
        assertTrue(msg.transactionFeeSchedule().isEmpty());
    }
}
