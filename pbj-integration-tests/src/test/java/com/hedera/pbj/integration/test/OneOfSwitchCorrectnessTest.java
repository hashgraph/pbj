// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.integration.OneOfTestData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Correctness tests for oneOf field handling.
 * Validates all 26 oneOf cases, mutual exclusivity, and serialization correctness.
 */
class OneOfSwitchCorrectnessTest {

    @ParameterizedTest
    @MethodSource("provideAllOneOfCases")
    void testAllOneOfCasesRoundTrip(Everything message) throws Exception {
        // Serialize
        Bytes bytes = Everything.PROTOBUF.toBytes(message);
        assertNotNull(bytes);
        assertTrue(bytes.length() > 0);

        // Deserialize
        Everything parsed = Everything.PROTOBUF.parse(bytes);
        assertNotNull(parsed);

        // Verify exact match
        assertEquals(message, parsed, "Round-trip failed for " + message.oneofExample().kind());
        assertEquals(message.oneofExample().kind(), parsed.oneofExample().kind(),
            "OneOf kind mismatch");

        // Verify the actual value (if not UNSET)
        if (message.oneofExample().kind() != Everything.OneofExampleOneOfType.UNSET) {
            assertEquals(message.oneofExample().as(), parsed.oneofExample().as(),
                "OneOf value mismatch for " + message.oneofExample().kind());
        }
    }

    /**
     * Provider for all possible oneOf cases.
     * Uses OneOfTestData for comprehensive coverage.
     */
    static Stream<Everything> provideAllOneOfCases() {
        return Stream.of(OneOfTestData.ALL_ONEOF_CASES);
    }

    @Test
    void testMutualExclusivity() {
        // Start with INT32_NUMBER_ONE_OF
        var msg = Everything.newBuilder()
            .int32NumberOneOf(42)
            .build();

        assertEquals(Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
            msg.oneofExample().kind());

        // Setting DOUBLE_NUMBER_ONE_OF should replace INT32_NUMBER_ONE_OF
        var msg2 = msg.copyBuilder()
            .doubleNumberOneOf(3.14)
            .build();

        assertEquals(Everything.OneofExampleOneOfType.DOUBLE_NUMBER_ONE_OF,
            msg2.oneofExample().kind());
        assertNotEquals(msg.oneofExample().kind(), msg2.oneofExample().kind(),
            "OneOf should be mutually exclusive");
    }

    @Test
    void testUnsetCase() throws Exception {
        var msg = Everything.newBuilder()
            .int32Number(1234)
            .text("test")
            .build();

        assertEquals(Everything.OneofExampleOneOfType.UNSET,
            msg.oneofExample().kind());

        // Round trip should preserve UNSET
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(Everything.OneofExampleOneOfType.UNSET,
            parsed.oneofExample().kind());
    }

    @Test
    void testConsistentSerialization() {
        var msg = Everything.newBuilder()
            .int32NumberOneOf(42)
            .build();

        // Serialize 10 times
        Bytes[] results = new Bytes[10];
        for (int i = 0; i < 10; i++) {
            results[i] = Everything.PROTOBUF.toBytes(msg);
        }

        // All serializations should be identical
        for (int i = 1; i < 10; i++) {
            assertEquals(results[0], results[i],
                "Serialization " + i + " differs from first serialization");
        }
    }

    @Test
    void testFirstCase() throws Exception {
        var msg = OneOfTestData.FIRST_CASE;

        assertEquals(Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
            msg.oneofExample().kind());
        assertEquals(42, msg.oneofExample().as());

        // Round trip
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(msg, parsed);
    }

    @Test
    void testMiddleCase() throws Exception {
        var msg = OneOfTestData.MIDDLE_CASE;

        assertEquals(Everything.OneofExampleOneOfType.BOOLEAN_FIELD_ONE_OF,
            msg.oneofExample().kind());
        assertEquals(true, msg.oneofExample().as());

        // Round trip
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(msg, parsed);
    }

    @Test
    void testLastCase() throws Exception {
        var msg = OneOfTestData.LAST_CASE;

        assertEquals(Everything.OneofExampleOneOfType.STRING_BOXED_ONE_OF,
            msg.oneofExample().kind());
        assertEquals("boxed string", msg.oneofExample().as());

        // Round trip
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(msg, parsed);
    }

    @Test
    void testCaseSwitching() throws Exception {
        var builder = Everything.newBuilder()
            .int32Number(1234);

        // Set INT32_NUMBER_ONE_OF
        var msg1 = builder.int32NumberOneOf(1).build();
        assertEquals(Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
            msg1.oneofExample().kind());

        // Switch to FLOAT_NUMBER_ONE_OF
        var msg2 = msg1.copyBuilder().floatNumberOneOf(2.5f).build();
        assertEquals(Everything.OneofExampleOneOfType.FLOAT_NUMBER_ONE_OF,
            msg2.oneofExample().kind());

        // Switch to TEXT_ONE_OF
        var msg3 = msg2.copyBuilder().textOneOf("three").build();
        assertEquals(Everything.OneofExampleOneOfType.TEXT_ONE_OF,
            msg3.oneofExample().kind());

        // Each should round-trip correctly
        assertEquals(msg1, Everything.PROTOBUF.parse(Everything.PROTOBUF.toBytes(msg1)));
        assertEquals(msg2, Everything.PROTOBUF.parse(Everything.PROTOBUF.toBytes(msg2)));
        assertEquals(msg3, Everything.PROTOBUF.parse(Everything.PROTOBUF.toBytes(msg3)));
    }

    @Test
    void testBuilderOneOfHandling() {
        var builder = Everything.newBuilder();

        // Set one case
        builder.int32NumberOneOf(42);

        // Build and verify
        var msg1 = builder.build();
        assertEquals(Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
            msg1.oneofExample().kind());

        // Set different case on same builder
        builder.doubleNumberOneOf(3.14);

        // Build and verify it switched
        var msg2 = builder.build();
        assertEquals(Everything.OneofExampleOneOfType.DOUBLE_NUMBER_ONE_OF,
            msg2.oneofExample().kind());
    }

    @Test
    void testCopyBuilderPreservesOneOf() {
        var original = Everything.newBuilder()
            .int32Number(1234)
            .int32NumberOneOf(42)
            .build();

        // Copy without modifying oneOf
        var copy = original.copyBuilder()
            .int32Number(5678)
            .build();

        // OneOf should be preserved
        assertEquals(original.oneofExample().kind(), copy.oneofExample().kind());
        assertEquals(original.oneofExample().as(), copy.oneofExample().as());
    }

    @Test
    void testSerializationSizeConsistency() {
        for (Everything msg : OneOfTestData.ALL_ONEOF_CASES) {
            // Get serialized bytes
            Bytes bytes = Everything.PROTOBUF.toBytes(msg);

            // Measure size
            int measuredSize = Everything.PROTOBUF.measureRecord(msg);

            // They should match
            assertEquals(bytes.length(), measuredSize,
                "Size mismatch for " + msg.oneofExample().kind() +
                ": serialized=" + bytes.length() + " measured=" + measuredSize);
        }
    }
}
