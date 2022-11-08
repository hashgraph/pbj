package protoparse;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 5)
@Measurement(iterations = 5, time = 10)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ParserBench {
//	private final byte[] protobuf = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray();
//	private final TimestampParser parser = new TimestampParser();
//
//	@Benchmark
//	public void parseTimestamp(Blackhole blackhole) throws MalformedProtobufException {
//		blackhole.consume(parser.parse(protobuf));
//	}
//
//	@Benchmark
//	public void parseTimestamp2(Blackhole blackhole) throws InvalidProtocolBufferException {
//		blackhole.consume(Timestamp.parseFrom(protobuf));
//	}
}
