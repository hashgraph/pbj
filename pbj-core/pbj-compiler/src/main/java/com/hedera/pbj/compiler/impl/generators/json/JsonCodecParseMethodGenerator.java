// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.json;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator.toJsonFieldName;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
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
                /** {@inheritDoc} */
                public @NonNull $modelClassName parse(
                        @Nullable final JsonLexer lexer,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth)
                        throws ParseException {
                    try {
                        // -- TEMP STATE FIELDS --------------------------------------
                        $fieldDefs
                        // start parsing
                        lexer.openObject();
                        boolean isFirst = true;
                        while(true) {
                            if (isFirst) {
                                isFirst = false;
                            } else if(!lexer.nextFieldOrClose()){
                                break;
                            }
                            // read field name and colon
                            final String fieldName = lexer.readString();
                            if (fieldName == null) break;// there are no fields or no more fields
                            lexer.consumeColon();
                            // read and handle field value
                            switch (fieldName) {
                $caseStatements
                                default: {
                                    if (strictMode) {
                                        // Since we are parsing is strict mode, this is an exceptional condition.
                                        throw new UnknownFieldException(fieldName);
                                    }
                                }
                            }
                        }
                        return new $modelClassName($fieldsList);
                    } catch (IOException ex) {
                        throw new ParseException(ex);
                    }
                }
                """
                .replace("$modelClassName", modelClassName)
                .replace(
                        "$fieldDefs",
                        fields.stream()
                                .map(field -> "    %s temp_%s = %s;"
                                        .formatted(field.javaFieldType(), field.name(), field.javaDefault()))
                                .collect(Collectors.joining("\n")))
                .replace(
                        "$fieldsList",
                        fields.stream().map(field -> "temp_" + field.name()).collect(Collectors.joining(", ")))
                .replace("$caseStatements", generateCaseStatements(fields).indent(DEFAULT_INDENT * 4))
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
            if (field.repeated()) {
                sb.append("case \"" + toJsonFieldName(field.name()) + "\" /* [" + field.fieldNumber() + "] */ : {\n");
                sb.append("""
                    lexer.openArray();
                    boolean isFirst2 = true;
                    temp_$fieldName = new ArrayList<>();
                    while(true) {
                        if (isFirst2) {
                            isFirst2 = false;
                        } else if(!lexer.nextFieldOrClose()){
                            break;
                        }
                        // read value
                        temp_$fieldName.add($fieldValueCode);
                    }
                    break;
                }
                """.replace("$fieldName", field.name())
                   .replace("$fieldValueCode", generateFieldCaseStatement(field)));
            } else if (field instanceof final OneOfField oneOfField) {
                for (final Field subField : oneOfField.fields()) {
                    sb.append("case \"" + toJsonFieldName(subField.name()) + "\" /* [" + subField.fieldNumber()
                            + "] */ " + ": temp_"
                            + oneOfField.name() + " = new %s<>(\n".formatted(oneOfField.className())
                            + oneOfField.getEnumClassRef().indent(DEFAULT_INDENT)
                            + "." + Common.camelToUpperSnake(subField.name()) + ", \n".indent(DEFAULT_INDENT));
                    sb.append(generateFieldCaseStatement(subField));
                    sb.append("); break;\n");
                }
            } else if (field.type() == Field.FieldType.MAP) {
                final MapField mapField = (MapField) field;
                sb.append("case \"" + toJsonFieldName(field.name()) + "\" /* [" + field.fieldNumber() + "] */ : {\n");
                sb.append("""
                    lexer.openObject();
                    boolean isFirst2 = true;
                    temp_$fieldName = new HashMap<>();
                    while(true) {
                        if (isFirst2) {
                            isFirst2 = false;
                        } else if(!lexer.nextFieldOrClose()){
                            break;
                        }
                        // read value
                        var key = $fieldKeyCode;
                        lexer.consumeColon();
                        var value = $fieldValueCode;
                        temp_$fieldName.put(key, value);
                    }
                    break;
                }
                """.replace("$fieldName", field.name())
                        .replace("$fieldKeyCode", generateFieldCaseStatement(mapField.keyField()))
                        .replace("$fieldValueCode", generateFieldCaseStatement(mapField.valueField())));
            } else {
                sb.append("case \"" + toJsonFieldName(field.name()) + "\" /* [" + field.fieldNumber() + "] */ "
                        + ": temp_" + field.name() + " = ");
                sb.append(generateFieldCaseStatement(field));
                sb.append("; break;\n");
            }
        }
        return sb.toString().indent(DEFAULT_INDENT*2);
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field  field to generate case statement for
     * @return string of case statement code
     */
    private static String generateFieldCaseStatement(final Field field) {
        final StringBuilder sb = new StringBuilder();
        if (field.optionalValueType()) {
            switch (field.messageType()) {
                case "Int32Value", "UInt32Value" -> sb.append("(int)lexer.readSignedInteger()");
                case "Int64Value", "UInt64Value" -> sb.append("lexer.readSignedInteger()");
                case "FloatValue" -> sb.append("(float)lexer.readDouble()");
                case "DoubleValue" -> sb.append("lexer.readDouble()");
                case "StringValue" -> sb.append("lexer.readString()");
                case "BoolValue" -> sb.append("lexer.readBoolean()");
                case "BytesValue" -> sb.append("lexer.readBytes()");
                default -> throw new RuntimeException("Unknown message type [" + field.messageType() + "]");
            }
        } else {
            switch (field.type()) {
                case MESSAGE -> sb.append(field.javaFieldTypeBase()
                        + ".JSON.parse(lexer, strictMode, parseUnknownFields, maxDepth - 1)");
                case ENUM -> sb.append("lexer.readEnum("+field.javaFieldTypeBase()+".class)");
                case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> sb.append("(int)lexer.readSignedInteger()");
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> sb.append("lexer.readSignedInteger()");
                case FLOAT -> sb.append("(float)lexer.readDouble()");
                case DOUBLE -> sb.append("lexer.readDouble()");
                case STRING -> sb.append("lexer.readString()");
                case BOOL -> sb.append("lexer.readBoolean()");
                case BYTES -> sb.append("lexer.readBytes()");
                default -> throw new RuntimeException("Unknown field type [" + field.type() + "]");
            }
        }
        return sb.toString();
    }
}
