// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

/** A Gradle extension to pass parameters to the PbjCompilerPlugin. */
public interface PbjExtension {
    /**
     * An optional suffix to append to Java package names of PBJ-generated classes
     * when the Protobuf model is missing an explicit `pbj.java_package` option and PBJ has to
     * derive the Java package name from the standard `java_package` option or otherwise.
     * @return the suffix property
     */
    Property<String> getJavaPackageSuffix();

    /**
     * An optional boolean that indicates if test classes for protobuf models should be generated,
     * which is true by default.
     * @return true if tests should be generated
     */
    Property<Boolean> getGenerateTestClasses();

    /**
     * Generate transient copy-builder origin and candidate-field tracking for selected diff roots, which is false by default.
     * Tracking only accelerates diffs, so disabling it saves the per-instance cost without changing protobuf
     * encodings, value semantics, or diff results.
     * @return the tracking generation property
     */
    Property<Boolean> getGenerateCopyBuilderTracking();
    /** Fully qualified protobuf message names to generate diff APIs for. Empty by default. */
    SetProperty<String> getDiffMessages();

    /**
     * Fully qualified protobuf catalog messages whose direct message-valued fields select diff roots.
     * The catalogs themselves are not selected. Empty by default.
     */
    SetProperty<String> getDiffMessageContainers();
}
