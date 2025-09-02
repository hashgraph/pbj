// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static com.hedera.pbj.compiler.impl.Common.getJavaFile;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileSetWriter;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.LookupHelper;
import com.hedera.pbj.compiler.impl.generators.EnumGenerator;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.generators.ServiceGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** Compiler entry point to generate java src code from protobuf proto schema files. */
public abstract class PbjCompiler {
    private static final int MAX_TRACE_FRAMES = 8;
    private static final String STACK_ELEMENT_INDENT = "    ";

    /**
     * Compile source files and generate PBJ models.
     *
     * @param sourceFiles all the source files to compile
     * @param classpath protobuf files in dependencies located on the Java compile classpath
     * @param mainOutputDir output directory for generated model, codecs, and schema ("main" files)
     * @param testOutputDir output directory for generated tests ("test" files)
     * @param javaPackageSuffix an optional, nullable suffix to add to the Java package name in generated classes, e.g. ".pbj",
     *                          when an explicit `pbj.java_package` option is missing
     * @throws Exception
     */
    public static void compileFilesIn(
            Iterable<File> sourceFiles,
            Iterable<File> classpath,
            File mainOutputDir,
            File testOutputDir,
            String javaPackageSuffix,
            boolean generateTestClasses)
            throws Exception {
        // first we do a scan of files to build lookup tables for imports, packages etc.
        final LookupHelper lookupHelper = new LookupHelper(sourceFiles, classpath, javaPackageSuffix);
        // for each proto src directory generate code
        for (final File protoFile : sourceFiles) {
            if (protoFile.exists()
                    && protoFile.isFile()
                    && protoFile.getName().endsWith(LookupHelper.PROTO_EXTENSIION)) {
                final ContextualLookupHelper contextualLookupHelper =
                        new ContextualLookupHelper(lookupHelper, protoFile);
                try (final var input = new FileInputStream(protoFile)) {
                    final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
                    final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
                    final Protobuf3Parser.ProtoContext parsedDoc = parser.proto();
                    for (final var topLevelDef : parsedDoc.topLevelDef()) {
                        final Protobuf3Parser.MessageDefContext msgDef = topLevelDef.messageDef();
                        if (msgDef != null) {
                            final FileSetWriter writer = FileSetWriter.create(
                                    mainOutputDir, testOutputDir, msgDef, contextualLookupHelper, generateTestClasses);
                            for (Map.Entry<Class<? extends Generator>, Function<FileSetWriter, JavaFileWriter>> entry :
                                    Generator.GENERATORS.entrySet()) {
                                final var generator =
                                        entry.getKey().getDeclaredConstructor().newInstance();
                                generator.generate(msgDef, entry.getValue().apply(writer), contextualLookupHelper);
                            }
                            writer.writeAllFiles();
                        }
                        final Protobuf3Parser.EnumDefContext enumDef = topLevelDef.enumDef();
                        if (enumDef != null) {
                            // run just enum generators for enum
                            final String javaPackage = contextualLookupHelper.getPackage(FileType.MODEL, enumDef);
                            final JavaFileWriter writer = new JavaFileWriter(
                                    getJavaFile(
                                            mainOutputDir,
                                            javaPackage,
                                            enumDef.enumName().getText()),
                                    javaPackage);
                            EnumGenerator.generateEnum(enumDef, writer, contextualLookupHelper);
                            writer.writeFile();
                        }
                        final Protobuf3Parser.ServiceDefContext serviceDef = topLevelDef.serviceDef();
                        if (serviceDef != null) {
                            final String javaPackage = contextualLookupHelper.getPackage(FileType.MODEL, serviceDef);
                            final JavaFileWriter writer = new JavaFileWriter(
                                    getJavaFile(
                                            mainOutputDir,
                                            javaPackage,
                                            serviceDef.serviceName().getText() + ServiceGenerator.SUFFIX),
                                    javaPackage);
                            ServiceGenerator.generateService(serviceDef, writer, contextualLookupHelper);
                            writer.writeFile();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Exception while processing file: " + protoFile);
                    // Print an abbreviated stack trace for help in debugging.
                    System.err.println(e);
                    var trace = e.getStackTrace();
                    int count = 0;
                    for (var element : trace) {
                        if (count++ < MAX_TRACE_FRAMES) System.err.println(STACK_ELEMENT_INDENT + element);
                    }
                    throw e;
                }
            }
        }
    }
}
