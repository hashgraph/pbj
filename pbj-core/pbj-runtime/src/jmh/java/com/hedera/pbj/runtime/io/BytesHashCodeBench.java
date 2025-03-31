// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Random;
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

@SuppressWarnings({"unused", "CallToPrintStackTrace"})
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class BytesHashCodeBench {

    static final Bytes smallBytes;
    static final Bytes mediumBytes;
    static final Bytes largeBytes;

    static {
        final Random random = new Random(6262266);

        byte[] data = new byte[16];
        random.nextBytes(data);
        smallBytes = Bytes.wrap(data);

        data = new byte[16 * 1024];
        random.nextBytes(data);
        mediumBytes = Bytes.wrap(data);

        data = new byte[16 * 1024 * 1024];
        random.nextBytes(data);
        largeBytes = Bytes.wrap(data);
    }

    private void benchHashCode(final Bytes bytes, final Blackhole blackhole) {
        for (int i = 0; i < 100; i++) {
            blackhole.consume(bytes.hashCode());
        }
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void hashSmallBytes(Blackhole blackhole) {
        benchHashCode(smallBytes, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void hashMediumBytes(Blackhole blackhole) {
        benchHashCode(mediumBytes, blackhole);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void hashLargeBytes(Blackhole blackhole) {
        benchHashCode(largeBytes, blackhole);
    }
}
