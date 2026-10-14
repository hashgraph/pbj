// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void trackingIsOffByDefaultAndCanBeTurnedOn() throws Exception {
        final var proto = directory.resolve("model.proto");
        Files.writeString(proto, """
                syntax = "proto3";
                package example;
                // <<<pbj.diff = true>>>
                message Value { int64 count = 1; oneof choice { string name = 4; int64 number = 7; } }
                // <<<pbj.diff = true>>>
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

    @Test
    void catalogSelectionWalksMapsOneofsImportsAndCyclesWithoutSelectingUnrelatedMessages() throws Exception {
        final var child = directory.resolve("child.proto");
        Files.writeString(child, """
                syntax = "proto3";
                package example;
                message Child { repeated float values = 1; Child recursive = 2; }
                message Unrelated { string text = 1; }
                """);
        final var catalog = directory.resolve("catalog.proto");
        Files.writeString(catalog, """
                syntax = "proto3";
                package example;
                import "child.proto";
                message Catalog { oneof value { Root root = 1; } }
                message Root { map<string, Child> entries = 1; oneof value { Child nested = 2; } }
                // <<<pbj.diff = true>>>
                // <<<pbj.copy_builder_tracking = false>>>
                message PlainRoot { Child child = 1; }
                """);
        final var output = directory.resolve("selected");
        PbjCompiler.compileFilesIn(
                List.of(child.toFile(), catalog.toFile()),
                Set.of(),
                Set.of(directory.toFile()),
                output.toFile(),
                output.toFile(),
                null,
                false,
                true,
                Set.of(),
                Set.of("example.Catalog"));
        final String root = Files.readString(output.resolve("example/Root.java"));
        final String nested = Files.readString(output.resolve("example/Child.java"));
        assertTrue(root.contains("CopyBuilderTracked"));
        assertTrue(root.contains("applyPartial("));
        final String plainRoot = Files.readString(output.resolve("example/PlainRoot.java"));
        assertTrue(plainRoot.contains("applyPartial("));
        assertFalse(plainRoot.contains("CopyBuilderTracked"));
        assertTrue(nested.contains("$equalsForDiff("));
        assertFalse(nested.contains("CopyBuilderTracked"));
        assertFalse(nested.contains("applyPartial("));
        for (String name : List.of("Unrelated", "Catalog")) {
            final String source = Files.readString(output.resolve("example/" + name + ".java"));
            assertFalse(source.contains("$equalsForDiff("));
            assertFalse(source.contains("applyPartial("));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> PbjCompiler.compileFilesIn(
                        List.of(child.toFile(), catalog.toFile()),
                        Set.of(),
                        Set.of(directory.toFile()),
                        output.toFile(),
                        output.toFile(),
                        null,
                        false,
                        false,
                        Set.of("example.Missing"),
                        Set.of()));
        // No selection and no annotations generate the original model API, even when tracking is enabled globally.
        PbjCompiler.compileFilesIn(
                List.of(child.toFile()),
                Set.of(),
                Set.of(directory.toFile()),
                output.toFile(),
                output.toFile(),
                null,
                false,
                true);
        assertFalse(Files.readString(output.resolve("example/Child.java")).contains("$equalsForDiff("));
    }
}
