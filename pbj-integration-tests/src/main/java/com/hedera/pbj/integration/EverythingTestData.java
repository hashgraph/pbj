package com.hedera.pbj.integration;

import com.hedera.pbj.runtime.io.Bytes;
import com.hederahashgraph.api.proto.pbj.test.Everything;
import com.hederahashgraph.api.proto.pbj.test.InnerEverything;
import com.hederahashgraph.api.proto.pbj.test.Suit;
import com.hederahashgraph.api.proto.pbj.test.TimestampTest;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Sample test data for everything object
 */
public class EverythingTestData {

    // input objects
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
            .subObject(new TimestampTest.Builder().seconds(5155135L).nanos(44513).build())
            .text("Hello Everything!")
            .bytesField(Bytes.wrap(new byte[]{12,29,19,120,127,0,-127}))
            .int32NumberList(IntStream.range(0,10).boxed().toList())
            .sint32NumberList(IntStream.range(-10,10).boxed().toList())
            .uint32NumberList(IntStream.range(0,100).boxed().toList())
            .fixed32NumberList(IntStream.range(0,25).boxed().toList())
            .sfixed32NumberList(IntStream.range(-10,25).boxed().toList())
            .floatNumberList(List.of(513.51f,55535351.3545841f,0f,-1f))
            .floatNumberList(List.of(513.51f,55535351.3545841f,0f,-1f))
            .int64NumberList(LongStream.range(0,10).boxed().toList())
            .sint64NumberList(LongStream.range(-10,10).boxed().toList())
            .uint64NumberList(LongStream.range(0,10).boxed().toList())
            .fixed64NumberList(LongStream.range(0,10).boxed().toList())
            .sfixed64NumberList(LongStream.range(-10,10).boxed().toList())
            .doubleNumberList(List.of(513.51,55535351.3545841,0d,-1d))
            .booleanList(List.of(true, false, true, true, false))
            .enumSuitList(List.of(Suit.ACES, Suit.CLUBS, Suit.DIAMONDS))
            .subObjectList(List.of(
                    new TimestampTest.Builder().seconds(5155135L).nanos(44513).build(),
                    new TimestampTest.Builder().seconds(486486).nanos(31315).build(),
                    new TimestampTest.Builder().seconds(0).nanos(58).build()
            ))
            .textList(List.of(
                    "صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت — يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ",
                    "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम, अयोध्या के महाराज दशरथ के बड़े सपुत्र थे।",
                    "A quick brown fox jumps over the lazy dog"
            ))
            .bytesExampleList(List.of(
                    Bytes.wrap(new byte[]{12,29,19,120,127,0,-127}),
                    Bytes.wrap(new byte[]{13,15,65,98,-65}),
                    Bytes.wrap(new byte[]{127,0,-127})
            ))
            .int32Optional(Optional.of(1234))
            .uint32Optional(Optional.of(Integer.MAX_VALUE))
            .floatOptional(Optional.of(15834.213581f))
            .int64Optional(Optional.of(53451121355L))
            .uint64Optional(Optional.of(2451326663131L))
            .doubleOptional(Optional.of(135581531681.1535151))
            .boolOptional(Optional.of(true))
            .bytesOptional(Optional.of(Bytes.wrap(new byte[]{13,15,65,98,-65})))
            .stringOptional(Optional.of("Hello Everything!"))
            .doubleNumberOneOf(29292.299d)
            .innerEverything(new InnerEverything.Builder()
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
                    .subObject(new TimestampTest.Builder().seconds(5155135L).nanos(44513).build())
                    .text("Hello Everything!")
                    .bytesField(Bytes.wrap(new byte[]{12,29,19,120,127,0,-127}))
                    .int32NumberList(IntStream.range(0,10).boxed().toList())
                    .sint32NumberList(IntStream.range(-10,10).boxed().toList())
                    .uint32NumberList(IntStream.range(0,100).boxed().toList())
                    .fixed32NumberList(IntStream.range(0,25).boxed().toList())
                    .sfixed32NumberList(IntStream.range(-10,25).boxed().toList())
                    .floatNumberList(List.of(513.51f,55535351.3545841f,0f,-1f))
                    .floatNumberList(List.of(513.51f,55535351.3545841f,0f,-1f))
                    .int64NumberList(LongStream.range(0,10).boxed().toList())
                    .sint64NumberList(LongStream.range(-10,10).boxed().toList())
                    .uint64NumberList(LongStream.range(0,10).boxed().toList())
                    .fixed64NumberList(LongStream.range(0,10).boxed().toList())
                    .sfixed64NumberList(LongStream.range(-10,10).boxed().toList())
                    .doubleNumberList(List.of(513.51,55535351.3545841,0d,-1d))
                    .booleanList(List.of(true, false, true, true, false))
                    .enumSuitList(List.of(Suit.ACES, Suit.CLUBS, Suit.DIAMONDS))
                    .subObjectList(List.of(
                            new TimestampTest.Builder().seconds(5155135L).nanos(44513).build(),
                            new TimestampTest.Builder().seconds(486486).nanos(31315).build(),
                            new TimestampTest.Builder().seconds(0).nanos(58).build()
                    ))
                    .textList(List.of(
                            "صِف خَلقَ خَودِ كَمِثلِ الشَمسِ إِذ بَزَغَت — يَحظى الضَجيعُ بِها نَجلاءَ مِعطارِ",
                            "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम, अयोध्या के महाराज दशरथ के बड़े सपुत्र थे।",
                            "A quick brown fox jumps over the lazy dog"
                    ))
                    .bytesExampleList(List.of(
                            Bytes.wrap(new byte[]{12,29,19,120,127,0,-127}),
                            Bytes.wrap(new byte[]{13,15,65,98,-65}),
                            Bytes.wrap(new byte[]{127,0,-127})
                    ))
                    .int32Optional(Optional.of(1234))
                    .uint32Optional(Optional.of(Integer.MAX_VALUE))
                    .floatOptional(Optional.of(15834.213581f))
                    .int64Optional(Optional.of(53451121355L))
                    .uint64Optional(Optional.of(2451326663131L))
                    .doubleOptional(Optional.of(135581531681.1535151))
                    .boolOptional(Optional.of(true))
                    .bytesOptional(Optional.of(Bytes.wrap(new byte[]{13,15,65,98,-65})))
                    .stringOptional(Optional.of("Hello Everything!"))
                    .doubleNumberOneOf(29292.299d)
                    .build())
            .build();
}
