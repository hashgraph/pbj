package tests;

import com.hedera.hashgraph.protoparse.FieldDefinition;
import com.hedera.hashgraph.protoparse.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldDefinitionTest {
    @Test
    void nullNameThrows() {
        assertThrows(NullPointerException.class, () -> {
            new FieldDefinition(null, FieldType.STRING, false, 1);
        });
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> {
            new FieldDefinition("Name", null, false, 1);
        });
    }

    @Test
    void negativeNumberThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new FieldDefinition("Name", FieldType.STRING, false, -1);
        });
    }
}
