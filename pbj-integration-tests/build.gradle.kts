// SPDX-License-Identifier: Apache-2.0
import org.hiero.gradle.tasks.GitClone

plugins {
    id("org.hiero.gradle.module.application")
    id("org.gradlex.java-module-dependencies")
    // jmh for performance benchmarks
    id("org.hiero.gradle.feature.benchmark")
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj.
    // Then use it in tests to compare output and parsing with pbj to make sure it matches.
    id("org.hiero.gradle.feature.protobuf")
    // The "plugin-under-test"
    id("com.hedera.pbj.pbj-compiler")
}

// We use the dependency versions from the included main build, which we need to configure here
buildscript {
    dependencies { classpath(platform("com.hedera.hashgraph:hiero-dependency-versions")) }
}

jvmDependencyConflicts.consistentResolution {
    platform("com.hedera.hashgraph:hiero-dependency-versions")
}

mainModuleInfo {
    requires("com.hedera.pbj.runtime")

    requires("com.google.common")
    requires("com.google.protobuf")
    requires("io.grpc")
    requires("io.grpc.protobuf")
    requires("io.grpc.stub")
    requires("org.antlr.antlr4.runtime")
    requiresStatic("com.github.spotbugs.annotations")
}

testModuleInfo {
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("com.google.protobuf.util")
    runtimeOnly("org.junit.jupiter.engine")
}

jmhModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.google.protobuf.util")
}

// IMPROVE: Disable module-info transform for 'testRuntimeClasspath' which leads to an error
// possible caused by a cycle produced by depending on 'pbj-compiler' in multiple ways which
// eventually leads to 'pbj-compiler' depending on itself in this context.
configurations.testRuntimeClasspath {
    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
}

// IMPROVE: Test code should not have a direct dependency to 'com.hedera.pbj.compiler'
dependencies { testImplementation("com.hedera.pbj:pbj-compiler") { isTransitive = false } }

dependencyAnalysis { issues { all { onAny { exclude("com.hedera.pbj:pbj-compiler") } } } }

// IMPROVE: JMH code should not depend on test code
jmh { includeTests = true }

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
val cloneHederaProtobufs =
    tasks.register<GitClone>("cloneHederaProtobufs") {
        localCloneDirectory = layout.buildDirectory.dir("hedera-protobufs")
        url = "https://github.com/hashgraph/hedera-protobufs.git"
        // choose tag or branch of HAPI you would like to test with
        // branch = "main"
        tag = "v0.55.0"
    }

sourceSets {
    main {
        pbj {
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("block") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("platform") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("services") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("streams") })
        }
        proto {
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("block") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("platform") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("services") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("streams") })
        }
    }
}

// Exclude protoc generated from docs
tasks.javadoc {
    exclude("com/hederahashgraph/api/proto/**")
    exclude("com/hederahashgraph/service/proto/**")
    exclude("com/hedera/hapi/**/legacy/**")
    exclude("com/hedera/**/protoc/**")
    exclude("com/hedera/services/stream/proto/**")
    exclude("com/hedera/hashgraph/pbj/integration/**")
    exclude("com/hedera/pbj/test/proto/java/**")
    exclude("pbj/**")
}

testing {
    @Suppress("UnstableApiUsage")
    suites.named<JvmTestSuite>("test") {
        tasks.register<Test>("fuzzTest") {
            testClassesDirs = sources.output.classesDirs
            classpath = sources.runtimeClasspath
            useJUnitPlatform { includeTags("FUZZ_TEST") }
            enableAssertions = false
        }
        tasks.register<Test>("randomFuzzTest") {
            testClassesDirs = sources.output.classesDirs
            classpath = sources.runtimeClasspath
            useJUnitPlatform { includeTags("FUZZ_TEST") }
            enableAssertions = false
            systemProperties["com.hedera.pbj.integration.test.fuzz.useRandomSeed"] = true
        }
        targets.named("test") {
            testTask {
                useJUnitPlatform { excludeTags("FUZZ_TEST") }
                dependsOn(tasks.named("fuzzTest"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // We are running a lot of tests 10s of thousands, so they need to run in parallel
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // us parallel GC to keep up with high temporary garbage creation, and allow GC to use 40% of
    // CPU if needed
    jvmArgs("-XX:+UseParallelGC", "-XX:GCTimeRatio=90")
    // Some also need more memory
    minHeapSize = "512m"
    maxHeapSize = "4096m"
}
