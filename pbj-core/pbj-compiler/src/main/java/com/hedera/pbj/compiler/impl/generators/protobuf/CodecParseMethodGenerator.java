// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.PbjCompilerException;
import com.hedera.pbj.compiler.impl.generators.ModelGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
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
            StringBuilder sbFunc,
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final boolean isCacheable) {

        ParseAndDefaultBody parseAndDefaultBodyPair =
                generateParseLoop(generateCaseStatements(sbFunc, fields, schemaClassName), "", schemaClassName);
        // spotless:off
        return """
                /**
                 * Parses a $modelClassName object from ProtoBuf bytes in a {@link SlimBuffer}. Throws if in strict mode ONLY.
                 * <p>
                 * The {@code maxSize} specifies a custom value for the default `Codec.DEFAULT_MAX_SIZE` limit. IMPORTANT:
                 * specifying a value larger than the default one can put the application at risk because a maliciously-crafted
                 * payload can cause the parser to allocate too much memory which can result in OutOfMemory and/or crashes.
                 * It's important to carefully estimate the maximum size limit that a particular protobuf model type should support,
                 * and then pass that value as a parameter. Note that the estimated limit should apply to the **type** as a whole,
                 * rather than to individual instances of the model. In other words, this value should be a constant, or a config
                 * value that is controlled by the application, rather than come from the input that the application reads.
                 * When in doubt, use the other overloaded versions of this method that use the default `Codec.DEFAULT_MAX_SIZE`.
                 *
                 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
                 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
                 *              method. If there are no bytes remaining in the data input,
                 *              then the method also returns immediately.
                 * @param strictMode when {@code true}, the parser errors out on unknown fields; otherwise they'll be simply skipped.
                 * @param parseUnknownFields when {@code true} and strictMode is {@code false}, the parser will collect unknown
                 *                           fields in the unknownFields list in the model; otherwise they'll be simply skipped.
                 * @param maxDepth a ParseException will be thrown if the depth of nested messages exceeds the maxDepth value.
                 * @param maxSize a ParseException will be thrown if the size of a delimited field exceeds the limit
                 * @return Parsed $modelClassName model object or null if data input was null or empty
                 * @throws ParseException If parsing fails
                 */
                public $modelClassName realParse(
                        @NonNull final SlimBuffer input,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth,
                        final int maxSize) throws ParseException {
                    if (maxDepth < 0) {
                        input.setError(SlimBuffer.MaxDepthReached);
                        return null;
                    }
                    // -- TEMP STATE FIELDS --------------------------------------
                    $fieldDefs
                    List<UnknownField> $unknownFields = null;

                    $parseLoop
            $listFieldsWriteProtection
                    if ($unknownFields != null) {
                        Collections.sort($unknownFields);
                        $initialSizeOfUnknownFieldsArray = Math.max($initialSizeOfUnknownFieldsArray, $unknownFields.size());
                    }
                    input.upgradeErrorToParse();
                    $cacheableSupport
                }
                
                private List<UnknownField> defaultCase(int tag, int field, FieldDefinition f, boolean strictMode, boolean parseUnknownFields, List<UnknownField> $unknownFields, SlimBuffer input, int maxSize) {
                    $defaultCaseBody
                    return $unknownFields;
                }
                """
        .replace("$cacheableSupport", isCacheable ? generateCacheableSupport(modelClassName, fields) : "return new $modelClassName($fieldsList);")
        .replace("$modelClassName",modelClassName)
        .replace("$fieldDefs",fields.stream().map(field -> {
            final String javaFieldType = field.type() == Field.FieldType.ENUM ? field.repeated() ? "List" :  "Object" : field.javaFieldType();
            return "    %s temp_%s = %s;".formatted(javaFieldType,
                            field.name(), field.javaDefault());
        }).collect(Collectors.joining("\n")))
        .replace("$fieldsList",
                fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", "))
                + (fields.isEmpty() ? "" : ", ") + "$unknownFields"
        )
        .replace("$parseLoop", parseAndDefaultBodyPair.parseBody())
        .replace("$defaultCaseBody", parseAndDefaultBodyPair.defaultBody())
        .replace("$listFieldsWriteProtection", fields.stream()
                .filter(Field::repeated)
                .map(field -> "if (temp_" + field.name() + " instanceof UnmodifiableArrayList ual) ual.makeReadOnly();")
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT * 2))
        .indent(DEFAULT_INDENT);
        // spotless:on
    }

    static String generateCacheableSupport(String modelClassName, final List<Field> fields) {
        return """
                final int objectHashCode;
                {
                $hashCodeBody
                    objectHashCode = (int) hashCode;
                }
                $modelClassName _theObject = CACHE[objectHashCode & CACHE_KEY_MASK];
                if (_theObject != null) {
                    // Use switch() to reuse the generated equals() body by replacing `return` with `yield`:
                    if (switch (_theObject) {
                        case $modelClassName thatObj -> {
                $equalsBody
                        }
                    }) {
                        return _theObject;
                    }
                }
                // Since we've computed the hashCode already, let's initialize it:
                _theObject = new $modelClassName($fieldsList, objectHashCode);
                CACHE[objectHashCode & CACHE_KEY_MASK] = _theObject;
                return _theObject;
                """.replace("$hashCodeBody", ModelGenerator.generateHashCodeBody(modelClassName, fields, "temp_"))
                .replace(
                        "$equalsBody",
                        ModelGenerator.generateEqualsBody(fields, modelClassName, "temp_")
                                .replace("return false", "yield false")
                                .replace("return true", "yield true")
                                .indent(DEFAULT_INDENT))
                .indent(DEFAULT_INDENT * 2);
    }

    public record ParseAndDefaultBody(String parseBody, String defaultBody) {}

    // prefix is pre-pended to variable names to support a nested parsing loop.
    // The list returned is [$parseLoop, $defaultCaseBody]
    static ParseAndDefaultBody generateParseLoop(
            final String caseStatements, @NonNull final String prefix, @NonNull final String schemaClassName) {
        // spotless:off
        List<String> list = new ArrayList<>();
        list.add("""
                        // -- PARSE LOOP ---------------------------------------------
                        // Continue to parse bytes out of the input stream until we get to the end.
                        while (input.hasMore()) {

                            // Read the "tag" byte which gives us the field number for the next field to read
                            // and the wire type (way it is encoded on the wire).
                            int $prefixtag = input.readVarInt(false);
                            
                            // The field is the top 5 bits of the byte. Read this off
                            final int $prefixfield = $prefixtag >>> TAG_FIELD_OFFSET;

                            // Ask the Schema to inform us what field this represents.
                            final var $prefixf = $schemaClassName.getField($prefixfield);

                            // Given the wire type and the field type, parse the field
                            switch ($prefixtag) {
                                $caseStatements
                                default -> $unknownFields = defaultCase(tag, field, f, strictMode, parseUnknownFields, $unknownFields, input, maxSize);
                            }
                        }
""");
        list.add("""
                        // The wire type is the bottom 3 bits of the byte. Read that off
                        final int wireType = $prefixtag & TAG_WIRE_TYPE_MASK;
                        // handle error cases here, so we do not do if statements in normal loop
                        // Validate the field number is valid (must be > 0)
                        if ($prefixfield == 0) {
                            input.setError(SlimBuffer.IOError);
                            return $unknownFields;
                        }
                        // Validate the wire type is valid (must be >=0 && <= 5).
                        // Otherwise we cannot parse this.
                        // Note: it is always >= 0 at this point (see code above where it is defined).
                        if (wireType > 5) {
                            input.setError(SlimBuffer.Unsupported);
                            return $unknownFields;
                        }
                        // It may be that the parser subclass doesn't know about this field
                        if ($prefixf == null) {
                            if (strictMode) {
                                input.setError(SlimBuffer.UnknownField);
                                return $unknownFields;
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
                            input.setError(SlimBuffer.IOError);
                            return $unknownFields;
                        }""");
        for (int i = 0; i < list.size(); i++) {
            list.set(i, list.get(i)
                .replace("$caseStatements", caseStatements)
                .replace("$prefix", prefix)
                .replace("$schemaClassName", schemaClassName)
                .replace("$skipMaxSize", "maxSize")
                .indent(DEFAULT_INDENT));
        }
        // spotless:on
        return new ParseAndDefaultBody(list.get(0), list.get(1));
    }

    private static String generateCaseStatements(StringBuilder sbFunc, List<Field> fields, String schemaClassName) {
        StringBuilder sb = new StringBuilder();
        for (Field field : fields) {
            if (field instanceof final OneOfField oneOfField) {
                for (final Field subField : oneOfField.fields()) {
                    generateFieldCaseStatement(sb, sbFunc, subField, schemaClassName);
                }
            } else if (field.repeated() && field.type().wireType() != Common.TYPE_LENGTH_DELIMITED) {
                // for repeated fields that are not length encoded there are 2 forms they can be stored in file.
                // "packed" and repeated primitive fields
                generateFieldCaseStatement(sb, sbFunc, field, schemaClassName);
                generateFieldCaseStatementPacked(sb, sbFunc, field);
            } else {
                generateFieldCaseStatement(sb, sbFunc, field, schemaClassName);
            }
        }
        return sb.toString().indent(DEFAULT_INDENT * 4);
    }

    /**
     * Generate switch case statement for a repeated numeric value type in packed encoding.
     *
     * @param field field to generate case statement for
     * @param sbCase code written in case statement
     * @param sbFunc code written in class scope, used to create functions
     */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    private static void generateFieldCaseStatementPacked(
            StringBuilder sbCase, StringBuilder sbFunc, final Field field) {
        final int wireType = Common.TYPE_LENGTH_DELIMITED;
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        var fieldType =
                field.type() == Field.FieldType.ENUM ? field.repeated() ? "List" : "Object" : field.javaFieldType();
        var tempFieldName = "temp_" + field.name();
        // spotless:off
        sbCase.append("case %d /* type=%d [%s] packed-repeated field=%d [%s] */ -> {%n"
                .formatted(tag, wireType, field.type(), fieldNum, field.name()));
        sbCase.append("%s = case%d(input, maxSize, %s);%n".formatted(tempFieldName, tag, tempFieldName));
        sbFunc.append("%s case%d(SlimBuffer input, int maxSize, %s %s) {%n".formatted(fieldType, tag, fieldType, tempFieldName));
        final String preRead;
        int divideAmount = fieldType.equals("List<Integer>") ? 4
                : fieldType.equals("List<Long>") ? 8
                : fieldType.equals("List<Float>") ? 4
                : fieldType.equals("List<Double>") ? 8
                : fieldType.equals("List<Boolean>") ? 1
                : 0;

        if (field.type() == Field.FieldType.ENUM) {
            // spotless:off
            divideAmount = 4;
            preRead = """
                    final int enumOrdinal = readEnum(input);
                    Object value = $enumName.fromProtobufOrdinal(enumOrdinal);
                    if (value == $enumName.UNRECOGNIZED) {
                       value = Integer.valueOf(enumOrdinal);
                    }
                    
                    """
                    .replace("$enumName", Common.snakeToCamel(field.messageType(), true))
                    .indent(DEFAULT_INDENT)
            ;
            // spotless:on
        } else {
            preRead = "";
        }

        if (divideAmount == 0) {
            throw new RuntimeException("Need to implement");
        }
        // next line prevents list.add from growing, but github actions shows
        // that it's faster to not allocate so much memory
        // $readMethod is readVarInt most of the time. Many 1byte int32
        // would cause the list to grow
        // divideAmount = 1;
        sbFunc.append("""
                // Read the length of packed repeated field data
                final int length = input.readVarInt(false);
                if (length > $maxSize) {
                    input.setError(SlimBuffer.Parse);
                    return $tempFieldName;
                }
                final var beforeLimit = input.limit();
                final long beforePosition = input.position();
                input.limit(input.position() + length);
                var list = new UnmodifiableArray$fieldType();
                list.ensureCapacity(length$divideString);
                while (input.hasMore()) {
                    $preReadlist.add($readMethod);
                }
                $tempFieldName = list;
                input.limit(beforeLimit);
                if (input.position() != beforePosition + length) {
                    input.setError(SlimBuffer.BufferUnderflow);
                }""".replace("$tempFieldName", tempFieldName)
                .replace("$preRead", preRead)
                .replace("$fieldType", fieldType)
                .replace("$readMethod", field.type() == Field.FieldType.ENUM ? "value" : readMethod(field))
                .replace("$maxSize", field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                .replace("$fieldName", field.name())
                .replace("$divideString", divideAmount == 1 ? "" : "/%s".formatted(divideAmount))
                .indent(DEFAULT_INDENT));
        sbCase.append("\n}\n");
        sbFunc.append("    return %s;\n    }\n".formatted(tempFieldName));
        // spotless:on
    }

    /**
     * Generate switch case statement for a field.
     *
     * @param field field to generate case statement for
     * @param sbCase code written in case statement
     * @param sbFunc code written in class scope, used to create functions
     */
    private static void generateFieldCaseStatement(
            StringBuilder sbCase, StringBuilder sbFunc, final Field field, final String schemaClassName) {
        final int wireType = field.optionalValueType()
                ? Common.TYPE_LENGTH_DELIMITED
                : field.type().wireType();
        final int fieldNum = field.fieldNumber();
        final int tag = Common.getTag(wireType, fieldNum);
        // spotless:off
        sbCase.append("case %d /* type=%d [%s] field=%d [%s] */ -> {%n"
                .formatted(tag, wireType, field.type(), fieldNum, field.name()));
        if (field.optionalValueType()) {
            sbCase.append("""
                            // Read the message size, it is not needed
                            final var valueTypeMessageSize = input.readVarInt(false);
                            final $fieldType value;
                            if (valueTypeMessageSize > 0) {
                                final var beforeLimit = input.limit();
                                input.limit(input.position() + valueTypeMessageSize);
                                // read inner tag
                                final int valueFieldTag = input.readVarInt(false);
                                assert input.throwOnErrorOrTrue();
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
            sbCase.append('\n');
            // spotless:on
        } else if (field.type() == Field.FieldType.MESSAGE) {
            // spotless:off
            sbCase.append("""
                        final var messageLength = input.readVarInt(false);
                        final $fieldType value;
                        if (messageLength == 0) {
                            value = $fieldType.DEFAULT;
                        } else {
                            if (messageLength > $maxSize) {
                                input.setError(SlimBuffer.Parse);
                                return null;
                            }
                            final var limitBefore = input.limit();
                            // Make sure that we have enough bytes in the message
                            // to read the subObject.
                            // If the buffer is truncated on the boundary of a subObject,
                            // we will not throw.
                            final var startPos = input.position();
                            if ((startPos + messageLength) > limitBefore) {
                                input.setError(SlimBuffer.BufferUnderflow);
                                return null;
                            }
                            input.limit(startPos + messageLength);
                            value = $readMethod;
                            input.limit(limitBefore);
                            // Make sure we read the full number of bytes. for the types
                            if ((startPos + messageLength) != input.position()) {
                                input.setError(SlimBuffer.BufferOverflow);
                                return null;
                            }
                        }
                        """
                    .replace("$readMethod", readMethod(field))
                    .replace("$fieldType", field.javaFieldTypeBase())
                    .replace("$fieldName", field.name())
                    .replace("$maxSize", field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        } else if (field.type() == Field.FieldType.MAP) {
            // This is almost like reading a message above because that's how Protobuf encodes map entries.
            // However(!), we read the key and value fields explicitly to avoid creating temporary entry objects.
            final MapField mapField = (MapField) field;
            final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
            ParseAndDefaultBody parseAndDefaultBodyPair = generateParseLoop(
                    generateCaseStatements(sbFunc, mapEntryFields, schemaClassName), "map_entry_", schemaClassName);
            // spotless:off
            sbCase.append("""
                        final var __map_messageLength = input.readVarInt(false);
                        
                        $fieldDefs
                        if (__map_messageLength != 0) {
                            if (__map_messageLength > $maxSize) {
                                input.setError(SlimBuffer.Parse);
                                return null;
                            }
                            final var __map_limitBefore = input.limit();
                            // Make sure that we have enough bytes in the message
                            // to read the subObject.
                            // If the buffer is truncated on the boundary of a subObject,
                            // we will not throw.
                            final var __map_startPos = input.position();
                            try {
                                if ((__map_startPos + __map_messageLength) > __map_limitBefore) {
                                    input.setError(SlimBuffer.BufferUnderflow);
                                    return null;
                                }
                                input.limit(__map_startPos + __map_messageLength);
                                $mapParseLoop
                                // Make sure we read the full number of bytes. for the types
                                if ((__map_startPos + __map_messageLength) != input.position()) {
                                    input.setError(SlimBuffer.BufferOverflow);
                                    return null;
                                }
                            } finally {
                                input.limit(__map_limitBefore);
                            }
                        }
                        """
                    .replace("$fieldName", field.name())
                    .replace("$fieldDefs",mapEntryFields.stream().map(mapEntryField ->
                            "%s temp_%s = %s;".formatted(mapEntryField.javaFieldType(),
                                    mapEntryField.name(), mapEntryField.javaDefault())).collect(Collectors.joining("\n")))
                    .replace("$mapParseLoop", parseAndDefaultBodyPair.parseBody()
                            .indent(-DEFAULT_INDENT))
                    .replace("$maxSize", field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize")
            );
            // spotless:on
        } else if (field.type() == Field.FieldType.ENUM) {
            // spotless:off
            sbCase.append("""
                        final int enumOrdinal = readEnum(input);
                        final var value = $enumName.fromProtobufOrdinal(enumOrdinal);
                        """
                    .replace("$enumName", Common.snakeToCamel(field.messageType(), true))
                    .replace("$fieldName", field.name())
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        } else {
            sbCase.append(("final var value = " + readMethod(field) + ";\n").indent(DEFAULT_INDENT));
        }
        // set value to temp var
        // spotless:off
        sbCase.append(Common.FIELD_INDENT);
        if (field.parent() != null && field.repeated()) {
            throw new PbjCompilerException("Fields can not be oneof and repeated ["+field+"]");
        } else if (field.parent() != null) {
            final var oneOfField = field.parent();
            sbCase.append("temp_%s =  new %s<>(%s.%s, value);%n"
                    .formatted(oneOfField.name(), oneOfField.className(), oneOfField.getEnumClassRef(),
                            Common.camelToUpperSnake(field.name())));
        } else if (field.repeated()) {
            sbCase.append(
                """
                if (temp_%s.size() >= %s) {
                    input.setError(SlimBuffer.Parse);
                    return null;
                }
                temp_%1$s = addToList(temp_%1$s,value);
                """.formatted(field.name(), field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize"));
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            sbCase.append(
                """
                if (__map_messageLength != 0) {
                    if (temp_%s.size() >= %s) {
                        input.setError(SlimBuffer.Parse);
                        return null;
                    }
                    temp_%1$s = addToMap(temp_%1$s, temp_%s, temp_%s);
                }
                """.formatted(field.name(), field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize",
                        mapField.keyField().name(), mapField.valueField().name()));
        } else if (field.type() == Field.FieldType.ENUM) {
            // spotless:off
            sbCase.append("""
                        temp_$fieldName = value != $enumName.UNRECOGNIZED ? value : Integer.valueOf(enumOrdinal);
                        """
                    .replace("$enumName", Common.snakeToCamel(field.messageType(), true))
                    .replace("$fieldName", field.name())
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        } else {
            sbCase.append("temp_%s = value;\n".formatted(field.name()));
        }
        sbCase.append("}\n");
        // spotless:on
    }

    static String readMethod(Field field) {
        if (field.optionalValueType()) {
            return switch (field.messageType()) {
                case "StringValue" ->
                    "readString(input, %s)"
                            .formatted(field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize");
                case "Int32Value" -> "readInt32(input)";
                case "UInt32Value" -> "readUint32(input)";
                case "Int64Value" -> "readInt64(input)";
                case "UInt64Value" -> "readUint64(input)";
                case "FloatValue" -> "readFloat(input)";
                case "DoubleValue" -> "readDouble(input)";
                case "BoolValue" -> "readBool(input)";
                case "BytesValue" ->
                    "readBytes(input, %s)"
                            .formatted(field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize");
                default ->
                    throw new PbjCompilerException(
                            "Optional message type [%s] not supported".formatted(field.messageType()));
            };
        }
        return switch (field.type()) {
            //            case ENUM ->
            //
            // "%s.fromProtobufOrdinal(readEnum(input))".formatted(Common.snakeToCamel(field.messageType(), true));
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
            case STRING ->
                "readString(input, %s)".formatted(field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize");
            case BOOL -> "readBool(input)";
            case BYTES ->
                "readBytes(input, %s)".formatted(field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize");
            case MESSAGE -> field.parseCode();
            case ENUM -> throw new PbjCompilerException("Should never happen, enum handled elsewhere");
            case ONE_OF -> throw new PbjCompilerException("Should never happen, oneOf handled elsewhere");
            case MAP -> throw new PbjCompilerException("Should never happen, map handled elsewhere");
        };
    }
}
