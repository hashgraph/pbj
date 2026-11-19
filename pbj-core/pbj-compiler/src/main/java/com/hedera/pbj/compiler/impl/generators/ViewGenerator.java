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

    /**
     * The name of the view's private field holding the offset of a delimited field. The view's own state separates the
     * field's name and the state's role with an underscore, which camel case Java field names never have, so the names
     * cannot clash with another field's.
     */
    private static String offsetVar(final String name) {
        return name + "_offset";
    }

    /** The name of the view's private field holding the length of a delimited field. */
    private static String lengthVar(final String name) {
        return name + "_length";
    }

    /** The name of the view's private field holding a number, bool or enum field's decoded value. */
    private static String valueVar(final String name) {
        return name + "_value";
    }

    /** The code generated for the view, built field by field. */
    private record Parts(
            StringBuilder state,
            StringBuilder resets,
            StringBuilder fastPath,
            StringBuilder cases,
            StringBuilder accessors,
            StringBuilder validations,
            StringBuilder helpers,
            boolean useHelpers) {

        /**
         * The code indexing one field: inline, or in a large message a call to a method holding it.
         *
         * @param method the method's name
         * @param code the code, which may use {@code $tagStart}, the position of the field's tag
         * @param tagStart the expression for {@code $tagStart} where the code is used
         * @param declare true the first time the code is used, to generate the method
         * @return the code to use
         */
        String index(final String method, final String code, final String tagStart, final boolean declare) {
            if (!useHelpers) {
                return code.replace("$tagStart", tagStart);
            }
            final boolean usesTagStart = code.contains("$tagStart");
            if (declare) {
                helpers.append("%nprivate void %s(%s) throws java.io.IOException, ParseException {%n%s}%n"
                        .formatted(
                                method,
                                usesTagStart ? "final int tagStart" : "",
                                code.replace("$tagStart", "tagStart").indent(DEFAULT_INDENT)));
            }
            return "%s(%s);%n".formatted(method, usesTagStart ? tagStart : "");
        }
    }

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
        // In messages with more than MAX_INLINE_FIELDS fields, each field is indexed in its own small method, called
        // from
        // both the fast path and the general loop, to keep index() well under HotSpot's 8000-byte limit for compiling a
        // method at all. The JIT still inlines them where they are hot.
        final Parts parts = new Parts(
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                Common.fieldCount(fields) > Common.MAX_INLINE_FIELDS);
        for (final Field field : fields) {
            if (field instanceof OneOfField oneOf) {
                oneOf(oneOf, parts);
            } else if (field.repeated()) {
                repeated((SingleField) field, parts);
            } else {
                singular((SingleField) field, null, null, parts);
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
                 * singular messages. In strict mode it also validates every nested message, repeated message element and
                 * wrapper type value, at any depth, so it fails fast on anything strict parsing rejects. Otherwise they
                 * are only validated when they are read, and a malformed one throws from its accessor or cursor: a
                 * non-strict {@code wrap} does not validate nested messages that are never read, so use strict mode, or
                 * parse, to validate untrusted input.
                 *
                 * <p>A view is valid until it is wrapped again, and reads the wrapped bytes directly, so they must not
                 * change while it is used. Bytes it returns are views of the wrapped {@link Bytes}, which is immutable,
                 * or copies when it wraps a plain array. Child views and cursors are reused: the view a nested accessor
                 * or a cursor's {@code value()} returns changes on the next call, so copy what you need, or call
                 * {@code toModel()}, before reading further. Not thread safe.
                 */
                public static final class View implements com.hedera.pbj.runtime.MessageView {
                    private final ArrayCursor cursor = new ArrayCursor();
                    /** Counts wraps: a child view wrapped in the current one is returned again without indexing it. */
                    private long generation;
                    private byte[] in;
                    /** The Bytes holding {@code in}, if this view wraps one, so bytes fields need not be copied */
                    private Bytes source;
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
                     * does with the same arguments. Bytes fields are returned as copies, since the array could change.
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
                        return wrap(null, in, offset, length, strictMode, maxDepth, maxSize);
                    }

                    /**
                     * Wrap an encoded $model in {@code in[offset, offset + length)}, part of {@code source} if it is not
                     * null, validating it as {@link Codec#parse} does with the same arguments. Views use it for the
                     * messages they contain, so bytes fields are views of {@code source} rather than copies.
                     *
                     * @param source the {@link Bytes} holding the message, or null if it is part of a plain array
                     * @param in the bytes
                     * @param offset where the message starts
                     * @param length the message's length
                     * @param strictMode when {@code true}, unknown fields and repeated singular messages are errors
                     * @param maxDepth the maximum depth of nested messages below this one
                     * @param maxSize the maximum length of a field, nested message or unknown field
                     * @return this view
                     * @throws ParseException if the message is malformed or nested too deep
                     * @throws IllegalArgumentException if {@code source} does not hold the message
                     */
                    @Override
                    public View wrap(
                            @Nullable final Bytes source,
                            @NonNull final byte[] in,
                            final int offset,
                            final int length,
                            final boolean strictMode,
                            final int maxDepth,
                            final int maxSize)
                            throws ParseException {
                        cursor.reset(source, in, offset, offset + length);
                        return index(strictMode, maxDepth, maxSize);
                    }

                    /**
                     * Wrap an encoded $model in {@code length} bytes from {@code offset} within what {@code outer} reads,
                     * validating it as {@link Codec#parse} does with the same arguments. Views use it for the messages
                     * they contain, whose bytes they checked already.
                     *
                     * @param outer the cursor reading the bytes around the message
                     * @param offset where the message starts
                     * @param length the message's length
                     * @param strictMode when {@code true}, unknown fields and repeated singular messages are errors
                     * @param maxDepth the maximum depth of nested messages below this one
                     * @param maxSize the maximum length of a field, nested message or unknown field
                     * @return this view
                     * @throws ParseException if the message is malformed or nested too deep
                     * @throws IndexOutOfBoundsException if the message is not inside what {@code outer} reads
                     */
                    @Override
                    public View wrap(
                            @NonNull final ArrayCursor outer,
                            final int offset,
                            final int length,
                            final boolean strictMode,
                            final int maxDepth,
                            final int maxSize)
                            throws ParseException {
                        cursor.reset(outer, offset, length);
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
                        generation++;
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
                        source = cursor.source();
                        in = cursor.unsafeArray();
                        if (strictMode) {
                            // strict mode fails fast: validate nested messages now, not when they are read
                            try {
                                validateNested();
                            } catch (final ParseException e) {
                                in = null;
                                throw e;
                            }
                        }
                        return this;
                    }

                    /** Validates every nested message, repeated message element and wrapper value, at any depth. */
                    private void validateNested() throws ParseException {
                $validations    }

                $helpers
                    /**
                     * Parse the wrapped message into a model object.
                     *
                     * @return the model object
                     * @throws ParseException if the message is malformed
                     */
                    public $model toModel() throws ParseException {
                        return $model.PROTOBUF.parse(ArrayCursor.bytes(source, in, start, end - start));
                    }
                $accessors
                }
                """
                .replace("$state", parts.state().toString().indent(DEFAULT_INDENT))
                .replace("$resets", parts.resets().toString().indent(DEFAULT_INDENT * 2))
                .replace("$fastPath", parts.fastPath().toString().indent(DEFAULT_INDENT * 3))
                .replace("$cases", parts.cases().toString().indent(DEFAULT_INDENT * 5))
                .replace("$accessors", parts.accessors().toString().indent(DEFAULT_INDENT))
                .replace("$validations", parts.validations().toString().indent(DEFAULT_INDENT * 2))
                .replace("$helpers", helpers(parts).indent(DEFAULT_INDENT))
                .replace("$model", modelClass)
                .replace("$schema", schemaClass)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /** The view's helper methods, with the cursor wrapper fields are read with if it has any. */
    private static String helpers(final Parts parts) {
        if (!parts.accessors().toString().contains("wrapperCursor(")) {
            return parts.helpers().toString();
        }
        return parts.helpers() + """

                /** Reads wrapper fields' values, leaving {@code cursor} over this message for its child views. */
                private ArrayCursor wrapperCursor;

                /** A cursor over the wrapper message {@code length} bytes from {@code offset}. */
                private ArrayCursor wrapperCursor(final int offset, final int length) {
                    if (wrapperCursor == null) {
                        wrapperCursor = new ArrayCursor();
                    }
                    return wrapperCursor.reset(cursor, offset, length);
                }
                """;
    }

    /** {@code if (cursor.at(…))} or {@code while}, for a tag of at most two bytes; empty for longer tags. */
    private static String fastPath(final String keyword, final int fieldNumber, final int wireType, final String body) {
        final int[] tagBytes = Common.tagBytes(fieldNumber, wireType);
        if (tagBytes.length > 2) {
            return "";
        }
        final String tag = Arrays.stream(tagBytes)
                .mapToObj(b -> "(byte) 0x%02X".formatted(b))
                .collect(Collectors.joining(", "));
        return """
                $keyword (cursor.at($tag)) {
                $body}
                """.replace("$keyword", keyword).replace("$tag", tag).replace("$body", body.indent(DEFAULT_INDENT));
    }

    private static String caseCode(final SingleField field, final int wireType, final String body) {
        return """
                case $tag /* [$type] field=$number [$field] */ -> {
                $body}
                """.replace("$tag", Integer.toString(Common.getTag(wireType, field.fieldNumber())))
                .replace("$type", field.type().toString())
                .replace("$number", Integer.toString(field.fieldNumber()))
                .replace("$field", field.name())
                .replace("$body", body.indent(DEFAULT_INDENT));
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

    /**
     * A singular field, or a oneof alternative when {@code oneOfKind} names the oneof's kind field.
     *
     * @param slot for a oneof alternative indexed with others into one shared offset and length, the slot's name;
     *     otherwise null
     * @return for a oneof alternative, its in-order fast path ({@code if (cursor.at(…)) {…}}), or empty if its tag is
     * too long for one or it is in a shared slot; for a singular field, which adds its own, null
     */
    private static String singular(
            final SingleField field, final String oneOfKind, final String slot, final Parts parts) {
        final String name = field.nameCamelFirstLower();
        final int wireType = field.optionalValueType()
                ? Common.TYPE_LENGTH_DELIMITED
                : field.type().wireType();
        final String guard = oneOfKind == null ? "" : oneOfKind + " == " + field.fieldNumber();
        if (slot != null) {
            // indexed by the oneof's shared method; its accessors read the shared offset and length
            parts.accessors()
                    .append(slotted(
                            field.optionalValueType()
                                    ? wrapperAccessor(field, name, guard)
                                    : accessor(field, name, guard),
                            name,
                            slot));
            if (field.type() == Field.FieldType.MESSAGE) {
                parts.validations().append(name + "();\n");
            }
            return "";
        }
        String record;
        if (wireType == Common.TYPE_LENGTH_DELIMITED) {
            parts.state().append("""
                    private int $offset = -1;
                    private int $length;
                    """.replace("$offset", offsetVar(name)).replace("$length", lengthVar(name)));
            if (oneOfKind == null) {
                // a oneof alternative's offset is only read while the oneof's kind says it is set: no reset
                parts.resets().append("$offset = -1;\n".replace("$offset", offsetVar(name)));
            }
            // strings are checked to be valid UTF-8 while wrapping, as parsing does
            record = """
                    $length = cursor.$read(maxSize, "$field");
                    $offset = cursor.position();
                    cursor.skip($length);
                    """.replace("$read", field.type() == Field.FieldType.STRING ? "stringLength" : "length")
                    .replace("$field", field.name())
                    .replace("$offset", offsetVar(name))
                    .replace("$length", lengthVar(name));
        } else {
            // numbers are decoded once, while wrapping; absent fields keep their default, 0
            parts.state().append("private long $value;\n".replace("$value", valueVar(name)));
            if (oneOfKind == null) {
                parts.resets().append("$value = 0;\n".replace("$value", valueVar(name)));
            }
            record = "$value = $decode;\n"
                    .replace("$value", valueVar(name))
                    .replace("$decode", decode(field.type(), "cursor"));
        }
        if (field.type() == Field.FieldType.MESSAGE && !field.optionalValueType()) {
            // as in parsing, strict mode rejects a singular message, or a oneof with a message alternative, seen twice
            record = """
                    if (strictMode && $isSet) {
                        throw new ParseException("A non-repeated $field encountered more than once");
                    }
                    """.replace("$isSet", oneOfKind == null ? offsetVar(name) + " >= 0" : oneOfKind + " != 0").replace(
                            "$field",
                            oneOfKind == null ? field.name() : field.parent().name())
                    + record;
        }
        final String fast;
        if (oneOfKind != null) {
            record += "$kind = $number;\n"
                    .replace("$kind", oneOfKind)
                    .replace("$number", Integer.toString(field.fieldNumber()));
            fast = fastPath("if", field.fieldNumber(), wireType, parts.index("index_" + name, record, "", false));
        } else {
            fast = null;
            parts.fastPath()
                    .append(fastPath(
                            "if", field.fieldNumber(), wireType, parts.index("index_" + name, record, "", false)));
        }
        parts.cases().append(caseCode(field, wireType, parts.index("index_" + name, record, "", true)));
        parts.accessors()
                .append(field.optionalValueType() ? wrapperAccessor(field, name, guard) : accessor(field, name, guard));
        if (field.type() == Field.FieldType.MESSAGE) {
            // reading a nested message or wrapper value validates it; absent ones are skipped by the accessor
            parts.validations().append(name + "();\n");
        }
        return fast;
    }

    /**
     * The alternatives of a oneof that share one offset and length: their state, the method indexing any of them, its
     * call in the in-order fast path when the next field is one of them, and its call from the tag switch.
     *
     * <p>Only alternatives with one-byte tags share the slot ({@link #oneOf} picks them), so their field numbers are 1 to
     * 15: one 16-bit mask, bit {@code n} for field {@code n}, lets {@code ArrayCursor.atField} test the next byte for
     * any of them in one compare, and a 16-entry array maps a field number back to its name for error messages. The
     * oneof's other alternatives, with larger field numbers, keep their own offsets and go through the tag switch. For a
     * oneof {@code key} with message alternatives {@code account_id = 1} and {@code token_id = 2}, the view gets:
     *
     * <pre>{@code
     * private int key_offset = -1;
     * private int key_length;
     * private static final String[] KEY_NAMES = {null, "account_id", "token_id", null, ...};
     *
     * private void index_key(final int tag) throws java.io.IOException, ParseException {
     *     if (strictMode && key_kind != 0) {
     *         throw new ParseException("A non-repeated key encountered more than once");
     *     }
     *     key_length = cursor.length(maxSize, KEY_NAMES[tag >>> 3]);
     *     key_offset = cursor.position();
     *     cursor.skip(key_length);
     *     key_kind = tag >>> 3;
     * }
     *
     * // in the in-order fast path
     * final int keyTag = cursor.atField(0x6);
     * if (keyTag != 0) {
     *     index_key(keyTag);
     * }
     *
     * // in the tag switch
     * case 10, 18 /* key *&#47; -> index_key(tag);
     * }</pre>
     */
    private static void sharedSlot(
            final OneOfField oneOf,
            final List<SingleField> shared,
            final String kind,
            final String slot,
            final Parts parts) {
        int mask = 0;
        // one-byte tags only, so field numbers 1 to 15: see the javadoc
        final String[] names = new String[16];
        boolean message = false;
        final StringBuilder tags = new StringBuilder();
        for (final SingleField alternative : shared) {
            mask |= 1 << alternative.fieldNumber();
            names[alternative.fieldNumber()] = alternative.name();
            message |= alternative.type() == Field.FieldType.MESSAGE && !alternative.optionalValueType();
            tags.append(tags.isEmpty() ? "" : ", ")
                    .append(Common.getTag(Common.TYPE_LENGTH_DELIMITED, alternative.fieldNumber()));
        }
        final String namesConstant = Common.camelToUpperSnake(slot) + "_NAMES";
        parts.state()
                .append("""
                        private int $offset = -1;
                        private int $length;
                        """.replace("$offset", offsetVar(slot)).replace("$length", lengthVar(slot)))
                .append("/** The field names of {@code %s}'s shared alternatives, by field number. */%n"
                        .formatted(oneOf.name()))
                .append("private static final String[] %s = {%s};%n"
                        .formatted(
                                namesConstant,
                                Arrays.stream(names)
                                        .map(n -> n == null ? "null" : "\"" + n + "\"")
                                        .collect(Collectors.joining(", "))));
        final String strict = message ? """
                    if (strictMode && $kind != 0) {
                        throw new ParseException("A non-repeated $oneOf encountered more than once");
                    }
                """ : "";
        parts.helpers().append("""

                        /** Index whichever message or bytes alternative of {@code $oneOf} this is, from its tag. */
                        private void index_$slot(final int tag) throws java.io.IOException, ParseException {
                        $strict    $slot_length = cursor.length(maxSize, $NAMES[tag >>> 3]);
                            $slot_offset = cursor.position();
                            cursor.skip($slot_length);
                            $kind = tag >>> 3;
                        }
                        """.replace("$strict", strict)
                .replace("$NAMES", namesConstant)
                .replace("$slot", slot)
                .replace("$kind", kind)
                .replace("$oneOf", oneOf.name()));
        parts.fastPath().append("""
                        final int $slotTag = cursor.atField(0x$mask);
                        if ($slotTag != 0) {
                            index_$slot($slotTag);
                        }
                        """.replace("$mask", Integer.toHexString(mask).toUpperCase(java.util.Locale.ROOT))
                .replace("$slot", slot));
        parts.cases().append("case %s /* %s */ -> index_%s(tag);%n".formatted(tags, oneOf.name(), slot));
    }

    /** An accessor's code reading a shared oneof slot's offset and length instead of the alternative's own. */
    private static String slotted(final String code, final String name, final String slot) {
        return code.replaceAll("\\b" + offsetVar(name) + "\\b", offsetVar(slot))
                .replaceAll("\\b" + lengthVar(name) + "\\b", lengthVar(slot));
    }

    /** A oneof: each alternative is recorded like a singular field, and the oneof remembers the last one seen. */
    private static void oneOf(final OneOfField oneOf, final Parts parts) {
        final String kind = oneOf.nameCamelFirstLower() + "_kind";
        parts.state().append("private int " + kind + ";\n");
        parts.resets().append(kind + " = 0;\n");
        final StringBuilder kinds = new StringBuilder();
        // The alternatives that are messages or bytes, with one-byte tags, share one offset and length, as at most one
        // is set, and are indexed by one method: one compare in the in-order fast path for all of them, and one call
        // in the tag switch, so index() stays small, which it must for the JIT to inline the cursor's reads into it.
        final List<SingleField> shared = oneOf.fields().stream()
                .map(f -> (SingleField) f)
                .filter(f -> f.type() == Field.FieldType.MESSAGE || f.type() == Field.FieldType.BYTES)
                .filter(f -> Common.tagBytes(f.fieldNumber(), Common.TYPE_LENGTH_DELIMITED).length == 1)
                .toList();
        final String slot = shared.isEmpty() ? null : oneOf.nameCamelFirstLower();
        if (slot != null) {
            sharedSlot(oneOf, shared, kind, slot, parts);
        }
        // a small oneof's other alternatives are read in order too, when its first alternative in field order is set:
        // one at() per alternative, chained, as at most one is set. Ids are mostly such oneofs.
        final boolean fast = oneOf.fields().size() <= Common.MAX_FAST_PATH_ONEOF_ALTERNATIVES;
        final StringBuilder chain = new StringBuilder();
        for (final Field alternative : oneOf.fields()) {
            final String alternativeFastPath =
                    singular((SingleField) alternative, kind, shared.contains(alternative) ? slot : null, parts);
            if (fast && !alternativeFastPath.isEmpty()) {
                chain.append(chain.isEmpty() ? "" : "else ").append(alternativeFastPath);
            }
            kinds.append("case $number -> $enum.$alternative;\n"
                    .replace("$number", Integer.toString(alternative.fieldNumber()))
                    .replace("$enum", oneOf.getEnumClassRef())
                    .replace("$alternative", Common.camelToUpperSnake(alternative.name())));
        }
        // "} else if" rather than a new statement after each "}"
        parts.fastPath().append(chain.toString().replace("}\nelse ", "} else "));
        parts.accessors().append("""

                /** @return which alternative of {@code $name} is set, as in the model */
                public $enum $accessor() {
                    return switch ($kind) {
                $kinds        default -> $enum.UNSET;
                    };
                }
                """.replace("$kinds", kinds.toString().indent(DEFAULT_INDENT * 2))
                .replace("$enum", oneOf.getEnumClassRef())
                .replace("$accessor", oneOf.nameCamelFirstLower() + "Kind")
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
        parts.state().append("""
                private int $name_first = -1;
                private int $name_count;
                private $NameCursor $name_cursor;
                """.replace("$Name", field.nameCamelFirstUpper()).replace("$name", name));
        parts.resets().append("""
                $name_first = -1;
                $name_count = 0;
                """.replace("$name", name));
        final String element = delimited
                ? "cursor.skip(cursor.$read(maxSize, \"$field\"));\n"
                        .replace("$read", field.type() == Field.FieldType.STRING ? "stringLength" : "length")
                        .replace("$field", field.name())
                : skipValue(field.type(), "cursor") + ";\n";
        final String unpacked = """
                if ($name_first < 0) {
                    $name_first = $tagStart;
                }
                $name_count++;
                $element""".replace("$element", element);
        final int tagLength = Common.tagBytes(field.fieldNumber(), wireType).length;
        parts.fastPath()
                .append(fastPath(
                        "while",
                        field.fieldNumber(),
                        wireType,
                        parts.index(
                                "index_" + name,
                                unpacked.replace("$name", name),
                                "cursor.position() - " + tagLength,
                                false)));
        parts.cases()
                .append(caseCode(
                        field,
                        wireType,
                        parts.index("index_" + name, unpacked.replace("$name", name), "tagStart", true)));
        if (!delimited) {
            final String packed =
                    """
                    if ($name_first < 0) {
                        $name_first = $tagStart;
                    }
                    final int packedLength = cursor.length(maxSize, "$field");
                    final int packedEnd = cursor.position() + packedLength;
                    while (cursor.position() < packedEnd) {
                        $decode;
                        $name_count++;
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
                            parts.index(
                                    "indexPacked_" + name,
                                    packed.replace("$name", name),
                                    "cursor.position() - " + packedTagLength,
                                    false)));
            parts.cases()
                    .append(caseCode(
                            field,
                            Common.TYPE_LENGTH_DELIMITED,
                            parts.index("indexPacked_" + name, packed.replace("$name", name), "tagStart", true)));
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
                    child.wrap(c, offset, length, strictMode, maxDepth - 1, maxSize);
                    """.replace("$elem", field.javaFieldTypeBase());
                    case STRING, BYTES -> """
                    length = c.length(maxSize, "$field");
                    offset = c.position();
                    c.skip(length);
                    """;
                    default -> "value = " + decode(field.type(), "c") + ";\n";
                };
        if (field.type() == Field.FieldType.MESSAGE) {
            // moving the cursor to each element wraps, and so validates, it
            parts.validations()
                    .append("""
                            for (final $NameCursor elements = $name(); elements.next(); ) {
                                // validated by next()
                            }
                            """.replace("$Name", field.nameCamelFirstUpper()).replace("$name", name));
        }
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
                            return $name_count;
                        }

                        /**
                         * @return a cursor over the {@code $field} elements, positioned before the first: call
                         *     {@code next()} to move to each one. The same cursor is reset and returned on each call.
                         */
                        public $NameCursor $name() {
                            if ($name_cursor == null) {
                                $name_cursor = new $NameCursor();
                            }
                            return $name_cursor.reset();
                        }

                        /** A reusable cursor over the {@code $field} elements. */
                        public final class $NameCursor {
                            private final ArrayCursor c = new ArrayCursor();
                            private int remaining;
                            private int packedEnd = -1;
                            private long value;
                            private int offset;
                            private int length;
                            private $childType child;

                            private $NameCursor reset() {
                                remaining = $name_count;
                                packedEnd = -1;
                                if ($name_first >= 0) {
                                    c.reset(cursor, $name_first, end - $name_first);
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

                    /** @return the length in bytes of the current element */
                    public int valueLength() {
                        return length;
                    }

                    /**
                     * Copy the current element's bytes, without allocating.
                     *
                     * @param dst the array to copy to
                     * @param dstOffset where in {@code dst} to copy to
                     * @return the number of bytes copied, {@link #valueLength()}
                     */
                    public int valueCopyTo(@NonNull final byte[] dst, final int dstOffset) {
                        System.arraycopy(in, offset, dst, dstOffset, length);
                        return length;
                    }

                    /**
                     * @param index the index of a byte of the current element, from 0
                     * @return that byte, read in place
                     * @throws IndexOutOfBoundsException if {@code index} is not less than {@link #valueLength()}
                     */
                    public byte valueByteAt(final int index) {
                        java.util.Objects.checkIndex(index, length);
                        return in[offset + index];
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
                                        : "as a view of the wrapped Bytes, or a copy when wrapping an array")
                        .replace("$type", field.type() == Field.FieldType.STRING ? "String" : "Bytes")
                        .replace(
                                "$value",
                                field.type() == Field.FieldType.STRING
                                        ? "new String(in, offset, length, java.nio.charset.StandardCharsets.UTF_8)"
                                        : "ArrayCursor.bytes(source, in, offset, length)");
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
        final String doc =
                "\n/** @return {@code $field}, or its default when absent */\n".replace("$field", field.name());
        final String when = guard.isEmpty() ? "" : guard + " ? ";
        final String otherwise = guard.isEmpty() ? "" : " : 0";
        final String number =
                switch (field.type()) {
                    case INT32, UINT32, FIXED32, SFIXED32 ->
                        "public int $name() {\n    return $when(int) $v$otherwise;\n}\n";
                    case SINT32 ->
                        "public int $name() {\n    return $when(int) (($v >>> 1) ^ -($v & 1))$otherwise;\n}\n";
                    case INT64, UINT64, FIXED64, SFIXED64 ->
                        "public long $name() {\n    return $when$v$otherwise;\n}\n";
                    case SINT64 -> "public long $name() {\n    return $when($v >>> 1) ^ -($v & 1)$otherwise;\n}\n";
                    case BOOL -> "public boolean $name() {\n    return $guard$v != 0;\n}\n";
                    case FLOAT ->
                        "public float $name() {\n    return $whenFloat.intBitsToFloat((int) $v)$otherwise;\n}\n";
                    case DOUBLE ->
                        "public double $name() {\n    return $whenDouble.longBitsToDouble($v)$otherwise;\n}\n";
                    default -> null;
                };
        if (number != null) {
            return doc
                    + number.replace("$guard", guard.isEmpty() ? "" : guard + " && ")
                            .replace("$when", when)
                            .replace("$otherwise", otherwise)
                            .replace("$v", valueVar(name))
                            .replace("$name", name);
        }
        return switch (field.type()) {
            case ENUM ->
                doc
                        + """
                    public int $nameProtoOrdinal() {
                        return $when(int) $name_value$otherwise;
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

                    /** @return the length in bytes of {@code $field}, 0 when absent */
                    public int $nameLength() {
                        return $absent ? 0 : $name_length;
                    }

                    /**
                     * Copy the bytes of {@code $field}, without allocating.
                     *
                     * @param dst the array to copy to
                     * @param dstOffset where in {@code dst} to copy to
                     * @return the number of bytes copied, {@link #$nameLength()}
                     */
                    public int $nameCopyTo(@NonNull final byte[] dst, final int dstOffset) {
                        final int length = $nameLength();
                        if (length > 0) {
                            System.arraycopy(in, $name_offset, dst, dstOffset, length);
                        }
                        return length;
                    }

                    /**
                     * @param other bytes to compare with
                     * @return true if {@code $field} is exactly {@code other}, compared without allocating
                     */
                    public boolean $nameEquals(@NonNull final byte[] other) {
                        final int length = $nameLength();
                        return length == 0
                                ? other.length == 0
                                : java.util.Arrays.equals(
                                        in, $name_offset, $name_offset + length, other, 0, other.length);
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
                                                : "as a view of the wrapped Bytes, or a copy when wrapping an array")
                                .replace("$type", field.type() == Field.FieldType.STRING ? "String" : "Bytes")
                                .replace("$default", field.type() == Field.FieldType.STRING ? "\"\"" : "Bytes.EMPTY")
                                .replace(
                                        "$value",
                                        field.type() == Field.FieldType.STRING
                                                ? "new String(in, $name_offset, $name_length, java.nio.charset.StandardCharsets.UTF_8)"
                                                : "ArrayCursor.bytes(source, in, $name_offset, $name_length)")
                                .replace("$name", name)
                                .replace("$field", field.name())
                        + (field.type() == Field.FieldType.BYTES ? bytesExtras(name, field) : "");
            case MESSAGE ->
                """

                    private $type.View $name_view;
                    private long $name_viewGeneration;

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
                        if ($name_view == null) {
                            $name_view = new $type.View();
                        } else if ($name_viewGeneration == generation && $name_view.wraps(in, $name_offset, $name_length)) {
                            // already wrapped over this field since this view was wrapped: don't index it again
                            return $name_view;
                        }
                        $name_view.wrap(cursor, $name_offset, $name_length, strictMode, maxDepth - 1, maxSize);
                        $name_viewGeneration = generation;
                        return $name_view;
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
        final String primitive =
                switch (typeReadDefault[0]) {
                    case "Integer" -> "int";
                    case "Long" -> "long";
                    case "Float" -> "float";
                    case "Double" -> "double";
                    case "Boolean" -> "boolean";
                    default -> null;
                };
        if (primitive != null) {
            // numbers and bools also have an unboxed accessor, so reading them allocates nothing
            return """

                /** @return true if {@code $field} is present */
                public boolean has$Name() {
                    return !($absent);
                }

                /**
                 * @return {@code $field}: null when absent, as in the model; {@link #$nameOrElse} does not box
                 * @throws ParseException if the wrapper is malformed
                 */
                public $type $name() throws ParseException {
                    return $absent ? null : $nameOrElse($default);
                }

                /**
                 * @param defaultValue the value to return when {@code $field} is absent
                 * @return {@code $field}, or {@code defaultValue} when absent
                 * @throws ParseException if the wrapper is malformed
                 */
                public $primitive $nameOrElse(final $primitive defaultValue) throws ParseException {
                    if ($absent) {
                        return defaultValue;
                    }
                    if ($name_length == 0) {
                        return $default;
                    }
                    try {
                        // the wrapper's one field: its tag, then the value
                        final ArrayCursor cursor = wrapperCursor($name_offset, $name_length);
                        cursor.tag();
                        return $read;
                    } catch (final RuntimeException e) {
                        throw new ParseException(e);
                    }
                }
                """.replace("$absent", absent(name, guard))
                    .replace("$primitive", primitive)
                    .replace("$type", typeReadDefault[0])
                    .replace("$read", typeReadDefault[1])
                    .replace("$default", typeReadDefault[2])
                    .replace("$Name", field.nameCamelFirstUpper())
                    .replace("$name", name)
                    .replace("$field", field.name());
        }
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
                    if ($name_length == 0) {
                        return $default;
                    }
                    try {
                        // the wrapper's one field: its tag, then the value
                        final ArrayCursor cursor = wrapperCursor($name_offset, $name_length);
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

    /** A bytes field's accessors that read it in place: one byte, or as an encoded message through its view. */
    private static String bytesExtras(final String name, final SingleField field) {
        return """

                /**
                 * @param index the index of a byte of {@code $field}, from 0
                 * @return that byte, read in place
                 * @throws IndexOutOfBoundsException if {@code index} is not less than {@link #$nameLength()}
                 */
                public byte $nameByteAt(final int index) {
                    java.util.Objects.checkIndex(index, $nameLength());
                    return in[$name_offset + index];
                }

                /**
                 * Read {@code $field}, an encoded message, through its view without copying it: wrap {@code view} over
                 * it, with this view's strict mode and limits, one level deeper.
                 *
                 * @param view the view of the message type {@code $field} holds
                 * @param <V> the view's type
                 * @return {@code view}, wrapped over {@code $field}, empty when absent
                 * @throws ParseException if the bytes are not a well-formed message
                 */
                public <V extends com.hedera.pbj.runtime.MessageView> V $nameAs(@NonNull final V view)
                        throws ParseException {
                    final int length = $nameLength();
                    view.wrap(cursor, length == 0 ? start : $name_offset, length, strictMode, maxDepth - 1, maxSize);
                    return view;
                }
                """.replace("$name", name).replace("$field", field.name());
    }

    /** The condition for a delimited field being absent: no offset recorded, or another oneof alternative set. */
    private static String absent(final String name, final String guard) {
        // a oneof alternative's offset is recorded whenever its kind is set
        return guard.isEmpty() ? offsetVar(name) + " < 0" : "!(" + guard + ")";
    }
}
