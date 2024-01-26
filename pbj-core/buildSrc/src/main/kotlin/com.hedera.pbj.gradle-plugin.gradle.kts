/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.pbj.conventions")
    id("com.gradle.plugin-publish")
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.pbj.compiler.impl.grammar")
}

// Do not generate Java Doc for generated antlr grammar
tasks.withType<Javadoc> { excludes.add("com/hedera/pbj/compiler/impl/grammar/**") }

tasks.register("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.named("publishPlugins"))
}
