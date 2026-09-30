// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CopyBuilderGenerationTest {
    @TempDir
    Path directory;

    @Test
    void trackingIsOptInAndDoesNotChangeDefaultGeneration() throws Exception {
        final var proto = directory.resolve("model.proto");
        Files.writeString(proto, """
                syntax = "proto3";
                package example;
                message Value { int64 count = 1; oneof choice { string name = 4; int64 number = 7; } }
                message Empty {}
                """);
        final var plain = directory.resolve("plain");
        PbjCompiler.compileFilesIn(
                List.of(proto.toFile()),
                Set.of(),
                Set.of(directory.toFile()),
                plain.toFile(),
                plain.toFile(),
                null,
                false);
        final String plainSource = Files.readString(plain.resolve("example/Value.java"));
        assertFalse(plainSource.contains("CopyBuilderTracked"));
        assertFalse(plainSource.contains("$copyBuilderOrigin"));
        for (String helper : List.of(" diff(", " partial(", " clearedFields(", " worthPartial(", " applyPartial(")) {
            assertTrue(plainSource.contains(helper), helper);
        }
        assertTrue(Files.readString(plain.resolve("example/Empty.java")).contains("applyPartial("));
        final var tracked = directory.resolve("tracked");
        PbjCompiler.compileFilesIn(
                List.of(proto.toFile()),
                Set.of(),
                Set.of(directory.toFile()),
                tracked.toFile(),
                tracked.toFile(),
                null,
                false,
                true);
        assertTrue(Files.readString(tracked.resolve("example/Value.java")).contains("CopyBuilderTracked<Value>"));
        assertTrue(Files.readString(tracked.resolve("example/Empty.java")).contains("CopyBuilderTracked<Empty>"));
    }
}
