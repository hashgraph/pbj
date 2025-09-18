// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
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
    private static final int NUM_OF_VALUES = 1201;

    ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
    final ByteBuffer bufferDirect = ByteBuffer.allocateDirect(256 * 1024);
    final BufferedData dataBuffer = BufferedData.wrap(buffer);
    final BufferedData dataBufferDirect = BufferedData.wrap(bufferDirect);

    Bytes bytes = Bytes.EMPTY;

    InputStream bais = null;
    ReadableStreamingData rsd = null;

    InputStream baisNonSync = null;
    ReadableStreamingData rsdNonSync = null;

    private final int[] offsets = new int[NUM_OF_VALUES];
    /**
     * Number of bytes to read at a time (1, 2, 4, or 8). So create inputs with 1 byte siz,e, 2 byte size, 4 byte size,
     * and 8 byte size.
     */
    @Param({"1", "2", "4", "8"})
    public int numOfBytes;

    public VarIntReaderBench() {
        try {
            CodedOutputStream cout = CodedOutputStream.newInstance(buffer);
            Random random = new Random(9387498731984L);
            int pos = 0;
            offsets[pos++] = 0;
            for (int i = 0; i < 600; i++) {
                cout.writeUInt64NoTag(random.nextLong(0, 128));
                offsets[pos++] = cout.getTotalBytesWritten();
            }
            for (int i = 0; i < 150; i++) {
                cout.writeUInt64NoTag(random.nextLong(128, 256));
                offsets[pos++] = cout.getTotalBytesWritten();
            }
            for (int i = 0; i < 150; i++) {
                cout.writeUInt64NoTag(random.nextLong(256, Integer.MAX_VALUE));
                offsets[pos++] = cout.getTotalBytesWritten();
            }
            for (int i = 0; i < 150; i++) {
                cout.writeUInt64NoTag(random.nextLong(Integer.MIN_VALUE, Integer.MAX_VALUE));
                offsets[pos++] = cout.getTotalBytesWritten();
            }
            for (int i = 0; i < 150; i++) {
                cout.writeUInt64NoTag(random.nextLong(0, Long.MAX_VALUE));
                offsets[pos++] = cout.getTotalBytesWritten();
            }
            cout.flush();
            // copy to direct buffer
            buffer.flip();
            bufferDirect.put(buffer);
            byte[] bts = new byte[buffer.limit()];
            for (int i = 0; i < buffer.limit(); i++) {
                bts[i] = buffer.get(i);
            }
            bytes = Bytes.wrap(bts);
            bais = new ByteArrayInputStream(bts.clone());
            rsd = new ReadableStreamingData(bais);
            baisNonSync = new NonSynchronizedByteArrayInputStream(bts.clone());
            rsdNonSync = new ReadableStreamingData(baisNonSync);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataBufferRead(Blackhole blackhole) throws IOException {
        dataBuffer.reset();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(dataBuffer.readVarLong(false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataBufferGet(Blackhole blackhole) throws IOException {
        dataBuffer.reset();
        int offset = 0;
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(dataBuffer.getVarLong(offsets[offset++], false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataBufferDirectRead(Blackhole blackhole) throws IOException {
        dataBufferDirect.reset();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(dataBufferDirect.readVarLong(false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataBytesGet(Blackhole blackhole) throws IOException {
        int offset = 0;
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(bytes.getVarLong(offsets[offset++], false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataSyncInputStreamRead(Blackhole blackhole) throws IOException {
        bais.reset();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(rsd.readVarLong(false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void dataNonSyncInputStreamRead(Blackhole blackhole) throws IOException {
        baisNonSync.reset();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(rsdNonSync.readVarLong(false));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void richardGet(Blackhole blackhole) throws MalformedProtobufException {
        int offset = 0;
        buffer.clear();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(getVarLongRichard(offsets[offset++], buffer));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void googleRead(Blackhole blackhole) throws IOException {
        buffer.clear();
        final CodedInputStream codedInputStream = CodedInputStream.newInstance(buffer);
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(codedInputStream.readRawVarint64());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void googleDirecRead(Blackhole blackhole) throws IOException {
        bufferDirect.clear();
        final CodedInputStream codedInputStream = CodedInputStream.newInstance(bufferDirect);
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(codedInputStream.readRawVarint64());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void googleSlowPathRead(Blackhole blackhole) throws MalformedProtobufException {
        buffer.clear();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(readRawVarint64SlowPath(buffer));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1200)
    public void googleSlowPathDirectRead(Blackhole blackhole) throws MalformedProtobufException {
        bufferDirect.clear();
        for (int i = 0; i < 1200; i++) {
            blackhole.consume(readRawVarint64SlowPath(bufferDirect));
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

    private static final int VARINT_CONTINUATION_MASK = 0b1000_0000;
    private static final int VARINT_DATA_MASK = 0b0111_1111;
    private static final int NUM_BITS_PER_VARINT_BYTE = 7;

    public static long getVarLongRichard(int offset, ByteBuffer buf) throws MalformedProtobufException {
        // Protobuf encodes smaller integers with fewer bytes than larger integers. It takes a full byte
        // to encode 7 bits of information. So, if all 64 bits of a long are in use (for example, if the
        // leading bit is 1, or even all bits are 1) then it will take 10 bytes to transmit what would
        // have otherwise been 8 bytes of data!
        //
        // Thus, at most, reading a varint should involve reading 10 bytes of data.
        //
        // The leading bit of each byte is a continuation bit. If set, another byte will follow.
        // If we read 10 bytes in sequence with a continuation bit set, then we have a malformed
        // byte stream.
        // The bytes come least to most significant 7 bits. So the first byte we read represents
        // the lowest 7 bytes, then the next byte is the next highest 7 bytes, etc.

        // The final value.
        long value = 0;
        // The amount to shift the bits we read by before AND with the value
        int shift = -NUM_BITS_PER_VARINT_BYTE;

        // This method works with heap byte buffers only
        final byte[] arr = buf.array();
        final int arrOffset = buf.arrayOffset() + offset;

        int i = 0;
        for (; i < 10; i++) {
            // Use UnsafeUtil instead of arr[arrOffset + i] to avoid array range checks
            byte b = UnsafeUtils.getArrayByteNoChecks(arr, arrOffset + i);
            value |= (long) (b & 0x7F) << (shift += NUM_BITS_PER_VARINT_BYTE);

            if (b >= 0) {
                return value;
            }
        }
        // If we read 10 in a row all with the leading continuation bit set, then throw a malformed
        // protobuf exception
        throw new MalformedProtobufException("Malformed var int");
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(VarIntReaderBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
