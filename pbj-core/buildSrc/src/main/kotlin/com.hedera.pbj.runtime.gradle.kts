/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
    id("java-library")
    id("com.hedera.pbj.conventions")
    id("com.google.protobuf") // protobuf plugin is only used for tests
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.pbj.runtime.jsonparser")
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.21.10"
    }
}

val maven = publishing.publications.create<MavenPublication>("maven") { from(components["java"]) }

signing.sign(maven)

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
        maven {
            name = "sonatypeSnapshot"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
    }
}

tasks.register("release-maven-central") {
    group = "release"
    dependsOn(
        tasks.withType<PublishToMavenRepository>().matching {
            it.name.endsWith("ToSonatypeRepository")
        }
    )
}

tasks.register("release-maven-central-snapshot") {
    group = "release"
    dependsOn(
        tasks.withType<PublishToMavenRepository>().matching {
            it.name.endsWith("ToSonatypeSnapshotRepository")
        }
    )
}
