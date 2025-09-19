// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.base.lifecycle")
    id("org.hiero.gradle.base.jpms-modules")
    id("org.hiero.gradle.check.spotless")
    id("org.hiero.gradle.check.spotless-kotlin")
}

group = "com.hedera.hashgraph"

val antlr = "4.13.2"
val grpc = "1.75.0"
val helidon = "4.2.7"
val protobuf = "4.31.1"

val junit5 = "5.13.4"
val mockito = "5.19.0"

dependencies { api(platform("io.netty:netty-bom:4.2.2.Final")) }

dependencies.constraints {
    api("org.antlr:antlr4-runtime:$antlr") { because("org.antlr.antlr4.runtime") }
    api("com.github.spotbugs:spotbugs-annotations:4.9.3") {
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
    api("io.helidon.config.metadata:helidon-config-metadata-codegen:$helidon") {
        because("io.helidon.config.metadata.codegen")
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
    api("io.grpc:protoc-gen-grpc-java:1.72.0")

    // Testing only
    api("com.google.guava:guava:33.4.8-jre") { because("com.google.common") }
    api("com.google.protobuf:protobuf-java:$protobuf") { because("com.google.protobuf") }
    api("com.google.protobuf:protobuf-java-util:$protobuf") { because("com.google.protobuf.util") }
    api("net.bytebuddy:byte-buddy:1.17.6") { because("net.bytebuddy") }
    api("org.assertj:assertj-core:3.27.3") { because("org.assertj.core") }
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
