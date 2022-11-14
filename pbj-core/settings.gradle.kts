
plugins {
    id("com.gradle.enterprise").version("3.11.4")
}

// Include sub modules
include(":pbj-runtime")
include(":pbj-compiler")

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
