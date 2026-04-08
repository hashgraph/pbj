// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * Tests comparing XDR encoded size vs protobuf encoded size.
 *
 * <p>XDR uses fixed-width encodings with 4-byte alignment padding and per-field presence flags.
 * Protobuf uses variable-length (varint) encoding with field tags only for present fields.
 * For messages with many fields or complex structure, XDR is typically larger. However for
 * messages with only a few integer fields and large values, XDR can be smaller because it
 * avoids varint overhead for large integers.
 *
 * <p>The {@code Everything} type with its many fields, lists, and maps provides a reliable
 * case where XDR size >= protobuf size for non-empty messages.
 */
class XdrSizeComparisonTest {

    /**
     * For simple messages like TimestampTest (long + int), log the XDR vs protobuf size
     * ratio. XDR size may be smaller than proto for large integer values since XDR avoids
     * varint encoding overhead.
     */
    @ParameterizedTest
    @MethodSource("createTimestampTestArgs")
    void timestampTest_xdrVsProtoSizeReport(final NoToStringWrapper<TimestampTest> wrapper) {
        final TimestampTest obj = wrapper.getValue();
        final long xdrSize = TimestampTest.XDR.measureRecord(obj);
        final long protoSize = TimestampTest.PROTOBUF.measureRecord(obj);
        // Log for analysis — XDR can be smaller than proto for messages with few integer fields
        if (protoSize > 0) {
            System.out.printf(
                    "TimestampTest — Proto: %d bytes, XDR: %d bytes, ratio: %.2fx%n",
                    protoSize, xdrSize, (double) xdrSize / protoSize);
        }
    }

    /**
     * For Hasheval (many scalar fields), log the XDR vs protobuf size ratio.
     * XDR size may be smaller than proto for some field value combinations.
     */
    @ParameterizedTest
    @MethodSource("createHashevalArgs")
    void hasheval_xdrVsProtoSizeReport(final NoToStringWrapper<Hasheval> wrapper) {
        final Hasheval obj = wrapper.getValue();
        final long xdrSize = Hasheval.XDR.measureRecord(obj);
        final long protoSize = Hasheval.PROTOBUF.measureRecord(obj);
        // Log for analysis
        if (protoSize > 0) {
            System.out.printf(
                    "Hasheval — Proto: %d bytes, XDR: %d bytes, ratio: %.2fx%n",
                    protoSize, xdrSize, (double) xdrSize / protoSize);
        }
    }

    /**
     * For Everything (many fields, lists, and maps), XDR size is always >= protobuf size
     * for non-empty messages, due to 4-byte alignment padding and per-field presence flags.
     */
    @ParameterizedTest
    @MethodSource("createEverythingArgs")
    void everything_xdrSizeGeqProto(final NoToStringWrapper<Everything> wrapper) {
        final Everything obj = wrapper.getValue();
        final int xdrSize = Everything.XDR.measureRecord(obj);
        final int protoSize = Everything.PROTOBUF.measureRecord(obj);
        System.out.printf(
                "Everything — Proto: %d bytes, XDR: %d bytes, ratio: %.2fx%n",
                protoSize, xdrSize, protoSize > 0 ? (double) xdrSize / protoSize : 0.0);
        // For a complex message type, XDR overhead from presence flags and 4-byte alignment
        // ensures XDR is >= proto for non-empty messages
        assertTrue(
                xdrSize >= protoSize || protoSize == 0,
                "XDR size " + xdrSize + " should be >= proto size " + protoSize + " for Everything");
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
