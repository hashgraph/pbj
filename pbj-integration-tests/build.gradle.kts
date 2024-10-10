/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.gradle.tasks.GitClone

plugins {
    id("com.hedera.gradle.module.application")
    id("org.gradlex.java-module-dependencies")
    // jmh for performance benchmarks
    id("com.hedera.gradle.feature.benchmark")
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj.
    // Then use it in tests to compare output and parsing with pbj to make sure it matches.
    id("com.hedera.gradle.feature.protobuf")
    // The "plugin-under-test"
    id("com.hedera.pbj.pbj-compiler")
}

extraJavaModuleInfo.knownModule(
    "org.checkerframework:checker-qual",
    "org.checkerframework.checker.qua"
)

// We use the dependency versions from the included main build, which we need to configure here
buildscript {
    dependencies { classpath(platform("com.hedera.hashgraph:hedera-dependency-versions")) }
}

jvmDependencyConflicts.consistentResolution {
    platform("com.hedera.hashgraph:hedera-dependency-versions")
}

mainModuleInfo {
    requires("com.google.common")
    requires("com.google.protobuf")
    requires("com.google.protobuf.util")
    requires("com.hedera.pbj.runtime")
    requires("io.grpc")
    requires("io.grpc.protobuf")
    requires("io.grpc.stub")
    requires("org.antlr.antlr4.runtime")
    requiresStatic("com.github.spotbugs.annotations")
}

testModuleInfo {
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    runtimeOnly("org.junit.jupiter.engine")
}

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
val cloneHederaProtobufs =
    tasks.register<GitClone>("cloneHederaProtobufs") {
        url = "https://github.com/hashgraph/hedera-protobufs.git"
        // choose tag or branch of HAPI you would like to test with
        // branch = "main"
        tag = "v0.55.0"
    }

sourceSets {
    main {
        pbj {
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("services") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("streams") })
        }
        proto {
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("services") })
            srcDir(cloneHederaProtobufs.flatMap { it.localCloneDirectory.dir("streams") })
        }
    }
}

// Exclude protoc generated from docs
tasks.javadoc {
    exclude("com/hederahashgraph/api/proto/**")
    exclude("com/hederahashgraph/service/proto/**")
    exclude("com/hedera/services/stream/proto/**")
    exclude("com/hedera/hashgraph/pbj/integration/**")
    exclude("pbj/**")
}

tasks.spotlessKotlinGradleCheck

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
            systemProperties["com.hedera.pbj.intergration.test.fuzz.useRandomSeed"] = true
        }
        targets.named("test") {
            testTask {
                useJUnitPlatform { excludeTags("FUZZ_TEST") }
                dependsOn(tasks.named("fuzzTest"))
            }
        }
    }
}

jmh { includeTests = true }

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
