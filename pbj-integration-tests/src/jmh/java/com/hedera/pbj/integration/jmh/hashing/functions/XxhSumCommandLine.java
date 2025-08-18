// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing.functions;

import java.io.IOException;

/**
 * Wrapper around the `xxhsum` command line utility to compute a 64-bit hash.
 */
public class XxhSumCommandLine {

    public static long hashXxh_32(final byte[] bytes, int start, int length) {
        final String resultString = xxhsum(0, bytes, start, length);
        final String resultHexString = resultString.substring(0, resultString.indexOf(' '));
        return Long.parseUnsignedLong(resultHexString, 16);
    }

    public static long hashXxh_64(final byte[] bytes, int start, int length) {
        final String resultString = xxhsum(1, bytes, start, length);
        final String resultHexString = resultString.substring(0, resultString.indexOf(' '));
        return Long.parseUnsignedLong(resultHexString, 16);
    }

    public static long[] hashXxh3_128(final byte[] bytes, int start, int length) {
        final String resultString = xxhsum(2, bytes, start, length);
        final String first64bit = resultString.substring(0, 16);
        final String second64bit = resultString.substring(16, 32);
        return new long[] {Long.parseUnsignedLong(first64bit, 16), Long.parseUnsignedLong(second64bit, 16)};
    }

    public static long hashXxh3_64(final byte[] bytes, int start, int length) {
        final String resultString = xxhsum(3, bytes, start, length);
        final String resultHexString = resultString.substring(resultString.indexOf('_') + 1, resultString.indexOf(' '));
        return Long.parseUnsignedLong(resultHexString, 16);
    }

    private static String xxhsum(final int algorithm, final byte[] bytes, int start, int length) {
        ProcessBuilder pb = new ProcessBuilder("xxhsum", "-H" + algorithm, "-");
        Process process = null;
        try {
            process = pb.start();
            // Write input and close output to signal EOF to xxhsum
            try (var out = process.getOutputStream()) {
                out.write(bytes, start, length);
                out.flush();
            }
            // Read result from input stream
            String resultString;
            try (var in = process.getInputStream()) {
                var resultBytes = in.readAllBytes();
                resultString = new String(resultBytes).trim();
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
            return resultString;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        long testHash = hashXxh_32("helloworld".getBytes(), 0, "helloworld".getBytes().length);
        System.out.println("hashXxh_32 = " + testHash);
        testHash = hashXxh_64("helloworld".getBytes(), 0, "helloworld".getBytes().length);
        System.out.println("hashXxh_64 = " + testHash);
        testHash = hashXxh3_64("helloworld".getBytes(), 0, "helloworld".getBytes().length);
        System.out.println("hashXxh3_64 = " + testHash);
        long[] testHash128 = hashXxh3_128("helloworld".getBytes(), 0, "helloworld".getBytes().length);
        System.out.println("hashXxh3_128 = " + testHash128[0] + ", " + testHash128[1]);
    }
}
