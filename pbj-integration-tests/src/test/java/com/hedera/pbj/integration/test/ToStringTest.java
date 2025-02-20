// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static com.hedera.pbj.integration.EverythingTestData.INNER_EVERYTHING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.PbjMap;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.InnerEverything;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Test class for testing the toString method of generated PBJ model objects
 */
public class ToStringTest {
    /**
     * Test that the toString method of generated PBj model object matches that of a Java record
     */
    @Test
    void testTimeStampToString() {
        String expected = "TimestampRecord[seconds=1, nanos=2]";
        TimestampRecord record = new TimestampRecord(1, 2);
        assertEquals(expected, record.toString());
        expected = expected.replace("TimestampRecord", "TimestampTest");
        TimestampTest timestampTest = new TimestampTest(1, 2);
        assertEquals(expected, timestampTest.toString());
    }

    /**
     * Test that the toString method of generated InnerEverything PBJ model object matches that of a Java record
     */
    @Test
    void testInnerEverythingToString() {
        String innerEverythingString = INNER_EVERYTHING.toString();
        String innerEverythingRecordString =
                convertInnerEverything(INNER_EVERYTHING).toString();
        String expected = innerEverythingRecordString
                .replace("InnerEverythingRecord", "InnerEverything")
                .replace("TimestampRecord", "TimestampTest");
        assertEquals(expected, innerEverythingString);
    }

    /**
     * Test that the toString method of generated Everything PBJ model object matches that of a Java record
     */
    @Test
    void testEverythingToString() {
        String everythingString = EVERYTHING.toString();
        String everythingRecordString = EVERYTHING_RECORD.toString();
        String expected = everythingRecordString
                .replace("EverythingRecord", "Everything")
                .replace("TimestampRecord", "TimestampTest");
        assertEquals(expected, everythingString);
    }

    /** Record to match the protobuf test class TimestampTest */
    record TimestampRecord(long seconds, int nanos) {}

    /** Record to match the protobuf test class InnerEverything */
    record InnerEverythingRecord(
            int int32Number,
            int sint32Number,
            int uint32Number,
            int fixed32Number,
            int sfixed32Number,
            float floatNumber,
            long int64Number,
            long sint64Number,
            long uint64Number,
            long fixed64Number,
            long sfixed64Number,
            double doubleNumber,
            boolean booleanField,
            Suit enumSuit,
            TimestampRecord subObject,
            String text,
            Bytes bytesField,
            List<Integer> int32NumberList,
            List<Integer> sint32NumberList,
            List<Integer> uint32NumberList,
            List<Integer> fixed32NumberList,
            List<Integer> sfixed32NumberList,
            List<Float> floatNumberList,
            List<Long> int64NumberList,
            List<Long> sint64NumberList,
            List<Long> uint64NumberList,
            List<Long> fixed64NumberList,
            List<Long> sfixed64NumberList,
            List<Double> doubleNumberList,
            List<Boolean> booleanList,
            List<Suit> enumSuitList,
            List<TimestampRecord> subObjectList,
            List<String> textList,
            List<Bytes> bytesExampleList,
            Integer int32Boxed,
            Integer uint32Boxed,
            Long int64Boxed,
            Long uint64Boxed,
            Float floatBoxed,
            Double doubleBoxed,
            Boolean boolBoxed,
            Bytes bytesBoxed,
            String stringBoxed,
            OneOf<InnerEverything.OneofExampleOneOfType> oneofExample) {}

    /** Record to match the protobuf test class Everything */
    record EverythingRecord(
            int int32Number,
            int sint32Number,
            int uint32Number,
            int fixed32Number,
            int sfixed32Number,
            float floatNumber,
            long int64Number,
            long sint64Number,
            long uint64Number,
            long fixed64Number,
            long sfixed64Number,
            double doubleNumber,
            boolean booleanField,
            Suit enumSuit,
            TimestampRecord subObject,
            String text,
            Bytes bytesField,
            InnerEverythingRecord innerEverything,
            Map<Integer, String> mapInt32ToString,
            Map<Boolean, Double> mapBoolToDouble,
            Map<String, InnerEverythingRecord> mapStringToMessage,
            Map<Long, Bytes> mapUInt64ToBytes,
            Map<Long, Boolean> mapInt64ToBool,
            List<Integer> int32NumberList,
            List<Integer> sint32NumberList,
            List<Integer> uint32NumberList,
            List<Integer> fixed32NumberList,
            List<Integer> sfixed32NumberList,
            List<Float> floatNumberList,
            List<Long> int64NumberList,
            List<Long> sint64NumberList,
            List<Long> uint64NumberList,
            List<Long> fixed64NumberList,
            List<Long> sfixed64NumberList,
            List<Double> doubleNumberList,
            List<Boolean> booleanList,
            List<Suit> enumSuitList,
            List<TimestampRecord> subObjectList,
            List<String> textList,
            List<Bytes> bytesExampleList,
            Integer int32Boxed,
            Integer uint32Boxed,
            Long int64Boxed,
            Long uint64Boxed,
            Float floatBoxed,
            Double doubleBoxed,
            Boolean boolBoxed,
            Bytes bytesBoxed,
            String stringBoxed,
            OneOf<Everything.OneofExampleOneOfType> oneofExample) {}

    /** Sample Everything object for testing */
    private static final Everything EVERYTHING = EverythingTestData.EVERYTHING;

