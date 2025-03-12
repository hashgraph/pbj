// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import java.io.IOException;

/**
 * This is essentially a set of JavaFileWriter objects that are necessary to support the generation
 * of a single top-level .proto model implementation in Java.
 *
 * There's not a lot of logic around the set of JavaFileWriter objects, and for this reason they
 * are exposed as `public final` fields directly.
 */
public class FileSetWriter {
    public final JavaFileWriter modelWriter;
    public final JavaFileWriter schemaWriter;
    public final JavaFileWriter codecWriter;
    public final JavaFileWriter jsonCodecWriter;
    public final JavaFileWriter testWriter;

    /** Creates a new FileSetWriter object. */
    public FileSetWriter(
            final JavaFileWriter modelWriter,
            final JavaFileWriter schemaWriter,
            final JavaFileWriter codecWriter,
            final JavaFileWriter jsonCodecWriter,
            final JavaFileWriter testWriter) {
        this.modelWriter = modelWriter;
        this.schemaWriter = schemaWriter;
        this.codecWriter = codecWriter;
        this.jsonCodecWriter = jsonCodecWriter;
        this.testWriter = testWriter;
    }

    /** A utility method to write all files at once. */
    public void writeFiles() throws IOException {
        modelWriter.writeFile();
        schemaWriter.writeFile();
        codecWriter.writeFile();
        jsonCodecWriter.writeFile();
        testWriter.writeFile();
    }
}
