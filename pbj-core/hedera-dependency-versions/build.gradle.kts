/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
    id("com.hedera.gradle.base.lifecycle")
    id("com.hedera.gradle.base.jpms-modules")
}

group = "com.hedera.hashgraph"

val helidonVersion = "4.1.1"
val grpcVersion = "1.65.1"
val protobufVersion = "4.28.2"
val antlrVersion = "4.11.1"

dependencies.constraints {
    api("org.antlr:antlr4-runtime:$antlrVersion") {
        because("org.antlr.antlr4.runtime")
    }
    api("com.github.spotbugs:spotbugs-annotations:4.8.6") {
        because("com.github.spotbugs.annotations")
    }

    // The libs of this catalog are used to compile but are not bundled. The user will select helidon
    // which will have the set of these dependencies that are required.
    api("io.helidon.webserver:helidon-webserver:$helidonVersion") {
        because("io.helidon.webserver")
    }
    api("io.helidon.webserver:helidon-webserver-http2:$helidonVersion") {
        because("io.helidon.webserver.http2")
    }
    api("io.helidon.metrics:helidon-metrics-api:$helidonVersion") {
        because("io.helidon.metrics.api")
    }
    api("io.helidon.common.features:helidon-common-features-api:$helidonVersion") {
        because("io.helidon.common.features.api")
    }

    // Annotation processing
    api("io.helidon.common.features:helidon-common-features-processor:$helidonVersion") {
        because("io.helidon.common.features.processor")
    }
    api("io.helidon.config:helidon-config-metadata-processor:$helidonVersion") {
        because("io.helidon.config.metadata.processor")
    }
    api("io.helidon.codegen:helidon-codegen-apt:$helidonVersion") {
        because("io.helidon.codegen.apt")
    }
    api("io.helidon.builder:helidon-builder-codegen:$helidonVersion") {
        because("io.helidon.builder.codegen")
    }

    // Code generation
    api("org.antlr:antlr4:$antlrVersion")
    api("com.google.protobuf:protoc:$protobufVersion")
    api("io.grpc:protoc-gen-grpc-java:1.66.0")

    // Testing only
    api("com.google.guava:guava:33.3.1-jre") {
        because("com.google.common")
    }
    api("com.google.protobuf:protobuf-java:$protobufVersion") {
        because("com.google.protobuf")
    }
    api("com.google.protobuf:protobuf-java-util:$protobufVersion") {
        because("com.google.protobuf.util")
    }
    api("org.assertj:assertj-core:3.23.1") {
        because("org.assertj.core")
    }
    api("org.junit.jupiter:junit-jupiter-api:5.8.2") {
        because("org.junit.jupiter.api")
    }
    api("org.junit.jupiter:junit-jupiter-engine:5.8.2") {
        because("org.junit.jupiter.engine")
    }
    api("org.mockito:mockito-core:5.8.0") {
        because("org.mockito")
    }
    api("org.mockito:mockito-junit-jupiter:5.8.0") {
        because("org.mockito.junit.jupiter")
    }
    api("org.mockito:mockito-junit-jupiter:5.10.0") {
        because("org.mockito.junit.jupiter")
    }
    api("io.grpc:grpc-netty:$grpcVersion") {
        because("io.grpc.netty")
    }
    api("io.grpc:grpc-protobuf:$grpcVersion") {
        because("io.grpc.protobuf")
    }
    api("io.grpc:grpc-stub:$grpcVersion") {
        because("io.grpc.stub")
    }
    api("io.helidon.webclient:helidon-webclient:$helidonVersion") {
        because("io.helidon.webclient")
    }
    api("io.helidon.webclient:helidon-webclient-http2:$helidonVersion") {
        because("io.helidon.webclient.http2")
    }
}
