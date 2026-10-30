// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.PbjCompilerException;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageElementContext;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates the zero-copy view of a message with {@code pbj.views}: a reusable flyweight, nested in the model class as
 * {@code View}, that reads fields in place from encoded bytes. {@code wrap} indexes the message in one pass, with an
 * in-order fast path and the same validation of tags, wire types, lengths and bools as parsing: numbers are decoded
 * into primitive fields once, and strings, bytes and nested messages are recorded as offsets and lengths. Oneofs record
 * which alternative was seen last. Repeated fields record their first element and count, and are read with a reusable
 * cursor. Nested messages are read through child views created once and re-wrapped. Wrapping and reading numbers,
 * enums, nested views and the offsets and lengths of strings and bytes allocate nothing.
 */
final class ViewGenerator {

    private ViewGenerator() {}

    /** The code generated for the view, built field by field. */
    private record Parts(
            StringBuilder state,
            StringBuilder resets,
            StringBuilder fastPath,
            StringBuilder cases,
            StringBuilder accessors) {}

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
            final String denial = lookupHelper.getLookupHelper().viewDenial(msgDef);
            if (denial != null) {
                System.err.printf("PBJ: no view for message %s: %s%n", modelClass, denial);
            }
            return "";
        }
        for (final MessageElementContext element : msgDef.messageBody().messageElement()) {
            final String nested = element.messageDef() != null
                    ? element.messageDef().messageName().getText()
                    : element.enumDef() != null ? element.enumDef().enumName().getText() : null;
            if ("View".equals(nested)) {
                throw new PbjCompilerException(("Message %s has pbj.views but declares its own nested type named View, "
                                + "which would clash with the generated view class: rename it, or turn pbj.views off "
                                + "for this message with a <<<pbj.views = false>>> comment")
                        .formatted(modelClass));
            }
        }
        final Parts parts = new Parts(
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder());
        for (final Field field : fields) {
            if (field instanceof OneOfField oneOf) {
                oneOf(oneOf, parts);
            } else if (field.repeated()) {
                repeated((SingleField) field, parts);
            } else {
                singular((SingleField) field, null, parts);
            }
        }
        // spotless:off
        return """
                /**
                 * A reusable, zero-copy view of an encoded {@link $model}: reads its fields in place, without parsing it into
                 * a model object. Accessors return fields' defaults when they are absent. Nested messages are returned as
                 * child views, created once and re-wrapped, and repeated fields are read with reusable cursors, so after the
                 * first use wrapping and reading numbers, enums, nested views and the offsets and lengths of strings and
                 * bytes allocate nothing.
                 *
                 * <p>{@link #wrap} indexes this message and validates it as parsing does, with the same limits and strict
                 * mode: tags, wire types, lengths, bools, UTF-8 strings, unknown fields and, in strict mode, repeated
                 * singular messages. Nested messages, repeated message elements and wrapper type values are validated
                 * when they are read, so a malformed one throws from its accessor or cursor rather than from {@code wrap}.
                 *
                 * <p>A view is valid until it is wrapped again, and reads the wrapped bytes directly, so they must not
                 * change. Child views and cursors are reused: the view a nested accessor or a cursor's {@code value()}
                 * returns changes on the next call, so copy what you need, or call {@code toModel()}, before reading
                 * further. Not thread safe.
                 */
                public static final class View {
                    private final ArrayCursor cursor = new ArrayCursor(new byte[0], 0, 0);
                    private byte[] in;
                    private int start;
                    private int end;
                    private boolean strictMode;
                    private int maxDepth;
                    private int maxSize;
                $state
                    /**
                     * Wrap an encoded $model, with the defaults of {@link Codec#parse(Bytes)}.
                     *
                     * @param bytes the encoded message
                     * @return this view
                     * @throws ParseException if the message is malformed
                     */
                    public View wrap(@NonNull final Bytes bytes) throws ParseException {
                        return wrap(bytes, false, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE);
                    }

                    /**
                     * Wrap an encoded $model, validating it as {@link Codec#parse} does with the same arguments.
                     *
                     * @param bytes the encoded message
                     * @param strictMode when {@code true}, unknown fields and repeated singular messages are errors
                     * @param maxDepth the maximum depth of nested messages
                     * @param maxSize the maximum length of a field, nested message or unknown field
                     * @return this view
                     * @throws ParseException if the message is malformed
                     */
                    public View wrap(
                            @NonNull final Bytes bytes, final boolean strictMode, final int maxDepth, final int maxSize)
                            throws ParseException {
                        cursor.reset(bytes);
                        return index(strictMode, maxDepth, maxSize);
                    }

                    /**
                     * Wrap an encoded $model in {@code in[offset, offset + length)}, with the defaults of
                     * {@link Codec#parse(Bytes)}.
                     *
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @return this view
                     * @throws ParseException if the message is malformed
                     */
                    public View wrap(@NonNull final byte[] in, final int offset, final int length) throws ParseException {
                        return wrap(in, offset, length, false, Codec.DEFAULT_MAX_DEPTH, Codec.DEFAULT_MAX_SIZE);
                    }

                    /**
                     * Wrap an encoded $model in {@code in[offset, offset + length)}, validating it as {@link Codec#parse}
                     * does with the same arguments. Views also use it for the messages they contain.
                     *
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @param strictMode when {@code true}, unknown fields and repeated singular messages are errors
                     * @param maxDepth the maximum depth of nested messages below this one
                     * @param maxSize the maximum length of a field, nested message or unknown field
                     * @return this view
                     * @throws ParseException if the message is malformed or nested too deep
                     */
                    public View wrap(
                            @NonNull final byte[] in,
                            final int offset,
                            final int length,
                            final boolean strictMode,
                            final int maxDepth,
                            final int maxSize)
                            throws ParseException {
                        cursor.reset(in, offset, offset + length);
                        return index(strictMode, maxDepth, maxSize);
                    }

                    /**
                     * Whether this view wraps exactly {@code in[offset, offset + length)}, so a containing view can return
                     * it again without indexing it again.
                     *
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @return true if this view is valid and wraps those bytes
                     */
                    public boolean wraps(@NonNull final byte[] in, final int offset, final int length) {
                        return this.in == in && start == offset && end == offset + length;
                    }

                    private View index(final boolean strictMode, final int maxDepth, final int maxSize)
                            throws ParseException {
                        if (maxDepth < 0) {
                            throw new ParseException("Reached maximum allowed depth of nested messages");
                        }
                        // invalid until indexing succeeds
                        in = null;
                        this.strictMode = strictMode;
                        this.maxDepth = maxDepth;
                        this.maxSize = maxSize;
                        final int start = cursor.position();
                $resets
                        try {
                            // in-order fast path, then anything else in any order, as parsing does
                $fastPath
                            while (cursor.hasRemaining()) {
                                final int tagStart = cursor.position();
                                final int tag = cursor.tag();
                                switch (tag) {
                $cases
                                    default -> cursor.unknownField(
                                            tag, $schema.getField(tag >>> 3) != null,
                                            strictMode, false, null, maxSize);
                                }
                            }
                        } catch (final ParseException e) {
                            throw e;
                        } catch (final Exception e) {
                            throw new ParseException(e);
                        }
                        this.start = start;
                        end = cursor.limit();
                        in = cursor.array();
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
                .replace("$state", parts.state().toString().indent(DEFAULT_INDENT))
                .replace("$resets", parts.resets().toString().indent(DEFAULT_INDENT * 2))
                .replace("$fastPath", parts.fastPath().toString().indent(DEFAULT_INDENT * 3))
                .replace("$cases", parts.cases().toString().indent(DEFAULT_INDENT * 5))
                .replace("$accessors", parts.accessors().toString().indent(DEFAULT_INDENT))
                .replace("$model", modelClass)
                .replace("$schema", schemaClass)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /** {@code if (cursor.at(…))} or {@code while}, for a tag of at most two bytes; empty for longer tags. */
    private static String fastPath(final String keyword, final int fieldNumber, final int wireType, final String body) {
        final int[] tagBytes = Common.tagBytes(fieldNumber, wireType);
        if (tagBytes.length > 2) {
            return "";
        }
        return "%s (cursor.at(%s)) {%n%s}%n"
                .formatted(
                        keyword,
                        Arrays.stream(tagBytes)
                                .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                                .collect(Collectors.joining(", ")),
                        body.indent(DEFAULT_INDENT));
    }

    private static String caseCode(final SingleField field, final int wireType, final String body) {
        return "case %d /* [%s] field=%d [%s] */ -> {%n%s}%n"
                .formatted(
                        Common.getTag(wireType, field.fieldNumber()),
                        field.type(),
                        field.fieldNumber(),
                        field.name(),
                        body.indent(DEFAULT_INDENT));
    }

    /** An expression decoding one non-delimited value from {@code cursor} into a long. */
    private static String decode(final Field.FieldType type, final String cursor) {
        return switch (type) {
            case BOOL -> cursor + ".bool() ? 1 : 0";
            case FIXED32, SFIXED32, FLOAT -> cursor + ".fixed32()";
            case FIXED64, SFIXED64, DOUBLE -> cursor + ".fixed64()";
            default -> cursor + ".varint()";
        };
    }

    /** A statement reading one non-delimited value from {@code cursor}, to validate and skip it. */
    private static String skipValue(final Field.FieldType type, final String cursor) {
        return type == Field.FieldType.BOOL ? cursor + ".bool()" : decode(type, cursor);
    }

    /** A singular field, or a oneof alternative when {@code oneOfKind} names the oneof's kind field. */
    private static void singular(final SingleField field, final String oneOfKind, final Parts parts) {
        final String name = field.nameCamelFirstLower();
        final int wireType = field.optionalValueType()
                ? Common.TYPE_LENGTH_DELIMITED
                : field.type().wireType();
        String record;
        if (wireType == Common.TYPE_LENGTH_DELIMITED) {
            parts.state().append("private int %sOffset = -1;%nprivate int %sLength;%n".formatted(name, name));
            parts.resets().append("%sOffset = -1;%n".formatted(name));
            // strings are checked to be valid UTF-8 while wrapping, as parsing does
            record = "%sLength = cursor.%s(maxSize, \"%s\");%n%sOffset = cursor.position();%ncursor.skip(%sLength);%n"
                    .formatted(
                            name,
                            field.type() == Field.FieldType.STRING ? "stringLength" : "length",
                            field.name(),
                            name,
                            name);
        } else {
            // numbers are decoded once, while wrapping; absent fields keep their default, 0
            parts.state().append("private long %sValue;%n".formatted(name));
            parts.resets().append("%sValue = 0;%n".formatted(name));
            record = "%sValue = %s;%n".formatted(name, decode(field.type(), "cursor"));
        }
        if (field.type() == Field.FieldType.MESSAGE && !field.optionalValueType()) {
            // as in parsing, strict mode rejects a singular message, or a oneof with a message alternative, seen twice
            record = """
                    if (strictMode && %s) {
                        throw new ParseException("A non-repeated %s encountered more than once");
                    }
                    """.formatted(
                            oneOfKind == null ? name + "Offset >= 0" : oneOfKind + " != 0",
                            oneOfKind == null ? field.name() : field.parent().name())
                    + record;
            parts.resets().append("%sViewCurrent = false;%n".formatted(name));
        }
        if (oneOfKind != null) {
            record += "%s = %d;%n".formatted(oneOfKind, field.fieldNumber());
        } else {
            parts.fastPath().append(fastPath("if", field.fieldNumber(), wireType, record));
        }
        parts.cases().append(caseCode(field, wireType, record));
        final String guard = oneOfKind == null ? "" : "%s == %d".formatted(oneOfKind, field.fieldNumber());
        parts.accessors()
                .append(field.optionalValueType() ? wrapperAccessor(field, name, guard) : accessor(field, name, guard));
    }

    /** A oneof: each alternative is recorded like a singular field, and the oneof remembers the last one seen. */
    private static void oneOf(final OneOfField oneOf, final Parts parts) {
        final String kind = oneOf.nameCamelFirstLower() + "Kind";
        parts.state().append("private int %s;%n".formatted(kind));
        parts.resets().append("%s = 0;%n".formatted(kind));
        final StringBuilder kinds = new StringBuilder();
        for (final Field alternative : oneOf.fields()) {
            singular((SingleField) alternative, kind, parts);
            kinds.append("case %d -> %s.%s;%n"
                    .formatted(
                            alternative.fieldNumber(),
                            oneOf.getEnumClassRef(),
                            Common.camelToUpperSnake(alternative.name())));
        }
        parts.accessors().append("""

                /** @return which alternative of {@code $name} is set, as in the model */
                public $enum $kind() {
                    return switch ($kind) {
                $kinds        default -> $enum.UNSET;
                    };
                }
                """.replace("$kinds", kinds.toString().indent(DEFAULT_INDENT * 2))
                .replace("$enum", oneOf.getEnumClassRef())
                .replace("$kind", kind)
                .replace("$name", oneOf.name()));
    }

    /**
     * A repeated field: wrapping validates and counts the elements and records where the first one is; a reusable
     * cursor reads them in order, from unpacked elements and packed runs alike.
     */
    private static void repeated(final SingleField field, final Parts parts) {
        final String name = field.nameCamelFirstLower();
        final int wireType = field.type().wireType();
        final boolean delimited = wireType == Common.TYPE_LENGTH_DELIMITED;
        final int elementTag = Common.getTag(wireType, field.fieldNumber());
        final int packedTag = Common.getTag(Common.TYPE_LENGTH_DELIMITED, field.fieldNumber());
        parts.state()
                .append("private int %sFirst = -1;%nprivate int %sCount;%nprivate %sCursor %sCursor;%n"
                        .formatted(name, name, field.nameCamelFirstUpper(), name));
        parts.resets().append("%sFirst = -1;%n%sCount = 0;%n".formatted(name, name));
        final String element = delimited
                ? "cursor.skip(cursor.%s(maxSize, \"%s\"));%n"
                        .formatted(field.type() == Field.FieldType.STRING ? "stringLength" : "length", field.name())
                : "%s;%n".formatted(skipValue(field.type(), "cursor"));
        final String unpacked = """
                if ($nameFirst < 0) {
                    $nameFirst = $tagStart;
                }
                $nameCount++;
                $element""".replace("$element", element);
        final int tagLength = Common.tagBytes(field.fieldNumber(), wireType).length;
        parts.fastPath()
                .append(fastPath(
                        "while",
                        field.fieldNumber(),
                        wireType,
                        unpacked.replace("$tagStart", "cursor.position() - " + tagLength)
                                .replace("$name", name)));
        parts.cases()
                .append(caseCode(
                        field,
                        wireType,
                        unpacked.replace("$tagStart", "tagStart").replace("$name", name)));
        if (!delimited) {
            final String packed =
                    """
                    if ($nameFirst < 0) {
                        $nameFirst = $tagStart;
                    }
                    final int packedLength = cursor.length(maxSize, "$field");
                    final int packedEnd = cursor.position() + packedLength;
                    while (cursor.position() < packedEnd) {
                        $decode;
                        $nameCount++;
                    }
                    if (cursor.position() != packedEnd) {
                        throw new java.nio.BufferOverflowException();
                    }
                    """.replace("$decode", skipValue(field.type(), "cursor")).replace("$field", field.name());
            final int packedTagLength = Common.tagBytes(field.fieldNumber(), Common.TYPE_LENGTH_DELIMITED).length;
            parts.fastPath()
                    .append(fastPath(
                            "if",
                            field.fieldNumber(),
                            Common.TYPE_LENGTH_DELIMITED,
                            packed.replace("$tagStart", "cursor.position() - " + packedTagLength)
                                    .replace("$name", name)));
            parts.cases()
                    .append(caseCode(
                            field,
                            Common.TYPE_LENGTH_DELIMITED,
                            packed.replace("$tagStart", "tagStart").replace("$name", name)));
        }
        final String read =
                switch (field.type()) {
                    case MESSAGE -> """
                    length = c.length(maxSize, "$field");
                    offset = c.position();
                    c.skip(length);
                    if (child == null) {
                        child = new $elem.View();
                    }
                    child.wrap(in, offset, length, strictMode, maxDepth - 1, maxSize);
                    """.replace("$elem", field.javaFieldTypeBase());
                    case STRING, BYTES -> """
                    length = c.length(maxSize, "$field");
                    offset = c.position();
                    c.skip(length);
                    """;
                    default -> "value = %s;%n".formatted(decode(field.type(), "c"));
                };
        final String packedStep = delimited ? "" : """
                if (packedEnd >= 0) {
                    if (c.position() < packedEnd) {
                        value = $decode;
                        return true;
                    }
                    packedEnd = -1;
                }
                """.replace("$decode", decode(field.type(), "c"));
        final String packedTagCase = delimited ? "" : """
                if (tag == $packedTag) {
                    final int packedLength = c.length(maxSize, "$field");
                    packedEnd = c.position() + packedLength;
                    continue;
                }
                """.replace("$packedTag", Integer.toString(packedTag));
        // spotless:off
        parts.accessors().append("""

                /** @return the number of {@code $field} elements */
                public int $nameCount() {
                    return $nameCount;
                }

                /**
                 * @return a cursor over the {@code $field} elements, positioned before the first: call
                 *     {@code next()} to move to each one. The same cursor is reset and returned on each call.
                 */
                public $NameCursor $name() {
                    if ($nameCursor == null) {
                        $nameCursor = new $NameCursor();
                    }
                    return $nameCursor.reset();
                }

                /** A reusable cursor over the {@code $field} elements. */
                public final class $NameCursor {
                    private final ArrayCursor c = new ArrayCursor(new byte[0], 0, 0);
                    private int remaining;
                    private int packedEnd = -1;
                    private long value;
                    private int offset;
                    private int length;
                    private $childType child;

                    private $NameCursor reset() {
                        remaining = $nameCount;
                        packedEnd = -1;
                        if ($nameFirst >= 0) {
                            c.reset(in, $nameFirst, end);
                        }
                        return this;
                    }

                    /**
                     * Move to the next element.
                     *
                     * @return false when there are no more elements
                     * @throws ParseException if a nested message is malformed
                     */
                    public boolean next() throws ParseException {
                        if (remaining == 0) {
                            return false;
                        }
                        remaining--;
                        try {
                            while (true) {
                $packedStep
                                final int tag = c.tag();
                                if (tag == $elementTag) {
                $read
                                    return true;
                                }
                $packedTagCase
                                c.unknownField(tag, false, false, false, null, maxSize);
                            }
                        } catch (final ParseException e) {
                            throw e;
                        } catch (final Exception e) {
                            throw new ParseException(e);
                        }
                    }
                $elementAccessors
                }
                """
                .replace("$packedStep", packedStep.indent(DEFAULT_INDENT * 4))
                .replace("$packedTagCase", packedTagCase.indent(DEFAULT_INDENT * 4))
                .replace("$read", read.indent(DEFAULT_INDENT * 5))
                .replace("$elementAccessors", elementAccessors(field).indent(DEFAULT_INDENT))
                .replace("$childType", field.type() == Field.FieldType.MESSAGE ? field.javaFieldTypeBase() + ".View" : "Object")
                .replace("$elementTag", Integer.toString(elementTag))
                .replace("$Name", field.nameCamelFirstUpper())
                .replace("$name", name)
                .replace("$field", field.name()));
        // spotless:on
    }

    /** Accessors for the current element of a repeated field's cursor. */
    private static String elementAccessors(final SingleField field) {
        final String doc = "\n/** @return the current element */\n";
        return switch (field.type()) {
            case INT32, UINT32, FIXED32, SFIXED32, ENUM ->
                doc + "public int value() {%n    return (int) value;%n}%n".formatted();
            case SINT32 ->
                doc + "public int value() {%n    return (int) ((value >>> 1) ^ -(value & 1));%n}%n".formatted();
            case INT64, UINT64, FIXED64, SFIXED64 -> doc + "public long value() {%n    return value;%n}%n".formatted();
            case SINT64 -> doc + "public long value() {%n    return (value >>> 1) ^ -(value & 1);%n}%n".formatted();
            case BOOL -> doc + "public boolean value() {%n    return value != 0;%n}%n".formatted();
            case FLOAT ->
                doc + "public float value() {%n    return Float.intBitsToFloat((int) value);%n}%n".formatted();
            case DOUBLE -> doc + "public double value() {%n    return Double.longBitsToDouble(value);%n}%n".formatted();
            case STRING, BYTES ->
                """

                    /** @return the offset of the current element in the wrapped array */
                    public int valueOffset() {
                        return offset;
                    }

                    /** @return the length in bytes of the current element */
                    public int valueLength() {
                        return length;
                    }

                    /**
                     * @param other bytes to compare with
                     * @return true if the current element is exactly {@code other}, compared without allocating
                     */
                    public boolean valueEquals(@NonNull final byte[] other) {
                        return java.util.Arrays.equals(in, offset, offset + length, other, 0, other.length);
                    }

                    /** @return the current element, $returns */
                    public $type value() {
                        return $value;
                    }
                    """.replace(
                                "$returns",
                                field.type() == Field.FieldType.STRING
                                        ? "decoded as a new String"
                                        : "as a view of the wrapped bytes")
                        .replace("$type", field.type() == Field.FieldType.STRING ? "String" : "Bytes")
                        .replace(
                                "$value",
                                field.type() == Field.FieldType.STRING
                                        ? "new String(in, offset, length, java.nio.charset.StandardCharsets.UTF_8)"
                                        : "Bytes.wrap(in, offset, length)");
            case MESSAGE -> """

                    /** @return a view of the current element, re-wrapped by each {@code next()} */
                    public $type.View value() {
                        return child;
                    }
                    """.replace("$type", field.javaFieldTypeBase());
            default -> throw new IllegalArgumentException("Not supported by views: " + field);
        };
    }

    /** The accessors for one singular field or oneof alternative, which return defaults unless {@code guard} holds. */
    private static String accessor(final SingleField field, final String name, final String guard) {
        final String doc = "%n/** @return {@code %s}, or its default when absent */%n".formatted(field.name());
        final String v = name + "Value";
        final String when = guard.isEmpty() ? "" : guard + " ? ";
        final String otherwise = guard.isEmpty() ? "" : " : 0";
        return switch (field.type()) {
            case INT32, UINT32, FIXED32, SFIXED32 ->
                doc + "public int %s() {%n    return %s(int) %s%s;%n}%n".formatted(name, when, v, otherwise);
            case SINT32 ->
                doc
                        + "public int %s() {%n    return %s(int) ((%s >>> 1) ^ -(%s & 1))%s;%n}%n"
                                .formatted(name, when, v, v, otherwise);
            case INT64, UINT64, FIXED64, SFIXED64 ->
                doc + "public long %s() {%n    return %s%s%s;%n}%n".formatted(name, when, v, otherwise);
            case SINT64 ->
                doc
                        + "public long %s() {%n    return %s(%s >>> 1) ^ -(%s & 1)%s;%n}%n"
                                .formatted(name, when, v, v, otherwise);
            case BOOL ->
                doc
                        + "public boolean %s() {%n    return %s%s != 0;%n}%n"
                                .formatted(name, guard.isEmpty() ? "" : guard + " && ", v);
            case FLOAT ->
                doc
                        + "public float %s() {%n    return %sFloat.intBitsToFloat((int) %s)%s;%n}%n"
                                .formatted(name, when, v, otherwise);
            case DOUBLE ->
                doc
                        + "public double %s() {%n    return %sDouble.longBitsToDouble(%s)%s;%n}%n"
                                .formatted(name, when, v, otherwise);
            case ENUM ->
                doc
                        + """
                    public int $nameProtoOrdinal() {
                        return $when(int) $nameValue$otherwise;
                    }

                    /** @return {@code $field} as an enum constant, {@code UNRECOGNIZED} for an unknown ordinal */
                    public $type $name() {
                        return $type.fromProtobufOrdinal($nameProtoOrdinal());
                    }
                    """.replace("$when", when)
                                .replace("$otherwise", otherwise)
                                .replace("$type", Common.snakeToCamel(field.messageType(), true))
                                .replace("$name", name)
                                .replace("$field", field.name());
            case STRING, BYTES ->
                """

                    /** @return the offset of {@code $field} in {@link #$nameArray()}, 0 when absent */
                    public int $nameOffset() {
                        return $absent ? 0 : $nameOffset;
                    }

                    /** @return the length in bytes of {@code $field}, 0 when absent */
                    public int $nameLength() {
                        return $absent ? 0 : $nameLength;
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
                        return $absent ? $default : $value;
                    }
                    """.replace("$absent", absent(name, guard))
                        .replace(
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
                    private boolean $nameViewCurrent;

                    /** @return true if {@code $field} is present */
                    public boolean has$Name() {
                        return !($absent);
                    }

                    /**
                     * @return a view of {@code $field}, or null when absent; the same child view, wrapped again only when this
                     *     view has been
                     * @throws ParseException if the nested message is malformed
                     */
                    public $type.View $name() throws ParseException {
                        if ($absent) {
                            return null;
                        }
                        if ($nameView == null) {
                            $nameView = new $type.View();
                        } else if ($nameViewCurrent && $nameView.wraps(in, $nameOffset, $nameLength)) {
                            // already wrapped over this field since this view was wrapped: don't index it again
                            return $nameView;
                        }
                        $nameView.wrap(in, $nameOffset, $nameLength, strictMode, maxDepth - 1, maxSize);
                        $nameViewCurrent = true;
                        return $nameView;
                    }
                    """.replace("$absent", absent(name, guard))
                        .replace("$type", field.javaFieldTypeBase())
                        .replace("$Name", field.nameCamelFirstUpper())
                        .replace("$name", name)
                        .replace("$field", field.name());
            default -> throw new IllegalArgumentException("Not supported by views yet: " + field);
        };
    }

    /**
     * The accessors for a {@code google.protobuf} wrapper type: like the model, null when absent and the default value
     * when the wrapper is empty. The value is decoded with the view's own cursor, which is free once wrapping is done.
     */
    private static String wrapperAccessor(final SingleField field, final String name, final String guard) {
        final String[] typeReadDefault =
                switch (field.messageType()) {
                    case "StringValue" -> new String[] {"String", "cursor.string(maxSize)", "\"\""};
                    case "BytesValue" -> new String[] {"Bytes", "cursor.bytes(maxSize)", "Bytes.EMPTY"};
                    case "Int32Value", "UInt32Value" -> new String[] {"Integer", "(int) cursor.varint()", "0"};
                    case "Int64Value", "UInt64Value" -> new String[] {"Long", "cursor.varint()", "0L"};
                    case "FloatValue" -> new String[] {"Float", "Float.intBitsToFloat(cursor.fixed32())", "0f"};
                    case "DoubleValue" -> new String[] {"Double", "Double.longBitsToDouble(cursor.fixed64())", "0d"};
                    case "BoolValue" -> new String[] {"Boolean", "cursor.varint() != 0", "false"};
                    default -> throw new IllegalArgumentException("Not a wrapper type: " + field);
                };
        return """

                /** @return true if {@code $field} is present */
                public boolean has$Name() {
                    return !($absent);
                }

                /**
                 * @return {@code $field}: null when absent, as in the model
                 * @throws ParseException if the wrapper is malformed
                 */
                public $type $name() throws ParseException {
                    if ($absent) {
                        return null;
                    }
                    if ($nameLength == 0) {
                        return $default;
                    }
                    try {
                        cursor.reset(in, $nameOffset, $nameOffset + $nameLength);
                        // the wrapper's one field: its tag, then the value
                        cursor.tag();
                        return $read;
                    $catch
                }
                """.replace(
                        "$catch",
                        field.messageType().equals("StringValue")
                                        || field.messageType().equals("BytesValue")
                                ? "} catch (final ParseException e) {\n        throw e;\n    } catch (final Exception e) {\n        throw new ParseException(e);\n    }"
                                : "} catch (final RuntimeException e) {\n        throw new ParseException(e);\n    }")
                .replace("$absent", absent(name, guard))
                .replace("$type", typeReadDefault[0])
                .replace("$read", typeReadDefault[1])
                .replace("$default", typeReadDefault[2])
                .replace("$Name", field.nameCamelFirstUpper())
                .replace("$name", name)
                .replace("$field", field.name());
    }

    /** The condition for a delimited field being absent: no offset recorded, or another oneof alternative set. */
    private static String absent(final String name, final String guard) {
        return guard.isEmpty() ? name + "Offset < 0" : "!(" + guard + ") || " + name + "Offset < 0";
    }
}
