// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.compiler.impl.PbjCompilerException;
import com.hedera.pbj.compiler.impl.PerformanceOptions;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which messages get writers ({@code pbj.writers}), and what the writers of different kinds of fields look like. */
class WriterGenerationTest {
    @TempDir
    Path directory;

    /** Compile proto files, given by name and content, with project options; the first {@code sources} are sources. */
    private void compile(final PerformanceOptions project, final int sources, final String... namesAndProtos)
            throws Exception {
        final List<File> files = new java.util.ArrayList<>();
        for (int i = 0; i < namesAndProtos.length; i += 2) {
            final Path file = directory.resolve(namesAndProtos[i]);
            Files.writeString(file, namesAndProtos[i + 1]);
            files.add(file.toFile());
        }
        PbjCompiler.compileFilesIn(
                files.subList(0, sources),
                files.subList(sources, files.size()),
                Set.of(directory.toFile()),
                directory.resolve("out").toFile(),
                directory.resolve("out").toFile(),
                null,
                false,
                false,
                Set.of(),
                Set.of(),
                project);
    }

    private void compile(final String proto) throws Exception {
        compile(PerformanceOptions.NONE, 1, "writers.proto", proto);
    }

    private String model(final String message) throws Exception {
        return Files.readString(directory.resolve("out/example/" + message + ".java"));
    }

    private boolean hasWriter(final String message) throws Exception {
        return model(message).contains("public static final class Writer extends MessageWriter");
    }

