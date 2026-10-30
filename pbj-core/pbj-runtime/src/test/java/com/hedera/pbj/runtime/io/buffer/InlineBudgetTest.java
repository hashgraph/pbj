// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * HotSpot inlines a hot method only if its bytecode is at most {@code FreqInlineSize}, 325 bytes, and any other call
 * only if the method is at most {@code MaxInlineSize}, 35 bytes. Generated parsers call these {@link ArrayCursor}
 * methods for every field, so they must stay within those budgets: a method over its budget becomes a real call on
 * every field. Move rare cases into a separate method rather than raising a budget.
 */
final class InlineBudgetTest {
    /** HotSpot's default {@code FreqInlineSize}. */
    private static final int HOT = 325;

    /** HotSpot's default {@code MaxInlineSize}. */
    private static final int ANY = 35;

    /** Each method's budget, by name; overloads share it. */
    private static final Map<String, Integer> ARRAY_CURSOR = Map.ofEntries(
            // varint() is 39 bytes, just over MaxInlineSize; it is called from hot loops, where 325 applies
            Map.entry("varint", HOT),
            Map.entry("varintWide", HOT),
            Map.entry("tag", ANY),
            Map.entry("varintZigZag", ANY),
            Map.entry("at", HOT),
            Map.entry("length", HOT),
            Map.entry("bool", HOT),
            Map.entry("fixed32", HOT),
            Map.entry("fixed64", HOT),
            Map.entry("string", HOT),
            Map.entry("bytes", HOT),
            Map.entry("pushLimit", ANY),
            Map.entry("popLimit", ANY),
            Map.entry("hasRemaining", ANY),
            Map.entry("position", ANY),
            Map.entry("limit", ANY));

    static ClassModel classModel(final Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return ClassFile.of().parse(Objects.requireNonNull(in).readAllBytes());
        }
    }

    /**
     * Check that every method with a budget fits it.
     *
     * @param type the class
     * @param budgets each method's budget, by name
     */
    static void assertWithinBudgets(final Class<?> type, final Map<String, Integer> budgets) throws IOException {
        final StringBuilder over = new StringBuilder();
        int checked = 0;
        for (final MethodModel method : classModel(type).methods()) {
            final Integer budget = budgets.get(method.methodName().stringValue());
            final CodeAttribute code = method.code()
                    .filter(CodeAttribute.class::isInstance)
                    .map(CodeAttribute.class::cast)
                    .orElse(null);
            if (budget == null || code == null) {
                continue;
            }
            checked++;
            final int length = code.codeLength();
            if (length > budget) {
                over.append("%n  %s%s is %d bytes, over its budget of %d"
                        .formatted(
                                method.methodName().stringValue(),
                                method.methodType().stringValue(),
                                length,
                                budget));
            }
        }
        assertTrue(checked >= budgets.size(), "some methods with budgets no longer exist in " + type.getName());
        assertTrue(over.isEmpty(), type.getSimpleName() + " methods too large to inline:" + over);
    }

    @Test
    void arrayCursorMethodsFitTheirInlineBudgets() throws IOException {
        assertWithinBudgets(ArrayCursor.class, ARRAY_CURSOR);
    }
}
