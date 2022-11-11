package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import com.hedera.hashgraph.pbj.test.integration.parser.AccountDetailsProtoParser;
import com.hedera.hashgraph.pbj.test.integration.parser.TimestampProtoParser;
import com.hedera.hashgraph.pbj.test.integration.writer.AccountDetailsWriter;
import com.hedera.hashgraph.pbj.test.integration.writer.TimestampWriter;
import com.hederahashgraph.api.proto.java.GetAccountDetails;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 1, time = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class AccountBench {
	private static final com.hedera.hashgraph.pbj.test.integration.model.AccountDetails ACCOUNT_DETAILS_PBJ = AccountDetailsPbj.ACCOUNT_DETAILS;
	private static final GetAccountDetailsResponse.AccountDetails ACCOUNT_DETAILS_PROTOC;
	private static final byte[] PROTOBUF_BYTES;

	static {
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ,bout);
			bout.flush();
			PROTOBUF_BYTES = bout.toByteArray();
			ACCOUNT_DETAILS_PROTOC = GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTES);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
	private final AccountDetailsProtoParser parser = new AccountDetailsProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void pbjParseAccountDetails(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(PROTOBUF_BYTES));
	}

	@Benchmark
	public void protoCParseAccountDetails(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTES));
	}

	@Benchmark
	public void pbjWriteTimestamp(Blackhole blackhole) throws IOException {
		bout.reset();
		AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ, bout);
		blackhole.consume(bout.toByteArray());
	}

	@Benchmark
	public void protoCWriteTimestamp(Blackhole blackhole) {
		blackhole.consume(ACCOUNT_DETAILS_PROTOC.toByteArray());
	}
}
