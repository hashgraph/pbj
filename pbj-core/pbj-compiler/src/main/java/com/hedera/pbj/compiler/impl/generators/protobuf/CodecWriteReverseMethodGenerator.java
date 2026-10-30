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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates the reverse writer for messages with {@code pbj.fast_write}: a static {@code writeReverseTo} that writes
 * the message back to front into a {@code ReverseBuffer}. Fields, list elements and unknown fields go in reverse order,
 * and each value is written before its length and tag, so a nested message's length is the number of bytes written
 * since it began and nothing is measured first. The result is byte-identical to the forward writers.
 *
 * <p>Messages with maps or {@code google.protobuf} wrapper types write themselves forward into the buffer instead,
 * which measures only that message.
 */
final class CodecWriteReverseMethodGenerator {

    private CodecWriteReverseMethodGenerator() {}

    /**
     * Generate the reverse writer, or nothing if the message does not have {@code pbj.fast_write}.
     *
     * @param msgDef the message
     * @param modelClassName the model class
     * @param fields the message's fields
     * @param lookupHelper the lookup helper
     * @return the methods' code, or an empty string
     */
    static String generateWriteReverseMethod(
            final MessageDefContext msgDef,
            final String modelClassName,
            final List<Field> fields,
            final ContextualLookupHelper lookupHelper) {
        if (!lookupHelper.getLookupHelper().hasReverseWriter(msgDef)) {
            return "";
        }
        final boolean supported = fields.stream()
                .flatMap(field -> field instanceof OneOfField oneOf ? oneOf.fields().stream() : Stream.of(field))
                .noneMatch(field -> field instanceof MapField || field.optionalValueType());
        final String body = supported ? fieldsInReverse(fields, lookupHelper) : """
                // maps and wrapper types are written forward, which measures only this message
                final int size = data.protobufSize();
                final int offset = out.reserve(size);
                writeTo(data, out.array(), offset, offset + size);
                """;
        // spotless:off
        return """
                @Override
                public void writeReverse(@NonNull final $modelClass data, @NonNull final ReverseBuffer out) {
                    writeReverseTo(data, out);
                }

                /**
                 * Writes a $modelClass back to front into a {@link ReverseBuffer}, with the same bytes as the forward
                 * writers. Generated codecs call this directly for nested messages.
                 *
                 * @param data The item to write. Must not be null.
                 * @param out The buffer to write into.
                 */
                public static void writeReverseTo(@NonNull final $modelClass data, @NonNull final ReverseBuffer out) {
                $body}
                """
                .replace("$body", body.indent(DEFAULT_INDENT))
                .replace("$modelClass", modelClassName)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /** All fields in descending field order, after the unknown fields, which come last in field order. */
    private static String fieldsInReverse(final List<Field> fields, final ContextualLookupHelper lookupHelper) {
        final List<Field> sorted = fields.stream()
                .flatMap(field -> field instanceof OneOfField oneOf ? oneOf.fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber).reversed())
                .toList();
        final StringBuilder sb = new StringBuilder("""
                // unknown fields are written after the known fields, so in reverse they come first
                final List<UnknownField> unknownFields = data.getUnknownFields();
                for (int i = unknownFields.size() - 1; i >= 0; i--) {
                    final UnknownField uf = unknownFields.get(i);
                    uf.bytes().writeTo(out.array(), out.reserve((int) uf.bytes().length()));
                    out.varint((uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal());
                }
                """);
        int i = 0;
        while (i < sorted.size()) {
            final Field field = sorted.get(i);
            final OneOfField oneOf = field.parent();
            if (oneOf == null) {
                sb.append(field((SingleField) field, lookupHelper));
                i++;
                continue;
            }
            // a run of adjacent alternatives of the same oneof becomes one switch
            final StringBuilder cases = new StringBuilder();
            while (i < sorted.size() && sorted.get(i).parent() == oneOf) {
                final SingleField alternative = (SingleField) sorted.get(i);
                cases.append("""
                        // [$number] - $name
                        case $kind -> {
                        $body}
                        """.replace("$number", Integer.toString(alternative.fieldNumber()))
                        .replace("$name", alternative.name())
                        .replace("$kind", Common.camelToUpperSnake(alternative.name()))
                        .replace("$body", field(alternative, lookupHelper).indent(DEFAULT_INDENT)));
                i++;
            }
            sb.append("""
                    switch (data.$oneOf().kind()) {
                    $cases    default -> {}
                    }
                    """.replace("$oneOf", oneOf.nameCamelFirstLower())
                    .replace("$cases", cases.toString().indent(DEFAULT_INDENT)));
        }
        return sb.toString();
    }

