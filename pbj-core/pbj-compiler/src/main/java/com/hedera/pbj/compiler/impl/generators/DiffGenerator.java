// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.generators.protobuf.LazyGetProtobufSizeMethodGenerator;
import java.util.Comparator;
import java.util.List;

/** Generates shallow, declaration-indexed diff operations for every model. */
final class DiffGenerator {
    private DiffGenerator() {}

    static String generate(String model, List<Field> fields, String schema, boolean tracking) {
        final boolean wide = fields.size() > 64;
        final String maskType = wide ? "long[]" : "long";
        final String empty = wide ? "new long[" + (fields.size() + 63) / 64 + "]" : "0L";
        final String all = wide
                ? "final long[] candidates = " + empty + "; java.util.Arrays.fill(candidates, -1L);"
                : "final long candidates = -1L;";
        final String checkMask = wide
                ? "if (mask.length != " + (fields.size() + 63) / 64
                        + ") throw new IllegalArgumentException(\"Wrong mask length\");\n"
                : "";
        final StringBuilder code = new StringBuilder();
        code.append("/** Compare all fields, using provenance only when it identifies the exact prior instance. */\n")
                .append("public static ")
                .append(maskType)
                .append(" diff(")
                .append(model)
                .append(" prior, ")
                .append(model)
                .append(" next) {\n");
        if (tracking) {
            code.append(
                    "if (next.$copyBuilderOrigin == prior && prior != null) return diff(prior, next, next.$copyBuilderChanged);\n");
        }
        code.append(all).append("\nreturn diff(prior, next, candidates);\n}\n");
        code.append("/** Compare candidate fields by value. Bits index declarations; a oneof uses one bit. */\n")
                .append("public static ")
                .append(maskType)
                .append(" diff(")
                .append(model)
                .append(" prior, ")
                .append(model)
                .append(" next, ")
                .append(maskType)
                .append(" candidates) {\n")
                .append("java.util.Objects.requireNonNull(prior); java.util.Objects.requireNonNull(next);\n")
                .append("$requireDiffable(prior); $requireDiffable(next);\n")
                .append(checkMask.replace("mask", "candidates"))
                .append(maskType)
                .append(" result = ")
                .append(empty)
                .append(";\n");
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            code.append("if (")
                    .append(bit("candidates", i, wide))
                    .append(" && ")
                    .append(different(f, "prior." + f.nameCamelFirstLower(), "next." + f.nameCamelFirstLower()))
                    .append(") ")
                    .append(word("result", i, wide))
                    .append(" |= 1L << ")
                    .append(i % 64)
                    .append(";\n");
        }
        code.append("return result;\n}\n");
        code.append("/** Retain selected top-level fields, resetting other fields to defaults. */\n")
                .append("public static ")
                .append(model)
                .append(" partial(")
                .append(model)
                .append(" next, ")
                .append(maskType)
                .append(" mask) {\n$requireDiffable(next);\n")
                .append(checkMask)
                .append("return new ")
                .append(model)
                .append("(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) code.append(", ");
            final Field f = fields.get(i);
            code.append(bit("mask", i, wide))
                    .append(" ? next.")
                    .append(f.nameCamelFirstLower())
                    .append(" : DEFAULT.")
                    .append(f.nameCamelFirstLower());
        }
        code.append(fields.isEmpty() ? "" : ", ").append("java.util.List.of());\n}\n");
        code.append(
                        "/** Test savings without serializing or measuring the whole message. Costs must be nonnegative. */\n")
                .append("public static boolean worthPartial(")
                .append(model)
                .append(" next, ")
                .append(maskType)
                .append(" mask, int minSaving, int overhead) {\n$requireDiffable(next);\n")
                .append(checkMask)
                .append("if (minSaving < 0 || overhead < 0) throw new IllegalArgumentException(\"Negative cost\");\n")
                .append("final long target = (long) minSaving + overhead;\nlong saved = 0;\n")
                .append("if (target == 0) return true;\n");
        for (int i : java.util.stream.IntStream.range(0, fields.size())
                .boxed()
                .sorted(Comparator.comparingInt(index -> cost(fields.get(index))))
                .toList()) {
            code.append("if (!")
                    .append(bit("mask", i, wide))
                    .append(") { saved += next.$diffSize")
                    .append(i)
                    .append("(); if (saved >= target) return true; }\n");
        }
        code.append("return false;\n}\n");
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            code.append("private int $diffSize")
                    .append(i)
                    .append("() { int _size = 0;\n")
                    .append(LazyGetProtobufSizeMethodGenerator.buildFieldSizeOfLines(
                            null, schema, List.of(f), Field::nameCamelFirstLower, true))
                    .append("return _size;\n}\n");
        }
        code.append(wireHelpers(model, fields, maskType, wide, checkMask));
        code.append("/** {@inheritDoc} */\n@Override public boolean $equalsForDiff(Object other) {\n")
                .append("if (this == other) return true;\nif (!(other instanceof ")
                .append(model)
                .append(" that)) return false;\n");
        for (Field f : fields) {
            code.append("if (")
                    .append(different(f, "this." + f.nameCamelFirstLower(), "that." + f.nameCamelFirstLower()))
                    .append(") return false;\n");
        }
        code.append("return getUnknownFields().equals(that.getUnknownFields());\n}\n");
        return code.toString();
    }

    private static String wireHelpers(
            String model, List<Field> fields, String maskType, boolean wide, String checkMask) {
        final StringBuilder code = new StringBuilder();
        code.append("private static void $requireDiffable(")
                .append(model)
                .append(" value) {\n")
                .append(
                        "if (!value.getUnknownFields().isEmpty()) throw new UnsupportedOperationException(\"Unknown fields require a full replacement\");\n}\n");
        code.append("/** Clear absent selected fields, including all inactive alternatives of a changed oneof. */\n")
                .append("public static int[] clearedFields(")
                .append(model)
                .append(" next, ")
                .append(maskType)
                .append(" mask) { return clearedFields(null, next, mask); }\n");
        code.append("/** With a prior value, clear only its old oneof alternative on a switch or unset. */\n")
                .append("public static int[] clearedFields(")
                .append(model)
                .append(" prior, ")
                .append(model)
                .append(" next, ")
                .append(maskType)
                .append(" mask) {\n$requireDiffable(next);\n")
                .append("if (prior != null) $requireDiffable(prior);\n")
                .append(checkMask)
                .append("final var cleared = new java.util.TreeSet<Integer>();\n");
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            final String n = f.nameCamelFirstLower();
            code.append("if (").append(bit("mask", i, wide)).append(") {\n");
            if (f instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    code.append("if (next.")
                            .append(n)
                            .append(".kind().protoOrdinal() != ")
                            .append(alt.fieldNumber())
                            .append(" && (prior == null || prior.")
                            .append(n)
                            .append(".kind().protoOrdinal() == ")
                            .append(alt.fieldNumber())
                            .append(")) cleared.add(")
                            .append(alt.fieldNumber())
                            .append(");\n");
                }
            } else {
                code.append("if (!(")
                        .append(present(f, "next." + n))
                        .append(")) cleared.add(")
                        .append(f.fieldNumber())
                        .append(");\n");
            }
            code.append("}\n");
        }
        code.append("return cleared.stream().mapToInt(Integer::intValue).toArray();\n}\n");
        code.append("/** Apply top-level replacements and explicit clears directly, without serialization. */\n")
                .append("public static ")
                .append(model)
                .append(" applyPartial(")
                .append(model)
                .append(" prior, ")
                .append(model)
                .append(" partial, int[] cleared) {\n$requireDiffable(prior); $requireDiffable(partial);\n")
                .append("PartialApplier.validateCleared(cleared);\n");
        for (int i = 0; i < fields.size(); i++)
            code.append("boolean clear").append(i).append(" = false;\n");
        code.append("for (int number : cleared) { switch (number) {\n");
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            final String n = f.nameCamelFirstLower();
            if (f instanceof OneOfField group) {
                for (Field alt : group.fields()) {
                    code.append("case ")
                            .append(alt.fieldNumber())
                            .append(" -> {\n")
                            .append("if (partial.")
                            .append(n)
                            .append(".kind().protoOrdinal() == ")
                            .append(alt.fieldNumber())
                            .append(") throw new IllegalArgumentException(\"Field is both replaced and cleared\");\n")
                            .append("if (prior.")
                            .append(n)
                            .append(".kind().protoOrdinal() == ")
                            .append(alt.fieldNumber())
                            .append(") clear")
                            .append(i)
                            .append(" = true;\n}\n");
                }
            } else {
                code.append("case ")
                        .append(f.fieldNumber())
                        .append(" -> {\nif (")
                        .append(present(f, "partial." + n))
                        .append(") throw new IllegalArgumentException(\"Field is both replaced and cleared\");\n")
                        .append("clear")
                        .append(i)
                        .append(" = true;\n}\n");
            }
        }
        code.append("default -> throw new IllegalArgumentException(\"Unknown cleared field: \" + number);\n} }\n");
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            if (!(f instanceof OneOfField)) continue;
            final String n = f.nameCamelFirstLower();
            code.append("if (")
                    .append(present(f, "partial." + n))
                    .append(" && ")
                    .append(present(f, "prior." + n))
                    .append(" && partial.")
                    .append(n)
                    .append(".kind() != prior.")
                    .append(n)
                    .append(".kind() && !clear")
                    .append(i)
                    .append(") throw new IllegalArgumentException(\"Oneof switch must clear its old alternative\");\n");
        }
        code.append("return new ").append(model).append("(");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) code.append(", ");
            final Field f = fields.get(i);
            final String n = f.nameCamelFirstLower();
            code.append(present(f, "partial." + n))
                    .append(" ? partial.")
                    .append(n)
                    .append(" : clear")
                    .append(i)
                    .append(" ? DEFAULT.")
                    .append(n)
                    .append(" : prior.")
                    .append(n);
        }
        code.append(fields.isEmpty() ? "" : ", ").append("java.util.List.of());\n}\n");
        return code.toString();
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

    private static String word(String mask, int i, boolean wide) {
        return mask + (wide ? "[" + i / 64 + "]" : "");
    }

    private static String bit(String mask, int i, boolean wide) {
        return "((" + word(mask, i, wide) + " & (1L << " + i % 64 + ")) != 0)";
    }

    private static String different(Field f, String a, String b) {
        if (f.optionalValueType()) {
            final boolean floating =
                    f.messageType().equals("FloatValue") || f.messageType().equals("DoubleValue");
            return "!" + (floating ? "DiffSupport.equal" : "java.util.Objects.equals") + "(" + a + ", " + b + ")";
        }
        if (f.repeated()) {
            final boolean needsWireEquality =
                    switch (f.type()) {
                        case FLOAT, DOUBLE, ENUM, MESSAGE -> true;
                        default -> false;
                    };
            return "!" + (needsWireEquality ? "DiffSupport.equal" : "java.util.Objects.equals") + "(" + a + ", " + b
                    + ")";
        }
        return switch (f.type()) {
            case STRING, BYTES -> "!java.util.Objects.equals(" + a + ", " + b + ")";
            case MESSAGE, MAP, ONE_OF -> "!DiffSupport.equal(" + a + ", " + b + ")";
            case ENUM ->
                "(" + a + " == null ? 0 : EnumWithProtoMetadata.protoOrdinal(" + a + ")) != (" + b
                        + " == null ? 0 : EnumWithProtoMetadata.protoOrdinal(" + b + "))";
            default -> a + " != " + b;
        };
    }

    private static int cost(Field f) {
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
