package tests;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import sample.target.model.Suit;
import sample.target.proto.parsers.OmnibusParser;
import test.proto.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ParseTest {

	private final OmnibusParser parser = new OmnibusParser();

	// Also need object test and one-of test
	static Stream<List<sample.target.model.Nested>> nestedList() {
		return Stream.of(
				Collections.emptyList(),
				List.of(new sample.target.model.Nested("Bob"),
						new sample.target.model.Nested("Sue")),
				List.of(new sample.target.model.Nested("Bob"),
						new sample.target.model.Nested("Bob"),
						new sample.target.model.Nested("Fred"),
						new sample.target.model.Nested("Sally")));
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

	@ParameterizedTest
	@ValueSource(ints = {Integer.MIN_VALUE, -5, 0, 1, 3, 5, Integer.MAX_VALUE})
	void parseInt32Only(int val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setInt32Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.int32Number());
	}

	@ParameterizedTest
	@ValueSource(longs = { Long.MIN_VALUE, -5, 0, 1, 3, 5, Long.MAX_VALUE })
	void parseInt64Only(long val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setInt64Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.int64Number());
	}

	@ParameterizedTest
	@ValueSource(ints = { Integer.MIN_VALUE, -5, 0, 1, 3, 5, Integer.MAX_VALUE })
	void parseUint32Only(int val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setUint32Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.uint32Number());
	}

	@ParameterizedTest
	@ValueSource(longs = { Long.MIN_VALUE, -5, 0, 1, 3, 5, Long.MAX_VALUE })
	void parseUint64Only(long val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setUint64Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.uint64Number());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void parseBooleanOnly(boolean val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setFlag(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.flag());
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3 })
	void parseSuitOnly(int ordinal) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setSuitEnum(test.proto.Suit.valueOf(Suit.fromOrdinal(ordinal).name()))
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(ordinal, omnibus.suitEnum().ordinal());
	}

	@ParameterizedTest
	@ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
	void parseSint32Only(int val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setSint32Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.sint32Number());
	}

	@ParameterizedTest
	@ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
	void parseSint64Only(long val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setSint64Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.sint64Number());
	}

	@ParameterizedTest
	@ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
	void parseSfixed32Only(int val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setSfixed32Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.sfixed32Number());
	}

	@ParameterizedTest
	@ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
	void parseSfixed64Only(long val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setSfixed64Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.sfixed64Number());
	}

	@ParameterizedTest
	@ValueSource(ints = { Integer.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Integer.MAX_VALUE })
	void parseFixed32Only(int val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setFixed32Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.fixed32Number());
	}

	@ParameterizedTest
	@ValueSource(longs = { Long.MIN_VALUE, -102, -5, 0, 1, 3, 5, 42, Long.MAX_VALUE })
	void parseFixed64Only(long val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setFixed64Number(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.fixed64Number());
	}

	@ParameterizedTest
	@ValueSource(floats = {Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -102.7f, -5f, 0f, 1.7f, 3f, 5.2f, 42.1f, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NaN})
	void parseFloatOnly(float val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setFloatNumber(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.floatNumber());
	}

	@ParameterizedTest
	@ValueSource(doubles = {Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -102.7, -5, 0, 1.7, 3, 5.2, 42.1, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN})
	void parseDoubleOnly(double val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setDoubleNumber(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.doubleNumber());
	}

	// TODO Add a test with a VERY LONG STRING to force varint behavior in length
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
	void parseStringOnly(String val) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setMemo(val)
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(val, omnibus.memo());
	}

	@ParameterizedTest
	@MethodSource("byteArrays")
	void parseByteArraysOnly(byte[] val) throws Exception {
		final ByteBuffer buf = ByteBuffer.wrap(val).asReadOnlyBuffer();
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setRandomBytes(ByteString.copyFrom(val))
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(buf, omnibus.randomBytes());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "", ""})
	void parseNestedObjectOnly(String nestedMemo) throws Exception {
		final var protobuf = test.proto.Omnibus.newBuilder()
				.setNested(Nested.newBuilder().setNestedMemo(nestedMemo))
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(new sample.target.model.Nested(nestedMemo), omnibus.nested());
	}

	@Test
	void parseOneOfFruitOnly() throws Exception {
		var protobuf = Omnibus.newBuilder()
				.setApple(Apple.newBuilder().setVariety("Gala").build())
				.build()
				.toByteArray();

		var omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Fruits.FruitKind.APPLE, omnibus.fruit().kind());
		assertEquals(new sample.target.model.Apple("Gala"), omnibus.fruit().value());

		protobuf = Omnibus.newBuilder()
				.setBanana(Banana.newBuilder().setVariety("Yellow").build())
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Fruits.FruitKind.BANANA, omnibus.fruit().kind());
		assertEquals(new sample.target.model.Banana("Yellow"), omnibus.fruit().value());
	}

	@Test
	void parseOneOfFruitOnlyLastOneWins() throws Exception {
		var protobuf = Omnibus.newBuilder()
				.setApple(Apple.newBuilder().setVariety("Gala").build())
				.setBanana(Banana.newBuilder().setVariety("Yellow").build())
				.build()
				.toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Fruits.FruitKind.BANANA, omnibus.fruit().kind());
		assertEquals(new sample.target.model.Banana("Yellow"), omnibus.fruit().value());
	}

	@Test
	void parseOneOfEverythingOnly() throws Exception {
		var protobuf = Omnibus.newBuilder()
				.setInt32Unique(-42)
				.build()
				.toByteArray();

		var omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.INT32, omnibus.everything().kind());
		assertEquals(-42, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setInt64Unique(-43)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.INT64, omnibus.everything().kind());
		assertEquals(-43L, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setUint32Unique(44)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.UINT32, omnibus.everything().kind());
		assertEquals(44, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setUint64Unique(45)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.UINT64, omnibus.everything().kind());
		assertEquals(45L, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setFlagUnique(true)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.FLAG, omnibus.everything().kind());
		assertTrue((boolean) omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setSuitEnumUnique(test.proto.Suit.CLUBS)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.SUIT, omnibus.everything().kind());
		assertEquals(Suit.CLUBS, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setSint32Unique(-46)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.SINT32, omnibus.everything().kind());
		assertEquals(-46, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setSint64Unique(-47)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.SINT64, omnibus.everything().kind());
		assertEquals(-47L, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setSfixed32Unique(-48)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.SFIXED32, omnibus.everything().kind());
		assertEquals(-48, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setSfixed64Unique(-49)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.SFIXED64, omnibus.everything().kind());
		assertEquals(-49L, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setFixed32Unique(50)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.FIXED32, omnibus.everything().kind());
		assertEquals(50, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setFixed64Unique(51)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.FIXED64, omnibus.everything().kind());
		assertEquals(51L, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setFloatUnique(52.3f)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.FLOAT, omnibus.everything().kind());
		assertEquals(52.3f, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setDoubleUnique(54.5)
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.DOUBLE, omnibus.everything().kind());
		assertEquals(54.5, omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setMemoUnique("Learn BASIC Now!")
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.MEMO, omnibus.everything().kind());
		assertEquals("Learn BASIC Now!", omnibus.everything().value());

		protobuf = Omnibus.newBuilder()
				.setRandomBytesUnique(ByteString.copyFrom(new byte[]{(byte) 55, (byte) 56}))
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.RANDOM_BYTES, omnibus.everything().kind());
		assertEquals(ByteBuffer.wrap(new byte[]{(byte) 55, (byte) 56}).asReadOnlyBuffer(), omnibus.everything().as());

		protobuf = Omnibus.newBuilder()
				.setNestedUnique(Nested.newBuilder().setNestedMemo("Reminder").build())
				.build()
				.toByteArray();

		omnibus = parser.parse(protobuf);
		assertEquals(sample.target.model.Omnibus.Everything.NESTED, omnibus.everything().kind());
		assertEquals(new sample.target.model.Nested("Reminder"), omnibus.everything().value());
	}

	@ParameterizedTest
	@MethodSource("intList")
	void parseInt32ListOnly(List<Integer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Integer integer : list) {
			protobufBuilder.addInt32NumberList(integer);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.int32NumberList());
	}

	@ParameterizedTest
	@MethodSource("uintList")
	void parseUint32ListOnly(List<Integer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Integer integer : list) {
			protobufBuilder.addUint32NumberList(integer);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.uint32NumberList());
	}

	@ParameterizedTest
	@MethodSource("intList")
	void parseSint32ListOnly(List<Integer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Integer integer : list) {
			protobufBuilder.addSint32NumberList(integer);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.sint32NumberList());
	}

	@ParameterizedTest
	@MethodSource("intList")
	void parseSfixed32ListOnly(List<Integer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Integer integer : list) {
			protobufBuilder.addSfixed32NumberList(integer);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.sfixed32NumberList());
	}

	@ParameterizedTest
	@MethodSource("intList")
	void parseFixed32ListOnly(List<Integer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Integer integer : list) {
			protobufBuilder.addFixed32NumberList(integer);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.fixed32NumberList());
	}

	@ParameterizedTest
	@MethodSource("longList")
	void parseInt64ListOnly(List<Long> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Long aLong : list) {
			protobufBuilder.addInt64NumberList(aLong);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.int64NumberList());
	}

	@ParameterizedTest
	@MethodSource("ulongList")
	void parseUint64ListOnly(List<Long> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Long aLong : list) {
			protobufBuilder.addUint64NumberList(aLong);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.uint64NumberList());
	}

	@ParameterizedTest
	@MethodSource("longList")
	void parseSint64ListOnly(List<Long> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Long aLong : list) {
			protobufBuilder.addSint64NumberList(aLong);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.sint64NumberList());
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

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.sfixed64NumberList());
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

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.fixed64NumberList());
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

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.flagList());
	}

	@ParameterizedTest
	@MethodSource("suitList")
	void parseEnumListOnly(List<Suit> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (Suit suit : list) {
			protobufBuilder.addSuitEnumListValue(suit.ordinal());
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.suitEnumList());
	}

	@ParameterizedTest
	@MethodSource("stringList")
	void parseStringListOnly(List<String> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (String s : list) {
			protobufBuilder.addMemoList(s);
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.memoList());
	}

	@ParameterizedTest
	@MethodSource("nestedList")
	void parseNestedListOnly(List<sample.target.model.Nested> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (sample.target.model.Nested nested : list) {
			protobufBuilder.addNestedList(Nested.newBuilder().setNestedMemo(nested.nestedMemo()));
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list, omnibus.nestedList());
	}

	@ParameterizedTest
	@MethodSource("byteList")
	void parseBytesListOnly(List<ByteBuffer> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (ByteBuffer bytes : list) {
			protobufBuilder.addRandomBytesList(ByteString.copyFrom(bytes));
			bytes.clear(); // important reset position after proto has read
		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list.size(), omnibus.randomBytesList().size());
		for (int i = 0; i < list.size(); i++) {
			assertEquals(list.get(i), omnibus.randomBytesList().get(i),
					"list.get(i)["+byteBufferToString(list.get(i))+"] is not equal to omnibus.randomBytesList().get(i)["+byteBufferToString(omnibus.randomBytesList().get(i))+"]");
		}
	}

	private static String byteBufferToString(ByteBuffer b) {
		byte[] bytes = new byte[b.capacity()];
		b.get(0,bytes);
		return "ByteBuffer{position="+b.position()+", capacity="+b.capacity()+", data="+ Arrays.toString(bytes)+"}";
	}

	@ParameterizedTest
	@MethodSource("fruitList")
	void parseFruitListOnly(List<Object> list) throws Exception {
		final var protobufBuilder = test.proto.Omnibus.newBuilder();
		for (final Object fruit : list) {
			if (fruit instanceof sample.target.model.Apple) {
				protobufBuilder.addFruitsList(
						Fruits.newBuilder().setApple(
								Apple.newBuilder().setVariety(((sample.target.model.Apple) fruit).variety())
										.build()));
			} else if (fruit instanceof sample.target.model.Banana) {
				protobufBuilder.addFruitsList(
						Fruits.newBuilder().setBanana(
								Banana.newBuilder().setVariety(((sample.target.model.Banana) fruit).variety())
										.build()));
			}

		}
		final var protobuf = protobufBuilder.build().toByteArray();

		final var omnibus = parser.parse(protobuf);
		assertEquals(list.size(), omnibus.fruitList().size());
		for (int i = 0; i < list.size(); i++) {
			assertEquals(list.get(i), omnibus.fruitList().get(i));
		}
	}
}
