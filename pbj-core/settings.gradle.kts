/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

plugins {
    id("com.gradle.enterprise").version("3.11.4")
}

// Include sub modules
include(":pbj-runtime")
include(":pbj-compiler")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
        // distribution. These libs can be depended on during compilation, or bundled as part of runtime.
        create("libs") {
            // Define the approved version numbers
            version("antlr-version", "4.11.1")
            version("jetbrains-annotations-version", "23.0.0")
            version("spotbugs-version", "4.7.3")

            // List of bundles provided for us. When applicable, favor using these over individual libraries.
            bundle("antlr", listOf("antlr"))
            bundle("jetbrains", listOf("jetbrains-annotations"))

            // Define the individual libraries
            library("antlr", "org.antlr", "antlr4").versionRef("antlr-version")
            library("jetbrains-annotations", "org.jetbrains", "annotations").versionRef("jetbrains-annotations-version")
            library("spotbugs-annotations", "com.github.spotbugs", "spotbugs-annotations").versionRef("spotbugs-version")
        }

        create("testLibs") {
            // Define the approved version numbers
            version("junit-version", "5.9.0")
            version("protobuf-version", "3.21.9")

            // List of bundles provided for us. When applicable, favor using these over individual libraries.
            bundle("junit", listOf("junit-jupiter", "junit-jupiter-api", "junit-jupiter-params"))
            bundle("protobuf", listOf("protobuf-java"))

            // Define the individual libraries
            // JUnit Bundle
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit-version")
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit-version")
            library("junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit-version")
            library("junit-jupiter-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef("junit-version")

            // Protobuf Bundle
            library("protobuf-java", "com.google.protobuf", "protobuf-java").versionRef("protobuf-version")
        }
    }
}
