// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import java.util.List;
import java.util.stream.Collectors;

/** Generates opt-in provenance without adding it to the schema's value fields. */
final class CopyBuilderGenerator {
    private CopyBuilderGenerator() {}

    static String maskType(List<Field> fields) {
        return fields.size() <= Long.SIZE ? "long" : "long[]";
    }

    static String constructorArguments(List<Field> fields) {
        return fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", "))
                + (fields.isEmpty() ? "" : ", ") + "$unknownFields";
    }

    static String constructorAssignments(List<Field> fields, boolean tracked) {
        final boolean wide = fields.size() > Long.SIZE;
        return "this.$copyBuilderOrigin = " + (tracked ? "$origin" : "null") + ";\n"
                + "this.$copyBuilderChanged = "
                + (tracked
                        ? (wide ? "$origin == null ? null : $changed.clone()" : "$origin == null ? 0L : $changed")
                        : (wide ? "null" : "0L"))
                + ";\n";
    }

    static String mark(List<Field> fields, int index) {
        return "$copyBuilderChanged" + (fields.size() <= Long.SIZE ? "" : "[" + index / Long.SIZE + "]") + " |= 1L << "
                + index % Long.SIZE + ";";
    }

    static String builderFields(String model, List<Field> fields) {
        return "private " + model + " $copyBuilderOrigin;\nprivate " + maskType(fields) + " $copyBuilderChanged"
                + (fields.size() <= Long.SIZE ? ";" : " = new long[" + (fields.size() + 63) / 64 + "];");
    }

    static String copyBuilderBody(List<Field> fields, boolean tracking) {
        final String create = "new Builder(" + constructorArguments(fields) + ")";
        if (!tracking) return "return " + create + ";";
        return "final var builder = " + create + ";\n"
                + "builder.$copyBuilderOrigin = $copyBuilderOrigin == null ? this : $copyBuilderOrigin;\n"
                + (fields.size() <= Long.SIZE
                        ? "builder.$copyBuilderChanged = $copyBuilderChanged;\n"
                        : "if ($copyBuilderChanged != null) builder.$copyBuilderChanged = $copyBuilderChanged.clone();\n")
                + "return builder;";
    }

    static String modelMembers(String model, List<Field> fields) {
        final StringBuilder cases = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            final Field field = fields.get(i);
            final List<Field> alternatives = field instanceof OneOfField group ? group.fields() : List.of(field);
            cases.append("case ")
                    .append(alternatives.stream()
                            .map(f -> Integer.toString(f.fieldNumber()))
                            .collect(Collectors.joining(", ")))
                    .append(" -> ($copyBuilderChanged")
                    .append(fields.size() <= Long.SIZE ? "" : "[" + i / Long.SIZE + "]")
                    .append(" & (1L << ")
                    .append(i % Long.SIZE)
                    .append(")) != 0;\n");
        }
        return """

                private final transient $model $copyBuilderOrigin;
                private final transient $maskType $copyBuilderChanged;

                /** {@inheritDoc} */
                @Override
                public @Nullable $model $copyBuilderOrigin() {
                    return $copyBuilderOrigin;
                }

                /** {@inheritDoc} */
                @Override
                public boolean $copyBuilderFieldChanged(final int fieldNumber) {
                    if ($copyBuilderOrigin == null) return false;
                    return switch (fieldNumber) {
                        $cases
                        default -> false;
                    };
                }

                /** Declaration-indexed setter candidates. Wide masks are defensive copies. */
                public $maskType $copyBuilderChangedMask() {
                    return $maskCopy;
                }

                /** {@inheritDoc} */
                @Override
                public $model $untracked() {
                    if ($copyBuilderOrigin == null) return this;
                    return new $model($arguments);
                }

                """.replace("$model", model)
                .replace("$maskType", maskType(fields))
                .replace(
                        "$maskCopy",
                        fields.size() <= Long.SIZE
                                ? "$copyBuilderChanged"
                                : "$copyBuilderChanged == null ? new long[" + (fields.size() + 63) / 64
                                        + "] : $copyBuilderChanged.clone()")
                .replace("$cases", cases.toString())
                .replace("$arguments", constructorArguments(fields));
    }
}
