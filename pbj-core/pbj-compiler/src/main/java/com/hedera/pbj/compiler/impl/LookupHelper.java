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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Class that manages packages and enum names that are used more than one place in code generation
 */
@SuppressWarnings({"unused", "DuplicatedCode"})
public final class LookupHelper {
    /** REGEX pattern to match options in special option comments */
    private static final Pattern OPTION_COMMENT =
            Pattern.compile("//\\s+<<<\\s*([\\w.]+)\\s*=\\s*\"?([^\"]+)\"?\\s*>>>");
    /** The option name for PBJ package at file level */
    private static final String PBJ_PACKAGE_OPTION_NAME = "pbj.java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_MESSAGE_PACKAGE_OPTION_NAME = "pbj.message_java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_ENUM_PACKAGE_OPTION_NAME = "pbj.enum_java_package";
    /** The option name for PBJ package at msgDef level */
    private static final String PBJ_SERVICE_PACKAGE_OPTION_NAME = "pbj.service_java_package";

    private static final String PBJ_COMPARABLE_OPTION_NAME = "pbj.comparable";
    private static final String PBJ_CACHEABLE_OPTION_NAME = "pbj.cacheable";

    /** The option name for protoc java package at file level */
    private static final String PROTOC_JAVA_PACKAGE_OPTION_NAME = "java_package";

    /** Extension for protobuf files */
    public static final String PROTO_EXTENSIION = ".proto";

    // Exception message templates
    private static final String METHOD_WRONG_CONTEXT_MESSAGE = "%s only supports MessageDefContext or EnumDefContext";
    private static final String FAILED_TO_FIND_LOCAL_MSG_MAP_MESSAGE =
            "Failed to find messageMapLocal for proto file [%s]";
    private static final String FAILED_TO_FIND_MSG_TYPE_MESSAGE =
            "Failed to find fully qualified message type for [%s] in file [%s]%n" + "Imports: %s%n"
                    + "This usually means:%n"
                    + "  - The type is not imported (add: import \"path/to/file.proto\")%n"
                    + "  - The type name is misspelled%n"
                    + "  - The type is in a different package than expected";
    private static final String PACKAGE_NOT_FOUND_MESSAGE =
            "Could not find %s package for message or enum [%s] in file [%s]";
    private static final String LIMITED_CONTEXT_OPTIONS_SUPPORT_MESSAGE =
            "%s only supports MessageDefContext, EnumDefContext or MessageTypeContext not [%s]";
    private static final String FILE_MISSING_PACKAGE_OPTION_MESSAGE =
            "%sProto file [%s] does not contain \"%s\" or \"%s\" options.%n";
    private static final String IMPORT_NOT_FOUND_MESSAGE =
            "Import \"%s\" in proto file \"%s\" can not be found in src files.%n" + "Please verify:%n"
                    + "  - The import path is correct%n"
                    + "  - The imported .proto file is in your source directory%n"
                    + "  - The file path uses forward slashes (/) even on Windows";

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
     * Map from fully qualified message names that are cacheable in the Codec's parser to their default cache size.
     */
    private final Map<String, Integer> cacheableMessagesCacheSize = new HashMap<>();

    /**
     * Set of all fully qualified message names that are enums, so we can check if a message is an
     * enum or not
     */
    private final Set<String> enumNames = new HashSet<>();

    private final Map<String, MessageDefContext> messageDefinitions = new HashMap<>();
    private final Map<String, File> messageFiles = new HashMap<>();
    private final Set<String> diffRoots = new HashSet<>();

    /**
     * Model classes (Java package plus complete class name) generated in this build from source files, as opposed to
     * messages from dependency jars. Generated codecs call each other's static methods directly only for these.
     */
    private final Set<String> modelClassesInThisBuild = new HashSet<>();

    /** Fully qualified proto message names of the models in {@link #modelClassesInThisBuild}, by model class. */
    private final Map<String, String> messagesByModelClass = new HashMap<>();

