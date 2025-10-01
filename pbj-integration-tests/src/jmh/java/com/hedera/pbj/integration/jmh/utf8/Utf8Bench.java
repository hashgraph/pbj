// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.utf8;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks three implementations: Utf8ToolsV1, Utf8ToolsV2, Utf8ToolsV3.
 *
 * <p>Each implementation must provide:</p>
 * <pre>
 *   public final class Utf8ToolsV* {
 *       // private static int encodedLength(String) throws IOException {...} // (not used here)
 *       public static String decodeUtf8(byte[] in, int offset, int length) throws java.io.IOException;
 *       public static void encodeUtf8(String in, byte[] out, int offset) throws java.io.IOException;
 *   }
 * </pre>
 * <p>We precompute input strings and their UTF-8 byte[] using the JDK encoder in @Setup,
 * and we preallocate output buffers sized to the exact UTF-8 length so the measured
 * methods do not allocate (other than what the implementation itself does).</p>
 *
 * <p>Make sure you run with JVM arg <code>--add-opens java.base/java.lang=ALL-UNNAMED</code></p>
 */
@SuppressWarnings("SameParameterValue")
@BenchmarkMode({Mode.AverageTime}) // ops/sec; switch to SampleTime if you want latency histograms
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1)
@State(Scope.Thread)
public class Utf8Bench {
    // -------------------- Parameters --------------------

    /** Which implementation to run (maps to class switch below). */
    @Param({"V0", "V1", "V2", "V3", "V4"})
    //    @Param({"V4"})
    public String impl;

    /** Dataset shape: ASCII-heavy, Latin-1, Mixed BMP, Emoji (surrogates). */
    @Param({"ascii", "latin1", "mixed", "emoji"})
    //    @Param({"ascii"})
    public String dataset;

    /** Mean string length to generate. Actual strings vary around this length. */
    @Param({"8", "32", "100"})
    public int meanLen;

    /** Number of distinct strings in the corpus (cycled during the run). */
    @Param({"1024"})
    public int corpusSize;

    // -------------------- Corpus --------------------

    private String[] strings; // inputs for encode & round-trip
    private byte[][] utf8Bytes; // pre-encoded with JDK for decode benchmark
    private byte[][] encodeBuffers; // sized exactly to utf8 length
    private int[] utf8Lens; // lengths of utf8Bytes[i]
    private int[] utf8LensJdk; // for correctness check of encodedLength
    private int idxMask; // for cheap modulo (power-of-two corpus)

    private final Random rnd = new Random(4141684161512124L);

    public static void main(String[] args) throws Exception {
        Options opt =
                new OptionsBuilder().include(Utf8Bench.class.getSimpleName()).build();

        new Runner(opt).run();
    }

    // -------------------- Lifecycle --------------------

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        // Ensure corpusSize is a power of two for cheap cycling
        int pow2 = 1;
        while (pow2 < corpusSize) pow2 <<= 1;
        if (pow2 != corpusSize) {
            corpusSize = pow2; // silently round up
        }
        idxMask = corpusSize - 1;

        strings = new String[corpusSize];
        utf8Bytes = new byte[corpusSize][];
        utf8Lens = new int[corpusSize];
        utf8LensJdk = new int[corpusSize];
        encodeBuffers = new byte[corpusSize][];

        // Generate corpus
        for (int i = 0; i < corpusSize; i++) {
            String s =
                    switch (dataset) {
                        case "ascii" -> genAsciiString(meanLen, 0.50);
                        case "latin1" -> genLatin1String(meanLen, 0.50); // includes bytes 0x80..0xFF (→ 2-byte UTF-8)
                        case "mixed" -> genMixedBmpString(meanLen, 0.30, 0.10); // some non-ASCII BMP
                        case "emoji" -> genEmojiString(meanLen, 0.15); // surrogate pairs sprinkled in
                        default -> throw new IllegalArgumentException("Unknown dataset: " + dataset);
                    };
            strings[i] = s;

            // Pre-encode with the JDK for decode() input and for sizing encode buffers.
            byte[] u = s.getBytes(StandardCharsets.UTF_8);
            utf8Bytes[i] = u;
            utf8Lens[i] = u.length;
            utf8LensJdk[i] = u.length;
            encodeBuffers[i] = new byte[u.length]; // exact size; offset=0 in benchmarks
        }

