// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.tests.HashevalTest;
import com.hedera.pbj.test.proto.pbj.tests.TimestampTestTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Cross-codec consistency tests that verify XDR round-trip produces the same object as
 * protobuf round-trip. For any message type without protoOrdinal=0 enum values, the
 * following invariant must hold:
 * <pre>
 *   parse(XDR.toBytes(obj)).equals(parse(PROTOBUF.toBytes(obj)))
 * </pre>
 */
public final class XdrCrossCodecConsistencyTest {

    // -------------------------------------------------------------------------
    // TimestampTest
    // -------------------------------------------------------------------------

    /**
     * Provides test arguments for TimestampTest cross-codec tests.
     *
     * @return stream of wrapped TimestampTest instances
     */
    public static Stream<NoToStringWrapper<TimestampTest>> createTimestampTestArguments() {
        return TimestampTestTest.ARGUMENTS.stream().map(NoToStringWrapper::new);
    }

    @ParameterizedTest
    @MethodSource("createTimestampTestArguments")
    void xdrRoundTrip_equalsProtobufRoundTrip_TimestampTest(
            final NoToStringWrapper<TimestampTest> wrapper) throws Exception {
        final TimestampTest original = wrapper.getValue();

        // Protobuf round-trip
        final Bytes protoBytes = TimestampTest.PROTOBUF.toBytes(original);
        final TimestampTest fromProto =
                TimestampTest.PROTOBUF.parse(protoBytes.toReadableSequentialData());

        // XDR round-trip
        final Bytes xdrBytes = TimestampTest.XDR.toBytes(original);
        final TimestampTest fromXdr =
                TimestampTest.XDR.parse(xdrBytes.toReadableSequentialData());

        // Both round-trips must produce equal objects
        assertEquals(fromProto, fromXdr);
        assertEquals(original, fromXdr);
    }

    // -------------------------------------------------------------------------
    // Hasheval
    // -------------------------------------------------------------------------

    /**
     * Provides test arguments for Hasheval cross-codec tests.
     * Hasheval contains an enum field (Suit); the test filters out objects where
     * the enum value has protoOrdinal=0 (i.e. the ACES value) to avoid false failures
     * caused by the XDR canonical encoding treating protoOrdinal=0 as "not present".
     *
     * @return stream of wrapped Hasheval instances whose enum fields will round-trip correctly
     */
    public static Stream<NoToStringWrapper<Hasheval>> createHashevalArguments() {
        return HashevalTest.ARGUMENTS.stream()
                // Filter out objects with default enum values (protoOrdinal == 0) because XDR
                // treats these as absent fields and proto treats them as explicit ACES values —
                // so the two codecs legitimately differ for those objects.
                .filter(h -> h.enumSuit() != null && h.enumSuit().protoOrdinal() != 0)
                .map(NoToStringWrapper::new);
    }

    @ParameterizedTest
    @MethodSource("createHashevalArguments")
    void xdrRoundTrip_equalsProtobufRoundTrip_Hasheval(
            final NoToStringWrapper<Hasheval> wrapper) throws Exception {
        final Hasheval original = wrapper.getValue();

        // Protobuf round-trip
        final Bytes protoBytes = Hasheval.PROTOBUF.toBytes(original);
        final Hasheval fromProto =
                Hasheval.PROTOBUF.parse(protoBytes.toReadableSequentialData());

        // XDR round-trip
        final Bytes xdrBytes = Hasheval.XDR.toBytes(original);
        final Hasheval fromXdr =
                Hasheval.XDR.parse(xdrBytes.toReadableSequentialData());

        // Both round-trips must produce equal objects
        assertEquals(fromProto, fromXdr);
        assertEquals(original, fromXdr);
    }
}
