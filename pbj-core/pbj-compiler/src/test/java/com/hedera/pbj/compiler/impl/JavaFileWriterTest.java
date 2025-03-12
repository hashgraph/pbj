// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JavaFileWriterTest {
    @TempDir
    private static File outputDir;

    @Test
    void testWriteFile() throws IOException {
        final File file = new File(outputDir, UUID.randomUUID().toString());
        final JavaFileWriter generator = new JavaFileWriter(file, "my.test.java.package");

        generator.addImport("java.util.*");
        generator.addImport("java.io.File");

        generator.append("class MyTestJavaClass { /* blah */ }");

        generator.writeFile();

        final String string = Files.readString(file.toPath());
        assertEquals(
                """
                // SPDX-License-Identifier: Apache-2.0
                package my.test.java.package;

                import java.io.File;
                import java.util.*;

                class MyTestJavaClass { /* blah */ }
                """,
                string);
    }
}
