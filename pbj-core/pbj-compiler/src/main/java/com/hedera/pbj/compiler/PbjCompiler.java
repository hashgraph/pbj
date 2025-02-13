// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.LookupHelper;
import com.hedera.pbj.compiler.impl.generators.EnumGenerator;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.FileInputStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** Compiler entry point to generate java src code from protobuf proto schema files. */
public abstract class PbjCompiler {
    private static final int MAX_TRACE_FRAMES = 8;
    private static final String STACK_ELEMENT_INDENT = "    ";

    public static void compileFilesIn(Iterable<File> sourceFiles, File mainOutputDir, File testOutputDir)
            throws Exception {
        // first we do a scan of files to build lookup tables for imports, packages etc.
        final LookupHelper lookupHelper = new LookupHelper(sourceFiles);
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
                            // run all generators for message
                            for (final var generatorClass : Generator.GENERATORS) {
                                final var generator =
                                        generatorClass.getDeclaredConstructor().newInstance();
                                generator.generate(msgDef, mainOutputDir, testOutputDir, contextualLookupHelper);
                            }
                        }
                        final Protobuf3Parser.EnumDefContext enumDef = topLevelDef.enumDef();
                        if (enumDef != null) {
                            // run just enum generators for enum
                            EnumGenerator.generateEnumFile(enumDef, mainOutputDir, contextualLookupHelper);
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
