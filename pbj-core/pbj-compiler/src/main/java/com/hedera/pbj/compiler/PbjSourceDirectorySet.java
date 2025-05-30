// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import org.gradle.api.file.SourceDirectorySet;

/** Set of source directories as input to PBJ full of .proto files */
public interface PbjSourceDirectorySet extends SourceDirectorySet {

    /** Name of the source set extension contributed by the PBJ plugin. */
    String NAME = "pbj";
}
