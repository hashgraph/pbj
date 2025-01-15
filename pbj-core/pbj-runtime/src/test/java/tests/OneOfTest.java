// SPDX-License-Identifier: Apache-2.0
package tests;

import com.hedera.pbj.runtime.EnumWithProtoMetadata;
import com.hedera.pbj.runtime.OneOf;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OneOfTest {
    @Test
    void nullNameIsOK() {
        final var oneOf = new OneOf<>(TestEnum.KIND1, null);
        assertNull(oneOf.value());
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> new OneOf<>(null, "Value"));
    }

    @Test
    void asReturnsValue() {
        final var oneOf = new OneOf<>(TestEnum.KIND1, "Value");
        assertEquals("Value", oneOf.as());
    }

    @Test
    void hashCodeReturnsHashCode() {
        final var oneOf = new OneOf<>(TestEnum.KIND1, "Value");
        assertEquals((31 + Integer.hashCode(TestEnum.KIND1.protoOrdinal())) * 31 + "Value".hashCode(), oneOf.hashCode());
    }

    @Test
    void equalsWorks() {
        final var oneOf = new OneOf<>(TestEnum.KIND1, "Value");
        final var sameOneOf = new OneOf<>(TestEnum.KIND1, "Value");
        final var differentOneOf = new OneOf<>(TestEnum.KIND2, "Value");
        final var anotherDifferentOneOf = new OneOf<>(TestEnum.KIND1, "AnotherValue");

        assertEquals(true, oneOf.equals(oneOf));
        assertEquals(true, oneOf.equals(sameOneOf));
        assertEquals(false, oneOf.equals(differentOneOf));
        assertEquals(false, oneOf.equals(anotherDifferentOneOf));
        assertEquals(false, oneOf.equals("Value"));
        assertEquals(false, oneOf.equals(TestEnum.KIND1));
    }

    public enum TestEnum implements EnumWithProtoMetadata {
        KIND1,
        KIND2;

        @Override
        public int protoOrdinal() {
            return ordinal();
        }

        @Override
        public String protoName() {
            return name();
        }
    }

}
