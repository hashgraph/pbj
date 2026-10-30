// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every method PBJ generates stays under a budget well below HotSpot's 8000-byte limit for compiling a method at all
 * ({@code -XX:+DontCompileHugeMethods}, the default): a method over it always runs in the interpreter. Without this
 * test, one more HAPI field or transaction type could silently push a parser or writer over the limit.
 */
final class GeneratedMethodSizeTest {
    /** The largest method allowed, in bytes of bytecode, leaving room for HAPI to grow. */
    private static final int BUDGET = 7000;

    private static final Path GENERATED_SOURCES = Path.of("build/generated/source/pbj-proto/main/java");
    private static final Path CLASSES = Path.of("build/classes/java/main");

    @Test
    void everyGeneratedMethodIsUnderTheBudget() throws IOException {
        assertTrue(Files.isDirectory(GENERATED_SOURCES), GENERATED_SOURCES.toAbsolutePath() + " not found");
        final List<String> tooLarge = new ArrayList<>();
        int classes = 0;
        try (Stream<Path> sources = Files.walk(GENERATED_SOURCES)) {
            for (final Path source :
                    sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                final String relative = GENERATED_SOURCES.relativize(source).toString();
                final String className = relative.substring(0, relative.length() - ".java".length());
                final Path classFile = CLASSES.resolve(className + ".class");
                // the class and its nested classes, such as a model's View or Builder
                final String simpleName = classFile.getFileName().toString().replace(".class", "");
                try (Stream<Path> siblings = Files.list(classFile.getParent())) {
                    for (final Path file : siblings.filter(p -> {
                                final String name = p.getFileName().toString();
                                return name.equals(simpleName + ".class") || name.startsWith(simpleName + "$");
                            })
                            .toList()) {
                        classes++;
                        tooLarge.addAll(methodsOverBudget(Files.readAllBytes(file)));
                    }
                }
            }
        }
        assertTrue(classes > 1000, "only " + classes + " generated classes found");
        assertTrue(tooLarge.isEmpty(), "Generated methods over " + BUDGET + " bytes:\n" + String.join("\n", tooLarge));
    }

    private static List<String> methodsOverBudget(final byte[] classBytes) {
        final ClassModel model = ClassFile.of().parse(classBytes);
        final List<String> found = new ArrayList<>();
        for (final MethodModel method : model.methods()) {
            // static initializers run once, so whether they are compiled doesn't matter
            if (method.methodName().equalsString("<clinit>")) {
                continue;
            }
            method.findAttribute(Attributes.code()).ifPresent(code -> {
                if (code.codeLength() > BUDGET) {
                    found.add("%6d %s.%s%s"
                            .formatted(
                                    code.codeLength(),
                                    model.thisClass().asInternalName().replace('/', '.'),
                                    method.methodName().stringValue(),
                                    method.methodTypeSymbol().displayDescriptor()));
                }
            });
        }
        return found;
    }
}
