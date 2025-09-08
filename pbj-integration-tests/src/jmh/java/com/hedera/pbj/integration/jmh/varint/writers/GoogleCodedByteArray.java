package com.hedera.pbj.integration.jmh.varint.writers;

import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class GoogleCodedByteArray {
    private byte[] byteArray;
    private CodedOutputStream output;

    @Setup(Level.Trial)
    public void setup() {
        byteArray = new byte[8 * VarIntWriterBench.NUM_OF_VALUES];
        output = CodedOutputStream.newInstance(byteArray);
    }

    public void writeVarint(long value) throws IOException {
        output.writeUInt64NoTag(value);
    }

    public void endLoop() {
        output = CodedOutputStream.newInstance(byteArray);
    }
}
