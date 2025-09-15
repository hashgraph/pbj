// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.Field.FieldType.MAP;
import static com.hedera.pbj.compiler.impl.Field.FieldType.STRING;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.PbjCompilerException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Code to generate the parse method for Codec classes.
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
class CodecParseMethodGenerator {

    /**
     * Because all UNSET OneOf values are the same and we use them often we create a static constant for them and just
     * reuse it throughout codec code.
     *
     * @param fields the fields to generate for
     * @return code for constants
     */
    static String generateUnsetOneOfConstants(final List<Field> fields) {
        // spotless:off
        return "\n" + fields.stream()
            .filter(f -> f instanceof OneOfField)
            .map(f -> {
                final OneOfField field = (OneOfField)f;
                return """
                           /** Constant for an unset oneof for $fieldName */
                           public static final $className<$enum> $unsetFieldName = new $className<>($enum.UNSET,null);
                       """
                        .replace("$className", field.className())
                        .replace("$enum", field.getEnumClassRef())
                        .replace("$fieldName", field.name())
                        .replace("$unsetFieldName", Common.camelToUpperSnake(field.name())+"_UNSET")
                        .replace("$unsetFieldName", field.getEnumClassRef());
            })
            .collect(Collectors.joining("\n"));
        // spotless:on
    }

