package com.hedera.pbj.integration.jmh.varint.writers;

import com.hedera.pbj.integration.jmh.varint.VarIntWriterBench;
import java.nio.ByteBuffer;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * A varint writer based on the code from Kafka project
 * <a href="https://github.com/apache/kafka/blob/trunk/clients/src/main/java/org/apache/kafka/common/utils/ByteUtils.java">ByteUtils.java</a>
 */
@State(Scope.Benchmark)
public class KafkaByteBuffer {
    private static final int[] VAR_INT_LENGTHS = new int[65];
    static {
        for (int i = 0; i <= 64; ++i) {
            VAR_INT_LENGTHS[i] = ((63 - i) / 7);
        }
    }

    private ByteBuffer buffer;

    @Setup(Level.Trial)
    public void setup() {
        buffer = ByteBuffer.allocate(8 * VarIntWriterBench.NUM_OF_VALUES);
    }

    @SuppressWarnings("fallthrough")
    public void writeVarint(long v) {
        while ((v & 0xffffffffffffff80L) != 0L) {
            byte b = (byte) ((v & 0x7f) | 0x80);
            buffer.put(b);
            v >>>= 7;
        }
        buffer.put((byte) v);
    }

    public void endLoop() {
        buffer.clear();
    }
}
