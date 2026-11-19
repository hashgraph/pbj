// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.pbj.test.proto.pbj.Suit;
import org.junit.jupiter.api.Test;

/**
 * {@code fromString} accepts the protobuf and enum names of every constant, and nothing else, both for small enums,
 * which use a string switch, and for large ones such as {@code ResponseCodeEnum}, which look names up in a map.
 */
final class EnumFromStringTest {
    @Test
    void largeEnumsAcceptEveryNameAndNothingElse() {
        for (final ResponseCodeEnum value : ResponseCodeEnum.values()) {
            if (value == ResponseCodeEnum.UNRECOGNIZED) {
                assertThrows(IllegalArgumentException.class, () -> ResponseCodeEnum.fromString(value.name()));
                continue;
            }
            assertSame(value, ResponseCodeEnum.fromString(value.protoName()));
            assertSame(value, ResponseCodeEnum.fromString(value.name()));
        }
        for (final HederaFunctionality value : HederaFunctionality.values()) {
            if (value != HederaFunctionality.UNRECOGNIZED) {
                assertSame(value, HederaFunctionality.fromString(value.protoName()));
                assertSame(value, HederaFunctionality.fromString(value.name()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ResponseCodeEnum.fromString("NOT_A_RESPONSE_CODE"));
        assertThrows(NullPointerException.class, () -> ResponseCodeEnum.fromString(null));
    }

    @Test
    void smallEnumsAcceptEveryNameAndNothingElse() {
        for (final Suit value : Suit.values()) {
            if (value != Suit.UNRECOGNIZED) {
                assertSame(value, Suit.fromString(value.protoName()));
                assertSame(value, Suit.fromString(value.name()));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Suit.fromString("UNRECOGNIZED"));
        assertThrows(NullPointerException.class, () -> Suit.fromString(null));
    }
}
