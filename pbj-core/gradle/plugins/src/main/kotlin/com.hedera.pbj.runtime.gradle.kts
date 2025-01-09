// SPDX-License-Identifier: Apache-2.0
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
