plugins {
    id("java")
    id("jacoco")
    id("com.hedera.pbj.pbj-compiler")
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj. Then use it in tests to
    // compare output and parsing with pbj to make sure it matches.
    id("com.google.protobuf").version("0.9.1")
    // add jmh for performance benchmarks
    id("me.champeau.jmh").version("0.7.1")
}

group = "com.hedera.pbj.integration-tests"

dependencies {
    implementation("com.hedera.pbj:pbj-runtime")
    implementation("com.hedera.pbj:pbj-compiler")
    implementation("com.google.protobuf:protobuf-java:3.21.12")
    implementation("com.google.protobuf:protobuf-java-util:3.21.12")
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
sourceSets {
    main {
        pbj {
            srcDir(layout.buildDirectory.dir("repos/hapi/services"))
            srcDir(layout.buildDirectory.dir("repos/hapi/streams"))
        }
        proto {
            srcDir(layout.buildDirectory.dir("repos/hapi/services"))
            srcDir(layout.buildDirectory.dir("repos/hapi/streams"))
        }
    }
}

/** Exclude protoc generated from docs, so we can see clear warnings and errors */
tasks.withType<Javadoc> {
    exclude("com/hederahashgraph/api/proto/**")
    exclude("com/hederahashgraph/service/proto/**")
    exclude("com/hedera/services/stream/proto/**")
    exclude("com/hedera/hashgraph/pbj/integration/**")
    exclude("pbj/**")
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
}

testing {
    @Suppress("UnstableApiUsage")
    suites.getByName<JvmTestSuite>("test") {
        useJUnitJupiter()

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
    }
}

tasks.test {
    useJUnitPlatform { excludeTags("FUZZ_TEST") }
    dependsOn("fuzzTest")
}

tasks.withType<Test>().configureEach {
    // We are running a lot of tests 10s of thousands, so they need to run in parallel
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // us parallel GC to keep up with high temporary garbage creation, and allow GC to use 40% of CPU if needed
    jvmArgs("-XX:+UseParallelGC", "-XX:GCTimeRatio=90")
    // Some also need more memory
    minHeapSize = "512m"
    maxHeapSize = "4096m"
}

jmh {
//    includes.add("AccountDetailsBench")
//    includes.add("JsonBench")
//    includes.add("VarIntBench")
//    includes.add("HashBench")
//    includes.add("EqualsHashCodeBench");

    jmhVersion.set("1.35")
    includeTests.set(true)
//    jvmArgsAppend.add("-XX:MaxInlineSize=100 -XX:MaxInlineLevel=20")
}

tasks.jmhJar {
    manifest {
        attributes(mapOf("Multi-Release" to true))
    }
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
tasks.named("check").configure {
    dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
}
