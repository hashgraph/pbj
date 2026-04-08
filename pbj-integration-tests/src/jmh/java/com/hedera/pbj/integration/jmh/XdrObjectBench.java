// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.io.IOException;
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

@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class XdrObjectBench {
    private static final int OPERATION_COUNT = 1000;

    // Prepared state for Everything
    private Everything everythingObj;
    private byte[] everythingXdrBytes;
    private BufferedData everythingXdrBuffer; // heap ByteBuffer
    private BufferedData everythingXdrBufferDirect; // off-heap ByteBuffer
    private byte[] everythingProtoBytes;
    private BufferedData everythingProtoBuffer;
    // output buffers (reset on each benchmark call)
    private BufferedData everythingOutBuffer;
    private BufferedData everythingOutBufferDirect;

    // Prepared state for TimestampTest (same fields pattern)
    private TimestampTest timestampObj;
    private byte[] timestampXdrBytes;
    private BufferedData timestampXdrBuffer;
    private BufferedData timestampXdrBufferDirect;
    private byte[] timestampProtoBytes;
    private BufferedData timestampProtoBuffer;
    private BufferedData timestampOutBuffer;
    private BufferedData timestampOutBufferDirect;

    @Setup
    public void setup() throws IOException {
        // Everything
        everythingObj = EverythingTestData.EVERYTHING;
        Bytes xdrE = Everything.XDR.toBytes(everythingObj);
        everythingXdrBytes = xdrE.toByteArray();
        everythingXdrBuffer = BufferedData.wrap(everythingXdrBytes);
        ByteBuffer evDirectBuf = ByteBuffer.allocateDirect(everythingXdrBytes.length);
        evDirectBuf.put(everythingXdrBytes);
        evDirectBuf.flip();
        everythingXdrBufferDirect = BufferedData.wrap(evDirectBuf);

        Bytes protoE = Everything.PROTOBUF.toBytes(everythingObj);
        everythingProtoBytes = protoE.toByteArray();
        everythingProtoBuffer = BufferedData.wrap(everythingProtoBytes);

        everythingOutBuffer = BufferedData.allocate(everythingXdrBytes.length);
        everythingOutBufferDirect = BufferedData.allocateOffHeap(everythingXdrBytes.length);

        // TimestampTest
        timestampObj = new TimestampTest(5678L, 1234);
        Bytes xdrT = TimestampTest.XDR.toBytes(timestampObj);
        timestampXdrBytes = xdrT.toByteArray();
        timestampXdrBuffer = BufferedData.wrap(timestampXdrBytes);
        ByteBuffer tsDirectBuf = ByteBuffer.allocateDirect(timestampXdrBytes.length);
        tsDirectBuf.put(timestampXdrBytes);
        tsDirectBuf.flip();
        timestampXdrBufferDirect = BufferedData.wrap(tsDirectBuf);

        Bytes protoT = TimestampTest.PROTOBUF.toBytes(timestampObj);
        timestampProtoBytes = protoT.toByteArray();
        timestampProtoBuffer = BufferedData.wrap(timestampProtoBytes);

        timestampOutBuffer = BufferedData.allocate(timestampXdrBytes.length);
        timestampOutBufferDirect = BufferedData.allocateOffHeap(timestampXdrBytes.length);
    }

    // ---- XDR parse benchmarks ----

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseXdrEverythingByteBuffer(Blackhole blackhole) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingXdrBuffer.resetPosition();
            blackhole.consume(Everything.XDR.parse(everythingXdrBuffer));
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseXdrEverythingByteBufferDirect(Blackhole blackhole) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingXdrBufferDirect.resetPosition();
            blackhole.consume(Everything.XDR.parse(everythingXdrBufferDirect));
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseXdrTimestampTestByteBuffer(Blackhole blackhole) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            timestampXdrBuffer.resetPosition();
            blackhole.consume(TimestampTest.XDR.parse(timestampXdrBuffer));
        }
    }

    // ---- Protobuf baseline parse ----

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void parseProtoEverythingByteBuffer(Blackhole blackhole) throws ParseException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingProtoBuffer.resetPosition();
            blackhole.consume(Everything.PROTOBUF.parse(everythingProtoBuffer));
        }
    }

    // ---- XDR write benchmarks ----

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeXdrEverythingByteBuffer(Blackhole blackhole) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingOutBuffer.reset();
            Everything.XDR.write(everythingObj, everythingOutBuffer);
            blackhole.consume(everythingOutBuffer);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeXdrEverythingByteDirect(Blackhole blackhole) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingOutBufferDirect.reset();
            Everything.XDR.write(everythingObj, everythingOutBufferDirect);
            blackhole.consume(everythingOutBufferDirect);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeXdrTimestampTestByteBuffer(Blackhole blackhole) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            timestampOutBuffer.reset();
            TimestampTest.XDR.write(timestampObj, timestampOutBuffer);
            blackhole.consume(timestampOutBuffer);
        }
    }

    // ---- Protobuf baseline write ----

    @Benchmark
    @OperationsPerInvocation(OPERATION_COUNT)
    public void writeProtoEverythingByteBuffer(Blackhole blackhole) throws IOException {
        for (int i = 0; i < OPERATION_COUNT; i++) {
            everythingOutBuffer.reset();
            Everything.PROTOBUF.write(everythingObj, everythingOutBuffer);
            blackhole.consume(everythingOutBuffer);
        }
    }
}
