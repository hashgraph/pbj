// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileSetWriter;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator;
import com.hedera.pbj.compiler.impl.generators.protobuf.CodecGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * Interface for a code generator from protobuf message definition
 */
public interface Generator {

    /**
     * All generators.
     */
    Map<Class<? extends Generator>, Function<FileSetWriter, JavaFileWriter>> GENERATORS = Map.of(
            ModelGenerator.class, FileSetWriter::modelWriter,
            SchemaGenerator.class, FileSetWriter::schemaWriter,
            CodecGenerator.class, FileSetWriter::codecWriter,
            JsonCodecGenerator.class, FileSetWriter::jsonCodecWriter,
            TestGenerator.class, FileSetWriter::testWriter);

    /**
     * Generate a code from protobuf message type
     *
     * @param msgDef                the parsed message
     * @param destinationSrcDir     the destination source directory to generate into
     * @param destinationTestSrcDir the destination source directory to generate test files into
     * @param lookupHelper          Lookup helper for global context lookups
     * @throws IOException if there was a problem writing generated code
     */
    void generate(
            final Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final File destinationSrcDir,
            File destinationTestSrcDir,
            final ContextualLookupHelper lookupHelper)
            throws IOException;
}
