// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.library") }

description = "A library with common code used by both PBJ gRPC client and server"

testModuleInfo {
    requires("org.junit.jupiter.api")
    requiresStatic("java.annotation")
}
