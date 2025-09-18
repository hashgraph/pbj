// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import java.io.IOException;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * A varint writer based on the code from Richard Startin's post
 * <a href="https://github.com/astei/varint-writing-showdown/issues/1">Precompute varint lengths, 64 bit values</a>
 */
@State(Scope.Benchmark)
public class RichardStartinByteArray {
    private static final int[] VAR_INT_LENGTHS = new int[65];

    static {
        for (int i = 0; i <= 64; ++i) {
            VAR_INT_LENGTHS[i] = ((63 - i) / 7);
        }
    }

    private byte[] buffer;
    private int position = 0;

    @Setup(Level.Trial)
    public void setup() {
        buffer = new byte[8 * VarIntWriterBench.NUM_OF_VALUES];
    }

    @SuppressWarnings("fallthrough")
    public void writeVarint(long value) throws IOException {
        int length = VAR_INT_LENGTHS[Long.numberOfLeadingZeros(value)];
        buffer[position + length] = (byte) (value >>> (length * 7));
        switch (length - 1) {
            case 8:
                buffer[position + 8] = (byte) ((value >>> 56) | 0x80);
            // Deliberate fallthrough
            case 7:
                buffer[position + 7] = (byte) ((value >>> 49) | 0x80);
            // Deliberate fallthrough
            case 6:
                buffer[position + 6] = (byte) ((value >>> 42) | 0x80);
            // Deliberate fallthrough
            case 5:
                buffer[position + 5] = (byte) ((value >>> 35) | 0x80);
            // Deliberate fallthrough
            case 4:
                buffer[position + 4] = (byte) ((value >>> 28) | 0x80);
            // Deliberate fallthrough
            case 3:
                buffer[position + 3] = (byte) ((value >>> 21) | 0x80);
            // Deliberate fallthrough
            case 2:
                buffer[position + 2] = (byte) ((value >>> 14) | 0x80);
            // Deliberate fallthrough
            case 1:
                buffer[position + 1] = (byte) ((value >>> 7) | 0x80);
            // Deliberate fallthrough
            case 0:
                buffer[position] = (byte) (value | 0x80);
        }
    }

    public void endLoop() {
        position = 0;
    }
}
