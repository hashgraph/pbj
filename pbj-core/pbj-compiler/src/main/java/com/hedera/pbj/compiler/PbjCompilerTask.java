/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.compiler;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.LookupHelper;
import com.hedera.pbj.compiler.impl.generators.EnumGenerator;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.FileInputStream;
import javax.inject.Inject;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

/** Gradle Task that generates java src code from protobuf proto schema files. */
public abstract class PbjCompilerTask extends SourceTask {

    /**
     * Set the java main directory that we write generated code into
     *
     * @return The java main directory that we write generated code into
     */
    @OutputDirectory
    public abstract DirectoryProperty getJavaMainOutputDirectory();

    /**
     * Set the java test directory that we write generated code into
     *
     * @return The java test directory that we write generated code into
     */
    @OutputDirectory
    public abstract DirectoryProperty getJavaTestOutputDirectory();

    /**
     * @return Gradle's FileOperations service to use for file deletion
     */
    @Inject
    protected abstract FileOperations getFileOperations();

    /**
     * Perform task action - Generates all the PBJ java source files
     *
     * @throws Exception If there was a problem performing action
     */
    @TaskAction
    public void perform() throws Exception {
        // Clean output directories
        getFileOperations().delete(getJavaMainOutputDirectory(), getJavaTestOutputDirectory());
        compileFilesIn(getSource(),
                getJavaMainOutputDirectory().get().getAsFile(),
                getJavaTestOutputDirectory().get().getAsFile());
    }

    /**
     * Compile all the proto files in the given source directories
     * @param sourceFiles The source files to compile
     * @param mainOutputDir The main output directory
     * @param testOutputDir The test output directory
     */

    public static void compileFilesIn(Iterable<File> sourceFiles,
                                       File mainOutputDir,
                                       File testOutputDir) throws Exception {
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
                                generator.generate(
                                        msgDef,
                                        mainOutputDir,
                                        testOutputDir,
                                        contextualLookupHelper);
                            }
                        }
                        final Protobuf3Parser.EnumDefContext enumDef = topLevelDef.enumDef();
                        if (enumDef != null) {
                            // run just enum generators for enum
                            EnumGenerator.generateEnumFile(
                                    enumDef,
                                    mainOutputDir,
                                    contextualLookupHelper);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Exception while processing file: " + protoFile);
                    throw e;
                }
            }
        }
    }
}
