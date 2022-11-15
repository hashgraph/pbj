import me.champeau.gradle.igp.gitRepositories

// Add local maven build directory to plugin repos
pluginManagement {
    repositories {
        maven {
            url = file("../build/testRepo").toURI()
            name = "test"
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }

}

// Use GIT plugin to clone HAPI protobuf files for testing
// See documentation https://melix.github.io/includegit-gradle-plugin/latest/index.html

plugins {
    id("com.gradle.enterprise").version("3.11.4")
    id("me.champeau.includegit").version("0.1.5")
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

