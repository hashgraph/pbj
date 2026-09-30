// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates the transient copy-builder provenance of a model: the origin reference and the mask of fields set by
 * builders. Bits index fields in declaration order (a oneof uses one bit), not by field number.
 */
final class CopyBuilderGenerator {
    /** How a generated model constructor initializes provenance. */
    enum ConstructorTracking {
        /** Tracking generation is disabled, so there is no provenance to initialize. */
        NONE,
        /** A public constructor, which always produces an untracked instance. */
        UNTRACKED,
        /** The private constructor used by {@code Builder.build()} to carry provenance over. */
        TRACKED
    }

    private CopyBuilderGenerator() {}

    /** Whether the mask needs more than one {@code long}. */
    static boolean isWide(List<Field> fields) {
        return fields.size() > Long.SIZE;
    }

    /** Number of {@code long} words in a wide mask. */
    static int maskWords(List<Field> fields) {
        return (fields.size() + Long.SIZE - 1) / Long.SIZE;
    }

    static String maskType(List<Field> fields) {
        return isWide(fields) ? "long[]" : "long";
    }

    /** Arguments for the model constructor that takes every field plus the unknown fields. */
    static String constructorArguments(List<Field> fields) {
        return fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", "))
                + (fields.isEmpty() ? "" : ", ") + "$unknownFields";
    }

    /** Extra parameters of the {@link ConstructorTracking#TRACKED} constructor. */
    static String constructorParameters(String model, List<Field> fields) {
        return ", final " + model + " $origin, final " + maskType(fields) + " $changed";
    }

    /**
     * Provenance assignments for a model constructor. A tracked instance always has a non-null wide mask, even when
     * no setter was called, so readers never need a null check.
     */
    static String constructorAssignments(List<Field> fields, ConstructorTracking tracking) {
        final String changed;
        if (tracking == ConstructorTracking.UNTRACKED) {
            changed = isWide(fields) ? "null" : "0L";
        } else if (isWide(fields)) {
            changed = "$origin == null ? null : $changed == null ? new long[" + maskWords(fields)
                    + "] : $changed.clone()";
        } else {
            changed = "$origin == null ? 0L : $changed";
        }
        return "this.$copyBuilderOrigin = " + (tracking == ConstructorTracking.UNTRACKED ? "null" : "$origin") + ";\n"
                + "this.$copyBuilderChanged = " + changed + ";";
    }

    /** Statement a builder setter uses to record that it set the field at {@code index}. */
    static String mark(List<Field> fields, int index) {
        final String bit = "1L << " + index % Long.SIZE;
        return isWide(fields)
                ? "$markChanged(" + index / Long.SIZE + ", " + bit + ");"
                : "$copyBuilderChanged |= " + bit + ";";
    }

    /** Builder members. A wide mask is allocated on the first setter call, so plain builders don't pay for it. */
    static String builderMembers(String model, List<Field> fields) {
        if (!isWide(fields)) {
            return """
                    private $model $copyBuilderOrigin;
                    private long $copyBuilderChanged;
                    """.replace("$model", model);
        }
        return """
                private $model $copyBuilderOrigin;
                private long[] $copyBuilderChanged;

                private void $markChanged(final int word, final long bit) {
                    if ($copyBuilderChanged == null) {
                        $copyBuilderChanged = new long[$words];
                    }
                    $copyBuilderChanged[word] |= bit;
                }
                """.replace("$model", model).replace("$words", Integer.toString(maskWords(fields)));
    }

    /** Body of {@code copyBuilder()}, which starts a lineage on an untracked instance and continues it otherwise. */
    static String copyBuilderBody(List<Field> fields, boolean tracking) {
        final String create = "new Builder(" + constructorArguments(fields) + ")";
        if (!tracking) {
            return "return " + create + ";";
        }
        return """
                final Builder builder = $create;
                builder.$copyBuilderOrigin = $copyBuilderOrigin == null ? this : $copyBuilderOrigin;
                builder.$copyBuilderChanged = $copyChanged;
                return builder;""".replace("$create", create).replace(
                "$copyChanged",
                isWide(fields)
                        ? "$copyBuilderChanged == null ? null : $copyBuilderChanged.clone()"
                        : "$copyBuilderChanged");
    }

    /** Model fields and accessors, indented as class members. */
    static String modelMembers(String model, List<Field> fields) {
        final String cases = IntStream.range(0, fields.size())
                .mapToObj(i -> {
                    final Field field = fields.get(i);
                    final List<Field> alternatives =
                            field instanceof OneOfField group ? group.fields() : List.of(field);
                    return "case "
                            + alternatives.stream()
                                    .map(f -> Integer.toString(f.fieldNumber()))
                                    .collect(Collectors.joining(", "))
                            + " -> ($copyBuilderChanged" + (isWide(fields) ? "[" + i / Long.SIZE + "]" : "")
                            + " & (1L << " + i % Long.SIZE + ")) != 0;";
                })
                .collect(Collectors.joining("\n"));
        final String fieldChangedBody = fields.isEmpty()
                ? "return false;"
                : """
                if ($copyBuilderOrigin == null) {
                    return false;
                }
                return switch (fieldNumber) {
                    $cases
                    default -> false;
                };""".replace("$cases", cases.indent(DEFAULT_INDENT).strip());
        // spotless:off
        return """
                /** Untracked instance the copy-builder chain started from, or null if this instance is untracked. */
                private final transient $model $copyBuilderOrigin;
                /** Fields set by builders since the origin, one bit per declared field. Excluded from value semantics. */
                private final transient $maskType $copyBuilderChanged;

                /** {@inheritDoc} */
                @Override
                public @Nullable $model $copyBuilderOrigin() {
                    return $copyBuilderOrigin;
                }

                /** {@inheritDoc} */
                @Override
                public boolean $copyBuilderFieldChanged(final int fieldNumber) {
                    $fieldChangedBody
                }

                /**
                 * Get the fields set by builders since the origin, bit {@code i} being the {@code i}-th declared field.
                 *
                 * @return the declaration-indexed mask$maskCopyDoc
                 */
                public $maskType $copyBuilderChangedMask() {
                    return $maskCopy;
                }

                /** {@inheritDoc} */
                @Override
                public $model $untracked() {
                    if ($copyBuilderOrigin == null) {
                        return this;
                    }
                    return new $model($arguments);
                }
                """
                .replace("$fieldChangedBody", fieldChangedBody.indent(DEFAULT_INDENT).strip())
                .replace("$maskCopyDoc", isWide(fields) ? ", as a copy the caller may modify" : "")
                .replace("$maskCopy", isWide(fields)
                        ? "$copyBuilderChanged == null ? new long[" + maskWords(fields) + "] : $copyBuilderChanged.clone()"
                        : "$copyBuilderChanged")
                .replace("$model", model)
                .replace("$maskType", maskType(fields))
                .replace("$arguments", constructorArguments(fields))
                .indent(DEFAULT_INDENT);
        // spotless:on
    }
}
