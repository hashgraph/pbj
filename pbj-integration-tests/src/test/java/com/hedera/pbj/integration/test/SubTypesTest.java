// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static com.hedera.pbj.compiler.PbjCompiler.compileFilesIn;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SubTypesTest {
    @TempDir
    private static File outputDir;

    @Test
    void testSubTypes() throws Exception {
        // Verify if we're able to reference inner and imported types w/o using fully qualified names
        getCompileFilesIn("sub_types.proto", "sub_types_import.proto");
    }

    private static void getCompileFilesIn(String... fileNames) throws Exception {
        final List<File> files = Arrays.stream(fileNames)
                .map(fileName -> {
                    try {
                        final URL url = SubTypesTest.class.getClassLoader().getResource(fileName);
                        final URI uri = url.toURI();
                        return new File(uri);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();
        compileFilesIn(files, outputDir, outputDir, null);
    }
}
