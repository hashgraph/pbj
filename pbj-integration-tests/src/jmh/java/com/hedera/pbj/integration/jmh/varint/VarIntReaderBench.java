// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.MemoryData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarIntReaderBench {
    private static final int NUM_OF_VALUES = 1200;

    /**
     * Number of bytes each varint occupies (1, 2, 4, or 8). All values are generated
     * in the range [{@code 1L << ((numOfBytes-1)*7)}, {@code (1L << (numOfBytes*7)) - 1}].
     */
    @Param({"1", "2", "3", "4", "8"})
    public int numOfBytes;

    // PBJ BufferedData benchmarks use their own backing buffers so that google benchmarks
    // calling buffer.clear() do not clobber limit for PBJ reads.
    private BufferedData dataBuffer;
    private BufferedData dataBufferDirect;
    private MemoryData dataMemory;

    // Google / Richard benchmarks share heap and direct ByteBuffers.
    // Capacity equals dataLength so buffer.clear() correctly resets to position=0, limit=dataLength.
    private ByteBuffer googleBuffer;
    private ByteBuffer googleBufferDirect;

    private Bytes bytes;
    private InputStream bais;
    private ReadableStreamingData rsd;
    private InputStream baisNonSync;
    private ReadableStreamingData rsdNonSync;

    private final int[] offsets = new int[NUM_OF_VALUES];

    @Setup(Level.Trial)
    public void setup() throws IOException {
        final long minValue = numOfBytes == 1 ? 0L : 1L << ((numOfBytes - 1) * 7);
        final long maxValue = (1L << (numOfBytes * 7)) - 1;
        final int maxBufSize = (numOfBytes + 1) * NUM_OF_VALUES;

        byte[] scratch = new byte[maxBufSize];
        CodedOutputStream cout = CodedOutputStream.newInstance(scratch);
        Random random = new Random(9387498731984L);
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            offsets[i] = cout.getTotalBytesWritten();
            cout.writeUInt64NoTag(random.nextLong(minValue, maxValue));
        }
        cout.flush();
        byte[] data = Arrays.copyOf(scratch, cout.getTotalBytesWritten());

        // Exact-sized copy for Google/Richard heap buffer — clear() sets limit back to dataLength
        googleBuffer = ByteBuffer.wrap(data.clone());

        // Direct buffer for Google direct benchmarks
        googleBufferDirect = ByteBuffer.allocateDirect(data.length);
        googleBufferDirect.put(data);
        googleBufferDirect.flip();

        // PBJ heap BufferedData — separate backing array, limit stays correct after resetPosition()
        dataBuffer = BufferedData.wrap(ByteBuffer.wrap(data.clone()));

        // PBJ direct BufferedData
        ByteBuffer directBuf = ByteBuffer.allocateDirect(data.length);
        directBuf.put(data);
        directBuf.flip();
        dataBufferDirect = BufferedData.wrap(directBuf);

        // PBJ MemoryData — wrap exact byte array, limit = data.length
        dataMemory = MemoryData.wrap(data.clone());

        // Bytes (immutable)
        bytes = Bytes.wrap(data.clone());

        // Synchronized stream
        bais = new ByteArrayInputStream(data.clone());
        rsd = new ReadableStreamingData(bais);

        // Non-synchronized stream
        baisNonSync = new NonSynchronizedByteArrayInputStream(data.clone());
        rsdNonSync = new ReadableStreamingData(baisNonSync);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataBufferRead(Blackhole blackhole) {
        dataBuffer.resetPosition();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(dataBuffer.readVarLong(false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataBufferGet(Blackhole blackhole) {
        int offset = 0;
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(dataBuffer.getVarLong(offsets[offset++], false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataBufferDirectRead(Blackhole blackhole) {
        dataBufferDirect.resetPosition();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(dataBufferDirect.readVarLong(false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataMemoryRead(Blackhole blackhole) {
        dataMemory.resetPosition();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(dataMemory.readVarLong(false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataMemoryGet(Blackhole blackhole) {
        int offset = 0;
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(dataMemory.getVarLong(offsets[offset++], false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataBytesGet(Blackhole blackhole) {
        int offset = 0;
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(bytes.getVarLong(offsets[offset++], false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataSyncInputStreamRead(Blackhole blackhole) throws IOException {
        bais.reset();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(rsd.readVarLong(false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void dataNonSyncInputStreamRead(Blackhole blackhole) throws IOException {
        baisNonSync.reset();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(rsdNonSync.readVarLong(false));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void richardGet(Blackhole blackhole) throws MalformedProtobufException {
        int offset = 0;
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(getVarLongRichard(offsets[offset++], googleBuffer));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleRead(Blackhole blackhole) throws IOException {
        googleBuffer.clear();
        final CodedInputStream codedInputStream = CodedInputStream.newInstance(googleBuffer);
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(codedInputStream.readRawVarint64());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleDirectRead(Blackhole blackhole) throws IOException {
        googleBufferDirect.clear();
        final CodedInputStream codedInputStream = CodedInputStream.newInstance(googleBufferDirect);
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(codedInputStream.readRawVarint64());
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleSlowPathRead(Blackhole blackhole) throws MalformedProtobufException {
        googleBuffer.clear();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(readRawVarint64SlowPath(googleBuffer));
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleSlowPathDirectRead(Blackhole blackhole) throws MalformedProtobufException {
        googleBufferDirect.clear();
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            blackhole.consume(readRawVarint64SlowPath(googleBufferDirect));
        }
    }

    private static long readRawVarint64SlowPath(ByteBuffer buf) throws MalformedProtobufException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new MalformedProtobufException("Malformed varInt");
    }

    private static final int NUM_BITS_PER_VARINT_BYTE = 7;

    public static long getVarLongRichard(int offset, ByteBuffer buf) throws MalformedProtobufException {
        long value = 0;
        int shift = -NUM_BITS_PER_VARINT_BYTE;
        final byte[] arr = buf.array();
        final int arrOffset = buf.arrayOffset() + offset;
        for (int i = 0; i < 10; i++) {
            byte b = UnsafeUtils.getArrayByteNoChecks(arr, arrOffset + i);
            value |= (long) (b & 0x7F) << (shift += NUM_BITS_PER_VARINT_BYTE);
            if (b >= 0) {
                return value;
            }
        }
        throw new MalformedProtobufException("Malformed var int");
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(VarIntReaderBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
