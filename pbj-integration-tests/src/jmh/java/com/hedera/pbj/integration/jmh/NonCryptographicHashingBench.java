// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.jmh.hashing.CityHash;
import com.hedera.pbj.integration.jmh.hashing.FasterLeemon;
import com.hedera.pbj.integration.jmh.hashing.HashFunction;
import com.hedera.pbj.integration.jmh.hashing.JavaStyleHashing;
import com.hedera.pbj.integration.jmh.hashing.XxHash;
import com.hedera.pbj.integration.jmh.hashing.Xxh3;
import com.hedera.pbj.runtime.NonCryptographicHashing;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
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
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class NonCryptographicHashingBench {
    public static final int SAMPLES = 10_000;

    public enum HashAlgorithm {
        LEEMON(NonCryptographicHashing::hash64),
        FASTER_LEEMON(FasterLeemon::hash64),
        JAVA_31(JavaStyleHashing::hash31),
        JAVA_255(JavaStyleHashing::hash255),
        JAVA_256(JavaStyleHashing::hash256),
        XXHASH_32(XxHash::xxHashCode),
        XXHASH_64(XxHash::xxHashCodeFast),
        XXH3(Xxh3::xxh3HashCode),
        CITY_HASH(CityHash::cityHash64);
        public final HashFunction function;

        HashAlgorithm(HashFunction function) {
            this.function = function;
        }
    }

    @Param({"4", "8", "9", "12", "40", "60", "1000"})
    public int dataSize;

    @Param({"LEEMON", "FASTER_LEEMON", "JAVA_31", "JAVA_255", "JAVA_256", "XXHASH_32", "XXHASH_64", "XXH3", "CITY_HASH"
    })
    public HashAlgorithm hashAlgorithm;

    private Random random;
    private List<byte[]> sampleBytes;

    @Setup(Level.Trial)
    public void setup() {
        random = new Random(6351384163846453326L);
        sampleBytes = IntStream.range(0, SAMPLES)
                .mapToObj(i -> {
                    final byte[] bytes = new byte[dataSize];
                    random.nextBytes(bytes);
                    return bytes;
                })
                .distinct()
                .toList();
    }

    @Benchmark
    public void testHashing(Blackhole blackhole) {
        long sum = 0;
        for (final byte[] bytes : sampleBytes) {
            long hash = hashAlgorithm.function.applyAsLong(bytes, 0, dataSize);
            sum += hash;
        }
        blackhole.consume(sum);
    }
}
