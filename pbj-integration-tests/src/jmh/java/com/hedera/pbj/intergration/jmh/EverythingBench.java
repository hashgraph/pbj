package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.hedera.pbj.test.proto.pbj.Everything;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static com.hedera.pbj.integration.EverythingTestData.EVERYTHING;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class EverythingBench {
	// input objects
	private final Everything everythingPbj;
	private final com.hedera.pbj.test.proto.java.Everything everythingProtoC;

	// input bytes
	private final byte[] protobuf;
	private final ByteBuffer protobufByteBuffer;
	private final DataBuffer protobufDataBuffer;
	private final ByteBuffer protobufByteBufferDirect;
	private final DataBuffer protobufDataBufferDirect;
	private final NonSynchronizedByteArrayInputStream bin;

	// output buffers
	private final NonSynchronizedByteArrayOutputStream bout;
	private final DataBuffer outDataBuffer;
	private final DataBuffer outDataBufferDirect;
	private final ByteBuffer bbout;
	private final ByteBuffer bboutDirect;

	public EverythingBench() {
		try {
			everythingPbj = EVERYTHING;
			// write to temp data buffer and then read into byte array
			DataBuffer tempDataBuffer = DataBuffer.allocate(5 * 1024 * 1024, false);
			Everything.PROTOBUF.write(everythingPbj, tempDataBuffer);
			tempDataBuffer.flip();
			protobuf = new byte[(int) tempDataBuffer.getRemaining()];
			System.out.println("protobuf.length = " + protobuf.length);
			tempDataBuffer.readBytes(protobuf);
			// start by parsing using protoc
			everythingProtoC = com.hedera.pbj.test.proto.java.Everything.parseFrom(protobuf);

			// input buffers
			protobufByteBuffer = ByteBuffer.wrap(protobuf);
			protobufDataBuffer = DataBuffer.wrap(protobuf);
			protobufByteBufferDirect = ByteBuffer.allocateDirect(protobuf.length);
			protobufByteBufferDirect.put(protobuf);
			System.out.println("protobufByteBufferDirect = " + protobufByteBufferDirect);
			protobufDataBufferDirect = DataBuffer.wrap(protobufByteBufferDirect);
			bin = new NonSynchronizedByteArrayInputStream(protobuf);
			DataInputStream din = new DataInputStream(bin);
			// output buffers
			bout = new NonSynchronizedByteArrayOutputStream();
			DataOutputStream dout = new DataOutputStream(bout);
			outDataBuffer = DataBuffer.allocate(protobuf.length, false);
			outDataBufferDirect = DataBuffer.allocate(protobuf.length, true);
			bbout = ByteBuffer.allocate(protobuf.length);
			bboutDirect = ByteBuffer.allocateDirect(protobuf.length);
		} catch (IOException e) {
			e.getStackTrace();
			System.err.flush();
			throw new RuntimeException(e);
		}
	}

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			protobufDataBuffer.resetPosition();
			blackhole.consume(Everything.PROTOBUF.parse(protobufDataBuffer));
		}
	}

	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			protobufDataBufferDirect.resetPosition();
			blackhole.consume(Everything.PROTOBUF.parse(protobufDataBufferDirect));
		}
	}
	@Benchmark
	public void parsePbjInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
//			blackhole.consume(EverythingProtoParser.parse(din));
			blackhole.consume(Everything.PROTOBUF.parse(new DataInputStream(bin)));
		}
	}

	@Benchmark
	public void parseProtoCByteArray(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(com.hedera.pbj.test.proto.java.Everything.parseFrom(protobuf));
		}
	}
	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			protobufByteBufferDirect.position(0);
			blackhole.consume(com.hedera.pbj.test.proto.java.Everything.parseFrom(protobufByteBufferDirect));
		}
	}
	@Benchmark
	public void parseProtoCByteBuffer(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(com.hedera.pbj.test.proto.java.Everything.parseFrom(protobufByteBuffer));
		}
	}
	@Benchmark
	public void parseProtoCInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
			blackhole.consume(com.hedera.pbj.test.proto.java.Everything.parseFrom(bin));
		}
	}

	@Benchmark
	public void writePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBuffer.reset();
			Everything.PROTOBUF.write(everythingPbj, outDataBuffer);
			blackhole.consume(outDataBuffer);
		}
	}
	@Benchmark
	public void writePbjByteDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBufferDirect.reset();
			Everything.PROTOBUF.write(everythingPbj, outDataBufferDirect);
			blackhole.consume(outDataBufferDirect);
		}
	}
	@Benchmark
	public void writePbjOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
//			EverythingWriter.write(everythingPbj, dout);
			Everything.PROTOBUF.write(everythingPbj, new DataOutputStream(bout));
			blackhole.consume(bout.toByteArray());
		}
	}

	@Benchmark
	public void writeProtoCByteArray(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(everythingProtoC.toByteArray());
		}
	}
	@Benchmark
	public void writeProtoCByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bbout);
			everythingProtoC.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bboutDirect);
			everythingProtoC.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			everythingProtoC.writeTo(bout);
			blackhole.consume(bout.toByteArray());
		}
	}

}