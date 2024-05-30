/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    implementation("org.gradlex:java-module-dependencies:1.5.2")
}
