plugins {
    java
    id("hedera.pbj-compiler") version "1.0-SNAPSHOT"
    // We depend on Google protobuf plugin as we generate protobuf code using it as well as pbj. Then use it in tests to
    // compare output and parsing with pbj to make sure it matches.
    id("com.google.protobuf") version "0.9.1"
}

group = "com.hedera.hashgraph.pbj.integration-tests"
version = "1.0-SNAPSHOT"

repositories {
    // add the test maven repo we build with compiler and runtime
    maven {
        url = file("../build/testRepo").toURI()
        name = "test"
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("com.hedera.hashgraph.pbj:pbj-runtime:1.0-SNAPSHOT")
    implementation("com.google.protobuf:protobuf-java:3.21.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

// Configure PBJ to set the base package for generated code
pbj {
    basePackage.set("com.hedera.hashgraph.pbj.test.integration")
}