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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that XDR encoding is deterministic: same object always produces identical bytes,
 * and equal objects produce equal bytes.
 */
class XdrDeterminismTest {

    @ParameterizedTest
    @MethodSource("createTimestampTestArgs")
    void timestampTest_deterministicEncoding(final NoToStringWrapper<TimestampTest> wrapper) {
        final TimestampTest obj = wrapper.getValue();
        final Bytes xdr1 = TimestampTest.XDR.toBytes(obj);
        final Bytes xdr2 = TimestampTest.XDR.toBytes(obj);
        assertEquals(xdr1, xdr2);
    }

    @ParameterizedTest
    @MethodSource("createHashevalArgs")
    void hasheval_deterministicEncoding(final NoToStringWrapper<Hasheval> wrapper) {
        final Hasheval obj = wrapper.getValue();
        final Bytes xdr1 = Hasheval.XDR.toBytes(obj);
        final Bytes xdr2 = Hasheval.XDR.toBytes(obj);
        assertEquals(xdr1, xdr2);
    }

    @ParameterizedTest
    @MethodSource("createEverythingArgs")
    void everything_deterministicEncoding(final NoToStringWrapper<Everything> wrapper) {
        final Everything obj = wrapper.getValue();
        final Bytes xdr1 = Everything.XDR.toBytes(obj);
        final Bytes xdr2 = Everything.XDR.toBytes(obj);
        assertEquals(xdr1, xdr2);
    }

    @Test
    void equalObjects_produceSameBytes() {
        final TimestampTest a = new TimestampTest(42L, 100);
        final TimestampTest b = new TimestampTest(42L, 100);
        assertEquals(a, b);
        assertEquals(TimestampTest.XDR.toBytes(a), TimestampTest.XDR.toBytes(b));
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
