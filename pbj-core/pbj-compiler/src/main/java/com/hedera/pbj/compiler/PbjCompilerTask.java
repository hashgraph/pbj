// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import javax.inject.Inject;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
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
     * The classpath to import 'proto' files from dependencies. The task expects the proto files to be extracted
     * from the Jar files. It is also expected that the classpath already contains the generated code for these
     * files.
     *
     * @return The classpath to find imports in other libraries.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * @return Gradle's FileOperations service to use for file deletion
     */
    @Inject
    protected abstract FileOperations getFileOperations();

    /** An optional Java package suffix for PBJ-generated classes when `pbj.java_package` is missing. */
    @Optional
    @Input
    public abstract Property<String> getJavaPackageSuffix();

    /** An optional boolean that indicates if test classes for protobuf models should be generated, which is true by default. */
    @Optional
    @Input
    public abstract Property<Boolean> getGenerateTestClasses();

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
                getClasspath().getAsFileTree(),
                getJavaMainOutputDirectory().get().getAsFile(),
                getJavaTestOutputDirectory().get().getAsFile(),
                getJavaPackageSuffix().getOrNull(),
                getGenerateTestClasses().getOrElse(Boolean.FALSE));
    }
}
