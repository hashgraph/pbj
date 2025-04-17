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
 * Code to generate the write method for Codec classes.
 */
final class CodecWriteMethodGenerator {

    static String generateWriteMethod(
            final String modelClassName, final String schemaClassName, final List<Field> fields) {
        final String fieldWriteLines = buildFieldWriteLines(
                modelClassName,
                schemaClassName,
                fields,
                field -> "data.%s()".formatted(field.nameCamelFirstLower()),
                true);
        // spotless:off
        return
            """
            /**
             * Write out a $modelClass model to output stream in protobuf format.
             *
             * @param data The input model data to write
             * @param out The output stream to write to
             * @throws IOException If there is a problem writing
             */
            public void write(@NonNull $modelClass data, @NonNull final WritableSequentialData out) throws IOException {
                $fieldWriteLines
                // Check if not-empty to avoid creating a lambda if there's nothing to write.
                if (!data.getUnknownFields().isEmpty()) {
                    data.getUnknownFields().forEach((field, uf) -> {
                        final int tag = (field << TAG_FIELD_OFFSET) | uf.wireType().ordinal();
                        uf.bytes().forEach(bytes -> {
                            out.writeVarInt(tag, false);
                            bytes.writeTo(out);
                        });
                    });
                }
            }
            """
            .replace("$modelClass", modelClassName)
            .replace("$fieldWriteLines", fieldWriteLines)
            .indent(DEFAULT_INDENT);
        // spotless:on
    }

    private static String buildFieldWriteLines(
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final Function<Field, String> getValueBuilder,
            final boolean skipDefault) {
        return fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldWriteLines(
                        field, modelClassName, schemaClassName, getValueBuilder.apply(field), skipDefault))
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT);
    }

    /**
     * Generate lines of code for writing field
     *
     * @param field The field to generate writing line of code for
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @param skipDefault skip writing the field if it has default value (for non-oneOf only)
     * @return java code to write field to output
     */
    private static String generateFieldWriteLines(
            final Field field,
            final String modelClassName,
            final String schemaClassName,
            String getValueCode,
            boolean skipDefault) {
        final String fieldDef = schemaClassName + "." + Common.camelToUpperSnake(field.name());
        String prefix = "// [%d] - %s%n".formatted(field.fieldNumber(), field.name());

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = "%s.%sOneOfType".formatted(modelClassName, oneOfField.nameCamelFirstUpper());
            getValueCode = "data.%s().as()".formatted(oneOfField.nameCamelFirstLower());
            prefix += "if (data.%s().kind() == %s.%s)%n"
                    .formatted(oneOfField.nameCamelFirstLower(), oneOfType, Common.camelToUpperSnake(field.name()));
        }
        // spotless:off
        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
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
                default -> throw new UnsupportedOperationException(
                        "Unhandled optional message type:%s".formatted(field.messageType()));
            };
        } else {
            String codecReference = "";
            if (Field.FieldType.MESSAGE.equals(field.type())) {
                codecReference = "%s.%s.PROTOBUF".formatted(((SingleField) field).messageTypeModelPackage(),
                        ((SingleField) field).completeClassName());
            }
            if (field.repeated()) {
                return prefix + switch(field.type()) {
                    case ENUM -> "writeEnumList(out, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case MESSAGE -> "writeMessageList(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    default -> "write%sList(out, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
                };
            } else if (field.type() == Field.FieldType.MAP) {
                // https://protobuf.dev/programming-guides/proto3/#maps
                // On the wire, a map is equivalent to:
                //    message MapFieldEntry {
                //      key_type key = 1;
                //      value_type value = 2;
                //    }
                //    repeated MapFieldEntry map_field = N;
                // NOTE: we serialize the map in the natural order of keys by design,
                //       so that the binary representation of the map is deterministic.
                // NOTE: protoc serializes default values (e.g. "") in maps, so we should too.
                final MapField mapField = (MapField) field;
                final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
                final Function<Field, String> getValueBuilder = mapEntryField ->
                        mapEntryField == mapField.keyField() ? "k" : (mapEntryField == mapField.valueField() ? "v" : null);
                final String fieldWriteLines = buildFieldWriteLines(
                        field.name(),
                        schemaClassName,
                        mapEntryFields,
                        getValueBuilder,
                        false);
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
                                    writeTag(out, $fieldDef, WIRE_TYPE_DELIMITED);
                                    $K k = pbjMap.getSortedKeys().get(i);
                                    $V v = pbjMap.get(k);
                                    int size = 0;
                                    $fieldSizeOfLines
                                    out.writeVarInt(size, false);
                                    $fieldWriteLines
                                }
                            }
                            """
                        .replace("$fieldDef", fieldDef)
                        .replace("$map", getValueCode)
                        .replace("$javaFieldType", mapField.javaFieldType())
                        .replace("$K", mapField.keyField().type().boxedType)
                        .replace("$V", mapField.valueField().type() == Field.FieldType.MESSAGE ? ((SingleField)mapField.valueField()).messageType() : mapField.valueField().type().boxedType)
                        .replace("$fieldWriteLines", fieldWriteLines.indent(DEFAULT_INDENT))
                        .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT));
            } else {
                return prefix + switch(field.type()) {
                    case ENUM -> "writeEnum(out, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case STRING -> "writeString(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case MESSAGE -> "writeMessage(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    case BOOL -> "writeBoolean(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32, INT64, SINT64, UINT64, FIXED64, SFIXED64, BYTES ->
                            "write%s(out, %s, %s, %s);".formatted(writeMethodName, fieldDef, getValueCode, skipDefault);
                    default -> "write%s(out, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
                };
            }
        }
        // spotless:on
    }
}
