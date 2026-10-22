// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.generators.protobuf.LazyGetProtobufSizeMethodGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generates shallow, top-level diff helpers for selected root models. Masks index fields in declaration order, with one bit
 * per oneof; cleared-field lists use protobuf field numbers.
 */
final class DiffGenerator {
    private DiffGenerator() {}

    /** One entry of a cleared-fields list, generated in ascending field-number order. */
    private record Clear(int fieldNumber, String condition) {}

    static String generate(String model, List<Field> fields, String schema, boolean tracking) {
        final boolean wide = CopyBuilderGenerator.isWide(fields);
        final String maskType = CopyBuilderGenerator.maskType(fields);
        return String.join(
                        "\n",
                        generateDiff(model, fields, maskType, wide, tracking),
                        generatePartial(model, fields, maskType, wide),
                        generateWorthPartial(model, fields, schema, maskType, wide),
                        generateRequireDiffable(model),
                        generateClearedFields(model, fields, maskType, wide),
                        generateApplyPartial(model, fields),
                        generateEqualsForDiff(model, fields))
                .lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n", "", "\n"))
                .indent(DEFAULT_INDENT);
    }

    private static String generateDiff(
            String model, List<Field> fields, String maskType, boolean wide, boolean tracking) {
        final String trackingCheck = tracking ? """
                if (next.$copyBuilderOrigin == prior) {
                    return diff(prior, next, next.$copyBuilderChanged);
                }
                """.indent(DEFAULT_INDENT) : "";
        final String comparisons = IntStream.range(0, fields.size())
                .mapToObj(i -> {
                    final Field field = fields.get(i);
                    final String name = field.nameCamelFirstLower();
                    return """
                            if ($candidate && $different) {
                                $resultWord |= $bit;
                            }
                            """.replace("$candidate", isSet("candidates", i, wide))
                            .replace("$different", different(field, "prior." + name, "next." + name))
                            .replace("$resultWord", word("result", i, wide))
                            .replace("$bit", bit(i));
                })
                .collect(Collectors.joining());
        return """
                /**
                 * Find the fields whose values differ. When {@code next} was copy-built from exactly {@code prior},
                 * only the fields its builders set are compared; otherwise every field is compared.
                 *
                 * @param prior the prior value
                 * @param next the next value
                 * @return mask of differing fields, bit {@code i} being the {@code i}-th declared field
                 */
                public static $maskType diff(final $model prior, final $model next) {
                $trackingCheck
                    return diff(prior, next, $allFields);
                }

                /**
                 * Compare candidate fields by value, preserving raw NaN payloads. Other scalar values use
                 * {@code ==}; objects use identity and then an equality that implies equal encodings.
                 *
                 * @param prior the prior value
                 * @param next the next value
                 * @param candidates mask of the fields to compare
                 * @return mask of the candidate fields whose values differ
                 */
                public static $maskType diff(final $model prior, final $model next, final $maskType candidates) {
                    requireNonNull(prior);
                    requireNonNull(next);
                    $requireDiffable(prior);
                    $requireDiffable(next);
                $maskCheck
                    $resultDeclaration
                $comparisons
                    return result;
                }
                """.replace("$maskType", maskType)
                .replace("$model", model)
                .replace("$trackingCheck\n", trackingCheck)
                .replace("$allFields", allFields(fields, wide))
                .replace(
                        "$maskCheck\n",
                        maskValidation(fields, wide, "candidates").indent(DEFAULT_INDENT))
                .replace(
                        "$resultDeclaration",
                        wide
                                ? "final long[] result = new long[" + CopyBuilderGenerator.maskWords(fields) + "];"
                                : "long result = 0L;")
                .replace("$comparisons\n", comparisons.indent(DEFAULT_INDENT));
    }

