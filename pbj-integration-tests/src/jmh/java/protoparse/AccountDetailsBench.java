package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.token.parser.AccountDetailsProtoParser;
import com.hedera.hapi.node.token.writer.AccountDetailsWriter;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
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
public class AccountDetailsBench {
	private static final com.hedera.hapi.node.token.AccountDetails ACCOUNT_DETAILS_PBJ = AccountDetailsPbj.ACCOUNT_DETAILS;
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
	public void parsePbj(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(PROTOBUF_BYTES));
	}

	@Benchmark
	public void parseProtoC(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTES));
	}

	@Benchmark
	public void writePbj(Blackhole blackhole) throws IOException {
		bout.reset();
		AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ, bout);
		blackhole.consume(bout.toByteArray());
	}

	@Benchmark
	public void writeProtoC(Blackhole blackhole) {
		blackhole.consume(ACCOUNT_DETAILS_PROTOC.toByteArray());
	}
}
