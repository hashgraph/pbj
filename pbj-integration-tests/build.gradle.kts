plugins {
    java
    id("com.hedera.pbj.pbj-compiler") version "1.0-SNAPSHOT"
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj. Then use it in tests to
    // compare output and parsing with pbj to make sure it matches.
    id("com.google.protobuf") version "0.9.1"
    // add jmh for performance benchmarks
    id("me.champeau.jmh") version "0.6.8"
}


group = "com.hedera.pbj.integration-tests"
version = "1.0-SNAPSHOT"

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
val hapiServicesProtoSrc = buildDir.resolve("repos/hapi/services")
sourceSets {
    main {
        allSource.srcDir(hapiServicesProtoSrc)
        proto {
            srcDir(hapiServicesProtoSrc)
        }
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

// Configure PBJ to set the base package for generated code
pbj {
    basePackage.set("com.hedera.hashgraph.pbj.test.integration")
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
