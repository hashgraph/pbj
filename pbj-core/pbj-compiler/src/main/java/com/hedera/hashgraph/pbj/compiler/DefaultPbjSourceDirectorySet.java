package com.hedera.hashgraph.pbj.compiler;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;

import javax.inject.Inject;

public abstract class DefaultPbjSourceDirectorySet extends DefaultSourceDirectorySet implements PbjSourceDirectorySet {

    @Inject
    public DefaultPbjSourceDirectorySet(SourceDirectorySet sourceDirectorySet) {
        super(sourceDirectorySet);
    }
}
