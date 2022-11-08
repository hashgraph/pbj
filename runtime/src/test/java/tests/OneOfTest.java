package tests;

import com.hedera.hashgraph.protoparse.OneOf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OneOfTest {
    @Test
    void nullNameIsOK() {
        final var oneOf = new OneOf<>(1, TestEnum.KIND1, null);
        assertNull(oneOf.value());
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> {
            new OneOf<>(1, null, "Value");
        });
    }

    @Test
    void negativeNumberThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new OneOf<>(-1, TestEnum.KIND1, "Value");
        });
    }

    @Test
    void asReturnsValue() {
        final var oneOf = new OneOf<>(1, TestEnum.KIND1, "Value");
        assertEquals("Value", oneOf.as());
    }

    public enum TestEnum {
        KIND1,
        KIND2
    }

}
