// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.google.protobuf.GeneratedMessage;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.SlimBuffer;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class ProtobufObjectBench<P, G extends GeneratedMessage> {
    /** we repeat all operations 1000 times so that measured times are nig enough */
    private static final int OPERATION_COUNT = 1000;

    @State(Scope.Benchmark)
    public static class BenchmarkState<P, G extends GeneratedMessage> {
        private Codec<P> pbjCodec;
        private ProtobufParseFunction<byte[], G> googleByteArrayParseMethod;
        private ProtobufParseFunction<ByteBuffer, G> googleByteBufferParseMethod;
        private ProtobufParseFunction<InputStream, G> googleInputStreamParseMethod;
        // input objects
        private P pbjModelObject;
        private G googleModelObject;

        // input bytes
        private byte[] protobuf;
        private ByteBuffer protobufByteBuffer;
        private BufferedData protobufDataBuffer;
        private ByteBuffer protobufByteBufferDirect;
        private BufferedData protobufDataBufferDirect;
        private NonSynchronizedByteArrayInputStream bin;

        // output buffers
        private NonSynchronizedByteArrayOutputStream bout;
        private BufferedData outDataBuffer;
        private BufferedData outDataBufferDirect;
        private ByteBuffer bbout;
        private ByteBuffer bboutDirect;
        private byte[] outArray;

        private SlimBuffer slimProtobufDataBuffer, slimBin;

        public void configure(
                P pbjModelObject,
                Codec<P> pbjCodec,
                ProtobufParseFunction<byte[], G> googleByteArrayParseMethod,
                ProtobufParseFunction<ByteBuffer, G> googleByteBufferParseMethod,
                ProtobufParseFunction<InputStream, G> googleInputStreamParseMethod) {
            try {
                this.pbjModelObject = pbjModelObject;
                this.pbjCodec = pbjCodec;
                this.googleByteArrayParseMethod = googleByteArrayParseMethod;
                this.googleByteBufferParseMethod = googleByteBufferParseMethod;
                this.googleInputStreamParseMethod = googleInputStreamParseMethod;
                // write to temp data buffer and then read into byte array
                BufferedData tempDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
                pbjCodec.write(pbjModelObject, tempDataBuffer);
                tempDataBuffer.flip();
                this.protobuf = new byte[(int) tempDataBuffer.remaining()];
                tempDataBuffer.readBytes(this.protobuf);
                // start by parsing using protoc
                this.googleModelObject = googleByteArrayParseMethod.parse(this.protobuf);

                // input buffers
                this.protobufByteBuffer = ByteBuffer.wrap(this.protobuf);
                this.protobufDataBuffer = BufferedData.wrap(this.protobuf);
                this.slimProtobufDataBuffer = new SlimBuffer(this.protobuf);
                this.protobufByteBufferDirect = ByteBuffer.allocateDirect(this.protobuf.length);
                this.protobufByteBufferDirect.put(this.protobuf);
                this.protobufDataBufferDirect = BufferedData.wrap(this.protobufByteBufferDirect);
                this.bin = new NonSynchronizedByteArrayInputStream(this.protobuf);
                this.slimBin = new SlimBuffer(this.protobuf);
                ReadableStreamingData din = new ReadableStreamingData(this.bin);
                // output buffers
                this.bout = new NonSynchronizedByteArrayOutputStream();
                WritableStreamingData dout = new WritableStreamingData(this.bout);
                this.outArray = new byte[this.protobuf.length * 2]; // make sure big enough
                this.outDataBuffer = BufferedData.allocate(this.protobuf.length);
                this.outDataBufferDirect = BufferedData.allocateOffHeap(this.protobuf.length);
                this.bbout = ByteBuffer.allocate(this.protobuf.length);
                this.bboutDirect = ByteBuffer.allocateDirect(this.protobuf.length);
            } catch (IOException e) {
                e.getStackTrace();
                System.err.flush();
                throw new RuntimeException(e);
            }
        }
    }

    /** Same as parsePbjByteBuffer because DataBuffer.wrap(byte[]) uses ByteBuffer today, added this because makes result plotting easier */
    @Benchmark
    @Warmup(iterations = 5, time = 2)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjByteArray_slim(BenchmarkState<P, G> benchmarkState, Blackhole blackhole) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            benchmarkState.slimProtobufDataBuffer.resetPosition();
            blackhole.consume(benchmarkState.pbjCodec.parse(benchmarkState.slimProtobufDataBuffer));
        }
    }

    @Benchmark
    @Warmup(iterations = 4, time = 2)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjInputStream_slim(BenchmarkState<P, G> benchmarkState, Blackhole blackhole)
            throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            benchmarkState.slimBin.resetPosition();
            blackhole.consume(benchmarkState.pbjCodec.parse(benchmarkState.slimBin));
        }
    }

    /** Custom interface for method references as java.util.Function does not throw IOException */
    public interface ProtobufParseFunction<D, G> {
        G parse(D data) throws IOException;
    }

    @State(Scope.Benchmark)
    public static class EverythingBench
            extends ProtobufObjectBench<Everything, com.hedera.pbj.test.proto.java.Everything> {
        @Setup
        public void setup(BenchmarkState<Everything, com.hedera.pbj.test.proto.java.Everything> benchmarkState) {
            benchmarkState.configure(
                    EverythingTestData.EVERYTHING,
                    Everything.PROTOBUF,
                    com.hedera.pbj.test.proto.java.Everything::parseFrom,
                    com.hedera.pbj.test.proto.java.Everything::parseFrom,
                    com.hedera.pbj.test.proto.java.Everything::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class TimeStampBench
            extends ProtobufObjectBench<Timestamp, com.hederahashgraph.api.proto.java.Timestamp> {
        @Setup
        public void setup(BenchmarkState<Timestamp, com.hederahashgraph.api.proto.java.Timestamp> benchmarkState) {
            benchmarkState.configure(
                    new Timestamp(5678L, 1234),
                    Timestamp.PROTOBUF,
                    com.hederahashgraph.api.proto.java.Timestamp::parseFrom,
                    com.hederahashgraph.api.proto.java.Timestamp::parseFrom,
                    com.hederahashgraph.api.proto.java.Timestamp::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class AccountDetailsBench
            extends ProtobufObjectBench<
                    com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails,
                    GetAccountDetailsResponse.AccountDetails> {
        @Setup
        public void setup(
                BenchmarkState<
                                com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails,
                                GetAccountDetailsResponse.AccountDetails>
                        benchmarkState) {
            benchmarkState.configure(
                    AccountDetailsPbj.ACCOUNT_DETAILS,
                    AccountDetails.PROTOBUF,
                    GetAccountDetailsResponse.AccountDetails::parseFrom,
                    GetAccountDetailsResponse.AccountDetails::parseFrom,
                    GetAccountDetailsResponse.AccountDetails::parseFrom);
        }
    }

    static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(EverythingBench.class.getSimpleName())
                .build();

        BenchmarkReporter.printResults(new Runner(opt).run());
    }
}
