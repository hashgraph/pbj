// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.custom.proto.pbj.CustomMessage;
import org.junit.jupiter.api.Test;

class CustomSourceSetTest {

    @Test
    void testPbjCodeFromCustomSourceSet() {
        var customMessage = new CustomMessage.Builder()
                .bytes(Bytes.wrap(new byte[] {1, 2, 3}))
                .build();
        assertArrayEquals(new byte[] {1, 2, 3}, customMessage.bytes().toByteArray());
    }
}
