// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Common functions and constants for code generation
 */
@SuppressWarnings({"DuplicatedCode", "EscapedSpace", "StringConcatenationInLoop"})
public final class Common {
    /** The indent for fields, default 4 spaces */
    public static final String FIELD_INDENT = " ".repeat(4);

    public static final int DEFAULT_INDENT = 4;
    /** Wire format code for var int */
    public static final int TYPE_VARINT = 0;
    /** Wire format code for fixed 64bit number */
    public static final int TYPE_FIXED64 = 1;
    /** Wire format code for length delimited, all the complex types */
    public static final int TYPE_LENGTH_DELIMITED = 2;
    /** Wire format code for fixed 32bit number */
    public static final int TYPE_FIXED32 = 5;
    /** Number of bits used to represent the tag type */
    static final int TAG_TYPE_BITS = 3;

    private static final Pattern COMPARABLE_PATTERN = Pattern.compile("implements Comparable<\\w+>\\s*\\{");

    /**
     * Makes a tag value given a field number and wire type.
     *
     * @param wireType the wire type part of tag
     * @param fieldNumber the field number part of tag
     *
     * @return packed encoded tag
     */
    public static int getTag(final int wireType, final int fieldNumber) {
        return (fieldNumber << TAG_TYPE_BITS) | wireType;
    }