    /** Code writing a field's tag, after its value. */
    private static String tag(final int fieldNumber, final int wireType) {
        final int[] bytes = InlineFieldCodeGenerator.tagBytes(fieldNumber, wireType);
        final String comment = " // tag: field %d, wire type %d\n".formatted(fieldNumber, wireType);
        return switch (bytes.length) {
            case 1 -> "out.writeByte(0x%02X);".formatted(bytes[0]) + comment;
            case 2 -> "out.writeBytes(0x%02X, 0x%02X);".formatted(bytes[0], bytes[1]) + comment;
            default -> "out.varint(%dL);".formatted(((long) fieldNumber << 3) | wireType) + comment;
        };
    }

    /** Code writing one value of a non-message field held in {@code v}, before its tag. */
    private static String value(final Field.FieldType type, final String v, final boolean inOneOf) {
        return switch (type) {
                    case INT32, ENUM, INT64, UINT64 -> "out.varint(%s);".formatted(v);
                    case UINT32 -> "out.varint(Integer.toUnsignedLong(%s));".formatted(v);
                    case SINT32 -> "out.varint(Integer.toUnsignedLong((%s << 1) ^ (%s >> 31)));".formatted(v, v);
                    case SINT64 -> "out.varint((%s << 1) ^ (%s >> 63));".formatted(v, v);
                    case FIXED32, SFIXED32 -> "out.fixed32(%s);".formatted(v);
                    case FLOAT -> "out.fixed32(Float.floatToIntBits(%s));".formatted(v);
                    case FIXED64, SFIXED64 -> "out.fixed64(%s);".formatted(v);
                    case DOUBLE -> "out.fixed64(Double.doubleToLongBits(%s));".formatted(v);
                    case BOOL -> inOneOf ? "out.writeByte(%s ? 1 : 0);".formatted(v) : "out.writeByte(1);";
                    case STRING -> "out.string(%s);".formatted(v);
                    case BYTES -> "out.bytes(%s);".formatted(v);
                    default -> throw new IllegalArgumentException(type.toString());
                }
                + "\n";
    }

    /** Code writing a nested message held in {@code v}, then its length, before its tag. */
    private static String nested(final SingleField field, final String v, final ContextualLookupHelper lookupHelper) {
        final String write = lookupHelper
                        .getLookupHelper()
                        .hasReverseWriter(field.messageTypeModelPackage(), field.completeClassName())
                ? "%s.writeReverseTo(%s, out);"
                        .formatted(InlineFieldCodeGenerator.directCodecClass(field, lookupHelper), v)
                : "%s.%s.PROTOBUF.writeReverse(%s, out);"
                        .formatted(field.messageTypeModelPackage(), field.completeClassName(), v);
        return """
                final int mark = out.mark();
                $write
                out.lengthSince(mark);
                """.replace("$write", write);
    }

