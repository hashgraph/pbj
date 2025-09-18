// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint;

import com.hedera.pbj.runtime.ProtoWriterTools;
import java.io.IOException;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
// @Warmup(iterations = 4, time = 2)
// @Measurement(iterations = 5, time = 2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 4, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarInt64SizeOfBench {
    public static final int NUM_OF_VALUES = 1024;
    /**
     * Number of bytes to read at a time (1, 2, 4, or 8). So create inputs with 1 byte siz,e, 2 byte size, 4 byte size,
     * and 8 byte size.
     */
    @Param({"1", "2", "4", "8"})
    public int numOfBytes;

    private long[] numbers;

    @Setup
    public void setupNumbers() {
        Random random = new Random(9387498731984L);
        numbers = new long[NUM_OF_VALUES];
        final long minValue = numOfBytes == 1 ? 0L : 1L << ((numOfBytes - 1) * 7);
        final long maxValue = (1L << (numOfBytes * 7)) - 1;
        // System.out.println("Generating "+NUM_OF_VALUES+" random numbers between "+min
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            this.numbers[i] = random.nextLong(minValue, maxValue);
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void pbjProtoTools(Blackhole blackhole) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) blackhole.consume(ProtoWriterTools.sizeOfVarInt64(numbers[i]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void kafka(Blackhole blackhole) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) blackhole.consume(kafkaSizeOfVarlong(numbers[i]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void google(Blackhole blackhole) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++)
            blackhole.consume(com.google.protobuf.CodedOutputStream.computeInt64SizeNoTag(numbers[i]));
    }

    public static int kafkaSizeOfVarlong(long value) {
        long v = (value << 1) ^ (value >> 63);
        int leadingZeros = Long.numberOfLeadingZeros(v);
        int leadingZerosBelow70DividedBy7 = ((70 - leadingZeros) * 0b10010010010010011) >>> 19;
        return leadingZerosBelow70DividedBy7 + (leadingZeros >>> 6);
    }

    public static int kafkaSizeOfUnsignedVarint(int value) {
        // Protocol buffers varint encoding is variable length, with a minimum of 1 byte
        // (for zero). The values themselves are not important. What's important here is
        // any leading zero bits are dropped from output. We can use this leading zero
        // count w/ fast intrinsic to calc the output length directly.
        // Test cases verify this matches the output for loop logic exactly.
        // return (38 - leadingZeros) / 7 + leadingZeros / 32;
        // The above formula provides the implementation, but the Java encoding is suboptimal
        // when we have a narrow range of integers, so we can do better manually
        int leadingZeros = Integer.numberOfLeadingZeros(value);
        int leadingZerosBelow38DividedBy7 = ((38 - leadingZeros) * 0b10010010010010011) >>> 19;
        return leadingZerosBelow38DividedBy7 + (leadingZeros >>> 5);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(VarInt64SizeOfBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
