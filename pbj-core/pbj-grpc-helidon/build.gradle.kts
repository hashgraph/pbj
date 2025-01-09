// SPDX-License-Identifier: Apache-2.0
plugins {
    id("com.hedera.pbj.helidon")
    id("com.hedera.pbj.protoc") // protobuf plugin is only used for tests
}

// These annotation processors are used to generate config and other files that Helidon needs
mainModuleInfo {
    annotationProcessor("io.helidon.common.features.processor")
    annotationProcessor("io.helidon.codegen.apt")
    annotationProcessor("io.helidon.builder.codegen")
}

testModuleInfo {
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("io.helidon.webclient")
    requires("io.helidon.webserver")
    requires("io.helidon.webserver.http2")
    requires("io.helidon.webserver.observe.metrics")
    requires("io.helidon.webclient.http2")
    requires("com.google.protobuf.util")
    requires("io.grpc.protobuf")
    requires("io.grpc.netty")
    requires("io.grpc.stub")
    requiresStatic("com.github.spotbugs.annotations")
    requiresStatic("java.annotation")
}

tasks.named("compileJava") { dependsOn(":pbj-runtime:jar") }

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom { description.set("A Helidon gRPC plugin with PBJ") }
    }
}
