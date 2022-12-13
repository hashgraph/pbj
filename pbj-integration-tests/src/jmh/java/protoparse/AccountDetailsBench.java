package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.token.parser.AccountDetailsProtoParser;
import com.hedera.hapi.node.token.writer.AccountDetailsWriter;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
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
	private static final DataBuffer PROTOBUF_DATA_BUFFER;
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER;
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER_DIRECT;
	private static final DataBuffer OUT_DATA_BUFFER;

	static {
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			PROTOBUF_DATA_BUFFER = DataBuffer.allocate(1*1024*1024,false);
			AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ,PROTOBUF_DATA_BUFFER);
			PROTOBUF_DATA_BUFFER.flip();
			PROTOBUF_BYTES = new byte[(int)PROTOBUF_DATA_BUFFER.getRemaining()];
			PROTOBUF_DATA_BUFFER.readBytes(PROTOBUF_BYTES);

			PROTOBUF_BYTE_BUFFER = ByteBuffer.wrap(PROTOBUF_BYTES);
			ACCOUNT_DETAILS_PROTOC = GetAccountDetailsResponse.AccountDetails.parseFrom(PROTOBUF_BYTES);
			PROTOBUF_BYTE_BUFFER_DIRECT = ByteBuffer
					.allocateDirect(PROTOBUF_BYTES.length)
					.put(PROTOBUF_BYTES);
			OUT_DATA_BUFFER = DataBuffer.allocate(PROTOBUF_BYTES.length, false);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
	private final AccountDetailsProtoParser parser = new AccountDetailsProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			PROTOBUF_DATA_BUFFER.resetPosition();
			blackhole.consume(parser.parse(PROTOBUF_DATA_BUFFER));
		}
	}
//	@Benchmark
//	public void parsePbjByteBufferDirect(Blackhole blackhole) throws IOException {
//		for (int i = 0; i < 1000; i++) {
//			PROTOBUF_DATA_BUFFER.resetPosition();
//			blackhole.consume(parser.parse(PROTOBUF_DATA_BUFFER.clear()));
//		}
//	}

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
			OUT_DATA_BUFFER.reset();
			AccountDetailsWriter.write(ACCOUNT_DETAILS_PBJ, OUT_DATA_BUFFER);
			blackhole.consume(OUT_DATA_BUFFER);
		}
	}

	@Benchmark
	public void writeProtoC(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(ACCOUNT_DETAILS_PROTOC.toByteArray());
		}
	}
}
