// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.test.proto.pbj.Everything;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class BuilderGetterTest {
    @Test
    void getterTest() {
        Everything obj = Everything.DEFAULT;

        assertTrue(obj.sfixed32NumberList().isEmpty());

        final Everything.Builder builder = obj.copyBuilder();
        assertTrue(builder.sfixed32NumberList().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> builder.sfixed32NumberList()
                .add(666));

        builder.sfixed32NumberList(new ArrayList<>());
        builder.sfixed32NumberList().add(666);
        assertEquals(List.of(666), builder.sfixed32NumberList());

        Everything obj2 = builder.build();
        assertEquals(List.of(666), obj2.sfixed32NumberList());
    }
}
