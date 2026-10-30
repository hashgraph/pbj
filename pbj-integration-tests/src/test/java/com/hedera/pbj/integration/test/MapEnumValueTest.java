// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MapColor;
import com.hedera.pbj.test.proto.pbj.MessageWithEnumMaps;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Maps with enum values store the values' protobuf ordinals, so they parse, write and round-trip like protoc's, including
 * the default value and values this version of the enum does not know.
 */
final class MapEnumValueTest {

    private static byte[] protoc(final com.hedera.pbj.test.proto.java.MessageWithEnumMaps message) {
        return message.toByteArray();
    }

    @Test
    void valuesAreProtobufOrdinals() throws Exception {
        final MessageWithEnumMaps message = new MessageWithEnumMaps(
                Map.of(1, MapColor.MAP_COLOR_RED.protoOrdinal(), 2, MapColor.MAP_COLOR_GREEN.protoOrdinal()),
                Map.of("a", MapColor.MAP_COLOR_GREEN.protoOrdinal()));
        final Bytes bytes = MessageWithEnumMaps.PROTOBUF.toBytes(message);
        final var parsedByProtoc = com.hedera.pbj.test.proto.java.MessageWithEnumMaps.parseFrom(bytes.toByteArray());
        assertEquals(
                Map.of(
                        1,
                        com.hedera.pbj.test.proto.java.MapColor.MAP_COLOR_RED,
                        2,
                        com.hedera.pbj.test.proto.java.MapColor.MAP_COLOR_GREEN),
                parsedByProtoc.getMapInt32ToColorMap());
        assertArrayEquals(protoc(parsedByProtoc), bytes.toByteArray());
        assertEquals(message, MessageWithEnumMaps.PROTOBUF.parse(bytes));
    }

    @Test
    void theDefaultValueIsWrittenAsProtocWritesIt() throws Exception {
        final var fromProtoc = com.hedera.pbj.test.proto.java.MessageWithEnumMaps.newBuilder()
                .putMapInt32ToColorValue(7, 0)
                .putMapStringToColorValue("", 0)
                .build();
        final MessageWithEnumMaps message = MessageWithEnumMaps.PROTOBUF.parse(Bytes.wrap(protoc(fromProtoc)));
        assertEquals(Map.of(7, 0), message.mapInt32ToColor());
        assertEquals(Map.of("", 0), message.mapStringToColor());
        // protoc writes both the key and the value of every map entry, default or not
        assertArrayEquals(
                protoc(fromProtoc),
                MessageWithEnumMaps.PROTOBUF.toBytes(message).toByteArray());
        assertEquals(protoc(fromProtoc).length, MessageWithEnumMaps.PROTOBUF.measureRecord(message));
    }

    @Test
    void unrecognizedValuesRoundTrip() throws Exception {
        // 99 and -5 are not values of MapColor: an enum from a newer schema, or a malformed message
        final var fromProtoc = com.hedera.pbj.test.proto.java.MessageWithEnumMaps.newBuilder()
                .putMapInt32ToColorValue(1, 99)
                .putMapInt32ToColorValue(2, -5)
                .putMapStringToColorValue("b", 99)
                .build();
        final byte[] bytes = protoc(fromProtoc);
        final MessageWithEnumMaps message = MessageWithEnumMaps.PROTOBUF.parse(Bytes.wrap(bytes));
        assertEquals(Map.of(1, 99, 2, -5), message.mapInt32ToColor());
        assertEquals(Map.of("b", 99), message.mapStringToColor());
        assertEquals(
                MapColor.UNRECOGNIZED,
                MapColor.fromProtobufOrdinal(message.mapInt32ToColor().get(1)));
        // nothing is lost: the same bytes as protoc, from every writer
        assertArrayEquals(bytes, MessageWithEnumMaps.PROTOBUF.toBytes(message).toByteArray());
        final byte[] array = new byte[bytes.length];
        assertEquals(bytes.length, MessageWithEnumMaps.PROTOBUF.write(message, array, 0));
        assertArrayEquals(bytes, array);
        assertEquals(bytes.length, message.protobufSize());
    }

    @Test
    void jsonUsesTheEnumNames() throws Exception {
        final MessageWithEnumMaps message = new MessageWithEnumMaps(
                Map.of(1, MapColor.MAP_COLOR_RED.protoOrdinal()), Map.of("a", MapColor.MAP_COLOR_GREEN.protoOrdinal()));
        final String json = MessageWithEnumMaps.JSON.toJSON(message);
        assertTrue(json.contains("\"MAP_COLOR_RED\""), json);
        assertTrue(json.contains("\"MAP_COLOR_GREEN\""), json);
        assertEquals(message, MessageWithEnumMaps.JSON.parse(Bytes.wrap(json)));
    }
}
