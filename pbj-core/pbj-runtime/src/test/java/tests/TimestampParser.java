package tests;

import com.hedera.hashgraph.pbj.runtime.FieldDefinition;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import com.hedera.hashgraph.pbj.runtime.ProtoParser;

import static tests.TimestampSchema.NANOS;
import static tests.TimestampSchema.SECONDS;

public class TimestampParser extends ProtoParser {

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
			case 1 -> SECONDS;
			case 2 -> NANOS;
			default -> null;
		};
	}

	@Override
	public void intField(final int fieldNum, final int value) {
		if (fieldNum == NANOS.number()) {
			this.nanos = value;
		}
	}

	@Override
	public void longField(final int fieldNum, final long value) {
		if (fieldNum == SECONDS.number()) {
			this.seconds = value;
		}
	}
}
