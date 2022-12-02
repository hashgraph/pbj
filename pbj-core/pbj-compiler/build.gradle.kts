/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

plugins {
    `java-gradle-plugin`
    id("com.hedera.pbj.conventions")
    id("com.hedera.pbj.maven-publish")
    id("com.gradle.plugin-publish").version("1.1.0")
    id("antlr")
}

dependencies {
    implementation(libs.bundles.jetbrains)
    antlr(libs.bundles.antlr)
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.hashgraph.pbj.compiler.impl.grammar")
}

gradlePlugin {
    plugins {
        create("compiler") {
            id = "com.hedera.pbj.pbj-compiler"
            group = "com.hedera.pbj"
            implementationClass = "com.hedera.hashgraph.pbj.compiler.PbjCompilerPlugin"
            displayName = "PBJ Compiler"
            description = "The PBJ Protobuf plugin provides protobuf compilation to java records."
        }
    }
}

pluginBundle {
    website = "https://github.com/hashgraph/pbj"
    vcsUrl = "https://github.com/hashgraph/pbj"

    tags = listOf("protobuf", "compiler", "generator", "runtime")
}
