// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.json;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator.toJsonFieldName;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code to generate the write method for Codec classes.
 */
@SuppressWarnings("SwitchStatementWithTooFewBranches")
final class JsonCodecWriteMethodGenerator {

    static String generateWriteMethod(final String modelClassName, final List<Field> fields) {
        final List<Field> fieldsToWrite = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        final String fieldWriteLines = fieldsToWrite.stream()
                .map(field -> generateFieldWriteLines(
                        field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT);

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
                    if (!fieldLines.isEmpty()){
                        sb.append(childIndent);
                        sb.append(String.join(",\\n"+childIndent, fieldLines));
                        sb.append("\\n");
                    }
                    // end
                    sb.append(indent + "}");
                    return sb.toString();
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldWriteLines", fieldWriteLines)
                .indent(DEFAULT_INDENT);
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
        final String basicFieldCode = generateBasicFieldLines(field, getValueCode, fieldDef, fieldName, "childIndent");
        String prefix = "// [" + field.fieldNumber() + "] - " + field.name() + "\n";

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName + "." + oneOfField.nameCamelFirstUpper() + "OneOfType";
            prefix += "if (data." + oneOfField.nameCamelFirstLower() + "().kind() == " + oneOfType + "."
                    + Common.camelToUpperSnake(field.name()) + ")";
            prefix += "\n";
            return prefix + "fieldLines.add(" + basicFieldCode + ");";
        } else {
            if (field.repeated()) {
                return prefix + "if (!data." + field.nameCamelFirstLower() + "().isEmpty()) fieldLines.add("
                        + basicFieldCode + ");";
            } else if (field.type() == Field.FieldType.BYTES) {
                return prefix + "if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault() + " && data."
                        + field.nameCamelFirstLower() + "() != null" + " && data."
                        + field.nameCamelFirstLower() + "().length() > 0) fieldLines.add(" + basicFieldCode + ");";
            } else if (field.type() == Field.FieldType.MAP) {
                return prefix + "if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault()
                        + " && !data." + field.nameCamelFirstLower() + "().isEmpty()) fieldLines.add(" + basicFieldCode
                        + ");";
            } else {
                return prefix + "if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault()
                        + ") fieldLines.add(" + basicFieldCode + ");";
            }
        }
    }

    @NonNull
    private static String generateBasicFieldLines(
            Field field, String getValueCode, String fieldDef, String fieldName, String childIndent) {
        if (field.optionalValueType()) {
            return switch (field.messageType()) {
                case "StringValue",
                        "BoolValue",
                        "Int32Value",
                        "UInt32Value",
                        "FloatValue",
                        "DoubleValue",
                        "BytesValue" -> "field(%s, %s)".formatted(fieldName, getValueCode);
                case "Int64Value", "UInt64Value" -> "field(%s, %s, true)".formatted(fieldName, getValueCode);
                default -> throw new UnsupportedOperationException(
                        "Unhandled optional message type:" + field.messageType());
            };
        } else if (field.repeated()) {
            return switch (field.type()) {
                case MESSAGE -> "arrayField(childIndent, $fieldName, $codec, $valueCode)"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace(
                                "$codec",
                                ((SingleField) field).messageTypeModelPackage() + "."
                                        + Common.capitalizeFirstLetter(field.messageType()) + ".JSON");
                default -> "arrayField($fieldName, $fieldDef, $valueCode)"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode);
            };
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            final String vComposerMethod = generateBasicFieldLines(
                    mapField.valueField(),
                    "v",
                    Common.camelToUpperSnake(mapField.valueField().name()),
                    "n",
                    "indent");
            return "field(%s, %s, $kEncoder, $vComposer)"
                    .formatted(fieldName, getValueCode)
                    // Maps in protobuf can only have simple scalar and not floating keys, so toString should do a good
                    // job.
                    // Also see https://protobuf.dev/programming-guides/proto3/#json
                    .replace("$kEncoder", "k -> escape(k.toString())")
                    .replace("$vComposer", "(n, v) -> " + vComposerMethod);
        } else {
            return switch (field.type()) {
                case ENUM -> "field($fieldName, $valueCode.protoName())"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode);
                case MESSAGE -> "field($childIndent, $fieldName, $codec, $valueCode)"
                        .replace("$childIndent", childIndent)
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace(
                                "$codec",
                                ((SingleField) field).messageTypeModelPackage() + "."
                                        + Common.capitalizeFirstLetter(field.messageType()) + ".JSON");
                default -> "field(%s, %s)".formatted(fieldName, getValueCode);
            };
        }
    }
}
