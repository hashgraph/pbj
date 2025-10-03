// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.json;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator.toJsonFieldName;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Code to generate the parse method for Codec classes.
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
class JsonCodecParseMethodGenerator {

    /**
     * Because all UNSET OneOf values are the same and we use them often we create a static constant for them and just
     * reuse it throughout codec code.
     *
     * @param fields the fields to generate for
     * @return code for constants
     */
    static String generateUnsetOneOfConstants(final List<Field> fields) {
        return "\n"
                + fields.stream()
                        .filter(f -> f instanceof OneOfField)
                        .map(f -> {
                            final OneOfField field = (OneOfField) f;
                            return """
                           /** Constant for an unset oneof for $fieldName */
                           public static final $className<$enum> $unsetFieldName = new $className<>($enum.UNSET,null);
                       """
                                    .replace("$className", field.className())
                                    .replace("$enum", field.getEnumClassRef())
                                    .replace("$fieldName", field.name())
                                    .replace("$unsetFieldName", Common.camelToUpperSnake(field.name()) + "_UNSET")
                                    .replace("$unsetFieldName", field.getEnumClassRef());
                        })
                        .collect(Collectors.joining("\n"));
    }

    static String generateParseObjectMethod(final String modelClassName, final List<Field> fields) {
        return """
                /**
                 * Parses a HashObject object from JSON parse tree for object JSONParser.ObjContext.
                 * Throws an UnknownFieldException wrapped in a ParseException if in strict mode ONLY.
                 * <p>
                 * The {@code maxSize} specifies a custom value for the default `Codec.DEFAULT_MAX_SIZE` limit. IMPORTANT:
                 * specifying a value larger than the default one can put the application at risk because a maliciously-crafted
                 * payload can cause the parser to allocate too much memory which can result in OutOfMemory and/or crashes.
                 * It's important to carefully estimate the maximum size limit that a particular protobuf model type should support,
                 * and then pass that value as a parameter. Note that the estimated limit should apply to the **type** as a whole,
                 * rather than to individual instances of the model. In other words, this value should be a constant, or a config
                 * value that is controlled by the application, rather than come from the input that the application reads.
                 * When in doubt, use the other overloaded versions of this method that use the default `Codec.DEFAULT_MAX_SIZE`.
                 *
                 * @param root The JSON parsed object tree to parse data from
                 * @param maxSize a ParseException will be thrown if the size of a delimited field exceeds the limit
                 * @return Parsed HashObject model object or null if data input was null or empty
                 * @throws ParseException If parsing fails
                 */
                public @NonNull $modelClassName parse(
                        @Nullable final JSONParser.ObjContext root,
                        final boolean strictMode,
                        final int maxDepth,
                        final int maxSize) throws ParseException {
                    if (maxDepth < 0) {
                        throw new ParseException("Reached maximum allowed depth of nested messages");
                    }
                    try {
                        // -- TEMP STATE FIELDS --------------------------------------
                        $fieldDefs

                        // -- EXTRACT VALUES FROM PARSE TREE ---------------------------------------------

                        for (JSONParser.PairContext kvPair : root.pair()) {
                            switch (kvPair.STRING().getText()) {
                                $caseStatements
                                default: {
                                    if (strictMode) {
                                        // Since we are parsing is strict mode, this is an exceptional condition.
                                        throw new UnknownFieldException(kvPair.STRING().getText());
                                    }
                                }
                            }
                        }

                        return new $modelClassName($fakeParams$fieldsList$unknownFields);
                    } catch (Exception ex) {
                        throw new ParseException(ex);
                    }
                }
                """
                .replace("$modelClassName", modelClassName)
                .replace(
                        "$fakeParams",
                        fields.stream().anyMatch(Field::hasDifferentStorageType)
                                ? ("0" + (fields.isEmpty() ? "" : ", "))
                                : "")
                .replace("$unknownFields", fields.isEmpty() ? "Collections.emptyList()" : ", Collections.emptyList()")
                .replace(
                        "$fieldDefs",
                        fields.stream()
                                .map(field -> "    %s temp_%s = %s;"
                                        .formatted(field.javaFieldStorageType(), field.name(), field.javaDefault()))
                                .collect(Collectors.joining("\n")))
                .replace(
                        "$fieldsList",
                        fields.stream().map(field -> "temp_" + field.name()).collect(Collectors.joining(", ")))
                .replace("$caseStatements", generateCaseStatements(fields))
                .indent(DEFAULT_INDENT);
    }

