// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.hashing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class XXH3Test {

    /**
     * This test checks the hash of the string "hello world". The expected hash is computed using the command line
     * tool `xxhsum` with the `-H3` option.
     */
    @Test
    @DisplayName("Test for the string 'hello world'")
    public void helloWorldTest() {
        byte[] inputBytes = "hello world".getBytes();
        // Assuming XXH3.hash() is a method that computes the hash
        long hash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(inputBytes, 0, inputBytes.length);
        // hello world expected hash in hex produced with command line -> echo -n "hello world" | xxhsum -H3
        String expectedHash = "d447b1ea40e6988b";
        assertEquals(expectedHash, Long.toHexString(hash));
    }

    /**
     * This test checks the hash of the byte sequence CAFEBABE, which is often used as a magic number in Java class
     * files.
     */
    @Test
    @DisplayName("Test for the CAFEBABE byte sequence")
    public void cafeBabyTest() {
        byte[] inputBytes = HexFormat.of().parseHex("CAFEBABE");
        // Assuming XXH3.hash() is a method that computes the hash
        long hash = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(inputBytes, 0, inputBytes.length);
        // hello world expected hash in hex produced with command line -> echo CAFEBABE | xxd -r -p | xxhsum -H3
        String expectedHash = "36afb8d0770d97ea";
        assertEquals(expectedHash, Long.toHexString(hash));
    }

    /**
     * This test checks the hash of a large random data set against the `xxhsum` command line tool if it is available.
     * It uses a large number of random byte arrays to ensure that the hash function behaves correctly across a wide
     * range of inputs.
     */
    @Test
    @DisplayName("Test random data against xxhsum if available")
    void testRandomDataAgainstXxhsumIfAvailable() {
        Assumptions.assumeTrue(isXXHSumAvailable(), "xxhsum not available, skipping test");
        // test with a large random data set
        Random random = new Random(18971947891479L);
        final AtomicBoolean allMatch = new AtomicBoolean(true);
        IntStream.range(0, 1_000).parallel().forEach(i -> {
            byte[] randomData = new byte[1 + random.nextInt(128)];
            random.nextBytes(randomData);
            long testCodeHashResult = XXH3_64.DEFAULT_INSTANCE.hashBytesToLong(randomData, 0, randomData.length);
            long referenceExpectedHash = xxh364HashWithCommandLine(randomData, 0, randomData.length);
            assertEquals(
                    referenceExpectedHash,
                    testCodeHashResult,
                    "Mismatch for random data " + i + ": Input: "
                            + HexFormat.of().formatHex(randomData)
                            + ", Expected xxhsum: " + Long.toHexString(referenceExpectedHash)
                            + ", XXH3_64: " + Long.toHexString(testCodeHashResult));
            if (testCodeHashResult != referenceExpectedHash) {
                allMatch.set(false);
            }
        });
        assertTrue(allMatch.get());
    }

    /**
     * This class checks if the `xxhsum` command line tool is available on the system.
     * It does this by trying to execute `xxhsum --version` and checking the exit code.
     */
    public static boolean isXXHSumAvailable() {
        try {
            Process process = new ProcessBuilder("xxhsum", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * This method computes the XXH3-64 hash of the given byte array using the `xxhsum` command line tool.
     * It writes the bytes to the standard input of `xxhsum` and reads the output.
     *
     * @param bytes  The byte array to hash.
     * @param start  The starting index in the byte array.
     * @param length The number of bytes to hash.
     * @return The computed hash as a long value.
     */
    public static long xxh364HashWithCommandLine(final byte[] bytes, int start, int length) {
        String result;
        ProcessBuilder pb = new ProcessBuilder("xxhsum", "-H" + 3, "-");
        Process process;
        try {
            process = pb.start();
            // Write input and close output to signal EOF to xxhsum
            try (var out = process.getOutputStream()) {
                out.write(bytes, start, length);
                out.flush();
            }
            // Read result from input stream
            String resultString1;
            try (var in = process.getInputStream()) {
                var resultBytes = in.readAllBytes();
                resultString1 = new String(resultBytes).trim();
            }
            // Drain error stream to avoid blocking
            try (var err = process.getErrorStream()) {
                var errorBytes = err.readAllBytes();
                if (errorBytes.length > 0) {
                    String errorString = new String(errorBytes).trim();
                    if (!errorString.isEmpty()) {
                        throw new RuntimeException("Error from xxhsum: " + errorString);
                    }
                }
            }
            process.waitFor();
            result = resultString1;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        final String resultString = result;
        final String resultHexString = resultString.substring(resultString.indexOf('_') + 1, resultString.indexOf(' '));
        return Long.parseUnsignedLong(resultHexString, 16);
    }
}
