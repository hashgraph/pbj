// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.MessageWithMaps;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Fields inside a map entry other than its key (1) and value (2). Before #993 the generated parser handled them with
 * the enclosing map field's tag, so an unknown field inside an entry always failed with "Bad tag", whatever
 * {@code strictMode} and {@code parseUnknownFields} said. With options on, this also covers the array parser.
 */
final class MapEntryUnknownFieldTest {

    private static Bytes bytes(final int... values) {
        final byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return Bytes.wrap(out);
    }

    /** mapInt32ToString = 1, one entry {key 1, value "a"} followed by an unknown varint field 3 inside the entry. */
    private static final Bytes ENTRY_WITH_UNKNOWN_FIELD = bytes(0x0A, 0x07, 0x08, 0x01, 0x12, 0x01, 'a', 0x18, 0x05);

    /** The same entry, with its key written with wire type 5 (fixed32) instead of 0 (varint). */
    private static final Bytes KEY_WITH_WRONG_WIRE_TYPE = bytes(0x0A, 0x08, 0x0D, 0x01, 0, 0, 0, 0x12, 0x01, 'a');

    @Test
    void unknownFieldInsideAnEntryIsSkippedWhenLenient() throws ParseException {
        final MessageWithMaps parsed = MessageWithMaps.PROTOBUF.parse(ENTRY_WITH_UNKNOWN_FIELD);
        assertEquals(Map.of(1, "a"), parsed.mapInt32ToString());
    }

    @Test
    void unknownFieldInsideAnEntryIsKeptWhenAsked() throws ParseException {
        final MessageWithMaps parsed = MessageWithMaps.PROTOBUF.parse(
                ENTRY_WITH_UNKNOWN_FIELD.toReadableSequentialData(), false, true, Codec.DEFAULT_MAX_DEPTH);
        assertEquals(Map.of(1, "a"), parsed.mapInt32ToString());
        // like parseImpl's other unknown fields, it is collected on the message
        assertEquals(1, parsed.getUnknownFields().size());
        assertEquals(3, parsed.getUnknownFields().getFirst().field());
    }

    @Test
    void unknownFieldInsideAnEntryIsRejectedWhenStrict() {
        assertThrows(ParseException.class, () -> MessageWithMaps.PROTOBUF.parseStrict(ENTRY_WITH_UNKNOWN_FIELD));
    }

    @Test
    void keyWithTheWrongWireTypeIsRejected() {
        assertThrows(ParseException.class, () -> MessageWithMaps.PROTOBUF.parse(KEY_WITH_WRONG_WIRE_TYPE));
        assertThrows(ParseException.class, () -> MessageWithMaps.PROTOBUF.parseStrict(KEY_WITH_WRONG_WIRE_TYPE));
    }
}