    /** Code writing one field, in reverse: value, then length if delimited, then tag. */
    private static String field(final SingleField field, final ContextualLookupHelper lookupHelper) {
        final boolean inOneOf = field.parent() != null;
        final String name = field.nameCamelFirstLower();
        final String local = "v_" + name;
        final String prefix = inOneOf ? "" : "// [%d] - %s%n".formatted(field.fieldNumber(), field.name());
        final int wireType = field.type().wireType();
        final String tag = tag(field.fieldNumber(), wireType);
        if (field.repeated()) {
            final String list = field.type() == Field.FieldType.ENUM
                    ? "data.%sProtoOrdinals()".formatted(name)
                    : "data.%s()".formatted(name);
            if (field.type() == Field.FieldType.MESSAGE) {
                return prefix
                        + """
                        final var $local = $list;
                        for (int i = $local.size() - 1; i >= 0; i--) {
                        $nested$tag}
                        """.replace("$local", local)
                                .replace("$list", list)
                                .replace(
                                        "$nested",
                                        nested(field, local + ".get(i)", lookupHelper)
                                                .indent(DEFAULT_INDENT))
                                .replace("$tag", tag.indent(DEFAULT_INDENT));
            }
            if (wireType == Common.TYPE_LENGTH_DELIMITED) {
                // repeated strings and bytes: one field per element
                return prefix
                        + """
                        final var $local = $list;
                        for (int i = $local.size() - 1; i >= 0; i--) {
                        $value$tag}
                        """.replace("$local", local)
                                .replace("$list", list)
                                .replace(
                                        "$value",
                                        value(field.type(), local + ".get(i)", false)
                                                .indent(DEFAULT_INDENT))
                                .replace("$tag", tag.indent(DEFAULT_INDENT));
            }
            // repeated numbers, bools and enums: packed
            return prefix
                    + """
                    final var $local = $list;
                    if (!$local.isEmpty()) {
                        final int mark = out.mark();
                        for (int i = $local.size() - 1; i >= 0; i--) {
                    $value    }
                        out.lengthSince(mark);
                    $tag}
                    """.replace("$local", local)
                            .replace("$list", list)
                            .replace(
                                    "$value",
                                    value(field.type(), local + ".get(i)", true).indent(DEFAULT_INDENT * 2))
                            .replace(
                                    "$tag",
                                    tag(field.fieldNumber(), Common.TYPE_LENGTH_DELIMITED)
                                            .indent(DEFAULT_INDENT));
        }
        final String javaType = !inOneOf ? "var" : field.type() == Field.FieldType.ENUM ? "int" : field.javaFieldType();
        final String oneOfValue =
                inOneOf ? "data.%s().as()".formatted(field.parent().nameCamelFirstLower()) : "";
        final String read = !inOneOf
                ? field.type() == Field.FieldType.ENUM
                        ? "data.%sProtoOrdinal()".formatted(name)
                        : "data.%s()".formatted(name)
                : field.type() == Field.FieldType.ENUM
                        ? "((%s) %s).protoOrdinal()".formatted(field.javaFieldType(), oneOfValue)
                        : oneOfValue;
        final String declaration = "final %s %s = %s;\n".formatted(javaType, local, read);
        if (field.type() == Field.FieldType.MESSAGE) {
            if (inOneOf) {
                // oneof members are written even when null, as an empty message
                return declaration
                        + """
                        if ($local == null) {
                            out.writeByte(0);
                        } else {
                        $nested}
                        $tag""".replace("$local", local)
                                .replace(
                                        "$nested",
                                        nested(field, local, lookupHelper).indent(DEFAULT_INDENT))
                                .replace("$tag", tag);
            }
            return prefix
                    + declaration
                    + """
                    if ($local != null) {
                    $nested$tag}
                    """.replace("$local", local)
                            .replace(
                                    "$nested",
                                    nested(field, local, lookupHelper).indent(DEFAULT_INDENT))
                            .replace("$tag", tag.indent(DEFAULT_INDENT));
        }
        final String value = value(field.type(), local, inOneOf);
        if (inOneOf) {
            // oneof members are always written, even with the default value
            return declaration + value + tag;
        }
        final String condition =
                switch (field.type()) {
                    case STRING -> "%s != null && !%s.isEmpty()".formatted(local, local);
                    case BYTES -> "%s.length() != 0".formatted(local);
                    case BOOL -> local;
                    default -> "%s != 0".formatted(local);
                };
        return prefix + declaration + "if (" + condition + ") {\n" + (value + tag).indent(DEFAULT_INDENT) + "}\n";
    }
}
