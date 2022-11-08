package sample.target.proto.schemas;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.FieldType;

public final class AppleSchema {
    public static final FieldDefinition VARIETY = new FieldDefinition("variety", FieldType.STRING, false, 1);

    public static boolean valid(FieldDefinition field) {
        return field != null && field == getField(field.number());
    }

    public static FieldDefinition getField(final int fieldNumber) {
        return switch (fieldNumber) {
            case 1 -> VARIETY;
            default -> null;
        };
    }
}
