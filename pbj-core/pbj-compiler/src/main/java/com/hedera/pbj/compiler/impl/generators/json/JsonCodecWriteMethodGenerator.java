package com.hedera.pbj.compiler.impl.generators.json;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator.toJsonFieldName;

/**
 * Code to generate the write method for Codec classes.
 */
@SuppressWarnings("SwitchStatementWithTooFewBranches")
final class JsonCodecWriteMethodGenerator {

    static String generateWriteMethod(final String modelClassName, final List<Field> fields) {
        final List<Field> fieldsToWrite = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        final String fieldWriteLines = fieldsToWrite.stream()
                .map(field -> generateFieldWriteLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
                .collect(Collectors.joining("\n"+Common.FIELD_INDENT));

        return """     
                /**
                 * Returns JSON string representing an item.
                 *
                 * @param data      The item to convert. Must not be null.
                 * @param indent    The indent to use for pretty printing
                 * @param inline    When true the output will start with indent end with a new line otherwise
                 *                        it will just be the object "{...}"
                 */
                @Override
                public String toJSON(@NonNull $modelClass data, String indent, boolean inline) {
                    StringBuilder sb = new StringBuilder();
                    // start
                    sb.append(inline ? "{\\n" : indent + "{\\n");
                    final String childIndent = indent + INDENT;
                    // collect field lines
                    final List<String> fieldLines = new ArrayList<>();
                    $fieldWriteLines
                    // write field lines
                    sb.append(childIndent);
                    sb.append(String.join(",\\n"+childIndent, fieldLines));
                    // end
                    sb.append("\\n" +indent + "}");
                    return sb.toString();
                }
                """
            .replace("$modelClass", modelClassName)
            .replace("$fieldWriteLines", fieldWriteLines)
            .replaceAll("\n", "\n" + Common.FIELD_INDENT);
    }


    /**
     * Generate lines of code for writing field
     *
     * @param field The field to generate writing line of code for
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @return java code to write field to output
     */
    private static String generateFieldWriteLines(final Field field, final String modelClassName, String getValueCode) {
        final String fieldDef = Common.camelToUpperSnake(field.name());
        final String fieldName = '\"' + toJsonFieldName(field.name()) + '\"';
        String prefix = "// ["+field.fieldNumber()+"] - "+field.name() + "\n"+ Common.FIELD_INDENT;

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
            getValueCode = "(("+field.javaFieldType()+")data."+oneOfField.nameCamelFirstLower()+"().as())";
            prefix += "if(data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
                    Common.camelToUpperSnake(field.name())+")";
            prefix += "\n"+ Common.FIELD_INDENT.repeat(2);
        }
        if (field.repeated()) {
            prefix += "if(!data." + field.nameCamelFirstLower() + "().isEmpty()) fieldLines.add(";
        } else {
            prefix += "if(data." + field.nameCamelFirstLower() + "() != " + field.javaDefault() + ") fieldLines.add(";
        }


        if(field.optionalValueType()) {
            return prefix + switch (field.messageType()) {
                case "StringValue", "BoolValue", "Int32Value",
                        "UInt32Value", "FloatValue",
                        "DoubleValue", "BytesValue" -> "field(%s, %s)"
                        .formatted(fieldName, getValueCode);
                case "Int64Value", "UInt64Value" -> "field(%s, %s, true)"
                        .formatted(fieldName, getValueCode);
                default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
            } +");";
        } else if (field.repeated()) {
            return prefix + switch(field.type()) {
                case MESSAGE -> "arrayField(childIndent, $fieldName, $codec, $valueCode)"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".JSON");
                default -> "arrayField($fieldName, $fieldDef, $valueCode)"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode);
            } +");";
        } else {
            return prefix + switch(field.type()) {
                case ENUM -> "field($fieldName, $valueCode.name())"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode);
                case MESSAGE -> "field(childIndent, $fieldName, $codec, $valueCode)"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".JSON");
                default -> "field(%s, %s)"
                        .formatted(fieldName, getValueCode);
            } +");";
        }
    }
}
