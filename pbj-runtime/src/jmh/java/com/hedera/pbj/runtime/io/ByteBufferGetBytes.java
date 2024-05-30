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

    @Setup(Level.Trial)
    public void init() {
        heapBuffer = ByteBuffer.allocate(size);
        directBuffer = ByteBuffer.allocateDirect(size);
        for (int i = 0; i < size; i++) {
            heapBuffer.put((byte) (i % 111));
            directBuffer.put((byte) (i % 111));
        }
    }

    // Heap buffer -> byte[] using System.arraycopy()
    @Benchmark
    public void arrayCopy(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        final byte[] src = heapBuffer.array();
        for (int i = 0; i < size - window; i++) {
            System.arraycopy(src, i, dst, 0, window);
            blackhole.consume(dst);
        }
    }

    // Heap buffer -> byte[] using ByteBuffer.get()
    @Benchmark
    public void heapBufferGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            heapBuffer.position(i);
            heapBuffer.get(dst, 0, window);
            blackhole.consume(dst);
        }
    }

    // Direct buffer -> byte[] using ByteBuffer.get()
    @Benchmark
    public void directBufferGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            directBuffer.position(i);
            directBuffer.get(dst, 0, window);
            blackhole.consume(dst);
        }
    }

    // Heap buffer -> byte[] using Unsafe
    @Benchmark
    public void unsafeHeapGet(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getHeapBufferToArray(heapBuffer, i, dst, 0, window);
            blackhole.consume(dst);
        }
    }

    // Direct buffer -> byte[] using Unsafe
    @Benchmark
    public void unsafeDirectBytes(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getDirectBufferToArray(directBuffer, i, dst, 0, window);
            blackhole.consume(dst);
        }
    }

    // Direct buffer -> direct buffer using Unsafe
    @Benchmark
    public void unsafeDirectBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        for (int i = 0; i < size - window; i++) {
            UnsafeUtils.getDirectBufferToDirectBuffer(directBuffer, i, dst, 0, window);
            blackhole.consume(dst);
        }
    }
}
