// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;

/**
 * Test data for oneOf testing.
 * Provides Everything messages with all 26 possible oneOf cases set.
 */
public class OneOfTestData {

    private static final Everything.Builder BASE =
            Everything.newBuilder().int32Number(1234).text("base message").booleanField(true);

    // ===== All 26 oneOf cases for Everything.oneofExample =====

    /** OneOf case 1/26: INT32_NUMBER_ONE_OF (field 100001) */
    public static final Everything WITH_INT32_ONEOF = BASE.int32NumberOneOf(42).build();

    /** OneOf case 2/26: SINT32_NUMBER_ONE_OF (field 100002) */
    public static final Everything WITH_SINT32_ONEOF =
            BASE.sint32NumberOneOf(-42).build();

    /** OneOf case 3/26: UINT32_NUMBER_ONE_OF (field 100003) */
    public static final Everything WITH_UINT32_ONEOF =
            BASE.uint32NumberOneOf(12345).build();

    /** OneOf case 4/26: FIXED32_NUMBER_ONE_OF (field 100004) */
    public static final Everything WITH_FIXED32_ONEOF =
            BASE.fixed32NumberOneOf(98765).build();

    /** OneOf case 5/26: SFIXED32_NUMBER_ONE_OF (field 100005) */
    public static final Everything WITH_SFIXED32_ONEOF =
            BASE.sfixed32NumberOneOf(-98765).build();

    /** OneOf case 6/26: FLOAT_NUMBER_ONE_OF (field 100006) */
    public static final Everything WITH_FLOAT_ONEOF =
            BASE.floatNumberOneOf(3.14159f).build();

    /** OneOf case 7/26: INT64_NUMBER_ONE_OF (field 100007) */
    public static final Everything WITH_INT64_ONEOF =
            BASE.int64NumberOneOf(1234567890L).build();

    /** OneOf case 8/26: SINT64_NUMBER_ONE_OF (field 100008) */
    public static final Everything WITH_SINT64_ONEOF =
            BASE.sint64NumberOneOf(-1234567890L).build();

    /** OneOf case 9/26: UINT64_NUMBER_ONE_OF (field 100009) */
    public static final Everything WITH_UINT64_ONEOF =
            BASE.uint64NumberOneOf(9876543210L).build();

    /** OneOf case 10/26: FIXED64_NUMBER_ONE_OF (field 100010) */
    public static final Everything WITH_FIXED64_ONEOF =
            BASE.fixed64NumberOneOf(1111111111L).build();

    /** OneOf case 11/26: SFIXED64_NUMBER_ONE_OF (field 100011) */
    public static final Everything WITH_SFIXED64_ONEOF =
            BASE.sfixed64NumberOneOf(-1111111111L).build();

    /** OneOf case 12/26: DOUBLE_NUMBER_ONE_OF (field 100012) */
    public static final Everything WITH_DOUBLE_ONEOF =
            BASE.doubleNumberOneOf(2.718281828).build();

    /** OneOf case 13/26: BOOLEAN_FIELD_ONE_OF (field 100013) - MIDDLE CASE */
    public static final Everything WITH_BOOLEAN_ONEOF =
            BASE.booleanFieldOneOf(true).build();

    /** OneOf case 14/26: ENUM_SUIT_ONE_OF (field 100014) */
    public static final Everything WITH_ENUM_ONEOF =
            BASE.enumSuitOneOf(Suit.SPADES).build();

    /** OneOf case 15/26: SUB_OBJECT_ONE_OF (field 100015) */
    public static final Everything WITH_SUBOBJECT_ONEOF =
            BASE.subObjectOneOf(new TimestampTest(123456789L, 987654321)).build();

    /** OneOf case 16/26: TEXT_ONE_OF (field 100016) */
    public static final Everything WITH_TEXT_ONEOF =
            BASE.textOneOf("hello world").build();

    /** OneOf case 17/26: BYTES_FIELD_ONE_OF (field 100017) */
    public static final Everything WITH_BYTES_ONEOF =
            BASE.bytesFieldOneOf(Bytes.wrap(new byte[] {1, 2, 3, 4, 5})).build();

    /** OneOf case 18/26: INT32_BOXED_ONE_OF (field 100018) */
    public static final Everything WITH_INT32_BOXED_ONEOF =
            BASE.int32BoxedOneOf(555).build();

