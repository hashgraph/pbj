package com.hedera.pbj.compiler;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;

import javax.inject.Inject;

/**
 * Source directory set for PBJ, a directory full of .proto source files
 */
public abstract class DefaultPbjSourceDirectorySet extends DefaultSourceDirectorySet implements PbjSourceDirectorySet {

    /**
     * Create a DefaultPbjSourceDirectorySet
     *
     * @param sourceDirectorySet the source directories for this set
     */
    @Inject
    public DefaultPbjSourceDirectorySet(SourceDirectorySet sourceDirectorySet) {
        super(sourceDirectorySet);
    }
}
