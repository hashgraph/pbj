// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Varint writer adapted from Andrew Steinborn's
 * <a href="https://github.com/astei/varint-writing-showdown">SmartNoDataDependencyUnrolledVarIntWriter</a> for 64-bit
 * longs.
 *
 * <p>All branch conditions test the <em>original</em> unshifted value, eliminating sequential
 * data dependencies between condition evaluations and enabling CPU instruction-level parallelism.
 */
@SuppressWarnings("MismatchedReadAndWriteOfArray")
@State(Scope.Benchmark)
public class SmartNoDataDependencyByteArray {
    private byte[] buffer;
    private int position = 0;

    @Setup(Level.Trial)
    public void setup() {
        buffer = new byte[10 * VarIntWriterBench.NUM_OF_VALUES];
    }

    public void writeVarint(long value) {
        if ((value & ~0x7FL) == 0) {
            buffer[position++] = (byte) value;
        } else {
            buffer[position++] = (byte) ((value & 0x7F) | 0x80);
            if ((value & ~0x3FFFL) == 0) {
                buffer[position++] = (byte) (value >>> 7);
            } else {
                buffer[position++] = (byte) ((value >>> 7) & 0x7F | 0x80);
                if ((value & ~0x1FFFFFL) == 0) {
                    buffer[position++] = (byte) (value >>> 14);
                } else {
                    buffer[position++] = (byte) ((value >>> 14) & 0x7F | 0x80);
                    if ((value & ~0xFFFFFFFL) == 0) {
                        buffer[position++] = (byte) (value >>> 21);
                    } else {
                        buffer[position++] = (byte) ((value >>> 21) & 0x7F | 0x80);
                        if ((value & ~0x7FFFFFFFFL) == 0) {
                            buffer[position++] = (byte) (value >>> 28);
                        } else {
                            buffer[position++] = (byte) ((value >>> 28) & 0x7F | 0x80);
                            if ((value & ~0x3FFFFFFFFFFL) == 0) {
                                buffer[position++] = (byte) (value >>> 35);
                            } else {
                                buffer[position++] = (byte) ((value >>> 35) & 0x7F | 0x80);
                                if ((value & ~0x1FFFFFFFFFFFFL) == 0) {
                                    buffer[position++] = (byte) (value >>> 42);
                                } else {
                                    buffer[position++] = (byte) ((value >>> 42) & 0x7F | 0x80);
                                    if ((value & ~0xFFFFFFFFFFFFFFL) == 0) {
                                        buffer[position++] = (byte) (value >>> 49);
                                    } else {
                                        buffer[position++] = (byte) ((value >>> 49) & 0x7F | 0x80);
                                        if ((value & ~0x7FFFFFFFFFFFFFFFL) == 0) {
                                            buffer[position++] = (byte) (value >>> 56);
                                        } else {
                                            buffer[position++] = (byte) ((value >>> 56) & 0x7F | 0x80);
                                            buffer[position++] = (byte) (value >>> 63);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void endLoop() {
        position = 0;
    }
}
