package sample.target.proto.parsers;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.MalformedProtobufException;
import com.hedera.hashgraph.protoparse.OneOf;
import com.hedera.hashgraph.protoparse.ProtoParser;
import sample.target.model.Fruits;
import sample.target.model.Nested;
import sample.target.model.Omnibus;
import sample.target.model.Suit;
import sample.target.proto.schemas.OmnibusSchema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;

public class OmnibusParser extends ProtoParser {
	private int int32Number;
	private long int64Number;
	private int uint32Number;
	private long uint64Number;
	private boolean flag;
	private Suit suitEnum;

	private int sint32Number;
	private long sint64Number;

	private int sfixed32Number;
	private long sfixed64Number;
	private int fixed32Number;
	private long fixed64Number;
	private float floatNumber;
	private double doubleNumber;

	private String memo;
	private ByteBuffer randomBytes;
	private Nested nested;

	private OneOf<Fruits.FruitKind, Object> fruit; // Apple or Banana

	private OneOf<Omnibus.Everything, Object> everything; // WHAT DO WE DO HERE??!!

	private List<Integer> int32NumberList = Collections.emptyList();
	private List<Long> int64NumberList = Collections.emptyList();
	private List<Integer> uint32NumberList = Collections.emptyList();
	private List<Long> uint64NumberList = Collections.emptyList();
	private List<Boolean> flagList = Collections.emptyList();
	private List<Suit> suitEnumList = Collections.emptyList();

	private List<Integer> sint32NumberList = Collections.emptyList();
	private List<Long> sint64NumberList = Collections.emptyList();

	private List<Integer> sfixed32NumberList = Collections.emptyList();
	private List<Long> sfixed64NumberList = Collections.emptyList();
	private List<Integer> fixed32NumberList = Collections.emptyList();
	private List<Long> fixed64NumberList = Collections.emptyList();
	private List<Float> floatNumberList = Collections.emptyList();
	private List<Double> doubleNumberList = Collections.emptyList();

	private List<String> memoList = null;
	private List<ByteBuffer> randomBytesList = null;
	private List<Nested> nestedList = null;

	private List<Object> fruitList = null; // Apple or Banana

	public Omnibus parse(byte[] protobuf) throws MalformedProtobufException {
		reset();
		super.start(protobuf);
		return createOmnibus();
	}

	public Omnibus parse(ByteBuffer protobuf) throws MalformedProtobufException {
		reset();
		super.start(protobuf);
		return createOmnibus();
	}

	public Omnibus parse(InputStream protobuf) throws IOException, MalformedProtobufException {
		reset();
		super.start(protobuf);
		return createOmnibus();
	}

	private Omnibus createOmnibus() {
		return new Omnibus(int32Number, int64Number, uint32Number, uint64Number, flag,
				suitEnum, sint32Number, sint64Number, sfixed32Number, sfixed64Number,
				fixed32Number, fixed64Number, floatNumber, doubleNumber, memo,
				randomBytes, nested, fruit, everything, int32NumberList,
				int64NumberList, uint32NumberList, uint64NumberList, flagList,
				suitEnumList, sint32NumberList, sint64NumberList, sfixed32NumberList,
				sfixed64NumberList, fixed32NumberList, fixed64NumberList, floatNumberList,
				doubleNumberList,
				memoList == null ? Collections.emptyList() : memoList,
				randomBytesList == null ? Collections.emptyList() : randomBytesList,
				nestedList == null ? Collections.emptyList() : nestedList,
				fruitList == null ? Collections.emptyList() : fruitList);
	}

	private void reset() {
		this.int32Number = 0;
		this.int64Number = 0;
		this.uint32Number = 0;
		this.uint64Number = 0;
		this.flag = false;
		this.suitEnum = Suit.ACES; // enums must have default of first enum (ordinal 0)

		this.sint32Number = 0;
		this.sint64Number = 0;

		this.sfixed32Number = 0;
		this.sfixed64Number = 0;
		this.fixed32Number = 0;
		this.fixed64Number = 0;
		this.floatNumber = 0;
		this.doubleNumber = 0;

		this.memo = "";
		this.randomBytes = ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer(); // arrays must have default of empty
		this.nested = null;

		this.fruit = null;

		this.everything = null;

		this.int32NumberList = new LinkedList<>();
		this.int64NumberList = new LinkedList<>();
		this.uint32NumberList = new LinkedList<>();
		this.uint64NumberList = new LinkedList<>();
		this.flagList = new LinkedList<>();
		this.suitEnumList = new LinkedList<>();

		this.sint32NumberList = new LinkedList<>();
		this.sint64NumberList = new LinkedList<>();

		this.sfixed32NumberList = new LinkedList<>();
		this.sfixed64NumberList = new LinkedList<>();
		this.fixed32NumberList = new LinkedList<>();
		this.fixed64NumberList = new LinkedList<>();
		this.floatNumberList = new LinkedList<>();
		this.doubleNumberList = new LinkedList<>();

		this.memoList = null;
		this.randomBytesList = null;
		this.nestedList = null;

		this.fruitList = null; // Apple or Banana
	}

