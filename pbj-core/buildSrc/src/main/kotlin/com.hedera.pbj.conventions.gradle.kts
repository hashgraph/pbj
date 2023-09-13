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
import com.adarshr.gradle.testlogger.theme.ThemeType

plugins {
    java
    id("com.hedera.pbj.repositories")
    id("com.hedera.pbj.spotless-conventions")
    id("com.hedera.pbj.spotless-java-conventions")
    id("com.hedera.pbj.spotless-kotlin-conventions")
    id("com.hedera.pbj.snykcode")    
    id("com.adarshr.test-logger")
}

group = "com.hedera.pbj"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    modularity.inferModulePath.set(true)

    // Enable JAR file generation required for publishing
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 664
    dirMode = 775
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions)
        .tags(
            "apiNote:a:API Note:",
            "implSpec:a:Implementation Requirements:",
            "implNote:a:Implementation Note:"
        )
}

testlogger {
    theme = ThemeType.MOCHA
    slowThreshold = 10000
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
}
