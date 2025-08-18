// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import com.hedera.pbj.integration.jmh.hashing.functions.CityHash;
import com.hedera.pbj.integration.jmh.hashing.functions.CityHashUnsafe;
import com.hedera.pbj.integration.jmh.hashing.functions.CityHashVarHandle;
import com.hedera.pbj.integration.jmh.hashing.functions.FarmHash;
import com.hedera.pbj.integration.jmh.hashing.functions.Guava;
import com.hedera.pbj.integration.jmh.hashing.functions.Hash4j;
import com.hedera.pbj.integration.jmh.hashing.functions.HighwayHash;
import com.hedera.pbj.integration.jmh.hashing.functions.JavaStyleHashing;
import com.hedera.pbj.integration.jmh.hashing.functions.LeemonMurmur;
import com.hedera.pbj.integration.jmh.hashing.functions.LuceneMurmur3;
import com.hedera.pbj.integration.jmh.hashing.functions.Md5;
import com.hedera.pbj.integration.jmh.hashing.functions.MetroHash64;
import com.hedera.pbj.integration.jmh.hashing.functions.Murmur3Fast;
import com.hedera.pbj.integration.jmh.hashing.functions.Murmur3OpenHFT;
import com.hedera.pbj.integration.jmh.hashing.functions.MurmurHash3;
import com.hedera.pbj.integration.jmh.hashing.functions.OlegHash;
import com.hedera.pbj.integration.jmh.hashing.functions.RapidHash3;
import com.hedera.pbj.integration.jmh.hashing.functions.Sha256;
import com.hedera.pbj.integration.jmh.hashing.functions.XXH3OpenHFT;
import com.hedera.pbj.integration.jmh.hashing.functions.XXH3OpenHFT2;
import com.hedera.pbj.integration.jmh.hashing.functions.XxHash;
import com.hedera.pbj.integration.jmh.hashing.functions.XxHashRichard;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3AiCPort;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3Lz4;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3ai;
import com.hedera.pbj.runtime.NonCryptographicHashing;
import com.hedera.pbj.runtime.hashing.XXH3_64;
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
@Warmup(iterations = 6, time = 2)
@Measurement(iterations = 4, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class NonCryptographicHashingBench {
    public static final int SAMPLES = 10_000;

    public enum HashAlgorithm {
        MURMUR_3_FAST(Murmur3Fast::hash64),
        FARM_HASH(FarmHash::hash64),
        METRO_HASH(MetroHash64::hash64),
        MURMUR_OPENHFT(Murmur3OpenHFT::hash64),
        LEEMON_MURMUR(LeemonMurmur::hash64),
        GUAVA_FARM_HASH(Guava::farmHash),
        XXH3_OHFT2(XXH3OpenHFT2::hash64),
        HIGHWAY_HASH_GOOGLE(HighwayHash::hash64),
        LEEMON_64(NonCryptographicHashing::hash64),
        LEEMON_64_XOR_32(NonCryptographicHashing::hash64xor32),
        LEEMON_64_UPPER_32(NonCryptographicHashing::hash64upper32),
        CITY_HASH(CityHash::cityHash64),
        CITY_HASH_UNSAFE(CityHashUnsafe::cityHash64),
        CITY_HASH_VAR(CityHashVarHandle::cityHash64),
        LEEMON_32(NonCryptographicHashing::hash32),
        MURMUR_HASH_3_32(MurmurHash3::murmurhash3_x86_32),
        OLEG_32(OlegHash::hash32),
        OLEG_32_2(OlegHash::hash32_2),
        OLEG_64(OlegHash::hash64),
        JAVA_31(JavaStyleHashing::hash31),
        JAVA_255(JavaStyleHashing::hash255),
        JAVA_256(JavaStyleHashing::hash256),
        JAVA_257(JavaStyleHashing::hash257),
        XXHASH_32(XxHash::xxHashCode),
        XXHASH_RICHARD(XxHashRichard::hash),
        XXHASH_64(XxHash::xxHashCodeFast),
        XXH3_AI(Xxh3ai::xxh3HashCode),
        XXH3_OHFT(XXH3OpenHFT::hash64),
        XXH3_AI_C_PORT(Xxh3AiCPort::xxh3_64bits),
        RAPID_HASH_3(RapidHash3::hashBytesToLong),
        SHA_256(Sha256::hash32),
        MD5(Md5::hash32),
        MURMUR_3_32_GUAVA(Guava::murmurhash3_x86_32),
        SIP_24_GUAVA(Guava::sipHash24),
        LUCENE_MURMUR3(LuceneMurmur3::murmurhash3_x86_32),
        LUCENE_MURMUR3_128(LuceneMurmur3::murmurhash3_x64_128),
        XXH64_LZ4_JAVA(Xxh3Lz4::xxh_64bits_java),
        XXH64_LZ4_NATIVE(Xxh3Lz4::xxh_64bits_native),
        FARM_HASH_NA_HASH4J(Hash4j::hash_farm_hash),
        FARM_HASH_UO_HASH4J(Hash4j::hash_farm_hash_uo),
        XXH3_64_HASH4J(Hash4j::hash_xxh3_64),
        MURMUR3_HASH4J(Hash4j::hash_murmur_3_32),
        XXH3_64_PBJ(XXH3_64::hash_xxh3_64),
        ;

        public final HashFunction function;

        HashAlgorithm(HashFunction function) {
            this.function = function;
        }
    }

    @Param({"4", "8", "9", "12", "40", "60", "1000"})
    public int dataSize;

    @Param({
        "MURMUR_3_FAST",
        "FARM_HASH",
        "METRO_HASH",
        "MURMUR_OPENHFT",
        "LEEMON_MURMUR",
        "GUAVA_FARM_HASH",
        "XXH3_OHFT2",
        "HIGHWAY_HASH_GOOGLE",
        "LEEMON_64",
        "LEEMON_64_XOR_32",
        "LEEMON_64_UPPER_32",
        "CITY_HASH",
        "CITY_HASH_UNSAFE",
        "CITY_HASH_VAR",
        "LEEMON_32",
        "MURMUR_HASH_3_32",
        "OLEG_32",
        "OLEG_32_2",
        "OLEG_64",
        "JAVA_31",
        "JAVA_255",
        "JAVA_256",
        "JAVA_257",
        "XXHASH_32",
        "XXHASH_RICHARD",
        "XXHASH_64",
        "XXH3_AI",
        "XXH3_OHFT",
        "RAPID_HASH_3",
        "SHA_256",
        "MD5",
        "MURMUR_3_32_GUAVA",
        "SIP_24_GUAVA",
        "LUCENE_MURMUR3",
        "LUCENE_MURMUR3_128",
        "XXH3_AI_C_PORT",
        "XXH64_LZ4_JAVA",
        "XXH64_LZ4_NATIVE",
        "FARM_HASH_NA_HASH4J",
        "FARM_HASH_UO_HASH4J",
        "XXH3_64_HASH4J",
        "MURMUR3_HASH4J",
        "XXH3_64_PBJ",
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
    @OperationsPerInvocation(SAMPLES)
    public void testHashing(Blackhole blackhole) {
        long sum = 0;
        for (final byte[] bytes : sampleBytes) {
            long hash = hashAlgorithm.function.applyAsLong(bytes, 0, dataSize);
            sum += hash;
        }
        blackhole.consume(sum);
    }
}
