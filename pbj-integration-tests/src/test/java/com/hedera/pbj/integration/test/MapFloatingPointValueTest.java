// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithFloatingPointMaps;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Map entries hold both a key and a value, and protoc writes both even when they are defaults. Floating point values
 * must be written the same way, by every writer, so the bytes match protoc's.
 */
final class MapFloatingPointValueTest {

    private static void assertWrittenAsProtocWritesIt(
            final com.hedera.pbj.test.proto.java.MessageWithFloatingPointMaps fromProtoc) throws Exception {
        final byte[] bytes = fromProtoc.toByteArray();
        final MessageWithFloatingPointMaps message = MessageWithFloatingPointMaps.PROTOBUF.parse(Bytes.wrap(bytes));
        assertArrayEquals(
                bytes, MessageWithFloatingPointMaps.PROTOBUF.toBytes(message).toByteArray());
        final byte[] array = new byte[bytes.length];
        assertEquals(bytes.length, MessageWithFloatingPointMaps.PROTOBUF.write(message, array, 0));
        assertArrayEquals(bytes, array);
        assertEquals(bytes.length, MessageWithFloatingPointMaps.PROTOBUF.measureRecord(message));
        assertEquals(bytes.length, message.protobufSize());
    }

    @Test
    void zeroValuesAreWritten() throws Exception {
        final var fromProtoc = com.hedera.pbj.test.proto.java.MessageWithFloatingPointMaps.newBuilder()
                .putMapInt32ToFloat(1, 0f)
                .putMapStringToDouble("a", 0d)
                .build();
        assertWrittenAsProtocWritesIt(fromProtoc);
        final MessageWithFloatingPointMaps message =
                MessageWithFloatingPointMaps.PROTOBUF.parse(Bytes.wrap(fromProtoc.toByteArray()));
        assertEquals(Map.of(1, 0f), message.mapInt32ToFloat());
        assertEquals(Map.of("a", 0d), message.mapStringToDouble());
    }

    @Test
    void negativeZeroValuesAreWritten() throws Exception {
        assertWrittenAsProtocWritesIt(com.hedera.pbj.test.proto.java.MessageWithFloatingPointMaps.newBuilder()
                .putMapInt32ToFloat(1, -0f)
                .putMapStringToDouble("a", -0d)
                .build());
    }

    @Test
    void otherValuesAreWritten() throws Exception {
        assertWrittenAsProtocWritesIt(com.hedera.pbj.test.proto.java.MessageWithFloatingPointMaps.newBuilder()
                .putMapInt32ToFloat(0, 1.5f)
                .putMapInt32ToFloat(2, Float.NaN)
                .putMapStringToDouble("", -2.25d)
                .putMapStringToDouble("b", Double.MAX_VALUE)
                .build());
    }
}