	@Override
	protected FieldDefinition getFieldDefinition(final int fieldNumber) {
		return switch(fieldNumber) {
			case 10 -> OmnibusSchema.INT32_NUMBER;
			case 11 -> OmnibusSchema.INT64_NUMBER;
			case 12 -> OmnibusSchema.UINT32_NUMBER;
			case 13 -> OmnibusSchema.UINT64_NUMBER;
			case 14 -> OmnibusSchema.FLAG;
			case 15 -> OmnibusSchema.SUIT;
			case 30 -> OmnibusSchema.SINT32_NUMBER;
			case 31 -> OmnibusSchema.SINT64_NUMBER;
			case 20 -> OmnibusSchema.SFIXED32_NUMBER;
			case 25 -> OmnibusSchema.SFIXED64_NUMBER;
			case 21 -> OmnibusSchema.FIXED32_NUMBER;
			case 26 -> OmnibusSchema.FIXED64_NUMBER;
			case 22 -> OmnibusSchema.FLOAT_NUMBER;
			case 27 -> OmnibusSchema.DOUBLE_NUMBER;
			case 1 -> OmnibusSchema.MEMO;
			case 2 -> OmnibusSchema.RANDOM_BYTES;
			case 3 -> OmnibusSchema.NESTED;
			case 200 -> OmnibusSchema.FRUIT_APPLE;
			case 201 -> OmnibusSchema.FRUIT_BANANA;
			case 210 -> OmnibusSchema.INT32_UNIQUE;
			case 211 -> OmnibusSchema.INT64_UNIQUE;
			case 212 -> OmnibusSchema.UINT32_UNIQUE;
			case 213 -> OmnibusSchema.UINT64_UNIQUE;
			case 214 -> OmnibusSchema.FLAG_UNIQUE;
			case 215 -> OmnibusSchema.SUIT_UNIQUE;
			case 230 -> OmnibusSchema.SINT32_UNIQUE;
			case 231 -> OmnibusSchema.SINT64_UNIQUE;
			case 220 -> OmnibusSchema.SFIXED32_UNIQUE;
			case 225 -> OmnibusSchema.SFIXED64_UNIQUE;
			case 221 -> OmnibusSchema.FIXED32_UNIQUE;
			case 226 -> OmnibusSchema.FIXED64_UNIQUE;
			case 222 -> OmnibusSchema.FLOAT_UNIQUE;
			case 227 -> OmnibusSchema.DOUBLE_UNIQUE;
			case 251 -> OmnibusSchema.MEMO_UNIQUE;
			case 252 -> OmnibusSchema.RANDOM_BYTES_UNIQUE;
			case 253 -> OmnibusSchema.NESTED_UNIQUE;
			case 300 -> OmnibusSchema.INT32_REPEATED;
			case 301 -> OmnibusSchema.INT64_REPEATED;
			case 302 -> OmnibusSchema.UINT32_REPEATED;
			case 303 -> OmnibusSchema.UINT64_REPEATED;
			case 304 -> OmnibusSchema.FLAG_REPEATED;
			case 305 -> OmnibusSchema.SUIT_REPEATED;
			case 306 -> OmnibusSchema.SINT32_REPEATED;
			case 307 -> OmnibusSchema.SINT64_REPEATED;
			case 308 -> OmnibusSchema.SFIXED32_REPEATED;
			case 309 -> OmnibusSchema.SFIXED64_REPEATED;
			case 310 -> OmnibusSchema.FIXED32_REPEATED;
			case 311 -> OmnibusSchema.FIXED64_REPEATED;
			case 312 -> OmnibusSchema.FLOAT_REPEATED;
			case 313 -> OmnibusSchema.DOUBLE_REPEATED;
			case 314 -> OmnibusSchema.MEMO_REPEATED;
			case 315 -> OmnibusSchema.RANDOM_BYTES_REPEATED;
			case 316 -> OmnibusSchema.NESTED_REPEATED;
			case 317 -> OmnibusSchema.FRUITS_REPEATED;
			default -> throw new AssertionError("Unknown field type!! Test bug? Or intentional...?");
		};
	}

