rootProject.name = "pbs-integration-tests"

// Add local maven build directory to plugin repos
pluginManagement {
    repositories {
        maven {
            url = file("../build/testRepo").toURI()
            name = "test"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
