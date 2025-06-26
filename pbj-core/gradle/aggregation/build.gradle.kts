// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.feature.publish-maven-central-aggregation")
    id("org.hiero.gradle.report.code-coverage")
    id("org.hiero.gradle.check.spotless")
    id("org.hiero.gradle.check.spotless-kotlin")
}

dependencies {
    published(project(":pbj-grpc-helidon"))
    published(project(":pbj-grpc-client-helidon"))
}
