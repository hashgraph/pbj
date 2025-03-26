// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

/** Gradle Task that generates java src code from protobuf proto schema files. */
public abstract class PbjCompilerTask extends SourceTask {

    /**
     * Set the java main directory that we write generated code into
     *
     * @return The java main directory that we write generated code into
     */
    @OutputDirectory
    public abstract DirectoryProperty getJavaMainOutputDirectory();

    /**
     * Set the java test directory that we write generated code into
     *
     * @return The java test directory that we write generated code into
     */
    @OutputDirectory
    public abstract DirectoryProperty getJavaTestOutputDirectory();

    /**
     * @return Gradle's FileOperations service to use for file deletion
     */
    @Inject
    protected abstract FileOperations getFileOperations();

    /** An optional Java package suffix for PBJ-generated classes when `pbj.java_package` is missing. */
    @Optional
    @Input
    public abstract Property<String> getJavaPackageSuffix();

    /**
     * Perform task action - Generates all the PBJ java source files
     *
     * @throws Exception If there was a problem performing action
     */
    @TaskAction
    public void perform() throws Exception {
        // Clean output directories
        getFileOperations().delete(getJavaMainOutputDirectory(), getJavaTestOutputDirectory());
        PbjCompiler.compileFilesIn(
                getSource(),
                getJavaMainOutputDirectory().get().getAsFile(),
                getJavaTestOutputDirectory().get().getAsFile(),
                getJavaPackageSuffix().getOrNull());
    }
}
