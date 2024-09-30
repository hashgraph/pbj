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

pluginManagement { includeBuild("gradle/plugins") }

plugins {
    id("com.gradle.enterprise").version("3.15.1")
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

include(":pbj-runtime")
include(":pbj-compiler")
include(":pbj-grpc-helidon-config")
include(":pbj-grpc-helidon")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

var helidonVersion = "4.1.1"
var grpcVersion = "1.65.1"
var protobufVersion = "4.28.2"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
            // distribution. These libs can be depended on during compilation, or bundled as part of runtime.
            version("org.antlr.antlr4.runtime", "4.11.1")
            version("com.github.spotbugs.annotations", "4.8.6")

            // The libs of this catalog are used to compile but are not bundled. The user will select helidon
            // which will have the set of these dependencies that are required.
            version("io.helidon.webserver", helidonVersion)
            version("io.helidon.webserver.http2", helidonVersion)
            version("io.helidon.metrics.api", helidonVersion)
            version("io.helidon.common.features.api", helidonVersion)
            version("io.helidon.common.features.processor", helidonVersion) // annotation processing
            version("io.helidon.config.metadata.processor", helidonVersion) // annotation processing
            version("io.helidon.codegen.apt", helidonVersion) // annotation processing
            version("io.helidon.builder.codegen", helidonVersion) // annotation processing

            // Testing only versions
            version("com.google.protobuf", protobufVersion)
            version("com.google.protobuf.util", protobufVersion)
            version("org.assertj.core", "3.23.1")
            version("org.junit.jupiter.api", "5.8.2")
            version("org.mockito", "4.6.1")
            version("org.mockito.inline", "4.6.1")
            version("org.mockito.junit.jupiter", "5.10.0")
            version("io.grpc.netty", grpcVersion)
            version("io.grpc.protobuf", grpcVersion)
            version("io.grpc.stub", grpcVersion)
            version("java.annotation", "1.3.2")
            version("io.helidon.webclient", helidonVersion)
            version("io.helidon.webclient.http2", helidonVersion)
        }
    }
}

// Build cache configuration
val isCiServer = System.getenv().containsKey("CI")
val gradleCacheUsername: String? = System.getenv("GRADLE_CACHE_USERNAME")
val gradleCachePassword: String? = System.getenv("GRADLE_CACHE_PASSWORD")
val gradleCacheAuthorized =
    (gradleCacheUsername?.isNotEmpty() ?: false) && (gradleCachePassword?.isNotEmpty() ?: false)

buildCache {
    remote<HttpBuildCache> {
        url = uri("https://cache.gradle.hedera.svcs.eng.swirldslabs.io/cache/")
        isPush = isCiServer && gradleCacheAuthorized

        isUseExpectContinue = true
        isEnabled = !gradle.startParameter.isOffline

        if (isCiServer && gradleCacheAuthorized) {
            credentials {
                username = gradleCacheUsername
                password = gradleCachePassword
            }
        }
    }
}
