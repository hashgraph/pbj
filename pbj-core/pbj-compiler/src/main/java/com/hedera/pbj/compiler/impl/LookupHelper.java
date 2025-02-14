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
     * Map from fully qualified msgDef name to fully qualified pbj java package, not including java
     * class
     */
    private final Map<String, String> pbjPackageMap = new HashMap<>();

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
    private final Map<String, Map<String, String>> msgAndEnumByFile = new HashMap<>();

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
     */
    public LookupHelper(final Iterable<File> allSrcFiles) {
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
        } else if (context instanceof final MessageTypeContext msgTypeContext) {
            final String messageType = msgTypeContext.getText();
            return messageType.contains(".") ? messageType.substring(messageType.lastIndexOf('.') + 1) : messageType;
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getUnqualifiedProtoName"));
        }
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
        if (context instanceof final MessageTypeContext msgTypeContext) {
            final String messageType = msgTypeContext.getText();
            // check if fully qualified
            if (messageType.contains(".")) {
                return messageType;
            }
            // check local file message types
            final var messageMapLocal = msgAndEnumByFile.get(protoSrcFile.getAbsolutePath());
            if (messageMapLocal == null) {
                throw new PbjCompilerException(FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE.formatted(protoSrcFile));
            }
            final String nameFoundInLocalFile = messageMapLocal.get(messageType);
            if (nameFoundInLocalFile != null) {
                return nameFoundInLocalFile;
            }
            // message type is not from local file so check imported files
            for (final var importedProtoFilePath : protoFileImports.get(protoSrcFile.getAbsolutePath())) {
                final var messageMap = msgAndEnumByFile.get(importedProtoFilePath);
                if (messageMap == null) {
                    throw new PbjCompilerException(
                            FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE.formatted(importedProtoFilePath));
                }
                final var found = messageMap.get(messageType);
                if (found != null) {
                    return found;
                }
            }
            // we failed to find
            final Object[] importsArray =
                    protoFileImports.get(protoSrcFile.getAbsolutePath()).toArray();
            final String importsString = Arrays.toString(importsArray);
            throw new PbjCompilerException(
                    FAILED_TO_FIND_MSG_TYPE_MESSAGE.formatted(messageType, protoSrcFile, importsString));
        } else if (context instanceof MessageDefContext || context instanceof EnumDefContext) {
            final Map<String, String> fileMap = msgAndEnumByFile.get(protoSrcFile.getAbsolutePath());
            if (fileMap == null) {
                throw new PbjCompilerException(FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE.formatted(protoSrcFile));
            }
            return fileMap.get(getUnqualifiedProtoName(context));
        } else {
            throw new UnsupportedOperationException(
                    METHOD_WRONG_CONTEXT_MESSAGE.formatted("getFullyQualifiedProtoName"));
        }
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
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getUnqualifiedClass"));
        }
        if (isEnum) {
            return name;
        } else {
            return switch (fileType) {
                case MODEL, PROTOC -> name;
                case SCHEMA -> name + FileAndPackageNamesConfig.SCHEMA_JAVA_FILE_SUFFIX;
                case CODEC -> name + FileAndPackageNamesConfig.CODEC_JAVA_FILE_SUFFIX;
                case JSON_CODEC -> name + FileAndPackageNamesConfig.JSON_CODEC_JAVA_FILE_SUFFIX;
                case TEST -> name + FileAndPackageNamesConfig.TEST_JAVA_FILE_SUFFIX;
            };
        }
    }

    /**
     * Get the Java package a class should be generated into for a given msgDef and file type.
     *
     * @param protoSrcFile the proto source file that the message or enum is in
     * @param fileType The type of file we want the package for
     * @param context Parser Context, a message or enum
     * @return java package to put model class in
     */
    @Nullable
    String getPackage(final File protoSrcFile, final FileType fileType, final ParserRuleContext context) {
        if (context instanceof MessageDefContext
                || context instanceof EnumDefContext
                || context instanceof MessageTypeContext) {
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
                    "getPackageForMsgOrEnum", context.getClass().getName()));
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
            final String packageName = getPackage(protoSrcFile, fileType, context);
            final String messageName = getUnqualifiedClass(protoSrcFile, fileType, context);
            // protoc supports nested classes so need parent classes/messages
            final String parentClasses;
            if (fileType == FileType.PROTOC && context.getParent() instanceof MessageElementContext) {
                final StringBuilder sb = new StringBuilder();
                ParserRuleContext parent = context.getParent();
                while (!(parent instanceof TopLevelDefContext)) {
                    if (parent instanceof MessageDefContext) {
                        sb.insert(0, '.');
                        sb.insert(0, ((MessageDefContext) parent).messageName().getText());
                    }
                    parent = parent.getParent();
                }
                parentClasses = sb.toString();
            } else {
                parentClasses = "";
            }
            return packageName + '.' + parentClasses + messageName;
        } else {
            throw new UnsupportedOperationException(METHOD_WRONG_CONTEXT_MESSAGE.formatted("getFullyQualifiedClass"));
        }
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
                    msgAndEnumByFile.computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>());
                    // look for PBJ package option
                    String pbjJavaPackage = null;
                    String protocJavaPackage = null;
                    // check for custom option
                    for (final var option : parsedDoc.optionStatement()) {
                        switch (option.optionName().getText().replaceAll("[()]", "")) {
                            case PBJ_PACKAGE_OPTION_NAME -> pbjJavaPackage =
                                    option.constant().getText().replaceAll("\"", "");
                            case PROTOC_JAVA_PACKAGE_OPTION_NAME -> protocJavaPackage =
                                    option.constant().getText().replaceAll("\"", "");
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
                        throw new PbjCompilerException(FILE_MISSING_PACKAGE_OPTION_MESSAGE.formatted(
                                "", file.getAbsolutePath(), PBJ_PACKAGE_OPTION_NAME, PROTOC_JAVA_PACKAGE_OPTION_NAME));
                    } else if (pbjJavaPackage == null) {
                        System.err.printf(FILE_MISSING_PACKAGE_OPTION_MESSAGE.formatted(
                                "WARNING, ",
                                file.getAbsolutePath(),
                                PBJ_PACKAGE_OPTION_NAME,
                                PROTOC_JAVA_PACKAGE_OPTION_NAME));
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
                    final String fileLevelJavaPackage = (pbjJavaPackage != null) ? pbjJavaPackage : protocJavaPackage;
                    for (final var item : parsedDoc.topLevelDef()) {
                        if (item.messageDef() != null)
                            buildMessage(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.messageDef());
                        if (item.enumDef() != null)
                            buildEnum(fullQualifiedFile, fileLevelJavaPackage, protocJavaPackage, item.enumDef());
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
        for (final var entry : msgAndEnumByFile.entrySet()) {
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
        String messagePbjPackage = fileLevelPbjJavaPackage;

        final String fullyQualifiedMessage = getFullyQualifiedProtoNameForMsgOrEnum(msgDef);
        comparableFieldsByMsg.computeIfAbsent(fullyQualifiedMessage, v -> extractComparableFields(msgDef));
        for (final var element : msgDef.messageBody().messageElement()) {
            final var option = element.optionStatement();
            if (option != null) {
                if (PBJ_MESSAGE_PACKAGE_OPTION_NAME.equals(
                        option.optionName().getText().replaceAll("[()]", ""))) {
                    messagePbjPackage = option.constant().getText().replaceAll("\"", "");
                }
            }
            final var optionComment = element.optionComment();
            if (optionComment != null) {
                final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
                if (matcher.find()) {
                    final String optionName = matcher.group(1);
                    final String optionValue = matcher.group(2);
                    if (optionName.equals(PBJ_PACKAGE_OPTION_NAME)) {
                        messagePbjPackage = optionValue;
                    }
                }
            }
        }
        // insert into maps
        pbjPackageMap.put(fullyQualifiedMessage, messagePbjPackage);
        protocPackageMap.put(fullyQualifiedMessage, fileLevelProtocJavaPackage);
        msgAndEnumByFile
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
        // check for msgDef/enum level pbj package level override option
        String enumPbjPackage = fileLevelPbjJavaPackage;
        for (final var element : enumDef.enumBody().enumElement()) {
            final var option = element.optionStatement();
            if (option != null) {
                if (PBJ_ENUM_PACKAGE_OPTION_NAME.equals(
                        option.optionName().getText().replaceAll("[()]", ""))) {
                    enumPbjPackage = option.constant().getText().replaceAll("\"", "");
                }
            }
            final var optionComment = element.optionComment();
            if (optionComment != null) {
                final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
                if (matcher.find()) {
                    final String optionName = matcher.group(1);
                    final String optionValue = matcher.group(2);
                    if (optionName.equals(PBJ_PACKAGE_OPTION_NAME)) {
                        enumPbjPackage = optionValue;
                    }
                }
            }
        }

        // insert into maps
        final var fullQualifiedEnumName = getFullyQualifiedProtoNameForMsgOrEnum(enumDef);
        pbjPackageMap.put(fullQualifiedEnumName, enumPbjPackage);
        protocPackageMap.put(fullQualifiedEnumName, fileLevelProtocJavaPackage);
        enumNames.add(fullQualifiedEnumName);
        msgAndEnumByFile
                .computeIfAbsent(fullQualifiedFile, fqf -> new HashMap<>())
                .put(enumName, fullQualifiedEnumName);
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
    private static String getFullyQualifiedProtoNameForMsgOrEnum(final ParserRuleContext ruleContext) {
        String thisName = "";
        if (ruleContext instanceof final Protobuf3Parser.ProtoContext parsedDoc) {
            // get proto package
            final var packageStatement = parsedDoc.packageStatement().stream().findFirst();
            thisName = packageStatement.isEmpty()
                    ? ""
                    : packageStatement.get().fullIdent().getText();
        } else if (ruleContext instanceof final EnumDefContext enumDef) {
            final String parentPart = getFullyQualifiedProtoNameForMsgOrEnum(enumDef.getParent());
            thisName = getFullyQualifiedProtoNameForMsgOrEnum(enumDef.getParent())
                    + "."
                    + enumDef.enumName().getText();
        } else if (ruleContext instanceof final MessageDefContext msgDef) {
            thisName = getFullyQualifiedProtoNameForMsgOrEnum(msgDef.getParent())
                    + "."
                    + msgDef.messageName().getText();
        } else if (ruleContext.getParent() != null) {
            thisName = getFullyQualifiedProtoNameForMsgOrEnum(ruleContext.getParent());
        }
        return Common.removingLeadingDot(thisName);
    }

    /**
     * Get a list of fields that are comparable for a given message.
     * @param message The message to get comparable fields for
     * @return a list of field names that are comparable
     */
    List<String> getComparableFields(MessageDefContext message) {
        return comparableFieldsByMsg.get(getFullyQualifiedProtoNameForMsgOrEnum(message));
    }
}
