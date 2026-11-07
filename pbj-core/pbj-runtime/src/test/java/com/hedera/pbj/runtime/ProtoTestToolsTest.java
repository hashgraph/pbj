// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static com.hedera.pbj.runtime.ProtoTestTools.appendVarIntField;
import static com.hedera.pbj.runtime.ProtoTestTools.reverseTopLevelFieldOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;

final class ProtoTestToolsTest {

    private static Bytes bytes(final int... values) {
        final byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return Bytes.wrap(out);
    }

    @Test
    void reversesDistinctFieldsAndKeepsRepeatedOccurrencesInOrder() {
        final Bytes canonical = bytes(
                0x08, 0x96, 0x01, // field 1 varint 150
                0x11, 1, 2, 3, 4, 5, 6, 7, 8, // field 2 fixed64
                0x1A, 0x01, 0x41, // field 3 "A"
                0x1A, 0x01, 0x42, // field 3 "B"
                0x25, 9, 9, 9, 9, // field 4 fixed32
                0xA2, 0x06, 0x00); // field 100 empty message (two-byte tag)
        final Bytes expected = bytes(
                0xA2, 0x06, 0x00, 0x25, 9, 9, 9, 9, 0x1A, 0x01, 0x41, 0x1A, 0x01, 0x42, 0x11, 1, 2, 3, 4, 5, 6, 7, 8,
                0x08, 0x96, 0x01);
        assertEquals(expected, reverseTopLevelFieldOrder(canonical));
    }

    @Test
    void emptyMessageStaysEmpty() {
        assertEquals(Bytes.EMPTY, reverseTopLevelFieldOrder(Bytes.EMPTY));
    }

    @Test
    void rejectsTruncatedInput() {
        assertThrows(IllegalArgumentException.class, () -> reverseTopLevelFieldOrder(bytes(0x1A, 0x05, 0x41)));
        assertThrows(IllegalArgumentException.class, () -> reverseTopLevelFieldOrder(bytes(0x08, 0x96)));
    }

    @Test
    void appendsVarIntField() {
        // field 99, wire type 0: tag 792 = 0x98 0x06, then value 1
        assertEquals(bytes(0x08, 0x01, 0x98, 0x06, 0x01), appendVarIntField(bytes(0x08, 0x01), 99));
        assertEquals(bytes(0x08, 0x01), appendVarIntField(Bytes.EMPTY, 1));
    }
}
