plugins {
    id("java-library")
    // jmh and protobuf plugins are only used for tests
    id("me.champeau.jmh") version "0.6.8"
    id("com.google.protobuf") version "0.9.1"
}

group = "com.hedera.hashgraph.pbj.runtime"
version = "1.0-SNAPSHOT"
tasks.jar {
    archiveBaseName.set("pbj-runtime")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation("com.google.protobuf:protobuf-java:3.21.9")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
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

//publishing {
//    publications {
//        create<MavenPublication>("maven") {
//            groupId = "com.hedera.hashgraph.pbj"
//            artifactId = "pbj-runtime"
//            version = "1.0-SNAPSHOT"
//            from(components["java"])
//        }
//    }
//}
