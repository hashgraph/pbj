// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.InnerEverything;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Sample test data for everything object
 */
public class EverythingTestData {

    /**
     * Empty constructor
     */
    public EverythingTestData() {
        // no-op
    }

    /**
     * Sample InnerEverything object for testing
     */
    public static final InnerEverything INNER_EVERYTHING = new InnerEverything.Builder()
            .int32Number(1234)
            .sint32Number(-1234)
            .uint32Number(Integer.MAX_VALUE)
            .fixed32Number(644534)
            .sfixed32Number(-31345)
            .floatNumber(15834.213581f)
            .int64Number(53451121355L)
            .sint64Number(-53451121355L)
            .uint64Number(2451326663131L)
            .fixed64Number(33626188515L)
            .sfixed64Number(-531311551515L)
            .doubleNumber(135581531681.1535151)
            .booleanField(true)
            .enumSuit(Suit.SPADES)
            .subObject(
                    new TimestampTest.Builder().seconds(5155135L).nanos(44513).build())
            .text("Hello Everything!")
            .bytesField(Bytes.wrap(new byte[] {12, 29, 19, 120, 127, 0, -127}))
            .int32NumberList(IntStream.range(0, 10).boxed().toList())
            .sint32NumberList(IntStream.range(-10, 10).boxed().toList())
            .uint32NumberList(IntStream.range(0, 100).boxed().toList())
            .fixed32NumberList(IntStream.range(0, 25).boxed().toList())
            .sfixed32NumberList(IntStream.range(-10, 25).boxed().toList())
            .floatNumberList(List.of(513.51f, 55535351.3545841f, 0f, -1f))
            .floatNumberList(List.of(513.51f, 55535351.3545841f, 0f, -1f))
            .int64NumberList(LongStream.range(0, 10).boxed().toList())
            .sint64NumberList(LongStream.range(-10, 10).boxed().toList())
            .uint64NumberList(LongStream.range(0, 10).boxed().toList())
            .fixed64NumberList(LongStream.range(0, 10).boxed().toList())
            .sfixed64NumberList(LongStream.range(-10, 10).boxed().toList())
            .doubleNumberList(List.of(513.51, 55535351.3545841, 0d, -1d))
            .booleanList(List.of(true, false, true, true, false))
            .enumSuitList(List.of(Suit.ACES, Suit.CLUBS, Suit.DIAMONDS))
            .subObjectList(List.of(
                    new TimestampTest.Builder().seconds(5155135L).nanos(44513).build(),
                    new TimestampTest.Builder().seconds(486486).nanos(31315).build(),
                    new TimestampTest.Builder().seconds(0).nanos(58).build()))
            .textList(List.of(
                    "صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت — يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ",
                    "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम, अयोध्या के महाराज दशरथ के बड़े सपुत्र थे।",
                    "A quick brown fox jumps over the lazy dog"))
            .bytesExampleList(List.of(
                    Bytes.wrap(new byte[] {12, 29, 19, 120, 127, 0, -127}),
                    Bytes.wrap(new byte[] {13, 15, 65, 98, -65}),
                    Bytes.wrap(new byte[] {127, 0, -127})))
            .int32Boxed(1234)
            .uint32Boxed(Integer.MAX_VALUE)
            .floatBoxed(15834.213581f)
            .int64Boxed(53451121355L)
            .uint64Boxed(2451326663131L)
            .doubleBoxed(135581531681.1535151)
            .boolBoxed(true)
            .bytesBoxed(Bytes.wrap(new byte[] {13, 15, 65, 98, -65}))
            .stringBoxed("Hello Everything!")
            .doubleNumberOneOf(29292.299d)
            .build();

