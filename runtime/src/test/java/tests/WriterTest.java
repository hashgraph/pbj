package tests;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import sample.target.model.*;
import sample.target.proto.writers.AppleWriter;
import sample.target.proto.writers.OmnibusWriter;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class WriterTest {
    // Also need object test and one-of test
    static Stream<List<Nested>> nestedList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(new sample.target.model.Nested("Bob"),
                        new sample.target.model.Nested("Sue")),
                List.of(new sample.target.model.Nested("Bob"),
                        new sample.target.model.Nested("Bob"),
                        new sample.target.model.Nested("Fred"),
                        new sample.target.model.Nested("Sally")));
    }

    static Stream<Nested> nested() {
        return Stream.of(
                new sample.target.model.Nested(""),
                new sample.target.model.Nested("I have written a memo"),
                new sample.target.model.Nested("My memo uses special characters like ℏ"),
                new sample.target.model.Nested("""
                        To be, or not to be, that is the question:
                        Whether ’tis nobler in the mind to suffer
                        The slings and arrows of outrageous fortune,
                        Or to take arms against a sea of troubles
                        And by opposing end them. To die—to sleep,
                        No more; and by a sleep to say we end
                        The heart-ache and the thousand natural shocks
                        That flesh is heir to: ’tis a consummation
                        Devoutly to be wish’d. To die, to sleep;
                        To sleep, perchance to dream—ay, there’s the rub:
                        For in that sleep of death what dreams may come,
                        When we have shuffled off this mortal coil,
                        Must give us pause—there’s the respect
                        That makes calamity of so long life…"""));
    }

    static Stream<List<Object>> fruitList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(new sample.target.model.Apple("Gala"),
                        new sample.target.model.Banana("Yellow")),
                List.of(new sample.target.model.Apple("Gala"),
                        new sample.target.model.Banana("Yellow"),
                        new sample.target.model.Banana("Short"),
                        new sample.target.model.Apple("Honey Crisp"),
                        new sample.target.model.Banana("Green")));
    }

    static Stream<byte[]> byteArrays() {
        return Stream.of(
                new byte[0],
                new byte[]{0b001},
                new byte[]{0b001, 0b010, 0b011});
    }

    static Stream<List<Integer>> intList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(0, 1, 2),
                List.of(Integer.MIN_VALUE, -42, -21, 0, 21, 42, Integer.MAX_VALUE));
    }

    static Stream<List<Integer>> uintList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(0, 1, 2, Integer.MAX_VALUE));
    }

    static Stream<List<Long>> longList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(0L, 1L, 2L),
                List.of(Long.MIN_VALUE, -42L, -21L, 0L, 21L, 42L, Long.MAX_VALUE));
    }

    static Stream<List<Long>> ulongList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(0L, 1L, 2L, Long.MAX_VALUE));
    }

