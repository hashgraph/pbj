// SPDX-License-Identifier: Apache-2.0
plugins { id("org.hiero.gradle.module.library") }

description = "Configuration for a Helidon gRPC plugin for PBJ"

// TODO fix warning 'helidon-config-metadata-processor is deprecated' and remove this
tasks.compileJava { options.compilerArgs.remove("-Werror") }

mainModuleInfo {
    annotationProcessor("io.helidon.common.features.processor")
    annotationProcessor("io.helidon.config.metadata.processor")
    annotationProcessor("io.helidon.codegen.apt")
    annotationProcessor("io.helidon.builder.codegen")
}