    /**
     * Method to convert InnerEverything to InnerEverythingRecord
     *
     * @param innerEverything the InnerEverything object to convert
     * @return the converted InnerEverythingRecord object
     */
    private static InnerEverythingRecord convertInnerEverything(InnerEverything innerEverything) {
        return new InnerEverythingRecord(
                innerEverything.int32Number(),
                innerEverything.sint32Number(),
                innerEverything.uint32Number(),
                innerEverything.fixed32Number(),
                innerEverything.sfixed32Number(),
                innerEverything.floatNumber(),
                innerEverything.int64Number(),
                innerEverything.sint64Number(),
                innerEverything.uint64Number(),
                innerEverything.fixed64Number(),
                innerEverything.sfixed64Number(),
                innerEverything.doubleNumber(),
                innerEverything.booleanField(),
                innerEverything.enumSuit(),
                new TimestampRecord(
                        innerEverything.subObject().seconds(),
                        innerEverything.subObject().nanos()),
                innerEverything.text(),
                innerEverything.bytesField(),
                innerEverything.int32NumberList(),
                innerEverything.sint32NumberList(),
                innerEverything.uint32NumberList(),
                innerEverything.fixed32NumberList(),
                innerEverything.sfixed32NumberList(),
                innerEverything.floatNumberList(),
                innerEverything.int64NumberList(),
                innerEverything.sint64NumberList(),
                innerEverything.uint64NumberList(),
                innerEverything.fixed64NumberList(),
                innerEverything.sfixed64NumberList(),
                innerEverything.doubleNumberList(),
                innerEverything.booleanList(),
                innerEverything.enumSuitList(),
                List.of(
                        new TimestampRecord(
                                innerEverything.subObjectList().get(0).seconds(),
                                innerEverything.subObjectList().get(0).nanos()),
                        new TimestampRecord(
                                innerEverything.subObjectList().get(1).seconds(),
                                innerEverything.subObjectList().get(1).nanos()),
                        new TimestampRecord(
                                innerEverything.subObjectList().get(2).seconds(),
                                innerEverything.subObjectList().get(2).nanos())),
                innerEverything.textList(),
                innerEverything.bytesExampleList(),
                innerEverything.int32Boxed(),
                innerEverything.uint32Boxed(),
                innerEverything.int64Boxed(),
                innerEverything.uint64Boxed(),
                innerEverything.floatBoxed(),
                innerEverything.doubleBoxed(),
                innerEverything.boolBoxed(),
                innerEverything.bytesBoxed(),
                innerEverything.stringBoxed(),
                innerEverything.oneofExample());
    }

    /** Sample EverythingRecord object for testing, same data as EVERYTHING above */
    static final EverythingRecord EVERYTHING_RECORD = new EverythingRecord(
            EVERYTHING.int32Number(),
            EVERYTHING.sint32Number(),
            EVERYTHING.uint32Number(),
            EVERYTHING.fixed32Number(),
            EVERYTHING.sfixed32Number(),
            EVERYTHING.floatNumber(),
            EVERYTHING.int64Number(),
            EVERYTHING.sint64Number(),
            EVERYTHING.uint64Number(),
            EVERYTHING.fixed64Number(),
            EVERYTHING.sfixed64Number(),
            EVERYTHING.doubleNumber(),
            EVERYTHING.booleanField(),
            EVERYTHING.enumSuit(),
            new TimestampRecord(
                    EVERYTHING.subObject().seconds(), EVERYTHING.subObject().nanos()),
            EVERYTHING.text(),
            EVERYTHING.bytesField(),
            convertInnerEverything(EVERYTHING.innerEverything()),
            EVERYTHING.mapInt32ToString(),
            EVERYTHING.mapBoolToDouble(),
            PbjMap.of(Map.of(
                    "One",
                            convertInnerEverything(
                                    EVERYTHING.mapStringToMessage().get("One")),
                    "Two",
                            convertInnerEverything(
                                    EVERYTHING.mapStringToMessage().get("Two")),
                    "Three",
                            convertInnerEverything(
                                    EVERYTHING.mapStringToMessage().get("Three")))),
            EVERYTHING.mapUInt64ToBytes(),
            EVERYTHING.mapInt64ToBool(),
            EVERYTHING.int32NumberList(),
            EVERYTHING.sint32NumberList(),
            EVERYTHING.uint32NumberList(),
            EVERYTHING.fixed32NumberList(),
            EVERYTHING.sfixed32NumberList(),
            EVERYTHING.floatNumberList(),
            EVERYTHING.int64NumberList(),
            EVERYTHING.sint64NumberList(),
            EVERYTHING.uint64NumberList(),
            EVERYTHING.fixed64NumberList(),
            EVERYTHING.sfixed64NumberList(),
            EVERYTHING.doubleNumberList(),
            EVERYTHING.booleanList(),
            EVERYTHING.enumSuitList(),
            List.of(
                    new TimestampRecord(
                            EVERYTHING.subObjectList().getFirst().seconds(),
                            EVERYTHING.subObjectList().getFirst().nanos()),
                    new TimestampRecord(
                            EVERYTHING.subObjectList().get(1).seconds(),
                            EVERYTHING.subObjectList().get(1).nanos()),
                    new TimestampRecord(
                            EVERYTHING.subObjectList().get(2).seconds(),
                            EVERYTHING.subObjectList().get(2).nanos())),
            EVERYTHING.textList(),
            EVERYTHING.bytesExampleList(),
            EVERYTHING.int32Boxed(),
            EVERYTHING.uint32Boxed(),
            EVERYTHING.int64Boxed(),
            EVERYTHING.uint64Boxed(),
            EVERYTHING.floatBoxed(),
            EVERYTHING.doubleBoxed(),
            EVERYTHING.boolBoxed(),
            EVERYTHING.bytesBoxed(),
            EVERYTHING.stringBoxed(),
            EVERYTHING.oneofExample());
}
