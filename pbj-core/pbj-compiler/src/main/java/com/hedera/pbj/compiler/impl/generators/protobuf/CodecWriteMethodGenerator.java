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

    static String generateWriteMethod(final String modelClassName, final List<Field> fields) {
        final String fieldWriteLines = buildFieldWriteLines(
                modelClassName, fields, field -> "data.%s()".formatted(field.nameCamelFirstLower()), true);
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
            }
            """
            .replace("$modelClass", modelClassName)
            .replace("$fieldWriteLines", fieldWriteLines)
            .indent(DEFAULT_INDENT);
        // spotless:on
    }

    private static String buildFieldWriteLines(
            final String modelClassName,
            final List<Field> fields,
            final Function<Field, String> getValueBuilder,
            final boolean skipDefault) {
        return fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldWriteLines(field, modelClassName, getValueBuilder.apply(field), skipDefault))
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
            final Field field, final String modelClassName, String getValueCode, boolean skipDefault) {
        final String fieldDef = Common.camelToUpperSnake(field.name());
        String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
        prefix += "\n";
        String postFix = "";
        int indent = 0;

        if (field.parent() != null) {
            final OneOfField oneOfField = field.parent();
            final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
            getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
            prefix += "if (data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
                    Common.camelToUpperSnake(field.name())+") {\n";
            postFix += "}\n";
            indent ++;
        }
        // spotless:off
        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix + (switch (field.messageType()) {
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
            }).indent(indent) + postFix;
        } else if (field.repeated()) {
            return prefix + (switch(field.type()) {
                    case ENUM -> "writeEnumList(out, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case MESSAGE -> "writeMessageList(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, codecReference);
                    default -> "write%sList(out, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
            }).indent(indent) + postFix;
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
                    .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT))
                    .indent(indent) + postFix
                    ;
            } else {
            return prefix + (switch(field.type()) {
                    case ENUM -> "writeEnum(out, %s, %s);"
                            .formatted(fieldDef, getValueCode);
                    case STRING -> "writeString(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                case MESSAGE -> writeMessageCode(field, fieldDef, getValueCode);
                    case BOOL -> "writeBoolean(out, %s, %s, %s);"
                            .formatted(fieldDef, getValueCode, skipDefault);
                case INT32, UINT32, SINT32, FIXED32, SFIXED32, INT64, SINT64, UINT64, FIXED64, SFIXED64 ->
                        writeNumberCode(field, getValueCode, skipDefault);
                case BYTES ->
                        "write%s(out, %s, %s, %s);"
                                .formatted(writeMethodName, fieldDef, getValueCode, skipDefault);
                    default -> "write%s(out, %s, %s);"
                            .formatted(writeMethodName, fieldDef, getValueCode);
            }).indent(indent) + postFix;
        }
    }

    private static String writeMessageCode(final Field field, final String fieldDef, final String getValueCode) {
        String code = "";
        // When not a oneOf don't write default value
        if (field.parent() != null) {
            code += "if (%s == null) {\n".formatted(getValueCode);
            code += writeTagCode(field, ProtoConstants.WIRE_TYPE_DELIMITED).indent(DEFAULT_INDENT);
            code += "out.writeByte((byte)0);\n".indent(DEFAULT_INDENT);
            code += "}\n";
        }
        code += "if (%s != null) {\n".formatted(getValueCode);
        code += writeTagCode(field, ProtoConstants.WIRE_TYPE_DELIMITED).indent(DEFAULT_INDENT);
        if(field.parent() != null) {
            code += "final int msgSize = ((%s)%s).protobufSize();\n".formatted(field.messageType(), getValueCode)
                    .indent(DEFAULT_INDENT);
        } else {
            code += "final int msgSize = %s.protobufSize();\n".formatted(getValueCode).indent(DEFAULT_INDENT);
        }
        code += "out.writeVarInt(msgSize, false);\n".indent(DEFAULT_INDENT);
        code += "if (msgSize > 0) %s.write(%s, out);\n".formatted(field.messageType()+".PROTOBUF", getValueCode).indent(DEFAULT_INDENT);
        code += "}\n";
        return code;
    }

    private static String writeNumberCode(final Field field, final String getValueCode, final boolean skipDefault) {
        assert !field.repeated() : "Use write***List methods with repeated types";
        final String objectCastName = switch(field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "Integer";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "Long";
            default -> throw new RuntimeException("Unsupported field type. Bug in ProtoOutputStream, shouldn't happen.");
        };
        String code = "";
        int indent = 0;
        if (skipDefault) {
            if(field.parent() == null) {
                code += "if (%s != 0) {\n".formatted(getValueCode);
            } else {
                code += "if ((%s)%s != 0) {\n".formatted(objectCastName, getValueCode);
            }
            indent ++;
        }
        String writeCode = switch (field.type()) {
            case INT32, INT64, UINT64 ->
                writeTagCode(field, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG) +
                "out.writeVarLong("+getValueCode+", false);\n";
            case UINT32 ->
                writeTagCode(field, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG) +
                "out.writeVarLong(Integer.toUnsignedLong("+getValueCode+"), false);\n";
            case SINT32, SINT64 ->
                writeTagCode(field, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG) +
                "out.writeVarLong("+getValueCode+", true);\n";
            case SFIXED32, FIXED32 ->
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTagCode(field, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG) +
                "out.writeInt("+getValueCode+", ByteOrder.LITTLE_ENDIAN);\n";
            case SFIXED64, FIXED64 ->
                // The bytes in protobuf are in little-endian order -- backwards for Java.
                // Smallest byte first.
                writeTagCode(field, ProtoConstants.WIRE_TYPE_VARINT_OR_ZIGZAG) +
                "out.writeLong("+getValueCode+", ByteOrder.LITTLE_ENDIAN);\n";
            default -> throw new RuntimeException("Unsupported field type. Bug in ProtoOutputStream, shouldn't happen.");
        };
        code += writeCode.indent(DEFAULT_INDENT * indent);

        if (skipDefault) {
            indent --;
            code += "}\n".indent(DEFAULT_INDENT * indent);
        }
        return code;
    }

    /**
     * Generate manually inlined code to write tag for field
     *
     * @param field The field to generate tag for
     * @param wireType The wire type for the field
     * @return java code to write tag for field
     */
    private static String writeTagCode(final Field field, final ProtoConstants wireType) {
        return writeVarLongCode(((long)field.fieldNumber() << TAG_TYPE_BITS) | wireType.ordinal(), false);
    }

    /**
     * Generate manually inlined code to write varint
     *
     * @param value The value to write
     * @param zigZag If true, use zigzag encoding
     * @return java code to write varint
     */
    private static String writeVarLongCode(long value, final boolean zigZag) {
            if (zigZag) {
                value = (value << 1) ^ (value >> 63);
            }
            StringBuilder code = new StringBuilder();
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    code.append("out.writeByte((byte) 0x%08X);\n".formatted(value));
                    break;
                } else {
                    code.append("out.writeByte((byte) 0x%08X );\n".formatted((byte) (((int) value & 0x7F) | 0x80)));
                    value >>>= 7;
                }
            }
            return code.toString();
    }

    /** The number of leading bits of the tag that are used to store field type, the rest is field number */
    private static final int TAG_TYPE_BITS = 3;

    /**
     * Protobuf field types
     */
    private enum ProtoConstants {
        /** On wire encoded type for varint */
        WIRE_TYPE_VARINT_OR_ZIGZAG,
        /** On wire encoded type for fixed 64bit */
        WIRE_TYPE_FIXED_64_BIT,
        /** On wire encoded type for length delimited */
        WIRE_TYPE_DELIMITED,
        /** On wire encoded type for group start, deprecated */
        WIRE_TYPE_GROUP_START,
        /** On wire encoded type for group end, deprecated */
        WIRE_TYPE_GROUP_END,
        /** On wire encoded type for fixed 32bit */
        WIRE_TYPE_FIXED_32_BIT;

        // values() seems to allocate a new array on each call, so let's cache it here
        private static final ProtoConstants[] values = values();

        /**
         * Mask used to extract the wire type from the "tag" byte
         */
        public static final int TAG_WIRE_TYPE_MASK = 0b0000_0111;

        public static ProtoConstants get(int ordinal) {
            return values[ordinal];
            }
        }
        // spotless:on
    }
}
