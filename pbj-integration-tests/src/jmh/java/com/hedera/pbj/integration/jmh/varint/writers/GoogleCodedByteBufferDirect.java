// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.varint.writers;

import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class GoogleCodedByteBufferDirect {
    private ByteBuffer byteBuffer;
    private CodedOutputStream output;

    @Setup(Level.Trial)
    public void setup() {
        byteBuffer = ByteBuffer.allocateDirect(8 * VarIntWriterBench.NUM_OF_VALUES);
        output = CodedOutputStream.newInstance(byteBuffer);
    }

    public void writeVarint(long value) throws IOException {
        output.writeUInt64NoTag(value);
    }

    public void endLoop() {
        byteBuffer.clear();
        output = CodedOutputStream.newInstance(byteBuffer);
    }
}
