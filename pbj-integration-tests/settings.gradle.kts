// SPDX-License-Identifier: Apache-2.0
import me.champeau.gradle.igp.gitRepositories

pluginManagement {
    repositories.gradlePluginPortal()
    // To use locally built 'pbj-core' (Gradle plugin)
    includeBuild("../pbj-core")
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") repositories.mavenCentral()
    // To use locally built 'pbj-runtime'
    includeBuild("../pbj-core")
}

// Use GIT plugin to clone HAPI protobuf files for testing
// See documentation https://melix.github.io/includegit-gradle-plugin/latest/index.html

plugins {
    id("com.gradle.enterprise").version("3.15.1")
    id("me.champeau.includegit").version("0.1.5")
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

gitRepositories {
    checkoutsDirectory.set(file("./build/repos"))
    include("hapi") {
        uri.set("https://github.com/hashgraph/hedera-protobufs.git")
        // choose tag or branch of HAPI you would like to test with
        tag.set("main")
        // do not load project from repo
        autoInclude.set(false)
    }
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
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
