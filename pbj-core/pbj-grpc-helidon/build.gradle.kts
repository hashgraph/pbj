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

plugins {
    id("java-library")
    id("com.hedera.pbj.conventions")
    id("com.google.protobuf") // protobuf plugin is only used for tests
    id("me.champeau.jmh")
}

testModuleInfo {
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("io.helidon.webclient")
    requires("io.helidon.webserver")
    requires("io.helidon.webserver.http2")
    requires("io.helidon.webclient.http2")
    requires("com.hedera.node.hapi")
    requires("com.google.protobuf.util")
    requiresStatic("com.github.spotbugs.annotations")
}

tasks.named("compileJava") {
    dependsOn(":pbj-runtime:jar")
}

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories
        artifact = "com.google.protobuf:protoc:3.21.10"
    }
}
