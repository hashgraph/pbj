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
final class CodecWriteByteArrayMethodGenerator {

    static String generateWriteMethod(
            final String modelClassName, final String schemaClassName, final List<Field> fields) {
        final String fieldWriteLines = buildFieldWriteLines(
                modelClassName,
                schemaClassName,
                fields,
                field -> {
                    if (field.type() == Field.FieldType.ENUM) {
                        if (field.repeated()) {
                            return "data.%sProtoOrdinals()".formatted(field.nameCamelFirstLower());
                        } else {
                            return "data.%sProtoOrdinal()".formatted(field.nameCamelFirstLower());
                        }
                    }
                    return " data.%s()".formatted(field.nameCamelFirstLower());
                },
                true);
        // spotless:off
        return
            """
            /**
             * Writes an item to the given byte array, this is a performance focused method. In non-performance centric use
             * cases there are simpler methods such as toBytes() or writing to a {@link WritableStreamingData}.
             *
             * @param data The item to write. Must not be null.
             * @param output The byte array to write to, this must be large enough to hold the entire item.
             * @param startOffset The offset in the output array to start writing at.
             * @return The number of bytes written to the output array.
             * @throws IndexOutOfBoundsException If the output array is not large enough to hold the entire item.
             */
            public int write(@NonNull $modelClass data, @NonNull byte[] output, final int startOffset) {
                int offset = startOffset;
            $fieldWriteLines
                // Write unknown fields if there are any
                for (final UnknownField uf : data.getUnknownFields()) {
                    final int tag = (uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal();
                    offset += ProtoArrayWriterTools.writeUnsignedVarInt(output, offset, tag);
                    offset += uf.bytes().writeTo(output, offset);
                }
                return offset - startOffset;
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
            getValueCode = "(%s)data.%s().as()".formatted(field.javaFieldType(), oneOfField.nameCamelFirstLower());
            prefix += "if (data.%s().kind() == %s.%s)%n"
                    .formatted(oneOfField.nameCamelFirstLower(), oneOfType, Common.camelToUpperSnake(field.name()));
        }
        // spotless:off
        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix + switch (field.messageType()) {
                case "StringValue" -> "offset += ProtoArrayWriterTools.writeOptionalString(output, offset, %s, %s);"
                        .formatted(fieldDef,getValueCode);
                case "BoolValue" -> "offset += ProtoArrayWriterTools.writeOptionalBoolean(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int32Value" -> "offset += ProtoArrayWriterTools.writeOptionalInt32Value(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "UInt32Value" -> "offset += ProtoArrayWriterTools.writeOptionalUInt32Value(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "Int64Value","UInt64Value" -> "offset += ProtoArrayWriterTools.writeOptionalInt64Value(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "FloatValue" -> "offset += ProtoArrayWriterTools.writeOptionalFloat(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "DoubleValue" -> "offset += ProtoArrayWriterTools.writeOptionalDouble(output, offset, %s, %s);"
                        .formatted(fieldDef, getValueCode);
                case "BytesValue" -> "offset += ProtoArrayWriterTools.writeOptionalBytes(output, offset, %s, %s);"
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
                    case ENUM -> "offset += ProtoArrayWriterTools.writeEnumListProtoOrdinals(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case MESSAGE -> "offset += ProtoArrayWriterTools.writeMessageList(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    case INT32 -> "offset += ProtoArrayWriterTools.writeInt32List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case UINT32 -> "offset += ProtoArrayWriterTools.writeUInt32List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case SINT32 -> "offset += ProtoArrayWriterTools.writeSInt32List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case FIXED32, SFIXED32 -> "offset += ProtoArrayWriterTools.writeFixed32List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case INT64, UINT64 -> "offset += ProtoArrayWriterTools.writeInt64List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case SINT64 -> "offset += ProtoArrayWriterTools.writeSInt64List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case FIXED64, SFIXED64 -> "offset += ProtoArrayWriterTools.writeFixed64List(output, offset, %s, %s);"
                            .formatted(fieldDef, getValueCode);

                    default -> "offset += ProtoArrayWriterTools.write%sList(output, offset, %s, %s);"
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
                                    offset += ProtoArrayWriterTools.writeTag(output, offset, $fieldDef, WIRE_TYPE_DELIMITED);
                                    $K k = pbjMap.getSortedKeys().get(i);
                                    $V v = pbjMap.get(k);
                                    int size = 0;
                                    $fieldSizeOfLines
                                    offset += ProtoArrayWriterTools.writeUnsignedVarInt(output, offset, size);
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
                    case ENUM -> "offset += ProtoArrayWriterTools.writeEnumProtoOrdinal(output, offset, %s, (%s)$suffix);"
                            .replace("$suffix", field.parent() == null ? "" : ".protoOrdinal()")
                            .formatted(fieldDef, getValueCode);
                    case STRING -> "offset += ProtoArrayWriterTools.writeString(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case MESSAGE -> "offset += ProtoArrayWriterTools.writeMessage(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    case BOOL -> "offset += ProtoArrayWriterTools.writeBoolean(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case INT32 -> "offset += ProtoArrayWriterTools.writeInt32(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case UINT32 -> "offset += ProtoArrayWriterTools.writeUInt32(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case SINT32 -> "offset += ProtoArrayWriterTools.writSInt32(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case FIXED32, SFIXED32 -> "offset += ProtoArrayWriterTools.writeFixed32(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case INT64, UINT64 -> "offset += ProtoArrayWriterTools.writeInt64(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case SINT64 -> "offset += ProtoArrayWriterTools.writeSInt64(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case FIXED64, SFIXED64 -> "offset += ProtoArrayWriterTools.writeFixed64(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    case BYTES -> "offset += ProtoArrayWriterTools.writeBytes(output, offset, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                    default -> "offset += ProtoArrayWriterTools.write%s(output, offset, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
                };
            }
        }
        // spotless:on
    }
}
