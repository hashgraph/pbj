package protoparse;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import tests.TimestampParser;
import tests.TimestampWriter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 1, time = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ParserBench {
	private final byte[] protobuf = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray();
	private final TimestampParser parser = new TimestampParser();
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();

	@Benchmark
	public void pbjParseTimestamp(Blackhole blackhole) throws MalformedProtobufException {
		blackhole.consume(parser.parse(protobuf));
	}

	@Benchmark
	public void protoCParseTimestamp(Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(Timestamp.parseFrom(protobuf));
	}

	@Benchmark
	public void pbjWriteTimestamp(Blackhole blackhole) throws IOException {
		bout.reset();
		TimestampWriter.write(new tests.Timestamp(5678L, 1234), bout);
		blackhole.consume(bout.toByteArray());
	}

	@Benchmark
	public void protoCWriteTimestamp(Blackhole blackhole) {
		blackhole.consume(Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray());
	}

}
