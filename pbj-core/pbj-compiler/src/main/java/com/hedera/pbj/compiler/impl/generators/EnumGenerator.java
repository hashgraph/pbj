// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.pbj.compiler.impl.Common.*;
import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

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
     * @param destinationSrcDir The destination source directory to generate into
     * @param lookupHelper Lookup helper for package information
     * @throws IOException if there was a problem writing generated code
     */
    public static void generateEnumFile(Protobuf3Parser.EnumDefContext enumDef, File destinationSrcDir,
                                 final ContextualLookupHelper lookupHelper) throws IOException {
        final String enumName = enumDef.enumName().getText();
        final String modelPackage = lookupHelper.getPackageForEnum(FileType.MODEL, enumDef);
        final String javaDocComment = (enumDef.docComment()== null) ? "" :
                cleanDocStr(enumDef.docComment().getText().replaceAll("\n \\*\s*\n","\n * <br>\n"));
        String deprecated = "";
        final Map<Integer, EnumValue> enumValues = new HashMap<>();
        int maxIndex = 0;
        for (var item: enumDef.enumBody().enumElement()) {
            if (item.enumField() != null && item.enumField().ident() != null) {
                final var enumValueName = item.enumField().ident().getText();
                final var enumNumber = Integer.parseInt(item.enumField().intLit().getText());
                final String enumValueJavaDoc = cleanDocStr(
                        (item.enumField().docComment() == null || item.enumField().docComment().getText().isBlank()) ?
                                enumValueName :
                        item.enumField().docComment().getText()
                                .replaceAll("[\t ]*/\\*\\*([\n\t ]+\\*\s+)?","") // remove doc start indenting
                                .replaceAll("/\\*\\*","") //  remove doc start
                                .replaceAll("[\n\t ]+\\*/","") //  remove doc end
                                .replaceAll("\n[\t\s]+\\*\\*?","\n") // remove doc indenting
                                .replaceAll("/n\s*/n","/n") //  remove empty lines
                );
                maxIndex = Math.max(maxIndex, enumNumber);
                // extract if the enum is marks as deprecated
                boolean deprecatedEnumValue = false;
                if(item.enumField().enumValueOptions() != null && item.enumField().enumValueOptions().enumValueOption() != null) {
                    for(var option:item.enumField().enumValueOptions().enumValueOption()) {
                        if ("deprecated".equals(option.optionName().getText())) {
                            deprecatedEnumValue = true;
                        } else {
                            System.err.printf("Unhandled Option: %s%n", option.getText());
                        }
                    }
                }
                enumValues.put(enumNumber, new EnumValue(enumValueName, deprecatedEnumValue,enumValueJavaDoc));
            } else if (item.optionStatement() != null){
                if ("deprecated".equals(item.optionStatement().optionName().getText())) {
                    deprecated = "@Deprecated ";
                } else {
                    System.err.printf("Unhandled Option: %s%n", item.optionStatement().getText());
                }
            } else {
                System.err.printf("EnumGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }
        try (FileWriter javaWriter = new FileWriter(getJavaFile(destinationSrcDir, modelPackage, enumName))) {
            javaWriter.write(
                    "package %s;\n\n%s".formatted(modelPackage, createEnum(javaDocComment, deprecated, enumName,
                            maxIndex, enumValues, false))
            );
        }
    }

    /**
     * Generate code for a enum
     *
     * @param javaDocComment either enum javadoc comment or empty string
     * @param deprecated either @deprecated string or empty string
     * @param enumName the name for enum
     * @param maxIndex the max ordinal for enum
     * @param enumValues map of ordinal to enum value
     * @param addUnknown when true we add an enum value for one of
     * @return string code for enum
     */
    static String createEnum(String javaDocComment, String deprecated, String enumName,
                             int maxIndex, Map<Integer, EnumValue> enumValues, boolean addUnknown) {
        final List<String> enumValuesCode = new ArrayList<>(maxIndex);
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
        for (int i = 0; i <= maxIndex; i++) {
            final EnumValue enumValue = enumValues.get(i);
            if (enumValue != null) {
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
                        .formatted(cleanedEnumComment, deprecatedText, camelToUpperSnake(enumValue.name), i,
                                enumValue.name));
                // spotless:on
            }
        }
        // spotless:off
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
                            default -> throw new IllegalArgumentException("Unknown protobuf ordinal "+ordinal);
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
                }
                """
                .replace("$javaDocComment", javaDocComment)
                .replace("$deprecated$", deprecated)
                .replace("$enumName", enumName)
                .replace("$enumValues", String.join(",\n\n", enumValuesCode).indent(DEFAULT_INDENT))
                .replace("$caseStatements", enumValues.entrySet()
                        .stream()
                        .map((entry) -> "case %s -> %s;"
                                .formatted(entry.getKey(), camelToUpperSnake(entry.getValue().name))
                                .indent(DEFAULT_INDENT * 3))
                        .collect(Collectors.joining("\n")))
                .replace("$fromStringCaseStatements", enumValues.values().stream().map(enumValue -> {
                    if (camelToUpperSnake(enumValue.name).equals(enumValue.name)) {
                        return "case \"%s\" -> %s;"
                                .formatted(enumValue.name, camelToUpperSnake(enumValue.name))
                                .indent(DEFAULT_INDENT * 3);
                    } else {
                        return "case \"%s\", \"%s\" -> %s;"
                                .formatted(enumValue.name, camelToUpperSnake(enumValue.name),
                                        camelToUpperSnake(enumValue.name)).indent(DEFAULT_INDENT * 3);
                    }
                }).collect(Collectors.joining("\n")));
        // spotless:on
    }
}
