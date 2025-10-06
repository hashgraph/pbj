// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.integration.EverythingTestData;
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
        assertEquals(
                message,
                parsed,
                "Round-trip failed for " + message.oneofExample().kind());
        assertEquals(message.oneofExample().kind(), parsed.oneofExample().kind(), "OneOf kind mismatch");

        // Verify the actual value (if not UNSET)
        if (message.oneofExample().kind() != Everything.OneofExampleOneOfType.UNSET) {
            Object expected = message.oneofExample().as();
            Object actual = parsed.oneofExample().as();
            assertEquals(
                    expected,
                    actual,
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
        var msg = Everything.newBuilder().int32NumberOneOf(42).build();

        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg.oneofExample().kind());

        // Setting DOUBLE_NUMBER_ONE_OF should replace INT32_NUMBER_ONE_OF
        var msg2 = msg.copyBuilder().doubleNumberOneOf(3.14).build();

        assertEquals(
                Everything.OneofExampleOneOfType.DOUBLE_NUMBER_ONE_OF,
                msg2.oneofExample().kind());
        assertNotEquals(msg.oneofExample().kind(), msg2.oneofExample().kind(), "OneOf should be mutually exclusive");
    }

    @Test
    void testUnsetCase() throws Exception {
        var msg = Everything.newBuilder().int32Number(1234).text("test").build();

        assertEquals(Everything.OneofExampleOneOfType.UNSET, msg.oneofExample().kind());

        // Round trip should preserve UNSET
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(
                Everything.OneofExampleOneOfType.UNSET, parsed.oneofExample().kind());
    }

    @Test
    void testConsistentSerialization() {
        var msg = Everything.newBuilder().int32NumberOneOf(42).build();

        // Serialize 10 times
        Bytes[] results = new Bytes[10];
        for (int i = 0; i < 10; i++) {
            results[i] = Everything.PROTOBUF.toBytes(msg);
        }

        // All serializations should be identical
        for (int i = 1; i < 10; i++) {
            assertEquals(results[0], results[i], "Serialization " + i + " differs from first serialization");
        }
    }

    @Test
    void testFirstCase() throws Exception {
        var msg = OneOfTestData.FIRST_CASE;

        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg.oneofExample().kind());
        assertEquals(Integer.valueOf(42), msg.oneofExample().as());

        // Round trip
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(msg, parsed);
    }

    @Test
    void testMiddleCase() throws Exception {
        var msg = OneOfTestData.MIDDLE_CASE;

        assertEquals(
                Everything.OneofExampleOneOfType.BOOLEAN_FIELD_ONE_OF,
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

        assertEquals(
                Everything.OneofExampleOneOfType.STRING_BOXED_ONE_OF,
                msg.oneofExample().kind());
        assertEquals("boxed string", msg.oneofExample().as());

        // Round trip
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        assertEquals(msg, parsed);
    }

    @Test
    void testCaseSwitching() throws Exception {
        var builder = Everything.newBuilder().int32Number(1234);

        // Set INT32_NUMBER_ONE_OF
        var msg1 = builder.int32NumberOneOf(1).build();
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg1.oneofExample().kind());

        // Switch to FLOAT_NUMBER_ONE_OF
        var msg2 = msg1.copyBuilder().floatNumberOneOf(2.5f).build();
        assertEquals(
                Everything.OneofExampleOneOfType.FLOAT_NUMBER_ONE_OF,
                msg2.oneofExample().kind());

        // Switch to TEXT_ONE_OF
        var msg3 = msg2.copyBuilder().textOneOf("three").build();
        assertEquals(
                Everything.OneofExampleOneOfType.TEXT_ONE_OF,
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
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg1.oneofExample().kind());

        // Set different case on same builder
        builder.doubleNumberOneOf(3.14);

        // Build and verify it switched
        var msg2 = builder.build();
        assertEquals(
                Everything.OneofExampleOneOfType.DOUBLE_NUMBER_ONE_OF,
                msg2.oneofExample().kind());
    }

    @Test
    void testCopyBuilderPreservesOneOf() {
        var original =
                Everything.newBuilder().int32Number(1234).int32NumberOneOf(42).build();

        // Copy without modifying oneOf
        var copy = original.copyBuilder().int32Number(5678).build();

        // OneOf should be preserved
        assertEquals(original.oneofExample().kind(), copy.oneofExample().kind());
        Object expectedValue = original.oneofExample().as();
        Object actualValue = copy.oneofExample().as();
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void testSerializationSizeConsistency() {
        for (Everything msg : OneOfTestData.ALL_ONEOF_CASES) {
            // Get serialized bytes
            Bytes bytes = Everything.PROTOBUF.toBytes(msg);

            // Measure size
            int measuredSize = Everything.PROTOBUF.measureRecord(msg);

            // They should match
            assertEquals(
                    bytes.length(),
                    measuredSize,
                    "Size mismatch for " + msg.oneofExample().kind() + ": serialized=" + bytes.length() + " measured="
                            + measuredSize);
        }
    }

    @Test
    void testOneOfWithDefaultValues() throws Exception {
        // Test int32 with value 0 (protobuf default)
        var msg1 = Everything.newBuilder().int32NumberOneOf(0).build();
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg1.oneofExample().kind());
        assertEquals(Integer.valueOf(0), msg1.oneofExample().as());

        // OneOf should serialize even with default value
        Bytes bytes1 = Everything.PROTOBUF.toBytes(msg1);
        assertTrue(bytes1.length() > 0);

        // Round trip should preserve oneOf with zero value
        Everything parsed1 = Everything.PROTOBUF.parse(bytes1);
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                parsed1.oneofExample().kind());
        assertEquals(Integer.valueOf(0), parsed1.oneofExample().as());

        // Test boolean with false (protobuf default)
        var msg2 = Everything.newBuilder().booleanFieldOneOf(false).build();
        assertEquals(
                Everything.OneofExampleOneOfType.BOOLEAN_FIELD_ONE_OF,
                msg2.oneofExample().kind());
        assertEquals(false, msg2.oneofExample().as());

        // Round trip
        Bytes bytes2 = Everything.PROTOBUF.toBytes(msg2);
        Everything parsed2 = Everything.PROTOBUF.parse(bytes2);
        assertEquals(
                Everything.OneofExampleOneOfType.BOOLEAN_FIELD_ONE_OF,
                parsed2.oneofExample().kind());
        assertEquals(false, parsed2.oneofExample().as());

        // Test empty string (protobuf default)
        var msg3 = Everything.newBuilder().textOneOf("").build();
        assertEquals(
                Everything.OneofExampleOneOfType.TEXT_ONE_OF,
                msg3.oneofExample().kind());
        assertEquals("", msg3.oneofExample().as());

        // Round trip
        Bytes bytes3 = Everything.PROTOBUF.toBytes(msg3);
        Everything parsed3 = Everything.PROTOBUF.parse(bytes3);
        assertEquals(
                Everything.OneofExampleOneOfType.TEXT_ONE_OF,
                parsed3.oneofExample().kind());
        assertEquals("", parsed3.oneofExample().as());
    }

    @Test
    void testOneOfInCompleteMessage() throws Exception {
        // Start with a fully-populated Everything message
        var baseMsg = EverythingTestData.EVERYTHING;

        // Add a oneOf field on top of all the other fields
        var msg = baseMsg.copyBuilder().int32NumberOneOf(999).build();

        // Verify oneOf is set correctly
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                msg.oneofExample().kind());
        assertEquals(Integer.valueOf(999), msg.oneofExample().as());

        // Round trip through serialization
        Bytes bytes = Everything.PROTOBUF.toBytes(msg);
        Everything parsed = Everything.PROTOBUF.parse(bytes);

        // Verify oneOf survived round-trip
        assertEquals(
                Everything.OneofExampleOneOfType.INT32_NUMBER_ONE_OF,
                parsed.oneofExample().kind());
        assertEquals(Integer.valueOf(999), parsed.oneofExample().as());

        // Verify all other fields are intact (full equality check)
        assertEquals(msg, parsed);

        // Test with different oneOf position (last case)
        var msg2 = baseMsg.copyBuilder().stringBoxedOneOf("integration test").build();

        assertEquals(
                Everything.OneofExampleOneOfType.STRING_BOXED_ONE_OF,
                msg2.oneofExample().kind());

        // Round trip
        Bytes bytes2 = Everything.PROTOBUF.toBytes(msg2);
        Everything parsed2 = Everything.PROTOBUF.parse(bytes2);

        assertEquals(
                Everything.OneofExampleOneOfType.STRING_BOXED_ONE_OF,
                parsed2.oneofExample().kind());
        assertEquals("integration test", parsed2.oneofExample().as());
        assertEquals(msg2, parsed2);
    }
}
