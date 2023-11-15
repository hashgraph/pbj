package com.hedera.pbj.runtime.io;

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
public class ByteBufferGetBytes {

    @Param({"10000"})
    public int size = 10000;

    @Param({"1000"})
    public int window = 1000;

    private ByteBuffer heapBuffer;
    private ByteBuffer directBuffer;

    private boolean printSum;

    @Setup(Level.Trial)
    public void init() {
        heapBuffer = ByteBuffer.allocate(size);
        directBuffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            heapBuffer.put((byte) (i % 111));
            directBuffer.put((byte) (i % 111));
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

    // Heap buffer -> byte[] using System.arraycopy()
    @Benchmark
    public void arrayCopy(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        final byte[] src = heapBuffer.array();
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            System.arraycopy(src, i, dst, 0, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    // Heap buffer -> byte[] using ByteBuffer.get()
    @Benchmark
    public void heapBufferGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            heapBuffer.position(i);
            heapBuffer.get(dst, 0, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    // Direct buffer -> byte[] using ByteBuffer.get()
    @Benchmark
    public void directBufferGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            directBuffer.position(i);
            directBuffer.get(dst, 0, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    // Heap buffer -> byte[] using Unsafe
    @Benchmark
    public void unsafeHeapGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getHeapBytes(heapBuffer, i, dst, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    // Direct buffer -> byte[] using Unsafe
    @Benchmark
    public void unsafeDirectBytes(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getDirectBytes(directBuffer, i, dst, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    // Direct buffer -> direct buffer using Unsafe
    @Benchmark
    public void unsafeDirectBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        long sum = 0;
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getDirectBytes(directBuffer, i, dst, window);
//            sum += sum(dst);
            blackhole.consume(dst);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }
}
