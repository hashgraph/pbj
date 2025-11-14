// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UnmodifiableListsTest {
    @Test
    public void testUnmodifiableList() throws Exception {
        // This list is unmodifiable, but can be modifiable technically if one were to supply an ArrayList instance
        // here.
        Everything obj =
                Everything.newBuilder().sfixed32NumberList(List.of(666)).build();
        final Bytes bytes = Everything.PROTOBUF.toBytes(obj);

        // However, a list in a parsed object is guaranteed to be unmodifiable.
        final Everything parsedObj = Everything.PROTOBUF.parse(bytes);
        assertEquals(List.of(666), parsedObj.sfixed32NumberList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> parsedObj.sfixed32NumberList().add(777));
    }
}
