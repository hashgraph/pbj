// SPDX-License-Identifier: Apache-2.0
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
