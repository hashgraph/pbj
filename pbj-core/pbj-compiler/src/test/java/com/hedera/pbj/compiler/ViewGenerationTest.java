// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.compiler.impl.PbjCompilerException;
import com.hedera.pbj.compiler.impl.PerformanceOptions;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which messages get views and array parsers when messages contain each other, and why views are denied. */
class ViewGenerationTest {
    @TempDir
    Path directory;

    /** Compile a proto file with every option on, and return what the compiler printed to standard error. */
    private String compile(final String proto) throws Exception {
        final Path file = directory.resolve("cycles.proto");
        Files.writeString(file, proto);
        final PrintStream err = System.err;
        final ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setErr(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            PbjCompiler.compileFilesIn(
                    List.of(file.toFile()),
                    Set.of(),
                    Set.of(directory.toFile()),
                    directory.resolve("out").toFile(),
                    directory.resolve("out").toFile(),
                    null,
                    false,
                    false,
                    Set.of(),
                    Set.of(),
                    PerformanceOptions.of(null, null, null, null, "max", "test project"));
        } finally {
            System.setErr(err);
        }
        return printed.toString(StandardCharsets.UTF_8);
    }

    private boolean hasView(final String message) throws Exception {
        return Files.readString(directory.resolve("out/example/" + message + ".java"))
                .contains("public static final class View");
    }

    private String codec(final String message) throws Exception {
        return Files.readString(directory.resolve("out/example/codec/" + message + "ProtoCodec.java"));
    }

    @Test
    void messagesThatContainEachOtherGetViewsAndArrayParsers() throws Exception {
        compile("""
                syntax = "proto3";
                package example;
                message Self { Self next = 1; int32 value = 2; }
                message PairA { PairB b = 1; }
                message PairB { PairA a = 1; repeated PairA as = 2; }
                message TriX { TriY y = 1; }
                message TriY { TriZ z = 1; }
                message TriZ { oneof choice { TriX x = 1; string text = 2; } }
                """);
        for (final String message : List.of("Self", "PairA", "PairB", "TriX", "TriY", "TriZ")) {
            assertTrue(hasView(message), message);
            assertTrue(codec(message).contains("parseArray("), message);
        }
        // nested array parsers in the cycles are called directly
        assertTrue(codec("Self").contains("SelfProtoCodec.parseArray("), codec("Self"));
        assertTrue(codec("PairA").contains("PairBProtoCodec.parseArray("), codec("PairA"));
        assertTrue(codec("TriZ").contains("TriXProtoCodec.parseArray("), codec("TriZ"));
    }

    @Test
    void aCycleThroughAMessageWithoutAViewGetsNoViews() throws Exception {
        // CycleB can't have a view because of Counts' map, so neither can CycleA, which contains CycleB, nor CycleC,
        // which contains CycleA, whichever message is looked at first
        // views requested in the file's comments, so the compiler reports why they are denied
        final String printed = compile("""
                syntax = "proto3";
                // <<<pbj.views = true>>>
                package example;
                message CycleA { CycleB b = 1; }
                message CycleB { CycleC c = 1; Counts counts = 2; }
                message CycleC { CycleA a = 1; int32 value = 2; }
                message Counts { map<string, int32> counts = 1; }
                message Fine { int32 value = 1; }
                """);
        for (final String message : List.of("CycleA", "CycleB", "CycleC", "Counts")) {
            assertFalse(hasView(message), message);
        }
        assertTrue(hasView("Fine"));
        // the compiler says why
        assertTrue(
                printed.contains("no view for message Counts: map field 'counts' (maps are not supported by views)"),
                printed);
        assertTrue(printed.contains("no view for message CycleB: field 'counts' has type example.Counts"), printed);
        assertTrue(printed.contains("no view for message CycleC: field 'a' has type example.CycleA"), printed);
    }

    @Test
    void viewsDeniedOnlyByTheProjectSettingAreNotReported() throws Exception {
        final String printed = compile("""
                syntax = "proto3";
                package example;
                message Counts { map<string, int32> counts = 1; }
                """);
        assertFalse(hasView("Counts"));
        assertFalse(printed.contains("no view"), printed);
    }

    @Test
    void aNestedTypeNamedViewIsAClearError() {
        final Exception e = assertThrows(Exception.class, () -> compile("""
                syntax = "proto3";
                package example;
                message Clash {
                    message View { int32 value = 1; }
                    View view = 1;
                }
                """));
        final Throwable cause = e instanceof PbjCompilerException ? e : e.getCause();
        assertTrue(cause instanceof PbjCompilerException, e.toString());
        assertTrue(cause.getMessage().contains("declares its own nested type named View"), cause.getMessage());
    }
}
