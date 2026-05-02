// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.GeneratedMessage;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.GetAccountDetailsResponse.AccountDetails;
import com.hedera.pbj.integration.AccountDetailsPbj;
import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.MemoryData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
 * Benchmark matrix for protobuf object serialization and deserialization across all PBJ and
 * Google Protobuf IO types.
 *
 * <p>Method names encode the full backing-store identity:
 * <pre>
 *   {parse|write}{Pbj|ProtoC}{WrapperType[BackingDetail]}
 * </pre>
 *
 * <p>PBJ backing-store coverage:
 * <pre>
 *   BufferedDataHeap     — BufferedData wrapping a heap ByteBuffer  (ByteArrayBufferedData)
 *   BufferedDataDirect   — BufferedData wrapping a direct ByteBuffer (DirectBufferedData)
 *   MemoryDataHeap       — MemoryData backed by MemorySegment.ofArray(byte[])     (heap)
 *   MemoryDataNative     — MemoryData backed by MemorySegment.ofBuffer(directBB)  (native)
 *   MemoryDataOffHeap    — MemoryData backed by Arena.ofAuto().allocate()         (native)
 *   Bytes                — immutable Bytes wrapping a byte[] (parse only)
 *   InputStream          — ReadableStreamingData over a NonSynchronizedByteArrayInputStream
 *   OutputStream         — WritableStreamingData over a NonSynchronizedByteArrayOutputStream
 *   ByteArray            — raw byte[] via Codec.write(T, byte[], int)             (write only)
 * </pre>
 */
