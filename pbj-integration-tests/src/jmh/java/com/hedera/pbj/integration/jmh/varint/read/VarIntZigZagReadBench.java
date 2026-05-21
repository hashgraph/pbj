// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.read;

import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.DataEncodingException;
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

/// Compare different options for reading (no-)zigZag varints.
@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(3)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.Throughput)
public class VarIntZigZagReadBench {
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
        long sum;

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

    /// Current PBJ implementation with zigZag condition on return.
    private static long getVarInt_current(final byte[] array, int pos, final boolean zigZag) {
        int vi;
        long vl;
        final int limit = Math.min(array.length, pos + 10);

        fastpath:
        {
            if (pos == limit) break fastpath;

            if ((vi = array[pos++]) >= 0) {
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            } else if (pos + 9 == limit) {
                // Fast path w/o any limit checks if we have 9 more bytes
                if ((vi ^= array[pos++] << 7) < 0) {
                    vi ^= (~0 << 7);
                    return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                }

                if ((vi ^= array[pos++] << 14) >= 0) {
                    vi ^= ((~0 << 7) ^ (~0 << 14));
                    return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                }

                if ((vi ^= array[pos++] << 21) < 0) {
                    vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                    return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
                }

                vl = vi;
                if ((vl ^= (long) array[pos++] << 28) >= 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }

                if ((vl ^= (long) array[pos++] << 35) < 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }

                if ((vl ^= (long) array[pos++] << 42) >= 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }

                if ((vl ^= (long) array[pos++] << 49) < 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }

                if ((vl ^= (long) array[pos++] << 56) >= 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }

                if (array[pos++] < 0) break fastpath;
                if ((vl ^= (long) array[pos - 1] << 63) >= 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56)
                            ^ (~0L << 63));
                    return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
                }
            }
        }

        slowpath:
        {
            // Slower path because this is an array/buffer, and we have less than 9 (or even 10) bytes ahead
            if (pos >= limit) break slowpath;

            // Since the above check is false, the pos was incremented in the fastpath above, and vi is actually
            // assigned there. However, javac is unable to see this and throw an error. So we re-initialize it.
            // This byte is in CPU L1 cache, so this should be fast. Also, this is a slowpath anyway.
            vi = array[pos - 1];
            if ((vi ^= array[pos++] << 7) < 0) {
                vi ^= (~0 << 7);
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= array[pos++] << 14) >= 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14));
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= array[pos++] << 21) < 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                return zigZag ? (vi >>> 1) ^ -(vi & 1) : vi;
            }
            if (pos >= limit) break slowpath;

            vl = vi;
            if ((vl ^= (long) array[pos++] << 28) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 35) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 42) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 49) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 56) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
            if (pos >= limit || array[pos++] < 0) break slowpath;

            if ((vl ^= (long) array[pos - 1] << 63) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56)
                        ^ (~0L << 63));
                return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
            }
        }

        throw new DataEncodingException("Malformed var int");
    }

    /// PBJ algo w/o zigZag condition (assume zigZag = false). The (supposed to be the) "fast" one.
    private static long getVarInt_noZigZag(final byte[] array, int pos) {
        int vi;
        long vl;
        final int limit = Math.min(array.length, pos + 10);

        fastpath:
        {
            if (pos == limit) break fastpath;

            if ((vi = array[pos++]) >= 0) {
                return vi;
            } else if (pos + 9 == limit) {
                // Fast path w/o any limit checks if we have 9 more bytes
                if ((vi ^= array[pos++] << 7) < 0) {
                    vi ^= (~0 << 7);
                    return vi;
                }

                if ((vi ^= array[pos++] << 14) >= 0) {
                    vi ^= ((~0 << 7) ^ (~0 << 14));
                    return vi;
                }

                if ((vi ^= array[pos++] << 21) < 0) {
                    vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                    return vi;
                }

                vl = vi;
                if ((vl ^= (long) array[pos++] << 28) >= 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                    return vl;
                }

                if ((vl ^= (long) array[pos++] << 35) < 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                    return vl;
                }

                if ((vl ^= (long) array[pos++] << 42) >= 0L) {
                    vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                    return vl;
                }

                if ((vl ^= (long) array[pos++] << 49) < 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49));
                    return vl;
                }

                if ((vl ^= (long) array[pos++] << 56) >= 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56));
                    return vl;
                }

                if (array[pos++] < 0) break fastpath;
                if ((vl ^= (long) array[pos - 1] << 63) >= 0L) {
                    vl ^= ((~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56)
                            ^ (~0L << 63));
                    return vl;
                }
            }
        }

        slowpath:
        {
            // Slower path because this is an array/buffer, and we have less than 9 (or even 10) bytes ahead
            if (pos >= limit) break slowpath;

            // Since the above check is false, the pos was incremented in the fastpath above, and vi is actually
            // assigned there. However, javac is unable to see this and throw an error. So we re-initialize it.
            // This byte is in CPU L1 cache, so this should be fast. Also, this is a slowpath anyway.
            vi = array[pos - 1];
            if ((vi ^= array[pos++] << 7) < 0) {
                vi ^= (~0 << 7);
                return vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= array[pos++] << 14) >= 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14));
                return vi;
            }
            if (pos >= limit) break slowpath;

            if ((vi ^= array[pos++] << 21) < 0) {
                vi ^= ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                return vi;
            }
            if (pos >= limit) break slowpath;

            vl = vi;
            if ((vl ^= (long) array[pos++] << 28) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28));
                return vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 35) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35));
                return vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 42) >= 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42));
                return vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 49) < 0L) {
                vl ^= ((~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42) ^ (~0L << 49));
                return vl;
            }
            if (pos >= limit) break slowpath;

            if ((vl ^= (long) array[pos++] << 56) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56));
                return vl;
            }
            if (pos >= limit || array[pos++] < 0) break slowpath;

            if ((vl ^= (long) array[pos - 1] << 63) >= 0L) {
                vl ^= ((~0L << 7)
                        ^ (~0L << 14)
                        ^ (~0L << 21)
                        ^ (~0L << 28)
                        ^ (~0L << 35)
                        ^ (~0L << 42)
                        ^ (~0L << 49)
                        ^ (~0L << 56)
                        ^ (~0L << 63));
                return vl;
            }
        }

        throw new DataEncodingException("Malformed var int");
    }

    /// Proposed "slow" method with zigZag arg, delegating to the fast noZigZag reader
    private static long getVarInt_zigZagArg(final byte[] array, int pos, final boolean zigZag) {
        final long vl = getVarInt_noZigZag(array, pos);
        return zigZag ? (vl >>> 1) ^ -(vl & 1) : vl;
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_current(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            final long v = getVarInt_current(state.array, pos, state.zigZag);
            state.sum += v;
            pos += ProtoWriterTools.sizeOfVarInt64(v);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_noZigZag(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            final long v = getVarInt_noZigZag(state.array, pos);
            state.sum += v;
            pos += ProtoWriterTools.sizeOfVarInt64(v);
        }
        blackhole.consume(state.sum);
    }

    @Benchmark
    @OperationsPerInvocation(INVOCATIONS)
    public void pbj_zigZagArg(final BenchState state, final Blackhole blackhole) {
        state.sum = 0;
        for (int invocation = 0, pos = 0; invocation < INVOCATIONS; invocation++) {
            final long v = getVarInt_zigZagArg(state.array, pos, state.zigZag);
            state.sum += v;
            pos += ProtoWriterTools.sizeOfVarInt64(v);
        }
        blackhole.consume(state.sum);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(VarIntZigZagReadBench.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
