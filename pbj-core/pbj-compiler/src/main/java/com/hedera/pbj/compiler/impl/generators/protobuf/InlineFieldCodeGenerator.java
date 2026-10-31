// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileAndPackageNamesConfig;
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
 * Generates straight-line code for every field of a message, for one of three targets: the byte-array writer, the
 * stream writer, or the model's {@code protobufSize()}. The code for all three has the same shape:
 * <ul>
 *     <li>fields in field-number order, with each run of adjacent oneof alternatives as one switch on the kind;</li>
 *     <li>constant tags (bytes for the writers, a literal size for {@code protobufSize()});</li>
 *     <li>the proto3 default check inline, for fields outside a oneof (oneof members are always written);</li>
 *     <li>nested messages generated in the same build reached directly: {@code XxxProtoCodec.writeTo(…)} and the
 *     nested model's {@code protobufSize()}, which are static or monomorphic calls the JIT can inline.</li>
 * </ul>
 * Maps, {@code google.protobuf} wrapper types, repeated scalars, strings and bytes, and messages from dependency jars
 * use the target's existing helper code, given as {@code fallback}.
 */
public final class InlineFieldCodeGenerator {

    /** What the generated code does with each field. */
    enum Target {
        /** Write to {@code output} at {@code offset}, bounded by {@code end}, in a codec's {@code writeTo}. */
        BYTE_ARRAY,
        /** Write to the {@code WritableSequentialData out}, in a codec's {@code writeTo}. */
        STREAM,
        /** Add the field's encoded size to {@code _size}, in the model's {@code protobufSize()}. */
        SIZE
    }

