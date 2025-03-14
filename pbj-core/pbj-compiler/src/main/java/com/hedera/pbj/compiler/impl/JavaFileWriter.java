// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.Common.getJavaFile;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An abstraction that allows various Java entity generators to populate a text buffer and then write it to disk
 * as a single .java file. This is useful for supporting inner entities (e.g. classes, enums, etc.) when all of them
 * have to be a part of the same outer entity, and hence have to be written into the same, single file.
 *
 * This abstraction provides support for maintaining a set of import statements that may be required by any java
 * entities being generated using this abstraction.
 */
public final class JavaFileWriter {

    /**
     * A currently hard-coded license header for generated files.
     * This can be made configurable if needed in the future.
     */
    private static final String LICENSE_HEADER = "// SPDX-License-Identifier: Apache-2.0";

    /** A java file to generate. */
    private final File javaFile;

    /** E.g. "com.package.proto", or "com.package.proto.codec", etc. */
    private final String javaPackage;

    /** All the imported symbols. */
    private final Set<String> imports = new HashSet<>();

    /** The actual text to be written, likely a top-level class or enum definition. */
    private final StringBuilder stringBuilder = new StringBuilder();

    /** Creates a new JavaFileWriter. */
    public JavaFileWriter(@NonNull final File javaFile, @NonNull final String javaPackage) {
        this.javaFile = Objects.requireNonNull(javaFile);
        this.javaPackage = Objects.requireNonNull(javaPackage);
        if (javaPackage.isBlank()) {
            throw new IllegalArgumentException("javaPackage must not be blank, instead got: `" + javaPackage + "`");
        }
    }

    /** A factory to create JavaFileWriter for messages. */
    public static JavaFileWriter create(
            final FileType fileType,
            final File outputDir,
            final Protobuf3Parser.MessageDefContext msgDef,
            final ContextualLookupHelper contextualLookupHelper) {
        final String javaPackage = contextualLookupHelper.getPackageForMessage(fileType, msgDef);
        return new JavaFileWriter(
                getJavaFile(
                        outputDir, javaPackage, contextualLookupHelper.getUnqualifiedClassForMessage(fileType, msgDef)),
                javaPackage);
    }

    /**
     * Add an imported symbol, including the `static ` prefix and/or `.*` suffix if necessary,
     * but w/o the opening `import ` or closing `;`.
     *
     * @param symbol the symbol to add to imports
     */
    public void addImport(final String symbol) {
        imports.add(symbol);
    }

    /**
     * Append a string to the generated text.
     * This should generally be a top-level class or enum definition with any inner definitions,
     * including inner classes or enums, embedded inside. The caller can call this method multiple times
     * to keep building their text.
     *
     * @param str a string to append
     */
    public void append(final String str) {
        stringBuilder.append(str);
    }

    /**
     * Generate the actual file on disk with a proper license header, `package`, `import` directives,
     * and finally the text that has been built by calling the `append()` method.
     * <p>
     * It's technically possible to call this method multiple times. It's even possible to add more imports
     * or append more text between the calls. However, it will overwrite the exact same file each time,
     * although it will write all the latest updates to the imports and the text on each invocation.
     * If the imports and/or text are updated and this `writeFile()` method is not invoked after the update,
     * then the latest updates will not be persisted.
     *
     * @throws IOException if an I/O error occurs
     */
    public void writeFile() throws IOException {
        try (Writer writer = new BufferedWriter(new FileWriter(javaFile))) {
            writer.append(LICENSE_HEADER);
            writer.append('\n');

            writer.append("package ").append(javaPackage).append(";");
            writer.append('\n');

            if (!imports.isEmpty()) {
                writer.append('\n');
                writer.append(imports.stream()
                        .sorted()
                        .map(v -> "import " + v + ";\n")
                        .collect(Collectors.joining()));
            }

            writer.append('\n');
            writer.append(stringBuilder.toString());
            writer.append('\n');
        }
    }
}
