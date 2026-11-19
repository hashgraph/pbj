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
     * Applies only to roots with more than 8 mask entries; an explicit {@code pbj.copy_builder_tracking} comment
     * overrides this. Tracking only accelerates diffs, so disabling it saves the per-instance cost without changing
     * protobuf encodings, value semantics, or diff results.
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

    /**
     * Project default for {@code pbj.fast_parse}: generate a byte-array parser with in-order fast paths, used
     * automatically for array-backed input. Unset by default (off). A file or message comment overrides it.
     * @return the fast parse property
     */
    Property<Boolean> getFastParse();

    /**
     * Project default for {@code pbj.fast_write}: generate a back-to-front writer into a reusable buffer. Unset by
     * default (off). A file or message comment overrides it.
     * @return the fast write property
     */
    Property<Boolean> getFastWrite();

    /**
     * Project default for {@code pbj.views}: generate zero-copy views over encoded bytes. Unset by default (off). A
     * file or message comment overrides it.
     * @return the views property
     */
    Property<Boolean> getGenerateViews();

    /**
     * Project default for {@code pbj.oneof_storage}: {@code "boxed"} (a {@code OneOf} object per oneof field) or
     * {@code "unboxed"} (a kind field and a value field). Unset by default ({@code "boxed"}). A file or message comment
     * overrides it.
     * @return the oneof storage property
     */
    Property<String> getOneofStorage();

    /**
     * Project default for {@code pbj.writers}: generate zero-allocation message writers ({@code Xxx.Writer}) for every
     * message, and so for every message reachable from one. Unset by default (off), and not turned on by
     * {@code performance = "max"}. A file or message comment overrides it.
     * @return the writers property
     */
    Property<Boolean> getGenerateWriters();

    /**
     * Project default for {@code pbj.performance}, shorthand for the four options above other than writers:
     * {@code "max"} turns them all on, {@code "default"} leaves them at their defaults. Unset by default. The specific
     * properties above win over it.
     * @return the performance property
     */
    Property<String> getPerformance();
}
