// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.application")
    id("org.hiero.gradle.feature.legacy-classpath")
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

group = "com.hedera.pbj"

jvmDependencyConflicts.consistentResolution {
    platform("com.hedera.hashgraph:hiero-dependency-versions")
}

javaModuleDependencies {
    moduleNameToGA.put("com.hedera.pbj.integration.tests", "com.hedera.pbj:pbj-integration-tests")
    moduleNameToGA.put("com.hedera.pbj.grpc.common", "com.hedera.pbj:pbj-grpc-common")
}

mainModuleInfo {
    runtimeOnly("io.helidon.common")
    runtimeOnly("io.helidon.webclient.http2")
    runtimeOnly("io.helidon.webserver")
}

testModuleInfo {
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.opentest4j")
    requires("com.google.protobuf.util")
    requires("com.hedera.pbj.grpc.helidon")
    runtimeOnly("com.hedera.pbj.grpc.client.helidon")
    requires("io.helidon.common")
    runtimeOnly("io.helidon.common.tls")
    runtimeOnly("io.helidon.webclient.api")
    runtimeOnly("io.helidon.webclient.http2")
    requires("io.helidon.webserver")
    runtimeOnly("io.grpc.netty")

    runtimeOnly("org.junit.jupiter.engine")
}

jmhModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.hedera.pbj.grpc.common")
    requires("com.google.protobuf.util")
    requires("io.helidon.common")
    requires("io.helidon.webserver")
    runtimeOnly("io.helidon.logging.jul")
}

// version is added to module-info.class files
version = "0.0.0.1-test"

// IMPROVE: Disable module-info transform for 'testRuntimeClasspath' which leads to an error
// possible caused by a cycle produced by depending on 'pbj-compiler' in multiple ways which
// eventually leads to 'pbj-compiler' depending on itself in this context.
configurations.testRuntimeClasspath {
    attributes { attribute(Attribute.of("javaModule", Boolean::class.javaObjectType), false) }
}

// IMPROVE: Test code should not have a direct dependency to 'com.hedera.pbj.compiler'
dependencies { testImplementation("com.hedera.pbj:pbj-compiler") { isTransitive = false } }

dependencyAnalysis { issues { all { onAny { exclude("com.hedera.pbj:pbj-compiler") } } } }

jmh {
    // IMPROVE: JMH code should not depend on test code
    includeTests = true

    // The Helidon GrpcBench misbehaves in GitHub runners:
    //  - server threads encounter InterruptedException
    //  - the benchmark times out after 6 hours
    // When run locally on a laptop, the benchmark completes normally within about 1 hour.
    // It's unclear why it fails in GitHub.
    // (Also, it's unclear if we want to spend 1 hour running it in GitHub in the first place as
    // this may be expensive.)
    // So we disable it when running the `jmh` Gradle target for now.
    // One can still run it locally from an IDE or command-line using the JMH runner.
    excludes = listOf(".*GrpcBench.*")
}

// `./gradlew build -PpbjPerformance=max` generates the code with every fast code generation option
// on (see
// docs/fast-codegen-plan.md), so the whole suite also runs against the new paths. CI runs both
// configurations.
// "default" leaves every option at its default.
val pbjPerformance = providers.gradleProperty("pbjPerformance").getOrElse("default")

require(pbjPerformance == "default" || pbjPerformance == "max") {
    "pbjPerformance must be \"default\" or \"max\", not \"$pbjPerformance\""
}

// Benchmarks of APIs that exist only with every option on, such as views of HAPI messages
if (pbjPerformance == "max") {
    sourceSets.named("jmh") { java.srcDir("src/jmhMax/java") }
}

// Tests can tell which configuration they run in
tasks.withType<Test>().configureEach { systemProperties["pbj.performance"] = pbjPerformance }

// Avoid a clash with Google protoc models when .proto files don't specify `pbj.java_package`:
pbj {
    performance = pbjPerformance
    javaPackageSuffix = ".pbj.integration.tests"
    // StateValue is the catalog Hiero uses, so every HAPI state value is a diff root.
    diffMessageContainers = setOf("proto.DiffCatalog", "com.hedera.hapi.platform.state.StateValue")
    // Leave tracking at its default (false). Local annotations cover the tracked path;
    // HAPI roots compile and run without tracking.
    diffMessages = setOf("proto.Account", "proto.Token", "proto.Topic", "proto.PlainDiffRoot")
}

// HAPI protobuf files from the published hedera-protobuf-java-api artifact. The proto sources
// moved into hiero-consensus-node, and hedera-protobufs no longer carries newer files such as
// StateValue. The artifact bundles them under one root, so imports like "services/x.proto" resolve.
val hapiProtos =
    configurations.dependencyScope("hapiProtos") {
        dependencies.add(
            project.dependencies.create("com.hedera.hashgraph:hedera-protobuf-java-api:0.78.2")
        )
    }
val hapiProtosClasspath =
    configurations.resolvable("hapiProtosClasspath") {
        extendsFrom(hapiProtos.get())
        isTransitive = false
    }
val extractHapiProtos =
    tasks.register<Sync>("extractHapiProtos") {
        from(hapiProtosClasspath.map { files -> files.map { zipTree(it) } }) {
            include("**/*.proto")
            // Same exclusions as hiero-consensus-node's own PBJ build
            exclude("mirror/**", "sdk/**")
        }
        includeEmptyDirs = false
        into(layout.buildDirectory.dir("hapi-protos"))
    }

sourceSets {
    main {
        pbj { srcDir(extractHapiProtos) }
        proto { srcDir(extractHapiProtos) }
    }
}

// The full HAPI generates several thousand sources, more than javac's default heap handles
tasks.compileJava {
    options.isFork = true
    options.forkOptions.memoryMaximumSize = "4g"
}

val custom = sourceSets.create("custom") { proto { setSrcDirs(emptyList<Directory>()) } }

java.registerFeature("custom") { usingSourceSet(custom) }

// Exclude protoc generated from docs
tasks.javadoc {
    exclude("com/hederahashgraph/service/proto/**")
    exclude("**/protoc/**")
    exclude("com/hedera/services/stream/proto/**")
    exclude("com/hedera/hashgraph/pbj/integration/**")
    exclude("com/hedera/pbj/test/proto/java/**")
}

@Suppress("UnstableApiUsage")
testing {
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
        tasks.register<Test>("testNoFuzz") {
            testClassesDirs = sources.output.classesDirs
            classpath = sources.runtimeClasspath
            useJUnitPlatform { excludeTags("FUZZ_TEST") }
        }
        targets.named("test") {
            testTask {
                useJUnitPlatform { excludeTags("FUZZ_TEST") }
                dependsOn(tasks.named("fuzzTest"))
            }
        }
        dependencies {
            this.implementation(project()) { capabilities { requireFeature("custom") } }
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
