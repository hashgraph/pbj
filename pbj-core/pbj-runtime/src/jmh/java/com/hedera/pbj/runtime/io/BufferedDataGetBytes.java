package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@Fork(value = 1)
@State(Scope.Benchmark)
public class BufferedDataGetBytes {

    @Param({"10000"})
    public int size = 10000;

    @Param({"1000"})
    public int window = 1000;

    private BufferedData heapData;
    private BufferedData directData;

    private boolean printSum;

    @Setup(Level.Trial)
    public void init() {
        heapData = BufferedData.allocate(size);
        directData = BufferedData.allocateOffHeap(size);
        for (int i = 0; i < size; i++) {
            heapData.writeByte((byte) (i % 111));
            directData.writeByte((byte) (i % 111));
        }
    }

    @Setup(Level.Iteration)
    public void initEach() {
        printSum = true;
    }

    private static long sum(final byte[] arr) {
        long result = 0;
        for (int i = 0; i < arr.length; i++) {
            result += arr[i];
        }
        return result;
    }

    private static long sum(final ByteBuffer buf) {
        long result = 0;
        for (int i = 0; i < buf.capacity(); i++) {
            result += buf.get(i);
        }
        return result;
    }

    private static long sum(final Bytes bytes) {
        long result = 0;
        for (int i = 0; i < bytes.length(); i++) {
            result += bytes.getByte(i);
        }
        return result;
    }

    @Benchmark
    public void heapToByteArray(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapToHeapByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocate(window);
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapToDirectByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapToBytes(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            final Bytes bytes = heapData.getBytes(i, window);
//            sum += sum(bytes);
            blackhole.consume(bytes);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directToByteArray(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directToHeapByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocate(window);
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directToDirectByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directToBytes(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            final Bytes bytes = directData.getBytes(i, window);
//            sum += sum(bytes);
            blackhole.consume(bytes);
        }
        if (printSum) {
//            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }


}
