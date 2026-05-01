// SPDX-License-Identifier: Apache-2.0
import org.gradlex.javamodule.moduleinfo.ExtraJavaModuleInfoPluginExtension
import org.gradlex.jvm.dependency.conflict.resolution.JvmDependencyConflictsExtension

plugins { id("org.hiero.gradle.build") version "0.7.6" }

javaModules {
    directory(".") {
        group = "com.hedera.pbj"
        module("pbj-compiler") // no 'module-info.java'
    }
}

// The patch rules below can be removed once "org.hiero.gradle.build" contains the following update:
// https://github.com/hiero-ledger/hiero-gradle-conventions/issues/444
@Suppress("UnstableApiUsage")
gradle.lifecycle.beforeProject {
    plugins.withId("org.hiero.gradle.base.jpms-modules") {
        configure<JvmDependencyConflictsExtension> {
            patch.module("io.prometheus:simpleclient") {
                addRuntimeOnlyDependency("io.prometheus:simpleclient_tracer_common")
            }
        }
        configure<ExtraJavaModuleInfoPluginExtension> {
            module("io.micrometer:micrometer-registry-otlp", "micrometer.registry.otlp")
            module("io.opentelemetry.proto:opentelemetry-proto", "io.opentelemetry.proto")
        }
    }
}
