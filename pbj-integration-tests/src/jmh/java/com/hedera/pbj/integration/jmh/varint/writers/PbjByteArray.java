// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import com.hedera.pbj.runtime.ProtoArrayWriterTools;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** PBJ's byte-array varint writers: the unrolled {@code writeUnsignedVarInt} and the single-store {@code writeVarInt}. */
@State(Scope.Benchmark)
public class PbjByteArray {
    private byte[] buffer;
    private int position = 0;

    @Setup(Level.Trial)
    public void setup() {
        buffer = new byte[8 * VarIntWriterBench.NUM_OF_VALUES];
    }

    public void writeVarint(long value) {
        position += ProtoArrayWriterTools.writeUnsignedVarInt(buffer, position, value);
    }

    public void writeVarintSingleStore(long value) {
        position += ProtoArrayWriterTools.writeVarInt(buffer, position, buffer.length, value);
    }

    public void endLoop() {
        position = 0;
    }
}
