/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
    id("org.hiero.gradle.base.lifecycle")
    id("org.hiero.gradle.base.jpms-modules")
    id("org.hiero.gradle.check.spotless")
    id("org.hiero.gradle.check.spotless-kotlin")
}

group = "com.hedera.hashgraph"

val antlr = "4.13.2"
val grpc = "1.69.0"
val helidon = "4.1.6"
val protobuf = "4.28.2"

val junit5 = "5.8.2"
val mockito = "5.10.0"

dependencies.constraints {
    api("org.antlr:antlr4-runtime:$antlr") { because("org.antlr.antlr4.runtime") }
    api("com.github.spotbugs:spotbugs-annotations:4.8.6") {
        because("com.github.spotbugs.annotations")
    }

    // The libs of this catalog are used to compile but are not bundled. The user will select
    // helidon, which will have the set of these dependencies that are required.
    api("io.helidon.webserver:helidon-webserver:$helidon") { because("io.helidon.webserver") }
    api("io.helidon.webserver:helidon-webserver-http2:$helidon") {
        because("io.helidon.webserver.http2")
    }
    api("io.helidon.metrics:helidon-metrics-api:$helidon") { because("io.helidon.metrics.api") }
    api("io.helidon.common.features:helidon-common-features-api:$helidon") {
        because("io.helidon.common.features.api")
    }

    // Annotation processing
    api("io.helidon.common.features:helidon-common-features-processor:$helidon") {
        because("io.helidon.common.features.processor")
    }
    api("io.helidon.config:helidon-config-metadata-processor:$helidon") {
        because("io.helidon.config.metadata.processor")
    }
    api("io.helidon.codegen:helidon-codegen-apt:$helidon") { because("io.helidon.codegen.apt") }
    api("io.helidon.builder:helidon-builder-codegen:$helidon") {
        because("io.helidon.builder.codegen")
    }

    // Code generation
    api("org.antlr:antlr4:$antlr")
    api("com.google.protobuf:protoc:$protobuf")
    api("io.grpc:protoc-gen-grpc-java:1.66.0")

    // Testing only
    api("com.google.guava:guava:33.3.1-jre") { because("com.google.common") }
    api("com.google.protobuf:protobuf-java:$protobuf") { because("com.google.protobuf") }
    api("com.google.protobuf:protobuf-java-util:$protobuf") { because("com.google.protobuf.util") }
    api("org.assertj:assertj-core:3.27.2") { because("org.assertj.core") }
    api("org.junit.jupiter:junit-jupiter-api:$junit5") { because("org.junit.jupiter.api") }
    api("org.junit.jupiter:junit-jupiter-engine:$junit5") { because("org.junit.jupiter.engine") }

    api("org.mockito:mockito-core:$mockito") { because("org.mockito") }
    api("org.mockito:mockito-junit-jupiter:$mockito") { because("org.mockito.junit.jupiter") }
    api("io.grpc:grpc-netty:$grpc") { because("io.grpc.netty") }
    api("io.grpc:grpc-protobuf:$grpc") { because("io.grpc.protobuf") }
    api("io.grpc:grpc-stub:$grpc") { because("io.grpc.stub") }

    api("io.helidon.webserver.observe:helidon-webserver-observe-metrics:$helidon") {
        because("io.helidon.webserver.observe.metrics")
    }
    api("io.helidon.webclient:helidon-webclient:$helidon") { because("io.helidon.webclient") }
    api("io.helidon.webclient:helidon-webclient-http2:$helidon") {
        because("io.helidon.webclient.http2")
    }
}
