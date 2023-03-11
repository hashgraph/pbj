package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.io.buffer.WritableBufferedData;
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
	private final WritableBufferedData protobufDataBuffer = WritableBufferedData.wrap(protobuf);
	private final ByteBuffer protobufByteBufferDirect = ByteBuffer
			.allocateDirect(protobuf.length)
			.put(protobuf);
	private final DataBuffer protobufDataBufferDirect = DataBuffer.wrap(protobufByteBufferDirect);
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();
	private final WritableBufferedData outDataBuffer = WritableBufferedData.allocate(protobuf.length, false);

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws IOException {
		protobufDataBuffer.resetPosition();
		blackhole.consume(com.hedera.hapi.node.base.Timestamp.PROTOBUF.parse(protobufDataBuffer));
	}

	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws IOException {
		protobufDataBufferDirect.resetPosition();
		blackhole.consume(com.hedera.hapi.node.base.Timestamp.PROTOBUF.parse(protobufDataBufferDirect));
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
		outDataBuffer.reset();
		com.hedera.hapi.node.base.Timestamp.PROTOBUF.write(
				new com.hedera.hapi.node.base.Timestamp(5678L, 1234), outDataBuffer);
		blackhole.consume(outDataBuffer);
	}

	@Benchmark
	public void writeProtoC(Blackhole blackhole) {
		blackhole.consume(Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build().toByteArray());
	}

}
