// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileAndPackageNamesConfig;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code to generate the write method for Codec classes.
 */
final class CodecWriteByteArrayMethodGenerator {

    static String generateWriteMethod(
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final ContextualLookupHelper lookupHelper) {
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
            protected final int writeImpl(@NonNull $modelClass data, @NonNull byte[] output, final int startOffset) {
                return writeTo(data, output, startOffset, startOffset + data.protobufSize());
            }

            /**
             * Writes an item to the given byte array. Generated codecs call this directly for nested messages, so the call
             * is static and can be inlined.
             *
             * @param data The item to write. Must not be null.
             * @param output The byte array to write to, this must be large enough to hold the entire item.
             * @param startOffset The offset in the output array to start writing at.
             * @param end The end of the item, {@code startOffset + data.protobufSize()}; no byte at or after it is written.
             * @return The number of bytes written to the output array.
             * @throws IndexOutOfBoundsException If the output array is not large enough to hold the entire item.
             */
            public static int writeTo(
                    @NonNull final $modelClass data, @NonNull final byte[] output, final int startOffset, final int end) {
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
            .replace("$fieldWriteLines", buildInlineFieldWriteLines(modelClassName, schemaClassName, fields, lookupHelper))
            .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /** Names the generated write method uses for its own locals and parameters. */
    private static final Set<String> RESERVED_NAMES =
            Set.of("data", "output", "startOffset", "end", "offset", "size", "i", "item", "uf", "tag");

    /**
     * Build the lines that write every field in field-number order, with oneof alternatives that are adjacent in that
     * order grouped into one switch on the oneof's kind.
     */
    private static String buildInlineFieldWriteLines(
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final ContextualLookupHelper lookupHelper) {
        final List<Field> sorted = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sorted.size()) {
            final Field field = sorted.get(i);
            final OneOfField oneOf = field.parent();
            if (oneOf == null) {
                sb.append(generateInlineFieldWrite(field, modelClassName, schemaClassName, lookupHelper));
                i++;
                continue;
            }
            // a run of adjacent alternatives of the same oneof becomes one switch
            final StringBuilder cases = new StringBuilder();
            while (i < sorted.size() && sorted.get(i).parent() == oneOf) {
                final Field alternative = sorted.get(i);
                cases.append("""
                        // [$number] - $name
                        case $kind -> {
                        $body}
                        """.replace("$number", Integer.toString(alternative.fieldNumber()))
                        .replace("$name", alternative.name())
                        .replace("$kind", Common.camelToUpperSnake(alternative.name()))
                        .replace(
                                "$body",
                                generateInlineFieldWrite(alternative, modelClassName, schemaClassName, lookupHelper)
                                        .indent(DEFAULT_INDENT)));
                i++;
            }
            sb.append("""
                    switch (data.$oneOf().kind()) {
                    $cases    default -> {}
                    }
                    """.replace("$oneOf", oneOf.nameCamelFirstLower())
                    .replace("$cases", cases.toString().indent(DEFAULT_INDENT)));
        }
        return sb.toString().indent(DEFAULT_INDENT);
    }

    /**
     * The bytes of a field's tag varint, as Java statements writing them at {@code offset}, with a comment.
     *
     * @param fieldNumber the field number
     * @param wireType the wire type
     * @return code writing the tag
     */
    static String writeTagCode(final int fieldNumber, final int wireType) {
        long tag = ((long) fieldNumber << 3) | wireType;
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        while (true) {
            final int b = (tag >>> 7) == 0 ? (int) tag : (int) ((tag & 0x7F) | 0x80);
            sb.append("output[offset++] = ")
                    .append(b > Byte.MAX_VALUE ? "(byte) " : "")
                    .append("0x%02X;".formatted(b));
            if (first) {
                sb.append(" // tag: field %d, wire type %d".formatted(fieldNumber, wireType));
                first = false;
            }
            sb.append("\n");
            if ((tag >>> 7) == 0) return sb.toString();
            tag >>>= 7;
        }
    }

    /** The Java name of a local holding a field's value, avoiding the names the write method uses itself. */
    private static String localName(final Field field) {
        final String name = field.nameCamelFirstLower();
        return RESERVED_NAMES.contains(name) ? name + "Value" : name;
    }

    /**
     * The fully qualified codec class of a message field's type, or null when that codec is not generated in this
     * build and so must be reached through its {@code Codec} instance.
     */
    private static String directCodecClass(final SingleField field, final ContextualLookupHelper lookupHelper) {
        if (!lookupHelper
                .getLookupHelper()
                .isGeneratedInThisBuild(field.messageTypeModelPackage(), field.completeClassName())) {
            return null;
        }
        return field.messageTypeCodecPackage() + "."
                + Arrays.stream(field.completeClassName().split("\\."))
                        .map(name -> name + FileAndPackageNamesConfig.CODEC_JAVA_FILE_SUFFIX)
                        .collect(Collectors.joining("."));
    }

    /**
     * Generate inline code writing one field: a constant tag, the value, and for fields outside a oneof the proto3
     * default check. Field kinds that are not inlined fall back to {@link #generateFieldWriteLines}.
     */
    private static String generateInlineFieldWrite(
            final Field field,
            final String modelClassName,
            final String schemaClassName,
            final ContextualLookupHelper lookupHelper) {
        final boolean inOneOf = field.parent() != null;
        final String prefix = inOneOf ? "" : "// [%d] - %s%n".formatted(field.fieldNumber(), field.name());
        if (!(field instanceof SingleField single)
                || field.optionalValueType()
                || (field.repeated() && field.type() != Field.FieldType.MESSAGE)) {
            // maps, google.protobuf wrapper types and repeated scalars, strings and bytes keep their helpers
            return fallback(field, modelClassName, schemaClassName);
        }
        final String local = localName(field);
        // outside a oneof, var avoids naming the type; inside, the type is needed to read the OneOf's value
        final String javaType = !inOneOf ? "var" : field.type() == Field.FieldType.ENUM ? "int" : field.javaFieldType();
        final String read = inOneOf
                ? (field.type() == Field.FieldType.ENUM
                        ? "((%s) data.%s().as()).protoOrdinal()"
                                .formatted(field.javaFieldType(), field.parent().nameCamelFirstLower())
                        : "data.%s().as()".formatted(field.parent().nameCamelFirstLower()))
                : valueCode(field).trim();
        final String tag = writeTagCode(field.fieldNumber(), field.type().wireType());
        if (field.repeated()) {
            // repeated message
            final String codec = directCodecClass(single, lookupHelper);
            if (codec == null) {
                return fallback(field, modelClassName, schemaClassName);
            }
            return prefix
                    + """
                    final var $local = $read;
                    for (int i = 0; i < $local.size(); i++) {
                        final var item = $local.get(i);
                    $tag    final int size = item.protobufSize();
                        offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, size);
                        offset += $codec.writeTo(item, output, offset, offset + size);
                    }
                    """.replace("$local", local)
                            .replace("$read", read)
                            .replace("$tag", tag.indent(DEFAULT_INDENT))
                            .replace("$codec", codec);
        }
        final String value;
        final String condition;
        switch (field.type()) {
            case MESSAGE -> {
                final String codec = directCodecClass(single, lookupHelper);
                if (codec == null) {
                    return fallback(field, modelClassName, schemaClassName);
                }
                final String write = """
                        final int size = $local.protobufSize();
                        offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, size);
                        offset += $codec.writeTo($local, output, offset, offset + size);
                        """.replace("$local", local).replace("$codec", codec);
                if (inOneOf) {
                    // oneof members are written even when null, as an empty message
                    return """
                            final $type $local = $read;
                            $tagif ($local == null) {
                                output[offset++] = 0;
                            } else {
                            $write}
                            """.replace("$type", javaType)
                            .replace("$local", local)
                            .replace("$read", read)
                            .replace("$tag", tag)
                            .replace("$write", write.indent(DEFAULT_INDENT));
                }
                return prefix
                        + """
                        final var $local = $read;
                        if ($local != null) {
                        $tag$write}
                        """.replace("$local", local)
                                .replace("$read", read)
                                .replace("$tag", tag.indent(DEFAULT_INDENT))
                                .replace("$write", write.indent(DEFAULT_INDENT));
            }
            case STRING -> {
                value = "offset += ProtoArrayWriterTools.writeStringNoTag(output, offset, end, %s);".formatted(local);
                condition = "%s != null && !%s.isEmpty()".formatted(local, local);
            }
            case BYTES -> {
                value = "offset += ProtoArrayWriterTools.writeBytesNoTag(output, offset, end, %s);".formatted(local);
                condition = "%s.length() != 0".formatted(local);
            }
            case BOOL -> {
                value = inOneOf ? "output[offset++] = %s ? (byte) 1 : 0;".formatted(local) : "output[offset++] = 1;";
                condition = local;
            }
            case INT32, INT64, UINT64, ENUM -> {
                value = "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, %s);".formatted(local);
                condition = "%s != 0".formatted(local);
            }
            case UINT32 -> {
                value = "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, Integer.toUnsignedLong(%s));"
                        .formatted(local);
                condition = "%s != 0".formatted(local);
            }
            case SINT32 -> {
                value = ("offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, "
                                + "Integer.toUnsignedLong((%s << 1) ^ (%s >> 31)));")
                        .formatted(local, local);
                condition = "%s != 0".formatted(local);
            }
            case SINT64 -> {
                value = "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, (%s << 1) ^ (%s >> 63));"
                        .formatted(local, local);
                condition = "%s != 0".formatted(local);
            }
            case FIXED32, SFIXED32 -> {
                value = "offset += ProtoArrayWriterTools.writeFixed32NoTag(output, offset, %s);".formatted(local);
                condition = "%s != 0".formatted(local);
            }
            case FLOAT -> {
                value = "offset += ProtoArrayWriterTools.writeFixed32NoTag(output, offset, Float.floatToIntBits(%s));"
                        .formatted(local);
                condition = "%s != 0".formatted(local);
            }
            case FIXED64, SFIXED64 -> {
                value = "offset += ProtoArrayWriterTools.writeFixed64NoTag(output, offset, %s);".formatted(local);
                condition = "%s != 0".formatted(local);
            }
            case DOUBLE -> {
                value =
                        "offset += ProtoArrayWriterTools.writeFixed64NoTag(output, offset, Double.doubleToLongBits(%s));"
                                .formatted(local);
                condition = "%s != 0".formatted(local);
            }
            default -> {
                return fallback(field, modelClassName, schemaClassName);
            }
        }
        final String declaration = "final %s %s = %s;\n".formatted(javaType, local, read);
        if (inOneOf) {
            // oneof members are always written, even with the default value
            return prefix + declaration + tag + value + "\n";
        }
        return prefix + declaration + "if (" + condition + ") {\n" + (tag + value).indent(DEFAULT_INDENT) + "}\n";
    }

    /** Write a field with the {@code ProtoArrayWriterTools} helpers, as before, without its own oneof guard. */
    private static String fallback(final Field field, final String modelClassName, final String schemaClassName) {
        return generateFieldWriteLines(field, modelClassName, schemaClassName, valueCode(field), true, false) + "\n";
    }

    /** Java code reading a field outside a oneof from {@code data}. */
    private static String valueCode(final Field field) {
        if (field.type() == Field.FieldType.ENUM) {
            return field.repeated()
                    ? "data.%sProtoOrdinals()".formatted(field.nameCamelFirstLower())
                    : "data.%sProtoOrdinal()".formatted(field.nameCamelFirstLower());
        }
        return " data.%s()".formatted(field.nameCamelFirstLower());
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
                        field, modelClassName, schemaClassName, getValueBuilder.apply(field), skipDefault, true))
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
     * @param oneOfGuard for a oneof member, whether to add the comment and kind check; false inside a switch case
     * @return java code to write field to output
     */
    private static String generateFieldWriteLines(
            final Field field,
            final String modelClassName,
            final String schemaClassName,
            String getValueCode,
            boolean skipDefault,
            boolean oneOfGuard) {
        final String fieldDef = schemaClassName + "." + Common.camelToUpperSnake(field.name());
        String prefix = "// [%d] - %s%n".formatted(field.fieldNumber(), field.name());

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = "%s.%sOneOfType".formatted(modelClassName, oneOfField.nameCamelFirstUpper());
            getValueCode = "(%s)data.%s().as()".formatted(field.javaFieldType(), oneOfField.nameCamelFirstLower());
            prefix = oneOfGuard
                    ? prefix
                            + "if (data.%s().kind() == %s.%s)%n"
                                    .formatted(
                                            oneOfField.nameCamelFirstLower(),
                                            oneOfType,
                                            Common.camelToUpperSnake(field.name()))
                    : "";
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
