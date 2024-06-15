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
    id("java-library")
    id("com.hedera.pbj.conventions")
    id("com.google.protobuf") // protobuf plugin is only used for tests
    id("me.champeau.jmh")
}

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
    requires("com.hedera.node.hapi")
    requires("com.google.protobuf.util")
    requires("io.grpc.protobuf")
    requires("io.grpc.netty")
    requires("io.grpc.stub")
    requiresStatic("com.github.spotbugs.annotations")
    requiresStatic("javax.annotation")
}

tasks.named("compileJava") {
    dependsOn(":pbj-runtime:jar")
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.21.10"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}
