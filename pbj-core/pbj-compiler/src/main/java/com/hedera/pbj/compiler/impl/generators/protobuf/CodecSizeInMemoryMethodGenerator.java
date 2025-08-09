// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 */
class CodecSizeInMemoryMethodGenerator {

    static String generateSizeInMemoryMethod(final String modelClassName, final List<Field> fields) {
        final String fieldSizeOfLines = buildFieldSizeOfLines(
                modelClassName, fields, field -> "data.%s()".formatted(field.nameCamelFirstLower()), true);
        return """
                public int sizeInMemory($modelClass data) {
                    int size = 0;
                    $fieldSizeOfLines
                    return size;
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldSizeOfLines", fieldSizeOfLines)
                .indent(DEFAULT_INDENT);
    }

    static String buildFieldSizeOfLines(
            final String modelClassName,
            final List<Field> fields,
            final Function<Field, String> getValueBuilder,
            boolean skipDefault) {
        return fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field ->
                        generateFieldSizeOfLines(field, modelClassName, getValueBuilder.apply(field), skipDefault))
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT);
    }

    /**
     * Generate lines of code for measure method, that measure the size of each field and add to "size" variable.
     *
     * @param field The field to generate size of line
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @param skipDefault true if default value of the field should result in size zero
     * @return java code for adding fields size to "size" variable
     */
    private static String generateFieldSizeOfLines(
            final Field field, final String modelClassName, String getValueCode, boolean skipDefault) {
        final String fieldDef = Common.camelToUpperSnake(field.name());
        String prefix = "// [" + field.fieldNumber() + "] - " + field.name();
        prefix += "\n";

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName + "." + oneOfField.nameCamelFirstUpper() + "OneOfType";
            getValueCode = "data." + oneOfField.nameCamelFirstLower() + "().as()";
            prefix += "if (data." + oneOfField.nameCamelFirstLower() + "().kind() == " + oneOfType + "."
                    + Common.camelToUpperSnake(field.name()) + ")";
            prefix += "\n";
        }

        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix
                    + switch (field.messageType()) {
                        case "StringValue" -> "size += ((String) %s).length() * 2;".formatted(getValueCode);
                        case "BoolValue" -> "size += 1;";
                        case "Int32Value", "UInt32Value" -> "size += 4;";
                        case "Int64Value", "UInt64Value" -> "size += 8;";
                        case "FloatValue" -> "size += 4;";
                        case "DoubleValue" -> "size += 8;";
                        case "BytesValue" -> "size += ((Bytes) %s).length();".formatted(getValueCode);
                        default -> throw new UnsupportedOperationException(
                                "Unhandled optional message type: " + field.messageType());
                    };
        } else if (field.repeated()) {
            return prefix
                    + switch (field.type()) {
                        case ENUM -> "size += %s.size() * 8;".formatted(getValueCode);
                        case MESSAGE -> "size += sizeInMemoryMessageList($valueCode, $codec::measureRecord);"
                                .replace("$valueCode", getValueCode)
                                .replace(
                                        "$codec",
                                        ((SingleField) field).messageTypeModelPackage() + "."
                                                + Common.capitalizeFirstLetter(field.messageType()) + ".PROTOBUF");
                        case BOOL -> "size += %s.size() * 1;".formatted(getValueCode);
                        case INT32, SINT32, UINT32, FIXED32, SFIXED32, FLOAT -> "size += %s.size() * 4;"
                                .formatted(getValueCode);
                        case INT64, SINT64, UINT64, FIXED64, SFIXED64, DOUBLE -> "size += %s.size() * 8;"
                                .formatted(getValueCode);
                        case BYTES -> "size += sizeInMemoryBytesList(%s);".formatted(getValueCode);
                        case STRING -> "size += sizeInMemoryStringList(%s);".formatted(getValueCode);
                        default -> "";
                            //                default -> throw new UnsupportedOperationException("Unhandled repeated
                            // message type: " + field.messageType());
                    };
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
            final Function<Field, String> getValueBuilder = mapEntryField ->
                    mapEntryField == mapField.keyField() ? "k" : (mapEntryField == mapField.valueField() ? "v" : null);
            final String fieldSizeOfLines = CodecSizeInMemoryMethodGenerator.buildFieldSizeOfLines(
                    field.name(), mapEntryFields, getValueBuilder, false);
            return prefix
                    + """
                        if (!$map.isEmpty()) {
                            final Pbj$javaFieldType pbjMap = (Pbj$javaFieldType) $map;
                            final int mapSize = pbjMap.size();
                            for (int i = 0; i < mapSize; i++) {
                                size += sizeOfTag($fieldDef, WIRE_TYPE_DELIMITED);
                                final int sizePre = size;
                                $K k = pbjMap.getSortedKeys().get(i);
                                $V v = pbjMap.get(k);
                                $fieldSizeOfLines
                                size += sizeOfVarInt32(size - sizePre);
                            }
                        }
                        """
                            .replace("$fieldDef", fieldDef)
                            .replace("$map", getValueCode)
                            .replace("$javaFieldType", mapField.javaFieldType())
                            .replace("$K", mapField.keyField().type().boxedType)
                            .replace(
                                    "$V",
                                    mapField.valueField().type() == Field.FieldType.MESSAGE
                                            ? ((SingleField) mapField.valueField()).messageType()
                                            : mapField.valueField().type().boxedType)
                            .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT));
        } else {
            return prefix
                    + switch (field.type()) {
                        case ENUM -> "size += 8;";
                        case STRING -> "size += ((String) %s).length() * 2;".formatted(getValueCode);
                        case MESSAGE -> "size += $codec.sizeInMemory($valueCode);"
                                .replace("$valueCode", getValueCode)
                                .replace(
                                        "$codec",
                                        ((SingleField) field).messageTypeModelPackage() + "."
                                                + Common.capitalizeFirstLetter(field.messageType()) + ".PROTOBUF");
                        case BOOL -> "size += 1;";
                        case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "size += 4;";
                        case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> "size += 8;";
                        case BYTES -> "size += ((Bytes) %s).length();".formatted(getValueCode);
                        default -> "";
                            //                default -> throw new UnsupportedOperationException("Unhandled repeated
                            // message type: " + field.messageType());
                    };
        }
    }
}
