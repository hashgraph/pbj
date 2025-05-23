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
@SuppressWarnings({"SwitchStatementWithTooFewBranches", "StringConcatenationInsideStringBufferAppend"})
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
                 * {@inheritDoc}
                 */
                @Override
                public void write(@NonNull $modelClass data, @NonNull WritableSequentialData out, int initialIndent, int indentStep, boolean inline) {
                    final byte[] indentBytes = new byte[initialIndent];
                    final byte[] childIndentBytes = new byte[initialIndent+indentStep];
                    Arrays.fill(indentBytes, SPACE);
                    Arrays.fill(childIndentBytes, SPACE);
                    // start
                    if (inline) {
                        out.writeByte(OPEN_OBJECT);
                    } else {
                        out.writeBytes(indentBytes);
                        out.writeByte2(OPEN_OBJECT, NL);
                    }
                    // write field lines
                    boolean isFirstField = true;
                    $fieldWriteLines
                    // end
                    if (inline) {
                        out.writeByte(CLOSE_OBJECT);
                    } else {
                        out.writeByte(NL);
                        out.writeBytes(indentBytes);
                        out.writeByte(CLOSE_OBJECT);
                    }
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
        final String basicFieldCode = generateBasicFieldLines(field, getValueCode, fieldDef, fieldName, "initialIndent+indentStep+indentStep"); // todo replace indent*2 with childIndentBytes
        StringBuilder sb = new StringBuilder();
        sb.append("// [" + field.fieldNumber() + "] - " + field.name() + "\n");

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName + "." + oneOfField.nameCamelFirstUpper() + "OneOfType";
            sb.append( "if (data." + oneOfField.nameCamelFirstLower() + "().kind() == " + oneOfType + "."
                    + Common.camelToUpperSnake(field.name()) + ") {\n");
        } else {
            if (field.repeated()) {
                sb.append("if (!data." + field.nameCamelFirstLower() + "().isEmpty()) {\n");
            } else if (field.type() == Field.FieldType.BYTES) {
                sb.append("if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault() + " && data."
                        + field.nameCamelFirstLower() + "() != null" + " && data."
                        + field.nameCamelFirstLower() + "().length() > 0) {\n");
            } else if (field.type() == Field.FieldType.MAP) {
                sb.append("if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault()
                        + " && !data." + field.nameCamelFirstLower() + "().isEmpty()) {\n");
            } else {
                sb.append("if (data." + field.nameCamelFirstLower() + "() != " + field.javaDefault()
                        + ") {\n");
            }
        }
        sb.append("    if (isFirstField) { isFirstField = false; } else { out.writeByte2(COMMA, NL); }\n");
        sb.append("    out.writeBytes(childIndentBytes);\n");
        sb.append("    "+ basicFieldCode + ";\n");
        sb.append("}");
        return sb.toString();
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
                        "BytesValue" -> "field(out, %s, %s)".formatted(fieldName, getValueCode);
                case "Int64Value", "UInt64Value" -> "field(out, %s, %s, true)".formatted(fieldName, getValueCode);
                default -> throw new UnsupportedOperationException(
                        "Unhandled optional message type:" + field.messageType());
            };
        } else if (field.repeated()) {
            return switch (field.type()) {
                case MESSAGE -> "arrayField(out, $indent, $fieldName, $codec, $valueCode)"
                        .replace("$indent", childIndent)
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace(
                                "$codec",
                                ((SingleField) field).messageTypeModelPackage() + "."
                                        + ((SingleField) field).completeClassName() + ".JSON");
                default -> "arrayField(out, $fieldName, $fieldDef, $valueCode)"
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
                    childIndent);
//            return "field(out, %s, %s, $kEncoder, $vComposer)"
            return "field(out, %s, %s, $vComposer)"
                    .formatted(fieldName, getValueCode)
                    // Maps in protobuf can only have simple scalar and not floating keys, so toString should do a good
                    // job.
                    // Also see https://protobuf.dev/programming-guides/proto3/#json
//                    .replace("$kEncoder", "k -> escape(k.toString())")
                    .replace("$vComposer", "(o, n, v) -> " + vComposerMethod.replaceAll("out","o"));
        } else {
            return switch (field.type()) {
                case ENUM -> "field(out, $fieldName, $valueCode.protoName())"
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode);
                case MESSAGE -> "field(out, $childIndent, $fieldName, $codec, $valueCode)"
                        .replace("$childIndent", childIndent)
                        .replace("$fieldName", fieldName)
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace(
                                "$codec",
                                ((SingleField) field).messageTypeModelPackage() + "."
                                        + ((SingleField) field).completeClassName() + ".JSON");
                default -> "field(out, %s, %s)".formatted(fieldName, getValueCode);
            };
        }
    }
}