    /**
     * Make sure first character of a string is upper case
     *
     * @param name string input who's first character can be upper or lower case
     *
     * @return name with first character converted to upper case
     */
    public static String capitalizeFirstLetter(String name) {
        if (!name.isEmpty()) {
            if (name.chars().allMatch(Character::isUpperCase)) {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
            } else {
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        }
        return name;
    }

    /**
     * Convert names like "hello_world" to "HelloWorld" or "helloWorld" depending on firstUpper. Also handles special
     * case
     * like "HELLO_WORLD" to same output as "hello_world", while "HelloWorld_Two" still becomes "helloWorldTwo".
     *
     * @param name input name in snake case
     * @param firstUpper if true then first char is upper case otherwise it is lower
     *
     * @return out name in camel case
     */
    @NonNull
    public static String snakeToCamel(@NonNull String name, boolean firstUpper) {
        final String out = Arrays.stream(name.split("_"))
                .map(Common::capitalizeFirstLetter)
                .collect(Collectors.joining(""));
        return (firstUpper ? Character.toUpperCase(out.charAt(0)) : Character.toLowerCase(out.charAt(0)))
                + out.substring(1);
    }

    /**
     * Convert a camel case name to upper case snake case
     *
     * @param name the input name in camel case
     *
     * @return output name in upper snake case
     */
    public static String camelToUpperSnake(String name) {
        // check if already camel upper
        if (name.chars().allMatch(c -> Character.isUpperCase(c) || Character.isDigit(c) || c == '_')) {
            return name;
        }
        // check if already has underscores, then just capitalize
        if (name.contains("_")) {
            return name.toUpperCase();
        }
        // else convert
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                buf.append("_");
                buf.append(c);
            } else {
                buf.append(Character.toUpperCase(c));
            }
        }
        // fix special case for capital ID
        return buf.toString().replaceAll("_I_D", "_ID");
    }

    /**
     * Build a clean java doc comment for a field
     *
     * @param fieldNumber The field proto number
     * @param docContext The parsed field comment contact
     *
     * @return clean comment
     */
    public static String buildCleanFieldJavaDoc(int fieldNumber, Protobuf3Parser.DocCommentContext docContext) {
        final String cleanedComment = docContext == null ? "" : cleanJavaDocComment(docContext.getText());
        final String fieldNumComment = "<b>(" + fieldNumber + ")</b> ";
        return fieldNumComment + cleanedComment;
    }

    /**
     * Build a clean java doc comment for an oneof field
     *
     * @param fieldNumbers The field proto numbers for all fields in oneof
     * @param docContext The parsed field comment contact
     *
     * @return clean comment
     */
    public static String buildCleanFieldJavaDoc(
            List<Integer> fieldNumbers, Protobuf3Parser.DocCommentContext docContext) {
        final String cleanedComment = docContext == null ? "" : cleanJavaDocComment(docContext.getText());
        final String fieldNumComment =
                "<b>(" + fieldNumbers.stream().map(Objects::toString).collect(Collectors.joining(", ")) + ")</b> ";
        return fieldNumComment + cleanedComment;
    }

    /**
     * Clean up a java doc style comment removing all the "*" etc.
     *
     * @param fieldComment raw Java doc style comment
     *
     * @return clean multi-line content of the comment
     */
    public static String cleanJavaDocComment(String fieldComment) {
        return cleanDocStr(
                fieldComment
                        .replaceAll("/\\*\\*[\n\r\s\t]*\\*[\t\s]*|[\n\r\s\t]*\\*/", "") // remove java doc
                        .replaceAll("(^|\n)\s+\\*(\s+|\n|$)", "\n") // remove indenting and *
                        .replaceAll(
                                "(^|\n)\s+\\*(\s+|\n|$)",
                                "\n\n") // remove lines starting with * as these were empty lines
                        .replaceAll("/\\*\\*", "") // remove indenting and /** at beginning of comment.
                        .trim() // Remove leading and trailing spaces.
                );
    }

    /**
     * Clean a string so that it can be included in JavaDoc. Does things like replace unsupported HTML tags.
     *
     * @param docStr The string to clean
     *
     * @return cleaned output
     */
    @SuppressWarnings("RegExpSingleCharAlternation")
    public static String cleanDocStr(String docStr) {
        return docStr.replaceAll("<(/?)tt>", "<$1code>") // tt tags are not supported in javadoc
                .replaceAll(" < ", " &lt; ") // escape loose less than
                .replaceAll(" > ", " &gt; ") // escape loose less than
                .replaceAll(" & ", " &amp; ") //
                .replaceAll("<p>([^<]*?)</p>", "%%%%%$1%%%%") // replace closed paragraphs temporarily
                .replaceAll("<p>((\\s|\\n)*?)($|<[^>]+>)", "$1$2$3") // remove <p> at end of paragraph
                .replaceAll(
                        "<p>((.|\\n)*?)([\\s\\n]*)(%%%%%|<p>|\\n@\\w+ |$|<[^>]+>)",
                        "<p>$1</p>$3$4") // clean up loose paragraphs
                // Do second pass as we can miss some <p> that were caught as closers in first pass
                .replaceAll("<p>([^<]*?)</p>", "%%%%%$1%%%%") // replace closed paragraphs temporarily
                .replaceAll(
                        "<p>((.|\\n)*?)([\\s\\n]*)(%%%%%|<p>|\\n@\\w+ |$|<[^>]+>)",
                        "<p>$1</p>$3$4") // clean up loose paragraphs
                // restore completed paragraphs
                .replaceAll("%%%%%", "<p>") // replace back to paragraphs
                .replaceAll("%%%%", "</p>"); // replace back to paragraphs
    }

    /**
     * Convert a field type like "long" to the Java object wrapper type "Long", or pass though if not java primitive
     *
     * @param primitiveFieldType java field type like "int" etc
     *
     * @return java object wrapper type like "Integer" or pass though
     */
    public static String javaPrimitiveToObjectType(String primitiveFieldType) {
        return switch (primitiveFieldType) {
            case "boolean" -> "Boolean";
            case "int" -> "Integer";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            default -> primitiveFieldType;
        };
    }

    /**
     * Recursively calculates the hashcode for a message fields.
     *
     * @param fields The fields of this object.
     * @param generatedCodeSoFar The accumulated hash code so far.
     *
     * @return The generated code for getting the hashCode value.
     */
    public static String getFieldsHashCode(final List<Field> fields, String generatedCodeSoFar) {
        for (Field f : fields) {
            if (f.parent() != null) {
                final OneOfField oneOfField = f.parent();
                generatedCodeSoFar += getFieldsHashCode(oneOfField.fields(), generatedCodeSoFar);
            } else if (f.optionalValueType()) {
                generatedCodeSoFar = getPrimitiveWrapperHashCodeGeneration(generatedCodeSoFar, f);
            } else if (f.repeated()) {
                generatedCodeSoFar = getRepeatedHashCodeGeneration(generatedCodeSoFar, f);
            } else {
                if (f.type() == Field.FieldType.FIXED32
                        || f.type() == Field.FieldType.INT32
                        || f.type() == Field.FieldType.SFIXED32
                        || f.type() == Field.FieldType.SINT32
                        || f.type() == Field.FieldType.UINT32) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                result = 31 * result + Integer.hashCode($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FIXED64
                        || f.type() == Field.FieldType.INT64
                        || f.type() == Field.FieldType.SFIXED64
                        || f.type() == Field.FieldType.SINT64
                        || f.type() == Field.FieldType.UINT64) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                result = 31 * result + Long.hashCode($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BOOL) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Boolean.hashCode($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Float.hashCode($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Double.hashCode($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BYTES) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                               result = 31 * result + $fieldName.hashCode();
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.ENUM) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                               result = 31 * result + Integer.hashCode($fieldName.protoOrdinal());
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.MAP) {
                    generatedCodeSoFar += getMapHashCodeGeneration(generatedCodeSoFar, f);
                } else if (f.type() == Field.FieldType.STRING || f.parent() == null) { // process sub message
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                               result = 31 * result + $fieldName.hashCode();
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else {
                    throw new RuntimeException("Unexpected field type for getting HashCode - "
                            + f.type().toString());
                }
            }
        }
        return generatedCodeSoFar.indent(DEFAULT_INDENT * 3);
    }

    /**
     * Get the hashcode codegen for a optional field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getPrimitiveWrapperHashCodeGeneration(String generatedCodeSoFar, Field f) {
        switch (f.messageType()) {
            case "StringValue" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + $fieldName.hashCode();
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "BoolValue" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + Boolean.hashCode($fieldName);
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "Int32Value", "UInt32Value" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + Integer.hashCode($fieldName);
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "Int64Value", "UInt64Value" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + Long.hashCode($fieldName);
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "FloatValue" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + Float.hashCode($fieldName);
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "DoubleValue" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + Double.hashCode($fieldName);
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            case "BytesValue" -> generatedCodeSoFar +=
                    ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        result = 31 * result + ($fieldName == null ? 0 : $fieldName.hashCode());
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
        }
        return generatedCodeSoFar;
    }

    /**
     * Get the hashcode codegen for a repeated field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getRepeatedHashCodeGeneration(String generatedCodeSoFar, Field f) {
        generatedCodeSoFar +=
                ("""
                java.util.List list$$fieldName = $fieldName;
                if (list$$fieldName != null) {
                    for (Object o : list$$fieldName) {
                        if (o != null) {
                            result = 31 * result + o.hashCode();
                        } else {
                            result = 31 * result;
                        }
                   }
                }
                """)
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }

    /**
     * Get the hashcode codegen for a map field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getMapHashCodeGeneration(String generatedCodeSoFar, final Field f) {
        generatedCodeSoFar +=
                ("""
                for (Object k : ((PbjMap) $fieldName).getSortedKeys()) {
                    if (k != null) {
                        result = 31 * result + k.hashCode();
                    } else {
                        result = 31 * result;
                    }
                    Object v = $fieldName.get(k);
                    if (v != null) {
                        result = 31 * result + v.hashCode();
                    } else {
                        result = 31 * result;
                    }
                }
                """)
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }

    /**
     * Recursively calculates `equals` statement for a message fields.
     *
     * @param fields The fields of this object.
     * @param generatedCodeSoFar The accumulated hash code so far.
     *
     * @return The generated code for getting the object equality
     */
    public static String getFieldsEqualsStatements(final List<Field> fields, String generatedCodeSoFar) {
        for (Field f : fields) {
            if (f.parent() != null) {
                final OneOfField oneOfField = f.parent();
                generatedCodeSoFar += getFieldsEqualsStatements(oneOfField.fields(), generatedCodeSoFar);
            }

            if (f.optionalValueType()) {
                generatedCodeSoFar = getPrimitiveWrapperEqualsGeneration(generatedCodeSoFar, f);
            } else if (f.repeated()) {
                generatedCodeSoFar = getRepeatedEqualsGeneration(generatedCodeSoFar, f);
            } else {
                f.nameCamelFirstLower();
                if (f.type() == Field.FieldType.FIXED32
                        || f.type() == Field.FieldType.INT32
                        || f.type() == Field.FieldType.SFIXED32
                        || f.type() == Field.FieldType.SINT32
                        || f.type() == Field.FieldType.UINT32) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FIXED64
                        || f.type() == Field.FieldType.INT64
                        || f.type() == Field.FieldType.SFIXED64
                        || f.type() == Field.FieldType.SINT64
                        || f.type() == Field.FieldType.UINT64) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BOOL) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.STRING
                        || f.type() == Field.FieldType.BYTES
                        || f.type() == Field.FieldType.ENUM
                        || f.type() == Field.FieldType.MAP
                        || f.parent() == null /* Process a sub-message */) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName == null && thatObj.$fieldName != null) {
                                return false;
                            }
                            if ($fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
                                return false;
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else {
                    throw new IllegalArgumentException("Unexpected field type for getting Equals - "
                            + f.type().toString());
                }
            }
        }
        return generatedCodeSoFar.indent(DEFAULT_INDENT);
    }

    /**
     * Get the equals codegen for a optional field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the equals code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getPrimitiveWrapperEqualsGeneration(String generatedCodeSoFar, Field f) {
        switch (f.messageType()) {
            case "StringValue",
                    "BoolValue",
                    "Int32Value",
                    "UInt32Value",
                    "Int64Value",
                    "UInt64Value",
                    "FloatValue",
                    "DoubleValue",
                    "BytesValue" -> generatedCodeSoFar +=
                    ("""
                    if (this.$fieldName == null && thatObj.$fieldName != null) {
                        return false;
                    }
                    if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
                        return false;
                    }
                    """)
                            .replace("$fieldName", f.nameCamelFirstLower());
            default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
        }
        return generatedCodeSoFar;
    }

    /**
     * Get the equals codegen for a repeated field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the equals code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getRepeatedEqualsGeneration(String generatedCodeSoFar, Field f) {
        generatedCodeSoFar +=
                ("""
                if (this.$fieldName == null && thatObj.$fieldName != null) {
                    return false;
                }

                if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
                    return false;
                }
                """)
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }

    /**
     * Generate the compareTo method content for the provided fields
     *
     * @param fields The fields of this object.
     * @param generatedCodeSoFar the generated code so far (non-empty in case of nested objects)
     * @param destinationSrcDir a directory where the previously generated code is saved
     *
     * @return The generated code for compareTo method body
     */
    public static String getFieldsCompareToStatements(
            final List<Field> fields, String generatedCodeSoFar, File destinationSrcDir) {
        for (Field f : fields) {
            if (f.optionalValueType()) {
                generatedCodeSoFar += getPrimitiveWrapperCompareToGeneration(f);
            } else if (f.repeated()) {
                throw new UnsupportedOperationException("Repeated fields are not supported in compareTo method");
            } else {
                if (f.type() == Field.FieldType.FIXED32
                        || f.type() == Field.FieldType.INT32
                        || f.type() == Field.FieldType.SFIXED32
                        || f.type() == Field.FieldType.SINT32) {
                    generatedCodeSoFar +=
                            """
                            result = Integer.compare($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.UINT32) {
                    generatedCodeSoFar +=
                            """
                            result = Integer.compareUnsigned($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());

                } else if (f.type() == Field.FieldType.FIXED64
                        || f.type() == Field.FieldType.INT64
                        || f.type() == Field.FieldType.SFIXED64
                        || f.type() == Field.FieldType.SINT64) {
                    generatedCodeSoFar +=
                            """
                            result = Long.compare($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.UINT64) {
                    generatedCodeSoFar +=
                            """
                            result = Long.compareUnsigned($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BOOL) {
                    generatedCodeSoFar +=
                            """
                            result = Boolean.compare($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            """
                            result = Float.compare($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            """
                            result = Double.compare($fieldName, thatObj.$fieldName);
                            if (result != 0) {
                                 return result;
                            }
                            """
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.STRING
                        || f.type() == Field.FieldType.BYTES
                        || f.type() == Field.FieldType.ENUM) {
                    generatedCodeSoFar += generateCompareToForObject(f);
                } else if (f.type() == Field.FieldType.MESSAGE || f.type() == Field.FieldType.ONE_OF) {
                    verifyComparable(f, destinationSrcDir);
                    generatedCodeSoFar += generateCompareToForObject(f);
                } else {
                    throw new IllegalArgumentException("Unexpected field type for getting CompareTo - "
                            + f.type().toString());
                }
            }
        }
        return generatedCodeSoFar.indent(DEFAULT_INDENT * 2);
    }

    @NonNull
    private static String generateCompareToForObject(Field f) {
        return """
               if ($fieldName == null && thatObj.$fieldName != null) {
                   return -1;
               }
               if ($fieldName != null && thatObj.$fieldName == null) {
                   return 1;
               }
               if ($fieldName != null) {
                   result = $fieldName.compareTo(thatObj.$fieldName);
               }
               if (result != 0) {
                   return result;
               }
               """
                .replace("$fieldName", f.nameCamelFirstLower());
    }

    /**
     * Verify that the field is comparable.
     *
     * @param field The field to verify.
     * @param destinationSrcDir The directory where the previously generated code is saved.
     */
    private static void verifyComparable(final Field field, File destinationSrcDir) {
        if (field instanceof final SingleField singleField) {
            if (singleField.type() != Field.FieldType.MESSAGE) {
                // everything else except message and bytes is comparable for sure
                return;
            }
            // let's check if the message implements Comparable
            final String className = singleField.javaFieldType();
            final File javaFile = getJavaFile(destinationSrcDir, singleField.messageTypeModelPackage(), className);
            try (BufferedReader reader = new BufferedReader(new FileReader(javaFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (COMPARABLE_PATTERN.matcher(line).find()) {
                        return;
                    }
                }
                throw new IllegalArgumentException(("Field %s.%s specified in `pbj.comparable` option must implement "
                                + "`Comparable` interface but it doesn't.")
                        .formatted(className, field.nameCamelFirstLower()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (field instanceof final OneOfField oneOfField) {
            oneOfField.fields().forEach(v -> verifyComparable(v, destinationSrcDir));
        } else {
            throw new UnsupportedOperationException("Unexpected field type - " + field.getClass());
        }
    }

    /**
     * Generates the compareTo code for a primitive wrapper field.
     *
     * @param f The field for which to generate the compareTo code.
     *
     * @return The generated code for compareTo method body
     */
    private static String getPrimitiveWrapperCompareToGeneration(Field f) {
        final String template =
                """
                if ($fieldName == null && thatObj.$fieldName != null) {
                    return -1;
                } else if ($fieldName != null && thatObj.$fieldName == null) {
                    return 1;
                } else if ($fieldName != null) {
                    result = $compareStatement;
                }
                if (result != 0) {
                    return result;
                }
                """;

        final String compareStatement =
                switch (f.messageType()) {
                    case "StringValue", "BytesValue" -> "$fieldName.compareTo(thatObj.$fieldName)";
                    case "BoolValue" -> "java.lang.Boolean.compare($fieldName, thatObj.$fieldName)";
                    case "Int32Value" -> "java.lang.Integer.compare($fieldName, thatObj.$fieldName)";
                    case "UInt32Value" -> "java.lang.Integer.compareUnsigned($fieldName, thatObj.$fieldName)";
                    case "Int64Value" -> "java.lang.Long.compare($fieldName, thatObj.$fieldName)";
                    case "UInt64Value" -> "java.lang.Long.compareUnsigned($fieldName, thatObj.$fieldName)";
                    case "FloatValue" -> "java.lang.Float.compare($fieldName, thatObj.$fieldName)";
                    case "DoubleValue" -> "java.lang.Double.compare($fieldName, thatObj.$fieldName)";
                    default -> throw new UnsupportedOperationException(
                            "Unhandled optional message type:" + f.messageType());
                };

        return template.replace("$compareStatement", compareStatement).replace("$fieldName", f.nameCamelFirstLower());
    }

    /**
     * Remove leading dot from a string so ".a.b.c" becomes "a.b.c"
     *
     * @param text text to remove leading dot from
     *
     * @return text without a leading dot
     */
    public static String removingLeadingDot(String text) {
        if (!text.isEmpty() && text.charAt(0) == '.') {
            return text.substring(1);
        }
        return text;
    }

    /**
     * Get the java file for a src directory, package and classname with optional suffix. All parent directories will
     * also be created.
     *
     * @param srcDir The src dir root of all java src
     * @param javaPackage the java package with '.' deliminators
     * @param className the camel case class name
     *
     * @return File object for java file
     */
    public static File getJavaFile(File srcDir, String javaPackage, String className) {
        File packagePath =
                new File(srcDir.getPath() + File.separatorChar + javaPackage.replaceAll("\\.", "\\" + File.separator));
        //noinspection ResultOfMethodCallIgnored
        packagePath.mkdirs();
        return new File(packagePath, className + ".java");
    }
}
