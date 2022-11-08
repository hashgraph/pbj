package sample.target.proto.schemas;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.FieldType;

public final class NestedSchema {
    public static final FieldDefinition NESTED_MEMO = new FieldDefinition("nestedMemo", FieldType.STRING, false, 100);

    public static boolean valid(FieldDefinition field) {
        return field != null && field == getField(field.number());
    }

    public static FieldDefinition getField(final int fieldNumber) {
        return switch (fieldNumber) {
            case 100 -> NESTED_MEMO;
            default -> null;
        };
    }
}
