package sample.target.proto.writers;

import com.hedera.hashgraph.protoparse.ProtoOutputStream;
import com.hedera.hashgraph.protoparse.ProtoWriter;
import sample.target.model.Omnibus;
import sample.target.model.Suit;
import sample.target.proto.schemas.OmnibusSchema;

import java.io.IOException;
import java.io.OutputStream;

public class OmnibusWriter implements ProtoWriter<Omnibus> {
    public void write(Omnibus omnibus, OutputStream out) throws IOException {
        final var pb = new ProtoOutputStream(OmnibusSchema::valid, out);
        pb.writeInteger(OmnibusSchema.INT32_NUMBER, omnibus.int32Number());
        pb.writeInteger(OmnibusSchema.SINT32_NUMBER, omnibus.sint32Number());
        pb.writeInteger(OmnibusSchema.UINT32_NUMBER, omnibus.uint32Number());
        pb.writeInteger(OmnibusSchema.FIXED32_NUMBER, omnibus.fixed32Number());
        pb.writeInteger(OmnibusSchema.SFIXED32_NUMBER, omnibus.sfixed32Number());
        pb.writeLong(OmnibusSchema.INT64_NUMBER, omnibus.int64Number());
        pb.writeLong(OmnibusSchema.SINT64_NUMBER, omnibus.sint64Number());
        pb.writeLong(OmnibusSchema.UINT64_NUMBER, omnibus.uint64Number());
        pb.writeLong(OmnibusSchema.FIXED64_NUMBER, omnibus.fixed64Number());
        pb.writeLong(OmnibusSchema.SFIXED64_NUMBER, omnibus.sfixed64Number());
        pb.writeBoolean(OmnibusSchema.FLAG, omnibus.flag());
        pb.writeEnum(OmnibusSchema.SUIT, omnibus.suitEnum());
        pb.writeFloat(OmnibusSchema.FLOAT_NUMBER, omnibus.floatNumber());
        pb.writeDouble(OmnibusSchema.DOUBLE_NUMBER, omnibus.doubleNumber());
        pb.writeBytes(OmnibusSchema.RANDOM_BYTES, omnibus.randomBytes());
        pb.writeMessage(OmnibusSchema.NESTED, omnibus.nested(), NestedWriter::write);
        pb.writeIntegerList(OmnibusSchema.INT32_REPEATED, omnibus.int32NumberList());
        pb.writeIntegerList(OmnibusSchema.UINT32_REPEATED, omnibus.uint32NumberList());
        pb.writeIntegerList(OmnibusSchema.SINT32_REPEATED, omnibus.sint32NumberList());
        pb.writeIntegerList(OmnibusSchema.SFIXED32_REPEATED, omnibus.sfixed32NumberList());
        pb.writeIntegerList(OmnibusSchema.FIXED32_REPEATED, omnibus.fixed32NumberList());
        pb.writeLongList(OmnibusSchema.INT64_REPEATED, omnibus.int64NumberList());
        pb.writeLongList(OmnibusSchema.UINT64_REPEATED, omnibus.uint64NumberList());
        pb.writeLongList(OmnibusSchema.SINT64_REPEATED, omnibus.sint64NumberList());
        pb.writeLongList(OmnibusSchema.SFIXED64_REPEATED, omnibus.sfixed64NumberList());
        pb.writeLongList(OmnibusSchema.FIXED64_REPEATED, omnibus.fixed64NumberList());
        // TODO add test for double number list and float number list
        pb.writeBooleanList(OmnibusSchema.FLAG_REPEATED, omnibus.flagList());
        pb.writeEnumList(OmnibusSchema.SUIT_REPEATED, omnibus.suitEnumList());
        pb.writeStringList(OmnibusSchema.MEMO_REPEATED, omnibus.memoList());
        pb.writeMessageList(OmnibusSchema.NESTED_REPEATED, omnibus.nestedList(), NestedWriter::write);
        pb.writeBytesList(OmnibusSchema.RANDOM_BYTES_REPEATED, omnibus.randomBytesList());

        final var oneOfFruit = omnibus.fruit();
        if (oneOfFruit != null) {
            switch (oneOfFruit.kind()) {
                case APPLE -> pb.writeMessage(OmnibusSchema.FRUIT_APPLE, oneOfFruit.as(), AppleWriter::write);
                case BANANA -> pb.writeMessage(OmnibusSchema.FRUIT_BANANA, oneOfFruit.as(), BananaWriter::write);
            }
        }

        final var oneOfEverything = omnibus.everything();
        if (oneOfEverything != null) {
            switch (oneOfEverything.kind()) {
                case INT32 -> pb.writeInteger(OmnibusSchema.INT32_UNIQUE, oneOfEverything.as());
                case SINT32 -> pb.writeInteger(OmnibusSchema.SINT32_UNIQUE, oneOfEverything.as());
                case UINT32 -> pb.writeInteger(OmnibusSchema.UINT32_UNIQUE, oneOfEverything.as());
                case FIXED32 -> pb.writeInteger(OmnibusSchema.FIXED32_UNIQUE, oneOfEverything.as());
                case SFIXED32 -> pb.writeInteger(OmnibusSchema.SFIXED32_UNIQUE, oneOfEverything.as());
                case INT64 -> pb.writeLong(OmnibusSchema.INT64_UNIQUE, oneOfEverything.as());
                case SINT64 -> pb.writeLong(OmnibusSchema.SINT64_UNIQUE, oneOfEverything.as());
                case UINT64 -> pb.writeLong(OmnibusSchema.UINT64_UNIQUE, oneOfEverything.as());
                case FIXED64 -> pb.writeLong(OmnibusSchema.FIXED64_UNIQUE, oneOfEverything.as());
                case SFIXED64 -> pb.writeLong(OmnibusSchema.SFIXED64_UNIQUE, oneOfEverything.as());
                case FLAG -> pb.writeBoolean(OmnibusSchema.FLAG_UNIQUE, oneOfEverything.as());
                case SUIT -> pb.writeEnum(OmnibusSchema.SUIT_UNIQUE, ((Suit)oneOfEverything.as()));
                case FLOAT -> pb.writeFloat(OmnibusSchema.FLOAT_UNIQUE, oneOfEverything.as());
                case DOUBLE -> pb.writeDouble(OmnibusSchema.DOUBLE_UNIQUE, oneOfEverything.as());
                case RANDOM_BYTES -> pb.writeBytes(OmnibusSchema.RANDOM_BYTES_UNIQUE, oneOfEverything.as());
                case MEMO -> pb.writeString(OmnibusSchema.MEMO_UNIQUE, oneOfEverything.as());
                case NESTED -> pb.writeMessage(OmnibusSchema.NESTED_UNIQUE, oneOfEverything.as(), NestedWriter::write);
            }
        }
    }
}
