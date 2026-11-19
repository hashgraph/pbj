// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the zero-copy view of a message with {@code pbj.views}: a reusable flyweight, nested in the model class as
 * {@code View}, that reads fields in place from encoded bytes. {@code wrap} indexes the message in one pass, with an
 * in-order fast path and the same validation of tags, wire types, lengths and bools as parsing: numbers are decoded
 * into primitive fields once, and strings, bytes and nested messages are recorded as offsets and lengths. Nested
 * messages are read through child views the parent creates once and re-wraps. Wrapping and reading numbers, enums,
 * nested views and the offsets and lengths of strings and bytes allocate nothing.
 */
final class ViewGenerator {

    private ViewGenerator() {}

    /**
     * Generate the view class for a message, or nothing if it has no view.
     *
     * @param msgDef the message
     * @param fields the message's fields
     * @param modelClass the model's simple class name
     * @param schemaClass the schema class
     * @param lookupHelper the lookup helper
     * @return the view class, indented for the model's body, or an empty string
     */
    static String generateView(
            final MessageDefContext msgDef,
            final List<Field> fields,
            final String modelClass,
            final String schemaClass,
            final ContextualLookupHelper lookupHelper) {
        if (!lookupHelper.getLookupHelper().hasView(msgDef)) {
            return "";
        }
        final StringBuilder state = new StringBuilder();
        final StringBuilder resets = new StringBuilder();
        final StringBuilder fastPath = new StringBuilder();
        final StringBuilder cases = new StringBuilder();
        final StringBuilder accessors = new StringBuilder();
        for (final Field f : fields) {
            final SingleField field = (SingleField) f;
            final String name = field.nameCamelFirstLower();
            final int wireType = field.type().wireType();
            final String record;
            if (wireType == Common.TYPE_LENGTH_DELIMITED) {
                state.append("""
                        private int $offset = -1;
                        private int $length;
                        """.replace("$offset", offsetVar(name)).replace("$length", lengthVar(name)));
                resets.append("$offset = -1;\n".replace("$offset", offsetVar(name)));
                // strings are checked to be valid UTF-8 while wrapping, as parsing does
                record = """
                        $length = cursor.$read(Codec.DEFAULT_MAX_SIZE, "$field");
                        $offset = cursor.position();
                        cursor.skip($length);
                        """.replace("$read", field.type() == Field.FieldType.STRING ? "stringLength" : "length")
                        .replace("$field", field.name())
                        .replace("$offset", offsetVar(name))
                        .replace("$length", lengthVar(name));
            } else {
                // numbers are decoded once, while wrapping; absent fields keep their default, 0
                state.append("private long $value;\n".replace("$value", valueVar(name)));
                resets.append("$value = 0;\n".replace("$value", valueVar(name)));
                record = "$value = $read;\n"
                        .replace("$value", valueVar(name))
                        .replace(
                                "$read",
                                switch (field.type()) {
                                    case BOOL -> "cursor.bool() ? 1 : 0";
                                    case FIXED32, SFIXED32, FLOAT -> "cursor.fixed32()";
                                    case FIXED64, SFIXED64, DOUBLE -> "cursor.fixed64()";
                                    default -> "cursor.varint()";
                                });
            }
            final int[] tagBytes = Common.tagBytes(field.fieldNumber(), wireType);
            if (tagBytes.length <= 2) {
                fastPath.append("""
                        if (cursor.at($tag)) {
                        $record}
                        """.replace(
                                "$tag",
                                Arrays.stream(tagBytes)
                                        .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                                        .collect(Collectors.joining(", ")))
                        .replace("$record", record.indent(DEFAULT_INDENT)));
            }
            cases.append("""
                    case $tag /* [$type] field=$number [$field] */ -> {
                    $record}
                    """.replace("$tag", Integer.toString(Common.getTag(wireType, field.fieldNumber())))
                    .replace("$type", field.type().toString())
                    .replace("$number", Integer.toString(field.fieldNumber()))
                    .replace("$field", field.name())
                    .replace("$record", record.indent(DEFAULT_INDENT)));
            accessors.append(accessor(field, name));
        }
        // spotless:off
        return """
                /**
                 * A reusable, zero-copy view of an encoded {@link $model}: reads its fields in place, without parsing it into
                 * a model object. {@link #wrap} indexes the encoded message, validating it as parsing does; accessors return
                 * fields' defaults when they are absent. Nested messages are returned as child views, created once and
                 * re-wrapped, so after the first use wrapping and reading numbers, enums, nested views and the offsets and
                 * lengths of strings and bytes allocate nothing. A view is valid until it is wrapped again, and reads the
                 * wrapped bytes directly, so they must not change. Not thread safe.
                 */
                public static final class View {
                    private final ArrayCursor cursor = new ArrayCursor(new byte[0], 0, 0);
                    private byte[] in;
                    private int start;
                    private int end;
                    private int depth;
                $state
                    /**
                     * Wrap an encoded $model.
                     *
                     * @param bytes the encoded message
                     * @return this view
                     * @throws ParseException if the message is malformed
                     */
                    public View wrap(@NonNull final Bytes bytes) throws ParseException {
                        cursor.reset(bytes);
                        return index(0);
                    }

                    /**
                     * Wrap an encoded $model in {@code in[offset, offset + length)}.
                     *
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @return this view
                     * @throws ParseException if the message is malformed
                     */
                    public View wrap(@NonNull final byte[] in, final int offset, final int length) throws ParseException {
                        return wrap(in, offset, length, 0);
                    }

                    /**
                     * Wrap an encoded $model nested {@code depth} messages deep, for the views that contain it.
                     *
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @param depth how deep the message is nested
                     * @return this view
                     * @throws ParseException if the message is malformed or nested too deep
                     */
                    public View wrap(@NonNull final byte[] in, final int offset, final int length, final int depth)
                            throws ParseException {
                        cursor.reset(in, offset, offset + length);
                        return index(depth);
                    }

                    private View index(final int depth) throws ParseException {
                        if (depth > Codec.DEFAULT_MAX_DEPTH) {
                            throw new ParseException("Reached maximum allowed depth of nested messages");
                        }
                        this.depth = depth;
                        in = cursor.unsafeArray();
                        start = cursor.position();
                        end = cursor.limit();
                $resets
                        try {
                            // in-order fast path, then anything else in any order, as parsing does
                $fastPath
                            while (cursor.hasRemaining()) {
                                final int tag = cursor.tag();
                                switch (tag) {
                $cases
                                    default -> cursor.unknownField(
                                            tag, $schema.getField(tag >>> 3) != null,
                                            false, false, null, Codec.DEFAULT_MAX_SIZE);
                                }
                            }
                        } catch (final ParseException e) {
                            throw e;
                        } catch (final Exception e) {
                            throw new ParseException(e);
                        }
                        return this;
                    }

                    /**
                     * Parse the wrapped message into a model object.
                     *
                     * @return the model object
                     * @throws ParseException if the message is malformed
                     */
                    public $model toModel() throws ParseException {
                        return $model.PROTOBUF.parse(Bytes.wrap(in, start, end - start));
                    }
                $accessors
                }
                """
                .replace("$state", state.toString().indent(DEFAULT_INDENT))
                .replace("$resets", resets.toString().indent(DEFAULT_INDENT * 2))
                .replace("$fastPath", fastPath.toString().indent(DEFAULT_INDENT * 3))
                .replace("$cases", cases.toString().indent(DEFAULT_INDENT * 5))
                .replace("$accessors", accessors.toString().indent(DEFAULT_INDENT))
                .replace("$model", modelClass)
                .replace("$schema", schemaClass)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * The name of the view's private field holding the offset of a string, bytes or message field. The names of the
     * view's own state separate the field's name and their role with an underscore, which the camel case Java names of
     * fields never have, so they cannot clash with another field's names.
     */
    private static String offsetVar(final String name) {
        return name + "_offset";
    }

    /** The name of the view's private field holding the length of a string, bytes or message field. */
    private static String lengthVar(final String name) {
        return name + "_length";
    }

    /** The name of the view's private field holding a number, bool or enum field's decoded value. */
    private static String valueVar(final String name) {
        return name + "_value";
    }

    /** The accessors for one field. */
    private static String accessor(final SingleField field, final String name) {
        final String doc =
                "\n/** @return {@code $field}, or its default when absent */\n".replace("$field", field.name());
        final String number =
                switch (field.type()) {
                    case INT32, UINT32, FIXED32, SFIXED32 -> "public int $name() {\n    return (int) $v;\n}\n";
                    case SINT32 -> "public int $name() {\n    return (int) (($v >>> 1) ^ -($v & 1));\n}\n";
                    case INT64, UINT64, FIXED64, SFIXED64 -> "public long $name() {\n    return $v;\n}\n";
                    case SINT64 -> "public long $name() {\n    return ($v >>> 1) ^ -($v & 1);\n}\n";
                    case BOOL -> "public boolean $name() {\n    return $v != 0;\n}\n";
                    case FLOAT -> "public float $name() {\n    return Float.intBitsToFloat((int) $v);\n}\n";
                    case DOUBLE -> "public double $name() {\n    return Double.longBitsToDouble($v);\n}\n";
                    default -> null;
                };
        if (number != null) {
            return doc + number.replace("$name", name).replace("$v", valueVar(name));
        }
        return switch (field.type()) {
            case ENUM ->
                doc
                        + """
                    public int $nameProtoOrdinal() {
                        return (int) $name_value;
                    }

                    /** @return {@code $field} as an enum constant, {@code UNRECOGNIZED} for an unknown ordinal */
                    public $type $name() {
                        return $type.fromProtobufOrdinal($nameProtoOrdinal());
                    }
                    """.replace("$type", Common.snakeToCamel(field.messageType(), true))
                                .replace("$name", name)
                                .replace("$field", field.name());
            case STRING, BYTES ->
                """

                    /** @return the offset of {@code $field} in {@link #$nameUnsafeArray()}, 0 when absent */
                    public int $nameOffset() {
                        return $name_offset < 0 ? 0 : $name_offset;
                    }

                    /** @return the length in bytes of {@code $field}, 0 when absent */
                    public int $nameLength() {
                        return $name_offset < 0 ? 0 : $name_length;
                    }

                    /**
                     * The wrapped array, which holds {@code $field}, without copying it. This is unsafe: it may be the
                     * backing array of an immutable {@code Bytes}, so it must never be modified.
                     *
                     * @return the wrapped array
                     */
                    public byte[] $nameUnsafeArray() {
                        return in;
                    }

                    /**
                     * @param other bytes to compare with
                     * @return true if {@code $field} is exactly {@code other}, compared without allocating
                     */
                    public boolean $nameEquals(@NonNull final byte[] other) {
                        return java.util.Arrays.equals(
                                in, $nameOffset(), $nameOffset() + $nameLength(), other, 0, other.length);
                    }

                    /** @return {@code $field}, $returns */
                    public $type $name() {
                        return $name_offset < 0 ? $default : $value;
                    }
                    """.replace(
                                "$returns",
                                field.type() == Field.FieldType.STRING
                                        ? "decoded as a new String"
                                        : "as a view of the wrapped bytes")
                        .replace("$type", field.type() == Field.FieldType.STRING ? "String" : "Bytes")
                        .replace("$default", field.type() == Field.FieldType.STRING ? "\"\"" : "Bytes.EMPTY")
                        .replace(
                                "$value",
                                field.type() == Field.FieldType.STRING
                                        ? "new String(in, $name_offset, $name_length, java.nio.charset.StandardCharsets.UTF_8)"
                                        : "Bytes.wrap(in, $name_offset, $name_length)")
                        .replace("$name", name)
                        .replace("$field", field.name());
            case MESSAGE ->
                """

                    private $type.View $name_view;

                    /** @return true if {@code $field} is present */
                    public boolean has$Name() {
                        return $name_offset >= 0;
                    }

                    /**
                     * @return a view of {@code $field}, or null when absent; the same child view is re-wrapped on each call
                     * @throws ParseException if the nested message is malformed
                     */
                    public $type.View $name() throws ParseException {
                        if ($name_offset < 0) {
                            return null;
                        }
                        if ($name_view == null) {
                            $name_view = new $type.View();
                        }
                        return $name_view.wrap(in, $name_offset, $name_length, depth + 1);
                    }
                    """.replace("$type", field.javaFieldTypeBase())
                        .replace("$Name", field.nameCamelFirstUpper())
                        .replace("$name", name)
                        .replace("$field", field.name());
            default -> throw new IllegalArgumentException("Not supported by views yet: " + field);
        };
    }
}
