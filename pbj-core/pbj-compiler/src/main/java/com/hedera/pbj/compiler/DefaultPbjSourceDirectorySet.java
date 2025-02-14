// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import javax.inject.Inject;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;

/** Source directory set for PBJ, a directory full of .proto source files */
public abstract class DefaultPbjSourceDirectorySet extends DefaultSourceDirectorySet implements PbjSourceDirectorySet {

    @Inject
    public DefaultPbjSourceDirectorySet(SourceDirectorySet sourceSet, TaskDependencyFactory taskDependencyFactory) {
        super(sourceSet, taskDependencyFactory);
    }
}
