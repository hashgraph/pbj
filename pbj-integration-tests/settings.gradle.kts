// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    includeBuild("../pbj-core") // use locally built 'pbj-core' (Gradle plugin)
}

plugins { id("org.hiero.gradle.build") version "0.3.9" }

// Downgrade 'dependency-analysis-gradle-plugin' due to regression in 2.7.0
// https://github.com/autonomousapps/dependency-analysis-gradle-plugin/issues/1364
buildscript {
    dependencies.constraints {
        classpath("com.autonomousapps:dependency-analysis-gradle-plugin:2.6.0!!")
    }
}

dependencyResolutionManagement {
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}
