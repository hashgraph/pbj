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
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.7.3")
    implementation("com.hedera.pbj:pbj-runtime:${project.version}")
    implementation("com.google.protobuf:protobuf-java:3.21.12")
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

/** Exclude protoc generated from docs, so we can see clear warnings and errors */
tasks.withType<Javadoc>() {
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

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<Test> {
    // We are running a lot of tests 10s of thousands, so they need to run in parallel
    systemProperties["junit.jupiter.execution.parallel.enabled"] = true
    systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
    // us parallel GC to keep up with high temporary garbage creation, and allow GC to use 40% of CPU if needed
    jvmArgs("-XX:+UseParallelGC","-XX:GCTimeRatio=90")
//    jvmArgs("-XX:+UseZGC","-XX:ZAllocationSpikeTolerance=2")
//    jvmArgs("-XX:+UseG1GC", "-XX:GCTimeRatio=90", "-XX:MaxGCPauseMillis=100")
    // Some also need more memory
    minHeapSize = "512m"
    maxHeapSize = "4096m"
}

jmh {
    includes.add("ProtobufObjectBench")
//    includes.add("AccountDetailsBench.writePbjByteDirect")
//    includes.add("EverythingBench")
//    includes.add("EverythingBench.parsePbjByteBufferDirect")
    jmhVersion.set("1.35")
    includeTests.set(true)
//    jvmArgsAppend.add("-XX:MaxInlineSize=100 -XX:MaxInlineLevel=20")
}

tasks.jmhJar {
    manifest {
        attributes(mapOf("Multi-Release" to true))
    }
}
