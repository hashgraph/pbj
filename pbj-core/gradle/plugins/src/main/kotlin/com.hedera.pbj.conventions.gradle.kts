// SPDX-License-Identifier: Apache-2.0
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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
