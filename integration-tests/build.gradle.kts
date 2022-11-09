plugins {
    java
//    id("protobuf-gradle-plugin") version "0.9.1"
    id("hedera.pbj-compiler") version "1.0-SNAPSHOT"
    id("com.google.protobuf") version "0.9.1"
//    id("com.google.protobuf") version "0.8.19"
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

//
//buildscript {
//    repositories {
//        gradlePluginPortal()
//    }
//    dependencies {
//        classpath( "com.google.protobuf:protobuf-gradle-plugin:0.9.1")
//    }
//}