// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.tests.EverythingTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that XDR encoded output is always 4-byte aligned for various message types.
 */
class XdrAlignmentTest {

    @ParameterizedTest
    @MethodSource("createTimestampTestArgs")
    void timestampTest_xdrSizeMultipleOf4(final NoToStringWrapper<TimestampTest> wrapper) {
        final Bytes xdr = TimestampTest.XDR.toBytes(wrapper.getValue());
        assertEquals(0, xdr.length() % 4, "XDR size must be 4-byte aligned");
    }

    @ParameterizedTest
    @MethodSource("createHashevalArgs")
    void hasheval_xdrSizeMultipleOf4(final NoToStringWrapper<Hasheval> wrapper) {
        final Bytes xdr = Hasheval.XDR.toBytes(wrapper.getValue());
        assertEquals(0, xdr.length() % 4, "XDR size must be 4-byte aligned");
    }

    @ParameterizedTest
    @MethodSource("createEverythingArgs")
    void everything_xdrSizeMultipleOf4(final NoToStringWrapper<Everything> wrapper) {
        final Bytes xdr = Everything.XDR.toBytes(wrapper.getValue());
        assertEquals(0, xdr.length() % 4, "XDR size must be 4-byte aligned");
    }

    static Stream<NoToStringWrapper<TimestampTest>> createTimestampTestArgs() {
        return TimestampTestTest.ARGUMENTS.stream().map(NoToStringWrapper::new);
    }

    static Stream<NoToStringWrapper<Hasheval>> createHashevalArgs() {
        return HashevalTest.ARGUMENTS.stream().map(NoToStringWrapper::new);
    }

    static Stream<NoToStringWrapper<Everything>> createEverythingArgs() {
        return EverythingTest.ARGUMENTS.stream().map(NoToStringWrapper::new);
    }
}
