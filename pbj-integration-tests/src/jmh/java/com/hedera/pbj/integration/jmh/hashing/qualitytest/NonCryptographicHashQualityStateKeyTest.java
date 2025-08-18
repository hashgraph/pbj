// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.qualitytest;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.pbj.integration.jmh.hashing.CountingArray;
import com.hedera.pbj.integration.jmh.hashing.NonCryptographicHashingBench.HashAlgorithm;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.java.teststate.pbj.integration.tests.StateKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

/**
 * A test to evaluate the quality of non-cryptographic hash functions by checking how many unique hashes can be
 * generated from 4.5 billion StateKey inputs.
 */
public final class NonCryptographicHashQualityStateKeyTest {
    private static final int NUM_BUCKETS = 33_554_432; // 2^25 33 million buckets
    // Where to place result files
    private static final Path OUTPUT_ROOT = Path.of("hash_quality_results");

    public static void main(String[] args) throws Exception {
        final Path outputDir = createOutputDirectory();
        System.out.println("Testing non-cryptographic hash quality - Random StateKeys, 4.5 billion inputs");
        try (ForkJoinPool customPool = new ForkJoinPool(4)) { // limit to 4 threads
            //
            //            customPool.submit(() ->
            //                    Arrays.stream(HashAlgorithm.values())
            ////                            .parallel()
            //                            .forEach(hashAlgorithm -> {
            //                                final CountingArray counts = new CountingArray(); // 4 billion counts
            //                                System.out.println("Testing " + hashAlgorithm.name() + "...");
            //                                try {
            //                                    testHashQuality4Bytes(hashAlgorithm, counts, outputDir);
            //                                } catch (IOException e) {
            //                                    e.printStackTrace();
            //                                    throw new RuntimeException(e);
            //                                }
            //                            })
            //            ).get(); // handle exceptions as needed
            final CountingArray counts = new CountingArray(); // 4 billion counts
            testHashQuality4Bytes(HashAlgorithm.XXH3_64_PBJ, counts, outputDir);
        }
    }

    private static void testHashQuality4Bytes(HashAlgorithm hashAlgorithm, CountingArray counts, final Path outputDir)
            throws IOException {
        final long START_TIME = System.currentTimeMillis();
        final long NUM_INPUTS = 4_500_000_000L; // 4.5 billion inputs
        //        final long NUM_INPUTS = 50_000_000L; // 4.5 billion inputs
        final byte[] bufferArray = new byte[1024];
        final BufferedData bufferedData = BufferedData.wrap(bufferArray);
        final int[] bucketCounts = new int[NUM_BUCKETS]; // 2^25 33 million buckets
        final Random random = new Random(2518643515415654L); // Seed for reproducibility
        long lengthSum = 0;
        long minLength = Integer.MAX_VALUE;
        long maxLength = Integer.MIN_VALUE;

        for (long i = 0; i < NUM_INPUTS; i++) {
            if (i % 10_000_000 == 0) {
                long averageLength = lengthSum / (i + 1);
                System.out.printf(
                        "\r       Progress: %.2f%% Length: avg=%,d, min=%,d, max=%,d",
                        (i * 100.0) / NUM_INPUTS, averageLength, minLength, maxLength);
                System.out.flush();
            }
            // create a sample StateKey that will be hashed
            StateKey stateKey =
                    switch (random.nextInt(4)) {
                        case 0 ->
                            StateKey.newBuilder()
                                    .accountId(AccountID.newBuilder().accountNum(i))
                                    .build();
                        case 1 ->
                            StateKey.newBuilder()
                                    .tokenId(TokenID.newBuilder().tokenNum(i))
                                    .build();
                        case 2 ->
                            StateKey.newBuilder()
                                    .entityIdPair(EntityIDPair.newBuilder()
                                            .accountId(AccountID.newBuilder().accountNum(i))
                                            .tokenId(TokenID.newBuilder().tokenNum(i)))
                                    .build();
                        case 3 ->
                            StateKey.newBuilder()
                                    .nftId(NftID.newBuilder()
                                            .tokenId(TokenID.newBuilder().tokenNum(i))
                                            .serialNumber(random.nextLong(1_000_000)))
                                    .build();
                        default -> throw new IllegalStateException("Unexpected value: ");
                    };
            bufferedData.position(0);
            StateKey.PROTOBUF.write(stateKey, bufferedData);
            int lengthWritten = (int) bufferedData.position();
            lengthSum += lengthWritten;
            if (lengthWritten < minLength) {
                minLength = lengthWritten;
            }
            if (lengthWritten > maxLength) {
                maxLength = lengthWritten;
            }

            final int hash32 = (int) hashAlgorithm.function.applyAsLong(bufferArray, 0, lengthWritten);
            counts.increment(Integer.toUnsignedLong(hash32));
            long bucket = computeBucketIndex(hash32);
            bucketCounts[(int) bucket]++;
        }

        long numUniqueHashes = counts.numberOfGreaterThanZeroCounts();
        long hashCollisions = counts.numberOfGreaterThanOneCounts();
        double collisionRate = (double) hashCollisions / NUM_INPUTS * 100;
        final long END_TIME = System.currentTimeMillis();
        StringBuilder resultStr = new StringBuilder(String.format(
                "%n%s => Number of unique hashes: %,d, hash collisions: %,d, collision rate: %.2f%% time taken: %.3f seconds%n",
                hashAlgorithm.name(),
                numUniqueHashes,
                hashCollisions,
                collisionRate,
                (END_TIME - START_TIME) / 1000.0));
        counts.printStats(resultStr);
        // print the distribution of hash buckets sorted by bucket index
        // convert the bucketCounts into the number of buckets with each count
        Map<String, Integer> bucketDistribution = Arrays.stream(bucketCounts)
                .mapToObj(count -> {
                    if (count == 0) {
                        return "0";
                    } else if (count <= 10) {
                        return "1->10";
                    } else if (count <= 100) {
                        return "11->100";
                    } else if (count <= 1000) {
                        return "101->1,000";
                    } else if (count <= 10000) {
                        return "1,001->10,000";
                    } else if (count <= 100_000) {
                        return "10,001->100,000";
                    } else if (count <= 250_000) {
                        return "100,001->250,000";
                    } else if (count <= 500_000) {
                        return "250,001->500,000";
                    } else {
                        return "500,000+";
                    }
                })
                .collect(java.util.stream.Collectors.toMap(count -> count, count -> 1, Integer::sum));
        resultStr.append("      Bucket distribution: ");
        bucketDistribution.forEach((category, count) -> {
            resultStr.append(String.format("  %s=%,d", category, count));
        });
        resultStr.append("\n");
        // print the total number of buckets
        System.out.print(resultStr);
        System.out.flush();

        // Export detailed per-bucket counts for plotting
        exportBucketCounts(outputDir, hashAlgorithm.name(), bucketCounts, NUM_INPUTS, NUM_BUCKETS);
    }

