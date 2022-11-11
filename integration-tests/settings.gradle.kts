import me.champeau.gradle.igp.gitRepositories

rootProject.name = "pbs-integration-tests"

// Add local maven build directory to plugin repos
pluginManagement {
    repositories {
        maven {
            url = file("../build/testRepo").toURI()
            name = "test"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

// Use GIT plugin to clone HAPI protobuf files for testing
// See documentation https://melix.github.io/includegit-gradle-plugin/latest/index.html

plugins {
    id("me.champeau.includegit") version "0.1.5"
}

gitRepositories {
    checkoutsDirectory.set(file("./build/repos"))
    include("hapi") {
        uri.set("https://github.com/hashgraph/hedera-protobufs.git")
        // optional, set what branch to use
        branch.set("main")
        // you can also use a tag
//        tag.set("v1.0")
    }
}
