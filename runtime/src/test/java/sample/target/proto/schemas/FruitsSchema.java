package sample.target.proto.schemas;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.FieldType;

public final class FruitsSchema {
    public static final FieldDefinition APPLE = new FieldDefinition("apple", FieldType.MESSAGE, false, 1);
    public static final FieldDefinition BANANA = new FieldDefinition("banana", FieldType.MESSAGE, false, 2);

    public static boolean valid(FieldDefinition field) {
        return field != null && field == getField(field.number());
    }

    public static FieldDefinition getField(final int fieldNumber) {
        return switch (fieldNumber) {
            case 1 -> APPLE;
            case 2 -> BANANA;
            default -> null;
        };
    }
}
