package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class AccountDetailsBench {
	// input objects
	private final com.hedera.hapi.node.token.AccountDetails accountDetailsPbj;
	private final GetAccountDetailsResponse.AccountDetails accountDetailsProtoC;

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

	public AccountDetailsBench() {
		try {
			accountDetailsPbj = AccountDetailsPbj.ACCOUNT_DETAILS;
			// write to temp data buffer and then read into byte array
			DataBuffer tempDataBuffer = DataBuffer.allocate(5 * 1024 * 1024, false);
			AccountDetails.PROTOBUF.write(accountDetailsPbj, tempDataBuffer);
			tempDataBuffer.flip();
			protobuf = new byte[(int) tempDataBuffer.getRemaining()];
			System.out.println("protobuf.length = " + protobuf.length);
			tempDataBuffer.readBytes(protobuf);
			// start by parsing using protoc
			accountDetailsProtoC = GetAccountDetailsResponse.AccountDetails.parseFrom(protobuf);

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
			blackhole.consume(AccountDetails.PROTOBUF.parse(protobufDataBuffer));
		}
	}

	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			protobufDataBufferDirect.resetPosition();
			blackhole.consume(AccountDetails.PROTOBUF.parse(protobufDataBufferDirect));
		}
	}
	@Benchmark
	public void parsePbjInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
//			blackhole.consume(AccountDetailsProtoParser.parse(din));
			blackhole.consume(AccountDetails.PROTOBUF.parse(new DataInputStream(bin)));
		}
	}

	@Benchmark
	public void parseProtoCByteArray(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(protobuf));
		}
	}
	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			protobufByteBufferDirect.position(0);
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(protobufByteBufferDirect));
		}
	}
	@Benchmark
	public void parseProtoCByteBuffer(Blackhole blackhole) throws InvalidProtocolBufferException {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(protobufByteBuffer));
		}
	}
	@Benchmark
	public void parseProtoCInputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bin.resetPosition();
			blackhole.consume(GetAccountDetailsResponse.AccountDetails.parseFrom(bin));
		}
	}

	@Benchmark
	public void writePbjByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBuffer.reset();
			AccountDetails.PROTOBUF.write(accountDetailsPbj, outDataBuffer);
			blackhole.consume(outDataBuffer);
		}
	}
	@Benchmark
	public void writePbjByteDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			outDataBufferDirect.reset();
			AccountDetails.PROTOBUF.write(accountDetailsPbj, outDataBufferDirect);
			blackhole.consume(outDataBufferDirect);
		}
	}
	@Benchmark
	public void writePbjOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
//			AccountDetailsWriter.write(accountDetailsPbj, dout);
			AccountDetails.PROTOBUF.write(accountDetailsPbj, new DataOutputStream(bout));
			blackhole.consume(bout.toByteArray());
		}
	}

	@Benchmark
	public void writeProtoCByteArray(Blackhole blackhole) {
		for (int i = 0; i < 1000; i++) {
			blackhole.consume(accountDetailsProtoC.toByteArray());
		}
	}
	@Benchmark
	public void writeProtoCByteBuffer(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bbout);
			accountDetailsProtoC.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCByteBufferDirect(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			CodedOutputStream cout = CodedOutputStream.newInstance(bboutDirect);
			accountDetailsProtoC.writeTo(cout);
			blackhole.consume(bbout);
		}
	}

	@Benchmark
	public void writeProtoCOutputStream(Blackhole blackhole) throws IOException {
		for (int i = 0; i < 1000; i++) {
			bout.reset();
			accountDetailsProtoC.writeTo(bout);
			blackhole.consume(bout.toByteArray());
		}
	}
}
