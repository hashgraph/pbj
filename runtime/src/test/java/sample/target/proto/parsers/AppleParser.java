package sample.target.proto.parsers;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.MalformedProtobufException;
import com.hedera.hashgraph.protoparse.ProtoParser;
import sample.target.model.Apple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static sample.target.proto.schemas.AppleSchema.VARIETY;

public class AppleParser extends ProtoParser {
    private String variety = "";

    public Apple parse(byte[] protobuf) throws MalformedProtobufException {
        variety = "";
        super.start(protobuf);
        return new Apple(variety);
    }

    public Apple parse(ByteBuffer protobuf) throws MalformedProtobufException {
        variety = "";
        super.start(protobuf);
        return new Apple(variety);
    }

    public Apple parse(InputStream protobuf) throws IOException, MalformedProtobufException {
        variety = "";
        super.start(protobuf);
        return new Apple(variety);
    }

    @Override
    protected FieldDefinition getFieldDefinition(final int fieldNumber) {
        return switch (fieldNumber) {
            case 1 -> VARIETY;
            default -> null;
        };
    }

    @Override
    public void stringField(final int fieldNum, final String value) {
        if (fieldNum != VARIETY.number()) {
            throw new AssertionError("Unknown field number " + fieldNum);
        }

        this.variety = value;
    }
}
