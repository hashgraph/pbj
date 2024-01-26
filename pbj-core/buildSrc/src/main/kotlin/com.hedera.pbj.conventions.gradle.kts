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

import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    id("java")
    id("jacoco")
    id("antlr")
    id("org.gradlex.java-module-dependencies")
    id("com.adarshr.test-logger")
    id("com.hedera.pbj.repositories")
    id("com.hedera.pbj.spotless-conventions")
    id("com.hedera.pbj.spotless-java-conventions")
    id("com.hedera.pbj.spotless-kotlin-conventions")
    id("com.hedera.pbj.maven-publish")
}

group = "com.hedera.pbj"

javaModuleDependencies {
    moduleNameToGA.put(
        "com.github.spotbugs.annotations",
        "com.github.spotbugs:spotbugs-annotations"
    )
    moduleNameToGA.put("com.google.protobuf", "com.google.protobuf:protobuf-java")
    moduleNameToGA.put("org.antlr.antlr4.runtime", "org.antlr:antlr4-runtime")
    moduleNameToGA.put("org.mockito.inline", "org.mockito:mockito-inline")
    // The following line is commented out because it causes the Gradle build to break
    // moduleNameToGA.put("org.mockito.junit.jupiter", "org.mockito:mockito-junit-jupiter")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }

    // Enable JAR file generation required for publishing
    withJavadocJar()
    withSourcesJar()
}

testing {
    @Suppress("UnstableApiUsage") suites.getByName<JvmTestSuite>("test") { useJUnitJupiter() }
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 436 // octal: 0664
    dirMode = 509 // octal: 0775
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).tags(
        "apiNote:a:API Note:",
        "implSpec:a:Implementation Requirements:",
        "implNote:a:Implementation Note:"
    )
}

testlogger {
    theme = ThemeType.MOCHA
    slowThreshold = 10000
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
}

val libs = the<VersionCatalogsExtension>().named("libs")

configurations {
    // Treat the ANTLR compiler as a separate tool that should not end up on the compile/runtime
    // classpath of our runtime.
    // https://github.com/gradle/gradle/issues/820
    api { setExtendsFrom(extendsFrom.filterNot { it == antlr.get() }) }
}

dependencies {
    antlr("org.antlr:antlr4") {
        version { require(libs.findVersion("org.antlr.antlr4.runtime").get().requiredVersion) }
    }
}

// See: https://github.com/gradle/gradle/issues/25885
tasks.named("sourcesJar") { dependsOn(tasks.generateGrammarSource) }

tasks.withType<com.autonomousapps.tasks.CodeSourceExploderTask>().configureEach {
    dependsOn(tasks.withType<AntlrTask>())
}

// Ensure JaCoCo coverage is generated and aggregated
tasks.jacocoTestReport.configure {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val testExtension = tasks.test.get().extensions.getByType<JacocoTaskExtension>()
    executionData.setFrom(testExtension.destinationFile)

    shouldRunAfter(tasks.named("check"))
}

// Ensure the check task also runs the JaCoCo coverage report
tasks.named("check").configure { dependsOn(tasks.named<JacocoReport>("jacocoTestReport")) }
