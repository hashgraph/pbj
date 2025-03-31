// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import org.gradle.api.provider.Property;

/** A Gradle extension to pass parameters to the PbjCompilerPlugin. */
public interface PbjExtension {
    /**
     * An optional suffix to append to Java package names of PBJ-generated classes
     * when the Protobuf model is missing an explicit `pbj.java_package` option and PBJ has to
     * derive the Java package name from the standard `java_package` option or otherwise.
     * @return the suffix property
     */
    Property<String> getJavaPackageSuffix();
}
