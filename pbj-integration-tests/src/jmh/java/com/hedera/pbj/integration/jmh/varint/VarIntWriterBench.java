package com.hedera.pbj.integration.jmh.varint;

import com.hedera.pbj.integration.jmh.varint.writers.GoogleCodedByteArray;
import com.hedera.pbj.integration.jmh.varint.writers.GoogleCodedByteBufferDirect;
import com.hedera.pbj.integration.jmh.varint.writers.GoogleCodedOutputStream;
import com.hedera.pbj.integration.jmh.varint.writers.KafkaByteBuffer;
import com.hedera.pbj.integration.jmh.varint.writers.PbjBufferedData;
import com.hedera.pbj.integration.jmh.varint.writers.PbjBufferedDataDirect;
import com.hedera.pbj.integration.jmh.varint.writers.PbjWritableStreamingData;
import com.hedera.pbj.integration.jmh.varint.writers.RichardStartinByteArray;
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

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarIntWriterBench {
    public static final int NUM_OF_VALUES = 4096*4;
    /**
     * Number of bytes to read at a time (1, 2, 4, or 8). So create inputs with 1 byte siz,e, 2 byte size, 4 byte size,
     * and 8 byte size.
     */
//    @Param({"1", "2", "3", "4", "8"})
    @Param({"4"})
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
    public void googleCodedOutputStream(GoogleCodedOutputStream state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleCodedByteArray(GoogleCodedByteArray state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void googleCodedByteBufferDirect(GoogleCodedByteBufferDirect state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void pbjWritableStreamingData(PbjWritableStreamingData state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void pbjBufferedData(PbjBufferedData state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }
//
//    @Benchmark
//    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
//    @OperationsPerInvocation(NUM_OF_VALUES)
//    public void pbjBufferedDataDirect(PbjBufferedDataDirect state) throws IOException {
//        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
//        state.endLoop();
//    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void richardStartinByteArray(RichardStartinByteArray state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @OperationsPerInvocation(NUM_OF_VALUES)
    public void kafkaByteBuffer(KafkaByteBuffer state) throws IOException {
        for (int i = 0; i < NUM_OF_VALUES; i++) state.writeVarint(numbers[i]);
        state.endLoop();
    }

}