    /** OneOf case 19/26: UINT32_BOXED_ONE_OF (field 100019) */
    public static final Everything WITH_UINT32_BOXED_ONEOF =
            BASE.uint32BoxedOneOf(666).build();

    /** OneOf case 20/26: INT64_BOXED_ONE_OF (field 100020) */
    public static final Everything WITH_INT64_BOXED_ONEOF =
            BASE.int64BoxedOneOf(777L).build();

    /** OneOf case 21/26: UINT64_BOXED_ONE_OF (field 100021) */
    public static final Everything WITH_UINT64_BOXED_ONEOF =
            BASE.uint64BoxedOneOf(888L).build();

    /** OneOf case 22/26: FLOAT_BOXED_ONE_OF (field 100022) */
    public static final Everything WITH_FLOAT_BOXED_ONEOF =
            BASE.floatBoxedOneOf(9.99f).build();

    /** OneOf case 23/26: DOUBLE_BOXED_ONE_OF (field 100023) */
    public static final Everything WITH_DOUBLE_BOXED_ONEOF =
            BASE.doubleBoxedOneOf(10.10).build();

    /** OneOf case 24/26: BOOL_BOXED_ONE_OF (field 100024) */
    public static final Everything WITH_BOOL_BOXED_ONEOF =
            BASE.boolBoxedOneOf(false).build();

    /** OneOf case 25/26: BYTES_BOXED_ONE_OF (field 100025) */
    public static final Everything WITH_BYTES_BOXED_ONEOF =
            BASE.bytesBoxedOneOf(Bytes.wrap(new byte[] {4, 5, 6})).build();

    /** OneOf case 26/26: STRING_BOXED_ONE_OF (field 100026) - LAST CASE */
    public static final Everything WITH_STRING_BOXED_ONEOF =
            BASE.stringBoxedOneOf("boxed string").build();

    /** UNSET case: no oneOf field set */
    public static final Everything WITH_UNSET_ONEOF = BASE.build();

    /**
     * Array of all messages for iteration in benchmarks and tests.
     * Ordered by field number (100001 - 100026, then UNSET).
     */
    public static final Everything[] ALL_ONEOF_CASES = {
        WITH_INT32_ONEOF,
        WITH_SINT32_ONEOF,
        WITH_UINT32_ONEOF,
        WITH_FIXED32_ONEOF,
        WITH_SFIXED32_ONEOF,
        WITH_FLOAT_ONEOF,
        WITH_INT64_ONEOF,
        WITH_SINT64_ONEOF,
        WITH_UINT64_ONEOF,
        WITH_FIXED64_ONEOF,
        WITH_SFIXED64_ONEOF,
        WITH_DOUBLE_ONEOF,
        WITH_BOOLEAN_ONEOF,
        WITH_ENUM_ONEOF,
        WITH_SUBOBJECT_ONEOF,
        WITH_TEXT_ONEOF,
        WITH_BYTES_ONEOF,
        WITH_INT32_BOXED_ONEOF,
        WITH_UINT32_BOXED_ONEOF,
        WITH_INT64_BOXED_ONEOF,
        WITH_UINT64_BOXED_ONEOF,
        WITH_FLOAT_BOXED_ONEOF,
        WITH_DOUBLE_BOXED_ONEOF,
        WITH_BOOL_BOXED_ONEOF,
        WITH_BYTES_BOXED_ONEOF,
        WITH_STRING_BOXED_ONEOF,
        WITH_UNSET_ONEOF
    };

    /**
     * Key test cases for performance benchmarking.
     * Represents first, middle, last, and unset positions.
     */
    public static final Everything FIRST_CASE = WITH_INT32_ONEOF;

    public static final Everything EARLY_CASE = WITH_FLOAT_ONEOF;
    public static final Everything MIDDLE_CASE = WITH_BOOLEAN_ONEOF;
    public static final Everything LATE_CASE = WITH_TEXT_ONEOF;
    public static final Everything LAST_CASE = WITH_STRING_BOXED_ONEOF;
    public static final Everything UNSET_CASE = WITH_UNSET_ONEOF;

    private OneOfTestData() {
        // Utility class
    }
}
