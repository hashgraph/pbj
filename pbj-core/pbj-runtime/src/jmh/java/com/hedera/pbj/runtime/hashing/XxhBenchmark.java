// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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

/**
 * Benchmark for XXH3_64 hashing functions.
 * This benchmark tests the performance of hashing byte arrays and strings using the XXH3_64
 * hashing algorithm.
 */
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class XxhBenchmark {
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final int SAMPLES = 10_000;


    @Param({"4","8","16","32","48","64","120","1024"})
    public int length = 10000;

    private final byte[][] byteInputData = new byte[SAMPLES][];
    private final String[] stringInputData = new String[SAMPLES];

    @Setup(Level.Trial)
    public void init() {
        final Random random = new Random(45155315113511L);
        for (int i = 0; i < SAMPLES; i++) {
            // byte[]
            byteInputData[i] = new byte[length];
            random.nextBytes(byteInputData[i]);
            // string
            StringBuilder builder = new StringBuilder(length);
            for (int j = 0; j < length; j++) {
                builder.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
            }
            stringInputData[i] =  builder.toString();
        }
    }

    @Benchmark
    @OperationsPerInvocation(SAMPLES)
    public void testBytesHashing(final Blackhole blackhole) {
        for (int i = 0; i < SAMPLES; i++) {
            blackhole.consume(XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(byteInputData[i], 0, byteInputData[i].length));
        }
    }

    @Benchmark
    @OperationsPerInvocation(SAMPLES)
    public void testStringHashing(final Blackhole blackhole) {
        for (int i = 0; i < SAMPLES; i++) {
            blackhole.consume(XXH3_64.DEFAULT_INSTANCE.hashCharsToLong(stringInputData[i]));
        }
    }
}
