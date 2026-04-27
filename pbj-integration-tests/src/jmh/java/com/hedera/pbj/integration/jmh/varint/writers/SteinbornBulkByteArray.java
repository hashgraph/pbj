// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Varint writer based on the "Lucky 5" / bulk-write technique from Andrew Steinborn's showdown
 * (<a href="https://github.com/astei/varint-writing-showdown">link</a>) adapted for 64-bit longs.
 *
 * <p>Packs multiple encoded varint bytes into a short, int, or long value and writes them with
 * a single bulk VarHandle set, reducing the number of individual byte write operations.
 * For example, a 4-byte varint is packed into one {@code int} and written in a single operation
 * instead of four separate byte writes.
 */
@State(Scope.Benchmark)
public class SteinbornBulkByteArray {
    private static final VarHandle SHORT_BE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_BE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_BE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

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
            SHORT_BE.set(buffer, position,
                    (short) (((value & 0x7F) | 0x80) << 8 | (value >>> 7)));
            position += 2;
        } else if (value < (1L << 21)) {
            SHORT_BE.set(buffer, position,
                    (short) (((value & 0x7F) | 0x80) << 8 | (((value >>> 7) & 0x7F) | 0x80)));
            buffer[position + 2] = (byte) (value >>> 14);
            position += 3;
        } else if (value < (1L << 28)) {
            INT_BE.set(buffer, position,
                    (int) (((value & 0x7F) | 0x80) << 24
                         | (((value >>> 7) & 0x7F) | 0x80) << 16
                         | (((value >>> 14) & 0x7F) | 0x80) << 8
                         | (value >>> 21)));
            position += 4;
        } else if (value < (1L << 35)) {
            INT_BE.set(buffer, position,
                    (int) (((value & 0x7F) | 0x80) << 24
                         | (((value >>> 7) & 0x7F) | 0x80) << 16
                         | (((value >>> 14) & 0x7F) | 0x80) << 8
                         | (((value >>> 21) & 0x7F) | 0x80)));
            buffer[position + 4] = (byte) (value >>> 28);
            position += 5;
        } else if (value < (1L << 42)) {
            INT_BE.set(buffer, position,
                    (int) (((value & 0x7F) | 0x80) << 24
                         | (((value >>> 7) & 0x7F) | 0x80) << 16
                         | (((value >>> 14) & 0x7F) | 0x80) << 8
                         | (((value >>> 21) & 0x7F) | 0x80)));
            SHORT_BE.set(buffer, position + 4,
                    (short) ((((value >>> 28) & 0x7F) | 0x80) << 8 | (value >>> 35)));
            position += 6;
        } else if (value < (1L << 49)) {
            INT_BE.set(buffer, position,
                    (int) (((value & 0x7F) | 0x80) << 24
                         | (((value >>> 7) & 0x7F) | 0x80) << 16
                         | (((value >>> 14) & 0x7F) | 0x80) << 8
                         | (((value >>> 21) & 0x7F) | 0x80)));
            SHORT_BE.set(buffer, position + 4,
                    (short) ((((value >>> 28) & 0x7F) | 0x80) << 8 | (((value >>> 35) & 0x7F) | 0x80)));
            buffer[position + 6] = (byte) (value >>> 42);
            position += 7;
        } else if (value < (1L << 56)) {
            LONG_BE.set(buffer, position,
                    ((value & 0x7FL) | 0x80L) << 56
                  | (((value >>> 7) & 0x7FL) | 0x80L) << 48
                  | (((value >>> 14) & 0x7FL) | 0x80L) << 40
                  | (((value >>> 21) & 0x7FL) | 0x80L) << 32
                  | (((value >>> 28) & 0x7FL) | 0x80L) << 24
                  | (((value >>> 35) & 0x7FL) | 0x80L) << 16
                  | (((value >>> 42) & 0x7FL) | 0x80L) << 8
                  | (value >>> 49));
            position += 8;
        } else //noinspection ConstantValue
            if (value >= 0) {
            // 9 bytes: value in [2^56, Long.MAX_VALUE]
            LONG_BE.set(buffer, position,
                    ((value & 0x7FL) | 0x80L) << 56
                  | (((value >>> 7) & 0x7FL) | 0x80L) << 48
                  | (((value >>> 14) & 0x7FL) | 0x80L) << 40
                  | (((value >>> 21) & 0x7FL) | 0x80L) << 32
                  | (((value >>> 28) & 0x7FL) | 0x80L) << 24
                  | (((value >>> 35) & 0x7FL) | 0x80L) << 16
                  | (((value >>> 42) & 0x7FL) | 0x80L) << 8
                  | (((value >>> 49) & 0x7FL) | 0x80L));
            buffer[position + 8] = (byte) (value >>> 56);
            position += 9;
        } else {
            // 10 bytes: negative long (protobuf treats int64 as uint64)
            LONG_BE.set(buffer, position,
                    ((value & 0x7FL) | 0x80L) << 56
                  | (((value >>> 7) & 0x7FL) | 0x80L) << 48
                  | (((value >>> 14) & 0x7FL) | 0x80L) << 40
                  | (((value >>> 21) & 0x7FL) | 0x80L) << 32
                  | (((value >>> 28) & 0x7FL) | 0x80L) << 24
                  | (((value >>> 35) & 0x7FL) | 0x80L) << 16
                  | (((value >>> 42) & 0x7FL) | 0x80L) << 8
                  | (((value >>> 49) & 0x7FL) | 0x80L));
            SHORT_BE.set(buffer, position + 8,
                    (short) ((((value >>> 56) & 0x7F) | 0x80) << 8 | (value >>> 63)));
            position += 10;
        }
    }

    public void endLoop() {
        position = 0;
    }
}
