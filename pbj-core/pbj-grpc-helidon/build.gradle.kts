// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.library")
    id("org.hiero.gradle.feature.protobuf") // protobuf plugin is only used for tests
}

description = "A Helidon gRPC plugin with PBJ"

// These annotation processors are used to generate config and other files that Helidon needs
mainModuleInfo {
    annotationProcessor("io.helidon.common.features.processor")
    annotationProcessor("io.helidon.codegen.apt")
    annotationProcessor("io.helidon.builder.codegen")
}

testModuleInfo {
    requires("com.google.common")
    requires("com.google.protobuf")
    requires("com.google.protobuf.util")
    requires("io.grpc")
    requires("io.grpc.protobuf")
    requires("io.grpc.stub")
    requires("io.helidon.http.media")
    requires("io.helidon.webclient.api")
    requires("io.helidon.webclient.http2")
    requires("io.netty.codec.http2")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requiresStatic("java.annotation")
    runtimeOnly("io.grpc.netty")
    runtimeOnly("io.helidon.webserver.observe.metrics")
}

tasks.named("compileJava") { dependsOn(":pbj-runtime:jar") } // FIXME !!!
