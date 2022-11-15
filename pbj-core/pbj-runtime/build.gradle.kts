plugins {
    id("java-library")
    id("maven-publish")
    // protobuf plugin is only used for tests
    id("com.google.protobuf") version "0.9.1"
}

group = "com.hedera.pbj"

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

tasks.assemble {
    dependsOn(tasks.publishToMavenLocal)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
