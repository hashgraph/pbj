package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class PbjWritableStreamingData {
    private ByteArrayOutputStream byteArrayOutputStream;
    private WritableStreamingData output;

    @Setup(Level.Trial)
    public void setup() {
        byteArrayOutputStream = new ByteArrayOutputStream(8 * VarIntWriterBench.NUM_OF_VALUES);
        output = new WritableStreamingData(byteArrayOutputStream);
    }

    public void writeVarint(long value) throws IOException {
        output.writeVarLong(value, false);
    }

    public void endLoop() {
        byteArrayOutputStream.reset();
    }
}