        // Quick sanity: round-trip each impl once to catch broken code before timing
        for (String version : new String[] {"V1", "V2", "V3"}) {
            for (int i = 0; i < Math.min(corpusSize, 128); i++) {
                String s = strings[i];
                byte[] buf = new byte[utf8Lens[i]];
                encode(version, s, buf, 0);
                String back = decode(version, buf, 0, buf.length);
                if (!s.equals(back)) {
                    throw new IllegalStateException(version + " failed round-trip on sample " + i);
                }
            }
        }

        // Sanity: encodedLength must match JDK UTF-8 byte length
        for (String version : new String[] {"V1", "V2", "V3"}) {
            for (int i = 0; i < Math.min(corpusSize, 1024); i++) {
                int len = strings[i].getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (len != utf8LensJdk[i]) {
                    throw new IllegalStateException(version + " encodedLength mismatch at " + i
                            + " expected=" + utf8LensJdk[i] + " got=" + len
                            + " str=\"" + preview(strings[i]) + "\"");
                }
            }
        }
    }

    // -------------------- Benchmarks --------------------

    private int cursor = 0;

    /** Encode String -> UTF-8 bytes into a pre-sized buffer. */
    @Benchmark
    public void encode(Blackhole bh) throws Exception {
        final int i = (cursor++) & idxMask;
        final String s = strings[i];
        final byte[] out = encodeBuffers[i];
        encode(impl, s, out, 0);
        // Consume a couple of bytes to keep JIT honest (avoid DCE):
        bh.consume(out[0]);
        bh.consume(out[out.length - 1]);
    }

    /** Decode UTF-8 bytes -> String (input was pre-encoded by the JDK). */
    @Benchmark
    public void decode(Blackhole bh) throws Exception {
        final int i = (cursor++) & idxMask;
        final byte[] in = utf8Bytes[i];
        final String s = decode(impl, in, 0, in.length);
        // Consume length & first char (if present) to avoid DCE:
        bh.consume(s.length());
        if (!s.isEmpty()) bh.consume(s.charAt(0));
    }

    /** Round-trip using the impl for both encode and decode (avoids JDK encoder in timed region). */
    @Benchmark
    public void roundTrip(Blackhole bh) throws Exception {
        final int i = (cursor++) & idxMask;
        final String s = strings[i];
        final byte[] buf = encodeBuffers[i];
        encode(impl, s, buf, 0);
        final String back = decode(impl, buf, 0, buf.length);
        bh.consume(back.length());
    }

    /** Measure just the UTF-8 length computation (no encoding). */
    @Benchmark
    public void encodedLength(Blackhole bh) {
        final int i = (cursor++) & idxMask;
        final String s = strings[i];
        final int len = encodedLength(impl, s);
        // consume value and a char to discourage CSE / constant folding
        bh.consume(len);
        if (!s.isEmpty()) bh.consume(s.charAt(0));
    }

    // -------------------- Dispatch to implementations --------------------

    // Replace these with your real classes (same static method signatures).
    // Example: Utf8ToolsV1.encodeUtf8(in, out, off);
    private static void encode(String version, String in, byte[] out, int off) throws Exception {
        switch (version) {
            case "V0" -> Utf8ToolsV0.encodeUtf8(in, out, off);
            case "V1" -> Utf8ToolsV1.encodeUtf8(in, out, off);
            case "V2" -> Utf8ToolsV2.encodeUtf8(in, out, off);
            case "V3" -> Utf8ToolsV3.encodeUtf8(in, out, off);
            case "V4" -> Utf8ToolsV4.encodeUtf8(in, out, off);
            default -> throw new IllegalArgumentException(version);
        }
    }

    private static String decode(String version, byte[] in, int off, int len) throws Exception {
        return switch (version) {
            case "V0" -> Utf8ToolsV0.decodeUtf8(in, off, len);
            case "V1" -> Utf8ToolsV1.decodeUtf8(in, off, len);
            case "V2" -> Utf8ToolsV2.decodeUtf8(in, off, len);
            case "V3" -> Utf8ToolsV3.decodeUtf8(in, off, len);
            case "V4" -> Utf8ToolsV4.decodeUtf8(in, off, len);
            default -> throw new IllegalArgumentException(version);
        };
    }

    private static int encodedLength(String version, String s) {
        try {
            return switch (version) {
                case "V0" -> Utf8ToolsV0.encodedLength(s);
                case "V1" -> Utf8ToolsV1.encodedLength(s);
                case "V2" -> Utf8ToolsV2.encodedLength(s);
                case "V3" -> Utf8ToolsV3.encodedLength(s);
                case "V4" -> Utf8ToolsV4.encodedLength(s);
                default -> throw new IllegalArgumentException(version);
            };
        } catch (java.io.IOException e) {
            // Treat malformed handling as a failure in correctness check
            throw new RuntimeException(e);
        }
    }

    // -------------------- Generators (fast & simple; deterministic-ish) --------------------

    private String genAsciiString(int mean, double punctRatio) {
        int len = jitteredLen(mean);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            if (rnd.nextDouble() < punctRatio) {
                sb.append(" .,-_/+[]()".charAt(rnd.nextInt(11)));
            } else {
                char c = (char) ('a' + rnd.nextInt(26));
                if (rnd.nextBoolean()) c = Character.toUpperCase(c);
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String genLatin1String(int mean, double highRatio) {
        int len = jitteredLen(mean);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            if (rnd.nextDouble() < highRatio) {
                // 0x80..0xFF (valid Latin-1; forces 2-byte UTF-8)
                sb.append((char) (0x80 + rnd.nextInt(0x80)));
            } else {
                sb.append((char) (' ' + rnd.nextInt(95))); // ASCII printable
            }
        }
        return sb.toString();
    }

    private String genMixedBmpString(int mean, double nonAsciiRatio, double threeByteRatio) {
        int len = jitteredLen(mean);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            double r = rnd.nextDouble();
            if (r < nonAsciiRatio) {
                if (r < threeByteRatio) {
                    // 3-byte UTF-8 BMP range excluding surrogates (e.g., Greek/Cyrillic)
                    char c = (char) (0x0800 + rnd.nextInt(0xD7FF - 0x0800));
                    sb.append(c);
                } else {
                    // Latin-1 high bytes (2-byte UTF-8)
                    sb.append((char) (0x80 + rnd.nextInt(0x80)));
                }
            } else {
                sb.append((char) (' ' + rnd.nextInt(95)));
            }
        }
        return sb.toString();
    }

    private String genEmojiString(int mean, double emojiRatio) {
        int len = jitteredLen(mean);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            if (rnd.nextDouble() < emojiRatio) {
                // A few common emoji code points (U+1F3xx / U+1F60x / U+1F9xx)
                int[] cps = {0x1F600, 0x1F602, 0x1F603, 0x1F60D, 0x1F680, 0x1F64C, 0x1F4AF, 0x1F3C3, 0x1F9E9};
                int cp = cps[rnd.nextInt(cps.length)];
                sb.appendCodePoint(cp);
            } else {
                sb.append((char) (' ' + rnd.nextInt(95)));
            }
        }
        return sb.toString();
    }

    private int jitteredLen(int mean) {
        // ±25% jitter around mean, min 1
        int span = Math.max(1, mean / 4);
        return Math.max(1, mean - span + rnd.nextInt(2 * span + 1));
    }

    private static String preview(String s) {
        if (s.length() <= 24) return s;
        return s.substring(0, 24) + "…(" + s.length() + ")";
    }
}
