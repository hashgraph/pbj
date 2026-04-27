// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Varint writer inspired by the "Blended" approach from Andrew Steinborn's varint writing showdown
 * (<a href="https://steinborn.me/posts/performance/how-fast-can-you-write-a-varint/">link</a>) adapted for 64-bit
 * longs.
 *
 * <p>Uses per-size-case branches with direct sequential byte array writes. Avoids ByteBuffer or
 * stream overhead; each branch writes only the exact bytes needed for that varint size.
 */
@SuppressWarnings("MismatchedReadAndWriteOfArray")
@State(Scope.Benchmark)
public class SteinbornBlendedByteArray {
    private byte[] buffer;
    private int position = 0;

    @Setup(Level.Trial)
    public void setup() {
        buffer = new byte[10 * VarIntWriterBench.NUM_OF_VALUES];
    }

    public void writeVarint(long value) {
        if (value < (1L << 7)) {
            buffer[position++] = (byte) value;
        } else if (value < (1L << 14)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (value >>> 7);
            position += 2;
        } else if (value < (1L << 21)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (value >>> 14);
            position += 3;
        } else if (value < (1L << 28)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (value >>> 21);
            position += 4;
        } else if (value < (1L << 35)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (value >>> 28);
            position += 5;
        } else if (value < (1L << 42)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (((value >>> 28) & 0x7F) | 0x80);
            buffer[position + 5] = (byte) (value >>> 35);
            position += 6;
        } else if (value < (1L << 49)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (((value >>> 28) & 0x7F) | 0x80);
            buffer[position + 5] = (byte) (((value >>> 35) & 0x7F) | 0x80);
            buffer[position + 6] = (byte) (value >>> 42);
            position += 7;
        } else if (value < (1L << 56)) {
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (((value >>> 28) & 0x7F) | 0x80);
            buffer[position + 5] = (byte) (((value >>> 35) & 0x7F) | 0x80);
            buffer[position + 6] = (byte) (((value >>> 42) & 0x7F) | 0x80);
            buffer[position + 7] = (byte) (value >>> 49);
            position += 8;
        } else //noinspection ConstantValue
        if (value >= 0) {
            // 9 bytes: value in [2^56, Long.MAX_VALUE]
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (((value >>> 28) & 0x7F) | 0x80);
            buffer[position + 5] = (byte) (((value >>> 35) & 0x7F) | 0x80);
            buffer[position + 6] = (byte) (((value >>> 42) & 0x7F) | 0x80);
            buffer[position + 7] = (byte) (((value >>> 49) & 0x7F) | 0x80);
            buffer[position + 8] = (byte) (value >>> 56);
            position += 9;
        } else {
            // 10 bytes: negative values (signed int64 treated as uint64 by protobuf)
            buffer[position] = (byte) ((value & 0x7F) | 0x80);
            buffer[position + 1] = (byte) (((value >>> 7) & 0x7F) | 0x80);
            buffer[position + 2] = (byte) (((value >>> 14) & 0x7F) | 0x80);
            buffer[position + 3] = (byte) (((value >>> 21) & 0x7F) | 0x80);
            buffer[position + 4] = (byte) (((value >>> 28) & 0x7F) | 0x80);
            buffer[position + 5] = (byte) (((value >>> 35) & 0x7F) | 0x80);
            buffer[position + 6] = (byte) (((value >>> 42) & 0x7F) | 0x80);
            buffer[position + 7] = (byte) (((value >>> 49) & 0x7F) | 0x80);
            buffer[position + 8] = (byte) (((value >>> 56) & 0x7F) | 0x80);
            buffer[position + 9] = (byte) (value >>> 63);
            position += 10;
        }
    }

    public void endLoop() {
        position = 0;
    }
}
