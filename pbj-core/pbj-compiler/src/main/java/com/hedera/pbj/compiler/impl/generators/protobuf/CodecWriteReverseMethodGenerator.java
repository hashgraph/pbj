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
 * <p>Maps are written as their entries in reverse of the sorted key order, each entry value first, and
 * {@code google.protobuf} wrapper types as the inner value, then its length and tag. Messages with maps of enums write
 * themselves forward into the buffer instead, which measures only that message.
 *
 * <p>A run of adjacent scalar fields makes room for its largest encoded size once, with {@code ReverseBuffer.ensure},
 * then writes every tag and value without checking for room.
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
                .noneMatch(field ->
                        field instanceof MapField map && map.valueField().type() == Field.FieldType.ENUM);
        final String body = supported ? fieldsInReverse(fields, lookupHelper) : """
                // maps of enums are written forward, which measures only this message
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
                    // reserve first: it can grow the buffer and replace its array
                    final int at = out.reserve((int) uf.bytes().length());
                    uf.bytes().writeTo(out.array(), at);
                    out.varint((uf.field() << TAG_FIELD_OFFSET) | uf.wireType().ordinal());
                }
                """);
        int i = 0;
        while (i < sorted.size()) {
            final Field field = sorted.get(i);
            final OneOfField oneOf = field.parent();
            if (field instanceof MapField map) {
                sb.append(map(map, lookupHelper));
                i++;
                continue;
            }
            if (scalar(field)) {
                // a run of adjacent scalar fields checks for room once, for the largest size they can take
                int maxSize = 0;
                final StringBuilder run = new StringBuilder();
                while (i < sorted.size() && scalar(sorted.get(i))) {
                    final SingleField single = (SingleField) sorted.get(i);
                    maxSize += InlineFieldCodeGenerator.tagSize(
                                    single.fieldNumber(), single.type().wireType())
                            + maxValueSize(single.type());
                    run.append(field(single, lookupHelper, true));
                    i++;
                }
                sb.append("out.ensure(%d);%n".formatted(maxSize)).append(run);
                continue;
            }
            if (oneOf == null) {
                sb.append(field((SingleField) field, lookupHelper, false));
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
                        .replace(
                                "$body", field(alternative, lookupHelper, false).indent(DEFAULT_INDENT)));
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

    /** Whether a field is a singular scalar outside a oneof, written in a run that checks for room once. */
    private static boolean scalar(final Field field) {
        return field instanceof SingleField
                && field.parent() == null
                && !field.repeated()
                && !field.optionalValueType()
                && switch (field.type()) {
                    case MESSAGE, STRING, BYTES, MAP, ONE_OF -> false;
                    default -> true;
                };
    }

    /** The largest encoded size of one scalar value. */
    private static int maxValueSize(final Field.FieldType type) {
        return switch (type) {
            case BOOL -> 1;
            case FIXED32, SFIXED32, FLOAT -> Integer.BYTES;
            case FIXED64, SFIXED64, DOUBLE -> Long.BYTES;
            case UINT32, SINT32 -> 5;
            default -> 10;
        };
    }

    /** Code writing a field's tag, after its value, without checking for room if {@code unchecked}. */
    private static String tag(final int fieldNumber, final int wireType, final boolean unchecked) {
        final int[] bytes = InlineFieldCodeGenerator.tagBytes(fieldNumber, wireType);
        final String comment = " // tag: field %d, wire type %d\n".formatted(fieldNumber, wireType);
        final String suffix = unchecked ? "Unchecked" : "";
        return switch (bytes.length) {
            case 1 -> "out.writeByte%s(0x%02X);".formatted(suffix, bytes[0]) + comment;
            case 2 -> "out.writeBytes%s(0x%02X, 0x%02X);".formatted(suffix, bytes[0], bytes[1]) + comment;
            default -> "out.varint%s(%dL);".formatted(suffix, ((long) fieldNumber << 3) | wireType) + comment;
        };
    }

    /** Code writing a field's tag, after its value. */
    private static String tag(final int fieldNumber, final int wireType) {
        return tag(fieldNumber, wireType, false);
    }

    /** Code writing one value of a non-message field held in {@code v}, before its tag. */
    private static String value(final Field.FieldType type, final String v, final boolean inOneOf) {
        return value(type, v, inOneOf, false);
    }

    /**
     * Code writing one value of a non-message field held in {@code v}, before its tag, without checking for room if
     * {@code unchecked} (scalars only).
     */
    private static String value(
            final Field.FieldType type, final String v, final boolean inOneOf, final boolean unchecked) {
        final String u = unchecked ? "Unchecked" : "";
        return switch (type) {
                    case INT32, ENUM, INT64, UINT64 -> "out.varint%s(%s);".formatted(u, v);
                    case UINT32 -> "out.varint%s(Integer.toUnsignedLong(%s));".formatted(u, v);
                    case SINT32 -> "out.varint%s(Integer.toUnsignedLong((%s << 1) ^ (%s >> 31)));".formatted(u, v, v);
                    case SINT64 -> "out.varint%s((%s << 1) ^ (%s >> 63));".formatted(u, v, v);
                    case FIXED32, SFIXED32 -> "out.fixed32%s(%s);".formatted(u, v);
                    case FLOAT -> "out.fixed32%s(Float.floatToIntBits(%s));".formatted(u, v);
                    case FIXED64, SFIXED64 -> "out.fixed64%s(%s);".formatted(u, v);
                    case DOUBLE -> "out.fixed64%s(Double.doubleToLongBits(%s));".formatted(u, v);
                    case BOOL ->
                        inOneOf ? "out.writeByte%s(%s ? 1 : 0);".formatted(u, v) : "out.writeByte%s(1);".formatted(u);
                    case STRING -> "out.string(%s);".formatted(v);
                    case BYTES -> "out.bytes(%s);".formatted(v);
                    default -> throw new IllegalArgumentException(type.toString());
                }
                + "\n";
    }

    /**
     * Code writing a map: its entries in reverse of the sorted key order, each as a nested message with the value
     * (field 2) before the key (field 1), as the forward writers do, which write both even when they are defaults.
     */
    private static String map(final MapField map, final ContextualLookupHelper lookupHelper) {
        final SingleField key = (SingleField) map.keyField();
        final SingleField value = (SingleField) map.valueField();
        final String writeValue = value.type() == Field.FieldType.MESSAGE
                ? nested(value, "mapValue", lookupHelper) + tag(2, Common.TYPE_LENGTH_DELIMITED)
                : value(value.type(), "mapValue", true) + tag(2, value.type().wireType());
        return """
                // [$number] - $name
                if (!data.$getter().isEmpty()) {
                    final PbjMap$generic map = (PbjMap$generic) data.$getter();
                    final List<$keyType> keys = map.getSortedKeys();
                    for (int i = keys.size() - 1; i >= 0; i--) {
                        final $keyType mapKey = keys.get(i);
                        final $valueType mapValue = map.get(mapKey);
                        final int entryMark = out.mark();
                $writeValue$writeKey        out.lengthSince(entryMark);
                        $tag    }
                }
                """.replace(
                        "$writeValue",
                        writeValue
                                .replace("final int mark", "final int valueMark")
                                .replace("(mark)", "(valueMark)")
                                .indent(DEFAULT_INDENT * 2))
                .replace(
                        "$writeKey",
                        (value(key.type(), "mapKey", true) + tag(1, key.type().wireType())).indent(DEFAULT_INDENT * 2))
                .replace("$tag", tag(map.fieldNumber(), Common.TYPE_LENGTH_DELIMITED))
                .replace("$generic", map.javaGenericType())
                .replace("$keyType", key.type().boxedType)
                .replace(
                        "$valueType",
                        map.javaGenericType()
                                .substring(
                                        map.javaGenericType().indexOf(", ") + 2,
                                        map.javaGenericType().length() - 1))
                .replace("$getter", map.nameCamelFirstLower())
                .replace("$number", Integer.toString(map.fieldNumber()))
                .replace("$name", map.name());
    }

    /**
     * Code writing a {@code google.protobuf} wrapper type held in {@code v}, if not null: the wrapped value as field 1,
     * unless it is the default, then the length and the tag, as the forward writers do.
     */
    private static String wrapper(final SingleField field, final String v, final String tag) {
        final Field.FieldType inner =
                switch (field.messageType()) {
                    case "StringValue" -> Field.FieldType.STRING;
                    case "BytesValue" -> Field.FieldType.BYTES;
                    case "Int32Value" -> Field.FieldType.INT32;
                    case "UInt32Value" -> Field.FieldType.UINT32;
                    case "Int64Value" -> Field.FieldType.INT64;
                    case "UInt64Value" -> Field.FieldType.UINT64;
                    case "FloatValue" -> Field.FieldType.FLOAT;
                    case "DoubleValue" -> Field.FieldType.DOUBLE;
                    case "BoolValue" -> Field.FieldType.BOOL;
                    default -> throw new IllegalArgumentException("Not a wrapper type: " + field);
                };
        final String notDefault =
                switch (inner) {
                    case STRING -> "!%s.isEmpty()".formatted(v);
                    case BYTES -> "%s.length() != 0".formatted(v);
                    case BOOL -> v;
                    default -> "%s != 0".formatted(v);
                };
        return """
                if ($local != null) {
                    final int mark = out.mark();
                    if ($notDefault) {
                $value$innerTag    }
                    out.lengthSince(mark);
                $tag}
                """.replace("$notDefault", notDefault)
                .replace("$value", value(inner, v, false).indent(DEFAULT_INDENT * 2))
                .replace("$innerTag", tag(1, inner.wireType()).indent(DEFAULT_INDENT * 2))
                .replace("$tag", tag.indent(DEFAULT_INDENT))
                .replace("$local", v);
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

    /**
     * Code writing one field, in reverse: value, then length if delimited, then tag. Scalars are written without
     * checking for room if {@code unchecked}, after the caller has made room.
     */
    private static String field(
            final SingleField field, final ContextualLookupHelper lookupHelper, final boolean unchecked) {
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
        if (field.optionalValueType()) {
            return (inOneOf ? "" : prefix) + declaration + wrapper(field, local, tag);
        }
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
        final String value = value(field.type(), local, inOneOf, unchecked);
        if (unchecked) {
            return prefix + declaration + "if (" + condition(field, local) + ") {\n"
                    + (value + tag(field.fieldNumber(), wireType, true)).indent(DEFAULT_INDENT) + "}\n";
        }
        if (inOneOf) {
            // oneof members are always written, even with the default value
            return declaration + value + tag;
        }
        return prefix + declaration + "if (" + condition(field, local) + ") {\n" + (value + tag).indent(DEFAULT_INDENT)
                + "}\n";
    }

    /** The condition for a field outside a oneof not holding its default, so it is written. */
    private static String condition(final SingleField field, final String local) {
        return switch (field.type()) {
            case STRING -> "%s != null && !%s.isEmpty()".formatted(local, local);
            case BYTES -> "%s.length() != 0".formatted(local);
            case BOOL -> local;
            default -> "%s != 0".formatted(local);
        };
    }
}
