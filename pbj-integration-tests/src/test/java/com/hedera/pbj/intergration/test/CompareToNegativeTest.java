/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.intergration.test;

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
        compileFilesIn(List.of(new File(fileUrl.toURI())), outputDir, outputDir);
    }
}
