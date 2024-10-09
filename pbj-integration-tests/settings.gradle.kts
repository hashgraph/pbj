// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    // To use locally built 'pbj-core' (Gradle plugin)
    includeBuild("../pbj-core")
}

buildscript {
    // TODO downgrading to avoid error: project :pbj-core:pbj-compiler not found.
    //      @jjohannes needs to investigate the issue
    dependencies.constraints {
        classpath("com.autonomousapps:dependency-analysis-gradle-plugin:2.1.0!!")
    }
}

plugins { id("org.hiero.gradle.build") version "0.1.2" }

dependencyResolutionManagement {
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}
