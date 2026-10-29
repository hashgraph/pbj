// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint;

import com.hedera.pbj.runtime.ProtoArrayWriterTools;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Variants of the single-store byte-array varint writer ({@code ProtoArrayWriterTools.writeVarInt}), against the
 * unrolled writer, to find where its time goes. Each benchmark writes the values one after another, so the position
 * update is a dependency chain, as in a message writer. {@code numOfBytes = 0} mixes lengths from 1 to 5 bytes, as in
 * real messages, so the unrolled writer's branch is not perfectly predicted.
 *
 * <p>On AMD EPYC (Zen 5) every single-store variant was slower than the unrolled writer for 2- to 8-byte varints; only
 * the inline one-byte case was faster. Encoding HAPI block items confirmed it (see the pull request for #988).
 */
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarIntSingleStoreVariantsBench {
    private static final int COUNT = 4096 * 4;
    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final int[] SIZES = new int[65];

    static {
        for (int i = 0; i <= 64; i++) SIZES[i] = (63 - i) / 7 + 1;
    }

    /** Varint length of every value, or 0 for random lengths from 1 to 5, as in real messages. */
    @Param({"0", "1", "2", "3", "4", "5", "8"})
    public int numOfBytes;

    private long[] numbers;
    private byte[] buffer;

    @Setup
    public void setup() {
        final Random random = new Random(9387498731984L);
        numbers = new long[COUNT];
        for (int i = 0; i < COUNT; i++) {
            final int bytes = numOfBytes == 0 ? 1 + random.nextInt(5) : numOfBytes;
            final long min = bytes == 1 ? 0L : 1L << ((bytes - 1) * 7);
            final long max = (1L << (bytes * 7)) - 1;
            numbers[i] = random.nextLong(min, max);
        }
        buffer = new byte[10 * COUNT + 16];
    }

    private static long spread(final long v) {
        return (v & 0x7FL)
                | ((v & 0x3F80L) << 1)
                | ((v & 0x1FC000L) << 2)
                | ((v & 0xFE00000L) << 3)
                | ((v & 0x7F0000000L) << 4)
                | ((v & 0x3F800000000L) << 5)
                | ((v & 0x1FC0000000000L) << 6)
                | ((v & 0xFE000000000000L) << 7);
    }

    /** (b) size from a multiply and shift instead of a divide by 7. */
    static int wideMultiply(final byte[] out, final int offset, final int end, final long v) {
        if ((v >>> 56) != 0 || end - offset < 8) return ProtoArrayWriterTools.writeUnsignedVarInt(out, offset, v);
        final int size = ((Long.SIZE * 9 + 64) - Long.numberOfLeadingZeros(v) * 9) >>> 6;
        LONG_LE.set(out, offset, spread(v) | (0x8080808080808080L & ((1L << ((size - 1) << 3)) - 1)));
        return size;
    }

    /** (c) size from a lookup table. */
    static int wideTable(final byte[] out, final int offset, final int end, final long v) {
        if ((v >>> 56) != 0 || end - offset < 8) return ProtoArrayWriterTools.writeUnsignedVarInt(out, offset, v);
        final int size = SIZES[Long.numberOfLeadingZeros(v)];
        LONG_LE.set(out, offset, spread(v) | (0x8080808080808080L & ((1L << ((size - 1) << 3)) - 1)));
        return size;
    }

    /** (e) Long.expand, a single PDEP instruction on x86-64 with BMI2. */
    static int wideExpand(final byte[] out, final int offset, final int end, final long v) {
        if ((v >>> 56) != 0 || end - offset < 8) return ProtoArrayWriterTools.writeUnsignedVarInt(out, offset, v);
        final int size = SIZES[Long.numberOfLeadingZeros(v)];
        LONG_LE.set(
                out,
                offset,
                Long.expand(v, 0x7F7F7F7F7F7F7F7FL) | (0x8080808080808080L & ((1L << ((size - 1) << 3)) - 1)));
        return size;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int unrolled() {
        int p = 0;
        for (int i = 0; i < COUNT; i++) p += ProtoArrayWriterTools.writeUnsignedVarInt(buffer, p, numbers[i]);
        return p;
    }

    /** (a) as in ProtoArrayWriterTools.writeVarInt. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int singleStore() {
        int p = 0;
        final int end = buffer.length;
        for (int i = 0; i < COUNT; i++) p += ProtoArrayWriterTools.writeVarInt(buffer, p, end, numbers[i]);
        return p;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int singleStoreMultiply() {
        int p = 0;
        final int end = buffer.length;
        for (int i = 0; i < COUNT; i++) {
            final long v = numbers[i];
            if ((v >>> 7) == 0) buffer[p++] = (byte) v;
            else p += wideMultiply(buffer, p, end, v);
        }
        return p;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int singleStoreTable() {
        int p = 0;
        final int end = buffer.length;
        for (int i = 0; i < COUNT; i++) {
            final long v = numbers[i];
            if ((v >>> 7) == 0) buffer[p++] = (byte) v;
            else p += wideTable(buffer, p, end, v);
        }
        return p;
    }

    /** (d) inline one-byte case, unrolled writer otherwise. */
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int oneByteInlineThenUnrolled() {
        int p = 0;
        for (int i = 0; i < COUNT; i++) {
            final long v = numbers[i];
            if ((v >>> 7) == 0) buffer[p++] = (byte) v;
            else p += ProtoArrayWriterTools.writeUnsignedVarInt(buffer, p, v);
        }
        return p;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(COUNT)
    public int singleStoreExpand() {
        int p = 0;
        final int end = buffer.length;
        for (int i = 0; i < COUNT; i++) {
            final long v = numbers[i];
            if ((v >>> 7) == 0) buffer[p++] = (byte) v;
            else p += wideExpand(buffer, p, end, v);
        }
        return p;
    }
}
