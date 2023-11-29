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
public class ByteBufferGetByte {

    @Param({"10000"})
    public int size = 10000;

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

    @Setup(Level.Invocation)
    public void initEachInvocation() {
        heapBuffer.clear();
        directBuffer.clear();
    }

    @Benchmark
    public void heapArrayGet(final Blackhole blackhole) {
        long sum = 0;
        final byte[] array = heapBuffer.array();
        for (int i = 0; i < size; i++) {
//            sum += array[i];
            blackhole.consume(array[i]);
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapBufferGet(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
//            sum += heapBuffer.get(i);
            blackhole.consume(heapBuffer.get(i));
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapBufferRead(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
//            sum += heapBuffer.get();
            blackhole.consume(heapBuffer.get());
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directBufferGet(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
//            sum += directBuffer.get(i);
            blackhole.consume(directBuffer.get(i));
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void heapUnsafeGet(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
//            sum += UnsafeUtils.getByteHeap(heapBuffer, i);
            blackhole.consume(UnsafeUtils.getByteHeap(heapBuffer, i));
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

    @Benchmark
    public void directUnsafeGet(final Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < size; i++) {
//            sum += UnsafeUtils.getByteDirect(directBuffer, i);
            blackhole.consume(UnsafeUtils.getByteDirect(directBuffer, i));
        }
        if (printSum) {
            System.out.println("sum = " + sum);
            printSum = false;
        }
        blackhole.consume(sum);
    }

}
