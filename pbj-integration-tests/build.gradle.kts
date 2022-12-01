plugins {
    java
    id("com.hedera.pbj.pbj-compiler").version("0.1.0-SNAPSHOT")
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj. Then use it in tests to
    // compare output and parsing with pbj to make sure it matches.
    id("com.google.protobuf").version("0.9.1")
    // add jmh for performance benchmarks
    id("me.champeau.jmh").version("0.6.8")
}

group = "com.hedera.pbj.integration-tests"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.hedera.pbj:pbj-runtime:${project.version}")
    implementation("com.google.protobuf:protobuf-java:3.21.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
sourceSets {
    main {
        pbj {
            srcDir(buildDir.resolve("repos/hapi/services"))
            srcDir(buildDir.resolve("repos/hapi/streams"))
        }
        proto {
            srcDir(buildDir.resolve("repos/hapi/services"))
            srcDir(buildDir.resolve("repos/hapi/streams"))
        }
    }
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.21.10"
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<Test> {
    // We are running a lot of tests 10s of thousands, so they need to run in parallel
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    // Some also need more memory
    minHeapSize = "512m"
    maxHeapSize = "2048m"
}

jmh {
    jmhVersion.set("1.35")
    includeTests.set(true)
}

tasks.jmhJar {
    manifest {
        attributes(mapOf("Multi-Release" to true))
    }
}