@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class ProtobufObjectBench<P, G extends GeneratedMessage> {
    /** Repeat each operation this many times so per-op times are large enough to measure. */
    private static final int OPERATION_COUNT = 1000;

    @State(Scope.Benchmark)
    public static class BenchmarkState<P, G extends GeneratedMessage> {
        private Codec<P> pbjCodec;
        private ProtobufParseFunction<byte[], G> googleByteArrayParseMethod;
        private ProtobufParseFunction<ByteBuffer, G> googleByteBufferParseMethod;
        private ProtobufParseFunction<InputStream, G> googleInputStreamParseMethod;

        // model objects
        private P pbjModelObject;
        private G googleModelObject;

        // ── parse inputs ──────────────────────────────────────────────────────
        /** Raw bytes — used directly by Google parseFrom(byte[]) */
        private byte[] protobuf;

        /** heap ByteBuffer — position=0 after resetPosition(); used by Google heap bench */
        private ByteBuffer protobufByteBufferHeap;
        /** direct ByteBuffer — position reset before each Google direct bench iteration */
        private ByteBuffer protobufByteBufferDirect;

        /** BufferedData (heap) — ByteArrayBufferedData wrapping a heap ByteBuffer */
        private BufferedData protobufBufferedDataHeap;
        /** BufferedData (direct) — DirectBufferedData wrapping a direct ByteBuffer */
        private BufferedData protobufBufferedDataDirect;

        /** MemoryData backed by MemorySegment.ofArray(byte[]) — heap segment */
        private MemoryData protobufMemoryDataHeap;
        /** MemoryData backed by MemorySegment.ofBuffer(directBB) — native segment */
        private MemoryData protobufMemoryDataNative;
        /** MemoryData backed by Arena.ofAuto().allocate() — native segment */
        private MemoryData protobufMemoryDataOffHeap;

        /** Immutable Bytes — no reset needed */
        private Bytes protobufBytes;

        /** Non-synchronized stream — reset with resetPosition() */
        private NonSynchronizedByteArrayInputStream bin;

        // ── write outputs ─────────────────────────────────────────────────────
        /** Raw byte[] — direct write target for Codec.write(T, byte[], int) */
        private byte[] outByteArray;

        /** BufferedData (heap) */
        private BufferedData outBufferedDataHeap;
        /** BufferedData (direct) */
        private BufferedData outBufferedDataDirect;

        /** MemoryData backed by MemorySegment.ofArray(byte[]) — heap segment */
        private MemoryData outMemoryDataHeap;
        /** MemoryData backed by MemorySegment.ofBuffer(directBB) — native segment */
        private MemoryData outMemoryDataNative;
        /** MemoryData backed by Arena.ofAuto().allocate() — native segment */
        private MemoryData outMemoryDataOffHeap;

        /** Output stream for PBJ and Google stream benchmarks */
        private NonSynchronizedByteArrayOutputStream bout;

        /** heap ByteBuffer output — used by Google heap write bench */
        private ByteBuffer bboutHeap;
        /** direct ByteBuffer output — used by Google direct write bench */
        private ByteBuffer bboutDirect;

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

                // Serialize once to get the canonical byte representation
                BufferedData tempDataBuffer = BufferedData.allocate(5 * 1024 * 1024);
                pbjCodec.write(pbjModelObject, tempDataBuffer);
                tempDataBuffer.flip();
                this.protobuf = new byte[(int) tempDataBuffer.remaining()];
                tempDataBuffer.readBytes(this.protobuf);
                this.googleModelObject = googleByteArrayParseMethod.parse(this.protobuf);

                final int dataLen = this.protobuf.length;

                // ── parse inputs ─────────────────────────────────────────────

                // Google byte[] — the raw array is reused directly (parseFrom does not advance it)
                // Each backing array is an independent clone so resets are independent.

                this.protobufByteBufferHeap = ByteBuffer.wrap(this.protobuf.clone());

                ByteBuffer directIn = ByteBuffer.allocateDirect(dataLen);
                directIn.put(this.protobuf).flip();
                this.protobufByteBufferDirect = directIn;

                this.protobufBufferedDataHeap = BufferedData.wrap(this.protobuf.clone());

                ByteBuffer directInBD = ByteBuffer.allocateDirect(dataLen);
                directInBD.put(this.protobuf);
                this.protobufBufferedDataDirect = BufferedData.wrap(directInBD);

                this.protobufMemoryDataHeap = MemoryData.wrap(this.protobuf.clone());

                ByteBuffer directInMD = ByteBuffer.allocateDirect(dataLen);
                directInMD.put(this.protobuf).flip();
                this.protobufMemoryDataNative = MemoryData.wrap(directInMD);

                this.protobufMemoryDataOffHeap = MemoryData.allocateOffHeap(dataLen);
                this.protobufMemoryDataOffHeap.writeBytes(this.protobuf);
                this.protobufMemoryDataOffHeap.resetPosition();

                this.protobufBytes = Bytes.wrap(this.protobuf.clone());

                this.bin = new NonSynchronizedByteArrayInputStream(this.protobuf.clone());

                // ── write outputs ────────────────────────────────────────────

                this.outByteArray = new byte[dataLen * 2];
                this.outBufferedDataHeap = BufferedData.allocate(dataLen);
                this.outBufferedDataDirect = BufferedData.allocateOffHeap(dataLen);
                this.outMemoryDataHeap = MemoryData.allocate(dataLen);
                this.outMemoryDataOffHeap = MemoryData.allocateOffHeap(dataLen);

                ByteBuffer directOutMD = ByteBuffer.allocateDirect(dataLen);
                this.outMemoryDataNative = MemoryData.wrap(directOutMD);

                this.bout = new NonSynchronizedByteArrayOutputStream();
                this.bboutHeap = ByteBuffer.allocate(dataLen);
                this.bboutDirect = ByteBuffer.allocateDirect(dataLen);
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

    /** Parse: PBJ · BufferedData (heap) — ByteArrayBufferedData, ByteBuffer.wrap(byte[]) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjBufferedDataHeap(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufBufferedDataHeap.resetPosition();
            bh.consume(s.pbjCodec.parse(s.protobufBufferedDataHeap));
        }
    }

    /** Parse: PBJ · BufferedData (direct) — DirectBufferedData, ByteBuffer.allocateDirect() */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjBufferedDataDirect(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufBufferedDataDirect.resetPosition();
            bh.consume(s.pbjCodec.parse(s.protobufBufferedDataDirect));
        }
    }

    /** Parse: PBJ · MemoryData (heap) — MemorySegment.ofArray(byte[]) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjMemoryDataHeap(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufMemoryDataHeap.resetPosition();
            bh.consume(s.pbjCodec.parse(s.protobufMemoryDataHeap));
        }
    }

    /** Parse: PBJ · MemoryData (native) — MemorySegment.ofBuffer(directByteBuffer) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjMemoryDataNative(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufMemoryDataNative.resetPosition();
            bh.consume(s.pbjCodec.parse(s.protobufMemoryDataNative));
        }
    }

    /** Parse: PBJ · MemoryData (off-heap) — Arena.ofAuto().allocate() native segment */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjMemoryDataOffHeap(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufMemoryDataOffHeap.resetPosition();
            bh.consume(s.pbjCodec.parse(s.protobufMemoryDataOffHeap));
        }
    }

    /** Parse: PBJ · Bytes (immutable) — backed by byte[], no reset needed */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjBytes(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.pbjCodec.parse(s.protobufBytes));
        }
    }

    /** Parse: PBJ · ReadableStreamingData wrapping NonSynchronizedByteArrayInputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parsePbjInputStream(BenchmarkState<P, G> s, Blackhole bh) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bin.resetPosition();
            bh.consume(s.pbjCodec.parse(new ReadableStreamingData(s.bin)));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Google Protobuf parse benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Parse: Google · byte[] */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoCByteArray(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.googleByteArrayParseMethod.parse(s.protobuf));
        }
    }

    /** Parse: Google · ByteBuffer (heap) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoCByteBufferHeap(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufByteBufferHeap.position(0);
            bh.consume(s.googleByteBufferParseMethod.parse(s.protobufByteBufferHeap));
        }
    }

    /** Parse: Google · ByteBuffer (direct) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoCByteBufferDirect(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.protobufByteBufferDirect.position(0);
            bh.consume(s.googleByteBufferParseMethod.parse(s.protobufByteBufferDirect));
        }
    }

    /** Parse: Google · InputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoCInputStream(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bin.resetPosition();
            bh.consume(s.googleInputStreamParseMethod.parse(s.bin));
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PBJ write benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Write: PBJ · raw byte[] via Codec.write(T, byte[], int) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjByteArray(BenchmarkState<P, G> s, Blackhole bh) {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.pbjCodec.write(s.pbjModelObject, s.outByteArray, 0);
            bh.consume(s.outByteArray);
        }
    }

    /** Write: PBJ · BufferedData (heap) — ByteArrayBufferedData */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjBufferedDataHeap(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.outBufferedDataHeap.reset();
            s.pbjCodec.write(s.pbjModelObject, s.outBufferedDataHeap);
            bh.consume(s.outBufferedDataHeap);
        }
    }

    /** Write: PBJ · BufferedData (direct) — DirectBufferedData */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjBufferedDataDirect(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.outBufferedDataDirect.reset();
            s.pbjCodec.write(s.pbjModelObject, s.outBufferedDataDirect);
            bh.consume(s.outBufferedDataDirect);
        }
    }

    /** Write: PBJ · MemoryData (heap) — MemorySegment.ofArray(byte[]) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjMemoryDataHeap(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.outMemoryDataHeap.reset();
            s.pbjCodec.write(s.pbjModelObject, s.outMemoryDataHeap);
            bh.consume(s.outMemoryDataHeap);
        }
    }

    /** Write: PBJ · MemoryData (native) — MemorySegment.ofBuffer(directByteBuffer) */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjMemoryDataNative(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.outMemoryDataNative.reset();
            s.pbjCodec.write(s.pbjModelObject, s.outMemoryDataNative);
            bh.consume(s.outMemoryDataNative);
        }
    }

    /** Write: PBJ · MemoryData (off-heap) — Arena.ofAuto().allocate() native segment */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjMemoryDataOffHeap(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.outMemoryDataOffHeap.reset();
            s.pbjCodec.write(s.pbjModelObject, s.outMemoryDataOffHeap);
            bh.consume(s.outMemoryDataOffHeap);
        }
    }

    /** Write: PBJ · WritableStreamingData wrapping NonSynchronizedByteArrayOutputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writePbjOutputStream(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bout.reset();
            s.pbjCodec.write(s.pbjModelObject, new WritableStreamingData(s.bout));
            bh.consume(s.bout.toByteArray());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Google Protobuf write benchmarks
    // ════════════════════════════════════════════════════════════════════════

    /** Write: Google · byte[] via toByteArray() */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeProtoCByteArray(BenchmarkState<P, G> s, Blackhole bh) {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            bh.consume(s.googleModelObject.toByteArray());
        }
    }

    /** Write: Google · ByteBuffer (heap) via CodedOutputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeProtoCByteBufferHeap(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bboutHeap.clear();
            CodedOutputStream cout = CodedOutputStream.newInstance(s.bboutHeap);
            s.googleModelObject.writeTo(cout);
            bh.consume(s.bboutHeap);
        }
    }

    /** Write: Google · ByteBuffer (direct) via CodedOutputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeProtoCByteBufferDirect(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bboutDirect.clear();
            CodedOutputStream cout = CodedOutputStream.newInstance(s.bboutDirect);
            s.googleModelObject.writeTo(cout);
            bh.consume(s.bboutDirect);
        }
    }

    /** Write: Google · OutputStream */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeProtoCOutputStream(BenchmarkState<P, G> s, Blackhole bh) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            s.bout.reset();
            s.googleModelObject.writeTo(s.bout);
            bh.consume(s.bout.toByteArray());
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
    public static class BlockBench extends ProtobufObjectBench<Block, com.hedera.hapi.block.stream.protoc.Block> {
        @Setup
        public void setup(BenchmarkState<Block, com.hedera.hapi.block.stream.protoc.Block> benchmarkState) {

            // load the protobuf bytes
            try (var in = new ReadableStreamingData(new BufferedInputStream(new GZIPInputStream(Objects.requireNonNull(
                    SampleBlockBench.class.getResourceAsStream("/000000000000000000000000000000497558.blk.gz")))))) {
                final Block TEST_BLOCK = Block.PROTOBUF.parse(in);
                benchmarkState.configure(
                        TEST_BLOCK,
                        Block.PROTOBUF,
                        com.hedera.hapi.block.stream.protoc.Block::parseFrom,
                        com.hedera.hapi.block.stream.protoc.Block::parseFrom,
                        com.hedera.hapi.block.stream.protoc.Block::parseFrom);
            } catch (IOException | ParseException e) {
                throw new RuntimeException(e);
            }
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
            extends ProtobufObjectBench<AccountDetails, GetAccountDetailsResponse.AccountDetails> {
        @Setup
        public void setup(BenchmarkState<AccountDetails, GetAccountDetailsResponse.AccountDetails> benchmarkState) {
            benchmarkState.configure(
                    AccountDetailsPbj.ACCOUNT_DETAILS,
                    AccountDetails.PROTOBUF,
                    GetAccountDetailsResponse.AccountDetails::parseFrom,
                    GetAccountDetailsResponse.AccountDetails::parseFrom,
                    GetAccountDetailsResponse.AccountDetails::parseFrom);
        }
    }

    @State(Scope.Benchmark)
    public static class AccountIDBench
            extends ProtobufObjectBench<AccountID, com.hederahashgraph.api.proto.java.AccountID> {
        @Setup
        public void setup(BenchmarkState<AccountID, com.hederahashgraph.api.proto.java.AccountID> benchmarkState) {
            benchmarkState.configure(
                    AccountDetailsPbj.ACCOUNT_ID,
                    AccountID.PROTOBUF,
                    com.hederahashgraph.api.proto.java.AccountID::parseFrom,
                    com.hederahashgraph.api.proto.java.AccountID::parseFrom,
                    com.hederahashgraph.api.proto.java.AccountID::parseFrom);
        }
    }
}