    private static String generatePartial(String model, List<Field> fields, String maskType, boolean wide) {
        final List<String> arguments = IntStream.range(0, fields.size())
                .mapToObj(i -> {
                    final String name = fields.get(i).nameCamelFirstLower();
                    return isSet("mask", i, wide) + " ? next." + name + " : DEFAULT." + name;
                })
                .toList();
        return """
                /**
                 * Keep only the selected fields, resetting every other field to its default. The result encodes
                 * to just the selected fields that are not default; see {@link #clearedFields}.
                 *
                 * @param next the value to take the selected fields from
                 * @param mask mask of the fields to keep
                 * @return the partial value
                 */
                public static $model partial(final $model next, final $maskType mask) {
                    $requireDiffable(next);
                $maskCheck
                $construction
                }
                """.replace("$model", model)
                .replace("$maskType", maskType)
                .replace("$maskCheck\n", maskValidation(fields, wide, "mask").indent(DEFAULT_INDENT))
                .replace("$construction\n", construct(model, arguments).indent(DEFAULT_INDENT));
    }

    private static String generateWorthPartial(
            String model, List<Field> fields, String schema, String maskType, boolean wide) {
        final String measurements = IntStream.range(0, fields.size())
                .boxed()
                .sorted(Comparator.comparingInt(i -> sizingCost(fields.get(i))))
                .map(i -> """
                        if ($condition) {
                        $fieldSize
                            if (_size >= $target) {
                                return true;
                            }
                        }
                        """.replace("$condition", isClear("$mask", i, wide)).replace(
                        "$fieldSize\n",
                        LazyGetProtobufSizeMethodGenerator.buildFieldSizeOfLines(
                                        null, schema, List.of(fields.get(i)), Field::nameCamelFirstLower, true)
                                .stripTrailing()
                                .stripIndent()
                                .lines()
                                .map(String::stripTrailing)
                                .collect(Collectors.joining("\n"))
                                .indent(DEFAULT_INDENT)))
                .collect(Collectors.joining());
        return """
                /**
                 * Check whether the fields outside {@code mask} encode to at least {@code minSaving + overhead}
                 * bytes. Fields are measured cheapest first (scalars, then strings and bytes, then messages and
                 * oneofs, then repeated fields and maps), stopping as soon as the target is reached, so the full
                 * encoded size is never computed.
                 *
                 * @param next the value that would be sent in full
                 * @param mask mask of the fields a partial value would carry
                 * @param minSaving the minimum number of bytes worth saving, not negative
                 * @param overhead the extra bytes a partial update costs, not negative
                 * @return true if sending a partial value saves enough bytes
                 */
                public static boolean worthPartial(final $model next, final $maskType mask, final int minSaving, final int overhead) {
                    $requireDiffable(next);
                $maskCheck
                    if (minSaving < 0 || overhead < 0) {
                        throw new IllegalArgumentException("Costs must not be negative");
                    }
                    return next.$unchangedSizeReaches(mask, (long) minSaving + overhead);
                }

                /** Sum the encoded sizes of the fields outside {@code mask}, stopping once they reach the target. */
                private boolean $unchangedSizeReaches(final $maskType $mask, final long $target) {
                    if ($target == 0) {
                        return true;
                    }
                $sizeInit
                $measurements
                    return false;
                }
                """.replace("$model", model)
                .replace("$maskType", maskType)
                .replace("$maskCheck\n", maskValidation(fields, wide, "mask").indent(DEFAULT_INDENT))
                .replace("$sizeInit\n", fields.isEmpty() ? "" : "int _size = 0;\n".indent(DEFAULT_INDENT))
                .replace("$measurements\n", measurements.indent(DEFAULT_INDENT));
    }

    private static String generateRequireDiffable(String model) {
        return """
                /** Diffs are top-level field replacements, which cannot carry unknown fields. */
                private static void $requireDiffable(final $model value) {
                    if (!value.getUnknownFields().isEmpty()) {
                        throw new UnsupportedOperationException("Unknown fields require a full replacement");
                    }
                }
                """.replace("$model", model);
    }

