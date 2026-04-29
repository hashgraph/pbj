// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.read;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/// Compares varint read algorithms performance.
/// Note: there's advanced algorithms for reading varints, e.g. https://arxiv.org/html/2403.06898v4 .
/// They use SIMD which isn't available in Java 25 directly. Also, they're optimized for reading arrays of varints,
/// which is not the use-case in PBJ/protobuf in general. So we couldn't benefit from them right now.
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class VarIntByteArrayReadBench {
    /// The number of invocation, aka the number of varints in the array.
    private static final int INVOCATIONS = 20 * 1024;

    private static final int MAX_BYTES_PER_VARINT = 5;

    @State(Scope.Thread)
    public static class BenchState {
        /// A varint value range corresponding to a certain varint encoding length.
        record NumRange(int min, int max) {}

        /// Existing varint ranges for 1, 2, 3, 4, and 5 bytes encoding.
        static final NumRange RANGES[] = {
            new NumRange(0, 127),
            new NumRange(127 + 1, 16383),
            new NumRange(16383 + 1, 2097151),
            new NumRange(2097151 + 1, 268435455),
            new NumRange(268435455 + 1, Integer.MAX_VALUE),
        };

        /// The range index (1-based) that's equal to the number of bytes in a varint encoding.
        @Param({"1", "2", "3", "4", "5"})
        int range;

        /// zigZag==true is rarely or never used in PBJ/CN today. So we hard-code it to false in this test for now.
        boolean zigZag;

        // We could use byte[][] to avoid the position tracking when reading.
        // However, for small varints, this would lead to spending cycles on reading the array reference (4 or 8 bytes),
        // which is comparable to reading the varint bytes themselves. So it would be difficult to measure the varint
        // parsing performance itself. So instead we track the position in a local var in the measurement method
        // and put all varints into a single array:
        byte[] array;

        /// A sum of varints to be consumed by the blackhole. We keep it in the state to avoid JVM optimizing the sum
        /// into a CPU register for some algorithms (but not others), which could potentially distort the
        /// results.
        /// So we make every algorithm maintain the sum in this variable instead, so that they all spend the
        /// exact same time updating it.
        int sum;

        @Setup(Level.Trial)
        public void setup() {
            // Hard-code for now. See comment above.
            zigZag = false;

            array = new byte[INVOCATIONS * MAX_BYTES_PER_VARINT];

            // For determinism:
            final Random random = new Random(723049435);
            final BufferedData bd = BufferedData.wrap(array);
            for (int i = 0; i < INVOCATIONS; i++) {
                bd.writeVarInt(
                        RANGES[range - 1].min + random.nextInt(RANGES[range - 1].max - RANGES[range - 1].min), zigZag);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {}
    }

    /// We use this algorithm everywhere in PBJ - in ReadableSequentialData , DirectBufferedData, RandomAccessData,
    /// ByteArrayBufferedData, and Bytes. It's known as "getVarLongRichard". The proper academic name is LEB128.
    /// It's also the Google slow-path algorithm as well.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            int value = 0;

            for (int i = 0; i < 10; i++) {
                final byte b = state.array[pos++];
                value |= (b & 0x7F) << (i * 7);
                if (b >= 0) {
                    state.sum += state.zigZag ? (value >>> 1) ^ -(value & 1) : value;
                    break;
                }
            }
        }
        blackhole.consume(state.sum);
    }

    /// A variation of LEB128 with the zigZag conditional removed.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_zigZagFalse(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            int value = 0;

            for (int i = 0; i < 10; i++) {
                final byte b = state.array[pos++];
                value |= (b & 0x7F) << (i * 7);
                if (b >= 0) {
                    state.sum += value;
                    break;
                }
            }
        }
        blackhole.consume(state.sum);
    }

    /// A variation of LEB128 that uses `(b & 0x80) == 0` instead of `b >= 0`.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_BitwiseAndCondition(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            int value = 0;

            for (int i = 0; i < 10; i++) {
                final byte b = state.array[pos++];
                value |= (b & 0x7F) << (i * 7);
                if ((b & 0x80) == 0) {
                    state.sum += state.zigZag ? (value >>> 1) ^ -(value & 1) : value;
                    break;
                }
            }
        }
        blackhole.consume(state.sum);
    }

    /// A variation of LEB128 that uses do/while loop instead of a for loop to skip one branch for 1-byte varint.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_doWhileLoop(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            int value = 0;

            int i = 0;
            do {
                final byte b = state.array[pos++];
                value |= (b & 0x7F) << (i * 7);
                if ((b & 0x80) == 0) {
                    state.sum += state.zigZag ? (value >>> 1) ^ -(value & 1) : value;
                    break;
                }
            } while (++i < 10);
        }
        blackhole.consume(state.sum);
    }

    /// A slightly modified copy of Google implementation in CodecInputStream.
    /// PBJ used to use a very similar algorithm, just before https://github.com/hashgraph/pbj/pull/144
    /// where we switched to LEB128.
    @SuppressWarnings("lossy-conversions") // the impl is able to support longs, but we ignore that here and use ints.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void google(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            final int limit = pos + 5;

            fastpath:
            {
                int tempPos = pos;

                if (limit == tempPos) {
                    break fastpath;
                }

                int x;
                int y;
                if ((y = state.array[tempPos++]) >= 0) {
                    pos = tempPos;
                    state.sum += y;
                    continue;
                } else if (limit - tempPos < 9) {
                    break fastpath;
                } else if ((y ^= (state.array[tempPos++] << 7)) < 0) {
                    x = y ^ (~0 << 7);
                } else if ((y ^= (state.array[tempPos++] << 14)) >= 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14));
                } else if ((y ^= (state.array[tempPos++] << 21)) < 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                } else if ((x = y ^ (state.array[tempPos++] << 28)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
                } else if ((x ^= (state.array[tempPos++] << 35)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
                } else if ((x ^= (state.array[tempPos++] << 42)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
                } else if ((x ^= (state.array[tempPos++] << 49)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49);
                } else if ((x ^= (state.array[tempPos++] << 56)) >= 0L) {
                    x ^= (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56);
                } else if ((x ^= (state.array[tempPos++] << 63)) >= 0L) {
                    x ^= (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56)
                            ^ (~0L << 63);
                } else {
                    break fastpath; // Will throw malformedVarint()
                }
                pos = tempPos;
                state.sum += x;
                continue;
            }

            // slow path
            {
                int result = 0;
                for (int shift = 0; shift < 64; shift += 7) {
                    final byte b = state.array[pos++];
                    result |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) {
                        state.sum += result;
                        break;
                    }
                }
            }
        }
        blackhole.consume(state.sum);
    }

    /// A LEB128 with fully unrolled loop.
    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void loopLess(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            byte b = state.array[pos++];
            if ((b & 0x80) == 0) {
                state.sum += b;
                continue;
            }

            int v = b & 0x7F;
            b = state.array[pos++];
            if ((b & 0x80) == 0) {
                state.sum += v | b << 7;
                continue;
            }

            v |= (b & 0x7F) << 7;
            b = state.array[pos++];
            if ((b & 0x80) == 0) {
                state.sum += v | b << 14;
                continue;
            }

            v |= (b & 0x7F) << 14;
            b = state.array[pos++];
            if ((b & 0x80) == 0) {
                state.sum += v | b << 21;
                continue;
            }

            v |= (b & 0x7F) << 21;
            b = state.array[pos++];
            // if (b > 0) {
            // Stop here because this benchmark doesn't support longs, only ints.
            state.sum += v | b << 28;
            // }
        }
        blackhole.consume(state.sum);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(VarIntByteArrayReadBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
