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
                state.append("private int %sOffset = -1;%nprivate int %sLength;%n".formatted(name, name));
                resets.append("%sOffset = -1;%n".formatted(name));
                record =
                        "%sLength = cursor.length(Codec.DEFAULT_MAX_SIZE, \"%s\");%n%sOffset = cursor.position();%ncursor.skip(%sLength);%n"
                                .formatted(name, field.name(), name, name);
            } else {
                // numbers are decoded once, while wrapping; absent fields keep their default, 0
                state.append("private long %sValue;%n".formatted(name));
                resets.append("%sValue = 0;%n".formatted(name));
                record = "%sValue = %s;%n"
                        .formatted(
                                name,
                                switch (field.type()) {
                                    case BOOL -> "cursor.bool() ? 1 : 0";
                                    case FIXED32, SFIXED32, FLOAT -> "cursor.fixed32()";
                                    case FIXED64, SFIXED64, DOUBLE -> "cursor.fixed64()";
                                    default -> "cursor.varint()";
                                });
            }
            final int[] tagBytes = Common.tagBytes(field.fieldNumber(), wireType);
            if (tagBytes.length <= 2) {
                fastPath.append("if (cursor.at(%s)) {%n%s}%n"
                        .formatted(
                                Arrays.stream(tagBytes)
                                        .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                                        .collect(Collectors.joining(", ")),
                                record.indent(DEFAULT_INDENT)));
            }
            cases.append("case %d /* [%s] field=%d [%s] */ -> {%n%s}%n"
                    .formatted(
                            Common.getTag(wireType, field.fieldNumber()),
                            field.type(),
                            field.fieldNumber(),
                            field.name(),
                            record.indent(DEFAULT_INDENT)));
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
                        in = cursor.array();
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

    /** The accessors for one field. */
    private static String accessor(final SingleField field, final String name) {
        final String doc = "%n/** @return {@code %s}, or its default when absent */%n".formatted(field.name());
        final String v = name + "Value";
        return switch (field.type()) {
            case INT32, UINT32, FIXED32, SFIXED32 ->
                doc + "public int %s() {%n    return (int) %s;%n}%n".formatted(name, v);
            case SINT32 ->
                doc + "public int %s() {%n    return (int) ((%s >>> 1) ^ -(%s & 1));%n}%n".formatted(name, v, v);
            case INT64, UINT64, FIXED64, SFIXED64 -> doc + "public long %s() {%n    return %s;%n}%n".formatted(name, v);
            case SINT64 -> doc + "public long %s() {%n    return (%s >>> 1) ^ -(%s & 1);%n}%n".formatted(name, v, v);
            case BOOL -> doc + "public boolean %s() {%n    return %s != 0;%n}%n".formatted(name, v);
            case FLOAT ->
                doc + "public float %s() {%n    return Float.intBitsToFloat((int) %s);%n}%n".formatted(name, v);
            case DOUBLE ->
                doc + "public double %s() {%n    return Double.longBitsToDouble(%s);%n}%n".formatted(name, v);
            case ENUM ->
                doc
                        + """
                    public int $nameProtoOrdinal() {
                        return (int) $nameValue;
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

                    /** @return the offset of {@code $field} in {@link #$nameArray()}, 0 when absent */
                    public int $nameOffset() {
                        return $nameOffset < 0 ? 0 : $nameOffset;
                    }

                    /** @return the length in bytes of {@code $field}, 0 when absent */
                    public int $nameLength() {
                        return $nameOffset < 0 ? 0 : $nameLength;
                    }

                    /** @return the wrapped array, which holds {@code $field}; do not modify it */
                    public byte[] $nameArray() {
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
                        return $nameOffset < 0 ? $default : $value;
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
                                        ? "new String(in, $nameOffset, $nameLength, java.nio.charset.StandardCharsets.UTF_8)"
                                        : "Bytes.wrap(in, $nameOffset, $nameLength)")
                        .replace("$name", name)
                        .replace("$field", field.name());
            case MESSAGE ->
                """

                    private $type.View $nameView;

                    /** @return true if {@code $field} is present */
                    public boolean has$Name() {
                        return $nameOffset >= 0;
                    }

                    /**
                     * @return a view of {@code $field}, or null when absent; the same child view is re-wrapped on each call
                     * @throws ParseException if the nested message is malformed
                     */
                    public $type.View $name() throws ParseException {
                        if ($nameOffset < 0) {
                            return null;
                        }
                        if ($nameView == null) {
                            $nameView = new $type.View();
                        }
                        return $nameView.wrap(in, $nameOffset, $nameLength, depth + 1);
                    }
                    """.replace("$type", field.javaFieldTypeBase())
                        .replace("$Name", field.nameCamelFirstUpper())
                        .replace("$name", name)
                        .replace("$field", field.name());
            default -> throw new IllegalArgumentException("Not supported by views yet: " + field);
        };
    }
}
