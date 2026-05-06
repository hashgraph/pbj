// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import com.hedera.pbj.runtime.io.buffer.MemoryData;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class PbjMemoryData {
    private MemoryData output;

    @Setup(Level.Trial)
    public void setup() {
        output = MemoryData.allocate(10 * VarIntWriterBench.NUM_OF_VALUES);
    }

    public void writeVarint(long value) {
        output.writeVarLong(value, false);
    }

    public void endLoop() {
        output.reset();
    }
}
