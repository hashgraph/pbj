// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh;

import com.hedera.pbj.integration.EverythingTestData;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.SlimBuffer;
import com.hedera.pbj.runtime.io.SlimWriter;
import com.hedera.pbj.test.proto.pbj.Everything;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
 * Benchmarks the cost of parse rejection under a mixed stream of valid and malformed
 * Everything messages, simulating a DoS attacker sending high volumes of broken packets.
 *
 * <p>Bad buffers are full-size (8122 bytes) with one byte corrupted at position 7186
 * (88.5% through the message): wire type bits changed from 2 to 6 (unsupported).
 * The parser does the same amount of field-dispatch work before hitting the error in
 * both good and bad cases.
 *
 * <p>Two variants are compared:
 *   - parseSlimMixed:       SlimBuffer flag-based path, throws ParseException at end
 *   - parseSlimThrowsMixed: SlimBufferThrows path, throws RuntimeException immediately at error
 *
 * <p>badPercent param drives three runs: 1% / 50% / 99% bad buffers.
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 7, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ParseDosBench {

    // Byte position of the corrupted field tag (field 111, 88.5% through the 8122-byte message).
    // Original byte 0xFA (wire type 2, length-delimited) -> 0xFE (wire type 6, unsupported).
    static final int CORRUPT_POS = 7186;
    static final byte CORRUPT_BYTE = (byte) 0xFE; // was 0xFA

    // Array size = 100 so badPercent maps directly to element count.
    static final int ARRAY_SIZE = 100;

    // Loop count per JMH invocation — one full pass through the buffer array.
    static final int LOOP_COUNT = ARRAY_SIZE;

    @State(Scope.Thread)
    public static class BenchState {

        @Param({"1", "50", "99"})
        int badPercent;

        SlimBuffer[] slimBuffers;
        SlimBufferThrows[] throwsBuffers;
        int slimIdx;
        int throwsIdx;

        @Setup
        public void setup() throws IOException {
            // Serialize the canonical Everything test object.
            SlimWriter tmp = new SlimWriter(1 << 20);
            Everything.PROTOBUF.write(EverythingTestData.EVERYTHING, tmp);
            byte[] good = tmp.toByteArray();

            // Build the bad buffer: same size, one byte corrupted at CORRUPT_POS.
            byte[] bad = Arrays.copyOf(good, good.length);
            bad[CORRUPT_POS] = CORRUPT_BYTE;

            System.out.printf("%nParseDosBench setup: good=%d bytes, bad=%d bytes, corrupt pos=%d%n",
                    good.length, bad.length, CORRUPT_POS);

            // Build shuffled 100-element arrays with exact bad ratio.
            slimBuffers = new SlimBuffer[ARRAY_SIZE];
            throwsBuffers = new SlimBufferThrows[ARRAY_SIZE];
            for (int i = 0; i < ARRAY_SIZE; i++) {
                boolean isBad = i < badPercent;
                slimBuffers[i] = new SlimBuffer(isBad ? bad : good);
                throwsBuffers[i] = new SlimBufferThrows(isBad ? bad : good);
            }

            // Shuffle with fixed seed for reproducibility.
            Random rng = new Random(42);
            for (int i = ARRAY_SIZE - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                SlimBuffer sb = slimBuffers[i]; slimBuffers[i] = slimBuffers[j]; slimBuffers[j] = sb;
                SlimBufferThrows st = throwsBuffers[i]; throwsBuffers[i] = throwsBuffers[j]; throwsBuffers[j] = st;
            }
        }
    }

    /**
     * Flag-based rejection: SlimBuffer sets an error flag at the corrupt byte, fast-exits
     * the remaining field loop, then throws ParseException from throwOnError() at the top.
     * Measures: parse work + flag overhead + deferred throw + catch.
     */
    @Benchmark
    @OperationsPerInvocation(LOOP_COUNT)
    public void parseSlimMixed(BenchState state, Blackhole bh) throws ParseException {
        for (int i = 0; i < LOOP_COUNT; i++) {
            SlimBuffer buf = state.slimBuffers[state.slimIdx++ % ARRAY_SIZE];
            buf.resetPosition();
            try {
                bh.consume(Everything.PROTOBUF.parse(buf));
            } catch (ParseException e) {
                bh.consume(e);
            }
        }
    }

    /**
     * Immediate-throw rejection: SlimBufferThrows throws RuntimeException the instant
     * setError is called at the corrupt byte, propagating through any try/finally cleanup.
     * Measures: parse work + immediate throw + catch.
     */
    @Benchmark
    @OperationsPerInvocation(LOOP_COUNT)
    public void parseSlimThrowsMixed(BenchState state, Blackhole bh) {
        for (int i = 0; i < LOOP_COUNT; i++) {
            SlimBufferThrows buf = state.throwsBuffers[state.throwsIdx++ % ARRAY_SIZE];
            buf.resetPosition();
            try {
                bh.consume(Everything.PROTOBUF.parse(buf));
            } catch (ParseException | RuntimeException e) {
                bh.consume(e);
            }
        }
    }
}