    private static String generateClearedFields(String model, List<Field> fields, String maskType, boolean wide) {
        final List<Clear> clears = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            final Field field = fields.get(i);
            final String name = field.nameCamelFirstLower();
            if (field instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    clears.add(new Clear(
                            alt.fieldNumber(),
                            isSet("mask", i, wide) + " && next." + name + ".kind().protoOrdinal() != "
                                    + alt.fieldNumber() + " && (prior == null || prior." + name
                                    + ".kind().protoOrdinal() == " + alt.fieldNumber() + ")"));
                }
            } else {
                clears.add(new Clear(
                        field.fieldNumber(), isSet("mask", i, wide) + " && " + absent(field, "next." + name)));
            }
        }
        clears.sort(Comparator.comparingInt(Clear::fieldNumber));
        final String clearChecks = clears.stream()
                .map(clear -> """
                        if ($condition) {
                            cleared[count++] = $number;
                        }
                        """.replace("$condition", clear.condition())
                        .replace("$number", Integer.toString(clear.fieldNumber())))
                .collect(Collectors.joining());
        final String clearBody = clears.isEmpty()
                ? "return new int[0];\n"
                : """
                final int[] cleared = new int[$count];
                int count = 0;
                $clearChecks
                return count == cleared.length ? cleared : java.util.Arrays.copyOf(cleared, count);
                """.replace("$count", Integer.toString(clears.size())).replace("$clearChecks\n", clearChecks);
        return """
                /**
                 * List the selected fields whose new value is the default, including every inactive alternative
                 * of a selected oneof.
                 *
                 * @param next the next value
                 * @param mask mask of the selected fields
                 * @return the field numbers to clear, in ascending order
                 */
                public static int[] clearedFields(final $model next, final $maskType mask) {
                    return clearedFields(null, next, mask);
                }

                /**
                 * List the selected fields whose new value is the default. With a prior value, a selected oneof
                 * clears only the alternative that was set before, if it is no longer set.
                 *
                 * @param prior the prior value, or null to clear every inactive oneof alternative
                 * @param next the next value
                 * @param mask mask of the selected fields
                 * @return the field numbers to clear, in ascending order
                 */
                public static int[] clearedFields(final @Nullable $model prior, final $model next, final $maskType mask) {
                    $requireDiffable(next);
                    if (prior != null) {
                        $requireDiffable(prior);
                    }
                $maskCheck
                $clearBody
                }
                """.replace("$model", model)
                .replace("$maskType", maskType)
                .replace("$maskCheck\n", maskValidation(fields, wide, "mask").indent(DEFAULT_INDENT))
                .replace("$clearBody\n", clearBody.indent(DEFAULT_INDENT));
    }

    private static String generateApplyPartial(String model, List<Field> fields) {
        final String clearFlags = fields.stream()
                .map(field -> "boolean " + clearFlag(field) + " = false;\n")
                .collect(Collectors.joining());
        final StringBuilder clearCases = new StringBuilder();
        final StringBuilder oneofChecks = new StringBuilder();
        for (Field field : fields) {
            final String name = field.nameCamelFirstLower();
            if (field instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    clearCases.append("""
                            case $number -> {
                            $conflictCheck
                                if (prior.$name.kind().protoOrdinal() == $number) {
                                    $clearFlag = true;
                                }
                            }
                            """.replace("$number", Integer.toString(alt.fieldNumber()))
                            .replace(
                                    "$conflictCheck\n",
                                    throwIfReplacedAndCleared("partial." + name + ".kind().protoOrdinal() == "
                                                    + alt.fieldNumber())
                                            .indent(DEFAULT_INDENT))
                            .replace("$name", name)
                            .replace("$clearFlag", clearFlag(field)));
                }
                oneofChecks.append("""
                        if ($partialPresent && $priorPresent
                                && partial.$name.kind() != prior.$name.kind()
                                && !$clearFlag) {
                            throw new IllegalArgumentException("Oneof switch must clear its old alternative");
                        }
                        """.replace("$partialPresent", present(field, "partial." + name))
                        .replace("$priorPresent", present(field, "prior." + name))
                        .replace("$name", name)
                        .replace("$clearFlag", clearFlag(field)));
            } else {
                clearCases.append("""
                        case $number -> {
                        $conflictCheck
                            $clearFlag = true;
                        }
                        """.replace("$number", Integer.toString(field.fieldNumber()))
                        .replace(
                                "$conflictCheck\n",
                                throwIfReplacedAndCleared(present(field, "partial." + name))
                                        .indent(DEFAULT_INDENT))
                        .replace("$clearFlag", clearFlag(field)));
            }
        }
        final List<String> arguments = fields.stream()
                .map(field -> {
                    final String name = field.nameCamelFirstLower();
                    return present(field, "partial." + name) + " ? partial." + name + " : " + clearFlag(field)
                            + " ? DEFAULT." + name + " : prior." + name;
                })
                .toList();
        return """
                /**
                 * Apply a partial value to a prior value without serialization, for consumers that hold models
                 * rather than bytes. The object-level equivalent of {@link com.hedera.pbj.runtime.PartialApplier#splice}.
                 *
                 * @param prior the prior value
                 * @param partial the replacement fields, as produced by {@link #partial}
                 * @param cleared the field numbers to reset to their defaults, in ascending order
                 * @return the next value
                 * @throws IllegalArgumentException if a field is both replaced and cleared, a field number is
                 *         unknown, or a oneof switch does not clear its old alternative
                 */
                public static $model applyPartial(final $model prior, final $model partial, final int[] cleared) {
                    $requireDiffable(prior);
                    $requireDiffable(partial);
                    com.hedera.pbj.runtime.PartialApplier.validateCleared(cleared);
                $clearFlags
                    for (final int number : cleared) {
                        switch (number) {
                $clearCases
                            default -> throw new IllegalArgumentException("Unknown cleared field: " + number);
                        }
                    }
                $oneofChecks
                $construction
                }
                """.replace("$model", model)
                .replace("$clearFlags\n", clearFlags.indent(DEFAULT_INDENT))
                .replace("$clearCases\n", clearCases.toString().indent(DEFAULT_INDENT * 3))
                .replace("$oneofChecks\n", oneofChecks.toString().indent(DEFAULT_INDENT))
                .replace("$construction\n", construct(model, arguments).indent(DEFAULT_INDENT));
    }

    private static String throwIfReplacedAndCleared(String replaced) {
        return """
                if ($replaced) {
                    throw new IllegalArgumentException("Field is both replaced and cleared: " + number);
                }
                """.replace("$replaced", replaced);
    }

    static String generateEqualsForDiff(String model, List<Field> fields) {
        final String comparisons = fields.stream()
                .map(field -> {
                    final String name = field.nameCamelFirstLower();
                    return """
                            if ($different) {
                                return false;
                            }
                            """.replace("$different", different(field, "this." + name, "that." + name));
                })
                .collect(Collectors.joining());
        return """
                /** {@inheritDoc} */
                @Override
                public boolean $equalsForDiff(final Object other) {
                    if (this == other) {
                        return true;
                    }
                    if (!(other instanceof $model that)) {
                        return false;
                    }
                $comparisons
                    return getUnknownFields().equals(that.getUnknownFields());
                }
                """.replace("$model", model).replace("$comparisons\n", comparisons.indent(DEFAULT_INDENT));
    }

    /** Emit a model construction with no unknown fields, one argument per line. */
    private static String construct(String model, List<String> arguments) {
        final List<String> all = new ArrayList<>(arguments);
        all.add("null");
        return """
                return new $model(
                $arguments);
                """.replace("$model", model).replace(
                "$arguments", String.join(",\n", all).indent(DEFAULT_INDENT * 2).stripTrailing());
    }

    private static String maskValidation(List<Field> fields, boolean wide, String mask) {
        if (!wide) return "";
        return """
                if ($mask.length != $words) {
                    throw new IllegalArgumentException("Mask must have $words words");
                }
                """.replace("$mask", mask).replace("$words", Integer.toString(CopyBuilderGenerator.maskWords(fields)));
    }

    private static String allFields(List<Field> fields, boolean wide) {
        if (!wide) return "-1L";
        return IntStream.range(0, CopyBuilderGenerator.maskWords(fields))
                .mapToObj(i -> "-1L")
                .collect(Collectors.joining(", ", "new long[] {", "}"));
    }

    private static String clearFlag(Field f) {
        return "clear" + f.nameCamelFirstUpper();
    }

    private static String present(Field f, String value) {
        if (f.repeated() || f.type() == Field.FieldType.MAP) return "!" + value + ".isEmpty()";
        if (f.optionalValueType()) return value + " != null";
        return switch (f.type()) {
            case MESSAGE -> value + " != null";
            case STRING -> "!" + value + ".isEmpty()";
            case BYTES -> value + ".length() != 0";
            case ONE_OF -> value + ".kind().protoOrdinal() > 0";
            case ENUM ->
                value + " != null && com.hedera.pbj.runtime.EnumWithProtoMetadata.protoOrdinal(" + value + ") != 0";
            case BOOL -> value;
            default -> value + " != 0";
        };
    }

    private static String absent(Field f, String value) {
        if (f.repeated() || f.type() == Field.FieldType.MAP) return value + ".isEmpty()";
        if (f.optionalValueType()) return value + " == null";
        return switch (f.type()) {
            case MESSAGE -> value + " == null";
            case STRING -> value + ".isEmpty()";
            case BYTES -> value + ".length() == 0";
            case ONE_OF -> value + ".kind().protoOrdinal() == 0";
            case ENUM ->
                "(" + value + " == null || com.hedera.pbj.runtime.EnumWithProtoMetadata.protoOrdinal(" + value
                        + ") == 0)";
            case BOOL -> "!" + value;
            default -> value + " == 0";
        };
    }

    private static String word(String mask, int i, boolean wide) {
        return mask + (wide ? "[" + i / Long.SIZE + "]" : "");
    }

    private static String bit(int i) {
        return "1L << " + i % Long.SIZE;
    }

    private static String isSet(String mask, int i, boolean wide) {
        return "(" + word(mask, i, wide) + " & (" + bit(i) + ")) != 0";
    }

    private static String isClear(String mask, int i, boolean wide) {
        return "(" + word(mask, i, wide) + " & (" + bit(i) + ")) == 0";
    }

    private static String different(Field f, String a, String b) {
        if (f.optionalValueType()) {
            final boolean floating =
                    f.messageType().equals("FloatValue") || f.messageType().equals("DoubleValue");
            return "!" + (floating ? "com.hedera.pbj.runtime.DiffSupport.equal" : "java.util.Objects.equals") + "(" + a
                    + ", " + b + ")";
        }
        if (f.repeated()) {
            final boolean needsWireEquality =
                    switch (f.type()) {
                        case FLOAT, DOUBLE, ENUM, MESSAGE -> true;
                        default -> false;
                    };
            return "!" + (needsWireEquality ? "com.hedera.pbj.runtime.DiffSupport.equal" : "java.util.Objects.equals")
                    + "(" + a + ", " + b + ")";
        }
        return switch (f.type()) {
            case STRING, BYTES -> "!java.util.Objects.equals(" + a + ", " + b + ")";
            case MESSAGE, MAP, ONE_OF -> "!com.hedera.pbj.runtime.DiffSupport.equal(" + a + ", " + b + ")";
            // Both signs of zero are omitted for singular scalars; NaNs must compare their raw payloads.
            case FLOAT ->
                a + " != " + b + " && java.lang.Float.floatToRawIntBits(" + a
                        + ") != java.lang.Float.floatToRawIntBits(" + b + ")";
            case DOUBLE ->
                a + " != " + b + " && java.lang.Double.doubleToRawLongBits(" + a
                        + ") != java.lang.Double.doubleToRawLongBits(" + b + ")";
            case ENUM ->
                "(" + a + " == null ? 0 : com.hedera.pbj.runtime.EnumWithProtoMetadata.protoOrdinal(" + a + ")) != ("
                        + b + " == null ? 0 : com.hedera.pbj.runtime.EnumWithProtoMetadata.protoOrdinal(" + b + "))";
            default -> a + " != " + b;
        };
    }

    /** Relative cost of measuring a field, used to order {@code worthPartial}. */
    private static int sizingCost(Field f) {
        if (f.repeated() || f.type() == Field.FieldType.MAP) return 3;
        if (f.optionalValueType()) {
            return f.messageType().equals("StringValue") || f.messageType().equals("BytesValue") ? 1 : 0;
        }
        return switch (f.type()) {
            case MESSAGE, ONE_OF -> 2;
            case STRING, BYTES -> 1;
            default -> 0;
        };
    }
}
