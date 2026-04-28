// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.google.protobuf.GeneratedMessage;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
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

/**
 * Benchmark matrix for protobuf object serialization and deserialization across only byte array PBJ and
 * Google Protobuf IO types.
 *
 * <p>PBJ backing-store coverage:
 * <pre>
 *   protobuf     — Original byte array (parse only)
 *   Bytes        — immutable Bytes wrapping a byte[] (parse only)
 * </pre>
 */
@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class ProtobufObjectArrayBench<P, G extends GeneratedMessage> {
    /** Repeat each operation this many times so per-op times are large enough to measure. */
    private static final int OPERATION_COUNT = 1000;

    @SuppressWarnings("DuplicatedCode")
    @State(Scope.Benchmark)
    public static class BenchmarkState<P, G extends GeneratedMessage> {
        private Codec<P> pbjCodec;
        private ProtobufParseFunction<byte[], G> googleByteArrayParseMethod;

        // model objects
        private P pbjModelObject;
        private G googleModelObject;

        // ── parse inputs ──────────────────────────────────────────────────────
        /** Raw bytes — used directly by Google parseFrom(byte[]) */
        private byte[] protobuf;

        /** Immutable Bytes — no reset needed */
        private Bytes protobufBytes;

        public void configure(
                P pbjModelObject, Codec<P> pbjCodec, ProtobufParseFunction<byte[], G> googleByteArrayParseMethod) {
            try {
                this.pbjModelObject = pbjModelObject;
                this.pbjCodec = pbjCodec;
                this.googleByteArrayParseMethod = googleByteArrayParseMethod;

                // Serialize once to get the canonical byte representation
                BufferedData tempDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
                pbjCodec.write(pbjModelObject, tempDataBuffer);
                tempDataBuffer.flip();
                this.protobuf = new byte[(int) tempDataBuffer.remaining()];
                tempDataBuffer.readBytes(this.protobuf);
                this.googleModelObject = googleByteArrayParseMethod.parse(this.protobuf);

                final int dataLen = this.protobuf.length;

                // ── parse inputs ─────────────────────────────────────────────
                this.protobufBytes = Bytes.wrap(this.protobuf.clone());
            } catch (IOException e) {
                e.getStackTrace();
                System.err.flush();
                throw new RuntimeException(e);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PBJ parse benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Parse: PBJ · Bytes (immutable) — backed by byte[], no reset needed */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbj(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.pbjCodec.parse(s.protobufBytes));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Google Protobuf parse benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Parse: Google · byte[] */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoC(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.googleByteArrayParseMethod.parse(s.protobuf));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // write benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Write: PBJ · raw byte[] via Codec.write(T, byte[], int) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void toBytesPbj(BenchmarkState<P, G> s, Blackhole bh) {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.pbjCodec.toBytes(s.pbjModelObject));
        }
    }

    /** Write: Google · byte[] via toByteArray() */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void toBytesProtoC(BenchmarkState<P, G> s, Blackhole bh) {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.googleModelObject.toByteArray());
        }
    }

    /** Custom interface for method references as java.util.Function does not throw IOException */
    public interface ProtobufParseFunction<D, G> {
        G parse(D data) throws IOException;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Concrete benchmark subclasses
    // ════════════════════════════════════════════════════════════════════════

    @State(Scope.Benchmark)
    public static class EverythingBench
            extends ProtobufObjectArrayBench<Everything, com.hedera.pbj.test.proto.java.Everything> {
        @Setup
        public void setup(BenchmarkState<Everything, com.hedera.pbj.test.proto.java.Everything> benchmarkState) {
            benchmarkState.configure(
                    EverythingTestData.EVERYTHING,
                    Everything.PROTOBUF,
                    com.hedera.pbj.test.proto.java.Everything::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class BlockBench extends ProtobufObjectArrayBench<Block, com.hedera.hapi.block.stream.protoc.Block> {
        @Setup
        public void setup(BenchmarkState<Block, com.hedera.hapi.block.stream.protoc.Block> benchmarkState) {

            // load the protobuf bytes
            try (var in = new ReadableStreamingData(new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(
                    SampleBlockBench.class.getResourceAsStream("/000000000000000000000000000000497558.blk.gz")))))) {
                final Block TEST_BLOCK = com.hedera.hapi.block.stream.Block.PROTOBUF.parse(in);
                benchmarkState.configure(
                        TEST_BLOCK, Block.PROTOBUF, com.hedera.hapi.block.stream.protoc.Block::parseFrom);
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class TimeStampBench
            extends ProtobufObjectArrayBench<Timestamp, com.hederahashgraph.api.proto.java.Timestamp> {
        @Setup
        public void setup(BenchmarkState<Timestamp, com.hederahashgraph.api.proto.java.Timestamp> benchmarkState) {
            benchmarkState.configure(
                    new Timestamp(5678L, 1234),
                    Timestamp.PROTOBUF,
                    com.hederahashgraph.api.proto.java.Timestamp::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class AccountDetailsBench
            extends ProtobufObjectArrayBench<AccountDetails, GetAccountDetailsResponse.AccountDetails> {
        @Setup
        public void setup(
                BenchmarkState<
                                com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails,
                                GetAccountDetailsResponse.AccountDetails>
                        benchmarkState) {
            benchmarkState.configure(
                    AccountDetailsPbj.ACCOUNT_DETAILS,
                    AccountDetails.PROTOBUF,
                    GetAccountDetailsResponse.AccountDetails::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class AccountIDBench
            extends ProtobufObjectArrayBench<AccountID, com.hederahashgraph.api.proto.java.AccountID> {
        @Setup
        public void setup(BenchmarkState<AccountID, com.hederahashgraph.api.proto.java.AccountID> benchmarkState) {
            benchmarkState.configure(
                    AccountDetailsPbj.ACCOUNT_ID,
                    AccountID.PROTOBUF,
                    com.hederahashgraph.api.proto.java.AccountID::parseFrom);
        }
    }
}