    /** Fast code generation options set by each proto file's option comments, by absolute path. */
    private final Map<String, PerformanceOptions> fileOptions = new HashMap<>();

    /** Fast code generation options set for the whole project. */
    private PerformanceOptions projectOptions = PerformanceOptions.NONE;

    private final Set<String> diffEqualityMessages = new HashSet<>();

    /**
     * Roots with at most this many mask entries get no copy-builder tracking from the project setting. Comparing a
     * few fields costs about as much as checking a mask, and tracking is per class, so small ubiquitous types such as
     * IDs and timestamps would otherwise carry tracking state in every instance. An explicit
     * {@code pbj.copy_builder_tracking} comment overrides this.
     */
    public static final int MAX_UNTRACKED_DIFF_ENTRIES = 8;

    /**
     * Select roots explicitly or from a catalog's direct message fields, then walk nested dependencies.
     * Misspelled selections fail compilation rather than silently losing optimization.
     */
    public void selectDiffMessages(Set<String> roots, Set<String> containers) {
        diffRoots.clear();
        diffEqualityMessages.clear();
        for (Map.Entry<String, MessageDefContext> entry : messageDefinitions.entrySet()) {
            final MessageDefContext message = entry.getValue();
            if (messageOption(message, "pbj.diff") || messageOption(message, "pbj.copy_builder_tracking")) {
                diffRoots.add(entry.getKey());
            }
        }
        for (String root : roots) {
            requireMessage(root);
            diffRoots.add(root);
        }
        for (String container : containers) {
            requireMessage(container);
            diffRoots.addAll(messageDependencies(container));
        }
        final ArrayDeque<String> pending = new ArrayDeque<>(diffRoots);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (diffEqualityMessages.add(name)) pending.addAll(messageDependencies(name));
        }
    }

    private void requireMessage(String name) {
        if (!messageDefinitions.containsKey(name)) {
            throw new IllegalArgumentException("Unknown diff message: " + name);
        }
    }

    private Set<String> messageDependencies(String name) {
        final Set<String> dependencies = new HashSet<>();
        for (MessageElementContext element :
                messageDefinitions.get(name).messageBody().messageElement()) {
            if (element.field() != null) {
                addDependency(name, element.field().type_(), dependencies);
            }
            if (element.mapField() != null) {
                addDependency(name, element.mapField().type_(), dependencies);
            }
            if (element.oneof() != null) {
                for (Protobuf3Parser.OneofFieldContext field : element.oneof().oneofField()) {
                    addDependency(name, field.type_(), dependencies);
                }
            }
        }
        return dependencies;
    }

    private void addDependency(String owner, Protobuf3Parser.Type_Context type, Set<String> dependencies) {
        if (type.messageType() == null) return;
        String name = getFullyQualifiedProtoName(messageFiles.get(owner), type.messageType());
        // Google wrappers are mapped to Java scalars; enums need no generated comparison.
        if (messageDefinitions.containsKey(name)) dependencies.add(name);
    }

    /** Whether this message has the full stateless diff APIs. */
    public boolean hasDiffApis(MessageDefContext message) {
        return diffRoots.contains(getFullyQualifiedProtoNameForContext(message));
    }

    /** Whether this message needs encoding-aware equality as a root or a nested dependency. */
    public boolean hasDiffEquality(MessageDefContext message) {
        return diffEqualityMessages.contains(getFullyQualifiedProtoNameForContext(message));
    }

    /** Number of diff mask entries: one per ordinary or map field, and one per oneof group. */
    public static int diffEntryCount(MessageDefContext message) {
        int count = 0;
        for (MessageElementContext element : message.messageBody().messageElement()) {
            if (element.field() != null || element.mapField() != null || element.oneof() != null) count++;
        }
        return count;
    }

    /**
     * Record which proto files this build generates code for, so generators can tell messages generated alongside
     * them from messages in dependencies.
     *
     * @param sourceFiles the proto files code is generated for
     */
    public void markSourceFiles(Iterable<File> sourceFiles) {
        final Set<String> paths = new HashSet<>();
        sourceFiles.forEach(file -> paths.add(file.getAbsolutePath()));
        messageFiles.forEach((message, file) -> {
            if (paths.contains(file.getAbsolutePath())) {
                final String modelClass = pbjPackageMap.get(message) + "." + pbjCompleteClassMap.get(message);
                modelClassesInThisBuild.add(modelClass);
                messagesByModelClass.put(modelClass, message);
            }
        });
    }

    /**
     * Whether a message's model, and so its codec, is generated in this build from a source file. Messages from
     * dependency jars may come from another PBJ version, so generated code reaches them only through their public
     * {@code Codec}.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return true if the codec is generated in this build
     */
    public boolean isGeneratedInThisBuild(String modelPackage, String completeClass) {
        return modelClassesInThisBuild.contains(modelPackage + "." + completeClass);
    }

    /**
     * Set the fast code generation options for the whole project.
     *
     * @param projectOptions the options set in Gradle
     */
    public void setProjectOptions(PerformanceOptions projectOptions) {
        this.projectOptions = projectOptions;
    }

    /**
     * Whether a message's codec has a byte-array parser: the message has {@code pbj.fast_parse}, from its comments, its
     * file's, or the project.
     *
     * @param message the message
     * @return true if the codec generated for it has an array parser
     */
    public boolean hasArrayParser(MessageDefContext message) {
        return fastParse(getFullyQualifiedProtoNameForContext(message), message);
    }

    /**
     * Whether the codec of a message generated in this build has a byte-array parser, so a generated array parser can
     * call it directly for a nested message.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return true if the message is generated in this build with an array parser
     */
    public boolean hasArrayParser(String modelPackage, String completeClass) {
        final String message = messagesByModelClass.get(modelPackage + "." + completeClass);
        return message != null && hasArrayParser(messageDefinitions.get(message));
    }

    /**
     * Whether a message's codec writes in reverse ({@code pbj.fast_write}, from its comments, its file's, or the
     * project), so it has a static {@code writeReverseTo}.
     *
     * @param message the message
     * @return true if the codec generated for it has a reverse writer
     */
    public boolean hasReverseWriter(MessageDefContext message) {
        return option(getFullyQualifiedProtoNameForContext(message), message, PerformanceOptions::fastWrite);
    }

    /**
     * Whether the codec of a message generated in this build has a static {@code writeReverseTo}, so generated reverse
     * writers can call it directly for a nested message.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return true if the message is generated in this build with a reverse writer
     */
    public boolean hasReverseWriter(String modelPackage, String completeClass) {
        final String message = messagesByModelClass.get(modelPackage + "." + completeClass);
        return message != null && hasReverseWriter(messageDefinitions.get(message));
    }

    /** The messages that get a view, by fully qualified proto name, computed on first use. */
    private Set<String> views;

    /**
     * Why each message that asks for a view in its own or its file's comments gets none, by fully qualified proto name.
     * Messages that have {@code pbj.views} only from the project are left out, so turning everything on doesn't report
     * every message with a map.
     */
    private final Map<String, String> viewDenials = new HashMap<>();

    /**
     * Whether a message gets a zero-copy view class: it has {@code pbj.views} (from its comments, its file's, or the
     * project), it has no maps or repeated {@code google.protobuf} wrapper types (not supported yet), and every message-typed
     * field's type is generated in this build and gets a view too.
     *
     * @param message the message
     * @return true if a view is generated for it
     */
    public boolean hasView(MessageDefContext message) {
        return views().contains(getFullyQualifiedProtoNameForContext(message));
    }

    /**
     * Why a message that has {@code pbj.views} gets no view.
     *
     * @param message the message
     * @return the reason, or null if the message gets a view or does not ask for one
     */
    public String viewDenial(MessageDefContext message) {
        views();
        return viewDenials.get(getFullyQualifiedProtoNameForContext(message));
    }

    /**
     * Compute which messages get views, as a greatest fixed point: start with every message that asks for a view and
     * supports one on its own, then drop those with a message-typed field whose type was dropped, until nothing
     * changes. Messages that contain each other, directly or through others, keep their views only if all of them can.
     */
    private Set<String> views() {
        if (views != null) {
            return views;
        }
        final Set<String> generated = new HashSet<>(messagesByModelClass.values());
        // each candidate's message-typed fields: field name and type, which must get views too
        final Map<String, Map<String, String>> candidates = new HashMap<>();
        for (Map.Entry<String, MessageDefContext> entry : messageDefinitions.entrySet()) {
            final String name = entry.getKey();
            if (generated.contains(name) && option(name, entry.getValue(), PerformanceOptions::views)) {
                final Map<String, String> nested = viewNestedTypes(name, entry.getValue(), generated);
                if (nested != null) {
                    candidates.put(name, nested);
                }
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var it = candidates.entrySet().iterator(); it.hasNext(); ) {
                final var candidate = it.next();
                for (Map.Entry<String, String> field : candidate.getValue().entrySet()) {
                    if (!candidates.containsKey(field.getValue())) {
                        viewDenials.put(
                                candidate.getKey(),
                                "field '%s' has type %s, which gets no view"
                                        .formatted(field.getKey(), field.getValue()));
                        it.remove();
                        changed = true;
                        break;
                    }
                }
            }
        }
        viewDenials
                .keySet()
                .removeIf(denied -> !PerformanceOptions.resolve(
                        levels(denied, messageDefinitions.get(denied)).subList(0, 2),
                        PerformanceOptions::views,
                        true,
                        false));
        views = candidates.keySet();
        return views;
    }

    /**
     * The message-typed fields of a message that asks for a view, by field name, with their types' proto names; or
     * null, with the reason recorded, if the message can't have a view whatever its nested types get.
     */
    private Map<String, String> viewNestedTypes(String name, MessageDefContext message, Set<String> generated) {
        final Map<String, String> nested = new HashMap<>();
        for (MessageElementContext element : message.messageBody().messageElement()) {
            if (element.mapField() != null) {
                viewDenials.put(
                        name,
                        "map field '%s' (maps are not supported by views)"
                                .formatted(element.mapField().mapName().getText()));
                return null;
            }
            final List<Map.Entry<String, Protobuf3Parser.Type_Context>> fields = new ArrayList<>();
            if (element.oneof() != null) {
                for (var alternative : element.oneof().oneofField()) {
                    fields.add(Map.entry(alternative.fieldName().getText(), alternative.type_()));
                }
            }
            final var field = element.field();
            if (field != null && field.fieldName() != null) {
                // repeated wrapper types are not supported
                final var type = field.type_().messageType();
                if (field.REPEATED() != null
                        && type != null
                        && WRAPPER_TYPES.contains(type.messageName().getText())) {
                    viewDenials.put(
                            name,
                            "repeated wrapper type field '%s' (not supported by views)"
                                    .formatted(field.fieldName().getText()));
                    return null;
                }
                fields.add(Map.entry(field.fieldName().getText(), field.type_()));
            }
            for (Map.Entry<String, Protobuf3Parser.Type_Context> f : fields) {
                final var type = f.getValue().messageType();
                if (type == null || WRAPPER_TYPES.contains(type.messageName().getText())) {
                    continue;
                }
                final String nestedName = getFullyQualifiedProtoName(messageFiles.get(name), type);
                if (enumNames.contains(nestedName)) {
                    continue;
                }
                if (!generated.contains(nestedName)) {
                    viewDenials.put(
                            name,
                            "field '%s' has type %s, which is not generated in this build"
                                    .formatted(f.getKey(), nestedName));
                    return null;
                }
                nested.put(f.getKey(), nestedName);
            }
        }
        return nested;
    }

    /**
     * Whether the model of a message generated in this build has a view, so another view can return it.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return true if the message is generated in this build with a view
     */
    public boolean hasView(String modelPackage, String completeClass) {
        final String message = messagesByModelClass.get(modelPackage + "." + completeClass);
        return message != null && hasView(messageDefinitions.get(message));
    }

    /** The messages that get writers, by fully qualified proto name, computed on first use. */
    private Set<String> writers;

    /** The largest encoded size of each message, or -1 if it is unbounded, by fully qualified proto name. */
    private final Map<String, Long> maxEncodedSizes = new HashMap<>();

    /**
     * Whether a message gets a writer class ({@code Xxx.Writer}): it is generated in this build, and it has
     * {@code pbj.writers} (from its comments, its file's, or the project), or it is reachable from a message that does,
     * through message-typed fields of any kind.
     *
     * @param message the message
     * @return true if a writer is generated for it
     * @throws PbjCompilerException if a message reachable from one with writers turns them off explicitly
     */
    public boolean hasWriter(MessageDefContext message) {
        return writers().contains(getFullyQualifiedProtoNameForContext(message));
    }

    /**
     * Whether the model of a message generated in this build has a writer, so another writer can open it as a child.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return true if the message is generated in this build with a writer
     */
    public boolean hasWriter(String modelPackage, String completeClass) {
        final String message = messagesByModelClass.get(modelPackage + "." + completeClass);
        return message != null && writers().contains(message);
    }

    /**
     * Compute which messages get writers: the transitive closure of the messages that ask for them, over their
     * message-typed fields (singular, repeated, oneof alternatives and map values) in this build. The closure is a
     * worklist over a visited set, so cycles of any length are reached once. Messages from dependency jars are not
     * regenerated, so they get no writer; fields of their types are written from objects or pre-encoded bytes.
     */
    private Set<String> writers() {
        if (writers != null) {
            return writers;
        }
        final Set<String> generated = new HashSet<>(messagesByModelClass.values());
        // each reached message, with the message and field it was reached through; roots have none
        final Map<String, String[]> reachedFrom = new HashMap<>();
        final ArrayDeque<String> pending = new ArrayDeque<>();
        messageDefinitions.keySet().stream()
                .filter(generated::contains)
                .sorted()
                .filter(name -> PerformanceOptions.resolveExplicit(
                        levels(name, messageDefinitions.get(name)), PerformanceOptions::writers, false))
                .forEach(root -> {
                    reachedFrom.put(root, null);
                    pending.add(root);
                });
        while (!pending.isEmpty()) {
            final String name = pending.removeFirst();
            for (Map.Entry<String, String> field : messageFields(name).entrySet()) {
                final String type = field.getValue();
                if (!generated.contains(type) || reachedFrom.containsKey(type)) {
                    continue;
                }
                reachedFrom.put(type, new String[] {name, field.getKey()});
                if (Boolean.FALSE.equals(PerformanceOptions.resolveExplicit(
                        levels(type, messageDefinitions.get(type)).subList(0, 2), PerformanceOptions::writers, null))) {
                    throw new PbjCompilerException(("Message %s has <<<pbj.writers = false>>>, but is reachable from a"
                                    + " message with writers: %s. Every message reachable from one with writers gets"
                                    + " them: remove the option, or turn writers off where the path starts")
                            .formatted(type, writerPath(type, reachedFrom)));
                }
                pending.add(type);
            }
        }
        writers = reachedFrom.keySet();
        return writers;
    }

    /** The path a message was reached by, from a root that asked for writers, for error messages. */
    private static String writerPath(final String message, final Map<String, String[]> reachedFrom) {
        final List<String> steps = new ArrayList<>();
        String current = message;
        while (reachedFrom.get(current) != null) {
            final String[] from = reachedFrom.get(current);
            steps.addFirst("." + from[1] + " -> " + current);
            current = from[0];
        }
        return current + String.join("", steps);
    }

    /**
     * The message-typed fields of a message, by field name, with their types' fully qualified proto names: singular and
     * repeated fields, oneof alternatives and map values. {@code google.protobuf} wrapper types and enums are left out.
     */
    private Map<String, String> messageFields(final String name) {
        final Map<String, String> fields = new java.util.LinkedHashMap<>();
        for (MessageElementContext element :
                messageDefinitions.get(name).messageBody().messageElement()) {
            final List<Map.Entry<String, Protobuf3Parser.Type_Context>> types = new ArrayList<>();
            if (element.field() != null && element.field().fieldName() != null) {
                types.add(Map.entry(
                        element.field().fieldName().getText(), element.field().type_()));
            }
            if (element.mapField() != null) {
                types.add(Map.entry(
                        element.mapField().mapName().getText(),
                        element.mapField().type_()));
            }
            if (element.oneof() != null) {
                for (var alternative : element.oneof().oneofField()) {
                    types.add(Map.entry(alternative.fieldName().getText(), alternative.type_()));
                }
            }
            for (Map.Entry<String, Protobuf3Parser.Type_Context> field : types) {
                final MessageTypeContext type = field.getValue().messageType();
                if (type == null || WRAPPER_TYPES.contains(type.messageName().getText())) {
                    continue;
                }
                final String typeName = getFullyQualifiedProtoName(messageFiles.get(name), type);
                if (!enumNames.contains(typeName)) {
                    fields.put(field.getKey(), typeName);
                }
            }
        }
        return fields;
    }

    /**
     * The number of bytes a writer reserves for the length prefix of a nested message of this type, before it knows the
     * message's length: one when the message's largest encoding is under 128 bytes, which needs only bounded fields,
     * otherwise two.
     *
     * @param modelPackage the Java package of the message's model class
     * @param completeClass the model's complete class name, including outer classes
     * @return 1 or 2
     */
    public int writerLengthReserve(String modelPackage, String completeClass) {
        final String message = messagesByModelClass.get(modelPackage + "." + completeClass);
        if (message == null) {
            return 2;
        }
        final long max = maxEncodedSize(message, new HashSet<>());
        return max >= 0 && max < 128 ? 1 : 2;
    }

    /**
     * The largest size a message can encode to, with each field written once, or -1 if it has strings, bytes, repeated
     * fields, maps, or messages that do, or is part of a cycle.
     */
    private long maxEncodedSize(final String name, final Set<String> visiting) {
        final Long known = maxEncodedSizes.get(name);
        if (known != null) {
            return known;
        }
        if (!visiting.add(name) || !messageDefinitions.containsKey(name)) {
            return -1;
        }
        long total = 0;
        for (MessageElementContext element :
                messageDefinitions.get(name).messageBody().messageElement()) {
            long size = 0;
            if (element.mapField() != null) {
                size = -1;
            } else if (element.field() != null && element.field().fieldName() != null) {
                size = element.field().REPEATED() != null
                        ? -1
                        : maxFieldSize(
                                name,
                                Integer.parseInt(element.field().fieldNumber().getText()),
                                element.field().type_(),
                                visiting);
            } else if (element.oneof() != null) {
                for (var alternative : element.oneof().oneofField()) {
                    final long alternativeSize = maxFieldSize(
                            name, Integer.parseInt(alternative.fieldNumber().getText()), alternative.type_(), visiting);
                    size = alternativeSize < 0 || size < 0 ? -1 : Math.max(size, alternativeSize);
                }
            }
            if (size < 0) {
                total = -1;
                break;
            }
            total += size;
        }
        visiting.remove(name);
        maxEncodedSizes.put(name, total);
        return total;
    }

    /** The largest size of one field, tag included, or -1 if it is unbounded. */
    private long maxFieldSize(
            final String owner, final int number, final Protobuf3Parser.Type_Context type, final Set<String> visiting) {
        final long tag = (Long.SIZE - Long.numberOfLeadingZeros(((long) number << 3) | 7) + 6) / 7;
        final long value;
        if (type.BOOL() != null) {
            value = 1;
        } else if (type.FIXED32() != null || type.SFIXED32() != null || type.FLOAT() != null) {
            value = 4;
        } else if (type.FIXED64() != null || type.SFIXED64() != null || type.DOUBLE() != null) {
            value = 8;
        } else if (type.UINT32() != null || type.SINT32() != null) {
            value = 5;
        } else if (type.STRING() != null || type.BYTES() != null) {
            value = -1;
        } else if (type.messageType() != null || type.enumType() != null) {
            final String typeName = type.messageType() != null
                    ? getFullyQualifiedProtoName(messageFiles.get(owner), type.messageType())
                    : null;
            if (type.enumType() != null || enumNames.contains(typeName)) {
                value = 10;
            } else if (WRAPPER_TYPES.contains(type.messageType().messageName().getText())) {
                value = switch (type.messageType().messageName().getText()) {
                    case "StringValue", "BytesValue" -> -1;
                    // the length, then the wrapped value's one-byte tag and value
                    case "BoolValue" -> 1 + 1 + 1;
                    case "FloatValue" -> 1 + 1 + 4;
                    case "DoubleValue" -> 1 + 1 + 8;
                    default -> 1 + 1 + 10;
                };
            } else {
                final long nested = maxEncodedSize(typeName, visiting);
                value = nested < 0 ? -1 : (Long.SIZE - Long.numberOfLeadingZeros(nested | 1) + 6) / 7 + nested;
            }
        } else {
            // int32, int64, uint64, sint64: up to ten bytes
            value = 10;
        }
        return value < 0 ? -1 : tag + value;
    }

    /** The {@code google.protobuf} wrapper types, which views read as boxed values. */
    private static final Set<String> WRAPPER_TYPES = Set.of(
            "StringValue",
            "BoolValue",
            "Int32Value",
            "UInt32Value",
            "Int64Value",
            "UInt64Value",
            "FloatValue",
            "DoubleValue",
            "BytesValue");

    /** Whether a message has {@code pbj.fast_parse}, from its comments, its file's, or the project. */
    private boolean fastParse(String name, MessageDefContext message) {
        return option(name, message, PerformanceOptions::fastParse);
    }

    /** Resolve a boolean fast code generation option for a message, from its comments, its file's, or the project. */
    private boolean option(String name, MessageDefContext message, Function<PerformanceOptions, Boolean> option) {
        return PerformanceOptions.resolve(levels(name, message), option, true, false);
    }

    /** The options that apply to a message, in order of precedence: its comments, its file's, and the project's. */
    private List<PerformanceOptions> levels(String name, MessageDefContext message) {
        final File file = messageFiles.get(name);
        return List.of(
                PerformanceOptions.fromComments(message.optionComment(), "message " + name),
                file == null
                        ? PerformanceOptions.NONE
                        : fileOptions.getOrDefault(file.getAbsolutePath(), PerformanceOptions.NONE),
                projectOptions);
    }

    /** Read a boolean PBJ message option from its special comments, defaulting to false. */
    public static boolean messageOption(MessageDefContext message, String option) {
        return Boolean.TRUE.equals(messageOptionValue(message, option));
    }

    /**
     * Read every option set by a list of special option comments, from a file or a message. When an option is set
     * more than once, the last value wins.
     *
     * @param comments the option comments
     * @return option names to their values, without quotes
     */
    public static Map<String, String> optionCommentValues(List<Protobuf3Parser.OptionCommentContext> comments) {
        final Map<String, String> values = new HashMap<>();
        for (Protobuf3Parser.OptionCommentContext comment : comments) {
            final java.util.regex.Matcher matcher = OPTION_COMMENT.matcher(comment.getText());
            if (matcher.find()) {
                values.put(matcher.group(1), matcher.group(2).trim());
            }
        }
        return values;
    }

    /** Read a boolean PBJ message option from its special comments, or null when it is absent. */
    public static @Nullable Boolean messageOptionValue(MessageDefContext message, String option) {
        for (Protobuf3Parser.OptionCommentContext comment : message.optionComment()) {
            final java.util.regex.Matcher matcher = OPTION_COMMENT.matcher(comment.getText());
            if (matcher.find() && matcher.group(1).equals(option)) {
                String value = matcher.group(2).trim();
                if (!value.equals("true") && !value.equals("false")) {
                    throw new IllegalArgumentException(option + " must be true or false");
                }
                return Boolean.parseBoolean(value);
            }
        }
        return null;
    }

    /**
     * Build a new lookup helper, root directory of protobuf files. This scans directory reading
     * protobuf files extracting what is needed.
     *
     * @param allSrcFiles collection of all proto src files
     * @param javaPackageSuffix an optional, nullable suffix to add to the Java package name in generated classes, e.g. ".pbj"
     */
    public LookupHelper(final Map<Path, File> allSrcFiles, final String javaPackageSuffix) {
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
        throw new PbjCompilerException(
                FAILED_TO_FIND_MSG_TYPE_MESSAGE.formatted(context.getText(), protoSrcFile, importsString));
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

    /**
     * Check if the given fullyQualifiedMessageOrEnumName is cacheable.
     *
     * @param fullyQualifiedMessageOrEnumName to check if cacheable
     * @return the cache size if the message is cacheable, or null
     */
    public Integer getCacheableMessageCacheSize(final String fullyQualifiedMessageOrEnumName) {
        return cacheableMessagesCacheSize.get(fullyQualifiedMessageOrEnumName);
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
    private void build(final Map<Path, File> allSrcFiles) {
        for (final File file : allSrcFiles.values()) {
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
                    fileOptions.put(
                            new File(fullQualifiedFile).getAbsolutePath(),
                            PerformanceOptions.fromComments(parsedDoc.optionComment(), file.getName()));
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

                        final var importedFile = allSrcFiles.get(Path.of(importedFileName));
                        if (importedFile != null) {
                            fileImports.add(importedFile.getAbsolutePath());
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
        messageDefinitions.put(fullyQualifiedMessage, msgDef);
        messageFiles.put(fullyQualifiedMessage, new File(fullQualifiedFile));
        comparableFieldsByMsg.computeIfAbsent(fullyQualifiedMessage, v -> extractComparableFields(msgDef));
        final Integer cacheSize = extractCacheSize(msgDef);
        if (cacheSize != null) {
            cacheableMessagesCacheSize.put(fullyQualifiedMessage, cacheSize);
        }
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
        if (msgDef.optionComment() == null || msgDef.optionComment().isEmpty()) {
            return emptyList();
        }
        for (Protobuf3Parser.OptionCommentContext optionComment : msgDef.optionComment()) {
            if (optionComment.getText() == null) {
                continue;
            }
            final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
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
        }
        return emptyList();
    }

    /**
     * Checks if a msgDef is cacheable.
     * @param msgDef a message definition
     * @return the cache size if the message is cacheable, or null
     */
    static Integer extractCacheSize(final MessageDefContext msgDef) {
        if (msgDef.optionComment() == null || msgDef.optionComment().isEmpty()) {
            return null;
        }
        for (Protobuf3Parser.OptionCommentContext optionComment : msgDef.optionComment()) {
            if (optionComment.getText() == null) {
                continue;
            }
            final var matcher = OPTION_COMMENT.matcher(optionComment.getText());
            if (matcher.find()) {
                final String optionName = matcher.group(1);
                final String optionValue = matcher.group(2);
                if (optionName.equals(PBJ_CACHEABLE_OPTION_NAME)) {
                    return Integer.valueOf(
                            optionValue.replace("\"", "").replace("_", "").trim());
                }
            }
        }
        return null;
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
