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

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 4, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarInt32SizeOfBench {
    public static final int NUM_OF_VALUES = 1024;
    @Param({"1", "2", "4"})
    public int numOfBytes;
    private int[] numbers;

    @Setup
    public void setupNumbers() {
        Random random = new Random(9387498731984L);
        numbers = new int[NUM_OF_VALUES];
        final int minValue = numOfBytes == 1 ? 0 : 1 << ((numOfBytes - 1) * 7);
        final int maxValue = (1 << (numOfBytes * 7)) - 1;
        for (int i = 0; i < NUM_OF_VALUES; i++) {
            this.numbers[i] = random.nextInt(minValue, maxValue);
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void pbjProtoTools(Blackhole blackhole) {
        for (int i = 0; i < NUM_OF_VALUES; i++) blackhole.consume(ProtoWriterTools.sizeOfUnsignedVarInt32(numbers[i]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void kafka(Blackhole blackhole) {
        for (int i = 0; i < NUM_OF_VALUES; i++) blackhole.consume(kafkaSizeOfUnsignedVarint(numbers[i]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void google(Blackhole blackhole) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) blackhole.consume(com.google.protobuf.CodedOutputStream
                .computeInt32SizeNoTag(numbers[i]));
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
}
