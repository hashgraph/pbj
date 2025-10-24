// SPDX-License-Identifier: Apache-2.0
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots")
        includeBuild("../pbj-core") // use locally built 'pbj-core' (Gradle plugin)
    }
}

buildscript {
    configurations.classpath { resolutionStrategy.cacheDynamicVersionsFor(0, "seconds") }
}

plugins { id("org.hiero.gradle.build") version "0.6.0-SNAPSHOT" }

dependencyResolutionManagement {
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}
