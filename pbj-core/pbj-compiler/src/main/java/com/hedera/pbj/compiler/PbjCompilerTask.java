/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.compiler;

import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
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
                getJavaTestOutputDirectory().get().getAsFile());
    }
}
