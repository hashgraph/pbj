// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates the byte-array parser for messages with {@code pbj.fast_parse}: a static {@code parseArray} over an
 * {@code ArrayCursor}, which {@code Codec.parse} uses for array-backed input. It gives the same results and errors as
 * the stream parser {@code parseImpl}.
 *
 * <p>Writers emit fields in field-number order, so the parser first tests for each field's tag in that order, one byte
 * compare per field (the in-order fast path); repeated fields loop on their tag. Oneofs and maps stay out of the fast
 * path: copying every oneof alternative into it doubles the method and made parsing slower in the prototype. A general
 * loop then handles anything else exactly as {@code parseImpl} does: fields out of order or repeated, oneofs, maps,
 * unknown fields, and strict mode.
 */
final class CodecParseArrayMethodGenerator {

    private CodecParseArrayMethodGenerator() {}

    /**
     * Generate the array parser methods, or nothing if the message has no array parser.
     *
     * @param msgDef the message
     * @param modelClassName the model class
     * @param schemaClassName the schema class
     * @param fields the message's fields
     * @param isCacheable whether parsed objects are cached, as in {@code parseImpl}
     * @param lookupHelper the lookup helper
     * @return the methods' code, or an empty string
     */
    static String generateParseArrayMethod(
            final MessageDefContext msgDef,
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final boolean isCacheable,
            final ContextualLookupHelper lookupHelper) {
        if (!lookupHelper.getLookupHelper().hasArrayParser(msgDef)) {
            return "";
        }
        final List<Field> sorted = fields.stream()
                .flatMap(field -> field instanceof OneOfField oneOf ? oneOf.fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        final StringBuilder fastPath = new StringBuilder();
        final StringBuilder cases = new StringBuilder();
        final StringBuilder helpers = new StringBuilder();
        for (final Field field : sorted) {
            if (field instanceof MapField map) {
                cases.append(caseCode(map, Common.TYPE_LENGTH_DELIMITED, mapEntry(map, schemaClassName, lookupHelper)));
            } else if (field.parent() != null) {
                cases.append(
                        caseCode(field, wireType(field), oneOfAlternative((SingleField) field, lookupHelper, helpers)));
            } else if (field.repeated()) {
                repeated((SingleField) field, lookupHelper, fastPath, cases, helpers);
            } else {
                final SingleField single = (SingleField) field;
                final String read = needsHelper(single)
                        ? "temp_%s = %s;%n".formatted(field.name(), valueHelper(single, true, lookupHelper, helpers))
                        : readInto(single, "temp_" + field.name(), true, lookupHelper);
                fastPath.append(fastPathCode(field, wireType(field), "if", read));
                final String strictCheck = single.type() == Field.FieldType.MESSAGE && !single.optionalValueType()
                        ? """
                        if (strictMode && temp_$name != null) {
                            throw new ParseException("A non-repeated $name encountered more than once");
                        }
                        """.replace("$name", field.name())
                        : "";
                cases.append(caseCode(field, wireType(field), strictCheck + read));
            }
        }
        // spotless:off
        return """
                @Override
                protected boolean hasArrayParser() {
                    return true;
                }

                @Override
                protected @NonNull $modelClassName parseArrayImpl(
                        @NonNull final ArrayCursor input,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth,
                        final int maxSize) throws ParseException {
                    return parseArray(input, strictMode, parseUnknownFields, maxDepth, maxSize);
                }

                /**
                 * Parses a $modelClassName object from a byte array, with the same results and errors as
                 * {@link #parseImpl}. {@code Codec.parse} uses it for array-backed input, and generated array parsers call
                 * it directly for nested messages.
                 *
                 * @see #parseImpl(ReadableSequentialData, boolean, boolean, int, int)
                 */
                public static @NonNull $modelClassName parseArray(
                        @NonNull final ArrayCursor input,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth,
                        final int maxSize) throws ParseException {
                    if (maxDepth < 0) {
                        throw new ParseException("Reached maximum allowed depth of nested messages");
                    }
                    try {
                $fieldDefs
                        List<UnknownField> $unknownFields = null;
                        // In-order fast path: writers emit fields in field-number order, so test for each tag in turn
                $fastPath
                        // Everything else, as parseImpl does: fields out of order or repeated, oneofs, maps, unknown fields
                        while (input.hasRemaining()) {
                            final int tag = input.tag();
                            switch (tag) {
                $cases
                                default -> $unknownFields = input.unknownField(
                                        tag, $schemaClassName.getField(tag >>> TAG_FIELD_OFFSET) != null,
                                        strictMode, parseUnknownFields, $unknownFields, maxSize);
                            }
                        }
                $listFieldsWriteProtection
                        if ($unknownFields != null) {
                            Collections.sort($unknownFields);
                        }
                $construct
                    } catch (final Exception anyException) {
                        if (anyException instanceof ParseException parseException) {
                            throw parseException;
                        }
                        throw new ParseException(anyException);
                    }
                }
                $helpers
                """
                .replace("$fieldDefs", fields.stream()
                        .map(field -> "%s temp_%s = %s;".formatted(
                                field.type() == Field.FieldType.ENUM
                                        ? field.repeated() ? "List" : "Object"
                                        : field.javaFieldType(),
                                field.name(),
                                field.javaDefault()))
                        .collect(Collectors.joining("\n"))
                        .indent(DEFAULT_INDENT * 2))
                .replace("$fastPath", fastPath.toString().indent(DEFAULT_INDENT * 2))
                .replace("$cases", cases.toString().indent(DEFAULT_INDENT * 4))
                .replace("$listFieldsWriteProtection", fields.stream()
                        .filter(Field::repeated)
                        .map(field -> "if (temp_" + field.name() + " instanceof UnmodifiableArrayList ual) ual.makeReadOnly();")
                        .collect(Collectors.joining("\n"))
                        .indent(DEFAULT_INDENT * 2))
                .replace("$helpers", helpers.toString())
                .replace("$construct", isCacheable
                        ? CodecParseMethodGenerator.generateCacheableSupport(modelClassName, fields)
                        : "return new $modelClassName($fieldsList);".indent(DEFAULT_INDENT * 2))
                .replace("$fieldsList", fields.stream().map(field -> "temp_" + field.name())
                        .collect(Collectors.joining(", ")) + (fields.isEmpty() ? "" : ", ") + "$unknownFields")
                .replace("$modelClassName", modelClassName)
                .replace("$schemaClassName", schemaClassName)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /** The wire type a field is written with: length-delimited for wrapper types, else its type's. */
    private static int wireType(final Field field) {
        return field.optionalValueType()
                ? Common.TYPE_LENGTH_DELIMITED
                : field.type().wireType();
    }

    private static String maxSize(final Field field) {
        return field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize";
    }

    /** A switch case for one tag. */
    private static String caseCode(final Field field, final int wireType, final String body) {
        return "case %d /* type=%d [%s] field=%d [%s] */ -> {%n%s}%n"
                .formatted(
                        Common.getTag(wireType, field.fieldNumber()),
                        wireType,
                        field.type(),
                        field.fieldNumber(),
                        field.name(),
                        body.indent(DEFAULT_INDENT));
    }

    /**
     * Fast path code testing for a field's tag, {@code if} or {@code while} it is next. Empty for tags longer than
     * two bytes, which only the general loop handles.
     */
    private static String fastPathCode(final Field field, final int wireType, final String keyword, final String body) {
        final int[] tagBytes = InlineFieldCodeGenerator.tagBytes(field.fieldNumber(), wireType);
        if (tagBytes.length > 2) {
            return "";
        }
        return "%s (input.at(%s)) {%n%s}%n"
                .formatted(
                        keyword,
                        Arrays.stream(tagBytes)
                                .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                                .collect(Collectors.joining(", ")),
                        body.indent(DEFAULT_INDENT));
    }

    /**
     * Repeated fields: messages, strings and bytes loop on their tag in the fast path; numbers and enums are written
     * packed, so the fast path reads the packed form, and the general loop accepts both forms.
     */
    private static void repeated(
            final SingleField field,
            final ContextualLookupHelper lookupHelper,
            final StringBuilder fastPath,
            final StringBuilder cases,
            final StringBuilder helpers) {
        final String temp = "temp_" + field.name();
        final String listType = field.type() == Field.FieldType.ENUM ? "List" : field.javaFieldType();
        helpers.append("""
                /** Reads one $name element for {@link #parseArray} and adds it to the list. */
                private static $listType addArray_$name(final ArrayCursor input, final $listType list,
                        final boolean strictMode, final boolean parseUnknownFields, final int maxDepth, final int maxSize)
                        throws ParseException, IOException {
                    if (list.size() >= $maxSize) {
                        throw new ParseException("$name size %d is greater than max $maxSize".formatted(list.size()));
                    }
                    final $type value;
                $read    return addToList(list, value);
                }

                """.replace("$listType", listType)
                .replace("$read", readInto(field, "value", false, lookupHelper).indent(DEFAULT_INDENT))
                .replace("$maxSize", maxSize(field))
                .replace("$name", field.name())
                .replace("$type", valueType(field)));
        final String addOne = "%s = addArray_%s(input, %s, strictMode, parseUnknownFields, maxDepth, maxSize);%n"
                .formatted(temp, field.name(), temp);
        final int wireType = field.type().wireType();
        if (wireType == Common.TYPE_LENGTH_DELIMITED) {
            fastPath.append(fastPathCode(field, wireType, "while", addOne));
            cases.append(caseCode(field, wireType, addOne));
            return;
        }
        // packed: one length-delimited run of values
        final String element = field.type() == Field.FieldType.ENUM
                ? """
                final int enumOrdinal = (int) input.varint();
                final Object value = $enum.fromProtobufOrdinal(enumOrdinal);
                list = addToList(list, value != $enum.UNRECOGNIZED ? value : Integer.valueOf(enumOrdinal));
                """.replace("$enum", Common.snakeToCamel(field.messageType(), true))
                : "list = addToList(list, %s);\n".formatted(valueExpression(field));
        helpers.append("""
                /** Reads a packed run of $name elements for {@link #parseArray} and adds them to the list. */
                private static $listType addPackedArray_$name(final ArrayCursor input, $listType list, final int maxSize)
                        throws ParseException, IOException {
                    final int length = input.length($maxSize, "$name");
                    final int limitBefore = input.pushLimit(length);
                    while (input.hasRemaining()) {
                $element    }
                    input.popLimit(limitBefore);
                    return list;
                }

                """.replace("$listType", listType)
                .replace("$maxSize", maxSize(field))
                .replace("$name", field.name())
                .replace("$element", element.indent(DEFAULT_INDENT * 2)));
        final String packed = "%s = addPackedArray_%s(input, %s, maxSize);%n".formatted(temp, field.name(), temp);
        fastPath.append(fastPathCode(field, Common.TYPE_LENGTH_DELIMITED, "if", packed));
        cases.append(caseCode(field, wireType, addOne));
        cases.append(caseCode(field, Common.TYPE_LENGTH_DELIMITED, packed));
    }

    /** A oneof alternative, which replaces whatever the oneof held before (last one wins). */
    private static String oneOfAlternative(
            final SingleField field, final ContextualLookupHelper lookupHelper, final StringBuilder helpers) {
        final OneOfField oneOf = field.parent();
        final String strictCheck = field.type() == Field.FieldType.MESSAGE && !field.optionalValueType()
                ? """
                if (strictMode && !temp_$oneOf.equals($unset)) {
                    throw new ParseException("A non-repeated $oneOf encountered more than once");
                }
                """.replace("$oneOf", oneOf.name()).replace("$unset", oneOf.javaDefault())
                : "";
        if (needsHelper(field)) {
            return strictCheck
                    + "temp_%s = new %s<>(%s.%s, %s);%n"
                            .formatted(
                                    oneOf.name(),
                                    oneOf.className(),
                                    oneOf.getEnumClassRef(),
                                    Common.camelToUpperSnake(field.name()),
                                    valueHelper(field, false, lookupHelper, helpers));
        }
        return strictCheck
                + """
                final $type value;
                $readtemp_$oneOf = new $className<>($enumRef.$kind, value);
                """.replace("$type", valueType(field))
                        .replace("$read", readInto(field, "value", false, lookupHelper))
                        .replace("$oneOf", oneOf.name())
                        .replace("$className", oneOf.className())
                        .replace("$enumRef", oneOf.getEnumClassRef())
                        .replace("$kind", Common.camelToUpperSnake(field.name()));
    }

    /**
     * A map entry: a nested message with the key as field 1 and the value as field 2, read without creating an entry
     * object, as in {@code parseImpl}. Like {@code parseImpl}, unknown fields inside an entry go to the enclosing
     * message's unknown fields.
     */
    private static String mapEntry(
            final MapField map, final String schemaClassName, final ContextualLookupHelper lookupHelper) {
        final SingleField key = (SingleField) map.keyField();
        final SingleField value = (SingleField) map.valueField();
        return """
                final int entryLength = input.length($maxSize, "$name");
                $keyType mapKey = $keyDefault;
                $valueType mapValue = $valueDefault;
                if (entryLength != 0) {
                    final int entryLimitBefore = input.pushLimit(entryLength);
                    while (input.hasRemaining()) {
                        final int entryTag = input.tag();
                        switch (entryTag) {
                            case $keyTag -> {
                $readKey            }
                            case $valueTag -> {
                $readValue            }
                            default -> $unknownFields = input.unknownField(
                                    entryTag, $schemaClassName.getField(entryTag >>> TAG_FIELD_OFFSET) != null,
                                    strictMode, parseUnknownFields, $unknownFields, maxSize);
                        }
                    }
                    input.popLimit(entryLimitBefore);
                    if (temp_$name.size() >= $maxSize) {
                        throw new ParseException("$name size %d is greater than max $maxSize".formatted(temp_$name.size()));
                    }
                    temp_$name = addToMap(temp_$name, mapKey, mapValue);
                }
                """.replace(
                        "$readKey", readInto(key, "mapKey", true, lookupHelper).indent(DEFAULT_INDENT * 4))
                .replace(
                        "$readValue",
                        readInto(value, "mapValue", true, lookupHelper).indent(DEFAULT_INDENT * 4))
                .replace("$keyTag", Integer.toString(Common.getTag(wireType(key), key.fieldNumber())))
                .replace("$valueTag", Integer.toString(Common.getTag(wireType(value), value.fieldNumber())))
                .replace("$keyType", key.javaFieldType())
                .replace("$keyDefault", key.javaDefault())
                .replace("$valueType", value.javaFieldType())
                .replace("$valueDefault", value.javaDefault())
                .replace("$maxSize", maxSize(map))
                .replace("$name", map.name())
                .replace("$schemaClassName", schemaClassName);
    }

    /**
     * Whether a field's value is read in its own method: nested messages and wrapper types, whose reads are the longest.
     * Calling one small method from both the fast path and the general loop keeps {@code parseArray} well under
     * HotSpot's 8000-byte limit for compiling a method at all, and the JIT still inlines the helper where it is hot.
     */
    private static boolean needsHelper(final SingleField field) {
        return field.type() == Field.FieldType.MESSAGE || field.optionalValueType();
    }

    /** Generate a method reading one value of a field, and return the code calling it. */
    private static String valueHelper(
            final SingleField field,
            final boolean recognizedOnly,
            final ContextualLookupHelper lookupHelper,
            final StringBuilder helpers) {
        helpers.append("""
                /** Reads one $name value for {@link #parseArray}. */
                private static $type readArray_$name(final ArrayCursor input, final boolean strictMode,
                        final boolean parseUnknownFields, final int maxDepth, final int maxSize)
                        throws ParseException, IOException {
                    final $type value;
                $read    return value;
                }

                """.replace("$type", valueType(field)).replace("$name", field.name()).replace(
                "$read", readInto(field, "value", recognizedOnly, lookupHelper).indent(DEFAULT_INDENT)));
        return "readArray_%s(input, strictMode, parseUnknownFields, maxDepth, maxSize)".formatted(field.name());
    }

    /** The declared type of a local holding one value of a field. */
    private static String valueType(final SingleField field) {
        if (field.type() == Field.FieldType.ENUM) {
            return "Object";
        }
        return field.optionalValueType() ? field.javaFieldType() : field.javaFieldTypeBase();
    }

    /** An expression reading one scalar, string or bytes value. */
    private static String valueExpression(final SingleField field) {
        final String maxSize = maxSize(field);
        return switch (field.type()) {
            case INT32, UINT32 -> "(int) input.varint()";
            case SINT32 -> "(int) input.varintZigZag()";
            case INT64, UINT64 -> "input.varint()";
            case SINT64 -> "input.varintZigZag()";
            case BOOL -> "input.bool()";
            case FIXED32, SFIXED32 -> "input.fixed32()";
            case FLOAT -> "Float.intBitsToFloat(input.fixed32())";
            case FIXED64, SFIXED64 -> "input.fixed64()";
            case DOUBLE -> "Double.longBitsToDouble(input.fixed64())";
            case STRING -> "input.string(" + maxSize + ")";
            case BYTES -> "input.bytes(" + maxSize + ")";
            default -> throw new IllegalArgumentException("Not a scalar, string or bytes field: " + field);
        };
    }

    /**
     * Code reading one value of a field, after its tag, into {@code target}.
     *
     * @param field the field
     * @param target the variable to assign
     * @param recognizedOnly for enums, store an unrecognized ordinal as an {@code Integer}, as {@code parseImpl} does for
     *     singular fields; otherwise store the {@code UNRECOGNIZED} constant, as it does for oneofs and repeated fields
     * @param lookupHelper the lookup helper
     * @return the code
     */
    private static String readInto(
            final SingleField field,
            final String target,
            final boolean recognizedOnly,
            final ContextualLookupHelper lookupHelper) {
        if (field.optionalValueType()) {
            return wrapper(field, target);
        }
        return switch (field.type()) {
            case ENUM ->
                """
                    final int enumOrdinal = (int) input.varint();
                    final var enumValue = $enum.fromProtobufOrdinal(enumOrdinal);
                    $target = $value;
                    """.replace(
                                "$value",
                                recognizedOnly
                                        ? "enumValue != $enum.UNRECOGNIZED ? enumValue : Integer.valueOf(enumOrdinal)"
                                        : "enumValue")
                        .replace("$enum", Common.snakeToCamel(field.messageType(), true))
                        .replace("$target", target);
            case MESSAGE -> {
                final String codec = lookupHelper
                                .getLookupHelper()
                                .hasArrayParser(field.messageTypeModelPackage(), field.completeClassName())
                        ? InlineFieldCodeGenerator.directCodecClass(field, lookupHelper)
                        : null;
                final String parse = codec != null
                        ? codec + ".parseArray(input, strictMode, parseUnknownFields, maxDepth - 1, maxSize)"
                        : "input.parseWith(%s.PROTOBUF, strictMode, parseUnknownFields, maxDepth - 1, maxSize)"
                                .formatted(field.messageType());
                yield """
                        final int messageLength = input.length($maxSize, "$name");
                        if (messageLength == 0) {
                            $target = $type.DEFAULT;
                        } else {
                            final int limitBefore = input.pushLimit(messageLength);
                            $target = $parse;
                            input.popLimit(limitBefore);
                        }
                        """.replace("$maxSize", maxSize(field))
                        .replace("$name", field.name())
                        .replace("$target", target)
                        .replace("$type", field.javaFieldTypeBase())
                        .replace("$parse", parse);
            }
            default -> "%s = %s;\n".formatted(target, valueExpression(field));
        };
    }

    /**
     * A {@code google.protobuf} wrapper type, read as {@code parseImpl} does: the wrapper's length, then its one field's
     * tag and value, or the default value for an empty wrapper.
     */
    private static String wrapper(final SingleField field, final String target) {
        final String maxSize = maxSize(field);
        final String[] readAndDefault =
                switch (field.messageType()) {
                    case "StringValue" -> new String[] {"input.string(" + maxSize + ")", "\"\""};
                    case "BytesValue" -> new String[] {"input.bytes(" + maxSize + ")", "Bytes.EMPTY"};
                    case "Int32Value", "UInt32Value" -> new String[] {"(int) input.varint()", "0"};
                    case "Int64Value", "UInt64Value" -> new String[] {"input.varint()", "0L"};
                    case "FloatValue" -> new String[] {"Float.intBitsToFloat(input.fixed32())", "0f"};
                    case "DoubleValue" -> new String[] {"Double.longBitsToDouble(input.fixed64())", "0d"};
                    case "BoolValue" -> new String[] {"input.bool()", "false"};
                    default -> throw new IllegalArgumentException("Not a wrapper type: " + field);
                };
        return """
                final int wrapperLength = (int) input.varint();
                if (wrapperLength > 0) {
                    final int limitBefore = input.pushLimit(input.checkedLength(wrapperLength));
                    // the wrapper's one field: its tag, then the value
                    input.tag();
                    $target = $read;
                    input.restoreLimit(limitBefore);
                } else {
                    $target = $default;
                }
                """.replace("$target", target)
                .replace("$read", readAndDefault[0])
                .replace("$default", readAndDefault[1]);
    }
}
