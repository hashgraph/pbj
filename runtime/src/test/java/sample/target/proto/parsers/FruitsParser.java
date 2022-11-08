package sample.target.proto.parsers;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.MalformedProtobufException;
import com.hedera.hashgraph.protoparse.ProtoParser;
import sample.target.model.Apple;
import sample.target.model.Banana;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static sample.target.proto.schemas.FruitsSchema.APPLE;
import static sample.target.proto.schemas.FruitsSchema.BANANA;

public class FruitsParser extends ProtoParser {
	private Apple apple;
	private Banana banana;

	public Object parse(byte[] protobuf) throws MalformedProtobufException {
		apple = null;
		banana = null;
		super.start(protobuf);
		return apple == null ? banana : apple;
	}

	public Object parse(ByteBuffer protobuf) throws MalformedProtobufException {
		apple = null;
		banana = null;
		super.start(protobuf);
		return apple == null ? banana : apple;
	}

	public Object parse(InputStream protobuf) throws IOException, MalformedProtobufException {
		apple = null;
		banana = null;
		super.start(protobuf);
		return apple == null ? banana : apple;
	}

	@Override
	protected FieldDefinition getFieldDefinition(final int fieldNumber) {
		return switch (fieldNumber) {
			case 1 -> APPLE;
			case 2 -> BANANA;
			default -> null;
		};
	}

	@Override
	public void objectField(final int fieldNum, final InputStream protoStream) throws IOException, MalformedProtobufException {
		switch (fieldNum) {
			case 1 -> apple = new AppleParser().parse(protoStream);
			case 2 -> banana = new BananaParser().parse(protoStream);
			default -> throw new AssertionError("Unknown field number " + fieldNum);
		}
	}
}
