package com.hedera.hashgraph.pbj.intergration.jmh;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.hedera.hapi.node.base.parser.TimestampProtoParser;
import com.hedera.hapi.node.base.writer.TimestampWriter;
import com.hedera.hashgraph.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.hashgraph.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
import com.hedera.hashgraph.pbj.runtime.io.DataInputStream;
import com.hedera.hashgraph.pbj.runtime.io.DataOutputStream;
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
	// input objects
	private final Timestamp timestamp = Timestamp.newBuilder().setNanos(1234).setSeconds(5678L).build();
	private final com.hedera.hapi.node.base.Timestamp timestampPbj = new com.hedera.hapi.node.base.Timestamp(5678L, 1234);
	// input bytes
	private final byte[] protobuf = timestamp.toByteArray();
	private final ByteBuffer protobufByteBuffer = ByteBuffer.wrap(protobuf);
	private final DataBuffer protobufDataBuffer = DataBuffer.wrap(protobuf);
	private final ByteBuffer protobufByteBufferDirect = ByteBuffer
			.allocateDirect(protobuf.length)
			.put(protobuf);
	private final DataBuffer protobufDataBufferDirect = DataBuffer.wrap(protobufByteBufferDirect);

	private NonSynchronizedByteArrayInputStream bin = new NonSynchronizedByteArrayInputStream(protobuf);
	private DataInputStream din = new DataInputStream(bin);

	// output buffers
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();
	private final DataOutputStream dout = new DataOutputStream(bout);
	private final DataBuffer outDataBuffer = DataBuffer.allocate(protobuf.length, false);
	private final DataBuffer outDataBufferDirect = DataBuffer.allocate(protobuf.length, true);
	private final ByteBuffer bbout = ByteBuffer.allocate(protobuf.length);
	private final ByteBuffer bboutDirect = ByteBuffer.allocateDirect(protobuf.length);

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			protobufDataBuffer.resetPosition();
			blackhole.consume(TimestampProtoParser.parse(protobufDataBuffer));
		}
	}

	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			protobufDataBufferDirect.resetPosition();
			blackhole.consume(TimestampProtoParser.parse(protobufDataBufferDirect));
		}
	}
	@Benchmark
	public void parsePbjInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
//			blackhole.consume(TimestampProtoParser.parse(din));
			blackhole.consume(TimestampProtoParser.parse(new DataInputStream(bin)));
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
			protobufByteBufferDirect.position(0);
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
	public void parseProtoCInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
			blackhole.consume(Timestamp.parseFrom(bin));
		}
	}

	@Benchmark
	public void writePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBuffer.reset();
			TimestampWriter.write(timestampPbj, outDataBuffer);
			blackhole.consume(outDataBuffer);
		}
	}
	@Benchmark
	public void writePbjByteDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBufferDirect.reset();
			TimestampWriter.write(timestampPbj, outDataBufferDirect);
			blackhole.consume(outDataBufferDirect);
		}
	}
	@Benchmark
	public void writePbjOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			TimestampWriter.write(timestampPbj, dout);
			blackhole.consume(bout.toByteArray());
		}
	}

	@Benchmark
	public void writeProtoCByteArray(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(timestamp.toByteArray());
		}
	}
	@Benchmark
	public void writeProtoCByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bbout);
			timestamp.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bboutDirect);
			timestamp.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			timestamp.writeTo(bout);
			blackhole.consume(bout.toByteArray());
		}
	}
}
