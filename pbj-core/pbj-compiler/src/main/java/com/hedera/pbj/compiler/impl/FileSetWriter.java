// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.File;
import java.io.IOException;

/**
 * This is essentially a set of all the JavaFileWriter objects that are necessary to support the generation
 * of a single top-level .proto model implementation in Java, such as a model writer, a codec writer, etc.
 */
public record FileSetWriter(
        JavaFileWriter modelWriter,
        JavaFileWriter schemaWriter,
        JavaFileWriter codecWriter,
        JavaFileWriter jsonCodecWriter,
        JavaFileWriter testWriter) {

    /** A factory to create a FileSetWriter instance for a given MessageDefContext. */
    public static FileSetWriter create(
            final File mainOutputDir,
            final File testOutputDir,
            final Protobuf3Parser.MessageDefContext msgDef,
            final ContextualLookupHelper contextualLookupHelper) {
        return new FileSetWriter(
                JavaFileWriter.create(FileType.MODEL, mainOutputDir, msgDef, contextualLookupHelper),
                JavaFileWriter.create(FileType.SCHEMA, mainOutputDir, msgDef, contextualLookupHelper),
                JavaFileWriter.create(FileType.CODEC, mainOutputDir, msgDef, contextualLookupHelper),
                JavaFileWriter.create(FileType.JSON_CODEC, mainOutputDir, msgDef, contextualLookupHelper),
                JavaFileWriter.create(FileType.TEST, testOutputDir, msgDef, contextualLookupHelper));
    }

    /** A utility method to write all the files at once. */
    public void writeAllFiles() throws IOException {
        modelWriter.writeFile();
        schemaWriter.writeFile();
        codecWriter.writeFile();
        jsonCodecWriter.writeFile();
        testWriter.writeFile();
    }
}
