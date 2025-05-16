// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static com.hedera.pbj.compiler.PbjCompiler.compileFilesIn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompareToNegativeTest {

    @TempDir
    private static File outputDir;

    @Test
    void testNonComparableSubObj() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> getCompileFilesIn("non_compilable_comparable_sub_obj.proto"));
        assertEquals(
                "Field NonComparableSubObj.subObject specified in `pbj.comparable` option must implement `Comparable` interface but it doesn't.",
                exception.getMessage());
    }

    @Test
    void testRepeatedField() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> getCompileFilesIn("non_compilable_comparable_repeated.proto"));
        assertEquals(
                "Field `int32List` specified in `pbj.comparable` option is repeated. Repeated fields are not supported by this option.",
                exception.getMessage());
    }

    @Test
    void testNonComparableOneOfField() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> getCompileFilesIn("non_compilable_comparable_oneOf.proto"));
        assertEquals(
                "Field NonComparableSubObj.subObject specified in `pbj.comparable` option must implement `Comparable` interface but it doesn't.",
                exception.getMessage());
    }

    private static void getCompileFilesIn(String fileName) throws Exception {
        URL fileUrl = CompareToNegativeTest.class.getClassLoader().getResource(fileName);
        compileFilesIn(List.of(new File(fileUrl.toURI())), outputDir, outputDir, null, true);
    }
}