    static String generateParseMethod(
            final String modelClassName, final String schemaClassName, final List<Field> fields) {
        // spotless:off
        return """
                /**
                 * Parses a $modelClassName object from ProtoBuf bytes in a {@link ReadableSequentialData}. Throws if in strict mode ONLY.
                 *
                 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
                 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
                 *              method. If there are no bytes remaining in the data input,
                 *              then the method also returns immediately.
                 * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
                 * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
                 *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
                 * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
                 * @return Parsed $modelClassName model object or null if data input was null or empty
                 * @throws ParseException If parsing fails
                 */
                public @NonNull $modelClassName parse(
                        @NonNull final ReadableSequentialData input,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth) throws ParseException {
                    if (maxDepth < 0) {
                        throw new ParseException("Reached maximum allowed depth of nested messages");
                    }
                    try {
                        // -- TEMP STATE FIELDS --------------------------------------
                        $fieldDefs
                        List<UnknownField> $unknownFields = null;

                        $parseLoop
                        if ($unknownFields != null) {
                            Collections.sort($unknownFields);
                            $initialSizeOfUnknownFieldsArray = Math.max($initialSizeOfUnknownFieldsArray, $unknownFields.size());
                        }
                        return new $modelClassName($fieldsList);
                    } catch (final Exception anyException) {
                        if (anyException instanceof ParseException parseException) {
                            throw parseException;
                        }
                        throw new ParseException(anyException);
                    }
                }
                """
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;"
                .formatted(field.javaFieldStorageType(),
                field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
        .replace("$fieldsList",
                fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", "))
                + (fields.isEmpty() ? "" : ", ") + "$unknownFields"
        )
        .replace("$parseLoop", generateParseLoop(generateCaseStatements(fields, schemaClassName, false), "", schemaClassName))
        .replace("$skipMaxSize", String.valueOf(Field.DEFAULT_MAX_SIZE))
        .indent(DEFAULT_INDENT);
        // spotless:on
    }

    // prefix is pre-pended to variable names to support a nested parsing loop.
    static String generateParseLoop(
            final String caseStatements, @NonNull final String prefix, @NonNull final String schemaClassName) {
        // spotless:off
        return """
                        // -- PARSE LOOP ---------------------------------------------
                        // Continue to parse bytes out of the input stream until we get to the end.
                        while (input.hasRemaining()) {
                            // Note: ReadableStreamingData.hasRemaining() won't flip to false
                            // until the end of stream is actually hit with a read operation.
                            // So we catch this exception here and **only** here, because an EOFException
                            // anywhere else suggests that we're processing malformed data and so
                            // we must re-throw the exception then.
                            final int $prefixtag;
                            try {
                                // Read the "tag" byte which gives us the field number for the next field to read
                                // and the wire type (way it is encoded on the wire).
                                $prefixtag = input.readVarInt(false);
                            } catch (EOFException e) {
                                // There's no more fields. Stop the parsing loop.
                                break;
                            }

                            // The field is the top 5 bits of the byte. Read this off
                            final int $prefixfield = $prefixtag >>> TAG_FIELD_OFFSET;

                            // Ask the Schema to inform us what field this represents.
                            final var $prefixf = $schemaClassName.getField($prefixfield);

                            // Given the wire type and the field type, parse the field
                            switch ($prefixtag) {
                $caseStatements
                                default -> {
                                    // The wire type is the bottom 3 bits of the byte. Read that off
                                    final int wireType = $prefixtag & TAG_WIRE_TYPE_MASK;
                                    // handle error cases here, so we do not do if statements in normal loop
                                    // Validate the field number is valid (must be > 0)
                                    if ($prefixfield == 0) {
                                        throw new IOException("Bad protobuf encoding. We read a field value of "
                                            + $prefixfield);
                                    }
                                    // Validate the wire type is valid (must be >=0 && <= 5).
                                    // Otherwise we cannot parse this.
                                    // Note: it is always >= 0 at this point (see code above where it is defined).
                                    if (wireType > 5) {
                                        throw new IOException("Cannot understand wire_type of " + wireType);
                                    }
                                    // It may be that the parser subclass doesn't know about this field
                                    if ($prefixf == null) {
                                        if (strictMode) {
                                            // Since we are parsing is strict mode, this is an exceptional condition.
                                            throw new UnknownFieldException($prefixfield);
                                        } else if (parseUnknownFields) {
                                            if ($unknownFields == null) {
                                                $unknownFields = new ArrayList<>($initialSizeOfUnknownFieldsArray);
                                            }
                                            $unknownFields.add(new UnknownField(
                                                    field,
                                                    ProtoConstants.get(wireType),
                                                    extractField(input, ProtoConstants.get(wireType), $skipMaxSize)
                                            ));
                                        } else {
                                            // We just need to read off the bytes for this field to skip it
                                            // and move on to the next one.
                                            skipField(input, ProtoConstants.get(wireType), $skipMaxSize);
                                        }
                                    } else {
                                        throw new IOException("Bad tag [" + $prefixtag + "], field [" + $prefixfield
                                                + "] wireType [" + wireType + "]");
                                    }
                                }
                            }
                        }
                """
                .replace("$caseStatements",caseStatements)
                .replace("$prefix",prefix)
                .replace("$schemaClassName",schemaClassName)
                .replace("$skipMaxSize", String.valueOf(Field.DEFAULT_MAX_SIZE))
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Generate switch case statements for each tag (field & wire type pair). For repeated numeric value types we
     * generate 2 case statements for packed and unpacked encoding.
     *
     * @param fields list of all fields in record
     * @return string of case statement code
     */
    private static String generateCaseStatements(final List<Field> fields, final String schemaClassName, final boolean isMapField) {
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            if (field instanceof final OneOfField oneOfField) {
                for (final Field subField : oneOfField.fields()) {
                    generateFieldCaseStatement(sb, subField, schemaClassName, isMapField);
                }
            } else if (field.repeated() && field.type().wireType() != Common.TYPE_LENGTH_DELIMITED) {
                // for repeated fields that are not length encoded there are 2 forms they can be stored in file.
                // "packed" and repeated primitive fields
                generateFieldCaseStatement(sb, field, schemaClassName, isMapField);
                generateFieldCaseStatementPacked(sb, field, isMapField);
            } else {
                generateFieldCaseStatement(sb, field, schemaClassName, isMapField);
            }
        }
        return sb.toString().indent(DEFAULT_INDENT * 4);
    }

    /**
     * Generate switch case statement for a repeated numeric value type in packed encoding.
     *
     * @param field field to generate case statement for
     * @param sb StringBuilder to append code to
     */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    private static void generateFieldCaseStatementPacked(final StringBuilder sb, final Field field, final boolean isMapField) {
        final int wireType = Common.TYPE_LENGTH_DELIMITED;
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        // spotless:off
        sb.append("case %d /* type=%d [%s] packed-repeated field=%d [%s] */ -> {%n"
                .formatted(tag, wireType, field.type(), fieldNum, field.name()));
        sb.append("""
                // Read the length of packed repeated field data
                final long length = input.readVarInt(false);
                if (length > $maxSize) {
                    throw new ParseException("$fieldName size " + length + " is greater than max " + $maxSize);
                }
                if (input.remaining() < length) {
                    throw new BufferUnderflowException();
                }
                final var beforeLimit = input.limit();
                final long beforePosition = input.position();
                input.limit(input.position() + length);
                while (input.hasRemaining()) {
                    $tempFieldName = addToList($tempFieldName,$readMethod);
                }
                input.limit(beforeLimit);
                if (input.position() != beforePosition + length) {
                    throw new BufferUnderflowException();
                }"""
                .replace("$tempFieldName", "temp_" + field.name())
                .replace("$readMethod", readMethod(field))
                .replace("$maxSize", String.valueOf(field.maxSize()))
                .replace("$fieldName", field.name())
                .indent(DEFAULT_INDENT)
        );
        sb.append("\n}\n");
        // spotless:on
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field field to generate case statement for
     * @param sb StringBuilder to append code to
     */
    private static void generateFieldCaseStatement(
            final StringBuilder sb, final Field field, final String schemaClassName, final boolean isMapField) {
        final int wireType = field.optionalValueType()
                ? Common.TYPE_LENGTH_DELIMITED
                : field.type().wireType();
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        // spotless:off
        sb.append("case %d /* type=%d [%s] field=%d [%s] */ -> {%n"
                .formatted(tag, wireType, field.type(), fieldNum, field.name()));
        if (field.optionalValueType()) {
            sb.append("""
                            // Read the message size, it is not needed
                            final var valueTypeMessageSize = input.readVarInt(false);
                            final $fieldType value;
                            if (valueTypeMessageSize > 0) {
                                final var beforeLimit = input.limit();
                                input.limit(input.position() + valueTypeMessageSize);
                                // read inner tag
                                final int valueFieldTag = input.readVarInt(false);
                                // assert tag is as expected
                                assert (valueFieldTag >>> TAG_FIELD_OFFSET) == 1;
                                assert (valueFieldTag & TAG_WIRE_TYPE_MASK) == $valueTypeWireType;
                                // read value
                                value = $readMethod;
                                input.limit(beforeLimit);
                            } else {
                                // means optional is default value
                                value = $defaultValue;
                            }"""
                    .replace("$fieldType", field.javaFieldStorageType())
                    .replace("$readMethod", readMethod(field))
                    .replace("$defaultValue",
                            switch (field.messageType()) {
                                case "Int32Value", "UInt32Value" -> "0";
                                case "Int64Value", "UInt64Value" -> "0l";
                                case "FloatValue" -> "0f";
                                case "DoubleValue" -> "0d";
                                case "BoolValue" -> "false";
                                case "BytesValue" -> "Bytes.EMPTY";
                                case "StringValue" -> "\"\"";
                                default -> throw new PbjCompilerException("Unexpected and unknown field type " + field.type() + " cannot be parsed");
                            })
                    .replace("$valueTypeWireType", Integer.toString(
                            switch (field.messageType()) {
                                case "StringValue", "BytesValue" -> Common.TYPE_LENGTH_DELIMITED;
                                case "Int32Value", "UInt32Value", "Int64Value", "UInt64Value", "BoolValue" -> Common.TYPE_VARINT;
                                case "FloatValue" -> Common.TYPE_FIXED32;
                                case "DoubleValue" -> Common.TYPE_FIXED64;
                                default -> throw new PbjCompilerException("Unexpected and unknown field type " + field.type() + " cannot be parsed");
                            }))
                    .indent(DEFAULT_INDENT)
            );
            sb.append('\n');
            // spotless:on
        } else if (field.type() == Field.FieldType.MESSAGE) {
            // spotless:off
            sb.append("""
                        final var messageLength = input.readVarInt(false);
                        final $fieldType value;
                        if (messageLength == 0) {
                            value = $fieldType.DEFAULT;
                        } else {
                            if (messageLength > $maxSize) {
                                throw new ParseException("$fieldName size " + messageLength + " is greater than max " + $maxSize);
                            }
                            final var limitBefore = input.limit();
                            // Make sure that we have enough bytes in the message
                            // to read the subObject.
                            // If the buffer is truncated on the boundary of a subObject,
                            // we will not throw.
                            final var startPos = input.position();
                            try {
                                if ((startPos + messageLength) > limitBefore) {
                                    throw new BufferUnderflowException();
                                }
                                input.limit(startPos + messageLength);
                                value = $readMethod;
                                // Make sure we read the full number of bytes. for the types
                                if ((startPos + messageLength) != input.position()) {
                                    throw new BufferOverflowException();
                                }
                            } finally {
                                input.limit(limitBefore);
                            }
                        }
                        """
                    .replace("$readMethod", readMethod(field))
                    .replace("$fieldType", field.javaFieldTypeBase())
                    .replace("$fieldName", field.name())
                    .replace("$maxSize", String.valueOf(field.maxSize()))
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        } else if (field.type() == Field.FieldType.MAP) {
            // This is almost like reading a message above because that's how Protobuf encodes map entries.
            // However(!), we read the key and value fields explicitly to avoid creating temporary entry objects.
            final MapField mapField = (MapField) field;
            final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
            // spotless:off
            sb.append("""
                        final var __map_messageLength = input.readVarInt(false);
                        
                        $fieldDefs
                        if (__map_messageLength != 0) {
                            if (__map_messageLength > $maxSize) {
                                throw new ParseException("$fieldName size " + __map_messageLength + " is greater than max " + $maxSize);
                            }
                            final var __map_limitBefore = input.limit();
                            // Make sure that we have enough bytes in the message
                            // to read the subObject.
                            // If the buffer is truncated on the boundary of a subObject,
                            // we will not throw.
                            final var __map_startPos = input.position();
                            try {
                                if ((__map_startPos + __map_messageLength) > __map_limitBefore) {
                                    throw new BufferUnderflowException();
                                }
                                input.limit(__map_startPos + __map_messageLength);
                                $mapParseLoop
                                // Make sure we read the full number of bytes. for the types
                                if ((__map_startPos + __map_messageLength) != input.position()) {
                                    throw new BufferOverflowException();
                                }
                            } finally {
                                input.limit(__map_limitBefore);
                            }
                        }
                        """
                    .replace("$fieldName", field.name())
                    .replace("$fieldDefs",mapEntryFields.stream().map(mapEntryField ->
                            "%s temp_%s = %s;".formatted(mapEntryField.javaFieldType(),
                                    mapEntryField.name(),
                                    mapEntryField.type() == STRING ? "\"\"" : mapEntryField.javaDefault()
                            )).collect(Collectors.joining("\n")))
                    .replace("$mapParseLoop", generateParseLoop(generateCaseStatements(mapEntryFields, schemaClassName, true), "map_entry_", schemaClassName)
                            .indent(-DEFAULT_INDENT))
                    .replace("$maxSize", String.valueOf(field.maxSize()))
            );
            // spotless:on
        } else {
            sb.append(("final var value = " + readMethod(field) + ";\n").indent(DEFAULT_INDENT));
        }
        // set value to temp var
        // spotless:off
        sb.append(Common.FIELD_INDENT);
        if (field.parent() != null && field.repeated()) {
            throw new PbjCompilerException("Fields can not be oneof and repeated ["+field+"]");
        } else if (field.parent() != null) {
            final var oneOfField = field.parent();
            sb.append("temp_%s =  new %s<>(%s.%s, %s);%n"
                    .formatted(oneOfField.name(), oneOfField.className(), oneOfField.getEnumClassRef(),
                            Common.camelToUpperSnake(field.name()),
                            field.type() == STRING ? "toUtf8String(value)" : "value"));
        } else if (field.repeated()) {
            sb.append(
                """
                if (temp_%s.size() >= %d) {
                    throw new ParseException("%1$s size %%d is greater than max %2$d".formatted(temp_%1$s.size()));
                }
                temp_%1$s = addToList(temp_%1$s,value);
                """.formatted(field.name(), field.maxSize()));
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            sb.append(
                """
                if (__map_messageLength != 0) {
                    if (temp_%s.size() >= %d) {
                        throw new ParseException("%1$s size %%d is greater than max %2$s".formatted(temp_%1$s.size()));
                    }
                    temp_%1$s = addToMap(temp_%1$s, temp_%s, temp_%s);
                }
                """.formatted(field.name(), field.maxSize(),
                        mapField.keyField().name(), mapField.valueField().name()));
        } else if(field.type() == STRING && isMapField){
            sb.append("temp_%s = toUtf8String(value);\n".formatted(field.name()));
        } else {
            sb.append("temp_%s = value;\n".formatted(field.name()));
        }
        sb.append("}\n");
        // spotless:on
    }

    static String readMethod(Field field) {
        if (field.optionalValueType()) {
            return switch (field.messageType()) {
                case "StringValue" -> "readString(input, %d)".formatted(field.maxSize());
                case "Int32Value" -> "readInt32(input)";
                case "UInt32Value" -> "readUint32(input)";
                case "Int64Value" -> "readInt64(input)";
                case "UInt64Value" -> "readUint64(input)";
                case "FloatValue" -> "readFloat(input)";
                case "DoubleValue" -> "readDouble(input)";
                case "BoolValue" -> "readBool(input)";
                case "BytesValue" -> "readBytes(input, %d)".formatted(field.maxSize());
                default ->
                    throw new PbjCompilerException(
                            "Optional message type [%s] not supported".formatted(field.messageType()));
            };
        }
        return switch (field.type()) {
            case ENUM ->
                "%s.fromProtobufOrdinal(readEnum(input))".formatted(Common.snakeToCamel(field.messageType(), true));
            case INT32 -> "readInt32(input)";
            case UINT32 -> "readUint32(input)";
            case SINT32 -> "readSignedInt32(input)";
            case INT64 -> "readInt64(input)";
            case UINT64 -> "readUint64(input)";
            case SINT64 -> "readSignedInt64(input)";
            case FLOAT -> "readFloat(input)";
            case FIXED32 -> "readFixed32(input)";
            case SFIXED32 -> "readSignedFixed32(input)";
            case DOUBLE -> "readDouble(input)";
            case FIXED64 -> "readFixed64(input)";
            case SFIXED64 -> "readSignedFixed64(input)";
            case STRING -> "readString%s(input, %d)".formatted(field.hasDifferentStorageType()?"Raw":"",field.maxSize());
            case BOOL -> "readBool(input)";
            case BYTES -> "readBytes(input, %d)".formatted(field.maxSize());
            case MESSAGE -> field.parseCode();
            case ONE_OF -> throw new PbjCompilerException("Should never happen, oneOf handled elsewhere");
            case MAP -> throw new PbjCompilerException("Should never happen, map handled elsewhere");
        };
    }
}
