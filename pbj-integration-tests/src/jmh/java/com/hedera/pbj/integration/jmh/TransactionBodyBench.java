// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
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

/**
 * Parsing and writing the {@link TransactionBody} of every signed transaction in the sample block-stream file. A signed
 * transaction carries its body as bytes, so the block benchmarks never parse one. {@code TransactionBody} has the
 * largest oneof in HAPI: its stream parser was over HotSpot's 8,000-byte limit for compiling a method, and its writer
 * close to it. Every result is the average time per body.
 */
@SuppressWarnings("unused")
@Fork(
        value = 3,
        jvmArgsAppend = {"-Xms32g", "-Xmx32g", "-Xmn24g"})
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class TransactionBodyBench {
    /** The sample block, as in {@link BlockItemBench}. */
    private static final String BLOCK_FILE = "/000000000000000000000000000000030221.blk.gz";

    /** Must match the number of bodies, checked in setup; JMH needs a constant for the operations per invocation. */
    private static final int BODIES = 21_981;

    private final List<Bytes> bodyBytes = new ArrayList<>();
    private final List<byte[]> bodyArrays = new ArrayList<>();
    private final List<TransactionBody> bodies = new ArrayList<>();
    private byte[] out;

    @Setup
    public void setup() throws IOException, ParseException {
        try (var in = new BufferedInputStream(new GZIPInputStream(
                Objects.requireNonNull(TransactionBodyBench.class.getResourceAsStream(BLOCK_FILE))))) {
            final Block block = Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
            for (final BlockItem item : block.items()) {
                if (item.hasSignedTransaction()) {
                    final SignedTransaction signed = SignedTransaction.PROTOBUF.parse(item.signedTransactionOrThrow());
                    bodyBytes.add(signed.bodyBytes());
                }
            }
        }
        if (bodyBytes.size() != BODIES) {
            throw new IllegalStateException("Update BODIES to " + bodyBytes.size());
        }
        int max = 0;
        for (final Bytes bytes : bodyBytes) {
            bodyArrays.add(bytes.toByteArray());
            final TransactionBody body = TransactionBody.PROTOBUF.parse(bytes);
            bodies.add(body);
            max = Math.max(max, body.protobufSize());
        }
        out = new byte[max];
    }

    @Benchmark
    @OperationsPerInvocation(BODIES)
    public void parse(final Blackhole bh) throws ParseException {
        for (final Bytes bytes : bodyBytes) {
            bh.consume(TransactionBody.PROTOBUF.parse(bytes));
        }
    }

    @Benchmark
    @OperationsPerInvocation(BODIES)
    public void parseProtoC(final Blackhole bh) throws InvalidProtocolBufferException {
        for (final byte[] bytes : bodyArrays) {
            bh.consume(com.hederahashgraph.api.proto.java.TransactionBody.parseFrom(bytes));
        }
    }

    /** Writes objects whose sizes are cached, so this times the byte-array writer alone. */
    @Benchmark
    @OperationsPerInvocation(BODIES)
    public void write(final Blackhole bh) {
        for (final TransactionBody body : bodies) {
            bh.consume(TransactionBody.PROTOBUF.write(body, out, 0));
        }
    }
}
