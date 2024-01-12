package com.hedera.pbj.intergration.test;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithBytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BytesNonNullTest {
    @Test
    void testBytesNeverNull() {
        MessageWithBytes msg;

        msg = MessageWithBytes.DEFAULT;
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());

        msg = MessageWithBytes.newBuilder().bytesField(null).build();
        assertNotNull(msg.bytesField());
        assertEquals(Bytes.EMPTY, msg.bytesField());
    }
}
