// SPDX-License-Identifier: Apache-2.0
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.ComparableOneOf;
import com.hedera.pbj.runtime.EnumWithProtoMetadata;
import org.junit.jupiter.api.Test;

class ComparableOneOfTest {
    @Test
    void nullNameIsOK() {
        final var oneOf = new ComparableOneOf<>(TestEnum.KIND1, null);
        assertNull(oneOf.value());
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () -> new ComparableOneOf<>(null, "Value"));
    }

    @Test
    void asReturnsValue() {
        final var oneOf = new ComparableOneOf<>(TestEnum.KIND1, "Value");
        assertEquals("Value", oneOf.as());
    }

    @Test
    void hashCodeReturnsHashCode() {
        final var oneOf = new ComparableOneOf<>(TestEnum.KIND1, "Value");
        assertEquals(
                (31 + Integer.hashCode(TestEnum.KIND1.protoOrdinal())) * 31 + "Value".hashCode(), oneOf.hashCode());
    }

    @Test
    void equalsWorks() {
        final var oneOf = new ComparableOneOf<>(TestEnum.KIND1, "Value");
        final var sameComparableOneOf = new ComparableOneOf<>(TestEnum.KIND1, "Value");
        final var differentComparableOneOf = new ComparableOneOf<>(TestEnum.KIND2, "Value");
        final var anotherDifferentComparableOneOf = new ComparableOneOf<>(TestEnum.KIND1, "AnotherValue");

        assertEquals(true, oneOf.equals(oneOf));
        assertEquals(true, oneOf.equals(sameComparableOneOf));
        assertEquals(false, oneOf.equals(differentComparableOneOf));
        assertEquals(false, oneOf.equals(anotherDifferentComparableOneOf));
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
