/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import io.github.gradlenexus.publishplugin.ReleaseNexusStagingRepository

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

tasks.withType<CloseNexusStagingRepository>().configureEach {
    mustRunAfter(tasks.withType<PublishToMavenRepository>())
}

tasks.withType<ReleaseNexusStagingRepository>().configureEach {
    mustRunAfter(tasks.withType<PublishToMavenRepository>())
}
