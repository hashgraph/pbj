package com.hedera.hashgraph.pbj.compiler;

import org.gradle.api.file.SourceDirectorySet;

public interface PbjSourceDirectorySet extends SourceDirectorySet {

    /**
     * Name of the source set extension contributed by the antlr plugin.
     *
     * @since 8.0
     */
    String NAME = "pbj";
}
