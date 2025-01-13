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
 * Code to generate the measure record method for Codec classes. This measures the number of bytes that would be
 * written if the record was serialized in protobuf format.
 */
class CodecMeasureRecordMethodGenerator {

    static String generateMeasureMethod(final String modelClassName, final List<Field> fields) {
        final String fieldSizeOfLines = buildFieldSizeOfLines(
                modelClassName,
                fields,
                field -> "data.%s()".formatted(field.nameCamelFirstLower()),
                true);
        return """
                /**
                 * Compute number of bytes that would be written when calling {@code write()} method.
                 *
                 * @param data The input model data to measure write bytes for
                 * @return The length in bytes that would be written
                 */
                public int measureRecord($modelClass data) {
                    return data.protobufSize();
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
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldSizeOfLines(field, modelClassName, getValueBuilder.apply(field), skipDefault))
                .collect(Collectors.joining("\n")).indent(DEFAULT_INDENT);
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
    private static String generateFieldSizeOfLines(final Field field, final String modelClassName, String getValueCode, boolean skipDefault) {
        final String fieldDef = Common.camelToUpperSnake(field.name());
        String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
        prefix += "\n";

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
            getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
            prefix += "if (data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
                    Common.camelToUpperSnake(field.name())+")";
            prefix += "\n";
        }

        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix + switch (field.messageType()) {
                case "StringValue" -> "size += sizeOfOptionalString(%s, %s);"
                        .formatted(fieldDef,getValueCode);
                case "BoolValue" -> "size += sizeOfOptionalBoolean(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int32Value","UInt32Value" -> "size += sizeOfOptionalInteger(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int64Value","UInt64Value" -> "size += sizeOfOptionalLong(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "FloatValue" -> "size += sizeOfOptionalFloat(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "DoubleValue" -> "size += sizeOfOptionalDouble(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "BytesValue" -> "size += sizeOfOptionalBytes(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
            };
        } else if (field.repeated()) {
            return prefix + switch (field.type()) {
                case ENUM -> "size += sizeOfEnumList(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case MESSAGE -> "size += sizeOfMessageList($fieldDef, $valueCode, $codec);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField) field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType()) + ".PROTOBUF");
                default -> "size += sizeOf%sList(%s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
            final Function<Field, String> getValueBuilder = mapEntryField ->
                    mapEntryField == mapField.keyField() ? "k" : (mapEntryField == mapField.valueField() ? "v" : null);
            final String fieldSizeOfLines = CodecMeasureRecordMethodGenerator.buildFieldSizeOfLines(
                    field.name(),
                    mapEntryFields,
                    getValueBuilder,
                    false);
            return prefix + """
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
                    .replace("$V", mapField.valueField().type() == Field.FieldType.MESSAGE ? mapField.valueField().messageType() : mapField.valueField().type().boxedType)
                    .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT))
                    ;
        } else {
            return prefix + switch(field.type()) {
                case ENUM -> "size += sizeOfEnum(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case STRING -> "size += sizeOfString(%s, %s, %s);"
                        .formatted(fieldDef, getValueCode, skipDefault);
                case MESSAGE -> "size += sizeOfMessage($fieldDef, $valueCode, $codec);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
                case BOOL -> "size += sizeOfBoolean(%s, %s, %s);"
                        .formatted(fieldDef, getValueCode, skipDefault);
                case INT32, UINT32, SINT32, FIXED32, SFIXED32, INT64, SINT64, UINT64, FIXED64, SFIXED64, BYTES -> "size += sizeOf%s(%s, %s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode, skipDefault);
                default -> "size += sizeOf%s(%s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        }
    }
}
