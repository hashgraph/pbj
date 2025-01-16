// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.build") version "0.3.0" }

javaModules {
    directory(".") {
        group = "com.hedera.pbj"
        module("pbj-compiler") // no 'module-info.java'
    }
}
