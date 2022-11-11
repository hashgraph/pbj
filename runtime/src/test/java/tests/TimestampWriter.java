package tests;

import com.hedera.hashgraph.pbj.runtime.ProtoOutputStream;

import java.io.IOException;
import java.io.OutputStream;

import static tests.TimestampSchema.NANOS;
import static tests.TimestampSchema.SECONDS;

/**
 * Writer for Timestamp model object. Generate based on protobuf schema.
 */
public final class TimestampWriter {
    /**
     * Write out a Timestamp model to output stream in protobuf format.
     *
     * @param data The input model data to write
     * @param out The output stream to write to
     * @throws IOException If there is a problem writing
     */
    public static void write(Timestamp data, OutputStream out) throws IOException {
        final ProtoOutputStream pout = new ProtoOutputStream(TimestampSchema::valid,out);
        pout.writeLong(SECONDS, data.seconds());
        pout.writeInteger(NANOS, data.nanos());
    }
}