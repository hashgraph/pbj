/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("io.helidon.webclient")
    requires("io.helidon.webserver")
    requires("io.helidon.webserver.http2")
    requires("io.helidon.webserver.observe.metrics")
    requires("io.helidon.webclient.http2")
    requires("com.google.common")
    requires("com.google.protobuf")
    requires("com.google.protobuf.util")
    requires("io.grpc")
    requires("io.grpc.protobuf")
    requires("io.grpc.stub")
    requires("io.helidon.http.media")
    requires("io.helidon.webclient.api")
    requires("io.helidon.webclient.http2")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requiresStatic("com.github.spotbugs.annotations")
    requiresStatic("java.annotation")
    runtimeOnly("io.grpc.netty")
}

tasks.named("compileJava") { dependsOn(":pbj-runtime:jar") } // FIXME !!!
