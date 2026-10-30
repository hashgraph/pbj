// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.Field.FieldType;
import com.hedera.pbj.compiler.impl.LookupHelper;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.PbjCompilerException;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.generators.protobuf.InlineFieldCodeGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageElementContext;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Generates the zero-allocation writer of a message with {@code pbj.writers}: a reusable flyweight, nested in the model
 * class as {@code Writer}, that encodes fields straight into a {@code ProtoWriteBuffer} as the caller supplies them, with
 * no model objects. The output is byte-identical to the model's encoding for the same values: fields in field-number
 * order, the same default values left out, canonical varints, and map keys in the model's order. The checks that keep
 * it so (field order, one alternative per oneof, ascending map keys, no writing after the end) are in the runtime's
 * {@code MessageWriter}, which the writer extends.
 *
 * <p>Nested messages generated in this build are written through child writers, created once per field and reused.
 * Messages from dependency jars, which are not regenerated, are written from objects (with their codec) or from
 * pre-encoded bytes.
 *
 * <p>Each field method checks the field order with one compare, makes room once for the field's largest encoding, then
 * writes its tag bytes and value with unchecked stores.
 */
final class WriterGenerator {

    /**
     * Names generated methods may not take, because the writer's base class, or {@code Object}, uses them. A field with
     * one of these names gets methods named with the suffix {@code Field}, such as {@code lengthField(long)}.
     */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "start",
            "end",
            "offset",
            "length",
            "isOpen",
            "writeFrom",
            "out",
            "startTopLevel",
            "openChild",
            "openPacked",
            "checkField",
            "checkRepeatedField",
            "isFirstElement",
            "checkOpen",
            "checkOneof",
            "beginWriteFrom",
            "finishWriteFrom",
            "hashCode",
            "equals",
            "toString",
            "getClass",
            "notify",
            "notifyAll",
            "wait",
            "clone",
            "finalize");

    /** The widest Javadoc text, so the comment fits in 120 columns inside the model and writer classes. */
    private static final int JAVADOC_WIDTH = 108;

    private WriterGenerator() {}

    /** The code generated for the writer, built field by field. */
    private record Parts(
            String modelClass,
            ContextualLookupHelper lookupHelper,
            StringBuilder state,
            StringBuilder methods,
            StringBuilder writeFrom,
            Set<String> signatures,
            Map<OneOfField, Integer> oneofBits) {

        LookupHelper lookup() {
            return lookupHelper.getLookupHelper();
        }

        /**
         * Add a method, failing generation if another field already generated one with the same signature.
         *
         * @param field the field the method writes
         * @param javadoc the method's Javadoc, without the comment markers
         * @param returnType the return type
         * @param name the method's name
         * @param parameters the parameters, as declared
         * @param body the method's statements
         */
        void method(
                final Field field,
                final String javadoc,
                final String returnType,
                final String name,
                final String parameters,
                final String body) {
            final String erased = parameters
                    .replaceAll("@\\w+ ", "")
                    .replaceAll("final ", "")
                    .replaceAll(" \\w+(,|$)", "$1")
                    .replace(" ", "");
            if (!signatures.add(name + "(" + erased + ")")) {
                throw new PbjCompilerException(("Message %s has writers, but two of its fields would both generate the"
                                + " writer method %s(%s), the second for field '%s': rename one of the fields")
                        .formatted(modelClass, name, erased, field.name()));
            }
            methods.append("\n/**\n");
            javadoc.lines().forEach(line -> wrap(line, methods));
            methods.append(" */\n");
            methods.append("public %s %s(%s) {%n".formatted(returnType, name, parameters));
            methods.append(body.indent(DEFAULT_INDENT));
            methods.append("}\n");
        }

        /** Append one line of Javadoc, wrapped to fit the line length in the writer class. */
        private static void wrap(final String line, final StringBuilder out) {
            if (line.isEmpty()) {
                out.append(" *\n");
                return;
            }
            // continuation lines of @param and the like are indented, as Javadoc tags are
            final String continuation = line.startsWith("@") ? "     " : "";
            String rest = line;
            String prefix = "";
            while (prefix.length() + rest.length() > JAVADOC_WIDTH) {
                final int cut = rest.lastIndexOf(' ', JAVADOC_WIDTH - prefix.length());
                if (cut <= 0) {
                    break;
                }
                out.append(" * ").append(prefix).append(rest, 0, cut).append('\n');
                rest = rest.substring(cut + 1);
                prefix = continuation;
            }
            out.append(" * ").append(prefix).append(rest).append('\n');
        }

        /** The check a oneof alternative makes before it is written, or an empty string outside a oneof. */
        String oneofCheck(final Field field) {
            final OneOfField oneOf = field.parent();
            return oneOf == null
                    ? ""
                    : "checkOneof(%dL, \"%s\");%n".formatted(1L << oneofBits.get(oneOf), oneOf.name());
        }
    }

    /**
     * Generate the writer class for a message, or nothing if it has none.
     *
     * @param msgDef the message
     * @param fields the message's fields
     * @param modelClass the model's simple class name
     * @param lookupHelper the lookup helper
     * @return the writer class, indented for the model's body, or an empty string
     */
    static String generateWriter(
            final MessageDefContext msgDef,
            final List<Field> fields,
            final String modelClass,
            final ContextualLookupHelper lookupHelper) {
        if (!lookupHelper.getLookupHelper().hasWriter(msgDef)) {
            return "";
        }
        for (final MessageElementContext element : msgDef.messageBody().messageElement()) {
            final String nested = element.messageDef() != null
                    ? element.messageDef().messageName().getText()
                    : element.enumDef() != null ? element.enumDef().enumName().getText() : null;
            if ("Writer".equals(nested)) {
                throw new PbjCompilerException(("Message %s has pbj.writers but declares its own nested type named"
                                + " Writer, which would clash with the generated writer class: rename it, or turn"
                                + " writers off for this message and every message it is reachable from")
                        .formatted(modelClass));
            }
        }
        final Parts parts = new Parts(
                modelClass,
                lookupHelper,
                new StringBuilder(),
                new StringBuilder(),
                new StringBuilder(),
                new HashSet<>(),
                new IdentityHashMap<>());
        for (final Field field : fields) {
            if (field instanceof OneOfField oneOf) {
                if (parts.oneofBits().size() == Long.SIZE) {
                    throw new PbjCompilerException(
                            "Message %s has more than 64 oneofs, more than writers support".formatted(modelClass));
                }
                parts.oneofBits().put(oneOf, parts.oneofBits().size());
            }
        }
        // the field methods, in field-number order
        final List<Field> sorted = fields.stream()
                .flatMap(field -> field instanceof OneOfField oneOf ? oneOf.fields().stream() : Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();
        for (final Field field : sorted) {
            if (field instanceof MapField map) {
                map(map, parts);
                continue;
            }
            final SingleField single = (SingleField) field;
            if (single.optionalValueType()) {
                if (single.repeated()) {
                    throw new PbjCompilerException(("Message %s has writers, but field '%s' is a repeated"
                                    + " google.protobuf wrapper type, which writers do not support")
                            .formatted(modelClass, single.name()));
                }
                wrapper(single, parts);
            } else if (single.type() == FieldType.MESSAGE) {
                message(single, parts);
            } else if (single.repeated()) {
                repeated(single, parts);
            } else {
                singular(single, parts);
            }
        }
        writeFrom(sorted, parts);
        // spotless:off
        return """
                /**
                 * A reusable, zero-allocation writer of {@link $model} messages: encodes fields straight into a
                 * {@link ProtoWriteBuffer} as they are given, without building model objects. The output is byte-identical to
                 * {@code $model.PROTOBUF.toBytes(…)} of a model object with the same values.
                 *
                 * <p>Start a top-level message with {@link #start}, write its fields in field-number order, and finish it
                 * with {@link #end()}. Each singular field may be written once, the elements of a repeated field one after
                 * another, and only one alternative of each oneof; anything else throws {@link IllegalStateException}. As
                 * in the model, fields outside oneofs with their default values are not written, and oneof alternatives
                 * always are.
                 *
                 * <p>A method for a nested message opens it and returns its writer: write its fields, then {@code end()}
                 * it, or simply carry on writing to this writer, which ends it first. Child writers are created once and
                 * reused, so a returned writer is valid only until the next call on this writer. After a message ends, its
                 * {@link #offset()} and {@link #length()} locate its bytes in the buffer, to hash or send them before the
                 * enclosing message is finished. Nested messages can also be written from model objects, or as pre-encoded
                 * bytes ({@code …Raw}), which must be the canonical encoding of the field's type for the output to be
                 * canonical.
                 *
                 * <p>Once every child writer has been created, writing allocates nothing. Not thread safe.
                 */
                public static final class Writer extends MessageWriter {
                $state
                    /** Create a writer: {@link #start} it, or get it from a parent writer, to use it. */
                    public Writer() {}

                    /**
                     * Start writing a top-level $model at the buffer's current position.
                     *
                     * @param out the buffer to write to, with no message open in it
                     * @return this writer
                     * @throws IllegalStateException if a message is still open in the buffer
                     */
                    public Writer start(@NonNull final ProtoWriteBuffer out) {
                        startTopLevel(out);
                        return this;
                    }
                $methods
                    /**
                     * Write every field of a model object, and its unknown fields, as {@code $model.PROTOBUF} would. Nested
                     * messages are written through child writers, with their length prefixes sized from the objects'
                     * {@code protobufSize()}. Call it on an open writer with nothing written yet; afterwards, only
                     * {@link #end()} may be called. Allocates nothing.
                     *
                     * @param data the object to write
                     * @return this writer
                     * @throws IllegalStateException if the writer is not open, or a field has been written
                     */
                    public Writer writeFrom(@NonNull final $model data) {
                        beginWriteFrom();
                $writeFrom
                        final List<UnknownField> unknownFields = data.getUnknownFields();
                        for (int i = 0; i < unknownFields.size(); i++) {
                            final UnknownField uf = unknownFields.get(i);
                            out.tagRaw((uf.field() << ProtoParserTools.TAG_FIELD_OFFSET) | uf.wireType().ordinal(), uf.bytes());
                        }
                        finishWriteFrom();
                        return this;
                    }
                }
                """
                .replace("$state", parts.state().toString().indent(DEFAULT_INDENT))
                .replace("$methods", parts.methods().toString().indent(DEFAULT_INDENT))
                .replace("$writeFrom", parts.writeFrom().toString().indent(DEFAULT_INDENT * 2))
                .replace("$model", modelClass)
                .indent(DEFAULT_INDENT)
                // String.indent indents blank lines too
                .replaceAll("(?m)^[ \t]+$", "")
                + "\n";
        // spotless:on
    }

    // ------------------------------------------------------------------------------------------- shared pieces

    /** The base name of a field's methods: its camel-case name, with {@code Field} added if it is reserved. */
    private static String baseName(final Field field) {
        final String name = field.nameCamelFirstLower();
        return RESERVED_NAMES.contains(name) ? name + "Field" : name;
    }

    /** The base name with its first letter upper case, for names such as {@code addLegs}. */
    private static String upperName(final Field field) {
        final String name = baseName(field);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** A field's tag, as a Java int literal: generated writers pass it to the buffer, which writes it as a varint. */
    private static String tag(final int fieldNumber, final int wireType) {
        final int tag = Common.getTag(wireType, fieldNumber);
        return tag >= 0 && tag < 10 ? Integer.toString(tag) : "0x%02X".formatted(tag);
    }

    /** The first sentence of a method's Javadoc, naming the field, and noting if it is a oneof alternative. */
    private static String about(final Field field, final String verb) {
        return field.parent() == null
                ? "%s {@code %s}, field %d.".formatted(verb, field.name(), field.fieldNumber())
                : "%s {@code %s}, field %d, an alternative of oneof {@code %s}: written even with its default value."
                        .formatted(
                                verb,
                                field.name(),
                                field.fieldNumber(),
                                field.parent().name());
    }

    /** The Java parameter type for a scalar field type. */
    private static String javaType(final FieldType type) {
        return switch (type) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32, ENUM -> "int";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case BOOL -> "boolean";
            case STRING -> "String";
            case BYTES -> "Bytes";
            default -> throw new IllegalArgumentException("Not a scalar: " + type);
        };
    }

    /** A statement writing a non-delimited scalar field: its tag {@code tag} and the value {@code v}. */
    private static String writeField(final FieldType type, final String tag, final String v) {
        return switch (type) {
            case INT32, ENUM, INT64, UINT64 -> "out.tagVarint(%s, %s);%n".formatted(tag, v);
            case UINT32 -> "out.tagVarint(%s, Integer.toUnsignedLong(%s));%n".formatted(tag, v);
            case SINT32 -> "out.tagVarint(%s, Integer.toUnsignedLong((%s << 1) ^ (%s >> 31)));%n".formatted(tag, v, v);
            case SINT64 -> "out.tagVarint(%s, (%s << 1) ^ (%s >> 63));%n".formatted(tag, v, v);
            case BOOL -> "out.tagVarint(%s, %s ? 1 : 0);%n".formatted(tag, v);
            case FIXED32, SFIXED32 -> "out.tagFixed32(%s, %s);%n".formatted(tag, v);
            case FLOAT -> "out.tagFixed32(%s, Float.floatToIntBits(%s));%n".formatted(tag, v);
            case FIXED64, SFIXED64 -> "out.tagFixed64(%s, %s);%n".formatted(tag, v);
            case DOUBLE -> "out.tagFixed64(%s, Double.doubleToLongBits(%s));%n".formatted(tag, v);
            default -> throw new IllegalArgumentException("Not a non-delimited scalar: " + type);
        };
    }

    /** An expression for the encoded size of a non-delimited scalar value {@code v}, without its tag. */
    private static String valueSize(final FieldType type, final String v) {
        return switch (type) {
            case INT32, ENUM -> "ProtoWriterTools.sizeOfVarInt32(%s)".formatted(v);
            case UINT32 -> "ProtoWriterTools.sizeOfUnsignedVarInt32(%s)".formatted(v);
            case SINT32 -> "ProtoWriterTools.sizeOfUnsignedVarInt32((%s << 1) ^ (%s >> 31))".formatted(v, v);
            case INT64, UINT64 -> "ProtoWriterTools.sizeOfUnsignedVarInt64(%s)".formatted(v);
            case SINT64 -> "ProtoWriterTools.sizeOfUnsignedVarInt64((%s << 1) ^ (%s >> 63))".formatted(v, v);
            case BOOL -> "1";
            case FIXED32, SFIXED32, FLOAT -> "4";
            case FIXED64, SFIXED64, DOUBLE -> "8";
            default -> throw new IllegalArgumentException("Not a non-delimited scalar: " + type);
        };
    }

    /** The condition under which the model writes a field outside a oneof: its value is not the default. */
    private static String notDefault(final FieldType type, final String v) {
        return switch (type) {
            case BOOL -> v;
            case STRING -> "%s != null && !%s.isEmpty()".formatted(v, v);
            case BYTES -> "%s.length() != 0".formatted(v);
            // as in the model, -0.0 is a default and NaN is not
            default -> "%s != 0".formatted(v);
        };
    }

    /** {@code body} inside {@code if (condition) { … }}, or as it is when {@code condition} is null. */
    private static String guarded(final String condition, final String body) {
        return condition == null ? body : "if (%s) {%n%s}%n".formatted(condition, body.indent(DEFAULT_INDENT));
    }

    /**
     * Code writing a message field from a model object, {@code value}, which is not null: the codec's static
     * {@code writeTo} when the message is generated in this build, so the call is direct, otherwise its codec.
     */
    private static String writeObject(
            final SingleField field, final String tag, final String value, final Parts parts) {
        final String codec = InlineFieldCodeGenerator.directCodecClass(field, parts.lookupHelper());
        if (codec == null) {
            return "out.tagMessage(%s, %s, %s.PROTOBUF);%n".formatted(tag, value, field.javaFieldTypeBase());
        }
        return """
                final int size = %s.protobufSize();
                out.tagVarint(%s, size);
                out.ensure(size);
                out.advance(%s.writeTo(%s, out.array(), out.position(), out.position() + size));
                """.formatted(value, tag, codec, value);
    }

    // ------------------------------------------------------------------------------------------- field kinds

    /** A singular scalar, enum, string or bytes field, or such an alternative of a oneof. */
    private static void singular(final SingleField field, final Parts parts) {
        final String name = baseName(field);
        final FieldType type = field.type();
        final boolean inOneof = field.parent() != null;
        final int number = field.fieldNumber();
        final String tag = tag(number, type.wireType());
        final String check = "checkField(%d);%n".formatted(number) + parts.oneofCheck(field);
        if (type == FieldType.STRING || type == FieldType.BYTES) {
            final boolean string = type == FieldType.STRING;
            parts.method(
                    field,
                    about(field, "Write")
                            + (inOneof ? "" : string ? " Not written if null or empty." : " Not written if empty.")
                            + "\n\n@param value the value\n@return this writer",
                    "Writer",
                    name,
                    (string && !inOneof ? "@Nullable" : "@NonNull") + " final " + (string ? "String" : "Bytes")
                            + " value",
                    check
                            + guarded(
                                    inOneof ? null : notDefault(type, "value"),
                                    "out.%s(%s, value);%n".formatted(string ? "tagString" : "tagBytes", tag))
                            + "return this;\n");
            parts.method(
                    field,
                    about(field, "Write") + " From "
                            + (string ? "UTF-8 bytes, which are checked to be well formed," : "the bytes")
                            + " in {@code value[offset, offset + length)}." + (inOneof ? "" : " Not written if empty.")
                            + "\n\n@param value the bytes\n@param offset where they start\n@param length how many there are"
                            + "\n@return this writer"
                            + (string
                                    ? "\n@throws IllegalArgumentException if the bytes are not well-formed UTF-8"
                                    : ""),
                    "Writer",
                    name,
                    "@NonNull final byte[] value, final int offset, final int length",
                    (string ? "ProtoWriteBuffer.checkUtf8(value, offset, length);\n" : "")
                            + check
                            + guarded(
                                    inOneof ? null : "length != 0",
                                    "out.tagBytes(%s, value, offset, length);%n".formatted(tag))
                            + "return this;\n");
            return;
        }
        final String javaType = javaType(type);
        // outside a oneof a bool is only written when true
        final String write = type == FieldType.BOOL && !inOneof
                ? "out.tagVarint(%s, 1);%n".formatted(tag)
                : writeField(type, tag, "value");
        final String method = type == FieldType.ENUM ? name + "ProtoOrdinal" : name;
        parts.method(
                field,
                about(field, "Write") + (type == FieldType.ENUM ? " From the enum value's proto ordinal." : "")
                        + (inOneof ? "" : " Not written if it is the default, as the model does.")
                        + "\n\n@param value the value\n@return this writer",
                "Writer",
                method,
                "final " + javaType + " value",
                check + guarded(inOneof ? null : notDefault(type, "value"), write) + "return this;\n");
        if (type == FieldType.ENUM) {
            parts.method(
                    field,
                    about(field, "Write") + (inOneof ? "" : " Not written if null or the default, as the model does.")
                            + "\n\n@param value the value\n@return this writer",
                    "Writer",
                    name,
                    (inOneof ? "@NonNull" : "@Nullable") + " final " + field.javaFieldTypeBase() + " value",
                    "return %s(%s);%n"
                            .formatted(
                                    method,
                                    inOneof ? "value.protoOrdinal()" : "value == null ? 0 : value.protoOrdinal()"));
        }
    }

    /** A {@code google.protobuf} wrapper type field: the value is written, wrapped, whenever the method is called. */
    private static void wrapper(final SingleField field, final Parts parts) {
        final String name = baseName(field);
        final int number = field.fieldNumber();
        final String tag = tag(number, Common.TYPE_LENGTH_DELIMITED);
        // the wrapped value's type, then code computing the wrapper's content size and code writing the content
        final String javaType;
        final String content;
        final String write;
        switch (field.messageType()) {
            case "StringValue" -> {
                javaType = "String";
                content = """
                        final int utf8Length = ProtoWriterTools.sizeOfStringNoTag(value);
                        final int content = utf8Length == 0
                                ? 0
                                : 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(utf8Length) + utf8Length;
                        """;
                write = guarded("utf8Length != 0", "out.tagString(0x0A, value, utf8Length);\n");
            }
            case "BytesValue" -> {
                javaType = "Bytes";
                content = """
                        final int bytesLength = (int) value.length();
                        final int content = bytesLength == 0
                                ? 0
                                : 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(bytesLength) + bytesLength;
                        """;
                write = guarded("bytesLength != 0", "out.tagBytes(0x0A, value);\n");
            }
            case "BoolValue" -> {
                javaType = "boolean";
                content = "final int content = value ? 2 : 0;\n";
                write = guarded("value", "out.tagVarint(8, 1);\n");
            }
            case "FloatValue" -> {
                javaType = "float";
                // the wrapped value is left out when its bits are zero, so -0.0 is written
                content = "final int bits = Float.floatToIntBits(value);\nfinal int content = bits == 0 ? 0 : 5;\n";
                write = guarded("bits != 0", "out.tagFixed32(0x0D, bits);\n");
            }
            case "DoubleValue" -> {
                javaType = "double";
                content = "final long bits = Double.doubleToLongBits(value);\nfinal int content = bits == 0 ? 0 : 9;\n";
                write = guarded("bits != 0", "out.tagFixed64(9, bits);\n");
            }
            default -> {
                final FieldType inner =
                        switch (field.messageType()) {
                            case "Int32Value" -> FieldType.INT32;
                            case "UInt32Value" -> FieldType.UINT32;
                            default -> FieldType.UINT64;
                        };
                javaType = javaType(inner);
                content = "final int content = value == 0 ? 0 : 1 + %s;%n".formatted(valueSize(inner, "value"));
                write = guarded("value != 0", writeField(inner, "8", "value"));
            }
        }
        final boolean delimited = javaType.equals("String") || javaType.equals("Bytes");
        parts.method(
                field,
                about(field, "Write the wrapper type")
                        + " The value is written, wrapped, even when it is the default; leave the field out for null."
                        + "\n\n@param value the wrapped value\n@return this writer",
                "Writer",
                name,
                (delimited ? "@NonNull " : "") + "final " + javaType + " value",
                "checkField(%d);%n".formatted(number)
                        + parts.oneofCheck(field)
                        + content
                        + "out.tagVarint(%s, content);%n".formatted(tag)
                        + write
                        + "return this;\n");
    }

    /** A singular or repeated message field, or a message alternative of a oneof. */
    private static void message(final SingleField field, final Parts parts) {
        final String name = baseName(field);
        final String type = field.javaFieldTypeBase();
        if (type.equals("Writer")) {
            throw new PbjCompilerException(("Message %s has writers, but field '%s' has a type named Writer, which"
                            + " clashes with the generated writer class")
                    .formatted(parts.modelClass(), field.name()));
        }
        final boolean repeated = field.repeated();
        final boolean inOneof = field.parent() != null;
        final String method = repeated ? "add" + upperName(field) : name;
        final int number = field.fieldNumber();
        final String check = (repeated ? "checkRepeatedField(%d);%n" : "checkField(%d);%n").formatted(number)
                + parts.oneofCheck(field);
        final String tag = tag(number, Common.TYPE_LENGTH_DELIMITED);
        final String element = repeated ? "the next element of" : "";
        if (parts.lookup().hasWriter(field.messageTypeModelPackage(), field.completeClassName())) {
            final String writerField = name + "Writer";
            parts.state().append("private %s.Writer %s;%n".formatted(type, writerField));
            parts.method(
                    field,
                    about(field, ("Open " + element).trim())
                            + " Write its fields to the returned writer, which is valid until the next call on this"
                            + " writer.\n\n@return the open nested message's writer",
                    type + ".Writer",
                    method,
                    "",
                    check
                            + guarded(writerField + " == null", "%s = new %s.Writer();%n".formatted(writerField, type))
                            + "openChild(%s, %d, %s);%n"
                                    .formatted(
                                            writerField,
                                            parts.lookup()
                                                    .writerLengthReserve(
                                                            field.messageTypeModelPackage(), field.completeClassName()),
                                            tag)
                            + "return %s;%n".formatted(writerField));
        }
        final String writeObject = writeObject(field, tag, "value", parts);
        final String body;
        if (inOneof) {
            // a null alternative is written as an empty message, as the model writes it
            body = "if (value == null) {\n"
                    + "out.tagVarint(%s, 0);%n".formatted(tag).indent(DEFAULT_INDENT)
                    + "} else {\n"
                    + writeObject.indent(DEFAULT_INDENT)
                    + "}\n";
        } else {
            body = repeated ? writeObject : guarded("value != null", writeObject);
        }
        parts.method(
                field,
                about(field, ("Write " + element).trim())
                        + (inOneof
                                ? " From a model object; null is written as an empty message, as the model does."
                                : repeated ? " From a model object." : " From a model object; not written if null.")
                        + "\n\n@param value the message\n@return this writer",
                "Writer",
                method,
                (repeated && !inOneof ? "@NonNull" : "@Nullable") + " final " + type + " value",
                check + body + "return this;\n");
        parts.method(
                field,
                about(field, ("Write " + element).trim())
                        + " From pre-encoded bytes, copied as they are: the caller guarantees they are the canonical"
                        + " encoding of a {@code " + type + "}, such as {@code " + type + ".PROTOBUF.toBytes(…)}."
                        + "\n\n@param encoded the message's encoding, without tag or length\n@return this writer",
                "Writer",
                method + "Raw",
                "@NonNull final Bytes encoded",
                check + "out.tagBytes(%s, encoded);%nreturn this;%n".formatted(tag));
    }

    /** A repeated scalar, enum, string or bytes field: packed, except strings and bytes. */
    private static void repeated(final SingleField field, final Parts parts) {
        final String name = baseName(field);
        final FieldType type = field.type();
        final int number = field.fieldNumber();
        final String tag = tag(number, Common.TYPE_LENGTH_DELIMITED);
        if (type == FieldType.STRING || type == FieldType.BYTES) {
            final boolean string = type == FieldType.STRING;
            final String check = "checkRepeatedField(%d);%n".formatted(number);
            parts.method(
                    field,
                    about(field, "Write the next element of") + "\n\n@param value the element\n@return this writer",
                    "Writer",
                    "add" + upperName(field),
                    "@NonNull final " + (string ? "String" : "Bytes") + " value",
                    check + "out.%s(%s, value);%nreturn this;%n".formatted(string ? "tagString" : "tagBytes", tag));
            parts.method(
                    field,
                    about(field, "Write the next element of")
                            + " From " + (string ? "UTF-8 bytes, which are checked to be well formed," : "the bytes")
                            + " in {@code value[offset, offset + length)}."
                            + "\n\n@param value the bytes\n@param offset where they start\n@param length how many there are"
                            + "\n@return this writer"
                            + (string
                                    ? "\n@throws IllegalArgumentException if the bytes are not well-formed UTF-8"
                                    : ""),
                    "Writer",
                    "add" + upperName(field),
                    "@NonNull final byte[] value, final int offset, final int length",
                    (string ? "ProtoWriteBuffer.checkUtf8(value, offset, length);\n" : "")
                            + check
                            + "out.tagBytes(%s, value, offset, length);%nreturn this;%n".formatted(tag));
            return;
        }
        // packed, as the model writes repeated scalars: in one call from an array, or element by element
        final String array;
        final String helper;
        final String packedWriter;
        final String encoding;
        switch (type) {
            case INT32, ENUM -> {
                array = "int[]";
                helper = "packedInt32";
                packedWriter = "PackedIntWriter";
                encoding = "PackedIntWriter.INT32";
            }
            case UINT32 -> {
                array = "int[]";
                helper = "packedUInt32";
                packedWriter = "PackedIntWriter";
                encoding = "PackedIntWriter.UINT32";
            }
            case SINT32 -> {
                array = "int[]";
                helper = "packedSInt32";
                packedWriter = "PackedIntWriter";
                encoding = "PackedIntWriter.SINT32";
            }
            case FIXED32, SFIXED32 -> {
                array = "int[]";
                helper = "packedFixed32";
                packedWriter = "PackedIntWriter";
                encoding = "PackedIntWriter.FIXED32";
            }
            case INT64, UINT64 -> {
                array = "long[]";
                helper = "packedInt64";
                packedWriter = "PackedLongWriter";
                encoding = "PackedLongWriter.INT64";
            }
            case SINT64 -> {
                array = "long[]";
                helper = "packedSInt64";
                packedWriter = "PackedLongWriter";
                encoding = "PackedLongWriter.SINT64";
            }
            case FIXED64, SFIXED64 -> {
                array = "long[]";
                helper = "packedFixed64";
                packedWriter = "PackedLongWriter";
                encoding = "PackedLongWriter.FIXED64";
            }
            case FLOAT -> {
                array = "float[]";
                helper = "packedFloat";
                packedWriter = "PackedFloatWriter";
                encoding = "";
            }
            case DOUBLE -> {
                array = "double[]";
                helper = "packedDouble";
                packedWriter = "PackedDoubleWriter";
                encoding = "";
            }
            case BOOL -> {
                array = "boolean[]";
                helper = "packedBool";
                packedWriter = "PackedBooleanWriter";
                encoding = "";
            }
            default -> throw new IllegalArgumentException("Not a packed type: " + field);
        }
        final String ordinals = type == FieldType.ENUM ? ", as the enum values' proto ordinals" : "";
        parts.method(
                field,
                about(field, "Write every element of") + " Packed, in one call" + ordinals
                        + ". Not written if {@code count} is 0, as the model does.\n\n@param values the values"
                        + "\n@param offset the first value\n@param count the number of values\n@return this writer",
                "Writer",
                name,
                "@NonNull final " + array + " values, final int offset, final int count",
                "checkField(%d);%n".formatted(number)
                        + guarded("count != 0", "out.%s(%s, values, offset, count);%n".formatted(helper, tag))
                        + "return this;\n");
        final String packedField = name + "Packed";
        parts.state().append("private %s %s;%n".formatted(packedWriter, packedField));
        parts.method(
                field,
                about(field, "Open") + " Packed, element by element" + ordinals + ": {@code add} each element to the"
                        + " returned writer, then {@code end()} it, or carry on writing to this writer. Not written if"
                        + " no element is added. The returned writer is valid until the next call on this writer."
                        + "\n\n@return the open field's writer",
                packedWriter,
                name,
                "",
                "checkField(%d);%n".formatted(number)
                        + guarded(
                                packedField + " == null",
                                "%s = new %s(%s);%n".formatted(packedField, packedWriter, encoding))
                        + "openPacked(%s, %s);%n".formatted(packedField, tag)
                        + "return %s;%n".formatted(packedField));
    }

    /**
     * A map field: each entry is written by one call, in the model's key order (ascending, in the keys' Java natural
     * order), which is checked. Message values are written through a child writer, or from objects.
     */
    private static void map(final MapField map, final Parts parts) {
        final String upper = upperName(map);
        final SingleField key = (SingleField) map.keyField();
        final SingleField value = (SingleField) map.valueField();
        final FieldType keyType = key.type();
        final FieldType valueType = value.type();
        final String keyJava = javaType(keyType);
        final String lastKey = baseName(map) + "LastKey";
        parts.state().append("private %s %s;%n".formatted(keyJava, lastKey));
        final int number = map.fieldNumber();
        final String tag = tag(number, Common.TYPE_LENGTH_DELIMITED);
        final String compare =
                switch (keyType) {
                    case STRING -> "key.compareTo(%s)".formatted(lastKey);
                    case BOOL -> "Boolean.compare(key, %s)".formatted(lastKey);
                    default -> "%s.compare(key, %s)".formatted(keyJava.equals("int") ? "Integer" : "Long", lastKey);
                };
        final String checkKey = """
                final boolean first = isFirstElement(%d);
                checkRepeatedField(%d);
                if (!first && %s <= 0) {
                    throw new IllegalStateException("The keys of map %s must be written in ascending order, each once,"
                            + " as the model writes them: " + key + " after " + %s);
                }
                %s = key;
                """.formatted(number, number, compare, map.name(), lastKey, lastKey);
        // the key field's size, tag included, and the code writing it; map entries always write the key
        final String keyLength =
                keyType == FieldType.STRING ? "final int keyLength = ProtoWriterTools.sizeOfStringNoTag(key);\n" : "";
        final String keySize = keyType == FieldType.STRING
                ? "final int keySize = 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(keyLength) + keyLength;\n"
                : "final int keySize = 1 + %s;%n".formatted(valueSize(keyType, "key"));
        final String keyTag = tag(1, keyType.wireType());
        final String writeKey = keyType == FieldType.STRING
                ? "out.tagString(%s, key, keyLength);%n".formatted(keyTag)
                : writeField(keyType, keyTag, "key");
        final String keyParameter = (keyType == FieldType.STRING ? "@NonNull " : "") + "final " + keyJava + " key";
        final String keyDoc = "\n\n@param key the entry's key, greater than the last one written";
        final String keyThrows = "\n@throws IllegalStateException if the key is not greater than the last one written";
        if (valueType == FieldType.MESSAGE) {
            final String type = value.javaFieldTypeBase();
            final String valueTag = tag(2, Common.TYPE_LENGTH_DELIMITED);
            if (parts.lookup().hasWriter(value.messageTypeModelPackage(), value.completeClassName())) {
                final String entryField = baseName(map) + "Entry";
                final String valueField = baseName(map) + "Value";
                parts.state()
                        .append("private MapEntryWriter %s;%nprivate %s.Writer %s;%n"
                                .formatted(entryField, type, valueField));
                parts.method(
                        map,
                        about(map, "Open the value of the next entry of")
                                + " Write its fields to the returned writer, which is valid until the next call on this"
                                + " writer." + keyDoc + "\n@return the open value's writer" + keyThrows,
                        type + ".Writer",
                        "put" + upper,
                        keyParameter,
                        checkKey
                                + guarded(
                                        entryField + " == null",
                                        "%s = new MapEntryWriter();%n%s = new %s.Writer();%n"
                                                .formatted(entryField, valueField, type))
                                + "openChild(%s, 2, %s);%n".formatted(entryField, tag)
                                + keyLength
                                + writeKey
                                + "return %s.openValue(%s, %d);%n"
                                        .formatted(
                                                entryField,
                                                valueField,
                                                parts.lookup()
                                                        .writerLengthReserve(
                                                                value.messageTypeModelPackage(),
                                                                value.completeClassName())));
            }
            final String codec = InlineFieldCodeGenerator.directCodecClass(value, parts.lookupHelper());
            parts.method(
                    map,
                    about(map, "Write the next entry of") + " From a model object." + keyDoc
                            + "\n@param value the entry's value\n@return this writer" + keyThrows,
                    "Writer",
                    "put" + upper,
                    keyParameter + ", @NonNull final " + type + " value",
                    checkKey
                            + keyLength
                            + keySize
                            + "final int valueSize = %s;%n"
                                    .formatted(
                                            codec != null
                                                    ? "value.protobufSize()"
                                                    : type + ".PROTOBUF.measureRecord(value)")
                            + "out.tagVarint(%s, keySize + 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(valueSize)"
                                    .formatted(tag)
                            + " + valueSize);\n"
                            + writeKey
                            + "out.tagVarint(%s, valueSize);%n".formatted(valueTag)
                            + "out.ensure(valueSize);\n"
                            + (codec != null
                                    ? "out.advance(%s.writeTo(value, out.array(), out.position(), out.position() + valueSize));%n"
                                            .formatted(codec)
                                    : "out.advance(%s.PROTOBUF.write(value, out.array(), out.position()));%n"
                                            .formatted(type))
                            + "return this;\n");
            return;
        }
        final String valueJava = javaType(valueType);
        final String valueTag = tag(2, valueType.wireType());
        // as the model writes map entries: float, double and enum values of zero are left out, others never are
        final boolean skipZero =
                valueType == FieldType.FLOAT || valueType == FieldType.DOUBLE || valueType == FieldType.ENUM;
        final String valueSize;
        final String writeValue;
        switch (valueType) {
            case STRING -> {
                valueSize = "final int valueLength = ProtoWriterTools.sizeOfStringNoTag(value);\n"
                        + "final int valueSize = 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(valueLength) + valueLength;\n";
                writeValue = "out.tagString(%s, value, valueLength);%n".formatted(valueTag);
            }
            case BYTES -> {
                valueSize = "final int valueLength = (int) value.length();\n"
                        + "final int valueSize = 1 + ProtoWriterTools.sizeOfUnsignedVarInt32(valueLength) + valueLength;\n";
                writeValue = "out.tagBytes(%s, value);%n".formatted(valueTag);
            }
            default -> {
                final String size = "1 + " + valueSize(valueType, "value");
                valueSize = "final int valueSize = %s;%n".formatted(skipZero ? "value == 0 ? 0 : " + size : size);
                writeValue = guarded(skipZero ? "value != 0" : null, writeField(valueType, valueTag, "value"));
            }
        }
        final boolean delimitedValue = valueType == FieldType.STRING || valueType == FieldType.BYTES;
        parts.method(
                map,
                about(map, "Write the next entry of") + keyDoc + "\n@param value the entry's value"
                        + (valueType == FieldType.ENUM ? ", as a proto ordinal" : "")
                        + "\n@return this writer" + keyThrows,
                "Writer",
                "put" + upper,
                keyParameter + ", " + (delimitedValue ? "@NonNull " : "") + "final " + valueJava + " value",
                checkKey
                        + keyLength
                        + keySize
                        + valueSize
                        + "out.tagVarint(%s, keySize + valueSize);%n".formatted(tag)
                        + writeKey
                        + writeValue
                        + "return this;\n");
    }

    // --------------------------------------------------------------------------------------------- writeFrom

    /** Generate the body of {@code writeFrom}: every field from the model's getters, in field-number order. */
    private static void writeFrom(final List<Field> sorted, final Parts parts) {
        final StringBuilder sb = parts.writeFrom();
        int i = 0;
        while (i < sorted.size()) {
            final Field field = sorted.get(i);
            final OneOfField oneOf = field.parent();
            if (oneOf == null) {
                sb.append("// [%d] - %s%n".formatted(field.fieldNumber(), field.name()));
                sb.append(writeFromField(field, "data." + field.nameCamelFirstLower() + "()", parts));
                i++;
                continue;
            }
            // a run of adjacent alternatives of the same oneof becomes one switch
            final StringBuilder cases = new StringBuilder();
            while (i < sorted.size() && sorted.get(i).parent() == oneOf) {
                final SingleField alternative = (SingleField) sorted.get(i);
                final String read = oneOf.unboxed()
                        ? "data.%s()".formatted(alternative.nameCamelFirstLower())
                        : "data.%s().as()".formatted(oneOf.nameCamelFirstLower());
                final String javaType = alternative.type() == FieldType.ENUM
                        ? alternative.javaFieldTypeBase()
                        : alternative.javaFieldType();
                final String body = "final %s value = %s;%n".formatted(javaType, read)
                        + writeFromField(alternative, "value", parts);
                cases.append("case %s -> {%n%s}%n"
                        .formatted(Common.camelToUpperSnake(alternative.name()), body.indent(DEFAULT_INDENT)));
                i++;
            }
            sb.append("switch (data.%s) {%n%s    default -> {}%n}%n"
                    .formatted(
                            oneOf.unboxed() ? oneOf.kindName() + "()" : oneOf.nameCamelFirstLower() + "().kind()",
                            cases.toString().indent(DEFAULT_INDENT)));
        }
    }

    /**
     * Code writing a nested message from a model object, not null, through its child writer: the object's size is
     * known, so the length prefix is reserved exactly and the message's bytes never move.
     */
    private static String writeFromChild(final SingleField field, final String value, final Parts parts) {
        final String writer = baseName(field) + "Writer";
        return (field.repeated() ? "checkRepeatedField(%d);%n" : "checkField(%d);%n").formatted(field.fieldNumber())
                + parts.oneofCheck(field)
                + guarded(writer + " == null", "%s = new %s.Writer();%n".formatted(writer, field.javaFieldTypeBase()))
                + "openChildSized(%s, %s.protobufSize(), %s);%n"
                        .formatted(writer, value, tag(field.fieldNumber(), Common.TYPE_LENGTH_DELIMITED))
                + "%s.writeFrom(%s).end();%n".formatted(writer, value);
    }

    /** Code writing one field, or oneof alternative, whose value is {@code read}, with the writer's methods. */
    private static String writeFromField(final Field field, final String read, final Parts parts) {
        final String name = baseName(field);
        final boolean inOneof = field.parent() != null;
        if (field instanceof MapField map) {
            final SingleField value = (SingleField) map.valueField();
            final boolean child = value.type() == FieldType.MESSAGE
                    && parts.lookup().hasWriter(value.messageTypeModelPackage(), value.completeClassName());
            return """
                    {
                        final var map = (PbjMap%s) %s;
                        final var keys = map.getSortedKeys();
                        for (int i = 0; i < keys.size(); i++) {
                            final var key = keys.get(i);
                            %s;
                        }
                    }
                    """.formatted(
                            map.javaGenericType(),
                            read,
                            child
                                    ? "put%s(key).writeFrom(map.get(key)).end()".formatted(upperName(field))
                                    : "put%s(key, map.get(key))".formatted(upperName(field)));
        }
        final SingleField single = (SingleField) field;
        final FieldType type = single.type();
        if (single.optionalValueType()) {
            return guarded("value != null", "%s(value);%n".formatted(name))
                    .transform(code -> inOneof
                            ? code
                            : "{%n    final var value = %s;%n%s}%n".formatted(read, code.indent(DEFAULT_INDENT)));
        }
        if (type == FieldType.MESSAGE) {
            final boolean child =
                    parts.lookup().hasWriter(single.messageTypeModelPackage(), single.completeClassName());
            if (single.repeated()) {
                return """
                        {
                            final var list = %s;
                            for (int i = 0; i < list.size(); i++) {
                            %s}
                        }
                        """.formatted(
                        read,
                        (child
                                        ? "final var item = list.get(i);\n" + writeFromChild(single, "item", parts)
                                        : "add%s(list.get(i));%n".formatted(upperName(field)))
                                .indent(DEFAULT_INDENT));
            }
            if (!child) {
                return "%s(%s);%n".formatted(name, read);
            }
            if (inOneof) {
                // a null alternative is written as an empty message, as the model writes it
                return "if (value == null) {%n    %s().end();%n} else {%n%s}%n"
                        .formatted(name, writeFromChild(single, "value", parts).indent(DEFAULT_INDENT));
            }
            return "{%n    final var value = %s;%n%s}%n"
                    .formatted(
                            read,
                            guarded("value != null", writeFromChild(single, "value", parts))
                                    .indent(DEFAULT_INDENT));
        }
        if (single.repeated()) {
            final String list =
                    type == FieldType.ENUM ? "data.%sProtoOrdinals()".formatted(field.nameCamelFirstLower()) : read;
            if (type == FieldType.STRING || type == FieldType.BYTES) {
                return """
                        {
                            final var list = %s;
                            for (int i = 0; i < list.size(); i++) {
                                add%s(list.get(i));
                            }
                        }
                        """.formatted(list, upperName(field));
            }
            return """
                    {
                        final var list = %s;
                        if (!list.isEmpty()) {
                            final var packed = %s();
                            for (int i = 0; i < list.size(); i++) {
                                packed.add(list.get(i));
                            }
                            packed.end();
                        }
                    }
                    """.formatted(list, name);
        }
        if (type == FieldType.ENUM) {
            return inOneof
                    ? "%sProtoOrdinal(value.protoOrdinal());%n".formatted(name)
                    : "%sProtoOrdinal(data.%sProtoOrdinal());%n".formatted(name, field.nameCamelFirstLower());
        }
        return "%s(%s);%n".formatted(name, read);
    }
}
