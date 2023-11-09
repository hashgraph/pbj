package com.hedera.pbj.compiler.impl.generators.json;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;

import java.util.List;
import java.util.stream.Collectors;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator.toJsonFieldName;

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
        return "\n" + fields.stream()
            .filter(f -> f instanceof OneOfField)
            .map(f -> {
                final OneOfField field = (OneOfField)f;
                return """
                           /** Constant for an unset oneof for $fieldName */
                           public static final OneOf<$enum> $unsetFieldName = new OneOf<>($enum.UNSET,null);
                       """
                        .replace("$enum", field.getEnumClassRef())
                        .replace("$fieldName", field.name())
                        .replace("$unsetFieldName", Common.camelToUpperSnake(field.name())+"_UNSET")
                        .replace("$unsetFieldName", field.getEnumClassRef());
            })
            .collect(Collectors.joining("\n"));
    }

    static String generateParseObjectMethod(final String modelClassName, final List<Field> fields) {
        return """
                /**
                 * Parses a HashObject object from JSON parse tree for object JSONParser.ObjContext. Throws if in strict mode ONLY.
                 *
                 * @param root The JSON parsed object tree to parse data from
                 * @return Parsed HashObject model object or null if data input was null or empty
                 * @throws UnknownFieldException If an unknown field is encountered while parsing the object, and we are in strict mode
                 * @throws IOException If the protobuf stream is not empty and has malformed JSON.
                 * @throws NoSuchElementException If there is no element of type T that can be parsed from this input
                 */
                public @NonNull $modelClassName parse(
                        @Nullable final JSONParser.ObjContext root,
                        final boolean strictMode) throws IOException {
                    // -- TEMP STATE FIELDS --------------------------------------
                $fieldDefs

                    // -- EXTRACT VALUES FROM PARSE TREE ---------------------------------------------
                    
                    for (JSONParser.PairContext kvPair : root.pair()) {
                        switch (kvPair.STRING().getText()) {
                            $caseStatements
                            default -> {
                                if (strictMode) {
                                    // Since we are parsing is strict mode, this is an exceptional condition.
                                    throw new UnknownFieldException(kvPair.STRING().getText());
                                }
                            }
                        }
                    }
                    
                    return new $modelClassName($fieldsList);
                }
                """
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;".formatted(field.javaFieldType(),
                field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
        .replace("$fieldsList",fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", ")))
        .replace("$caseStatements",generateCaseStatements(fields))
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
        for(Field field: fields) {
            if (field instanceof final OneOfField oneOfField) {
                for(final Field subField: oneOfField.fields()) {
                    sb.append("case \"" + toJsonFieldName(subField.name()) +"\" /* [" + subField.fieldNumber() + "] */ " +
                            "-> temp_" + oneOfField.name()+" = new OneOf<>(\n"+
                            oneOfField.getEnumClassRef().indent(DEFAULT_INDENT) +"."+Common.camelToUpperSnake(subField.name())+
                            ", \n".indent(DEFAULT_INDENT));
                    generateFieldCaseStatement(sb,subField);
                    sb.append(");\n");
                }
            } else {
                sb.append("case \"" + toJsonFieldName(field.name()) +"\" /* [" + field.fieldNumber() + "] */ " +
                        "-> temp_" + field.name()+" = ");
                generateFieldCaseStatement(sb, field);
                sb.append(";\n");
            }
        }
        return sb.toString().indent(DEFAULT_INDENT * 3);
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field field to generate case statement for
     * @param sb StringBuilder to append code to
     */
    private static void generateFieldCaseStatement(final StringBuilder sb, final Field field) {
        if(field.repeated()) {
            if (field.type() == Field.FieldType.MESSAGE) {
                sb.append("parseObjArray(kvPair.value().arr(), "+field.messageType()+".JSON)");
            } else {
                sb.append("kvPair.value().arr().value().stream().map(v -> ");
                switch (field.type()) {
                    case ENUM -> sb.append(field.messageType() + ".fromString(v.STRING().getText())");
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> sb.append("parseInteger(v)");
                    case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> sb.append("parseLong(v)");
                    case FLOAT -> sb.append("parseFloat(v)");
                    case DOUBLE -> sb.append("parseDouble(v)");
                    case STRING -> sb.append("unescape(v.STRING().getText())");
                    case BOOL -> sb.append("parseBoolean(v)");
                    case BYTES -> sb.append("Bytes.fromBase64(v.STRING().getText())");
                    default -> throw new RuntimeException("Unknown field type [" + field.type() + "]");
                }
                sb.append(").toList()");
            }
        } else if(field.optionalValueType()) {
            switch(field.messageType()) {
                case "Int32Value", "UInt32Value" -> sb.append("parseInteger(kvPair.value())");
                case "Int64Value", "UInt64Value" -> sb.append("parseLong(kvPair.value())");
                case "FloatValue" -> sb.append("parseFloat(kvPair.value())");
                case "DoubleValue" -> sb.append("parseDouble(kvPair.value())");
                case "StringValue" -> sb.append("unescape(kvPair.value().STRING().getText())");
                case "BoolValue" -> sb.append("parseBoolean(kvPair.value())");
                case "BytesValue" -> sb.append("Bytes.fromBase64(kvPair.value().STRING().getText())");
                default -> throw new RuntimeException("Unknown message type ["+field.messageType()+"]");
            }
        } else {
            switch (field.type()) {
                case MESSAGE -> sb.append(field.javaFieldType() + ".JSON.parse(kvPair.value().getChild(JSONParser.ObjContext.class, 0), false)");
                case ENUM -> sb.append(field.javaFieldType() + ".fromString(kvPair.value().STRING().getText())");
                case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> sb.append("parseInteger(kvPair.value())");
                case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> sb.append("parseLong(kvPair.value())");
                case FLOAT -> sb.append("parseFloat(kvPair.value())");
                case DOUBLE -> sb.append("parseDouble(kvPair.value())");
                case STRING -> sb.append("unescape(kvPair.value().STRING().getText())");
                case BOOL -> sb.append("parseBoolean(kvPair.value())");
                case BYTES -> sb.append("Bytes.fromBase64(kvPair.value().STRING().getText())");
                default -> throw new RuntimeException("Unknown field type ["+field.type()+"]");
            }
        }
    }
}
