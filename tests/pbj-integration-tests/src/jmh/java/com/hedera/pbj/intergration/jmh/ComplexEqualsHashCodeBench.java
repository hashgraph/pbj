package com.hedera.pbj.intergration.jmh;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.test.proto.pbj.Hasheval;
import com.hedera.pbj.test.proto.pbj.Suit;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/*
Mac Results

Benchmark                                            Mode  Cnt   Score   Error  Units
ComplexEqualsHashCodeBench.benchJavaRecordHashCode   avgt    5  16.304 ± 0.155  ns/op
ComplexEqualsHashCodeBench.benchHashCode             avgt    5  20.601 ± 0.136  ns/op   --- 1.26x slower

ComplexEqualsHashCodeBench.benchJavaRecordEquals     avgt    5  13.153 ± 0.043  ns/op
ComplexEqualsHashCodeBench.benchEquals               avgt    5  13.141 ± 0.055  ns/op  --- 1.0x slower

ComplexEqualsHashCodeBench.benchJavaRecordNotEquals  avgt    5   8.940 ± 0.052  ns/op
ComplexEqualsHashCodeBench.benchNotEquals            avgt    5   7.497 ± 0.057  ns/op --- 1.19x faster
 */

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ComplexEqualsHashCodeBench {
    public record HashevalJavaRecord(
            int int32Number,
            int sint32Number,
            int uint32Number,
            int fixed32Number,
            int sfixed32Number,
            float floatNumber,
            long int64Number,
            long sint64Number,
            long uint64Number,
            long fixed64Number,
            long sfixed64Number,
            double doubleNumber,
            boolean booleanField,
            Suit enumSuit,
            @Nullable TimestampTest subObject,
            String text,
            Bytes bytesField
    ){}

    private final Hasheval hasheval;
    private final Hasheval hasheval1;
    private final Hasheval hashevalDifferent;
    private final HashevalJavaRecord hashevalJavaRecord;
    private final HashevalJavaRecord hashevalJavaRecord1;
    private final HashevalJavaRecord hashevalJavaRecordDifferent;

    public ComplexEqualsHashCodeBench() {
        hasheval = new Hasheval(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "FooBarKKKKHHHHOIOIOI",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
        hasheval1 = new Hasheval(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "FooBarKKKKHHHHOIOIOI",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
        hashevalDifferent = new Hasheval(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "Different",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
        hashevalJavaRecord = new HashevalJavaRecord(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "FooBarKKKKHHHHOIOIOI",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
        hashevalJavaRecord1 = new HashevalJavaRecord(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "FooBarKKKKHHHHOIOIOI",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
        hashevalJavaRecordDifferent = new HashevalJavaRecord(123, 123, 123,
                123, 123, 1.23f, 123L, 123L,
                123L, 123L, 123L, 1.23D, true,
                Suit.ACES, new TimestampTest(987L, 123),
                "Different",
                Bytes.wrap(new byte[]{1, 2, 3, 4, 5, 6, 7, (byte)255}));
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchHashCode(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hasheval.hashCode());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordHashCode(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hashevalJavaRecord.hashCode());
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hasheval.equals(hasheval1));
        }
    }
    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hashevalJavaRecord.equals(hashevalJavaRecord1));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchNotEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hasheval.equals(hashevalDifferent));
        }
    }

    @Benchmark
    @OperationsPerInvocation(1050)
    public void benchJavaRecordNotEquals(Blackhole blackhole) {
        for (int i = 0; i < 1050; i++) {
            blackhole.consume(hashevalJavaRecord.equals(hashevalJavaRecordDifferent));
        }
    }
}