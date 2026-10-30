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

    static String generateSequentialWriteMethod(
            final String modelClassName, final String schemaClassName, final List<Field> fields) {
        final String fieldWriteLines = buildFieldWriteLines(
                fields,
                f -> generateFieldSequentialWriteLines(f, modelClassName, schemaClassName, valueBuilder(f), true));
        // spotless:off
        return
            """
            /**
             * Write out a $modelClass model to sequential output at its current position in protobuf format.
             *
             * @param data The input model to write
             * @param out The output to write to
             * @throws IOException If there is a problem writing
             */
            protected final void writeImpl(@NonNull $modelClass data, @NonNull final WritableSequentialData out) throws IOException {
                $fieldWriteLines
                // Check if not-empty to avoid creating a lambda if there's nothing to write.
                if (!data.getUnknownFields().isEmpty()) {
                    data.getUnknownFields().forEach(uf -> {
                        final int tag = (uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal();
                        out.writeVarInt(tag, false);
                        uf.bytes().writeTo(out);
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
            final List<Field> fields, final Function<Field, String> fieldWriteLines) {
        return fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(fieldWriteLines)
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT);
    }

    /**
     * Generate lines of code for writing a field to sequential output.
     *
     * @param field The field to generate writing line of code for
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @return java code to write field to output
     */
    private static String generateFieldSequentialWriteLines(
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
            getValueCode = "(%s)data.%s().as()".formatted(field.javaFieldType(), oneOfField.nameCamelFirstLower());
            prefix += "if (data.%s().kind() == %s.%s)%n"
                    .formatted(oneOfField.nameCamelFirstLower(), oneOfType, Common.camelToUpperSnake(field.name()));
        }
        // spotless:off
        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix + switch (field.messageType()) {
                case "StringValue" -> "writeOptionalString(out, %s, %s);"
                        .formatted(fieldDef, getValueCode);
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
                    case ENUM -> "writeEnumListProtoOrdinals(out, %s, %s);"
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
                        mapEntryFields,
                        f -> generateFieldSequentialWriteLines(f, f.name(), schemaClassName, getValueBuilder.apply(f), false));
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
                    case ENUM -> "writeEnumProtoOrdinal(out, %s, (%s)$suffix);"
                            .replace("$suffix", field.parent() == null ? "" : ".protoOrdinal()")
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

    static String generateRandomAccessWriteMethod(
            final String modelClassName, final String schemaClassName, final List<Field> fields) {
        final String fieldWriteLines = buildFieldWriteLines(
                fields,
                f -> generateFieldPositionedWriteLines(f, modelClassName, schemaClassName, valueBuilder(f), true));
        // spotless:off
        return
            """
            /**
             * Write out a $modelClass model to random access output at a given offset in protobuf format.
             *
             * @param data The input model to write
             * @param out The output to write to
             * @param offset The offset to write at
             * @return The number of bytes written
             * @throws IOException If there is a problem writing
             */
            protected final long writeImpl(@NonNull $modelClass data, @NonNull final RandomAccessData out, final long offset) throws IOException {
                long bytesWritten = 0;
                $fieldWriteLines
                // Check if not-empty to avoid creating a lambda if there's nothing to write.
                final List<UnknownField> unknownFields = data.getUnknownFields();
                for (int i = 0; i < unknownFields.size(); i++) {
                    final UnknownField uf = unknownFields.get(i);
                    final int tag = (uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal();
                    bytesWritten += out.putVarInt(offset + bytesWritten, tag, false);
                    bytesWritten += uf.bytes().writeTo(out, offset + bytesWritten);
                }
                return bytesWritten;
            }
            """
            .replace("$modelClass", modelClassName)
            .replace("$fieldWriteLines", fieldWriteLines)
            .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Generate lines of code for writing a field to random access output.
     *
     * @param field The field to generate writing line of code for
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @return java code to write field to output
     */
    private static String generateFieldPositionedWriteLines(
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
            getValueCode = "(%s)data.%s().as()".formatted(field.javaFieldType(), oneOfField.nameCamelFirstLower());
            prefix += "if (data.%s().kind() == %s.%s)%n"
                    .formatted(oneOfField.nameCamelFirstLower(), oneOfType, Common.camelToUpperSnake(field.name()));
        }
        // spotless:off
        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix + switch (field.messageType()) {
                case "StringValue" -> "bytesWritten += writeOptionalString(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "BoolValue" -> "bytesWritten += writeOptionalBoolean(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int32Value","UInt32Value" -> "bytesWritten += writeOptionalInteger(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int64Value","UInt64Value" -> "bytesWritten += writeOptionalLong(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "FloatValue" -> "bytesWritten += writeOptionalFloat(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "DoubleValue" -> "bytesWritten += writeOptionalDouble(out, offset + bytesWritten, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "BytesValue" -> "bytesWritten += writeOptionalBytes(out, offset + bytesWritten, %s, %s);"
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
                    case ENUM -> "bytesWritten += writeEnumListProtoOrdinals(out, offset + bytesWritten, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case MESSAGE -> "bytesWritten += writeMessageList(out, offset + bytesWritten, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    default -> "bytesWritten += write%sList(out, offset + bytesWritten, %s, %s);"
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
                        mapEntryFields,
                        f -> generateFieldPositionedWriteLines(f, f.name(), schemaClassName, getValueBuilder.apply(f), false));
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
                                    bytesWritten += writeTag(out, offset + bytesWritten, $fieldDef, WIRE_TYPE_DELIMITED);
                                    $K k = pbjMap.getSortedKeys().get(i);
                                    $V v = pbjMap.get(k);
                                    int size = 0;
                                    $fieldSizeOfLines
                                    bytesWritten += out.putVarInt(offset + bytesWritten, size, false);
                                    $fieldWriteLines
                                }
                            }
                            """
                        .replace("$fieldDef", fieldDef)
                        .replace("$map", getValueCode)
                        .replace("$javaFieldType", mapField.javaFieldType())
                        .replace("$K", mapField.keyField().type().boxedType)
                        .replace("$V", mapField.valueField().type() == Field.FieldType.MESSAGE ? (mapField.valueField()).messageType() : mapField.valueField().type().boxedType)
                        .replace("$fieldWriteLines", fieldWriteLines.indent(DEFAULT_INDENT))
                        .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT));
            } else {
                return prefix + switch(field.type()) {
                    case ENUM -> "bytesWritten += writeEnumProtoOrdinal(out, offset + bytesWritten, %s, (%s)$suffix);"
                            .replace("$suffix", field.parent() == null ? "" : ".protoOrdinal()")
                            .formatted(fieldDef, getValueCode);
                    case STRING -> "bytesWritten += writeString(out, offset + bytesWritten, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case MESSAGE -> "bytesWritten += writeMessage(out, offset + bytesWritten, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    case BOOL -> "bytesWritten += writeBoolean(out, offset + bytesWritten, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case INT32, UINT32, SINT32, FIXED32, SFIXED32, INT64, SINT64, UINT64, FIXED64, SFIXED64, BYTES ->
                            "bytesWritten += write%s(out, offset + bytesWritten, %s, %s, %s);".formatted(writeMethodName, fieldDef, getValueCode, skipDefault);
                    default -> "bytesWritten += write%s(out, offset + bytesWritten, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
                };
            }
        }
        // spotless:on
    }

    private static String valueBuilder(final Field field) {
        if (field.type() == Field.FieldType.ENUM) {
            if (field.repeated()) {
                return "data.%sProtoOrdinals()".formatted(field.nameCamelFirstLower());
            } else {
                return "data.%sProtoOrdinal()".formatted(field.nameCamelFirstLower());
            }
        }
        return "data.%s()".formatted(field.nameCamelFirstLower());
    }
}
