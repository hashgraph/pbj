package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.JsonCodec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class JsonBench<P extends Record,G extends GeneratedMessage> {

	@SuppressWarnings("rawtypes")
	@State(Scope.Benchmark)
	public static class JsonBenchmarkState<P extends Record,G extends GeneratedMessage> {
		private JsonCodec<P> pbjJsonCodec;
		private Supplier<GeneratedMessage.Builder> builderSupplier;
		// input objects
		private P pbjModelObject;
		private G googleModelObject;

		// input bytes
		private BufferedData jsonDataBuffer;
		private String jsonString;

		// output buffers
		private BufferedData outDataBuffer;
		public void configure(P pbjModelObject, Codec<P> pbjProtoCodec, JsonCodec<P> pbjJsonCodec,
							  ProtobufObjectBench.ProtobufParseFunction<byte[],G> googleByteArrayParseMethod,
							  Supplier<GeneratedMessage.Builder> builderSupplier) {
			try {
				this.pbjModelObject = pbjModelObject;
				this.pbjJsonCodec = pbjJsonCodec;
				this.builderSupplier = builderSupplier;
				// write to JSON for parse tests
				jsonDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
				pbjJsonCodec.write(pbjModelObject, jsonDataBuffer);
				jsonDataBuffer.flip();
				// get as string for parse tests
				jsonString = jsonDataBuffer.asUtf8String();

				// write to temp data buffer and then read into byte array
				BufferedData tempDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
				pbjProtoCodec.write(pbjModelObject, tempDataBuffer);
				tempDataBuffer.flip();
				byte[] protoBytes = new byte[(int)tempDataBuffer.length()];
				tempDataBuffer.getBytes(0,protoBytes);
				// convert to protobuf
				googleModelObject = googleByteArrayParseMethod.parse(protoBytes);

				// input buffers
				// output buffers
				this.outDataBuffer = BufferedData.allocate(jsonString.length());
			} catch (IOException e) {
				e.getStackTrace();
				System.err.flush();
				throw new RuntimeException(e);
			}
		}
	}

	/** Same as parsePbjByteBuffer because DataBuffer.wrap(byte[]) uses ByteBuffer today, added this because makes result plotting easier */
	@Benchmark
	public void parsePbj(JsonBenchmarkState<P,G> benchmarkState, Blackhole blackhole) throws ParseException {
		benchmarkState.jsonDataBuffer.position(0);
		blackhole.consume(benchmarkState.pbjJsonCodec.parse(benchmarkState.jsonDataBuffer));
	}

	@Benchmark
	public void parseProtoC(JsonBenchmarkState<P,G> benchmarkState, Blackhole blackhole) throws IOException {
		var builder = benchmarkState.builderSupplier.get();
		JsonFormat.parser().merge(benchmarkState.jsonString, builder);
		blackhole.consume(builder.build());
	}

	/** Same as writePbjByteBuffer because DataBuffer.wrap(byte[]) uses ByteBuffer today, added this because makes result plotting easier */
	@Benchmark
	public void writePbj(JsonBenchmarkState<P,G> benchmarkState, Blackhole blackhole) throws IOException {
		benchmarkState.outDataBuffer.reset();
		benchmarkState.pbjJsonCodec.write(benchmarkState.pbjModelObject, benchmarkState.outDataBuffer);
		blackhole.consume(benchmarkState.outDataBuffer);
	}

	@Benchmark
	public void writeProtoC(JsonBenchmarkState<P,G> benchmarkState, Blackhole blackhole) throws InvalidProtocolBufferException {
		blackhole.consume(JsonFormat.printer().print(benchmarkState.googleModelObject));
	}

	/** Custom interface for method references as java.util.Function does not throw IOException */
	public interface ProtobufParseFunction<D, G> {
		G parse(D data) throws IOException;
	}

	@State(Scope.Benchmark)
	public static class EverythingBench extends JsonBench<Everything, com.hedera.pbj.test.proto.java.Everything> {
		@Setup
		public void setup(JsonBenchmarkState<Everything, com.hedera.pbj.test.proto.java.Everything> benchmarkState) {
			benchmarkState.configure(EverythingTestData.EVERYTHING,
					Everything.PROTOBUF,
					Everything.JSON,
					com.hedera.pbj.test.proto.java.Everything::parseFrom,
					com.hedera.pbj.test.proto.java.Everything::newBuilder);
		}
	}

	@State(Scope.Benchmark)
	public static class TimeStampBench extends JsonBench<Timestamp , com.hederahashgraph.api.proto.java.Timestamp> {
		@Setup
		public void setup(JsonBenchmarkState<Timestamp , com.hederahashgraph.api.proto.java.Timestamp> benchmarkState) {
			benchmarkState.configure(new Timestamp(5678L, 1234),
					Timestamp.PROTOBUF,
					Timestamp.JSON,
					com.hederahashgraph.api.proto.java.Timestamp::parseFrom,
					com.hederahashgraph.api.proto.java.Timestamp::newBuilder);
		}
	}

	@State(Scope.Benchmark)
	public static class AccountDetailsBench extends JsonBench<com.hedera.hapi.node.token.AccountDetails, GetAccountDetailsResponse.AccountDetails> {
		@Setup
		public void setup(JsonBenchmarkState<com.hedera.hapi.node.token.AccountDetails, GetAccountDetailsResponse.AccountDetails> benchmarkState) {
			benchmarkState.configure(AccountDetailsPbj.ACCOUNT_DETAILS,
					AccountDetails.PROTOBUF,
					AccountDetails.JSON,
					GetAccountDetailsResponse.AccountDetails::parseFrom,
					GetAccountDetailsResponse.AccountDetails::newBuilder);
		}
	}
}