    /**
     * <p>Code direct from HalfDiskHashMap, only change is NUM_BUCKETS</p>
     *
     * Computes which bucket a key with the given hash falls. Depends on the fact the numOfBuckets
     * is a power of two. Based on same calculation that is used in java HashMap.
     *
     * @param keyHash the int hash for key
     * @return the index of the bucket that key falls in
     */
    private static int computeBucketIndex(final int keyHash) {
        return (NUM_BUCKETS - 1) & keyHash;
    }
    /**
     * Creates a timestamped output directory like:
     *   hash_quality_results/run_YYYYMMDD_HHMMSSZ
     */
    private static Path createOutputDirectory() throws IOException {
        final String ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssX").format(ZonedDateTime.now(ZoneOffset.UTC));
        final Path dir = OUTPUT_ROOT.resolve("run_" + ts);
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Exports the per-bucket counts in a compact binary format and writes a sidecar JSON metadata file.
     *
     * Format:
     * - Data file: <ALG>_counts_i32_le.bin (little-endian 32-bit signed ints), length == numBuckets.
     * - Metadata:  <ALG>.meta.json
     */
    private static void exportBucketCounts(
            final Path outputDir,
            final String algorithmName,
            final int[] bucketCounts,
            final long numInputs,
            final int numBuckets)
            throws IOException {
        final String safeAlg = algorithmName.replaceAll("[^A-Za-z0-9_.-]", "_");
        final Path dataFile = outputDir.resolve(safeAlg + "_counts_i32_le.bin");
        final Path metaFile = outputDir.resolve(safeAlg + ".meta.json");

        // Write binary counts in little-endian in chunks to avoid large buffers
        final int chunkSize = 1_048_576; // 1M ints (~4 MiB)
        try (FileChannel ch = FileChannel.open(
                dataFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            final ByteBuffer buf =
                    ByteBuffer.allocateDirect(chunkSize * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            int written = 0;
            while (written < numBuckets) {
                buf.clear();
                final int end = Math.min(written + chunkSize, numBuckets);
                for (int i = written; i < end; i++) {
                    buf.putInt(bucketCounts[i]);
                }
                buf.flip();
                while (buf.hasRemaining()) {
                    ch.write(buf);
                }
                written = end;
            }
            ch.force(true);
        }

        // Metadata JSON
        final double lambda = (double) numInputs / (double) numBuckets;
        final String metaJson = "{\n" + "  \"algorithm\": \""
                + escapeJson(algorithmName) + "\",\n" + "  \"numBuckets\": "
                + numBuckets + ",\n" + "  \"numInputs\": "
                + numInputs + ",\n" + "  \"hashBits\": 32,\n"
                + "  \"bucketIndexFormula\": \"(NUM_BUCKETS - 1) & hash\",\n"
                + "  \"countsFile\": \""
                + escapeJson(dataFile.getFileName().toString()) + "\",\n" + "  \"countsDtype\": \"int32\",\n"
                + "  \"endianness\": \"little\",\n"
                + "  \"expectedMeanPerBucket\": "
                + String.format("%.6f", lambda) + "\n" + "}\n";
        Files.writeString(
                metaFile,
                metaJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
