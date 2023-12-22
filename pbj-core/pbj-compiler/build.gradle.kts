/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

plugins { id("com.hedera.pbj.gradle-plugin") }

// This project does not have a module-info.java, as Gradle does not support plugins that are
// Java Modules. For consistency, we still defined dependencies in terms of Module Names here.
mainModuleInfo {
    requiresStatic("com.github.spotbugs.annotations")
    requires("org.antlr.antlr4.runtime")
}

testModuleInfo {
    requires("org.junit.jupiter.api")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
}

gradlePlugin {
    plugins {
        @Suppress("UnstableApiUsage")
        create("compiler") {
            id = "com.hedera.pbj.pbj-compiler"
            group = "com.hedera.pbj"
            implementationClass = "com.hedera.pbj.compiler.PbjCompilerPlugin"
            displayName = "PBJ Compiler"
            description = "The PBJ Protobuf plugin provides protobuf compilation to java records."
            website = "https://github.com/hashgraph/pbj"
            vcsUrl = "https://github.com/hashgraph/pbj"
            tags.set(listOf("protobuf", "compiler", "generator", "runtime"))
        }
    }
}
