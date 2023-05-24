package com.hedera.pbj.compiler.impl.generators.protobuf;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code to generate the write method for Codec classes.
 */
final class CodecWriteMethodGenerator {

    static String generateWriteMethod(final String modelClassName, final List<Field> fields) {
        final String fieldWriteLines = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldWriteLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
                .collect(Collectors.joining("\n" + Common.FIELD_INDENT));

        return """     
            /**
             * Write out a $modelClass model to output stream in protobuf format.
             *
             * @param data The input model data to write
             * @param out The output stream to write to
             * @throws IOException If there is a problem writing
             */
            public void write(@NonNull $modelClass data,@NonNull final WritableSequentialData out) throws IOException {
                $fieldWriteLines
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
                case "StringValue" -> "writeOptionalString(out, %s, %s);"
                        .formatted(fieldDef,getValueCode);
                case "BoolValue" -> "writeOptionalBoolean(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int32Value","UInt32Value" -> "writeOptionalInteger(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int64Value","UInt64Value" -> "writeOptionalLong(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "FloatValue" -> "writeOptionalFloat(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "DoubleValue" -> "writeOptionalDouble(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "BytesValue" -> "writeOptionalBytes(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
            };
        } else if (field.repeated()) {
            return prefix + switch(field.type()) {
                case ENUM -> "writeEnumList(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case MESSAGE -> "writeMessageList(out, $fieldDef, $valueCode, $codec::write, $codec::measureRecord);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
//                        .replace("$codec", Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
                default -> "write%sList(out, %s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        } else {
            return prefix + switch(field.type()) {
                case ENUM -> "writeEnum(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case STRING -> "writeString(out, %s, %s);"
                        .formatted(fieldDef,getValueCode);
                case MESSAGE -> "writeMessage(out, $fieldDef, $valueCode, $codec::write, $codec::measureRecord);"
                        .replace("$fieldDef", fieldDef)
                        .replace("$valueCode", getValueCode)
                        .replace("$codec", ((SingleField)field).messageTypeModelPackage() + "." +
                                Common.capitalizeFirstLetter(field.messageType())+ ".PROTOBUF");
                case BOOL -> "writeBoolean(out, %s, %s);"
                        .formatted(fieldDef,getValueCode);
                default -> "write%s(out, %s, %s);"
                        .formatted(writeMethodName, fieldDef, getValueCode);
            };
        }
    }
}
