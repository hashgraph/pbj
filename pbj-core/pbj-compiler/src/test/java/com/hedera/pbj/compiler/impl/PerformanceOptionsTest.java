// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.PerformanceOptions.OneofStorage.BOXED;
import static com.hedera.pbj.compiler.impl.PerformanceOptions.OneofStorage.UNBOXED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.compiler.PbjCompiler;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Precedence and validation of the fast code generation options. */
class PerformanceOptionsTest {
    @TempDir
    Path directory;

    /** A parsed proto file and the options resolved for its messages. */
    private record Parsed(ContextualLookupHelper helper, Map<String, MessageDefContext> messages) {
        MessageDefContext message(String name) {
            return messages.get(name);
        }
    }

    private static Parsed parse(String proto, PerformanceOptions project) {
        final var parser =
                new Protobuf3Parser(new CommonTokenStream(new Protobuf3Lexer(CharStreams.fromString(proto))));
        final Protobuf3Parser.ProtoContext doc = parser.proto();
        final var helper = new ContextualLookupHelper(
                null,
                new File("test.proto"),
                false,
                PerformanceOptions.fromComments(doc.optionComment(), "test.proto"),
                project);
        final Map<String, MessageDefContext> messages = new HashMap<>();
        for (var def : doc.topLevelDef()) {
            if (def.messageDef() != null) collect(def.messageDef(), messages);
        }
        return new Parsed(helper, messages);
    }

    private static void collect(MessageDefContext message, Map<String, MessageDefContext> messages) {
        messages.put(message.messageName().getText(), message);
        for (var element : message.messageBody().messageElement()) {
            if (element.messageDef() != null) collect(element.messageDef(), messages);
        }
    }

    private static PerformanceOptions project(
            String fastParse, String fastWrite, String views, String oneofStorage, String performance) {
        return PerformanceOptions.of(fastParse, fastWrite, views, oneofStorage, performance, "test project");
    }

