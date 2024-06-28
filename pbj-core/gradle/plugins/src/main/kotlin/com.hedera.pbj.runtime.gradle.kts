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
    id("com.hedera.pbj.protoc") // protobuf plugin is only used for tests
    id("me.champeau.jmh")
}

tasks.generateGrammarSource {
    arguments = arguments + listOf("-package", "com.hedera.pbj.runtime.jsonparser")
}

val maven = publishing.publications.create<MavenPublication>("maven") { from(components["java"]) }

signing.sign(maven)

// Filter JMH benchmarks for testing
//jmh {
//    includes.add("WriteBytesBench")
//    includes.add("WriteBufferedDataBench")
//}
