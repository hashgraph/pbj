// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.*;
import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Code for generating enum code
 */
@SuppressWarnings({"EscapedSpace"})
public final class EnumGenerator {

    /** Record for an enum value temporary storage */
    record EnumValue(String name, boolean deprecated, String javaDoc) {}

    /**
     * Generate a Java enum from protobuf enum
     *
     * @param enumDef the parsed enum def
     * @param writer the writer to append the generated enum to
     * @param lookupHelper Lookup helper for package information
     * @throws IOException if there was a problem writing generated code
     */
    public static void generateEnum(
            final Protobuf3Parser.EnumDefContext enumDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String enumName = enumDef.enumName().getText();
        final String javaDocComment = (enumDef.docComment() == null)
                ? ""
                : cleanDocStr(enumDef.docComment().getText().replaceAll("\n \\*\s*\n", "\n * <br>\n"));
        String deprecated = "";
        final Map<Integer, EnumValue> enumValues = new HashMap<>();
        for (var item : enumDef.enumBody().enumElement()) {
            if (item.enumField() != null && item.enumField().ident() != null) {
                final var enumValueName = item.enumField().ident().getText();
                final var enumNumber =
                        Integer.parseInt(item.enumField().intLit().getText());
                final String enumValueJavaDoc = cleanDocStr(
                        (item.enumField().docComment() == null
                                        || item.enumField()
                                                .docComment()
                                                .getText()
                                                .isBlank())
                                ? enumValueName
                                : item.enumField()
                                        .docComment()
                                        .getText()
                                        .replaceAll("[\t ]*/\\*\\*([\n\t ]+\\*\s+)?", "") // remove doc start indenting
                                        .replaceAll("/\\*\\*", "") //  remove doc start
                                        .replaceAll("[\n\t ]+\\*/", "") //  remove doc end
                                        .replaceAll("\n[\t\s]+\\*\\*?", "\n") // remove doc indenting
                                        .replaceAll("/n\s*/n", "/n") //  remove empty lines
                        );
                // extract if the enum is marks as deprecated
                boolean deprecatedEnumValue = false;
                if (item.enumField().enumValueOptions() != null
                        && item.enumField().enumValueOptions().enumValueOption() != null) {
                    for (var option : item.enumField().enumValueOptions().enumValueOption()) {
                        if ("deprecated".equals(option.optionName().getText())) {
                            deprecatedEnumValue = true;
                        } else {
                            System.err.printf("Unhandled Option: %s%n", option.getText());
                        }
                    }
                }
                enumValues.put(enumNumber, new EnumValue(enumValueName, deprecatedEnumValue, enumValueJavaDoc));
            } else if (item.optionStatement() != null) {
                if ("deprecated".equals(item.optionStatement().optionName().getText())) {
                    deprecated = "@Deprecated ";
                } else {
                    System.err.printf(
                            "Unhandled Option: %s%n", item.optionStatement().getText());
                }
            } else if (item.reserved() != null) {
                // Ignore reserved enum values
            } else {
                System.err.printf("EnumGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }

        writer.addImport("java.util.List");
        writer.append(createEnum(javaDocComment, deprecated, enumName, enumValues, false));
    }

    /**
     * Generate code for a enum
     *
     * @param javaDocComment either enum javadoc comment or empty string
     * @param deprecated either @deprecated string or empty string
     * @param enumName the name for enum
     * @param enumValues map of ordinal to enum value
     * @param addUnknown when true we add an enum value for one of
     * @return string code for enum
     */
    static String createEnum(
            String javaDocComment,
            String deprecated,
            String enumName,
            Map<Integer, EnumValue> enumValues,
            boolean addUnknown) {
        final List<String> enumValuesCode = new ArrayList<>(enumValues.size());
        if (addUnknown) {
            // spotless:off
            enumValuesCode.add(
                    """
                    /**
                     * Enum value for a unset OneOf, to avoid null OneOfs
                     */
                    UNSET(-1, "UNSET")""");
            // spotless:on
        }
        enumValues.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(entry -> {
                    final int enumNumber = entry.getKey();
                    final EnumValue enumValue = entry.getValue();
                    // spotless:off
                    final String cleanedEnumComment = enumValue.javaDoc.contains("\n") ?
                            """
                            /**
                             * $enumJavadoc
                             */
                            """
                                    .replace("$enumJavadoc",
                                            enumValue.javaDoc.replaceAll("\n\s*","\n * ")) :
                            """
                            /** $enumJavadoc */
                            """
                                    .replace("$enumJavadoc", enumValue.javaDoc);
                    final String deprecatedText = enumValue.deprecated ? "@Deprecated\n" : "";
                    enumValuesCode.add("%s%s%s(%d, \"%s\")"
                            .formatted(cleanedEnumComment, deprecatedText, camelToUpperSnake(enumValue.name), enumNumber,
                                    enumValue.name));
                    // spotless:on
                });
        // spotless:off

        if (!addUnknown) { // !addUnknown to avoid a clash with OneOf UNSET above.
            // spotless:off
            enumValuesCode.add(
                    """
                    /**
                     * Enum value for unrecognized protoOrdinal values.
                     */
                    UNRECOGNIZED(-1, "UNRECOGNIZED")""");
            // spotless:on
        }

        return """
                $javaDocComment
                $deprecated$public enum $enumName
                        implements com.hedera.pbj.runtime.EnumWithProtoMetadata {
                $enumValues;

                    /** The field ordinal in protobuf for this type */
                    private final int protoOrdinal;

                    /** The original field name in protobuf for this type */
                    private final String protoName;

                    /**
                     * OneOf Type Enum Constructor
                     *
                     * @param protoOrdinal The oneof field ordinal in protobuf for this type
                     * @param protoName The original field name in protobuf for this type
                     */
                    $enumName(final int protoOrdinal, String protoName) {
                        this.protoOrdinal = protoOrdinal;
                        this.protoName = protoName;
                    }

                    /**
                     * Get the oneof field ordinal in protobuf for this type
                     *
                     * @return The oneof field ordinal in protobuf for this type
                     */
                    public int protoOrdinal() {
                        return protoOrdinal;
                    }

                    /**
                     * Get the original field name in protobuf for this type
                     *
                     * @return The original field name in protobuf for this type
                     */
                    public String protoName() {
                        return protoName;
                    }

                    /**
                     * Get enum from protobuf ordinal
                     *
                     * @param ordinal the protobuf ordinal number
                     * @return enum for matching ordinal
                     * @throws IllegalArgumentException if ordinal doesn't exist
                     */
                    public static $enumName fromProtobufOrdinal(int ordinal) {
                        return switch(ordinal) {
                            $caseStatements
                            default -> $unknownValue;
                        };
                    }

                    /**
                     * Get enum from string name, supports the enum or protobuf format name
                     *
                     * @param name the enum or protobuf format name
                     * @return enum for matching name
                     */
                    public static $enumName fromString(String name) {
                        return switch(name) {
                            $fromStringCaseStatements
                            default -> throw new IllegalArgumentException("Unknown token kyc status "+name);
                        };
                    }

                    /**
                     * Get enum from an $enumName or an unrecognized object (likely an Integer, but we don't check here.)
                     *
                     * @param obj an object
                     * @return enum for matching ordinal, or UNRECOGNIZED/UNSET value
                     */
                    public static $enumName fromObject(Object obj) {
                        if (obj instanceof $enumName pbjEnum) {
                            return pbjEnum;
                        } else {
                            return $enumName.$unknownValue;
                        }
                    }

                    /**
                     * Get a list of enums from a list of $enumName or unrecognized objects (likely Integers, but we don't check here.)
                     * Note that this method creates a new list, so it's best to cache the result.
                     *
                     * @param list a list of objects
                     * @return a list of enum or UNRECOGNIZED/UNSET values
                     */
                    public static List<$enumName> fromObjects(List<?> list) {
                        return list.stream().map($enumName::fromObject).toList();
                    }

                    /**
                     * Get protoOrdinal for an enum or Integer object, or throw IllegalArgumentException.
                     *
                     * @param obj an object
                     * @return protoOrdinal of the object
                     */
                    public static int toProtoOrdinal(Object obj) {
                        if (obj instanceof $enumName pbjEnum) {
                            return pbjEnum.protoOrdinal();
                        } else if (obj instanceof Integer i) {
                            return i;
                        } else {
                            throw new IllegalArgumentException("Neither $enumName, nor Integer, but: " + obj.getClass().getName());
                        }
                    }

                    /**
                     * Get protoOrdinals for enums or Integers objects, or throw IllegalArgumentException.
                     * Note that this method creates a new list, so it's best to cache the result.
                     *
                     * @param list a list of enum or Integer objects
                     * @return a list of their protoOrdinals
                     */
                    public static List<Integer> toProtoOrdinals(List<?> list) {
                        return list.stream().map($enumName::toProtoOrdinal).toList();
                    }

                    /**
                     * Compare two objects, each of them could be an enum or an Integer representing a protoOrdinal value.
                     */
                    public static int compare(final Object o1, final Object o2) {
                        if (o1 instanceof $enumName e1 && o2 instanceof $enumName e2) {
                            return e1.compareTo(e2);
                        }
                        if (o1 == null && o2 != null) {
                            return -1;
                        }
                        if (o1 != null && o2 == null) {
                            return 1;
                        }
                        // Both non-null here
                        final int i1, i2;
                        if (o1 instanceof $enumName e1) {
                            i1 = e1.protoOrdinal();
                        } else if (o1 instanceof Integer ii1) {
                            i1 = ii1.intValue();
                        } else {
                            throw new IllegalArgumentException("o1 is neither $enumName, nor Integer. It's: " + o1.getClass().getName());
                        }
                        if (o2 instanceof $enumName e2) {
                            i2 = e2.protoOrdinal();
                        } else if (o2 instanceof Integer ii2) {
                            i2 = ii2.intValue();
                        } else {
                            throw new IllegalArgumentException("o2 is neither $enumName, nor Integer. It's: " + o2.getClass().getName());
                        }
                        return Integer.compare(i1, i2);
                    }
                }
                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated$", deprecated)
                .replace("$enumName", enumName)
                .replace("$enumValues", String.join(",\n\n", enumValuesCode).indent(DEFAULT_INDENT))
                .replace(
                        "$caseStatements",
                        enumValues.entrySet().stream()
                                .map((entry) -> "case %s -> %s;"
                                        .formatted(entry.getKey(), camelToUpperSnake(entry.getValue().name))
                                        .indent(DEFAULT_INDENT * 3))
                                .collect(Collectors.joining("\n")))
                .replace(
                        "$fromStringCaseStatements",
                        enumValues.values().stream()
                                .map(enumValue -> {
                                    if (camelToUpperSnake(enumValue.name).equals(enumValue.name)) {
                                        return "case \"%s\" -> %s;"
                                                .formatted(enumValue.name, camelToUpperSnake(enumValue.name))
                                                .indent(DEFAULT_INDENT * 3);
                                    } else {
                                        return "case \"%s\", \"%s\" -> %s;"
                                                .formatted(
                                                        enumValue.name,
                                                        camelToUpperSnake(enumValue.name),
                                                        camelToUpperSnake(enumValue.name))
                                                .indent(DEFAULT_INDENT * 3);
                                    }
                                })
                                .collect(Collectors.joining("\n")))
                .replace("$unknownValue", addUnknown ? "UNSET" : "UNRECOGNIZED");
        // spotless:on
    }
}
