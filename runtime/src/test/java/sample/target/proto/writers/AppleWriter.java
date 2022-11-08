package sample.target.proto.writers;

import com.hedera.hashgraph.protoparse.ProtoOutputStream;
import com.hedera.hashgraph.protoparse.ProtoWriter;
import sample.target.model.Apple;
import sample.target.proto.schemas.AppleSchema;

import java.io.IOException;
import java.io.OutputStream;

public final class AppleWriter {
    public static void write(Apple apple, OutputStream out) throws IOException {
        final var pb = new ProtoOutputStream(AppleSchema::valid, out);
        pb.writeString(AppleSchema.VARIETY, apple.variety());
    }
}
