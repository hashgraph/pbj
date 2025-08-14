// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;
import static com.hedera.pbj.compiler.impl.Common.FIELD_INDENT;
import static com.hedera.pbj.compiler.impl.Common.camelToUpperSnake;
import static com.hedera.pbj.compiler.impl.Common.cleanDocStr;
import static com.hedera.pbj.compiler.impl.Common.cleanJavaDocComment;
import static com.hedera.pbj.compiler.impl.Common.getFieldsHashCode;
import static com.hedera.pbj.compiler.impl.Common.javaPrimitiveToObjectType;
import static com.hedera.pbj.compiler.impl.generators.EnumGenerator.EnumValue;
import static com.hedera.pbj.compiler.impl.generators.EnumGenerator.createEnum;
import static java.util.stream.Collectors.toMap;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.Field.FieldType;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.generators.protobuf.LazyGetProtobufSizeMethodGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Code generator that parses protobuf files and generates nice Java source for record files for each message type and
 * enum.
 */
@SuppressWarnings({"EscapedSpace", "StringConcatenationInLoop"})
public final class ModelGenerator implements Generator {

    private static final String NON_NULL_ANNOTATION = "@NonNull";

    /**
     * {@inheritDoc}
     *
     * <p>Generates a new model object.
     */
    @Override
    public void generate(
            final MessageDefContext msgDef, final JavaFileWriter writer, final ContextualLookupHelper lookupHelper)
            throws IOException {

        // The javaRecordName will be something like "AccountID".
        final var javaRecordName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final var schemaClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.SCHEMA, msgDef);
        // The modelPackage is the Java package to put the model class into.
        final String modelPackage = lookupHelper.getPackage(FileType.MODEL, msgDef);
        // The Javadoc "@Deprecated" tag, which is set if the protobuf schema says the field is deprecated
        String deprecated = "";
        // The list of fields, as defined in the protobuf schema & precomputed fields
        final List<Field> fields = new ArrayList<>();
        // The list of fields, as defined in the protobuf schema
        // The generated Java code for an enum field if OneOf is used
        final List<String> oneofEnums = new ArrayList<>();
        // The generated Java code for getters if OneOf is used
        final List<String> oneofGetters = new ArrayList<>();
        // The generated Java code for has methods for normal fields
        final List<String> hasMethods = new ArrayList<>();
        // The generated Java import statements. We'll build this up as we go.
        writer.addImport("com.hedera.pbj.runtime.*");
        writer.addImport("com.hedera.pbj.runtime.UnknownField");
        writer.addImport("com.hedera.pbj.runtime.io.*");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport("com.hedera.pbj.runtime.io.stream.*");
        writer.addImport("edu.umd.cs.findbugs.annotations.*");
        writer.addImport(lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef));
        writer.addImport("static " + lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef) + ".*");
        writer.addImport("java.util.Collections");
        writer.addImport("java.util.List");
        writer.addImport("com.hedera.pbj.runtime.hashing.XXH3_64");
        writer.addImport("com.hedera.pbj.runtime.hashing.XXH3_64.HashingWritableSequentialData");

        // Iterate over all the items in the protobuf schema
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages down below in generateClass()
            } else if (item.oneof() != null) { // process one ofs
                oneofGetters.addAll(generateCodeForOneOf(
                        lookupHelper, item, javaRecordName, writer::addImport, oneofEnums, fields));
            } else if (item.mapField() != null) { // process map fields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, false, false);
            } else if (item.field() != null && item.field().fieldName() != null) {
                generateCodeForField(lookupHelper, item, fields, writer::addImport, hasMethods);
            } else if (item.optionStatement() != null) {
                if ("deprecated".equals(item.optionStatement().optionName().getText())) {
                    deprecated = "@Deprecated ";
                } else {
                    System.err.printf(
                            "Unhandled Option: %s\n", item.optionStatement().getText());
                }
            } else if (item.reserved() == null) { // ignore reserved and warn about anything else
                System.err.printf("ModelGenerator Warning - Unknown element: %s  -- %s\n", item, item.getText());
            }
        }

        // collect all non precomputed fields
        final List<Field> fieldsNoPrecomputed = new ArrayList<>(fields);

        // add precomputed fields to fields
        fields.add(new SingleField(
                false,
                FieldType.FIXED64,
                -1,
                "$hashCode",
                null,
                false,
                null,
                null,
                null,
                null,
                "Computed hash code, manual input ignored.",
                false,
                null));
        fields.add(new SingleField(
                false,
                FieldType.FIXED32,
                -1,
                "$protobufEncodedSize",
                null,
                false,
                null,
                null,
                null,
                null,
                "Computed protobuf encoded size, manual input ignored.",
                false,
                null));

        // The javadoc comment to use for the model class, which comes **directly** from the protobuf schema,
        // but is cleaned up and formatted for use in JavaDoc.
        String docComment =
                (msgDef.docComment() == null || msgDef.docComment().getText().isBlank())
                        ? javaRecordName
                        : cleanJavaDocComment(msgDef.docComment().getText());
        if (docComment.endsWith("\n")) {
            docComment = docComment.substring(0, docComment.length() - 1);
        }
        final String javaDocComment = "/**\n * " + docComment.replaceAll("\n", "\n * ") + "\n */";

        // === Build Body Content
        String bodyContent = "";

        // static codec and default instance
        bodyContent += generateCodecFields(msgDef, lookupHelper, javaRecordName);
        bodyContent += "\n";

        // add class fields
        bodyContent += fields.stream()
                .map(field -> {
                    String fieldPrefix = field.fieldNumber() != -1 ? "Field " : "";
                    String fieldComment = field.comment();
                    if (fieldComment.contains("\n")) {
                        fieldComment = "/**\n * " + fieldPrefix + fieldComment.replaceAll("\n", "\n * ") + "\n */\n";
                    } else {
                        fieldComment = "/** " + fieldPrefix + fieldComment + " */\n";
                    }
                    return fieldComment
                            + "private "
                            + (field.fieldNumber() != -1 ? "final " : "")
                            + getFieldAnnotations(field)
                            + field.javaFieldType() + " " + field.nameCamelFirstLower()
                            + (field.fieldNumber() == -1 ? " = -1" : "")
                            + ";";
                })
                .collect(Collectors.joining("\n"))
                .indent(DEFAULT_INDENT);
        bodyContent += "\n";

        bodyContent += "private final List<UnknownField> $unknownFields;".indent(DEFAULT_INDENT);
        bodyContent += "\n";
        bodyContent += "\n";

        // constructors: w/o unknownFields, and with unknownFields
        bodyContent +=
                generateConstructor(javaRecordName, fields, false, fieldsNoPrecomputed, true, msgDef, lookupHelper);
        bodyContent += "\n";
        bodyContent +=
                generateConstructor(javaRecordName, fields, true, fieldsNoPrecomputed, true, msgDef, lookupHelper);
        bodyContent += "\n";

        // record style getters
        bodyContent += generateRecordStyleGetters(fieldsNoPrecomputed);
        bodyContent += "\n";

        bodyContent +=
                """
                /**
                 * Get an unmodifiable list of all unknown fields parsed from the original data, i.e. the fields
                 * that are unknown to the .proto model which generated this Java model class. The fields are sorted
                 * by their field numbers in an increasing order.
                 * <p>
                 * Note that by default, PBJ Codec discards unknown fields for performance reasons.
                 * The parse() method has to be invoked with `parseUnknownFields = true` in order to populate the
                 * unknown fields.
                 * <p>
                 * Also note that there may be multiple `UnknownField` items with the same field number
                 * in case a repeated field uses the unpacked wire format. It's up to the application
                 * to interpret these unknown fields correctly if necessary.
                 * <p>
                 * If the parsing of unknown fields was enabled when this model instance was parsed originally and
                 * the unknown fields were present, then a subsequent `Codec.write()` call will persist all the parsed
                 * unknown fields.
                 *
                 * @return a (potentially empty) list of unknown fields
                 */
                public @NonNull List<UnknownField> getUnknownFields() {
                    return $unknownFields == null ? Collections.EMPTY_LIST : $unknownFields;
                }
                """
                        .indent(DEFAULT_INDENT);
        bodyContent += "\n";

        // protobuf size method
        bodyContent +=
                LazyGetProtobufSizeMethodGenerator.generateLazyGetProtobufSize(fieldsNoPrecomputed, schemaClassName);
        bodyContent += "\n";

        // hashCode method
        bodyContent += generateHashCode(fieldsNoPrecomputed);
        bodyContent += "\n";

        // equals method
        bodyContent += generateEquals(fieldsNoPrecomputed, javaRecordName);

        final List<Field> comparableFields = filterComparableFields(msgDef, lookupHelper, fields);
        final boolean hasComparableFields = !comparableFields.isEmpty();
        if (hasComparableFields) {
            bodyContent += generateCompareTo(comparableFields, javaRecordName);
        }
        bodyContent += "\n";

        // toString method
        bodyContent += generateToString(javaRecordName, fieldsNoPrecomputed);
        bodyContent += "\n";

        // Has methods
        bodyContent += String.join("\n", hasMethods);
        bodyContent += "\n";

        // oneof getters
        bodyContent += String.join("\n    ", oneofGetters);
        bodyContent += "\n";

        // builder copy & new builder methods
        bodyContent = generateBuilderFactoryMethods(bodyContent, fieldsNoPrecomputed);
        bodyContent += "\n";

        // generate builder
        bodyContent += generateBuilder(msgDef, fieldsNoPrecomputed, lookupHelper);
        if (!oneofEnums.isEmpty()) bodyContent += "\n";

        // oneof enums
        bodyContent += String.join("\n    ", oneofEnums);

        // === Generate code
        generateClass(
                msgDef,
                writer,
                javaDocComment,
                deprecated,
                javaRecordName,
                bodyContent,
                hasComparableFields,
                lookupHelper);
    }

    /**
     * Generating method that assembles all the previously generated pieces together
     * @param writer the writer to append generated code to
     * @param javaDocComment the java doc comment to use for the code generation
     * @param deprecated the deprecated annotation to add
     * @param javaRecordName the name of the class
     * @param bodyContent the body content to use for the code generation
     * @return the generated code
     */
    @NonNull
    private void generateClass(
            final MessageDefContext msgDef,
            final JavaFileWriter writer,
            final String javaDocComment,
            final String deprecated,
            final String javaRecordName,
            final String bodyContent,
            final boolean isComparable,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String implementsComparable;
        if (isComparable) {
            implementsComparable = "implements Comparable<$javaRecordName> ";
        } else {
            implementsComparable = "";
        }

        final String staticModifier = Generator.isInner(msgDef) ? " static" : "";

        writer.addImport("com.hedera.pbj.runtime.Codec");
        writer.addImport("java.util.function.Consumer");
        writer.addImport("edu.umd.cs.findbugs.annotations.Nullable");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport("static java.util.Objects.requireNonNull");
        writer.addImport("static com.hedera.pbj.runtime.ProtoWriterTools.*");
        writer.addImport("static com.hedera.pbj.runtime.ProtoConstants.*");

        // spotless:off
        writer.append("""
                $javaDocComment$deprecated
                @java.lang.SuppressWarnings("ForLoopReplaceableByForEach")
                public final$staticModifier class $javaRecordName $implementsComparable{
                $bodyContent

                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated", deprecated)
                .replace("$staticModifier", staticModifier)
                .replace("$implementsComparable", implementsComparable)
                .replace("$javaRecordName", javaRecordName)
                .replace("$bodyContent", bodyContent));
        // spotless:on

        // Iterate over all the items in the protobuf schema
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), writer, lookupHelper);
            } else if (item.enumDef() != null) {
                EnumGenerator.generateEnum(item.enumDef(), writer, lookupHelper);
            }
        }

        writer.append("}");
    }

    /**
     * Generating method that generates getters for the record style accessors. Needed now we use class not record.
     *
     * @param fields the fields to use for the code generation
     * @return the generated code
     */
    private static String generateRecordStyleGetters(final List<Field> fields) {
        return fields.stream()
                .map(field -> {
                    String fieldComment = field.comment();
                    String fieldCommentLowerFirst =
                            fieldComment.substring(0, 1).toLowerCase() + fieldComment.substring(1);
                    return """
                    /**
                     * Get field $fieldCommentLowerFirst
                     *
                     * @return the value of the $fieldName field
                     */
                    public $fieldType $fieldName() {
                        return $fieldName;
                    }
                    """
                            .replace("$fieldCommentLowerFirst", fieldCommentLowerFirst)
                            .replace("$fieldName", field.nameCamelFirstLower())
                            .replace("$fieldType", field.javaFieldType())
                            .indent(DEFAULT_INDENT);
                })
                .collect(Collectors.joining("\n"));
    }

    /**
     * Returns a set of annotations for a given field.
     * @param field a field
     * @return an empty string, or a string with Java annotations ending with a space
     */
    private static String getFieldAnnotations(final Field field) {
        if (field.repeated()) return NON_NULL_ANNOTATION + " ";

        return switch (field.type()) {
            case MESSAGE -> "@Nullable ";
            case BYTES, STRING -> NON_NULL_ANNOTATION + " ";
            default -> "";
        };
    }

    /**
     * Filter the fields to only include those that are comparable
     * @param msgDef The message definition
     * @param lookupHelper The lookup helper
     * @param fields The fields to filter
     * @return the filtered fields
     */
    @NonNull
    private static List<Field> filterComparableFields(
            final MessageDefContext msgDef, final ContextualLookupHelper lookupHelper, final List<Field> fields) {
        final Map<String, Field> fieldByName = fields.stream().collect(toMap(Field::name, f -> f));
        final List<String> comparableFields = lookupHelper.getComparableFields(msgDef);
        return comparableFields.stream().map(fieldByName::get).collect(Collectors.toList());
    }

    /**
     * Generates the compareTo method
     *
     * @param fields                the fields to use for the code generation
     * @param javaRecordName        the name of the class
     * @return the generated code
     */
    @NonNull
    private static String generateCompareTo(final List<Field> fields, final String javaRecordName) {
        // spotless:off
        String bodyContent =
            """
            /**
             * Implementation of Comparable interface
             */
            @Override
            public int compareTo($javaRecordName thatObj) {
                if (thatObj == null) {
                    return 1;
                }
                int result = 0;
            """.replace("$javaRecordName", javaRecordName).indent(DEFAULT_INDENT);

        bodyContent += Common.getFieldsCompareToStatements(fields, "");

        bodyContent +=
                """
                // Treat null and empty lists as equal
                if ($unknownFields != null && !$unknownFields.isEmpty()) {
                    if (thatObj.$unknownFields == null || $unknownFields.isEmpty()) {
                        // This has unknown fields, that one doesn't. So we're greater:
                        return 1;
                    }
                    if ($unknownFields.size() > thatObj.$unknownFields.size()) {
                        // This has more
                        return 1;
                    } else if ($unknownFields.size() < thatObj.$unknownFields.size()) {
                        // That has more
                        return -1;
                    }
                    // Both are non-null and non-empty lists of the same size, and both are sorted in the same order
                    // (the sorting is the parser responsibility.)
                    // So we need to iterate over both the lists at once and compare each field:
                    for (int i = 0; i < $unknownFields.size(); i++) {
                        result = $unknownFields.get(i).protobufCompareTo(thatObj.$unknownFields.get(i));
                        if (result != 0) {
                            return result;
                        }
                    }
                } else if (thatObj.$unknownFields != null && !thatObj.$unknownFields.isEmpty()) {
                    // This doesn't have unknown fields, but that one has some. So they are greater:
                    return -1;
                }
                """.indent(DEFAULT_INDENT);

        bodyContent +=
            """
                return result;
            }
            """.indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Generates the equals method
     * @param fields the fields to use for the code generation
     * @param javaRecordName the name of the class
     * @return the generated code
     */
    @NonNull
    private static String generateEquals(final List<Field> fields, final String javaRecordName) {
        String equalsStatements = "";
        // Generate a call to private method that iterates through fields
        // and calculates the hashcode.
        equalsStatements = Common.getFieldsEqualsStatements(fields, equalsStatements);
        // spotless:off
        String bodyContent =
        """
        /**
         * Override the default equals method for
         */
        @Override
        public boolean equals(Object that) {
            if (that == null || this.getClass() != that.getClass()) {
                return false;
            }
            $javaRecordName thatObj = ($javaRecordName)that;
            if ($hashCode != -1 && thatObj.$hashCode != -1 && $hashCode != thatObj.$hashCode) {
                return false;
            }
        """.replace("$javaRecordName", javaRecordName).indent(DEFAULT_INDENT);

        bodyContent += equalsStatements.indent(DEFAULT_INDENT);
        bodyContent +=
        """
            // Treat null and empty lists as equal
            if ($unknownFields != null && !$unknownFields.isEmpty()) {
                if (thatObj.$unknownFields == null || $unknownFields.size() != thatObj.$unknownFields.size()) {
                    return false;
                }
                // Both are non-null and non-empty lists of the same size, and both are sorted in the same order
                // (the sorting is the parser responsibility.)
                // So the List.equals() is the most optimal way to compare them here.
                // It will simply call UnknownField.equals() for each element at the same index in both the lists:
                if (!$unknownFields.equals(thatObj.$unknownFields)) {
                    return false;
                }
            } else if (thatObj.$unknownFields != null && !thatObj.$unknownFields.isEmpty()) {
                return false;
            }
            return true;
        }""".indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Generates the hashCode method
     *
     * @param fields the fields to use for the code generation
     *
     * @return the generated code
     */
    @NonNull
    private static String generateHashCode(final List<Field> fields) {
        // Generate a call to private method that iterates through fields and calculates the hashcode
        final String statements = getFieldsHashCode(fields, "");
        // spotless:off
        String bodyContent =
            """
            /**
            * Override the default hashCode method for to make hashCode better distributed and follows protobuf rules
            * for default values. This is important for backward compatibility. This also lazy computes and caches the
            * hashCode for future calls. It is designed to be thread safe.
            */
            @Override
            public int hashCode() {
                return(int)hashCode64();
            }
            
            /**
            * Extended 64bit hashCode method for to make hashCode better distributed and follows protobuf rules
            * for default values. This is important for backward compatibility. This also lazy computes and caches the
            * hashCode for future calls. It is designed to be thread safe.
            */
            public long hashCode64() {
                // The $hashCode field is subject to a benign data race, making it crucial to ensure that any
                // observable result of the calculation in this method stays correct under any possible read of this
                // field. Necessary restrictions to allow this to be correct without explicit memory fences or similar
                // concurrency primitives is that we can ever only write to this field for a given Model object
                // instance, and that the computation is idempotent and derived from immutable state.
                // This is the same trick used in java.lang.String.hashCode() to avoid synchronization.
            
                if($hashCode == -1) {
                    final HashingWritableSequentialData hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
            """.indent(DEFAULT_INDENT);

        bodyContent += statements;

        bodyContent +=
            """
                    if ($unknownFields != null) {
                        for (int i = 0; i < $unknownFields.size(); i++) {
                            hashingStream.writeInt($unknownFields.get(i).hashCode());
                        }
                    }
                    $hashCode = hashingStream.computeHash();
                }
                return $hashCode;
            }
            """.indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Generates the toString method, based on how Java records generate toStrings
     *
     * @param fields the fields to use for the code generation
     *
     * @return the generated code
     */
    @NonNull
    private static String generateToString(final String modelClassName, final List<Field> fields) {
        // spotless:off
        String bodyContent =
            """
            /**
             * Override the default toString method for $modelClassName to match the format of a Java record.
             */
            @Override
            public String toString() {
                String $ufstr = null;
                if ($unknownFields != null && !$unknownFields.isEmpty()) {
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < $unknownFields.size(); i++) {
                        if (i > 0) sb.append(", ");
                        $unknownFields.get(i).printToString(sb);
                    }
                    $ufstr = sb.toString();
                }
                return "$modelClassName["
            """.replace("$modelClassName", modelClassName);
        // spotless:on
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            bodyContent +=
                    FIELD_INDENT + FIELD_INDENT + "+ \"" + f.nameCamelFirstLower() + "=\" + " + f.nameCamelFirstLower();
            if (i < fields.size() - 1) {
                bodyContent += " + \", \"";
            }
            bodyContent += "\n";
        }

        bodyContent += "        + ($ufstr == null ? \"\" : ";
        if (fields.isEmpty()) {
            bodyContent += "$ufstr";
        } else {
            bodyContent += "(\", \" + $ufstr)";
        }
        bodyContent += ")\n";

        // spotless:off
        bodyContent +=
            """
                    +"]";
            }
            """;
        // spotless:on
        return bodyContent.indent(DEFAULT_INDENT);
    }

    /**
     * Generates a pre-populated constructor for a class.
     * @param fields the fields to use for the code generation
     * @return the generated code
     */
    private static String generateConstructor(
            final String constructorName,
            final List<Field> fields,
            final boolean initUnknownFields,
            final List<Field> fieldsNoPrecomputed,
            final boolean shouldThrowOnOneOfNull,
            final MessageDefContext msgDef,
            final ContextualLookupHelper lookupHelper) {
        if (fields.isEmpty() && !initUnknownFields) {
            return "";
        }
        // spotless:off
        return """
                /**
                 * Create a pre-populated $constructorName.
                 * $constructorParamDocs
                 */
                public $constructorName($constructorParams$unknownFieldsParam) {
                    $unknownFieldsCode
            $constructorCode    }
            """
                .replace("$constructorParamDocs",fieldsNoPrecomputed.stream().map(field ->
                        "\n     * @param "+field.nameCamelFirstLower()+" "+
                                field.comment().replaceAll("\n", "\n     *         "+" ".repeat(field.nameCamelFirstLower().length()))
                ).collect(Collectors.joining(" ")))
                .replace("$constructorName", constructorName)
                .replace("$constructorParams",fieldsNoPrecomputed.stream().map(field ->
                        field.javaFieldType() + " " + field.nameCamelFirstLower()
                ).collect(Collectors.joining(", ")))
                .replace("$unknownFieldsParam", initUnknownFields
                        ? ((fieldsNoPrecomputed.isEmpty() ? "" : ", ") + "final List<UnknownField> $unknownFields")
                        : "")
                .replace("$unknownFieldsCode", "this.$unknownFields = " + (initUnknownFields
                        ? "$unknownFields == null ? null : Collections.unmodifiableList($unknownFields)"
                        : "null") + ";")
                .replace("$constructorCode",fieldsNoPrecomputed.stream().map(field -> {
                    StringBuilder sb = new StringBuilder();
                    if (shouldThrowOnOneOfNull && field instanceof OneOfField) {
                        sb.append(generateConstructorCodeForField(field)).append('\n');
                    }
                    switch (field.type()) {
                        case BYTES, STRING: {
                            sb.append("this.$name = $name != null ? $name : $default;"
                                    .replace("$name", field.nameCamelFirstLower())
                                    .replace("$default", getDefaultValue(field, msgDef, lookupHelper))
                            );
                            break;
                        }
                        case MAP: {
                            sb.append("this.$name = PbjMap.of($name);"
                                    .replace("$name", field.nameCamelFirstLower())
                            );
                            break;
                        }
                        default:
                            if (field.repeated()) {
                                sb.append("this.$name = $name == null ? Collections.emptyList() : $name;".replace(
                                        "$name", field.nameCamelFirstLower()));
                            } else {
                                sb.append("this.$name = $name;".replace("$name", field.nameCamelFirstLower()));
                            }
                            break;
                    }
                    return sb.toString();
                }).collect(Collectors.joining("\n")).indent(DEFAULT_INDENT * 2));
        // spotless:on
    }

    /**
     * Generates the constructor code for the class
     * @param f the field to use for the code generation
     * @return the generated code
     */
    private static String generateConstructorCodeForField(final Field f) {
        // spotless:off
        final StringBuilder sb = new StringBuilder("""
                                if ($fieldName == null) {
                                    throw new NullPointerException("Parameter '$fieldName' must be supplied and can not be null");
                                }""".replace("$fieldName", f.nameCamelFirstLower()));
        if (f instanceof final OneOfField oof) {
            for (final Field subField: oof.fields()) {
                if (subField.optionalValueType()) {
                    sb.append("""
       
                            // handle special case where protobuf does not have destination between a OneOf with optional
                            // value of empty vs an unset OneOf.
                            if ($fieldName.kind() == $fieldUpperNameOneOfType.$subFieldNameUpper && $fieldName.value() == null) {
                                $fieldName = new $className<>($fieldUpperNameOneOfType.UNSET, null);
                            }"""
                            .replace("$className", oof.className())
                            .replace("$fieldName", f.nameCamelFirstLower())
                            .replace("$fieldUpperName", f.nameCamelFirstUpper())
                            .replace("$subFieldNameUpper", camelToUpperSnake(subField.name()))
                    );
                }
            }
        }
        // spotless:on
        return sb.toString().indent(DEFAULT_INDENT);
    }

    /**
     * Generates codec fields for the calss
     * @param msgDef the message definition
     * @param lookupHelper the lookup helper
     * @param javaRecordName the name of the class
     * @return the generated code
     */
    @NonNull
    private static String generateCodecFields(
            final MessageDefContext msgDef, final ContextualLookupHelper lookupHelper, final String javaRecordName) {
        // spotless:off
        return """
                /** Protobuf codec for reading and writing in protobuf format */
                public static final Codec<$modelClass> PROTOBUF = new $qualifiedCodecClass();
                /** JSON codec for reading and writing in JSON format */
                public static final JsonCodec<$modelClass> JSON = new $qualifiedJsonCodecClass();
                /** Default instance with all fields set to default values */
                public static final $modelClass DEFAULT = newBuilder().build();
                """
                .replace("$modelClass", javaRecordName)
                .replace("$qualifiedCodecClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.CODEC, msgDef))
                .replace("$qualifiedJsonCodecClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.JSON_CODEC, msgDef))
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Generates accessor fields for the class
     * @param item message element context provided by the parser
     * @param fields the fields to use for the code generation
     * @param imports the imports to use for the code generation
     * @param hasMethods the has methods to use for the code generation
     */
    private static void generateCodeForField(
            final ContextualLookupHelper lookupHelper,
            final Protobuf3Parser.MessageElementContext item,
            final List<Field> fields,
            final Consumer<String> imports,
            final List<String> hasMethods) {
        final SingleField field = new SingleField(item.field(), lookupHelper);
        fields.add(field);
        field.addAllNeededImports(imports, true, false, false);
        // Note that repeated fields default to empty list, so technically they always have a non-null value,
        // and therefore the additional convenience methods, especially when they throw an NPE, don't make sense.
        // spotless:off
        if (field.type() == FieldType.MESSAGE && !field.repeated()) {
            hasMethods.add("""
                    /**
                     * Convenience method to check if the $fieldName has a value
                     *
                     * @return true of the $fieldName has a value
                     */
                    public boolean has$fieldNameUpperFirst() {
                        return $fieldName != null;
                    }
                    
                    /**
                     * Gets the value for $fieldName if it has a value, or else returns the default
                     * value for the type.
                     *
                     * @param defaultValue the default value to return if $fieldName is null
                     * @return the value for $fieldName if it has a value, or else returns the default value
                     */
                    public $javaFieldType $fieldNameOrElse(@NonNull final $javaFieldType defaultValue) {
                        return has$fieldNameUpperFirst() ? $fieldName : defaultValue;
                    }
                    
                    /**
                     * Gets the value for $fieldName if it has a value, or else throws an NPE.
                     * value for the type.
                     *
                     * @return the value for $fieldName if it has a value
                     * @throws NullPointerException if $fieldName is null
                     */
                    public @NonNull $javaFieldType $fieldNameOrThrow() {
                        return requireNonNull($fieldName, "Field $fieldName is null");
                    }
                    
                    /**
                     * Executes the supplied {@link Consumer} if, and only if, the $fieldName has a value
                     *
                     * @param ifPresent the {@link Consumer} to execute
                     */
                    public void if$fieldNameUpperFirst(@NonNull final Consumer<$javaFieldType> ifPresent) {
                        if (has$fieldNameUpperFirst()) {
                            ifPresent.accept($fieldName);
                        }
                    }
                    """
                    .replace("$fieldNameUpperFirst", field.nameCamelFirstUpper())
                    .replace("$javaFieldType", field.javaFieldType())
                    .replace("$fieldName", field.nameCamelFirstLower())
                    .indent(DEFAULT_INDENT)
            );
        }
        // spotless:on
    }

    /**
     * Generates the code related to the oneof field
     * @param lookupHelper the lookup helper
     * @param item message element context provided by the parser
     * @param javaRecordName the name of the class
     * @param imports the imports to use for the code generation
     * @param oneofEnums the oneof enums to use for the code generation
     * @param fields the fields to use for the code generation
     * @return the generated code
     */
    private static List<String> generateCodeForOneOf(
            final ContextualLookupHelper lookupHelper,
            final Protobuf3Parser.MessageElementContext item,
            final String javaRecordName,
            final Consumer<String> imports,
            final List<String> oneofEnums,
            final List<Field> fields) {
        final List<String> oneofGetters = new ArrayList<>();
        final var oneOfField = new OneOfField(item.oneof(), javaRecordName, lookupHelper);
        final var enumName = oneOfField.nameCamelFirstUpper() + "OneOfType";
        final int maxIndex = oneOfField.fields().getLast().fieldNumber();
        final Map<Integer, EnumValue> enumValues = new HashMap<>();
        // spotless:off
        for (final var field : oneOfField.fields()) {
            final String javaFieldType = javaPrimitiveToObjectType(field.javaFieldType());
            final String enumComment = cleanDocStr(field.comment())
                .replaceAll("[\t\s]*/\\*\\*","") // remove doc start indenting
                .replaceAll("\n[\t\s]+\\*","\n") // remove doc indenting
                .replaceAll("/\\*\\*","") //  remove doc start
                .replaceAll("\\*\\*/",""); //  remove doc end
            enumValues.put(field.fieldNumber(), new EnumValue(field.name(), field.deprecated(), enumComment));
            // generate getters for one ofs
            oneofGetters.add("""
                    /**
                     * Direct typed getter for one of field $fieldName.
                     *
                     * @return one of value or null if one of is not set or a different one of value
                     */
                    public @Nullable $javaFieldType $fieldName() {
                        return $oneOfField.kind() == $enumName.$enumValue ? ($javaFieldType)$oneOfField.value() : null;
                    }
                    
                    /**
                     * Convenience method to check if the $oneOfField has a one-of with type $enumValue
                     *
                     * @return true of the one of kind is $enumValue
                     */
                    public boolean has$fieldNameUpperFirst() {
                        return $oneOfField.kind() == $enumName.$enumValue;
                    }
                    
                    /**
                     * Gets the value for $fieldName if it has a value, or else returns the default
                     * value for the type.
                     *
                     * @param defaultValue the default value to return if $fieldName is null
                     * @return the value for $fieldName if it has a value, or else returns the default value
                     */
                    public $javaFieldType $fieldNameOrElse(@NonNull final $javaFieldType defaultValue) {
                        return has$fieldNameUpperFirst() ? $fieldName() : defaultValue;
                    }
                    
                    /**
                     * Gets the value for $fieldName if it was set, or throws a NullPointerException if it was not set.
                     *
                     * @return the value for $fieldName if it has a value
                     * @throws NullPointerException if $fieldName is null
                     */
                    public @NonNull $javaFieldType $fieldNameOrThrow() {
                        return requireNonNull($fieldName(), "Field $fieldName is null");
                    }
                    """
                    .replace("$fieldNameUpperFirst",field.nameCamelFirstUpper())
                    .replace("$fieldName",field.nameCamelFirstLower())
                    .replace("$javaFieldType",javaFieldType)
                    .replace("$oneOfField",oneOfField.nameCamelFirstLower())
                    .replace("$enumName",enumName)
                    .replace("$enumValue",camelToUpperSnake(field.name()))
                    .indent(DEFAULT_INDENT)
            );
            if (field.type() == FieldType.MESSAGE || field.type() == FieldType.ENUM) {
                field.addAllNeededImports(imports, true, false, false);
            }
        }
        final String enumComment = """
                            /**
                             * Enum for the type of "%s" oneof value
                             */"""
                        .formatted(oneOfField.name());
        // spotless:on
        final String enumString = createEnum(enumComment, "", enumName, maxIndex, enumValues, true)
                .indent(DEFAULT_INDENT * 2);
        oneofEnums.add(enumString);
        fields.add(oneOfField);
        imports.accept("com.hedera.pbj.runtime.*");
        return oneofGetters;
    }

    /**
     * Generates the builder methods for the model class
     *
     * @param bodyContent the body content to append to
     * @param fields the fields to use for the code generation
     * @return the body content with new code appended
     */
    @NonNull
    private static String generateBuilderFactoryMethods(String bodyContent, final List<Field> fields) {
        // spotless:off
        bodyContent +=
            """
            /**
             * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
             * model object.
             *
             * @return a pre-populated builder
             */
            public Builder copyBuilder() {
                return new Builder(%s$unknownFieldsArg);
            }
            
            /**
             * Return a new builder for building a model object. This is just a shortcut for <code>new Model.Builder()</code>.
             *
             * @return a new builder
             */
            public static Builder newBuilder() {
                return new Builder();
            }
            """
            .formatted(fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
            .replace("$unknownFieldsArg", (fields.isEmpty() ? "" : ", ") + "$unknownFields")
            .indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Generates the builder class methods
     *
     * @param builderMethods the builder methods code to append to
     * @param msgDef the message definition
     * @param field the field to generate method for
     * @param lookupHelper the lookup helper
     */
    private static void generateBuilderMethods(
            final List<String> builderMethods,
            final MessageDefContext msgDef,
            final Field field,
            final ContextualLookupHelper lookupHelper) {
        final String prefix, postfix, fieldToSet;
        final String fieldAnnotations = getFieldAnnotations(field);
        final OneOfField parentOneOfField = field.parent();
        final String fieldName = field.nameCamelFirstLower();
        if (parentOneOfField != null) {
            final String oneOfEnumValue = parentOneOfField.getEnumClassRef() + "." + camelToUpperSnake(field.name());
            prefix = " new %s<>(".formatted(parentOneOfField.className()) + oneOfEnumValue + ",";
            postfix = ")";
            fieldToSet = parentOneOfField.nameCamelFirstLower();
        } else if (fieldAnnotations.contains(NON_NULL_ANNOTATION)) {
            prefix = "";
            postfix = " != null ? " + fieldName + " : " + getDefaultValue(field, msgDef, lookupHelper);
            fieldToSet = fieldName;
        } else {
            prefix = "";
            postfix = "";
            fieldToSet = fieldName;
        }
        // spotless:off
        builderMethods.add("""
                /**
                 * $fieldDoc
                 *
                 * @param $fieldName value to set
                 * @return builder to continue building with
                 */
                public Builder $fieldName($fieldAnnotations$fieldType $fieldName) {
                    this.$fieldToSet = $prefix$fieldName$postfix;
                    return this;
                }"""
                .replace("$fieldDoc", field.comment()
                        .replaceAll("\n", "\n * "))
                .replace("$fieldName", fieldName)
                .replace("$fieldToSet", fieldToSet)
                .replace("$prefix", prefix)
                .replace("$postfix", postfix)
                .replace("$fieldAnnotations", fieldAnnotations)
                .replace("$fieldType", field.javaFieldType())
                .indent(DEFAULT_INDENT)
        );
        // add nice method for simple message fields so can just set using un-built builder
        if (field.type() == Field.FieldType.MESSAGE && !field.optionalValueType() && !field.repeated()) {
            builderMethods.add(
                    """
                        /**
                         * $fieldDoc
                         *
                         * @param builder A pre-populated builder
                         * @return builder to continue building with
                         */
                        public Builder $fieldName($messageClass.Builder builder) {
                            this.$fieldToSet =$prefix builder.build() $postfix;
                            return this;
                        }"""
                    .replace("$messageClass",field.messageType())
                    .replace("$fieldDoc",field.comment()
                            .replaceAll("\n", "\n * "))
                    .replace("$fieldName", fieldName)
                    .replace("$fieldToSet",fieldToSet)
                    .replace("$prefix",prefix)
                    .replace("$postfix",postfix)
                    .replace("$fieldType",field.javaFieldType())
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        }

        // add nice method for message fields with list types for varargs
        if (field.repeated()) {
            // Need to re-define the prefix and postfix for repeated fields because they don't use `values` directly
            // but wrap it in List.of(values) instead, so the simple definitions above don't work here.
            final String repeatedPrefix;
            final String repeatedPostfix;
            // spotless:off
            if (parentOneOfField != null) {
                repeatedPrefix = prefix + " values == null ? " + getDefaultValue(field, msgDef, lookupHelper) + " : ";
                repeatedPostfix = postfix;
            } else if (fieldAnnotations.contains(NON_NULL_ANNOTATION)) {
                repeatedPrefix = "values == null ? " + getDefaultValue(field, msgDef, lookupHelper) + " : ";
                repeatedPostfix = "";
            } else {
                repeatedPrefix = prefix;
                repeatedPostfix = postfix;
            }
            builderMethods.add("""
                        /**
                         * $fieldDoc
                         *
                         * @param values varargs value to be built into a list
                         * @return builder to continue building with
                         */
                        public Builder $fieldName($baseType ... values) {
                            this.$fieldToSet = $repeatedPrefix List.of(values) $repeatedPostfix;
                            return this;
                        }"""
                    .replace("$baseType",field.javaFieldType().substring("List<".length(),field.javaFieldType().length()-1))
                    .replace("$fieldDoc",field.comment()
                            .replaceAll("\n", "\n * "))
                    .replace("$fieldName", fieldName)
                    .replace("$fieldToSet",fieldToSet)
                    .replace("$fieldType",field.javaFieldType())
                    .replace("$repeatedPrefix",repeatedPrefix)
                    .replace("$repeatedPostfix",repeatedPostfix)
                    .indent(DEFAULT_INDENT)
            );
            // spotless:on
        }
    }

    /**
     * Generates the builder for the class
     * @param msgDef the message definition
     * @param fields the fields to use for the code generation
     * @param lookupHelper the lookup helper
     * @return the generated code
     */
    private static String generateBuilder(
            final MessageDefContext msgDef, final List<Field> fields, final ContextualLookupHelper lookupHelper) {
        final String javaRecordName = msgDef.messageName().getText();
        final List<String> builderMethods = new ArrayList<>();
        for (final Field field : fields) {
            if (field.type() == Field.FieldType.ONE_OF) {
                final OneOfField oneOfField = (OneOfField) field;
                for (final Field subField : oneOfField.fields()) {
                    generateBuilderMethods(builderMethods, msgDef, subField, lookupHelper);
                }
            } else {
                generateBuilderMethods(builderMethods, msgDef, field, lookupHelper);
            }
        }
        // spotless:off
        return """
            /**
             * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
             * paths use the constructor directly.
             */
            public static final class Builder {
                $fields;
                private final List<UnknownField> $unknownFields;
        
                /**
                 * Create an empty builder
                 */
                public Builder() { $unknownFields = null; }
            
            $prePopulatedBuilder
            $prePopulatedWithUnknownFieldsBuilder
                /**
                 * Build a new model record with data set on builder
                 *
                 * @return new model record with data set
                 */
                public $javaRecordName build() {
                    return new $javaRecordName($recordParams);
                }

            $builderMethods}"""
                .replace("$fields", fields.stream().map(field ->
                        getFieldAnnotations(field)
                                + "private " + field.javaFieldType()
                                + " " + field.nameCamelFirstLower()
                                + " = " + getDefaultValue(field, msgDef, lookupHelper)
                        ).collect(Collectors.joining(";\n    ")))
                .replace("$prePopulatedBuilder", generateConstructor("Builder", fields, false, fields, false, msgDef, lookupHelper))
                .replace("$prePopulatedWithUnknownFieldsBuilder", generateConstructor("Builder", fields, true, fields, false, msgDef, lookupHelper))
                .replace("$javaRecordName",javaRecordName)
                .replace("$recordParams",fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
                .replace("$builderMethods", String.join("\n", builderMethods))
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Gets the default value for the field
     * @param field the field to use for the code generation
     * @param msgDef the message definition
     * @param lookupHelper the lookup helper
     * @return the generated code
     */
    private static String getDefaultValue(
            final Field field, final MessageDefContext msgDef, final ContextualLookupHelper lookupHelper) {
        if (field.type() == Field.FieldType.ONE_OF) {
            return lookupHelper.getFullyQualifiedMessageClassname(FileType.CODEC, msgDef) + "." + field.javaDefault();
        } else {
            return field.javaDefault();
        }
    }
}
