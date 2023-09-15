/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle Plugin for generating java records model objects, parsers, writers and unit tests from
 * .proto files.
 */
@SuppressWarnings("unused")
public abstract class PbjCompilerPlugin implements Plugin<Project> {
    /** object factory for building objects needed */
    @Inject
    protected abstract ObjectFactory getObjectFactory();

    /**
     * Apply plugin to project, called during configuration phase
     *
     * @param project The project to apply to
     */
    @Override
    public void apply(Project project) {
        // get reference to java plugin
        final var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        // get java src sets
        final var mainSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final var testSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        final String outputDirectory = "generated/source/pbj-proto/main/";
        final Provider<Directory> outputDirectoryMain =
                project.getLayout().getBuildDirectory().dir(outputDirectory + "java");
        final Provider<Directory> outputDirectoryTest =
                project.getLayout().getBuildDirectory().dir(outputDirectory + "test");

        // for the 'main' source set we:
        // 1) Add a new 'pbj' virtual directory mapping
        PbjSourceDirectorySet pbjSourceSet =
                createPbjSourceDirectorySet(
                        ((DefaultSourceSet) mainSrcSet).getDisplayName(), getObjectFactory());
        mainSrcSet
                .getExtensions()
                .add(PbjSourceDirectorySet.class, PbjSourceDirectorySet.NAME, pbjSourceSet);
        pbjSourceSet.getFilter().include("**/*.proto");
        pbjSourceSet.srcDir("src/" + mainSrcSet.getName() + "/proto");
        mainSrcSet.getAllSource().source(pbjSourceSet);

        // 2) create an PbjTask for this sourceSet following the gradle
        //    naming conventions via call to sourceSet.getTaskName()
        final String taskName = mainSrcSet.getTaskName("generate", "PbjSource");

        TaskProvider<PbjCompilerTask> pbjCompiler =
                project.getTasks()
                        .register(
                                taskName,
                                PbjCompilerTask.class,
                                pbjTask -> {
                                    pbjTask.setDescription(
                                            "Processes the "
                                                    + mainSrcSet.getName()
                                                    + " Pbj grammars.");
                                    // 4) set up convention mapping for default sources (allows user
                                    // to not have to specify)
                                    pbjTask.setSource(pbjSourceSet);
                                    pbjTask.getJavaMainOutputDirectory().set(outputDirectoryMain);
                                    pbjTask.getJavaTestOutputDirectory().set(outputDirectoryTest);
                                });

        // 5) register fact that pbj should be run before compiling  by informing the 'java' part
        //    of the source set that it contains code produced by the pbj compiler
        mainSrcSet
                .getJava()
                .srcDir(pbjCompiler.flatMap(PbjCompilerTask::getJavaMainOutputDirectory));
        testSrcSet
                .getJava()
                .srcDir(pbjCompiler.flatMap(PbjCompilerTask::getJavaTestOutputDirectory));
    }

    /**
     * Create a PBJ source set
     *
     * @param parentDisplayName The parent display name
     * @param objectFactory object factory for building objects needed
     * @return new PBJ source directory set
     */
    private static PbjSourceDirectorySet createPbjSourceDirectorySet(
            String parentDisplayName, ObjectFactory objectFactory) {
        String name = parentDisplayName + ".pbj";
        String displayName = parentDisplayName + " Pbj source";
        PbjSourceDirectorySet pbjSourceSet =
                objectFactory.newInstance(
                        DefaultPbjSourceDirectorySet.class,
                        objectFactory.sourceDirectorySet(name, displayName));
        pbjSourceSet.getFilter().include("**/*.proto");
        return pbjSourceSet;
    }
}
