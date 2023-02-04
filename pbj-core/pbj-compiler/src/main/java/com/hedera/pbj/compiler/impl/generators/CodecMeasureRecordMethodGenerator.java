package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code to generate the measure record method for Codec classes. This measures the number of bytes that would be
 * written if the record was serialized in protobuf format.
 */
class CodecMeasureRecordMethodGenerator {

    static String generateMeasureMethod(final String modelClassName, final List<Field> fields) {
        final String fieldSizeOfLines = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldSizeOfLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
                .collect(Collectors.joining("\n" + Common.FIELD_INDENT));
        return """
                /**
                 * Compute number of bytes that would be written when calling {@code write()} method.
                 *
                 * @param data The input model data to measure write bytes for
                 * @return The length in bytes that would be written
                 */
                public int measureRecord($modelClass data) {
                    int size = 0;
                    $fieldSizeOfLines
                    return size;
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldSizeOfLines", fieldSizeOfLines)
                .replaceAll("\n", "\n" + Common.FIELD_INDENT)
                ;
    }

    /**
     * Generate lines of code for measure method, that measure the size of each field and add to "size" variable.
     *
     * @param field The field to generate size of line
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @return java code for adding fields size to "size" variable
     */
    private static String generateFieldSizeOfLines(final Field field, final String modelClassName, String getValueCode) {
        final String fieldDef = Common.camelToUpperSnake(field.name());
        String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
        prefix += "\n"+ Common.FIELD_INDENT.repeat(1);

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
            getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
            prefix += "if(data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
                    Common.camelToUpperSnake(field.name())+")";
            prefix += "\n"+ Common.FIELD_INDENT.repeat(2);
        }

        final String writeMethodName = field.methodNameType();
        if(field.optionalValueType()) {
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
            return prefix + switch(field.type()) {
                case ENUM -> "size += sizeOfEnumList(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case MESSAGE -> "size += sizeOfMessageList($fieldDef, $valueCode, $codec::measureRecord);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
                default -> "size += sizeOf%sList(%s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        } else {
            return prefix + switch(field.type()) {
                case ENUM -> "size += sizeOfEnum(%s, %s);"
                        .formatted(fieldDef, getValueCode);
                case STRING -> "size += sizeOfString(%s, %s);"
                        .formatted(fieldDef,getValueCode);
                case MESSAGE -> "size += sizeOfMessage($fieldDef, $valueCode, $codec::measureRecord);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
                case BOOL -> "size += sizeOfBoolean(%s, %s);"
                        .formatted(fieldDef,getValueCode);
                default -> "size += sizeOf%s(%s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        }
    }
}