    /**
     * Sample Everything object for testing
     */
    public static final Everything EVERYTHING = new Everything.Builder()
            .int32Number(1234)
            .sint32Number(-1234)
            .uint32Number(Integer.MAX_VALUE)
            .fixed32Number(644534)
            .sfixed32Number(-31345)
            .floatNumber(15834.213581f)
            .int64Number(53451121355L)
            .sint64Number(-53451121355L)
            .uint64Number(2451326663131L)
            .fixed64Number(33626188515L)
            .sfixed64Number(-531311551515L)
            .doubleNumber(135581531681.1535151)
            .booleanField(true)
            .enumSuit(Suit.SPADES)
            .subObject(
                    new TimestampTest.Builder().seconds(5155135L).nanos(44513).build())
            .text("Hello Everything!")
            .bytesField(Bytes.wrap(new byte[] {12, 29, 19, 120, 127, 0, -127}))
            .innerEverything(INNER_EVERYTHING)
            .mapInt32ToString(Map.of(1, "One", 2, "Two", 3, "Three"))
            .mapBoolToDouble(Map.of(true, 100000000.0, false, 0.00000123))
            .mapStringToMessage(Map.of(
                    "One", INNER_EVERYTHING,
                    "Two", INNER_EVERYTHING.copyBuilder().int32Number(2).build(),
                    "Three", INNER_EVERYTHING.copyBuilder().int32Number(3).build()))
            .mapUInt64ToBytes(Map.of(
                    1L, Bytes.wrap(new byte[] {12, 29, 19, 120, 127, 0, -127}),
                    2L, Bytes.wrap(new byte[] {13, 15, 65, 98, -65}),
                    3L, Bytes.wrap(new byte[] {127, 0, -127})))
            .mapInt64ToBool(Map.of(1L, true, 2L, false, 3L, true))
            .int32NumberList(IntStream.range(0, 10).boxed().toList())
            .sint32NumberList(IntStream.range(-10, 10).boxed().toList())
            .uint32NumberList(IntStream.range(0, 100).boxed().toList())
            .fixed32NumberList(IntStream.range(0, 25).boxed().toList())
            .sfixed32NumberList(IntStream.range(-10, 25).boxed().toList())
            .floatNumberList(List.of(513.51f, 55535351.3545841f, 0f, -1f))
            .floatNumberList(List.of(513.51f, 55535351.3545841f, 0f, -1f))
            .int64NumberList(LongStream.range(0, 10).boxed().toList())
            .sint64NumberList(LongStream.range(-10, 10).boxed().toList())
            .uint64NumberList(LongStream.range(0, 10).boxed().toList())
            .fixed64NumberList(LongStream.range(0, 10).boxed().toList())
            .sfixed64NumberList(LongStream.range(-10, 10).boxed().toList())
            .doubleNumberList(List.of(513.51, 55535351.3545841, 0d, -1d))
            .booleanList(List.of(true, false, true, true, false))
            .enumSuitList(List.of(Suit.ACES, Suit.CLUBS, Suit.DIAMONDS))
            .subObjectList(List.of(
                    new TimestampTest.Builder().seconds(5155135L).nanos(44513).build(),
                    new TimestampTest.Builder().seconds(486486).nanos(31315).build(),
                    new TimestampTest.Builder().seconds(0).nanos(58).build()))
            .textList(List.of(
                    "صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت — يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ",
                    "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम, अयोध्या के महाराज दशरथ के बड़े सपुत्र थे।",
                    "A quick brown fox jumps over the lazy dog"))
            .bytesExampleList(List.of(
                    Bytes.wrap(new byte[] {12, 29, 19, 120, 127, 0, -127}),
                    Bytes.wrap(new byte[] {13, 15, 65, 98, -65}),
                    Bytes.wrap(new byte[] {127, 0, -127})))
            .int32Boxed(1234)
            .uint32Boxed(Integer.MAX_VALUE)
            .int64Boxed(53451121355L)
            .uint64Boxed(2451326663131L)
            .floatBoxed(15834.213581f)
            .doubleBoxed(135581531681.1535151)
            .boolBoxed(true)
            .bytesBoxed(Bytes.wrap(new byte[] {13, 15, 65, 98, -65}))
            .stringBoxed("Hello Everything!")
            .doubleNumberOneOf(29292.299d)
            .build();
}
