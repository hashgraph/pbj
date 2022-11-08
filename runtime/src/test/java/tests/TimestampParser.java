package tests;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.FieldType;
import com.hedera.hashgraph.protoparse.MalformedProtobufException;
import com.hedera.hashgraph.protoparse.ProtoParser;

public class TimestampParser extends ProtoParser {
	private static final FieldDefinition TIMESTAMP_SECONDS_FIELD = new FieldDefinition("seconds", FieldType.INT_64, false, 1);
	private static final FieldDefinition TIMESTAMP_NANOS_FIELD = new FieldDefinition("nanos", FieldType.INT_32, false, 2);

	private long seconds;
	private int nanos;

	public Timestamp parse(byte[] protobuf) throws MalformedProtobufException {
		seconds = 0;
		nanos = 0;
		super.start(protobuf);
		return new Timestamp(seconds, nanos);
	}

	@Override
	protected FieldDefinition getFieldDefinition(final int field) {
		return switch (field) {
			case 1 -> TIMESTAMP_SECONDS_FIELD;
			case 2 -> TIMESTAMP_NANOS_FIELD;
			default -> null;
		};
	}

	@Override
	public void intField(final int fieldNum, final int value) {
		if (fieldNum == TIMESTAMP_NANOS_FIELD.number()) {
			this.nanos = value;
		}
	}

	@Override
	public void longField(final int fieldNum, final long value) {
		if (fieldNum == TIMESTAMP_SECONDS_FIELD.number()) {
			this.seconds = value;
		}
	}
}