    @Test
    void everythingIsOffByDefault() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                message Plain { int64 a = 1; }
                """, PerformanceOptions.NONE);
        final var message = parsed.message("Plain");
        assertFalse(parsed.helper().fastParse(message));
        assertFalse(parsed.helper().fastWrite(message));
        assertFalse(parsed.helper().generateViews(message));
        assertEquals(BOXED, parsed.helper().oneofStorage(message));
    }

    @Test
    void projectPerformanceMaxTurnsEverythingOn() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                message Plain { int64 a = 1; }
                """, project(null, null, null, null, "max"));
        final var message = parsed.message("Plain");
        assertTrue(parsed.helper().fastParse(message));
        assertTrue(parsed.helper().fastWrite(message));
        assertTrue(parsed.helper().generateViews(message));
        assertEquals(UNBOXED, parsed.helper().oneofStorage(message));
    }

    @Test
    void specificProjectSettingBeatsProjectPerformance() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                message Plain { int64 a = 1; }
                """, project(null, "false", null, "boxed", "max"));
        final var message = parsed.message("Plain");
        assertTrue(parsed.helper().fastParse(message));
        assertFalse(parsed.helper().fastWrite(message));
        assertEquals(BOXED, parsed.helper().oneofStorage(message));
    }

    @Test
    void messageBeatsFileBeatsProject() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                // <<<pbj.fast_parse = false>>>
                // <<<pbj.views = true>>>
                package example;

                // <<<pbj.fast_parse = true>>>
                message Flagged { int64 a = 1; }

                message FromFile { int64 a = 1; }
                """, project("true", "true", "false", null, null));
        final var flagged = parsed.message("Flagged");
        final var fromFile = parsed.message("FromFile");
        // message comment beats file comment
        assertTrue(parsed.helper().fastParse(flagged));
        // file comment beats project
        assertFalse(parsed.helper().fastParse(fromFile));
        assertTrue(parsed.helper().generateViews(fromFile));
        // project applies where neither message nor file sets the option
        assertTrue(parsed.helper().fastWrite(fromFile));
        // built-in default where nothing sets it
        assertEquals(BOXED, parsed.helper().oneofStorage(fromFile));
    }

    @Test
    void messageCanOptOutOfFilePerformanceMax() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                // <<<pbj.performance = "max">>>
                package example;

                // <<<pbj.performance = "default">>>
                message OptedOut { int64 a = 1; }

                // <<<pbj.fast_write = false>>>
                message MostlyMax { int64 a = 1; }

                message Max { int64 a = 1; }
                """, PerformanceOptions.NONE);
        final var helper = parsed.helper();
        final var optedOut = parsed.message("OptedOut");
        assertFalse(helper.fastParse(optedOut));
        assertFalse(helper.fastWrite(optedOut));
        assertFalse(helper.generateViews(optedOut));
        assertEquals(BOXED, helper.oneofStorage(optedOut));
        // a specific option beats performance; at the message level it also beats the file's performance
        final var mostlyMax = parsed.message("MostlyMax");
        assertTrue(helper.fastParse(mostlyMax));
        assertFalse(helper.fastWrite(mostlyMax));
        assertEquals(UNBOXED, helper.oneofStorage(mostlyMax));
        final var max = parsed.message("Max");
        assertTrue(helper.fastWrite(max));
        assertTrue(helper.generateViews(max));
    }

    @Test
    void specificOptionBeatsPerformanceOnTheSameMessage() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                // <<<pbj.performance = "max">>>
                // <<<pbj.oneof_storage = "boxed">>>
                message Mixed { int64 a = 1; }
                """, PerformanceOptions.NONE);
        final var mixed = parsed.message("Mixed");
        assertTrue(parsed.helper().fastParse(mixed));
        assertEquals(BOXED, parsed.helper().oneofStorage(mixed));
    }

    @Test
    void commentsDirectlyBeforeAMessageBelongToTheMessage() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                package example;
                // <<<pbj.fast_parse = true>>>
                message First { int64 a = 1; }
                message Second { int64 a = 1; }
                """, PerformanceOptions.NONE);
        assertTrue(parsed.helper().fastParse(parsed.message("First")));
        assertFalse(parsed.helper().fastParse(parsed.message("Second")));
    }

    @Test
    void nestedMessagesDoNotInheritTheirParentsOptions() {
        final Parsed parsed = parse("""
                syntax = "proto3";
                // <<<pbj.fast_parse = true>>>
                message Outer {
                    message Inner { int64 a = 1; }
                    // <<<pbj.fast_write = true>>>
                    message FlaggedInner { int64 a = 1; }
                    Inner inner = 1;
                }
                """, PerformanceOptions.NONE);
        assertTrue(parsed.helper().fastParse(parsed.message("Outer")));
        assertFalse(parsed.helper().fastParse(parsed.message("Inner")));
        assertTrue(parsed.helper().fastWrite(parsed.message("FlaggedInner")));
        assertFalse(parsed.helper().fastWrite(parsed.message("Outer")));
    }

    @Test
    void invalidValuesAreRejected() {
        final var badBoolean = assertThrows(
                PbjCompilerException.class, () -> PerformanceOptions.of("yes", null, null, null, null, "test project"));
        assertTrue(badBoolean.getMessage().contains("pbj.fast_parse must be true or false"), badBoolean.getMessage());
        final var badChoice = assertThrows(
                PbjCompilerException.class,
                () -> PerformanceOptions.of(null, null, null, "packed", null, "test project"));
        assertTrue(badChoice.getMessage().contains("pbj.oneof_storage must be one of"), badChoice.getMessage());
        assertThrows(
                PbjCompilerException.class,
                () -> PerformanceOptions.of(null, null, null, null, "fastest", "test project"));
        // in a file comment
        assertThrows(PbjCompilerException.class, () -> parse("""
                syntax = "proto3";
                // <<<pbj.views = maybe>>>
                package example;
                message Plain { int64 a = 1; }
                """, PerformanceOptions.NONE));
        // in a message comment, reported with the message's name
        final Parsed parsed = parse("""
                syntax = "proto3";
                // <<<pbj.oneof_storage = "packed">>>
                message Bad { int64 a = 1; }
                """, PerformanceOptions.NONE);
        final var badMessage =
                assertThrows(PbjCompilerException.class, () -> parsed.helper().checkOptions(parsed.message("Bad")));
        assertTrue(badMessage.getMessage().contains("message Bad"), badMessage.getMessage());
    }

    @Test
    void compilerRejectsInvalidMessageOptionsEvenWhenNothingReadsThem() throws Exception {
        final Path proto = directory.resolve("bad.proto");
        Files.writeString(proto, """
                syntax = "proto3";
                package example;
                message Outer {
                    // <<<pbj.fast_parse = sometimes>>>
                    message Inner { int64 a = 1; }
                }
                """);
        final Path out = directory.resolve("out");
        final var error = assertThrows(
                PbjCompilerException.class,
                () -> PbjCompiler.compileFilesIn(
                        List.of(proto.toFile()),
                        Set.of(),
                        Set.of(directory.toFile()),
                        out.toFile(),
                        out.toFile(),
                        null,
                        false,
                        false,
                        Set.of(),
                        Set.of(),
                        PerformanceOptions.NONE));
        assertTrue(error.getMessage().contains("message Inner"), error.getMessage());
    }

    @Test
    void fastParseAddsAnArrayParserOnlyWhereEnabled() throws Exception {
        final Path proto = directory.resolve("model.proto");
        Files.writeString(proto, """
                syntax = "proto3";
                package example;
                message Simple { int64 count = 1; string name = 2; }
                // <<<pbj.fast_parse = false>>>
                message OptedOut { int64 count = 1; }
                message WithList { repeated int64 counts = 1; }
                """);
        final Path plain = directory.resolve("plain");
        final Path max = directory.resolve("max");
        for (var run : List.of(
                Map.entry(plain, PerformanceOptions.NONE), Map.entry(max, project(null, null, null, null, "max")))) {
            PbjCompiler.compileFilesIn(
                    List.of(proto.toFile()),
                    Set.of(),
                    Set.of(directory.toFile()),
                    run.getKey().toFile(),
                    run.getKey().toFile(),
                    null,
                    true,
                    false,
                    Set.of(),
                    Set.of(),
                    run.getValue());
        }
        final String simple = Files.readString(max.resolve("example/codec/SimpleProtoCodec.java"));
        assertTrue(simple.contains("public static @NonNull Simple parseArray("), simple);
        assertFalse(Files.readString(plain.resolve("example/codec/SimpleProtoCodec.java"))
                .contains("parseArray("));
        // a message comment opts out of the project setting
        assertFalse(Files.readString(max.resolve("example/codec/OptedOutProtoCodec.java"))
                .contains("parseArray("));
        // every kind of field is supported, including repeated fields
        assertTrue(Files.readString(max.resolve("example/codec/WithListProtoCodec.java"))
                .contains("parseArray("));
        // models change only with pbj.views, which adds a nested View
        assertFalse(Files.readString(plain.resolve("example/Simple.java")).contains("class View"));
        assertTrue(Files.readString(max.resolve("example/Simple.java")).contains("public static final class View"));
    }
}
