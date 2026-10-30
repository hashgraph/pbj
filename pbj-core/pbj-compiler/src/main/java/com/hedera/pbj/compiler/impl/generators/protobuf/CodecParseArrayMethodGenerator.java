// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the byte-array parser for messages with {@code pbj.fast_parse}: a static {@code parseArray} over an
 * {@code ArrayCursor}, which {@code Codec.parse} uses for array-backed input. It gives the same results and errors as
 * the stream parser {@code parseImpl}.
 *
 * <p>Writers emit fields in field-number order, so the parser first tests for each field's tag in that order, one byte
 * compare per field (the in-order fast path), then runs a general loop that handles anything else exactly as
 * {@code parseImpl} does: fields out of order or repeated, unknown fields, and strict mode.
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
        final List<SingleField> sorted = fields.stream()
                .map(SingleField.class::cast)
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        final StringBuilder fastPath = new StringBuilder();
        final StringBuilder cases = new StringBuilder();
        for (final SingleField field : sorted) {
            final int wireType = field.type().wireType();
            final int tag = Common.getTag(wireType, field.fieldNumber());
            final String read = readCode(field, lookupHelper);
            final int[] tagBytes = InlineFieldCodeGenerator.tagBytes(field.fieldNumber(), wireType);
            // Tags of three or more bytes (field numbers above 2047) are left out of the fast path. That is safe: the
            // general loop that follows reads any field, in any order.
            if (tagBytes.length <= 2) {
                fastPath.append("if (input.at(%s)) {%n%s}%n"
                        .formatted(
                                java.util.Arrays.stream(tagBytes)
                                        .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                                        .collect(Collectors.joining(", ")),
                                read.indent(DEFAULT_INDENT)));
            }
            final String strictCheck =
                    field.type() == Field.FieldType.MESSAGE ? """
                    if (strictMode && temp_$name != null) {
                        throw new ParseException("A non-repeated $name encountered more than once");
                    }
                    """.replace("$name", field.name()) : "";
            cases.append("case %d /* type=%d [%s] field=%d [%s] */ -> {%n%s}%n"
                    .formatted(
                            tag,
                            wireType,
                            field.type(),
                            field.fieldNumber(),
                            field.name(),
                            (strictCheck + read).indent(DEFAULT_INDENT)));
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
                        // Everything else, as parseImpl does: fields out of order or repeated, and unknown fields
                        while (input.hasRemaining()) {
                            final int tag = input.tag();
                            switch (tag) {
                $cases
                                default -> $unknownFields = input.unknownField(
                                        tag, $schemaClassName.getField(tag >>> TAG_FIELD_OFFSET) != null,
                                        strictMode, parseUnknownFields, $unknownFields, maxSize);
                            }
                        }
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
                """
                .replace("$fieldDefs", sorted.stream()
                        .map(field -> "%s temp_%s = %s;".formatted(
                                field.type() == Field.FieldType.ENUM ? "Object" : field.javaFieldType(),
                                field.name(),
                                field.javaDefault()))
                        .collect(Collectors.joining("\n"))
                        .indent(DEFAULT_INDENT * 2))
                .replace("$fastPath", fastPath.toString().indent(DEFAULT_INDENT * 2))
                .replace("$cases", cases.toString().indent(DEFAULT_INDENT * 4))
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

    /** Code reading one field's value from {@code input} into {@code temp_<name>}, after its tag. */
    private static String readCode(final SingleField field, final ContextualLookupHelper lookupHelper) {
        final String maxSize = field.maxSize() >= 0 ? String.valueOf(field.maxSize()) : "maxSize";
        final String temp = "temp_" + field.name();
        return switch (field.type()) {
            case INT32, UINT32 -> temp + " = (int) input.varint();\n";
            case SINT32 -> temp + " = (int) input.varintZigZag();\n";
            case INT64, UINT64 -> temp + " = input.varint();\n";
            case SINT64 -> temp + " = input.varintZigZag();\n";
            case BOOL -> temp + " = input.bool();\n";
            case FIXED32, SFIXED32 -> temp + " = input.fixed32();\n";
            case FLOAT -> temp + " = Float.intBitsToFloat(input.fixed32());\n";
            case FIXED64, SFIXED64 -> temp + " = input.fixed64();\n";
            case DOUBLE -> temp + " = Double.longBitsToDouble(input.fixed64());\n";
            case STRING -> temp + " = input.string(" + maxSize + ");\n";
            case BYTES -> temp + " = input.bytes(" + maxSize + ");\n";
            case ENUM ->
                """
                    final int enumOrdinal = (int) input.varint();
                    final var value = $enum.fromProtobufOrdinal(enumOrdinal);
                    $temp = value != $enum.UNRECOGNIZED ? value : Integer.valueOf(enumOrdinal);
                    """.replace("$enum", Common.snakeToCamel(field.messageType(), true))
                        .replace("$temp", temp);
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
                            $temp = $type.DEFAULT;
                        } else {
                            final int limitBefore = input.pushLimit(messageLength);
                            $temp = $parse;
                            input.popLimit(limitBefore);
                        }
                        """.replace("$maxSize", maxSize)
                        .replace("$name", field.name())
                        .replace("$temp", temp)
                        .replace("$type", field.javaFieldTypeBase())
                        .replace("$parse", parse);
            }
            default -> throw new IllegalArgumentException("Not supported by the array parser: " + field);
        };
    }
}
