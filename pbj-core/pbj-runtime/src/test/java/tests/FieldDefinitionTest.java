// SPDX-License-Identifier: Apache-2.0
package tests;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FieldDefinitionTest {
    @Test
    void nullNameThrows() {
        assertThrows(NullPointerException.class, () ->
                new FieldDefinition(null, FieldType.STRING, false, 1));
    }

    @Test
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () ->
                new FieldDefinition("Name", null, false, 1));
    }

    @Test
    void negativeNumberThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new FieldDefinition("Name", FieldType.STRING, false, -1));
    }
}
