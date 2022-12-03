package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.hapi.node.base.parser.TimestampProtoParser;
import com.hedera.hapi.node.base.writer.TimestampWriter;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 1, time = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class TimestampBench {
	private final byte[] protobuf = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray();
	private final TimestampProtoParser parser = new TimestampProtoParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void parsePbj(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(protobuf));
	}

	@Benchmark
	public void parseProtoC(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(Timestamp.parseFrom(protobuf));
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
