// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.module.library")
    id("org.hiero.gradle.feature.protobuf") // protobuf plugin is only used for tests
}

description = "A PBJ gRPC client with Helidon HTTP2 webclient"

testModuleInfo {
    requires("org.junit.jupiter.api")
    requiresStatic("java.annotation")
}