    /** The cause of a compiler exception, which may be wrapped. */
    private static Throwable compilerError(final Exception e) {
        Throwable cause = e;
        while (cause != null && !(cause instanceof PbjCompilerException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof PbjCompilerException, e.toString());
        return cause;
    }

    @Test
    void writersReachEveryMessageFromTheRootAcrossFiles() throws Exception {
        compile(PerformanceOptions.NONE, 2, "root.proto", """
                syntax = "proto3";
                package example;
                import "parts.proto";
                // <<<pbj.writers = true>>>
                message Root { Part part = 1; repeated Part parts = 2; map<string, Value> values = 3;
                    oneof choice { Choice choice_message = 4; int32 number = 5; } }
                message Unrelated { int32 a = 1; }
                """, "parts.proto", """
                syntax = "proto3";
                package example;
                message Part { Leaf leaf = 1; }
                message Leaf { int32 a = 1; }
                message Value { string s = 1; }
                message Choice { bool b = 1; }
                """);
        for (final String message : List.of("Root", "Part", "Leaf", "Value", "Choice")) {
            assertTrue(hasWriter(message), message);
        }
        assertFalse(hasWriter("Unrelated"));
    }

    @Test
    void messagesThatContainEachOtherGetWriters() throws Exception {
        compile("""
                syntax = "proto3";
                package example;
                // <<<pbj.writers = true>>>
                message Self { Self next = 1; repeated Self more = 2; int32 value = 3; }
                // <<<pbj.writers = true>>>
                message PairA { PairB b = 1; }
                message PairB { PairA a = 1; repeated PairA as = 2; }
                message TriX { TriY y = 1; }
                message TriY { TriZ z = 1; }
                // <<<pbj.writers = true>>>
                message TriZ { oneof choice { TriX x = 1; string text = 2; } }
                """);
        for (final String message : List.of("Self", "PairA", "PairB", "TriX", "TriY", "TriZ")) {
            assertTrue(hasWriter(message), message);
        }
        // nested messages in a cycle are opened through child writers
        assertTrue(model("Self").contains("public Self.Writer next()"), model("Self"));
        assertTrue(model("TriY").contains("public TriZ.Writer z()"), model("TriY"));
    }

    @Test
    void anExplicitFalseOnAReachableMessageIsAnErrorNamingThePath() {
        final Exception e = assertThrows(Exception.class, () -> compile("""
                syntax = "proto3";
                package example;
                // <<<pbj.writers = true>>>
                message Root { Middle middle = 1; }
                message Middle { Leaf leaf = 2; }
                // <<<pbj.writers = false>>>
                message Leaf { int32 a = 1; }
                """));
        final String message = compilerError(e).getMessage();
        assertTrue(message.contains("Message example.Leaf has <<<pbj.writers = false>>>"), message);
        assertTrue(message.contains("example.Root.middle -> example.Middle.leaf -> example.Leaf"), message);
    }

    @Test
    void aFileLevelFalseIsExplicitToo() {
        final Exception e = assertThrows(
                Exception.class, () -> compile(PerformanceOptions.NONE, 2, "root.proto", """
                        syntax = "proto3";
                        package example;
                        import "leaf.proto";
                        // <<<pbj.writers = true>>>
                        message Root { Leaf leaf = 1; }
                        """, "leaf.proto", """
                        syntax = "proto3";
                        // <<<pbj.writers = false>>>
                        package example;
                        message Leaf { int32 a = 1; }
                        """));
        assertTrue(compilerError(e).getMessage().contains("example.Root.leaf -> example.Leaf"));
    }

    @Test
    void theProjectOptionTurnsWritersOnButPerformanceMaxDoesNot() throws Exception {
        final String proto = """
                syntax = "proto3";
                package example;
                message Plain { int32 a = 1; }
                // <<<pbj.writers = false>>>
                message Off { int32 a = 1; }
                """;
        compile(PerformanceOptions.of(null, null, null, null, "max", "test project"), 1, "max.proto", proto);
        assertFalse(hasWriter("Plain"));
        // views, part of max, are generated
        assertTrue(model("Plain").contains("public static final class View"));
        compile(PerformanceOptions.of(null, null, null, null, "true", null, "test project"), 1, "on.proto", proto);
        assertTrue(hasWriter("Plain"));
        // a message's own option wins over the project's
        assertFalse(hasWriter("Off"));
    }

    @Test
    void fieldsOfMessagesFromOtherModulesGetNoChildWriter() throws Exception {
        // the second file is on the classpath, as a dependency's protos are, so its messages are not generated
        compile(PerformanceOptions.NONE, 1, "holder.proto", """
                syntax = "proto3";
                package example;
                import "dependency.proto";
                // <<<pbj.writers = true>>>
                message Holder { Foreign foreign = 1; repeated Foreign foreigns = 2; Local local = 3; }
                message Local { int32 a = 1; }
                """, "dependency.proto", """
                syntax = "proto3";
                package example;
                message Foreign { int32 a = 1; }
                """);
        final String holder = model("Holder");
        assertTrue(holder.contains("public Writer foreign(@Nullable final Foreign value)"), holder);
        assertTrue(holder.contains("public Writer foreignRaw(@NonNull final Bytes encoded)"), holder);
        assertTrue(holder.contains("public Writer addForeigns(@NonNull final Foreign value)"), holder);
        assertTrue(holder.contains("out.tagMessage(0x0A, value, Foreign.PROTOBUF);"), holder);
        assertFalse(holder.contains("Foreign.Writer"), holder);
        // a message from this build gets a child writer
        assertTrue(holder.contains("public Local.Writer local()"), holder);
        assertFalse(directory.resolve("out/example/Foreign.java").toFile().exists());
    }

    @Test
    void aNestedTypeNamedWriterIsAClearError() {
        final Exception e = assertThrows(Exception.class, () -> compile("""
                syntax = "proto3";
                package example;
                // <<<pbj.writers = true>>>
                message Clash {
                    message Writer { int32 value = 1; }
                    Writer writer = 1;
                }
                """));
        assertTrue(compilerError(e).getMessage().contains("declares its own nested type named Writer"));
    }

    @Test
    void fieldsNamedLikeTheWritersOwnMethodsGetASuffix() throws Exception {
        compile("""
                syntax = "proto3";
                package example;
                // <<<pbj.writers = true>>>
                message Names { int64 length = 1; Names offset = 2; string end = 3; int32 count = 4; }
                """);
        final String names = model("Names");
        assertTrue(names.contains("public Writer lengthField(final long value)"), names);
        assertTrue(names.contains("public Names.Writer offsetField()"), names);
        assertTrue(names.contains("public Writer endField(@Nullable final String value)"), names);
        assertTrue(names.contains("public Writer count(final int value)"), names);
    }

    @Test
    void repeatedWrapperTypesAreAClearError() {
        final Exception e = assertThrows(Exception.class, () -> compile("""
                syntax = "proto3";
                package example;
                import "google/protobuf/wrappers.proto";
                // <<<pbj.writers = true>>>
                message Wrapped { repeated google.protobuf.StringValue values = 1; }
                """));
        assertTrue(compilerError(e).getMessage().contains("repeated google.protobuf wrapper type"));
    }

    @Test
    void lengthPrefixesOfBoundedMessagesReserveOneByte() throws Exception {
        compile("""
                syntax = "proto3";
                package example;
                // <<<pbj.writers = true>>>
                message Outer { Small small = 1; Large large = 2; Text text = 3; Many many = 4; Self self = 5; }
                message Small { int64 a = 1; fixed64 b = 2; Small2 c = 3; }
                message Small2 { bool d = 1; }
                // eight ten-byte varints and tags: 88 bytes, plus a wide field: 137
                message Large { int64 a = 1; int64 b = 2; int64 c = 3; int64 d = 4; int64 e = 5; int64 f = 6;
                    int64 g = 7; int64 h = 8; fixed64 i = 9; fixed64 j = 10; fixed64 k = 11; fixed64 l = 12;
                    fixed64 m = 13; fixed64 n = 14; }
                message Text { string s = 1; }
                message Many { repeated int32 values = 1; }
                message Self { Self next = 1; }
                """);
        final String outer = model("Outer");
        assertTrue(outer.contains("openChild(smallWriter, 1, "), outer);
        assertTrue(outer.contains("openChild(largeWriter, 2, "), outer);
        assertTrue(outer.contains("openChild(textWriter, 2, "), outer);
        assertTrue(outer.contains("openChild(manyWriter, 2, "), outer);
        assertTrue(outer.contains("openChild(selfWriter, 2, "), outer);
    }
}
