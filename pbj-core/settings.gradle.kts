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
    id("com.gradle.enterprise").version("3.14.1")
}

include(":pbj-runtime")
include(":pbj-compiler")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
            // distribution. These libs can be depended on during compilation, or bundled as part of runtime.
            version("org.antlr.antlr4.runtime", "4.11.1")
            version("com.github.spotbugs.annotations", "4.7.3")

            // Testing only versions
            version("org.junit.jupiter.api", "5.9.0")
            version("org.junit.jupiter.params", "5.9.0")
            version("org.assertj.core", "3.23.1")
            version("org.mockito", "4.6.1")
            version("org.mockito.inline", "4.6.1")
            version("com.google.protobuf", "3.21.9")

            library("dd", "dd:xx:1.0")
        }
    }
}
