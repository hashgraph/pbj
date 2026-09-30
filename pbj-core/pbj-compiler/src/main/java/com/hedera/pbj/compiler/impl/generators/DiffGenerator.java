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
 * Generates shallow, top-level diff helpers for every model. Masks index fields in declaration order, with one bit
 * per oneof; cleared-field lists use protobuf field numbers.
 */
final class DiffGenerator {
    private DiffGenerator() {}

    /** Minimal line-oriented writer that keeps generated code indented like hand-written code. */
    private static final class Code {
        private final StringBuilder out = new StringBuilder();
        private int depth;

        Code line(String text) {
            if (!text.isEmpty()) out.append(" ".repeat(depth * DEFAULT_INDENT)).append(text);
            out.append('\n');
            return this;
        }

        /** Append pre-generated lines at the current depth. */
        Code lines(String text) {
            text.stripTrailing().lines().forEach(l -> line(l.stripTrailing()));
            return this;
        }

        Code open(String text) {
            line(text);
            depth++;
            return this;
        }

        Code close(String text) {
            depth--;
            return line(text);
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }

    /** One entry of a cleared-fields list, generated in ascending field-number order. */
    private record Clear(int fieldNumber, String condition) {}

    static String generate(String model, List<Field> fields, String schema, boolean tracking) {
        final boolean wide = CopyBuilderGenerator.isWide(fields);
        final String maskType = CopyBuilderGenerator.maskType(fields);
        final Code code = new Code();
        generateDiff(code, model, fields, maskType, wide, tracking);
        generatePartial(code, model, fields, maskType, wide);
        generateWorthPartial(code, model, fields, schema, maskType, wide);
        generateRequireDiffable(code, model);
        generateClearedFields(code, model, fields, maskType, wide);
        generateApplyPartial(code, model, fields);
        generateEqualsForDiff(code, model, fields);
        return code.toString().indent(DEFAULT_INDENT);
    }

    private static void generateDiff(
            Code code, String model, List<Field> fields, String maskType, boolean wide, boolean tracking) {
        code.line("/**")
                .line(
                        " * Find the fields whose values differ. When {@code next} was copy-built from exactly {@code prior},")
                .line(" * only the fields its builders set are compared; otherwise every field is compared.")
                .line(" *")
                .line(" * @param prior the prior value")
                .line(" * @param next the next value")
                .line(" * @return mask of differing fields, bit {@code i} being the {@code i}-th declared field")
                .line(" */")
                .open("public static " + maskType + " diff(final " + model + " prior, final " + model + " next) {");
        if (tracking) {
            code.open("if (next.$copyBuilderOrigin == prior) {")
                    .line("return diff(prior, next, next.$copyBuilderChanged);")
                    .close("}");
        }
        code.line("return diff(prior, next, " + allFields(fields, wide) + ");")
                .close("}")
                .line("");

        code.line("/**")
                .line(
                        " * Compare candidate fields by value: primitives by {@code ==}, other values by identity and then")
                .line(" * by an equality that implies equal encodings.")
                .line(" *")
                .line(" * @param prior the prior value")
                .line(" * @param next the next value")
                .line(" * @param candidates mask of the fields to compare")
                .line(" * @return mask of the candidate fields whose values differ")
                .line(" */")
                .open("public static " + maskType + " diff(final " + model + " prior, final " + model + " next, final "
                        + maskType + " candidates) {")
                .line("requireNonNull(prior);")
                .line("requireNonNull(next);")
                .line("$requireDiffable(prior);")
                .line("$requireDiffable(next);");
        checkMaskLength(code, fields, wide, "candidates");
        code.line(
                wide
                        ? "final long[] result = new long[" + CopyBuilderGenerator.maskWords(fields) + "];"
                        : "long result = 0L;");
        for (int i = 0; i < fields.size(); i++) {
            final String name = fields.get(i).nameCamelFirstLower();
            code.open("if (" + isSet("candidates", i, wide) + " && "
                            + different(fields.get(i), "prior." + name, "next." + name) + ") {")
                    .line(word("result", i, wide) + " |= " + bit(i) + ";")
                    .close("}");
        }
        code.line("return result;").close("}").line("");
    }

