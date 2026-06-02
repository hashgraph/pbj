// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class StringBench {
    private static final int INVOCATIONS = 20 * 1024;

    @State(Scope.Thread)
    public static class BenchState {
        @Param({"4", "16", "128", "512", "4096", "32768"})
        int length;

        BufferedData bd;

        @Setup(Level.Trial)
        public void setup() {
            // Use ASCII strings, not full UTF-8:
            bd = BufferedData.allocate((length + ProtoWriterTools.sizeOfVarInt64(length)) * INVOCATIONS);

            // For determinism:
            final Random random = new Random(723049435);
            for (int i = 0; i < INVOCATIONS; i++) {
                final StringBuilder sb = new StringBuilder();
                for (int j = 0; j < length; j++) {
                    sb.append((char) (random.nextInt(127 - 32) + 32));
                }

                bd.writeVarInt(length, false);
                bd.writeUTF8(sb.toString());
            }

            bd.limit(bd.position());
        }

        @TearDown(Level.Trial)
        public void tearDown() {}
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj(final BenchState state, final Blackhole blackhole) throws IOException, ParseException {
        state.bd.resetPosition();
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(ProtoParserTools.readString(state.bd, Long.MAX_VALUE));
        }
    }

    public static String readString_preAlloc(final ReadableSequentialData input, final long maxSize)
            throws IOException, ParseException {
        final int length = input.readVarInt(false);
        if (length > maxSize) {
            throw new ParseException("size " + length + " is greater than max " + maxSize);
        }
        if (input.remaining() < length) {
            throw new BufferUnderflowException();
        }
        final ByteBuffer bb = ByteBuffer.allocate(length);
        final long bytesRead = input.readBytes(bb);
        if (bytesRead != length) {
            throw new BufferUnderflowException();
        }
        bb.rewind();

        // Shouldn't use `new String()` because we want to error out on malformed UTF-8 bytes.
        final CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        final CharBuffer cb = CharBuffer.allocate((int) (length * decoder.maxCharsPerByte()) + 1);

        CoderResult coderResult = decoder.decode(bb, cb, true);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        coderResult = decoder.flush(cb);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        cb.flip();

        return cb.toString();
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_preAlloc(final BenchState state, final Blackhole blackhole) throws IOException, ParseException {
        state.bd.resetPosition();
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(StringBench.readString_preAlloc(state.bd, Long.MAX_VALUE));
        }
    }

    private static final ThreadLocal<CharBuffer> THREAD_LOCAL_CHAR_BUFFER =
            ThreadLocal.withInitial(() -> CharBuffer.allocate(16));

    public static String readString_cacheCharBuffer(final ReadableSequentialData input, final long maxSize)
            throws IOException, ParseException {
        final int length = input.readVarInt(false);
        if (length > maxSize) {
            throw new ParseException("size " + length + " is greater than max " + maxSize);
        }
        if (input.remaining() < length) {
            throw new BufferUnderflowException();
        }
        final ByteBuffer bb = ByteBuffer.allocate(length);
        final long bytesRead = input.readBytes(bb);
        if (bytesRead != length) {
            throw new BufferUnderflowException();
        }
        bb.rewind();

        // Shouldn't use `new String()` because we want to error out on malformed UTF-8 bytes.
        final CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        CharBuffer cb = THREAD_LOCAL_CHAR_BUFFER.get();
        final int capacity = (int) (length * decoder.maxCharsPerByte()) + 1;
        if (cb.capacity() < capacity) {
            THREAD_LOCAL_CHAR_BUFFER.set(cb = CharBuffer.allocate(capacity));
        }
        cb.limit(cb.capacity());
        cb.rewind();

        CoderResult coderResult = decoder.decode(bb, cb, true);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        coderResult = decoder.flush(cb);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        cb.flip();

        return cb.toString();
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_cacheCharBuffer(final BenchState state, final Blackhole blackhole)
            throws IOException, ParseException {
        state.bd.resetPosition();
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(StringBench.readString_cacheCharBuffer(state.bd, Long.MAX_VALUE));
        }
    }

    private static final ThreadLocal<ByteBuffer> THREAD_LOCAL_BYTE_BUFFER =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(16));

    public static String readString_cacheAllBuffers(final ReadableSequentialData input, final long maxSize)
            throws IOException, ParseException {
        final int length = input.readVarInt(false);
        if (length > maxSize) {
            throw new ParseException("size " + length + " is greater than max " + maxSize);
        }
        if (input.remaining() < length) {
            throw new BufferUnderflowException();
        }
        ByteBuffer bb = THREAD_LOCAL_BYTE_BUFFER.get();
        if (bb.capacity() < length) {
            THREAD_LOCAL_BYTE_BUFFER.set(bb = ByteBuffer.allocate(length));
        }
        bb.limit(length);
        bb.rewind();
        final long bytesRead = input.readBytes(bb);
        if (bytesRead != length) {
            throw new BufferUnderflowException();
        }
        bb.rewind();

        // Shouldn't use `new String()` because we want to error out on malformed UTF-8 bytes.
        final CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        CharBuffer cb = THREAD_LOCAL_CHAR_BUFFER.get();
        final int capacity = (int) (length * decoder.maxCharsPerByte()) + 1;
        if (cb.capacity() < capacity) {
            THREAD_LOCAL_CHAR_BUFFER.set(cb = CharBuffer.allocate(capacity));
        }

        CoderResult coderResult = decoder.decode(bb, cb, true);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        coderResult = decoder.flush(cb);
        if (coderResult.isError()) {
            throw new MalformedProtobufException("Malformed UTF-8 string encountered: " + coderResult);
        }

        cb.flip();

        return cb.toString();
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_cacheAllBuffers(final BenchState state, final Blackhole blackhole)
            throws IOException, ParseException {
        state.bd.resetPosition();
        for (int invocation = 0; invocation < INVOCATIONS; invocation++) {
            blackhole.consume(StringBench.readString_cacheAllBuffers(state.bd, Long.MAX_VALUE));
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder().include(StringBench.class.getSimpleName()).build();

        new Runner(opt).run();
    }
}
