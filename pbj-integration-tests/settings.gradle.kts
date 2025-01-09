/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

pluginManagement {
    includeBuild("../pbj-core") // use locally built 'pbj-core' (Gradle plugin)
}

buildscript {
    // TODO downgrading to avoid error: project :pbj-core:pbj-compiler not found.
    //      @jjohannes needs to investigate the issue
    dependencies.constraints {
        classpath("com.autonomousapps:dependency-analysis-gradle-plugin:2.1.0!!")
    }
}

plugins { id("org.hiero.gradle.build") version "0.2.0" }

dependencyResolutionManagement {
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}