    private static void generatePartial(Code code, String model, List<Field> fields, String maskType, boolean wide) {
        code.line("/**")
                .line(
                        " * Keep only the selected fields, resetting every other field to its default. The result encodes")
                .line(" * to just the selected fields that are not default; see {@link #clearedFields}.")
                .line(" *")
                .line(" * @param next the value to take the selected fields from")
                .line(" * @param mask mask of the fields to keep")
                .line(" * @return the partial value")
                .line(" */")
                .open("public static " + model + " partial(final " + model + " next, final " + maskType + " mask) {")
                .line("$requireDiffable(next);");
        checkMaskLength(code, fields, wide, "mask");
        final List<String> arguments = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            final String name = fields.get(i).nameCamelFirstLower();
            arguments.add(isSet("mask", i, wide) + " ? next." + name + " : DEFAULT." + name);
        }
        construct(code, model, arguments);
        code.close("}").line("");
    }

    private static void generateWorthPartial(
            Code code, String model, List<Field> fields, String schema, String maskType, boolean wide) {
        code.line("/**")
                .line(
                        " * Check whether the fields outside {@code mask} encode to at least {@code minSaving + overhead}")
                .line(
                        " * bytes. Fields are measured cheapest first (scalars, then strings and bytes, then messages and")
                .line(
                        " * oneofs, then repeated fields and maps), stopping as soon as the target is reached, so the full")
                .line(" * encoded size is never computed.")
                .line(" *")
                .line(" * @param next the value that would be sent in full")
                .line(" * @param mask mask of the fields a partial value would carry")
                .line(" * @param minSaving the minimum number of bytes worth saving, not negative")
                .line(" * @param overhead the extra bytes a partial update costs, not negative")
                .line(" * @return true if sending a partial value saves enough bytes")
                .line(" */")
                .open("public static boolean worthPartial(final " + model + " next, final " + maskType
                        + " mask, final int minSaving, final int overhead) {")
                .line("$requireDiffable(next);");
        checkMaskLength(code, fields, wide, "mask");
        code.open("if (minSaving < 0 || overhead < 0) {")
                .line("throw new IllegalArgumentException(\"Costs must not be negative\");")
                .close("}")
                .line("return next.$unchangedSizeReaches(mask, (long) minSaving + overhead);")
                .close("}")
                .line("");

        code.line(
                        "/** Sum the encoded sizes of the fields outside {@code mask}, stopping once they reach the target. */")
                .open("private boolean $unchangedSizeReaches(final " + maskType + " mask, final long target) {")
                .open("if (target == 0) {")
                .line("return true;")
                .close("}");
        if (!fields.isEmpty()) {
            code.line("int _size = 0;");
        }
        final List<Integer> order = IntStream.range(0, fields.size())
                .boxed()
                .sorted(Comparator.comparingInt(i -> sizingCost(fields.get(i))))
                .toList();
        for (int i : order) {
            code.open("if (" + isClear("mask", i, wide) + ") {")
                    .lines(LazyGetProtobufSizeMethodGenerator.buildFieldSizeOfLines(
                                    null, schema, List.of(fields.get(i)), Field::nameCamelFirstLower, true)
                            .stripTrailing()
                            .stripIndent())
                    .open("if (_size >= target) {")
                    .line("return true;")
                    .close("}")
                    .close("}");
        }
        code.line("return false;").close("}").line("");
    }

    private static void generateRequireDiffable(Code code, String model) {
        code.line("/** Diffs are top-level field replacements, which cannot carry unknown fields. */")
                .open("private static void $requireDiffable(final " + model + " value) {")
                .open("if (!value.getUnknownFields().isEmpty()) {")
                .line("throw new UnsupportedOperationException(\"Unknown fields require a full replacement\");")
                .close("}")
                .close("}")
                .line("");
    }

