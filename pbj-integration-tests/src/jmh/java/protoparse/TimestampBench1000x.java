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
public class TimestampBench1000x {
	private final byte[] protobuf = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray();
	private final ByteBuffer protobufByteBuffer = ByteBuffer.wrap(protobuf);
	private final ByteBuffer protobufByteBufferDirect = ByteBuffer
			.allocateDirect(protobuf.length)
			.put(protobuf);
	private final TimestampProtoParser parser = new TimestampProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws MalformedProtobufException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(parser.parse(protobufByteBuffer.clear()));
		}
	}
	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws MalformedProtobufException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(parser.parse(protobufByteBufferDirect.clear()));
		}
	}

	@Benchmark
	public void parseProtoCByteArray(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(Timestamp.parseFrom(protobuf));
		}
	}
	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(Timestamp.parseFrom(protobufByteBufferDirect));
		}
	}
	@Benchmark
	public void parseProtoCByteBuffer(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(Timestamp.parseFrom(protobufByteBuffer));
		}
	}

	@Benchmark
	public void writePbj(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			TimestampWriter.write(
					new com.hedera.hapi.node.base.Timestamp(5678L, 1234), bout);
			blackhole.consume(bout.toByteArray());
		}
	}

	@Benchmark
	public void writeProtoCByteArray(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray());
		}
	}

	@Benchmark
	public void writeProtoCOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().writeTo(bout);
			blackhole.consume(bout.toByteArray());
		}
	}
}
