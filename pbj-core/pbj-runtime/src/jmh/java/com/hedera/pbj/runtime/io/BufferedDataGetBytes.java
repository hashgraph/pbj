// SPDX-License-Identifier: Apache-2.0
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

    @Setup(Level.Trial)
    public void init() {
        heapData = BufferedData.allocate(size);
        directData = BufferedData.allocateOffHeap(size);
        for (int i = 0; i < size; i++) {
            heapData.writeByte((byte) (i % 111));
            directData.writeByte((byte) (i % 111));
        }
    }

    @Benchmark
    public void heapToByteArray(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void heapToHeapByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocate(window);
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void heapToDirectByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        for (int i = 0; i < size - window; i++) {
            heapData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void heapToBytes(final Blackhole blackhole) {
        for (int i = 0; i < size - window; i++) {
            final Bytes bytes = heapData.getBytes(i, window);
            blackhole.consume(bytes);
        }
    }

    @Benchmark
    public void directToByteArray(final Blackhole blackhole) {
        final byte[] dst = new byte[window];
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void directToHeapByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocate(window);
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void directToDirectByteBuffer(final Blackhole blackhole) {
        final ByteBuffer dst = ByteBuffer.allocateDirect(window);
        for (int i = 0; i < size - window; i++) {
            directData.getBytes(i, dst);
            blackhole.consume(dst);
        }
    }

    @Benchmark
    public void directToBytes(final Blackhole blackhole) {
        for (int i = 0; i < size - window; i++) {
            final Bytes bytes = directData.getBytes(i, window);
            blackhole.consume(bytes);
        }
    }
}
