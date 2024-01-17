package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.PbjCompilerException;

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
    }

    static String generateParseMethod(final String modelClassName, final List<Field> fields) {
        return """
                /**
                 * Parses a $modelClassName object from ProtoBuf bytes in a {@link ReadableSequentialData}
                 *
                 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
                 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
                 *              method. If null, the method returns immediately. If there are no bytes remaining in the data input,
                 *              then the method also returns immediately.
                 * @return Parsed $modelClassName model object or null if data input was null or empty
                 * @throws IOException If the protobuf stream is not empty and has malformed protobuf bytes (i.e. isn't valid protobuf).
                 */
                public @NonNull $modelClassName parse(@NonNull final ReadableSequentialData input) throws IOException {
                    return parseInternal(input, false);
                }
                """
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;".formatted(field.javaFieldType(),
                field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
        .replace("$fieldsList",fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", ")))
        .replace("$caseStatements",generateCaseStatements(fields))
        .replaceAll("\n", "\n" + Common.FIELD_INDENT);
    }

    static String generateParseStrictMethod(final String modelClassName, final List<Field> fields) {
        return """
                /**
                 * Parses a $modelClassName object from ProtoBuf bytes in a {@link ReadableSequentialData} in strict mode, such that
                 * parsing will fail if the encoded protobuf object contains any fields that are unknown to this
                 * version of the parser.
                 *
                 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
                 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
                 *              method. If null, the method returns immediately. If there are no bytes remaining in the data input,
                 *              then the method also returns immediately.
                 * @return Parsed $modelClassName model object or null if data input was null or empty
                 * @throws UnknownFieldException If an unknown field is encountered while parsing the object
                 * @throws IOException If the protobuf stream is not empty and has malformed
                 * 									  protobuf bytes (i.e. isn't valid protobuf).
                 */
                public @NonNull $modelClassName parseStrict(@NonNull final ReadableSequentialData input) throws IOException {
                    return parseInternal(input, true);
                }
                """
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;".formatted(field.javaFieldType(),
                field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
        .replace("$fieldsList",fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", ")))
        .replace("$caseStatements",generateCaseStatements(fields))
        .indent(DEFAULT_INDENT);
    }

    static String generateParseInternalMethod(final String modelClassName, final List<Field> fields) {
        return """
                /**
                 * Parses a $modelClassName object from ProtoBuf bytes in a {@link ReadableSequentialData}. Throws if in strict mode ONLY.
                 *
                 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
                 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
                 *              method. If null, the method returns immediately. If there are no bytes remaining in the data input,
                 *              then the method also returns immediately.
                 * @return Parsed $modelClassName model object or null if data input was null or empty
                 * @throws UnknownFieldException If an unknown field is encountered while parsing the object and we are in strict mode
                 * @throws IOException If the protobuf stream is not empty and has malformed
                 * 									  protobuf bytes (i.e. isn't valid protobuf).
                 */
                private @NonNull $modelClassName parseInternal(
                        @NonNull final ReadableSequentialData input,
                        final boolean strictMode) throws IOException {
                    try {
                        // -- TEMP STATE FIELDS --------------------------------------
                        $fieldDefs

                        // -- PARSE LOOP ---------------------------------------------
                        // Continue to parse bytes out of the input stream until we get to the end.
                        while (input.hasRemaining()) {
                            // Note: ReadableStreamingData.hasRemaining() won't flip to false
                            // until the end of stream is actually hit with a read operation.
                            // So we catch this exception here and **only** here, because an EOFException
                            // anywhere else suggests that we're processing malformed data and so
                            // we must re-throw the exception then.
                            final int tag;
                            try {
                                // Read the "tag" byte which gives us the field number for the next field to read
                                // and the wire type (way it is encoded on the wire).
                                tag = input.readVarInt(false);
                            } catch (EOFException e) {
                                // There's no more fields. Stop the parsing loop.
                                break;
                            }

                            // The field is the top 5 bits of the byte. Read this off
                            final int field = tag >>> TAG_FIELD_OFFSET;

                            // Ask the Schema to inform us what field this represents.
                            final var f = getField(field);

                            // Given the wire type and the field type, parse the field
                            switch (tag) {
                $caseStatements
                                default -> {
                                    // The wire type is the bottom 3 bits of the byte. Read that off
                                    final int wireType = tag & TAG_WIRE_TYPE_MASK;
                                    // handle error cases here, so we do not do if statements in normal loop
                                    // Validate the field number is valid (must be > 0)
                                    if (field == 0) {
                                        throw new IOException("Bad protobuf encoding. We read a field value of "
                                            + field);
                                    }
                                    // Validate the wire type is valid (must be >=0 && <= 5).
                                    // Otherwise we cannot parse this.
                                    // Note: it is always >= 0 at this point (see code above where it is defined).
                                    if (wireType > 5) {
                                        throw new IOException("Cannot understand wire_type of " + wireType);
                                    }
                                    // It may be that the parser subclass doesn't know about this field
                                    if (f == null) {
                                        if (strictMode) {
                                            // Since we are parsing is strict mode, this is an exceptional condition.
                                            throw new UnknownFieldException(field);
                                        } else {
                                            // We just need to read off the bytes for this field to skip it
                                            // and move on to the next one.
                                            skipField(input, ProtoConstants.get(wireType));
                                        }
                                    } else {
                                        throw new IOException("Bad tag [" + tag + "], field [" + field
                                                + "] wireType [" + wireType + "]");
                                    }
                                }
                            }
                        }
                        return new $modelClassName($fieldsList);
                    } catch (final RuntimeException uncheckedException) {
                        throw new IOException("Unchecked parsing exception", uncheckedException);
                    }
                }
                """
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;".formatted(field.javaFieldType(),
                field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
        .replace("$fieldsList",fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", ")))
        .replace("$caseStatements",generateCaseStatements(fields))
        .indent(DEFAULT_INDENT);
    }

    /**
     * Generate switch case statements for each tag (field & wire type pair). For repeated numeric value types we
     * generate 2 case statements for packed and unpacked encoding.
     *
     * @param fields list of all fields in record
     * @return string of case statement code
     */
    private static String generateCaseStatements(final List<Field> fields) {
        StringBuilder sb = new StringBuilder();
        for(Field field: fields) {
            if (field instanceof final OneOfField oneOfField) {
                for(final Field subField: oneOfField.fields()) {
                    generateFieldCaseStatement(sb,subField);
                }
            } else if (field.repeated() && field.type().wireType() != Common.TYPE_LENGTH_DELIMITED) {
                // for repeated fields that are not length encoded there are 2 forms they can be stored in file.
                // "packed" and repeated primitive fields
                generateFieldCaseStatement(sb, field);
                generateFieldCaseStatementPacked(sb, field);
            } else {
                generateFieldCaseStatement(sb, field);
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
    private static void generateFieldCaseStatementPacked(final StringBuilder sb, final Field field) {
        final int wireType = Common.TYPE_LENGTH_DELIMITED;
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        sb.append("case " + tag +" /* type=" + wireType + " [" + field.type() + "] packed-repeated " +
                "field=" + fieldNum + " [" + field.name() + "] */ -> {\n");
        sb.append("""
				// Read the length of packed repeated field data
				final long length = input.readVarInt(false);
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
                .indent(DEFAULT_INDENT)
        );
        sb.append("\n}\n");
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field field to generate case statement for
     * @param sb StringBuilder to append code to
     */
    private static void generateFieldCaseStatement(final StringBuilder sb, final Field field) {
        final int wireType = field.optionalValueType() ? Common.TYPE_LENGTH_DELIMITED : field.type().wireType();
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        sb.append("case " + tag +" /* type=" + wireType + " [" + field.type() + "] " +
                "field=" + fieldNum + " [" + field.name() + "] */ -> {\n");
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
                    .replace("$fieldType", field.javaFieldType())
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
        } else if (field.type() == Field.FieldType.MESSAGE){
            sb.append("""
						final var messageLength = input.readVarInt(false);
						final $fieldType value;
						if (messageLength == 0) {
							value = $fieldType.DEFAULT;
						} else {
							final var limitBefore = input.limit();
							// Make sure that we have enough bytes in the message
							// to read the subObject.
							// If the buffer is truncated on the boundary of a subObject,
							// we will not throw.
							final var startPos = input.position();
							try {
								if ((startPos + messageLength) > limitBefore)
									throw new BufferUnderflowException();
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
                    .indent(DEFAULT_INDENT)
            );
        } else {
            sb.append(("final var value = " + readMethod(field) + ";\n").indent(DEFAULT_INDENT));
        }
        // set value to temp var
        sb.append(Common.FIELD_INDENT);
        if (field.parent() != null && field.repeated()) {
            throw new PbjCompilerException("Fields can not be oneof and repeated ["+field+"]");
        } else if (field.parent() != null) {
            final var oneOfField = field.parent();
            sb.append("temp_" + oneOfField.name() + " =  new %s<>(".formatted(oneOfField.className()) +
                    oneOfField.getEnumClassRef() + '.' + Common.camelToUpperSnake(field.name()) + ", value);\n");
        } else if (field.repeated()) {
            sb.append("temp_" + field.name() + " = addToList(temp_" + field.name() + ",value);\n");
        } else {
            sb.append("temp_" + field.name() + " = value;\n");
        }
        sb.append("}\n");
    }

    private static String readMethod(Field field) {
        if (field.optionalValueType()) {
            return switch (field.messageType()) {
                case "StringValue" -> "readString(input)";
                case "Int32Value" -> "readInt32(input)";
                case "UInt32Value" -> "readUint32(input)";
                case "Int64Value" -> "readInt64(input)";
                case "UInt64Value" -> "readUint64(input)";
                case "FloatValue" -> "readFloat(input)";
                case "DoubleValue" -> "readDouble(input)";
                case "BoolValue" -> "readBool(input)";
                case "BytesValue" -> "readBytes(input)";
                default -> throw new PbjCompilerException("Optional message type [" + field.messageType() + "] not supported");
            };
        }
        return switch(field.type()) {
            case ENUM ->  Common.snakeToCamel(field.messageType(), true) + ".fromProtobufOrdinal(readEnum(input))";
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
            case STRING -> "readString(input)";
            case BOOL -> "readBool(input)";
            case BYTES -> "readBytes(input)";
            case MESSAGE -> field.parseCode();
            case ONE_OF -> throw new PbjCompilerException("Should never happen, oneOf handled elsewhere");
        };
    }
}
