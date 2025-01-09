// SPDX-License-Identifier: Apache-2.0
import com.autonomousapps.DependencyAnalysisSubExtension

plugins {
    id("com.hedera.pbj.conventions")
    id("com.gradle.plugin-publish")
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.pbj.compiler.impl.grammar")
}

// Do not generate Java Doc for generated antlr grammar
tasks.withType<Javadoc> { excludes.add("com/hedera/pbj/compiler/impl/grammar/**") }

tasks.register("release") {
    group = "release"
    dependsOn(tasks.named("publishPlugins"))
}

// As a Gradle plugin cannot be a Java Module, we do not have official internal packages.
// We tell the dependency analysis to treat packages as "internal".
configure<DependencyAnalysisSubExtension>() { abi { exclusions { ignoreSubPackage("impl") } } }
