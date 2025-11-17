// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    includeBuild("../pbj-core") // use locally built 'pbj-core' (Gradle plugin)
}

plugins { id("org.hiero.gradle.build") version "0.6.3" }

dependencyResolutionManagement {
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}
