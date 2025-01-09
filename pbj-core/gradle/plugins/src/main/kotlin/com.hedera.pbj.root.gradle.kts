// SPDX-License-Identifier: Apache-2.0
import io.github.gradlenexus.publishplugin.CloseNexusStagingRepository

plugins {
    id("com.hedera.pbj.repositories")
    id("com.hedera.pbj.aggregate-reports")
    id("com.hedera.pbj.spotless-conventions")
    id("com.hedera.pbj.spotless-kotlin-conventions")
    id("com.autonomousapps.dependency-analysis")
    id("io.github.gradle-nexus.publish-plugin")
}

group = "com.hedera.pbj"

spotless { kotlinGradle { target("buildSrc/**/*.gradle.kts") } }

nexusPublishing {
    repositories {
        sonatype {
            username = System.getenv("OSSRH_USERNAME")
            password = System.getenv("OSSRH_PASSWORD")
        }
    }
}

tasks.withType<CloseNexusStagingRepository> {
    // The publishing of all components to Maven Central (in this case only 'pbj-runtime') is
    // automatically done before close (which is done before release).
    dependsOn(":pbj-runtime:publishToSonatype")
    dependsOn(":pbj-grpc-helidon:publishToSonatype")
    dependsOn(":pbj-grpc-helidon-config:publishToSonatype")
}

tasks.register("release") {
    group = "release"
    dependsOn(tasks.closeAndReleaseStagingRepository)
}

tasks.register("releaseSnapshot") {
    group = "release"
    dependsOn(":pbj-runtime:publishToSonatype")
    dependsOn(":pbj-grpc-helidon:publishToSonatype")
    dependsOn(":pbj-grpc-helidon-config:publishToSonatype")
}
