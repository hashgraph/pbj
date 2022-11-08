package sample.target.model;

import com.hedera.hashgraph.protoparse.EnumWithProtoOrdinal;
import com.hedera.hashgraph.protoparse.OneOf;
import sample.target.proto.schemas.OmnibusSchema;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public record Omnibus(
        int int32Number, long int64Number, int uint32Number, long uint64Number,
        boolean flag, Suit suitEnum,
        int sint32Number, long sint64Number,
        int sfixed32Number, long sfixed64Number, int fixed32Number, long fixed64Number,
        float floatNumber, double doubleNumber,
        String memo, ByteBuffer randomBytes, Nested nested,
        OneOf<Fruits.FruitKind, Object> fruit,
        OneOf<Everything, Object> everything,
        List<Integer> int32NumberList, List<Long> int64NumberList,
        List<Integer> uint32NumberList, List<Long> uint64NumberList,
        List<Boolean> flagList, List<Suit> suitEnumList,
        List<Integer> sint32NumberList, List<Long> sint64NumberList,
        List<Integer> sfixed32NumberList, List<Long> sfixed64NumberList,
        List<Integer> fixed32NumberList, List<Long> fixed64NumberList,
        List<Float> floatNumberList, List<Double> doubleNumberList,
        List<String> memoList, List<ByteBuffer> randomBytesList,
        List<Nested> nestedList, List<Object> fruitList) {
    public enum Everything implements EnumWithProtoOrdinal {
        INT32,
        INT64,
        UINT32,
        UINT64,
        FLAG,
        SUIT,
        SINT32,
        SINT64,
        SFIXED32,
        SFIXED64,
        FIXED32,
        FIXED64,
        FLOAT,
        DOUBLE,
        MEMO,
        RANDOM_BYTES,
        NESTED;

        @Override
        public int protoOrdinal() {
            return ordinal();
        }
    }

    public static final class Builder {
        private int int32Number;
        private long int64Number;
        private int uint32Number;
        private long uint64Number;
        private boolean flag;
        private Suit suitEnum = Suit.ACES;
        private int sint32Number;
        private long sint64Number;
        private int sfixed32Number;
        private long sfixed64Number;
        private int fixed32Number;
        private long fixed64Number;
        private float floatNumber;
        private double doubleNumber;
        private String memo = "";
        private ByteBuffer randomBytes = ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer();
        private Nested nested;
        private OneOf<Fruits.FruitKind, Object> fruit;
        private OneOf<Everything, Object> everything;
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
        private List<String> memoList = Collections.emptyList();
        private List<ByteBuffer> randomBytesList = Collections.emptyList();
        private List<Nested> nestedList = Collections.emptyList();
//        private List<Object> fruitList;

        public Builder int32Number(int value) {
            this.int32Number = value;
            return this;
        }

        public Builder int64Number(long value) {
            this.int64Number = value;
            return this;
        }

        public Builder uint32Number(int value) {
            this.uint32Number = value;
            return this;
        }

        public Builder uint64Number(long value) {
            this.uint64Number = value;
            return this;
        }

        public Builder flag(boolean value) {
            this.flag = value;
            return this;
        }

        public Builder suitEnum(Suit value) {
            this.suitEnum = value;
            return this;
        }

        public Builder sint32Number(int value) {
            this.sint32Number = value;
            return this;
        }

        public Builder sint64Number(long value) {
            this.sint64Number = value;
            return this;
        }

        public Builder sfixed32Number(int value) {
            this.sfixed32Number = value;
            return this;
        }

        public Builder sfixed64Number(long value) {
            this.sfixed64Number = value;
            return this;
        }

        public Builder fixed32Number(int value) {
            this.fixed32Number = value;
            return this;
        }

        public Builder fixed64Number(long value) {
            this.fixed64Number = value;
            return this;
        }

        public Builder floatNumber(float value) {
            this.floatNumber = value;
            return this;
        }

        public Builder doubleNumber(double value) {
            this.doubleNumber = value;
            return this;
        }

        public Builder memo(String value) {
            this.memo = value;
            return this;
        }

        public Builder randomBytes(ByteBuffer value) {
            this.randomBytes = value;
            return this;
        }

        public Builder nested(Nested value) {
            this.nested = value;
            return this;
        }

        public Builder apple(Apple apple) {
            fruit = new OneOf<>(OmnibusSchema.FRUIT_APPLE.number(), Fruits.FruitKind.APPLE, apple);
            return this;
        }

        public Builder banana(Banana banana) {
            fruit = new OneOf<>(OmnibusSchema.FRUIT_BANANA.number(), Fruits.FruitKind.BANANA, banana);
            return this;
        }

        public Builder int32Unique(int value) {
            everything = new OneOf<>(OmnibusSchema.INT32_UNIQUE.number(), Everything.INT32, value);
            return this;
        }

        public Builder int64Unique(long value) {
            everything = new OneOf<>(OmnibusSchema.INT64_UNIQUE.number(), Everything.INT64, value);
            return this;
        }

        public Builder uint32Unique(int value) {
            everything = new OneOf<>(OmnibusSchema.UINT32_UNIQUE.number(), Everything.UINT32, value);
            return this;
        }

        public Builder uint64Unique(long value) {
            everything = new OneOf<>(OmnibusSchema.UINT64_UNIQUE.number(), Everything.UINT64, value);
            return this;
        }

        public Builder flagUnique(boolean value) {
            everything = new OneOf<>(OmnibusSchema.FLAG_UNIQUE.number(), Everything.FLAG, value);
            return this;
        }

        public Builder suitEnumUnique(Suit value) {
            everything = new OneOf<>(OmnibusSchema.SUIT_UNIQUE.number(), Everything.SUIT, value);
            return this;
        }

        public Builder sint32Unique(int value) {
            everything = new OneOf<>(OmnibusSchema.SINT32_UNIQUE.number(), Everything.SINT32, value);
            return this;
        }

        public Builder sint64Unique(long value) {
            everything = new OneOf<>(OmnibusSchema.SINT64_UNIQUE.number(), Everything.SINT64, value);
            return this;
        }

        public Builder sfixed32Unique(int value) {
            everything = new OneOf<>(OmnibusSchema.SFIXED32_UNIQUE.number(), Everything.SFIXED32, value);
            return this;
        }

        public Builder sfixed64Unique(long value) {
            everything = new OneOf<>(OmnibusSchema.SFIXED64_UNIQUE.number(), Everything.SFIXED64, value);
            return this;
        }

        public Builder fixed32Unique(int value) {
            everything = new OneOf<>(OmnibusSchema.FIXED32_UNIQUE.number(), Everything.FIXED32, value);
            return this;
        }

        public Builder fixed64Unique(long value) {
            everything = new OneOf<>(OmnibusSchema.FIXED64_UNIQUE.number(), Everything.FIXED64, value);
            return this;
        }

        public Builder floatUnique(float value) {
            everything = new OneOf<>(OmnibusSchema.FLOAT_UNIQUE.number(), Everything.FLOAT, value);
            return this;
        }

        public Builder doubleUnique(double value) {
            everything = new OneOf<>(OmnibusSchema.DOUBLE_UNIQUE.number(), Everything.DOUBLE, value);
            return this;
        }

        public Builder memoUnique(String value) {
            everything = new OneOf<>(OmnibusSchema.MEMO_UNIQUE.number(), Everything.MEMO, value);
            return this;
        }

        public Builder randomBytesUnique(ByteBuffer value) {
            everything = new OneOf<>(OmnibusSchema.RANDOM_BYTES_UNIQUE.number(), Everything.RANDOM_BYTES, value);
            return this;
        }

        public Builder nestedUnique(Nested value) {
            everything = new OneOf<>(OmnibusSchema.NESTED_UNIQUE.number(), Everything.NESTED, value);
            return this;
        }

        public Builder int32NumberList(List<Integer> value) {
            this.int32NumberList = value;
            return this;
        }

        public Builder int64NumberList(List<Long> value) {
            this.int64NumberList = value;
            return this;
        }

        public Builder uint32NumberList(List<Integer> value) {
            this.uint32NumberList = value;
            return this;
        }

        public Builder uint64NumberList(List<Long> value) {
            this.uint64NumberList = value;
            return this;
        }

        public Builder flagList(List<Boolean> value) {
            this.flagList = value;
            return this;
        }

        public Builder suitEnumList(List<Suit> value) {
            this.suitEnumList = value;
            return this;
        }

        public Builder sint32NumberList(List<Integer> value) {
            this.sint32NumberList = value;
            return this;
        }

        public Builder sint64NumberList(List<Long> value) {
            this.sint64NumberList = value;
            return this;
        }

        public Builder sfixed32NumberList(List<Integer> value) {
            this.sfixed32NumberList = value;
            return this;
        }

        public Builder sfixed64NumberList(List<Long> value) {
            this.sfixed64NumberList = value;
            return this;
        }

        public Builder fixed32NumberList(List<Integer> value) {
            this.fixed32NumberList = value;
            return this;
        }

        public Builder fixed64NumberList(List<Long> value) {
            this.fixed64NumberList = value;
            return this;
        }

        public Builder floatNumberList(List<Float> value) {
            this.floatNumberList = value;
            return this;
        }

        public Builder doubleNumberList(List<Double> value) {
            this.doubleNumberList = value;
            return this;
        }

        public Builder memoList(List<String> value) {
            this.memoList = value;
            return this;
        }

        public Builder randomBytesList(List<ByteBuffer> value) {
            this.randomBytesList = value;
            return this;
        }

        public Builder nestedList(List<Nested> value) {
            this.nestedList = value;
            return this;
        }

        public Omnibus build() {
            return new Omnibus(int32Number, int64Number, uint32Number, uint64Number, flag, suitEnum, sint32Number,
                    sint64Number, sfixed32Number, sfixed64Number, fixed32Number, fixed64Number, floatNumber,
                    doubleNumber, memo, randomBytes, nested, fruit,
                    everything, int32NumberList, int64NumberList, uint32NumberList, uint64NumberList, flagList,
                    suitEnumList, sint32NumberList, sint64NumberList, sfixed32NumberList, sfixed64NumberList,
                    fixed32NumberList, fixed64NumberList, floatNumberList, doubleNumberList, memoList,
                    randomBytesList, nestedList, null);
        }
    }
}
