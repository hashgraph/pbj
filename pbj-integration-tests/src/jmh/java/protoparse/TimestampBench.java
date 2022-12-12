package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.hapi.node.base.parser.TimestampProtoParser;
import com.hedera.hapi.node.base.writer.TimestampWriter;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

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
public class TimestampBench {
	static {
		System.out.println("TimestampBench.static initializer 2");
	}
	private final byte[] protobuf = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray();
	private final ByteBuffer protobufByteBuffer = ByteBuffer.wrap(protobuf);
	private final ByteBuffer protobufByteBufferDirect = ByteBuffer
			.allocateDirect(protobuf.length)
			.put(protobuf);
	private final TimestampProtoParser parser = new TimestampProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(protobufByteBuffer.clear()));
	}
	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(protobufByteBufferDirect.clear()));
	}

	@Benchmark
	public void parseProtoCByteBuffer(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(Timestamp.parseFrom(protobufByteBuffer));
	}
	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(Timestamp.parseFrom(protobufByteBufferDirect));
	}

	@Benchmark
	public void writePbj(Blackhole blackhole) throws IOException {
		bout.reset();
		TimestampWriter.write(
				new com.hedera.hapi.node.base.Timestamp(5678L, 1234), bout);
		blackhole.consume(bout.toByteArray());
	}

	@Benchmark
	public void writeProtoC(Blackhole blackhole) {
		blackhole.consume(Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray());
	}

}
