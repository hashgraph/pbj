// SPDX-License-Identifier: Apache-2.0
plugins { id("com.hedera.pbj.runtime") }

testModuleInfo {
    requires("com.google.protobuf")
    requires("org.assertj.core")
    requires("net.bytebuddy")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
    requiresStatic("com.github.spotbugs.annotations")
}
