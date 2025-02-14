// SPDX-License-Identifier: Apache-2.0
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

    @Setup(Level.Trial)
    public void init() {
        heapBuffer = ByteBuffer.allocate(size);
        directBuffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            heapBuffer.put((byte) (i % 111));
            directBuffer.put((byte) (i % 111));
        }
    }

    @Setup(Level.Invocation)
    public void initEachInvocation() {
        heapBuffer.clear();
        directBuffer.clear();
    }

    @Benchmark
    public void heapArrayGet(final Blackhole blackhole) {
        final byte[] array = heapBuffer.array();
        for (int i = 0; i < size; i++) {
            blackhole.consume(array[i]);
        }
    }

    @Benchmark
    public void heapBufferGet(final Blackhole blackhole) {
        for (int i = 0; i < size; i++) {
            blackhole.consume(heapBuffer.get(i));
        }
    }

    @Benchmark
    public void heapBufferRead(final Blackhole blackhole) {
        for (int i = 0; i < size; i++) {
            blackhole.consume(heapBuffer.get());
        }
    }

    @Benchmark
    public void directBufferGet(final Blackhole blackhole) {
        for (int i = 0; i < size; i++) {
            blackhole.consume(directBuffer.get(i));
        }
    }

    @Benchmark
    public void heapUnsafeGet(final Blackhole blackhole) {
        for (int i = 0; i < size; i++) {
            blackhole.consume(UnsafeUtils.getHeapBufferByte(heapBuffer, i));
        }
    }

    @Benchmark
    public void directUnsafeGet(final Blackhole blackhole) {
        for (int i = 0; i < size; i++) {
            blackhole.consume(UnsafeUtils.getDirectBufferByte(directBuffer, i));
        }
    }
}
