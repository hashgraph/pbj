// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static java.util.Collections.emptyList;
import static java.util.regex.Matcher.quoteReplacement;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.EnumDefContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageElementContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageTypeContext;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.TopLevelDefContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Class that manages packages and enum names that are used more than one place in code generation
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public final class LookupHelper {
    /** REGEX pattern to match options in special option comments */
    private static final Pattern OPTION_COMMENT = Pattern.compile("//\\s+<<<\\s*([\\w.]+)\\s*=\\s*\"([^\"]+)\"\\s*>>>");
    /** The option name for PBJ package at file level */
    private static final String PBJ_PACKAGE_OPTION_NAME = "pbj.java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_MESSAGE_PACKAGE_OPTION_NAME = "pbj.message_java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_ENUM_PACKAGE_OPTION_NAME = "pbj.enum_java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_SERVICE_PACKAGE_OPTION_NAME = "pbj.service_java_package";

    private static final String PBJ_COMPARABLE_OPTION_NAME = "pbj.comparable";

    /** The option name for protoc java package at file level */
    private static final String PROTOC_JAVA_PACKAGE_OPTION_NAME = "java_package";

    /** Extension for protobuf files */
    public static final String PROTO_EXTENSIION = ".proto";

    // Exception message templates
    private static final String METHOD_WRONG_CONTEXT_MESSAGE = "%s only supports MessageDefContext or EnumDefContext";
    private static final String FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE =
            "Failed to find messageMapLocal for proto file [%s]";
    private static final String FAILED_TO_FIND_MSG_TYPE_MESSAGE =
            "Failed to find fully qualified message type for [%s] in file [%s] imports = %s";
    private static final String PACKAGE_NOT_FOUND_MESSAGE =
            "Could not find %s package for message or enum [%s] in file [%s]";
    private static final String LIMITED_CONTEXT_OPTIONS_SUPPORT_MESSAGE =
            "%s only supports MessageDefContext, EnumDefContext or MessageTypeContext not [%s]";
    private static final String FILE_MISSING_PACKAGE_OPTION_MESSAGE =
            "%sProto file [%s] does not contain \"%s\" or \"%s\" options.%n";
    private static final String IMPORT_MATCHED_MULTIPLE_MESSAGE =
            "Import \"%s\" in proto file \"%s\" matched more than 1 file in src files [%s]";
    private static final String IMPORT_NOT_FOUND_MESSAGE =
            "Import \"%s\" in proto file \"%s\" can not be found in src files.";

    /**
     * A non-null suffix to add to the Java package name of generated PBJ classes when an explicit `pbj.java_package`
     * is missing and PBJ instead derives the Java package for PBJ models from the standard `java_package` option
     * or otherwise. May be empty.
     */
    private final String javaPackageSuffix;

    /**
     * Map from fully qualified msgDef name to fully qualified pbj java package, not including java
     * class
     */
    private final Map<String, String> pbjPackageMap = new HashMap<>();

    /**
     * Map from fully qualified msgDef name to complete Java class name, including outer classes names,
     * but w/o a java package prefix.
     */
    private final Map<String, String> pbjCompleteClassMap = new HashMap<>();

    /**
     * Map from fully qualified msgDef name to fully qualified protoc java package, not including
     * java class
     */
    private final Map<String, String> protocPackageMap = new HashMap<>();

    /** Map from proto file path to list of other proto files it imports */
    private final Map<String, Set<String>> protoFileImports = new HashMap<>();

    /** Map from fully qualified msgDef name to a list of field names that are comparable.
     * We use a list here, because the order of the field matters. */
    private final Map<String, List<String>> comparableFieldsByMsg = new HashMap<>();

    /**
     * Map from file path to map of message/enum name to fully qualified message/enum name. This
     * allows us to lookup for a given protobuf file an unqualified message name and get back the
     * qualified message name.
     */
    private final Map<String, Map<String, String>> msgEnumServiceByFile = new HashMap<>();

    /**
     * Set of all fully qualified message names that are enums, so we can check if a message is an
     * enum or not
     */
    private final Set<String> enumNames = new HashSet<>();

    /**
     * Build a new lookup helper, root directory of protobuf files. This scans directory reading
     * protobuf files extracting what is needed.
     *
     * @param allSrcFiles collection of all proto src files
     * @param javaPackageSuffix an optional, nullable suffix to add to the Java package name in generated classes, e.g. ".pbj"
     */
    public LookupHelper(final Iterable<File> allSrcFiles, final String javaPackageSuffix) {
        this.javaPackageSuffix = javaPackageSuffix == null ? "" : javaPackageSuffix.trim();
        build(allSrcFiles);
    }

    /**
     * Get the unqualified proto name for a message, enum or a message type. For example
     * "proto.GetAccountDetailsResponse.AccountDetails" would return "AccountDetails".
     *
     * @param context The parser context for a message, enum or a message type.
     * @return unqualified proto name
     */
    public String getUnqualifiedProtoName(final ParserRuleContext context) {
        if (context instanceof final MessageDefContext msgDef) {
            return msgDef.messageName().getText();
        } else if (context instanceof final EnumDefContext enumDef) {
            return enumDef.enumName().getText();
        } else if (context instanceof final Protobuf3Parser.ServiceDefContext serviceDef) {
            return serviceDef.serviceName().getText();
        } else if (context instanceof final MessageTypeContext msgTypeContext) {
            final String messageType = msgTypeContext.getText();
            return messageType.contains(".") ? messageType.substring(messageType.lastIndexOf('.') + 1) : messageType;
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getUnqualifiedProtoName"));
        }
    }

    private String getFullyQualifiedProtoMessageName(final String messageType, final ParserRuleContext context) {
        // messageType can be a fully qualified name already, or it may be an unqualified, or partially qualified name.
        // Check all known fully qualified names, both local and imported:
        if (pbjPackageMap.containsKey(messageType)) {
            // It's a known fully-qualified name already
            return messageType;
        }

        // We know this is a partial, non-fully-qualified name at this point. It may be defined
        // as an inner type in the current message that we're parsing, or as an inner type
        // in the next-level-outer-message and so on recursively, or it can be a top-level
        // type in the current file.
        // It can also be an imported type from another file that is in the same proto package.
        // Just walk up the parsing tree recursively and try every level as a prefix:
        ParserRuleContext parentContext = context;
        while ((parentContext = parentContext.getParent()) != null) {
            if (parentContext instanceof MessageDefContext ctx) {
                final String msgName = ctx.messageName().getText();
                final String tryName = getFullyQualifiedProtoMessageName(msgName + "." + messageType, ctx);
                if (tryName != null) {
                    return tryName;
                }
            } else if (parentContext instanceof Protobuf3Parser.ProtoContext ctx) {
                // We've reached the top-level context. Weirdly, the grammar allows for multiple package
                // statements, so simply try each:
                for (final Protobuf3Parser.PackageStatementContext psc : ctx.packageStatement()) {
                    final String packageName = psc.fullIdent().getText();
                    if (packageName == null || packageName.isBlank()) {
                        continue;
                    }
                    final String tryName = getFullyQualifiedProtoMessageName(packageName + "." + messageType, ctx);
                    if (tryName != null) {
                        return tryName;
                    }
                }
            }
        }

        // Well, we searched everywhere and couldn't find any matching names.
        return null;
    }

    /**
     * Get the fully qualified proto name for a message, enum or a message type. For example
     * "proto.GetAccountDetailsResponse.AccountDetails" would return
     * "proto.GetAccountDetailsResponse.AccountDetails".
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param context The parser context for a message, enum or a message type.
     * @return fully qualified proto name
     */
    public String getFullyQualifiedProtoName(final File protoSrcFile, final ParserRuleContext context) {
        // First, try msgType and msgDef to process nested types correctly. Note that this branch cannot resolve
        // references to enums. But due to recursive calls, this particular branch runs for enum references through
        // the msgDef condition. So we shouldn't error out immediately if we cannot find a name in this branch.
        // We should first try the next one (see an if below this one), which partially overlaps with this branch.
        if (context instanceof MessageTypeContext || context instanceof MessageDefContext) {
            final String messageType = context instanceof MessageTypeContext
                    ? context.getText()
                    : ((MessageDefContext) context).messageName().getText();

            // Ugly, but this is how PBJ implements the built-in "support" for Google Protobuf boxed types...
            // Imports for "google/protobuf/wrappers.proto" are ignored elsewhere in PBJ,
            // so we cannot really refer to the real Google types that we refuse to import physically.
            // This is the only option for now:
            if (messageType.startsWith("google.protobuf.")) {
                return messageType;
            }

            final String fullyQualifiedProtoMessageName = getFullyQualifiedProtoMessageName(messageType, context);
            if (fullyQualifiedProtoMessageName != null) {
                return fullyQualifiedProtoMessageName;
            }
        }

        // msgDef/msgType failed. Okay, let's try the other option. Note that this option doesn't support nested types
        // well. However, it does support enums well (through this same msgDef condition, same as above.)
        if (context instanceof MessageDefContext
                || context instanceof EnumDefContext
                || context instanceof Protobuf3Parser.ServiceDefContext) {
            // It's unclear what exactly is being handled in this branch. However, the symbol maps
            // inside msgAndEnumByFile use unqualified names as keys, meaning that this code doesn't support
            // having multiple inner classes with the same name located in different outer classes.
            // We'll get back to fixing this in the second part of https://github.com/hashgraph/pbj/issues/263
            final Map<String, String> fileMap = msgEnumServiceByFile.get(protoSrcFile.getAbsolutePath());
            if (fileMap == null) {
                throw new PbjCompilerException(FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE.formatted(protoSrcFile));
            }
            return fileMap.get(getUnqualifiedProtoName(context));
        } else if (!(context instanceof MessageTypeContext)) {
            // We only support msgDef, msgTye, enumDef, and serviceDef in this method, so error out here:
            throw new UnsupportedOperationException(
                    METHOD_WRONG_CONTEXT_MESSAGE.formatted("getFullyQualifiedProtoName"));
        }

        // ...otherwise, error out this way:
        // we failed to find
        final Object[] importsArray =
                protoFileImports.get(protoSrcFile.getAbsolutePath()).toArray();
        final String importsString = Arrays.toString(importsArray);
        throw new PbjCompilerException(FAILED_TO_FIND_MSG_TYPE_MESSAGE.formatted(context, protoSrcFile, importsString));
    }

    /**
     * Get the unqualified Java class name for given message or enum.
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param fileType The type of file we want the package for
     * @param context Parser Context, a message or enum
     * @return java package to put model class in
     */
    String getUnqualifiedClass(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
        final String name;
        final boolean isEnum;
        if (context instanceof final MessageTypeContext msgType) {
            final var unqualifiedName = getUnqualifiedProtoName(msgType);
            final var qualifiedName = getFullyQualifiedProtoName(protoSrcFile, msgType);
            isEnum = isEnum(qualifiedName);
            name = unqualifiedName;
        } else if (context instanceof final MessageDefContext msgDef) {
            name = msgDef.messageName().getText();
            isEnum = false;
        } else if (context instanceof final EnumDefContext enumDef) {
            name = enumDef.enumName().getText();
            isEnum = true;
        } else if (context instanceof final Protobuf3Parser.ServiceDefContext serviceDef) {
            name = serviceDef.serviceName().getText();
            isEnum = false;
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getUnqualifiedClass"));
        }
        if (isEnum) {
            return name;
        } else {
            return formatFileTypeName(name, fileType);
        }
    }

    String formatFileTypeName(final String name, final FileType fileType) {
        return switch (fileType) {
            case MODEL, PROTOC -> name;
            case SCHEMA -> name + FileAndPackageNamesConfig.SCHEMA_JAVA_FILE_SUFFIX;
            case CODEC -> name + FileAndPackageNamesConfig.CODEC_JAVA_FILE_SUFFIX;
            case JSON_CODEC -> name + FileAndPackageNamesConfig.JSON_CODEC_JAVA_FILE_SUFFIX;
            case TEST -> name + FileAndPackageNamesConfig.TEST_JAVA_FILE_SUFFIX;
        };
    }

    /**
     * Get the Java package a class should be generated into for a given context and file type.
     *
     * @param protoSrcFile the proto source file that the message/enum/service is in
     * @param fileType The type of file we want the package for
     * @param context Parser Context, a message, or enum, or service
     * @return java package to put model class in
     */
    @Nullable
    String getPackage(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
        if (context instanceof MessageDefContext
                || context instanceof EnumDefContext
                || context instanceof Protobuf3Parser.ServiceDefContext
                || context instanceof MessageTypeContext
                || context instanceof Protobuf3Parser.EnumTypeContext) {
            final String qualifiedProtoName = getFullyQualifiedProtoName(protoSrcFile, context);
            if (qualifiedProtoName.startsWith("google.protobuf")) {
                return null;
            } else if (fileType == FileType.PROTOC) {
                final String protocPackage = protocPackageMap.get(qualifiedProtoName);
                if (protocPackage == null) {
                    throw new PbjCompilerException(
                            PACKAGE_NOT_FOUND_MESSAGE.formatted("protoc", qualifiedProtoName, protoSrcFile));
                }
                return protocPackage;
            } else {
                final String basePackage = pbjPackageMap.get(qualifiedProtoName);
                if (basePackage == null) {
                    throw new PbjCompilerException(
                            PACKAGE_NOT_FOUND_MESSAGE.formatted("pbj", qualifiedProtoName, protoSrcFile));
                }
                return switch (fileType) {
                    //noinspection ConstantConditions
                    case MODEL, PROTOC -> basePackage;
                    case SCHEMA -> basePackage + '.' + FileAndPackageNamesConfig.SCHEMAS_SUBPACKAGE;
                    case CODEC, JSON_CODEC -> basePackage + '.' + FileAndPackageNamesConfig.CODECS_SUBPACKAGE;
                    case TEST -> basePackage + '.' + FileAndPackageNamesConfig.TESTS_SUBPACKAGE;
                };
            }

        } else {
            throw new UnsupportedOperationException(LIMITED_CONTEXT_OPTIONS_SUPPORT_MESSAGE.formatted(
                    "getPackage", context.getClass().getName()));
        }
    }

    /**
     * Get the fully qualified Java class name for given message/enum and file type.
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param fileType The type of file we want the class name for
     * @param context Parser Context, a message or enum
     * @return fully qualified Java class name
     */
    String getFullyQualifiedClass(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
        if (context instanceof MessageDefContext
                || context instanceof EnumDefContext
                || context instanceof MessageTypeContext) {
            return getPackage(protoSrcFile, fileType, context)
                    + '.'
                    + formatCompleteClass(protoSrcFile, fileType, context);
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getFullyQualifiedClass"));
        }
    }

    /**
     * Format the complete Java class name for a given message/enum being parsed and file type, including outer classes names,
     * but w/o the package name.
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param fileType The type of file we want the class name for
     * @param context Parser Context, a message or enum
     * @return fully qualified Java class name
     */
    String formatCompleteClass(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
        if (context instanceof MessageDefContext
                || context instanceof EnumDefContext
                || context instanceof Protobuf3Parser.ServiceDefContext
                || context instanceof MessageTypeContext) {
            final String messageName = getUnqualifiedClass(protoSrcFile, fileType, context);
            // protoc supports nested classes so need parent classes/messages
            final String parentClasses;
            if (context.getParent() instanceof MessageElementContext) {
                final StringBuilder sb = new StringBuilder();
                ParserRuleContext parent = context.getParent();
                while (!(parent instanceof TopLevelDefContext)) {
                    if (parent instanceof MessageDefContext) {
                        sb.insert(0, '.');
                        final String name =
                                ((MessageDefContext) parent).messageName().getText();
                        sb.insert(0, fileType == FileType.PROTOC ? name : formatFileTypeName(name, fileType));
                    }
                    parent = parent.getParent();
                }
                parentClasses = sb.toString();
            } else {
                parentClasses = "";
            }
            return parentClasses + messageName;
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("formatCompleteClass"));
        }
    }

    /**
     * Get the complete Java class name for a given message, including outer classes names,
     * but w/o the package name.
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param context Parser Context, a message
     * @return fully qualified Java class name, or null
     */
    String getCompleteClass(final File protoSrcFile, final ParserRuleContext context) {
        return pbjCompleteClassMap.get(getFullyQualifiedProtoName(protoSrcFile, context));
    }

    /**
     * Check if the given fullyQualifiedMessageOrEnumName is a known enum
     *
     * @param fullyQualifiedMessageOrEnumName to check if enum
     * @return true if known as an enum, recorded by addEnum()
     */
    private boolean isEnum(final String fullyQualifiedMessageOrEnumName) {
        return enumNames.contains(fullyQualifiedMessageOrEnumName);
    }

    /**
     * Check if the given fullyQualifiedMessageOrEnumName is a known enum
     *
     * @param messageType field message type to check if enum
     * @return true if known as an enum, recorded by addEnum()
     */
    boolean isEnum(final File protoSrcFile, final MessageTypeContext messageType) {
        return isEnum(getFullyQualifiedProtoName(protoSrcFile, messageType));
    }

    /**
     * Check if the given fullyQualifiedMessageOrEnumName is comparable
     *
     * @param fullyQualifiedMessageOrEnumName to check if enum
     * @return true if comparable
     */
    private boolean isComparable(final String fullyQualifiedMessageOrEnumName) {
        return comparableFieldsByMsg.get(fullyQualifiedMessageOrEnumName) != null
                && !comparableFieldsByMsg.get(fullyQualifiedMessageOrEnumName).isEmpty();
    }

    /**
     * Check if the given fullyQualifiedMessageOrEnumName is comparable
     *
     * @param messageType field message type to check if enum
     * @return true if comparable
     */
    boolean isComparable(final File protoSrcFile, final MessageTypeContext messageType) {
        return isComparable(getFullyQualifiedProtoName(protoSrcFile, messageType));
    }

    // =================================================================================================================
    // BUILD METHODS to construct lookup tables

    /**
     * Builds onto map from protobuf msgDef to java package. This is used to produce imports for
     * messages in other packages. It parses every src file an extra time but it is so fast it
     * doesn't really matter.
     *
     * @param allSrcFiles collection of all proto src files
     */
    private void build(final Iterable<File> allSrcFiles) {
        for (final File file : allSrcFiles) {
            final Path filePath = file.toPath();
            final String fullQualifiedFile = file.getAbsolutePath();
            if (file.exists() && file.isFile() && file.getName().endsWith(PROTO_EXTENSIION)) {
                try (final var input = new FileInputStream(file)) {
                    // parse file
                    final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
                    final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
                    final Protobuf3Parser.ProtoContext parsedDoc = parser.proto();
                    // create entry in map for file
                    msgEnumServiceByFile.computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>());
                    // look for PBJ package option
                    String pbjJavaPackage = null;
                    String protocJavaPackage = null;
                    // check for custom option
                    for (final var option : parsedDoc.optionStatement()) {
                        switch (option.optionName().getText().replaceAll("[()]", "")) {
                            case PBJ_PACKAGE_OPTION_NAME ->
                                pbjJavaPackage = option.constant().getText().replaceAll("\"", "");
                            case PROTOC_JAVA_PACKAGE_OPTION_NAME ->
                                protocJavaPackage = option.constant().getText().replaceAll("\"", "");
                        }
                    }
                    // check for special comment option
                    for (final var optionComment : parsedDoc.optionComment()) {
                        final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
                        if (matcher.find()) {
                            final String optionName = matcher.group(1);
                            final String optionValue = matcher.group(2);
                            if (optionName.equals(PBJ_PACKAGE_OPTION_NAME)) {
                                pbjJavaPackage = optionValue;
                            }
                        }
                    }
                    if (file.getName().endsWith("pbj_custom_options.proto")) {
                        // ignore pbj_custom_options.proto file
                        continue;
                    } else if (pbjJavaPackage == null && protocJavaPackage == null) {
                        if (parsedDoc.packageStatement() == null
                                || parsedDoc.packageStatement().isEmpty()) {
                            throw new PbjCompilerException(
                                    "ERROR: Proto file $file doesn't specify pbj.java_package, java_package, or package values. Unable to infer the Java package."
                                            .replace("$file", file.getAbsolutePath()));
                        }
                        if (parsedDoc.packageStatement().size() > 1) {
                            System.err.println(
                                    "WARNING: Proto file $file specifies package several times. PBJ will use the first value only. All specified values: $values"
                                            .replace("$file", file.getAbsolutePath())
                                            .replace(
                                                    "$values",
                                                    parsedDoc.packageStatement().stream()
                                                            .map(psc -> psc.fullIdent()
                                                                    .getText())
                                                            .collect(Collectors.joining(", "))));
                        }
                        // Protoc would use this package, so we pretend this is a protoc package:
                        protocJavaPackage =
                                parsedDoc.packageStatement().get(0).fullIdent().getText();
                    }
                    // process imports
                    final Set<String> fileImports =
                            protoFileImports.computeIfAbsent(fullQualifiedFile, key -> new HashSet<>());
                    for (final var importStatement : parsedDoc.importStatement()) {
                        final String importedFileName =
                                normalizeFileName(importStatement.strLit().getText());
                        // ignore standard google protobuf imports as we do not need them
                        if (importedFileName.startsWith(
                                "google" + FileSystems.getDefault().getSeparator() + "protobuf")) {
                            continue;
                        }
                        // now scan all src files to find import as there can be many src
                        // directories
                        final List<File> matchingSrcFiles = StreamSupport.stream(allSrcFiles.spliterator(), false)
                                .filter(srcFile -> srcFile.getAbsolutePath()
                                        .endsWith(FileSystems.getDefault().getSeparator() + importedFileName))
                                .toList();
                        if (matchingSrcFiles.size() == 1) {
                            fileImports.add(matchingSrcFiles.get(0).getAbsolutePath());
                        } else if (matchingSrcFiles.size() > 1) {
                            throw new PbjCompilerException(IMPORT_MATCHED_MULTIPLE_MESSAGE.formatted(
                                    importedFileName,
                                    file.getAbsolutePath(),
                                    Arrays.toString(matchingSrcFiles.toArray())));
                        } else {
                            throw new PbjCompilerException(
                                    IMPORT_NOT_FOUND_MESSAGE.formatted(importedFileName, file.getAbsolutePath()));
                        }
                    }
                    // process message and enum defs
                    final String fileLevelJavaPackage =
                            (pbjJavaPackage != null) ? pbjJavaPackage : (protocJavaPackage + javaPackageSuffix);
                    for (final var item : parsedDoc.topLevelDef()) {
                        if (item.messageDef() != null) {
                            buildMessage(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.messageDef());
                        }
                        if (item.enumDef() != null) {
                            buildEnum(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.enumDef());
                        }
                        if (item.serviceDef() != null) {
                            buildService(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.serviceDef());
                        }
                    }
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        //		printDebug();
    }

    @NonNull
    static String normalizeFileName(final String fileName) {
        return fileName.replaceAll("\"", "")
                .replaceAll("/", quoteReplacement(FileSystems.getDefault().getSeparator()));
    }

    /** Debug dump internal state */
    private void printDebug() {
        System.out.println("== Package Map ================================================================");
        for (final var entry : pbjPackageMap.entrySet()) {
            System.out.printf("entry = %s = %s%n", entry.getKey(), entry.getValue());
        }
        System.out.println("== Complete Class Map =========================================================");
        for (final var entry : pbjCompleteClassMap.entrySet()) {
            System.out.printf("    %s => %s%n", entry.getKey(), entry.getValue());
        }
        System.out.println("== Enum Names =================================================================");
        for (final var enumName : enumNames) {
            System.out.printf("enumName = %s%n", enumName);
        }
        System.out.println("== Proto File Imports =========================================================");
        for (final var entry : protoFileImports.entrySet()) {
            System.out.printf("FILE - %s%n", entry.getKey());
            for (final var imp : entry.getValue()) {
                System.out.printf("    IMPORT - %s%n", imp);
            }
        }
        System.out.println("== Message Imports ============================================================");
        for (final var entry : msgEnumServiceByFile.entrySet()) {
            System.out.printf("FILE - %s%n", entry.getKey());
            for (final var entry2 : entry.getValue().entrySet()) {
                System.out.printf("    %s -> %s%n", entry2.getKey(), entry2.getValue());
            }
        }
        System.out.println("===============================================================================");
    }

    /**
     * Walk a msgDef def and build packages and enums lists
     *
     * @param fullQualifiedFile the fully qualified path and file name for file containing the
     *     message
     * @param fileLevelPbjJavaPackage the pbj java package relative to root package that the msgDef
     *     should be in
     * @param fileLevelProtocJavaPackage the protoc java package relative to root package that the
     *     msgDef should be in
     * @param msgDef protobuf msgDef def
     */
    private void buildMessage(
            final String fullQualifiedFile,
            final String fileLevelPbjJavaPackage,
            final String fileLevelProtocJavaPackage,
            final MessageDefContext msgDef) {
        final var msgName = msgDef.messageName().getText();
        // check for msgDef/enum level pbj package level override option
        final String messagePbjPackage = computePbjPackage(
                fileLevelPbjJavaPackage,
                msgDef.messageBody().messageElement(),
                Protobuf3Parser.MessageElementContext::optionStatement,
                Protobuf3Parser.MessageElementContext::optionComment,
                PBJ_MESSAGE_PACKAGE_OPTION_NAME);

        final String fullyQualifiedMessage = getFullyQualifiedProtoNameForContext(msgDef);
        comparableFieldsByMsg.computeIfAbsent(fullyQualifiedMessage, v -> extractComparableFields(msgDef));
        // insert into maps
        pbjPackageMap.put(fullyQualifiedMessage, messagePbjPackage);
        pbjCompleteClassMap.put(
                fullyQualifiedMessage, formatCompleteClass(new File(fullQualifiedFile), FileType.MODEL, msgDef));
        protocPackageMap.put(fullyQualifiedMessage, fileLevelProtocJavaPackage);
        msgEnumServiceByFile
                .computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>())
                .put(msgName, fullyQualifiedMessage);

        // handle child messages and enums
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) {
                buildMessage(fullQualifiedFile, messagePbjPackage, fileLevelProtocJavaPackage, item.messageDef());
            }
            if (item.enumDef() != null) {
                buildEnum(fullQualifiedFile, messagePbjPackage, fileLevelProtocJavaPackage, item.enumDef());
            }
        }
    }

    /**
     * Extract the set of fields that are comparable for a given message.
     * @param msgDef The message definition to get comparable fields for
     * @return a list of field names that are comparable
     */
    static List<String> extractComparableFields(final MessageDefContext msgDef) {
        if (msgDef.optionComment() == null || msgDef.optionComment().getText() == null) {
            return emptyList();
        }
        final var matcher = OPTION_COMMENT.matcher(msgDef.optionComment().getText());
        if (matcher.find()) {
            final String optionName = matcher.group(1);
            final String optionValue = matcher.group(2);
            if (optionName.equals(PBJ_COMPARABLE_OPTION_NAME)) {
                final Set<String> repeatedFields = new HashSet<>();
                final Set<String> regularFieldNames = msgDef.messageBody().messageElement().stream()
                        .filter(v -> v.field() != null)
                        .filter(v -> {
                            if (v.field().REPEATED() != null) {
                                repeatedFields.add(v.field().fieldName().getText());
                                return false;
                            } else {
                                return true;
                            }
                        })
                        .map(v -> v.field().fieldName().getText())
                        .collect(Collectors.toSet());
                final Set<String> oneOfFieldNames = msgDef.messageBody().messageElement().stream()
                        .filter(v -> v.oneof() != null)
                        .map(v -> v.oneof().oneofName().getText())
                        .collect(Collectors.toSet());
                final Set<String> allFieldNames = new HashSet<>();
                allFieldNames.addAll(regularFieldNames);
                allFieldNames.addAll(oneOfFieldNames);
                return Arrays.stream(optionValue.split(","))
                        .map(String::trim)
                        .peek(v -> {
                            if (repeatedFields.contains(v)) {
                                throw new IllegalArgumentException(
                                        "Field `%s` specified in `%s` option is repeated. Repeated fields are not supported by this option."
                                                .formatted(v, PBJ_COMPARABLE_OPTION_NAME));
                            }
                            if (!allFieldNames.contains(v)) {
                                throw new IllegalArgumentException("Field '%s' specified in %s option is not found."
                                        .formatted(v, PBJ_COMPARABLE_OPTION_NAME));
                            }
                        })
                        .collect(Collectors.toList());
            }
        }
        return emptyList();
    }

    /**
     * Walk an enum def and build packages and enums lists
     *
     * @param fullQualifiedFile the fully qualified path and file name for file containing the enum
     * @param fileLevelPbjJavaPackage the pbj java package relative to root package that the msgDef
     *     should be in
     * @param fileLevelProtocJavaPackage the protoc java package relative to root package that the
     *     msgDef should be in
     * @param enumDef protobuf enum def
     */
    private void buildEnum(
            final String fullQualifiedFile,
            final String fileLevelPbjJavaPackage,
            final String fileLevelProtocJavaPackage,
            final EnumDefContext enumDef) {
        final var enumName = enumDef.enumName().getText();
        final String enumPbjPackage = computePbjPackage(
                fileLevelPbjJavaPackage,
                enumDef.enumBody().enumElement(),
                Protobuf3Parser.EnumElementContext::optionStatement,
                Protobuf3Parser.EnumElementContext::optionComment,
                PBJ_ENUM_PACKAGE_OPTION_NAME);

        // insert into maps
        final var fullQualifiedEnumName = getFullyQualifiedProtoNameForContext(enumDef);
        pbjPackageMap.put(fullQualifiedEnumName, enumPbjPackage);
        pbjCompleteClassMap.put(
                fullQualifiedEnumName, formatCompleteClass(new File(fullQualifiedFile), FileType.MODEL, enumDef));
        protocPackageMap.put(fullQualifiedEnumName, fileLevelProtocJavaPackage);
        enumNames.add(fullQualifiedEnumName);
        msgEnumServiceByFile
                .computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>())
                .put(enumName, fullQualifiedEnumName);
    }

    /**
     * Walk a service def and build packages and services lists
     *
     * @param fullQualifiedFile the fully qualified path and file name for file containing the service
     * @param fileLevelPbjJavaPackage the pbj java package relative to root package that the serviceDef
     *     should be in
     * @param fileLevelProtocJavaPackage the protoc java package relative to root package that the
     *     msgDef should be in
     * @param serviceDef protobuf service def
     */
    private void buildService(
            final String fullQualifiedFile,
            final String fileLevelPbjJavaPackage,
            final String fileLevelProtocJavaPackage,
            final Protobuf3Parser.ServiceDefContext serviceDef) {
        final var serviceName = serviceDef.serviceName().getText();
        final String servicePbjPackage = computePbjPackage(
                fileLevelPbjJavaPackage,
                serviceDef.serviceElement(),
                Protobuf3Parser.ServiceElementContext::optionStatement,
                Protobuf3Parser.ServiceElementContext::optionComment,
                PBJ_SERVICE_PACKAGE_OPTION_NAME);

        // insert into maps
        final var fullQualifiedServiceName = getFullyQualifiedProtoNameForContext(serviceDef);
        pbjPackageMap.put(fullQualifiedServiceName, servicePbjPackage);
        pbjCompleteClassMap.put(
                fullQualifiedServiceName, formatCompleteClass(new File(fullQualifiedFile), FileType.MODEL, serviceDef));
        protocPackageMap.put(fullQualifiedServiceName, fileLevelProtocJavaPackage);
        msgEnumServiceByFile
                .computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>())
                .put(serviceName, fullQualifiedServiceName);
    }

    private static <T extends ParserRuleContext> String computePbjPackage(
            String pbjPackage,
            final List<T> elements,
            final Function<T, Protobuf3Parser.OptionStatementContext> optionStatementFunction,
            final Function<T, Protobuf3Parser.OptionCommentContext> optionCommentFunction,
            final String pbjPackageOptionName) {
        for (final T element : elements) {
            final var option = optionStatementFunction.apply(element);
            if (option != null) {
                if (PBJ_SERVICE_PACKAGE_OPTION_NAME.equals(
                        option.optionName().getText().replaceAll("[()]", ""))) {
                    pbjPackage = option.constant().getText().replaceAll("\"", "");
                }
            }
            final var optionComment = optionCommentFunction.apply(element);
            if (optionComment != null) {
                final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
                if (matcher.find()) {
                    final String optionName = matcher.group(1);
                    final String optionValue = matcher.group(2);
                    if (optionName.equals(PBJ_PACKAGE_OPTION_NAME)) {
                        pbjPackage = optionValue;
                    }
                }
            }
        }
        return pbjPackage;
    }

    /**
     * Get part of the fully qualified protobuf message name for a ParserRuleContext. This walks up
     * parse tree and computes name. So = <code>
     *  proto package + '.' + parent messages + '.' + message/enum name</code>
     *
     * @param ruleContext The ParserRuleContext to compute qualified name for, can be
     *     MessageDefContext or EnumDefContext
     * @return part of fully qualified protobuf name
     */
    public String getFullyQualifiedProtoNameForContext(final ParserRuleContext ruleContext) {
        String thisName = "";
        if (ruleContext instanceof final Protobuf3Parser.ProtoContext parsedDoc) {
            // get proto package
            final var packageStatement = parsedDoc.packageStatement().stream().findFirst();
            thisName = packageStatement.isEmpty()
                    ? ""
                    : packageStatement.get().fullIdent().getText();
        } else if (ruleContext instanceof final EnumDefContext enumDef) {
            final String parentPart = getFullyQualifiedProtoNameForContext(enumDef.getParent());
            thisName = getFullyQualifiedProtoNameForContext(enumDef.getParent())
                    + "."
                    + enumDef.enumName().getText();
        } else if (ruleContext instanceof final MessageDefContext msgDef) {
            thisName = getFullyQualifiedProtoNameForContext(msgDef.getParent())
                    + "."
                    + msgDef.messageName().getText();
        } else if (ruleContext instanceof final Protobuf3Parser.ServiceDefContext serviceDef) {
            thisName = getFullyQualifiedProtoNameForContext(serviceDef.getParent())
                    + "."
                    + serviceDef.serviceName().getText();
        } else if (ruleContext.getParent() != null) {
            thisName = getFullyQualifiedProtoNameForContext(ruleContext.getParent());
        }
        return Common.removingLeadingDot(thisName);
    }

    /**
     * Get a list of fields that are comparable for a given message.
     * @param message The message to get comparable fields for
     * @return a list of field names that are comparable
     */
    List<String> getComparableFields(MessageDefContext message) {
        return comparableFieldsByMsg.get(getFullyQualifiedProtoNameForContext(message));
    }
}