    /** Names the generated methods use for their own parameters and locals. */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "data",
            "output",
            "out",
            "startOffset",
            "end",
            "offset",
            "size",
            "length",
            "i",
            "item",
            "uf",
            "tag",
            "_size");

    private final Target target;
    /** The prefix for calling the model's getters: {@code "data."} in a codec, empty in the model itself. */
    private final String receiver;

    private final ContextualLookupHelper lookupHelper;
    /** The target's existing code for a field, without a oneof guard, for field kinds that are not inlined. */
    private final Function<Field, String> fallback;
    /** The Java names of all fields of the message being generated, so renamed locals don't clash with them. */
    private Set<String> fieldNames = Set.of();

    /** The model class, for the writers' helper methods; null for {@link Target#SIZE}, which has none. */
    private final String modelClass;
    /** Helper methods the generated code calls, to add to the class after the method. */
    private final StringBuilder helpers = new StringBuilder();

    /**
     * @param target what the generated code does with each field
     * @param receiver the prefix for calling the model's getters, {@code "data."} or empty
     * @param lookupHelper to find which nested messages are generated in this build
     * @param fallback the target's existing code for a field, without a oneof guard
     */
    InlineFieldCodeGenerator(
            final Target target,
            final String receiver,
            final ContextualLookupHelper lookupHelper,
            final Function<Field, String> fallback) {
        this(target, receiver, lookupHelper, fallback, null);
    }

    /**
     * @param target what the generated code does with each field
     * @param receiver the prefix for calling the model's getters, {@code "data."} or empty
     * @param lookupHelper to find which nested messages are generated in this build
     * @param fallback the target's existing code for a field, without a oneof guard
     * @param modelClass the model class, for the writers' helper methods
     */
    InlineFieldCodeGenerator(
            final Target target,
            final String receiver,
            final ContextualLookupHelper lookupHelper,
            final Function<Field, String> fallback,
            final String modelClass) {
        this.modelClass = modelClass;
        this.target = target;
        this.receiver = receiver;
        this.lookupHelper = lookupHelper;
        this.fallback = fallback;
    }

    /**
     * Generate the code for all fields, in field-number order.
     *
     * @param fields the message's fields
     * @return the code
     */
    String fieldsInOrder(final List<Field> fields) {
        fieldNames = fields.stream()
                .flatMap(field -> field instanceof OneOfField oneOf ? oneOf.fields().stream() : Stream.of(field))
                .map(Field::nameCamelFirstLower)
                .collect(Collectors.toSet());
        final List<Field> sorted = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        // In messages with more than MAX_INLINE_FIELDS fields, such as HAPI's TransactionBody and StateValue, each
        // message
        // alternative of a oneof is written in its own small method, to keep the writer well under HotSpot's 8000-byte
        // limit for compiling a method at all. Only one alternative is written per message, so the extra call costs
        // little, and the JIT still inlines it where it is hot.
        final boolean helpersForMessages =
                target != Target.SIZE && modelClass != null && Common.fieldCount(fields) > Common.MAX_INLINE_FIELDS;
        final StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sorted.size()) {
            final Field field = sorted.get(i);
            final OneOfField oneOf = field.parent();
            if (oneOf == null) {
                sb.append(field(field));
                i++;
                continue;
            }
            // a run of adjacent alternatives of the same oneof becomes one switch
            final StringBuilder cases = new StringBuilder();
            while (i < sorted.size() && sorted.get(i).parent() == oneOf) {
                final Field alternative = sorted.get(i);
                final String body = helpersForMessages && alternative.type() == Field.FieldType.MESSAGE
                        ? helper(alternative, field(alternative))
                        : field(alternative);
                cases.append("""
                        // [$number] - $name
                        case $kind -> {
                        $body}
                        """.replace("$number", Integer.toString(alternative.fieldNumber()))
                        .replace("$name", alternative.name())
                        .replace("$kind", Common.camelToUpperSnake(alternative.name()))
                        .replace("$body", body.indent(DEFAULT_INDENT)));
                i++;
            }
            sb.append("""
                    switch ($receiver$kind) {
                    $cases    default -> {}
                    }
                    """.replace("$receiver", receiver)
                    .replace(
                            "$kind",
                            oneOf.unboxed() ? oneOf.kindName() + "()" : oneOf.nameCamelFirstLower() + "().kind()")
                    .replace("$cases", cases.toString().indent(DEFAULT_INDENT)));
        }
        return sb.toString();
    }

    /** @return the helper methods the generated code calls, to add to the class after the method */
    String helpers() {
        return helpers.toString();
    }

    /** Put the code writing one oneof alternative in a helper method, and return the code calling it. */
    private String helper(final Field alternative, final String body) {
        // spotless:off
        if (target == Target.BYTE_ARRAY) {
            helpers.append("""

                    /** Writes the oneof alternative {@code $field}, for {@link #writeTo(%s, byte[], int, int)}. */
                    private static int writeArray_$field(
                            final $model data, final byte[] output, int offset, final int end) {
                    $body    return offset;
                    }
                    """.formatted(modelClass)
                    .replace("$model", modelClass)
                    .replace("$field", alternative.name())
                    .replace("$body", body.indent(DEFAULT_INDENT)));
            return "offset = writeArray_%s(data, output, offset, end);%n".formatted(alternative.name());
        }
        helpers.append("""

                /** Writes the oneof alternative {@code $field}, for {@link #writeTo(%s, WritableSequentialData)}. */
                private static void writeStream_$field(final $model data, final WritableSequentialData out)
                        throws IOException {
                $body}
                """.formatted(modelClass)
                .replace("$model", modelClass)
                .replace("$field", alternative.name())
                .replace("$body", body.indent(DEFAULT_INDENT)));
        return "writeStream_%s(data, out);%n".formatted(alternative.name());
        // spotless:on
    }

    /** The number of bytes in a field's tag. */
    static int tagSize(final int fieldNumber, final int wireType) {
        final long tag = ((long) fieldNumber << 3) | wireType;
        return (Long.SIZE - Long.numberOfLeadingZeros(tag) + 6) / 7;
    }

    /** The bytes of a field's tag varint. */
    static int[] tagBytes(final int fieldNumber, final int wireType) {
        long tag = ((long) fieldNumber << 3) | wireType;
        final int[] bytes = new int[tagSize(fieldNumber, wireType)];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (tag >>> 7) == 0 ? (int) tag : (int) ((tag & 0x7F) | 0x80);
            tag >>>= 7;
        }
        return bytes;
    }

    private static String byteLiteral(final int b, final boolean alwaysCast) {
        return (alwaysCast || b > Byte.MAX_VALUE ? "(byte) " : "") + "0x%02X".formatted(b);
    }

    /** Java code writing a field's tag, with a comment; empty for {@link Target#SIZE}. */
    private String tagCode(final int fieldNumber, final int wireType) {
        final String comment = " // tag: field %d, wire type %d\n".formatted(fieldNumber, wireType);
        final int[] bytes = tagBytes(fieldNumber, wireType);
        return switch (target) {
            case BYTE_ARRAY -> {
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bytes.length; i++) {
                    sb.append("output[offset++] = ")
                            .append(byteLiteral(bytes[i], false))
                            .append(';');
                    sb.append(i == 0 ? comment : "\n");
                }
                yield sb.toString();
            }
            case STREAM -> {
                final String method =
                        switch (bytes.length) {
                            case 1 -> "writeByte";
                            case 2, 3, 4 -> "writeByte" + bytes.length;
                            default -> null;
                        };
                if (method == null) {
                    // five-byte tags, for field numbers from 2^25: one byte at a time
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < bytes.length; i++) {
                        sb.append("out.writeByte(")
                                .append(byteLiteral(bytes[i], true))
                                .append(");");
                        sb.append(i == 0 ? comment : "\n");
                    }
                    yield sb.toString();
                }
                yield "out." + method + "("
                        + Arrays.stream(bytes)
                                .mapToObj(b -> byteLiteral(b, true))
                                .collect(Collectors.joining(", "))
                        + ");" + comment;
            }
            case SIZE -> "";
        };
    }

    /** The Java name of a local holding a field's value, avoiding the names the generated method uses itself. */
    private String localName(final Field field) {
        final String name = field.nameCamelFirstLower();
        if (!RESERVED_NAMES.contains(name)) {
            return name;
        }
        // a new name, clashing with neither the method's own names nor another field's local
        String local = name + "Value";
        for (int i = 2; RESERVED_NAMES.contains(local) || fieldNames.contains(local); i++) {
            local = name + "Value" + i;
        }
        return local;
    }

    /**
     * The fully qualified codec class of a message field's type, or null when that message is not generated in this
     * build and so must be reached through its {@code Codec} instance.
     */
    public static String directCodecClass(final SingleField field, final ContextualLookupHelper lookupHelper) {
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

    /** Java code reading a field outside a oneof through the model's getters. */
    private String read(final Field field) {
        return field.type() == Field.FieldType.ENUM
                ? "%s%sProtoOrdinal()".formatted(receiver, field.nameCamelFirstLower())
                : "%s%s()".formatted(receiver, field.nameCamelFirstLower());
    }

    /** Code writing (or sizing) one nested message held in {@code local}, which is not null. */
    private String nestedMessage(final String local, final String codec, final int tagSize) {
        final String code =
                switch (target) {
                    case BYTE_ARRAY -> """
                    final int size = $local.protobufSize();
                    offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, size);
                    offset += $codec.writeTo($local, output, offset, offset + size);
                    """;
                    case STREAM -> """
                    final int size = $local.protobufSize();
                    out.writeVarInt(size, false);
                    $codec.writeTo($local, out);
                    """;
                    case SIZE -> """
                    final int size = $local.protobufSize();
                    _size += $tagSize + sizeOfVarInt32(size) + size;
                    """;
                };
        return code.replace("$local", local).replace("$codec", codec).replace("$tagSize", Integer.toString(tagSize));
    }

    /**
     * Generate inline code for one field: its tag, its value, and for fields outside a oneof the proto3 default check.
     * Field kinds that are not inlined use {@link #fallback}.
     */
    private String field(final Field field) {
        final boolean inOneOf = field.parent() != null;
        final String prefix = inOneOf ? "" : "// [%d] - %s%n".formatted(field.fieldNumber(), field.name());
        if (!(field instanceof SingleField single)
                || field.optionalValueType()
                || (field.repeated() && field.type() != Field.FieldType.MESSAGE)) {
            // maps, google.protobuf wrapper types and repeated scalars, strings and bytes keep their helpers
            return fallback.apply(field);
        }
        final String local = localName(field);
        // outside a oneof, var avoids naming the type; inside, the type is needed to read the OneOf's value
        final String javaType = !inOneOf ? "var" : field.type() == Field.FieldType.ENUM ? "int" : field.javaFieldType();
        // an unboxed oneof's value is read with the alternative's typed getter, which allocates nothing
        final String oneOfValue = !inOneOf
                ? ""
                : field.parent().unboxed()
                        ? "%s%s()".formatted(receiver, field.nameCamelFirstLower())
                        : "%s%s().as()".formatted(receiver, field.parent().nameCamelFirstLower());
        final String read = !inOneOf
                ? read(field)
                : field.type() == Field.FieldType.ENUM
                        ? (field.parent().unboxed()
                                ? "%s.protoOrdinal()".formatted(oneOfValue)
                                : "((%s) %s).protoOrdinal()".formatted(field.javaFieldType(), oneOfValue))
                        : oneOfValue;
        final int tagSize = tagSize(field.fieldNumber(), field.type().wireType());
        final String tag = tagCode(field.fieldNumber(), field.type().wireType());

        if (field.type() == Field.FieldType.MESSAGE) {
            final String codec = directCodecClass(single, lookupHelper);
            if (codec == null) {
                return fallback.apply(field);
            }
            if (field.repeated()) {
                return prefix
                        + """
                        final var $local = $read;
                        for (int i = 0; i < $local.size(); i++) {
                            final var item = $local.get(i);
                        $tag$nested}
                        """.replace("$local", local)
                                .replace("$read", read)
                                .replace("$tag", tag.indent(DEFAULT_INDENT))
                                .replace(
                                        "$nested",
                                        nestedMessage("item", codec, tagSize).indent(DEFAULT_INDENT));
            }
            final String nested = nestedMessage(local, codec, tagSize);
            if (inOneOf) {
                // oneof members are written even when null, as an empty message
                final String empty =
                        switch (target) {
                            case BYTE_ARRAY -> "output[offset++] = 0;\n";
                            case STREAM -> "out.writeByte((byte) 0);\n";
                            case SIZE -> "_size += %d;\n".formatted(tagSize + 1);
                        };
                return """
                        final $type $local = $read;
                        $tagif ($local == null) {
                        $empty} else {
                        $nested}
                        """.replace("$type", javaType)
                        .replace("$local", local)
                        .replace("$read", read)
                        .replace("$tag", tag)
                        .replace("$empty", empty.indent(DEFAULT_INDENT))
                        .replace("$nested", nested.indent(DEFAULT_INDENT));
            }
            return prefix
                    + """
                    final var $local = $read;
                    if ($local != null) {
                    $tag$nested}
                    """.replace("$local", local)
                            .replace("$read", read)
                            .replace("$tag", tag.indent(DEFAULT_INDENT))
                            .replace("$nested", nested.indent(DEFAULT_INDENT));
        }

        final String condition;
        switch (field.type()) {
            case STRING -> condition = "%s != null && !%s.isEmpty()".formatted(local, local);
            case BYTES -> condition = "%s.length() != 0".formatted(local);
            case BOOL -> condition = local;
            case INT32,
                    UINT32,
                    SINT32,
                    INT64,
                    UINT64,
                    SINT64,
                    ENUM,
                    FIXED32,
                    SFIXED32,
                    FLOAT,
                    FIXED64,
                    SFIXED64,
                    DOUBLE -> condition = "%s != 0".formatted(local);
            default -> {
                return fallback.apply(field);
            }
        }
        final String value = valueCode(field.type(), local, inOneOf, tagSize);
        final String declaration = "final %s %s = %s;\n".formatted(javaType, local, read);
        if (inOneOf) {
            // oneof members are always written, even with the default value
            return declaration + tag + value;
        }
        return prefix + declaration + "if (" + condition + ") {\n" + (tag + value).indent(DEFAULT_INDENT) + "}\n";
    }

    /** Code writing (or sizing) a scalar, enum, string or bytes value held in {@code local}, after its tag. */
    private String valueCode(final Field.FieldType type, final String local, final boolean inOneOf, final int tagSize) {
        final String code =
                switch (target) {
                    case BYTE_ARRAY ->
                        switch (type) {
                            case STRING -> "offset += ProtoArrayWriterTools.writeStringNoTag(output, offset, end, $v);";
                            case BYTES -> "offset += ProtoArrayWriterTools.writeBytesNoTag(output, offset, end, $v);";
                            case BOOL -> inOneOf ? "output[offset++] = $v ? (byte) 1 : 0;" : "output[offset++] = 1;";
                            case INT32, INT64, UINT64, ENUM ->
                                "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, $v);";
                            case UINT32 ->
                                "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, Integer.toUnsignedLong($v));";
                            case SINT32 ->
                                "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, "
                                        + "Integer.toUnsignedLong(($v << 1) ^ ($v >> 31)));";
                            case SINT64 ->
                                "offset += ProtoArrayWriterTools.writeVarInt(output, offset, end, ($v << 1) ^ ($v >> 63));";
                            case FIXED32, SFIXED32 ->
                                "offset += ProtoArrayWriterTools.writeFixed32NoTag(output, offset, $v);";
                            case FLOAT ->
                                "offset += ProtoArrayWriterTools.writeFixed32NoTag(output, offset, Float.floatToIntBits($v));";
                            case FIXED64, SFIXED64 ->
                                "offset += ProtoArrayWriterTools.writeFixed64NoTag(output, offset, $v);";
                            case DOUBLE ->
                                "offset += ProtoArrayWriterTools.writeFixed64NoTag(output, offset, "
                                        + "Double.doubleToLongBits($v));";
                            default -> throw new IllegalArgumentException(type.toString());
                        };
                    case STREAM ->
                        switch (type) {
                            case STRING -> "ProtoWriterTools.writeStringNoTag(out, $v);";
                            case BYTES -> "ProtoWriterTools.writeBytesNoTag(out, $v);";
                            case BOOL -> inOneOf ? "out.writeByte($v ? (byte) 1 : 0);" : "out.writeByte((byte) 1);";
                            case INT32, ENUM -> "out.writeVarInt($v, false);";
                            case UINT32 -> "out.writeVarLong(Integer.toUnsignedLong($v), false);";
                            case SINT32 -> "out.writeVarInt($v, true);";
                            case INT64, UINT64 -> "out.writeVarLong($v, false);";
                            case SINT64 -> "out.writeVarLong($v, true);";
                            case FIXED32, SFIXED32 -> "out.writeInt($v, ByteOrder.LITTLE_ENDIAN);";
                            case FLOAT -> "out.writeFloat($v, ByteOrder.LITTLE_ENDIAN);";
                            case FIXED64, SFIXED64 -> "out.writeLong($v, ByteOrder.LITTLE_ENDIAN);";
                            case DOUBLE -> "out.writeDouble($v, ByteOrder.LITTLE_ENDIAN);";
                            default -> throw new IllegalArgumentException(type.toString());
                        };
                    case SIZE ->
                        switch (type) {
                            case STRING ->
                                "final int length = sizeOfStringNoTag($v);\n"
                                        + "_size += $tagSize + sizeOfVarInt32(length) + length;";
                            case BYTES ->
                                "final int length = (int) $v.length();\n"
                                        + "_size += $tagSize + sizeOfVarInt32(length) + length;";
                            case BOOL -> "_size += $tagSize + 1;";
                            case INT32, ENUM -> "_size += $tagSize + sizeOfVarInt32($v);";
                            case UINT32 -> "_size += $tagSize + sizeOfUnsignedVarInt32($v);";
                            case SINT32 ->
                                "_size += $tagSize + sizeOfUnsignedVarInt64(((long) $v << 1) ^ ((long) $v >> 63));";
                            case INT64, UINT64 -> "_size += $tagSize + sizeOfUnsignedVarInt64($v);";
                            case SINT64 -> "_size += $tagSize + sizeOfUnsignedVarInt64(($v << 1) ^ ($v >> 63));";
                            case FIXED32, SFIXED32, FLOAT -> "_size += $tagSize + 4;";
                            case FIXED64, SFIXED64, DOUBLE -> "_size += $tagSize + 8;";
                            default -> throw new IllegalArgumentException(type.toString());
                        };
                };
        return code.replace("$v", local).replace("$tagSize", Integer.toString(tagSize)) + "\n";
    }
}
