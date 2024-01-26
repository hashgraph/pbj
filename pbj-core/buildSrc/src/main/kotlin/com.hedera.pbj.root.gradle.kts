/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
}

tasks.register("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.closeAndReleaseStagingRepository)
}

tasks.register("releaseMavenCentralSnapshot") {
    group = "release"
    dependsOn(":pbj-runtime:publishToSonatype")
}
