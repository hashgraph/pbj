/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.pbj.conventions")
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
    requires("io.helidon.webclient.http2")
    requires("com.google.protobuf.util")
    requires("io.grpc.protobuf")
    requires("io.grpc.netty")
    requires("io.grpc.stub")
    requiresStatic("com.github.spotbugs.annotations")
    requiresStatic("java.annotation")
}

tasks.named("compileJava") {
    dependsOn(":pbj-runtime:jar")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            description.set(
                "A Helidon gRPC plugin with PBJ"
            )
        }
    }
}

