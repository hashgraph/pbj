// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrpcTimeoutTest {
    @ParameterizedTest
    @CsvSource({"2H,HOURS", "3M,MINUTES", "4S,SECONDS", "5m,MILLISECONDS", "6u,MICROSECONDS", "7n,NANOSECONDS"})
    void parsesEveryUnit(final String value, final TimeUnit unit) {
        final long amount = Long.parseLong(value.substring(0, value.length() - 1));
        assertEquals(OptionalLong.of(unit.toNanos(amount)), GrpcTimeout.parseNanos(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "S", "10", "10s", "123456789S", "-1S", "1.5S", " 1S"})
    void ignoresInvalidValues(final String value) {
        assertEquals(OptionalLong.empty(), GrpcTimeout.parseNanos(value));
    }
}
