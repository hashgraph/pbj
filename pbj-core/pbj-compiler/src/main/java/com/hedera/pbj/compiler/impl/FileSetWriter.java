// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

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

    /** A utility method to write all the files at once. */
    public void writeFiles() throws IOException {
        modelWriter.writeFile();
        schemaWriter.writeFile();
        codecWriter.writeFile();
        jsonCodecWriter.writeFile();
        testWriter.writeFile();
    }
}