	@Override
	public void intField(final int fieldNum, final int value) {
		switch (fieldNum) {
			case 10 -> int32Number = value;
			case 12 -> uint32Number = value;
			case 30 -> sint32Number = value;
			case 20 -> sfixed32Number = value;
			case 21 -> fixed32Number = value;
			case 210 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.INT32, value);
			case 212 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.UINT32, value);
			case 230 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.SINT32, value);
			case 220 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.SFIXED32, value);
			case 221 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.FIXED32, value);
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void longField(final int fieldNum, final long value) {
		switch (fieldNum) {
			case 11 -> int64Number = value;
			case 13 -> uint64Number = value;
			case 31 -> sint64Number = value;
			case 25 -> sfixed64Number = value;
			case 26 -> fixed64Number = value;
			case 211 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.INT64, value);
			case 213 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.UINT64, value);
			case 231 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.SINT64, value);
			case 225 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.SFIXED64, value);
			case 226 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.FIXED64, value);
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void booleanField(final int fieldNum, final boolean value) {
		switch (fieldNum) {
			case 14 -> flag = value;
			case 214 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.FLAG, value);
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void floatField(final int fieldNum, final float value) {
		switch (fieldNum) {
			case 22 -> floatNumber = value;
			case 222 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.FLOAT, value);
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void doubleField(final int fieldNum, final double value) {
		switch (fieldNum) {
			case 27 -> doubleNumber = value;
			case 227 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.DOUBLE, value);
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void enumField(final int fieldNum, final int ordinal) {
		switch (fieldNum) {
			case 15 -> suitEnum = Suit.fromOrdinal(ordinal);
			case 215 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.SUIT, Suit.fromOrdinal(ordinal));
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void stringField(final int fieldNum, final String value) {
		switch (fieldNum) {
			case 1 -> memo = value;
			case 251 -> everything = new OneOf<>(fieldNum, Omnibus.Everything.MEMO, value);
			case 314 -> {
				if (memoList == null) {
					memoList = new ArrayList<>();
				}
				memoList.add(value);
			}
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void bytesField(int fieldNum, ByteBuffer value) {
		switch (fieldNum) {
			case 2 -> randomBytes = value;
			case 252 ->
					everything = new OneOf<>(fieldNum, Omnibus.Everything.RANDOM_BYTES, value);
			case 315 -> {
				if (randomBytesList == null) {
					randomBytesList = new ArrayList<>();
				}
				randomBytesList.add(value);
			}
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void objectField(int fieldNum, InputStream protoStream) throws IOException, MalformedProtobufException {
		switch (fieldNum) {
			case 3 -> nested = new NestedParser().parse(protoStream);
			case 200 -> fruit = new OneOf<>(fieldNum, Fruits.FruitKind.APPLE, new AppleParser().parse(protoStream));
			case 201 -> fruit = new OneOf<>(fieldNum, Fruits.FruitKind.BANANA, new BananaParser().parse(protoStream));
			case 253 ->
					everything = new OneOf<>(fieldNum, Omnibus.Everything.NESTED, new NestedParser().parse(protoStream));
			case 316 -> {
				if (nestedList == null) {
					nestedList = new ArrayList<>();
				}
				// TODO ProtoStream needs to know what the length is so it can stop parsing when it gets to the end...
				nestedList.add(new NestedParser().parse(protoStream));
			}
			case 317 -> {
				if (fruitList == null) {
					fruitList = new ArrayList<>();
				}
				fruitList.add(new FruitsParser().parse(protoStream));
			}
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void intList(int fieldNum, List<Integer> value) {
		switch (fieldNum) {
			case 300 -> int32NumberList = value;
			case 302 -> uint32NumberList = value;
			case 306 -> sint32NumberList = value;
			case 308 -> sfixed32NumberList = value;
			case 310 -> fixed32NumberList = value;
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void longList(int fieldNum, List<Long> value) {
		switch (fieldNum) {
			case 301 -> int64NumberList = value;
			case 303 -> uint64NumberList = value;
			case 307 -> sint64NumberList = value;
			case 309 -> sfixed64NumberList = value;
			case 311 -> fixed64NumberList = value;
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void booleanList(int fieldNum, List<Boolean> value) {
		switch (fieldNum) {
			case 304 -> flagList = value;
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

	@Override
	public void enumList(final int fieldNum, final List<Integer> ordinals) {
		switch (fieldNum) {
			case 305 -> suitEnumList = ordinals.stream().map(Suit::fromOrdinal).toList();
			default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
		}
	}

}