    /**
     * Generate switch case statements for each tag (field & wire type pair). For repeated numeric value types we
     * generate 2 case statements for packed and unpacked encoding.
     *
     * @param fields list of all fields in record
     * @return string of case statement code
     */
    private static String generateCaseStatements(final List<Field> fields) {
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            if (field instanceof final OneOfField oneOfField) {
                for (final Field subField : oneOfField.fields()) {
                    sb.append("case \"" + toJsonFieldName(subField.name()) + "\" /* [" + subField.fieldNumber()
                            + "] */ " + ": temp_"
                            + oneOfField.name() + " = new %s<>(\n".formatted(oneOfField.className())
                            + oneOfField.getEnumClassRef().indent(DEFAULT_INDENT)
                            + "." + Common.camelToUpperSnake(subField.name()) + ", \n".indent(DEFAULT_INDENT));
                    generateFieldCaseStatement(sb, subField, "kvPair.value()");
                    sb.append("); break;\n");
                }
            } else {
                sb.append("case \"" + toJsonFieldName(field.name()) + "\" /* [" + field.fieldNumber() + "] */ "
                        + ": temp_" + field.name() + " = ");
                generateFieldCaseStatement(sb, field, "kvPair.value()");
                sb.append("; break;\n");
            }
        }
        return sb.toString();
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field field to generate case statement for
     * @param origSB StringBuilder to append code to
     * @param valueGetter normally a "kvPair.value()", but may be different e.g. for maps parsing
     */
    private static void generateFieldCaseStatement(
            final StringBuilder origSB, final Field field, final String valueGetter) {
        final StringBuilder sb = new StringBuilder();
        final boolean isMapField = field instanceof SingleField && ((SingleField) field).isMapField();
        final boolean isNotMapFieldOrOneOf = !isMapField && field.parent() == null;
        if (field.repeated()) {
            if (field.type() == Field.FieldType.MESSAGE) {
                sb.append(("parseObjArray(checkSize(\"$fieldName\", $valueGetter.arr().value(), $maxSize), "
                                + field.messageType() + ".JSON, maxDepth - 1, $maxSize)")
                        .replace("$maxSize", field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                        .replace("$fieldName", field.name()));
            } else {
                sb.append("checkSize(\"$fieldName\", $valueGetter.arr().value(), $maxSize).stream().map(v -> "
                        .replace("$maxSize", field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                        .replace("$fieldName", field.name()));
                switch (field.type()) {
                    case ENUM -> sb.append(field.messageType() + ".fromString(v.STRING().getText())");
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> sb.append("parseInteger(v)");
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> sb.append("parseLong(v)");
                    case FLOAT -> sb.append("parseFloat(v)");
                    case DOUBLE -> sb.append("parseDouble(v)");
                    case STRING ->
                        sb.append((isNotMapFieldOrOneOf ? "toUtf8Bytes(" : "")
                                + "unescape(checkSize(\"$fieldName\", v.STRING().getText(), $maxSize))"
                                        .replace(
                                                "$maxSize",
                                                field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                        .replace("$fieldName", field.name())
                                + (isNotMapFieldOrOneOf ? ")" : ""));
                    case BOOL -> sb.append("parseBoolean(v)");

                    // maxSize * 2 - because Base64. The *2 math isn't precise, but it's good enough for our purposes.
                    case BYTES ->
                        sb.append(
                                "Bytes.fromBase64(checkSize(\"$fieldName\", v.STRING().getText(), $maxSize < (Integer.MAX_VALUE / 2) ? $maxSize * 2 : Integer.MAX_VALUE))"
                                        .replace(
                                                "$maxSize",
                                                field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                        .replace("$fieldName", field.name()));
                    default -> throw new RuntimeException("Unknown field type [" + field.type() + "]");
                }
                sb.append(").toList()");
            }
        } else if (field.optionalValueType()) {
            switch (field.messageType()) {
                case "Int32Value", "UInt32Value" -> sb.append("parseInteger($valueGetter)");
                case "Int64Value", "UInt64Value" -> sb.append("parseLong($valueGetter)");
                case "FloatValue" -> sb.append("parseFloat($valueGetter)");
                case "DoubleValue" -> sb.append("parseDouble($valueGetter)");
                case "StringValue" ->
                    sb.append((isNotMapFieldOrOneOf ? "toUtf8Bytes(" : "")
                            + "unescape(checkSize(\"$fieldName\", $valueGetter.STRING().getText(), $maxSize))"
                                    .replace(
                                            "$maxSize",
                                            field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                    .replace("$fieldName", field.name())
                            + (isNotMapFieldOrOneOf ? ")" : ""));
                case "BoolValue" -> sb.append("parseBoolean($valueGetter)");

                // maxSize * 2 - because Base64. The *2 math isn't precise, but it's good enough for our purposes:
                case "BytesValue" ->
                    sb.append(
                            "Bytes.fromBase64(checkSize(\"$fieldName\", $valueGetter.STRING().getText(), $maxSize < (Integer.MAX_VALUE / 2) ? $maxSize * 2 : Integer.MAX_VALUE))"
                                    .replace(
                                            "$maxSize",
                                            field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                    .replace("$fieldName", field.name()));
                default -> throw new RuntimeException("Unknown message type [" + field.messageType() + "]");
            }
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;

            final StringBuilder keySB = new StringBuilder();
            final StringBuilder valueSB = new StringBuilder();

            generateFieldCaseStatement(keySB, mapField.keyField(), "mapKV");
            generateFieldCaseStatement(valueSB, mapField.valueField(), "mapKV.value()");

            sb.append(
                    """
                    $valueGetter.getChild(JSONParser.ObjContext.class, 0).pair().stream()
                                        .collect(Collectors.toMap(
                                            mapKV -> $mapEntryKey,
                                            new UncheckedThrowingFunction<>(mapKV -> $mapEntryValue)
                                        ))"""
                            .replace("$mapEntryKey", keySB.toString())
                            .replace("$mapEntryValue", valueSB.toString()));
        } else {
            switch (field.type()) {
                case MESSAGE ->
                    sb.append(field.javaFieldType()
                            + ".JSON.parse($valueGetter.getChild(JSONParser.ObjContext.class, 0), false, maxDepth - 1, $maxSize)"
                                    .replace(
                                            "$maxSize",
                                            field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize"));
                case ENUM -> sb.append(field.javaFieldType() + ".fromString($valueGetter.STRING().getText())");
                case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> sb.append("parseInteger($valueGetter)");
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> sb.append("parseLong($valueGetter)");
                case FLOAT -> sb.append("parseFloat($valueGetter)");
                case DOUBLE -> sb.append("parseDouble($valueGetter)");
                case STRING ->
                    sb.append((isNotMapFieldOrOneOf ? "toUtf8Bytes(" : "")
                            + "unescape(checkSize(\"$fieldName\", $valueGetter.STRING().getText(), $maxSize))"
                                    .replace(
                                            "$maxSize",
                                            field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                    .replace("$fieldName", field.name())
                            + (isNotMapFieldOrOneOf ? ")" : ""));
                case BOOL -> sb.append("parseBoolean($valueGetter)");

                // maxSize * 2 - because Base64. The *2 math isn't precise, but it's good enough for our purposes:
                case BYTES ->
                    sb.append(
                            "Bytes.fromBase64(checkSize(\"$fieldName\", $valueGetter.STRING().getText(), $maxSize < (Integer.MAX_VALUE / 2) ? $maxSize * 2 : Integer.MAX_VALUE))"
                                    .replace(
                                            "$maxSize",
                                            field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                                    .replace("$fieldName", field.name()));
                default -> throw new RuntimeException("Unknown field type [" + field.type() + "]");
            }
        }
        origSB.append(sb.toString().replace("$valueGetter", valueGetter));
    }
}