//    @Test
//    void buildNullStringThrows() throws Exception {
//        final var out = new ByteArrayOutputStream();
//        final var builder = new AppleWriter();
//        assertThrows(NullPointerException.class, () -> builder.variety(null));
//    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -5, 1, 3, 5, Integer.MAX_VALUE})
    void writeInt32Only(int val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setInt32Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int32Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, -5, 1, 3, 5, Long.MAX_VALUE })
    void writeInt64Only(long val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setInt64Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int64Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -5, 1, 3, 5, Integer.MAX_VALUE })
    void writeUint32Only(int val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setUint32Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint32Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, -5, 1, 3, 5, Long.MAX_VALUE })
    void writeUint64Only(long val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setUint64Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint64Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void writeBooleanOnly(boolean val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setFlag(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().flag(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 2, 3 })
    void writeSuitOnly(int ordinal) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setSuitEnum(test.proto.Suit.valueOf(Suit.fromOrdinal(ordinal).name()))
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().suitEnum(Suit.fromOrdinal(ordinal)).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
    void writeSint32Only(int val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setSint32Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint32Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
    void writeSint64Only(long val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setSint64Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint64Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
    void writeSfixed32Only(int val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setSfixed32Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed32Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
    void writeSfixed64Only(long val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setSfixed64Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed64Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
    void writeFixed32Only(int val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setFixed32Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed32Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
    void writeFixed64Only(long val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setFixed64Number(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed64Number(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(floats = {Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -102.7f, -5f, 1.7f, 0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NaN})
    void writeFloatOnly(float val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setFloatNumber(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().floatNumber(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -102.7, -5, 0, 1.7, 3, 5.2, 42.1, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN})
    void writeDoubleOnly(double val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setDoubleNumber(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().doubleNumber(val).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "A String",
            "I need some HBAR to run work on Hedera!",
            "I need some ℏ to run work on Hedera!",
            """
                    To be, or not to be, that is the question:
                    Whether ’tis nobler in the mind to suffer
                    The slings and arrows of outrageous fortune,
                    Or to take arms against a sea of troubles
                    And by opposing end them. To die—to sleep,
                    No more; and by a sleep to say we end
                    The heart-ache and the thousand natural shocks
                    That flesh is heir to: ’tis a consummation
                    Devoutly to be wish’d. To die, to sleep;
                    To sleep, perchance to dream—ay, there’s the rub:
                    For in that sleep of death what dreams may come,
                    When we have shuffled off this mortal coil,
                    Must give us pause—there’s the respect
                    That makes calamity of so long life…"""
    })
    void writeStringOnly(String val) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setMemo(val)
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new AppleWriter().write(new Apple(val), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("byteArrays")
    void writeByteArraysOnly(byte[] val) throws Exception {
        final ByteBuffer buf = ByteBuffer.wrap(val).asReadOnlyBuffer();
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setRandomBytes(ByteString.copyFrom(val))
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().randomBytes(buf).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("nested")
    void writeNestedObjectOnly(Nested nested) throws Exception {
        final var protobuf = test.proto.Omnibus.newBuilder()
                .setNested(test.proto.Nested.newBuilder().setNestedMemo(nested.nestedMemo()))
                .build()
                .toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().nested(nested).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @Test
    void parseOneOfFruitOnly() throws Exception {
        var protobuf = test.proto.Omnibus.newBuilder()
                .setApple(test.proto.Apple.newBuilder().setVariety("Gala").build())
                .build()
                .toByteArray();

        var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().apple(new Apple("Gala")).build(), out);
        var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setBanana(test.proto.Banana.newBuilder().setVariety("Yellow").build())
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().banana(new Banana("Yellow")).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @Test
    void parseOneOfEverythingOnly() throws Exception {
        var protobuf = test.proto.Omnibus.newBuilder()
                .setInt32Unique(-42)
                .build()
                .toByteArray();

        var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int32Unique(-42).build(), out);
        var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setInt64Unique(-43)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int64Unique(-43L).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setUint32Unique(44)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint32Unique(44).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setUint64Unique(45)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint64Unique(45L).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setFlagUnique(true)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().flagUnique(true).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setSuitEnumUnique(test.proto.Suit.CLUBS)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().suitEnumUnique(Suit.CLUBS).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setSint32Unique(-46)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint32Unique(-46).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setSint64Unique(-47)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint64Unique(-47L).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setSfixed32Unique(-48)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed32Unique(-48).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setSfixed64Unique(-49)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed64Unique(-49L).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setFixed32Unique(50)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed32Unique(50).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setFixed64Unique(51)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed64Unique(51).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setFloatUnique(52.3f)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().floatUnique(52.3f).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setDoubleUnique(54.5)
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().doubleUnique(54.5).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setMemoUnique("Learn BASIC Now!")
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().memoUnique("Learn BASIC Now!").build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setRandomBytesUnique(ByteString.copyFrom(new byte[]{(byte) 55, (byte) 56}))
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().randomBytesUnique(ByteBuffer.wrap(new byte[]{(byte) 55, (byte) 56}).asReadOnlyBuffer()).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);

        protobuf = test.proto.Omnibus.newBuilder()
                .setNestedUnique(test.proto.Nested.newBuilder().setNestedMemo("Reminder").build())
                .build()
                .toByteArray();

        out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().nestedUnique(new Nested("Reminder")).build(), out);
        protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("intList")
    void writeInt32ListOnly(List<Integer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Integer integer : list) {
            protobufBuilder.addInt32NumberList(integer);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int32NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("uintList")
    void writeUint32ListOnly(List<Integer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Integer integer : list) {
            protobufBuilder.addUint32NumberList(integer);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint32NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("intList")
    void writeSint32ListOnly(List<Integer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Integer integer : list) {
            protobufBuilder.addSint32NumberList(integer);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint32NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("intList")
    void writeSfixed32ListOnly(List<Integer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Integer integer : list) {
            protobufBuilder.addSfixed32NumberList(integer);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed32NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("intList")
    void parseFixed32ListOnly(List<Integer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Integer integer : list) {
            protobufBuilder.addFixed32NumberList(integer);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed32NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("longList")
    void parseInt64ListOnly(List<Long> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Long aLong : list) {
            protobufBuilder.addInt64NumberList(aLong);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().int64NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("ulongList")
    void parseUint64ListOnly(List<Long> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Long aLong : list) {
            protobufBuilder.addUint64NumberList(aLong);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().uint64NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("longList")
    void parseSint64ListOnly(List<Long> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Long aLong : list) {
            protobufBuilder.addSint64NumberList(aLong);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sint64NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    static Stream<List<Boolean>> boolList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(false, false, true),
                List.of(true, false, true, true, false, true, false, false, false, true));
    }

    @ParameterizedTest
    @MethodSource("longList")
    void parseSfixed64ListOnly(List<Long> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Long aLong : list) {
            protobufBuilder.addSfixed64NumberList(aLong);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().sfixed64NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    static Stream<List<Suit>> suitList() {
        return Stream.of(
                Collections.emptyList(),
                List.of(Suit.ACES, Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES),
                // TODO add more, just for good measure
                List.of(Suit.ACES, Suit.ACES, Suit.DIAMONDS, Suit.ACES));
    }

    static Stream<List<String>> stringList() {
        return Stream.of(
                Collections.emptyList(),
                List.of("first", "third"),
                List.of("I", "have", "a", "joke", ",", "Who's", "on", "first?"));
    }

    @ParameterizedTest
    @MethodSource("longList")
    void parseFixed64ListOnly(List<Long> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Long aLong : list) {
            protobufBuilder.addFixed64NumberList(aLong);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().fixed64NumberList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    static Stream<List<ByteBuffer>> byteList() {
        final List<byte[]> empty = Collections.emptyList();
        return Stream.of(
                        empty,
                        List.of("What".getBytes(), "Is".getBytes()),
                        List.of("This".getBytes(), "Gonna".getBytes(), "Do?".getBytes()))
                .map(arrayList -> arrayList.stream().map(array -> ByteBuffer.wrap(array).asReadOnlyBuffer()).toList());
    }

    @ParameterizedTest
    @MethodSource("boolList")
    void parseBooleanListOnly(List<Boolean> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Boolean aBoolean : list) {
            protobufBuilder.addFlagList(aBoolean);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().flagList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("suitList")
    void parseEnumListOnly(List<Suit> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (Suit suit : list) {
            protobufBuilder.addSuitEnumListValue(suit.ordinal());
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().suitEnumList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("stringList")
    void parseStringListOnly(List<String> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (String s : list) {
            protobufBuilder.addMemoList(s);
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().memoList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("nestedList")
    void parseNestedListOnly(List<sample.target.model.Nested> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (sample.target.model.Nested nested : list) {
            protobufBuilder.addNestedList(test.proto.Nested.newBuilder().setNestedMemo(nested.nestedMemo()));
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().nestedList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }

    @ParameterizedTest
    @MethodSource("byteList")
    void parseBytesListOnly(List<ByteBuffer> list) throws Exception {
        final var protobufBuilder = test.proto.Omnibus.newBuilder();
        for (ByteBuffer bytes : list) {
            protobufBuilder.addRandomBytesList(ByteString.copyFrom(bytes));
        }
        final var protobuf = protobufBuilder.build().toByteArray();

        final var out = new ByteArrayOutputStream();
        new OmnibusWriter().write(
                new Omnibus.Builder().randomBytesList(list).build(), out);
        final var protobuf2 = out.toByteArray();

        assertArrayEquals(protobuf, protobuf2);
    }
}
