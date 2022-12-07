package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.token.parser.AccountDetailsProtoParser;
import com.hedera.hapi.node.token.writer.AccountDetailsWriter;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import com.hedera.hashgraph.pbj.runtime.test.NonSynchronizedByteArrayOutputStream;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER;
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER_DIRECT;

	static {
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ,bout);
			bout.flush();
			PROTOBUF_BYTES = bout.toByteArray();
			PROTOBUF_BYTE_BUFFER = ByteBuffer.wrap(PROTOBUF_BYTES);
			ACCOUNT_DETAILS_PROTOC = GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTES);
			PROTOBUF_BYTE_BUFFER_DIRECT = ByteBuffer
					.allocateDirect(PROTOBUF_BYTES.length)
					.put(PROTOBUF_BYTES);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
	private final AccountDetailsProtoParser parser = new AccountDetailsProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws MalformedProtobufException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(parser.parse(PROTOBUF_BYTE_BUFFER.clear()));
		}
	}
	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws MalformedProtobufException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(parser.parse(PROTOBUF_BYTE_BUFFER_DIRECT.clear()));
		}
	}

	@Benchmark
	public void parseProtoC(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTE_BUFFER));
		}
	}
	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTE_BUFFER_DIRECT));
		}
	}

	@Benchmark
	public void writePbj(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ, bout);
			blackhole.consume(bout.getByteBuffer());
		}
	}

	@Benchmark
	public void writeProtoC(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(ACCOUNT_DETAILS_PROTOC.toByteArray());
		}
	}
}
