// SPDX-License-Identifier: Apache-2.0
plugins { id("com.hedera.pbj.helidon") }

mainModuleInfo {
    annotationProcessor("io.helidon.common.features.processor")
    annotationProcessor("io.helidon.config.metadata.processor")
    annotationProcessor("io.helidon.codegen.apt")
    annotationProcessor("io.helidon.builder.codegen")
}

tasks.named("javadoc").configure { enabled = false }

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom { description.set("Configuration for a Helidon gRPC plugin for PBJ") }
    }
}
