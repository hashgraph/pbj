package sample.target.proto.writers;

import com.hedera.hashgraph.protoparse.ProtoOutputStream;
import com.hedera.hashgraph.protoparse.ProtoWriter;
import sample.target.model.Apple;
import sample.target.model.Banana;
import sample.target.proto.schemas.AppleSchema;
import sample.target.proto.schemas.BananaSchema;

import java.io.IOException;
import java.io.OutputStream;

public final class BananaWriter {
    public static void write(Banana banana, OutputStream out) throws IOException {
        final var pb = new ProtoOutputStream(BananaSchema::valid, out);
        pb.writeString(BananaSchema.VARIETY, banana.variety());
    }
}
