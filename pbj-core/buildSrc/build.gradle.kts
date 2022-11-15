
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.11.0")
    implementation("net.swiftzer.semver:semver:1.1.2")
    implementation("com.adarshr:gradle-test-logger-plugin:3.2.0")
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}