    private static void generateClearedFields(
            Code code, String model, List<Field> fields, String maskType, boolean wide) {
        code.line("/**")
                .line(
                        " * List the selected fields whose new value is the default, including every inactive alternative")
                .line(" * of a selected oneof.")
                .line(" *")
                .line(" * @param next the next value")
                .line(" * @param mask mask of the selected fields")
                .line(" * @return the field numbers to clear, in ascending order")
                .line(" */")
                .open("public static int[] clearedFields(final " + model + " next, final " + maskType + " mask) {")
                .line("return clearedFields(null, next, mask);")
                .close("}")
                .line("");

        final List<Clear> clears = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            final String name = f.nameCamelFirstLower();
            if (f instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    clears.add(new Clear(
                            alt.fieldNumber(),
                            isSet("mask", i, wide) + " && next." + name + ".kind().protoOrdinal() != "
                                    + alt.fieldNumber() + " && (prior == null || prior." + name
                                    + ".kind().protoOrdinal() == " + alt.fieldNumber() + ")"));
                }
            } else {
                clears.add(new Clear(f.fieldNumber(), isSet("mask", i, wide) + " && " + absent(f, "next." + name)));
            }
        }
        clears.sort(Comparator.comparingInt(Clear::fieldNumber));

        code.line("/**")
                .line(
                        " * List the selected fields whose new value is the default. With a prior value, a selected oneof")
                .line(" * clears only the alternative that was set before, if it is no longer set.")
                .line(" *")
                .line(" * @param prior the prior value, or null to clear every inactive oneof alternative")
                .line(" * @param next the next value")
                .line(" * @param mask mask of the selected fields")
                .line(" * @return the field numbers to clear, in ascending order")
                .line(" */")
                .open("public static int[] clearedFields(final @Nullable " + model + " prior, final " + model
                        + " next, final " + maskType + " mask) {")
                .line("$requireDiffable(next);")
                .open("if (prior != null) {")
                .line("$requireDiffable(prior);")
                .close("}");
        checkMaskLength(code, fields, wide, "mask");
        if (clears.isEmpty()) {
            code.line("return new int[0];").close("}").line("");
            return;
        }
        code.line("final int[] cleared = new int[" + clears.size() + "];").line("int count = 0;");
        for (Clear clear : clears) {
            code.open("if (" + clear.condition() + ") {")
                    .line("cleared[count++] = " + clear.fieldNumber() + ";")
                    .close("}");
        }
        code.line("return count == cleared.length ? cleared : Arrays.copyOf(cleared, count);")
                .close("}")
                .line("");
    }

    private static void generateApplyPartial(Code code, String model, List<Field> fields) {
        code.line("/**")
                .line(" * Apply a partial value to a prior value without serialization, for consumers that hold models")
                .line(" * rather than bytes. The object-level equivalent of {@link PartialApplier#splice}.")
                .line(" *")
                .line(" * @param prior the prior value")
                .line(" * @param partial the replacement fields, as produced by {@link #partial}")
                .line(" * @param cleared the field numbers to reset to their defaults, in ascending order")
                .line(" * @return the next value")
                .line(" * @throws IllegalArgumentException if a field is both replaced and cleared, a field number is")
                .line(" *         unknown, or a oneof switch does not clear its old alternative")
                .line(" */")
                .open("public static " + model + " applyPartial(final " + model + " prior, final " + model
                        + " partial, final int[] cleared) {")
                .line("$requireDiffable(prior);")
                .line("$requireDiffable(partial);")
                .line("PartialApplier.validateCleared(cleared);");
        for (Field f : fields) {
            code.line("boolean " + clearFlag(f) + " = false;");
        }
        code.open("for (final int number : cleared) {").open("switch (number) {");
        for (Field f : fields) {
            final String name = f.nameCamelFirstLower();
            if (f instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    code.open("case " + alt.fieldNumber() + " -> {");
                    throwIfReplacedAndCleared(
                            code, "partial." + name + ".kind().protoOrdinal() == " + alt.fieldNumber());
                    code.open("if (prior." + name + ".kind().protoOrdinal() == " + alt.fieldNumber() + ") {")
                            .line(clearFlag(f) + " = true;")
                            .close("}")
                            .close("}");
                }
            } else {
                code.open("case " + f.fieldNumber() + " -> {");
                throwIfReplacedAndCleared(code, present(f, "partial." + name));
                code.line(clearFlag(f) + " = true;").close("}");
            }
        }
        code.line("default -> throw new IllegalArgumentException(\"Unknown cleared field: \" + number);")
                .close("}")
                .close("}");
        for (Field f : fields) {
            if (!(f instanceof OneOfField)) continue;
            final String name = f.nameCamelFirstLower();
            code.open("if (" + present(f, "partial." + name) + " && " + present(f, "prior." + name))
                    .line("    && partial." + name + ".kind() != prior." + name + ".kind()")
                    .line("    && !" + clearFlag(f) + ") {")
                    .line("throw new IllegalArgumentException(\"Oneof switch must clear its old alternative\");")
                    .close("}");
        }
        final List<String> arguments = new ArrayList<>();
        for (Field f : fields) {
            final String name = f.nameCamelFirstLower();
            arguments.add(present(f, "partial." + name) + " ? partial." + name + " : " + clearFlag(f) + " ? DEFAULT."
                    + name + " : prior." + name);
        }
        construct(code, model, arguments);
        code.close("}").line("");
    }

    private static void throwIfReplacedAndCleared(Code code, String replaced) {
        code.open("if (" + replaced + ") {")
                .line("throw new IllegalArgumentException(\"Field is both replaced and cleared: \" + number);")
                .close("}");
    }

    private static void generateEqualsForDiff(Code code, String model, List<Field> fields) {
        code.line("/** {@inheritDoc} */")
                .line("@Override")
                .open("public boolean $equalsForDiff(final Object other) {")
                .open("if (this == other) {")
                .line("return true;")
                .close("}")
                .open("if (!(other instanceof " + model + " that)) {")
                .line("return false;")
                .close("}");
        for (Field f : fields) {
            final String name = f.nameCamelFirstLower();
            code.open("if (" + different(f, "this." + name, "that." + name) + ") {")
                    .line("return false;")
                    .close("}");
        }
        code.line("return getUnknownFields().equals(that.getUnknownFields());").close("}");
    }

    /** Emit a model construction with no unknown fields, one argument per line. */
    private static void construct(Code code, String model, List<String> arguments) {
        final List<String> all = new ArrayList<>(arguments);
        all.add("null");
        code.line("return new " + model + "(");
        code.depth += 2;
        for (int i = 0; i < all.size(); i++) {
            code.line(all.get(i) + (i < all.size() - 1 ? "," : ");"));
        }
        code.depth -= 2;
    }

    private static void checkMaskLength(Code code, List<Field> fields, boolean wide, String mask) {
        if (!wide) return;
        final int words = CopyBuilderGenerator.maskWords(fields);
        code.open("if (" + mask + ".length != " + words + ") {")
                .line("throw new IllegalArgumentException(\"Mask must have " + words + " words\");")
                .close("}");
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
            case ENUM -> value + " != null && EnumWithProtoMetadata.protoOrdinal(" + value + ") != 0";
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
            case ENUM -> "(" + value + " == null || EnumWithProtoMetadata.protoOrdinal(" + value + ") == 0)";
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
            return "!" + (floating ? "DiffSupport.equal" : "Objects.equals") + "(" + a + ", " + b + ")";
        }
        if (f.repeated()) {
            final boolean needsWireEquality =
                    switch (f.type()) {
                        case FLOAT, DOUBLE, ENUM, MESSAGE -> true;
                        default -> false;
                    };
            return "!" + (needsWireEquality ? "DiffSupport.equal" : "Objects.equals") + "(" + a + ", " + b + ")";
        }
        return switch (f.type()) {
            case STRING, BYTES -> "!Objects.equals(" + a + ", " + b + ")";
            case MESSAGE, MAP, ONE_OF -> "!DiffSupport.equal(" + a + ", " + b + ")";
            case ENUM ->
                "(" + a + " == null ? 0 : EnumWithProtoMetadata.protoOrdinal(" + a + ")) != (" + b
                        + " == null ? 0 : EnumWithProtoMetadata.protoOrdinal(" + b + "))";
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
