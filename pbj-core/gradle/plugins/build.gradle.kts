// SPDX-License-Identifier: Apache-2.0
plugins { `kotlin-dsl` }

repositories { gradlePluginPortal() }

dependencies {
    implementation("com.adarshr:gradle-test-logger-plugin:4.0.0")
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:1.29.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("com.gradle.publish:plugin-publish-plugin:1.2.1")
    implementation("gradle.plugin.lazy.zoo.gradle:git-data-plugin:1.2.2")
    implementation("io.github.gradle-nexus:publish-plugin:1.3.0")
    implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.2")
    implementation("net.swiftzer.semver:semver:1.3.0")
    implementation("org.gradlex:java-module-dependencies:1.6.6")
}
