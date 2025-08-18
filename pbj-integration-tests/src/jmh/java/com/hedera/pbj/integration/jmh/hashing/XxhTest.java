// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import com.hedera.pbj.integration.jmh.hashing.functions.XXH3OpenHFT;
import com.hedera.pbj.integration.jmh.hashing.functions.XXH3OpenHFT2;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3AiCPort;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3Lz4;
import com.hedera.pbj.integration.jmh.hashing.functions.Xxh3ai;
import com.hedera.pbj.integration.jmh.hashing.functions.XxhSumCommandLine;
import com.hedera.pbj.runtime.hashing.XXH3_64;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

public class XxhTest {
    public static void main3(String[] args) {
        // test with a large random data set
        Random random = new Random(18971947891479L);
        final AtomicBoolean allMatch = new AtomicBoolean(true);
        IntStream.range(0, 5_000)
                                .parallel()
                .forEach(i -> {
                    byte[] randomData = new byte[1 + random.nextInt(50)];
                    //            byte[] randomData = new byte[1 + random.nextInt(10)];
                    random.nextBytes(randomData);
                    long testCodeHashResult = XXH3_64.hash_xxh3_64(randomData, 0, randomData.length);
                    long referenceExpectedHash = XxhSumCommandLine.hashXxh3_64(randomData, 0, randomData.length);
                    if (testCodeHashResult != referenceExpectedHash) {
                        System.err.printf(
                                "Mismatch for random data %d: Input: %s, Expected xxhsum: %016x, Xxh3AiCPort: %016x %n",
                                i, HexFormat.of().formatHex(randomData), referenceExpectedHash, testCodeHashResult);
                        allMatch.set(false);
                    }
                });
        if (allMatch.get()) {
            System.out.println("All random data hashes match!");
        } else {
            System.err.println("Some random data hashes did not match!");
        }
    }

    public static void main(String[] args) {
        // compare hashes with other implementations
        byte[] data = "hello world".getBytes();
        System.out.println("Input data: " + HexFormat.of().formatHex(data));
        long hash64 = Xxh3AiCPort.xxh3_64bits(data, 0, data.length);
        long hash64_lz4_java = Xxh3Lz4.xxh_64bits_java(data, 0, data.length);
        long hash64_lz4_native = Xxh3Lz4.xxh_64bits_native(data, 0, data.length);
        long hash64ai = Xxh3ai.xxh3HashCode(data, 0, data.length);
        long hash64OpenHFT = XXH3OpenHFT.hash64(data, 0, data.length);
        long hash64OpenHFT2 = XXH3OpenHFT2.hash64(data, 0, data.length);
        long hashSumXxh_64 = XxhSumCommandLine.hashXxh_64(data, 0, data.length);
        long hashSumXxh3_64 = XxhSumCommandLine.hashXxh3_64(data, 0, data.length);
        long hashXxh3Pbj = XXH3_64.hash_xxh3_64(data, 0, data.length);
        // print hashes in hex
        System.out.printf("XXH3 64-bit hash:                %016x%n", hash64);
        System.out.printf("XXH3 64-bit hash (LZ4 Java):     %016x%n", hash64_lz4_java);
        System.out.printf("XXH3 64-bit hash (LZ4 Native):   %016x%n", hash64_lz4_native);
        System.out.printf("XXH3 64-bit ai hash:             %016x%n", hash64ai);
        System.out.printf("XXH3 OpenHFT 64-bit hash:        %016x%n", hash64OpenHFT);
        System.out.printf("XXH3 OpenHFT2 64-bit hash:       %016x%n", hash64OpenHFT2);
        System.out.printf("XXH3 xxhsum 64-bit hash:         %016x%n", hashSumXxh_64);
        System.out.printf("XXH3 xxhsum 64-bit hash (XXH3):  %016x%n", hashSumXxh3_64);
        System.out.printf("XXH3 PBJ 64-bit hash:            %016x%n", hashXxh3Pbj);

        // test with a large random data set
        Random random = new Random(18971947891479L);
        for (int i = 0; i < 10; i++) {
            byte[] randomData = new byte[1 + random.nextInt(1023)];
            random.nextBytes(randomData);
            long hash64Random = Xxh3AiCPort.xxh3_64bits(randomData, 0, randomData.length);
            long hash64aiRandom = Xxh3ai.xxh3HashCode(randomData, 0, randomData.length);
            long hash64OpenHFTRandom = XXH3OpenHFT.hash64(randomData, 0, randomData.length);
            long hash64OpenHFT2Random = XXH3OpenHFT2.hash64(randomData, 0, randomData.length);
            long hashSumXxh_64Random = XxhSumCommandLine.hashXxh_64(randomData, 0, randomData.length);
            long hashSumXxh3_64Random = XxhSumCommandLine.hashXxh3_64(randomData, 0, randomData.length);
            System.out.printf(
                    "Random data %d: expected xxh64: %016x expected xxh3_64: %016x -- XXH3 64-bit: %016x, ai: %016x, OpenHFT: %016x, OpenHFT2: %016x%n",
                    i,
                    hashSumXxh_64Random,
                    hashSumXxh3_64Random,
                    hash64Random,
                    hash64aiRandom,
                    hash64OpenHFTRandom,
                    hash64OpenHFT2Random);
        }
        final AtomicBoolean allMatch = new AtomicBoolean(true);
        IntStream.range(0, 100).parallel().forEach(i -> {
            byte[] randomData = new byte[1 + random.nextInt(1023)];
            random.nextBytes(randomData);
            long hash64OpenHFT2Random = XXH3OpenHFT2.hash64(randomData, 0, randomData.length);
            long hashSumXxh3_64Random = XxhSumCommandLine.hashXxh3_64(randomData, 0, randomData.length);
            if (hash64OpenHFT2Random != hashSumXxh3_64Random) {
                System.err.printf(
                        "Mismatch for random data %d: Input: %s, Expected xxhsum: %016x, OpenHFT2: %016x %n",
                        i, HexFormat.of().formatHex(randomData), hashSumXxh3_64Random, hash64OpenHFT2Random);
                allMatch.set(false);
            }
        });
        if (allMatch.get()) {
            System.out.println("All random data hashes match!");
        } else {
            System.err.println("Some random data hashes did not match!");
        }
    }
}
