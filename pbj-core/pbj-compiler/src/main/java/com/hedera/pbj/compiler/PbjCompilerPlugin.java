// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

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

    /**
     * Apply plugin to project, called during configuration phase
     *
     * @param project The project to apply to
     */
    @Override
    public void apply(Project project) {
        // Register the PbjExtension to fetch optional parameters from Gradle files
        final PbjExtension pbj = project.getExtensions().create("pbj", PbjExtension.class);
        // By default, test classes for 'main' are generated
        pbj.getGenerateTestClasses().convention(true);

        // get reference to java plugin
        final var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

        javaPlugin.getSourceSets().configureEach(sourceSet -> {
            // Treat the combination of main/test source sets special, as for protobufs in 'main', we support
            // generating tests into 'test'.
            if (SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                // In case of the main source set, test sources go into "test" (if activated)
                pbjSourceSet(sourceSet, project, SourceSet.TEST_SOURCE_SET_NAME);
            } else if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                // 'test' is special as it will contain tests generated from the sources in the 'main' source set.
                // We further configure the task generating code for 'main' to activate test generation if configured
                // via pbj { generateTestClasses = true/false }.
                final var generatePbjSource = project.getTasks().named("generatePbjSource", PbjCompilerTask.class);
                generatePbjSource.configure(t -> t.getGenerateTestClasses().set(pbj.getGenerateTestClasses()));
                sourceSet.getJava().srcDir(generatePbjSource.flatMap(PbjCompilerTask::getJavaTestOutputDirectory));
            } else {
                // We have to define a test folder, although it will be empty. This is because the PbjCompilerTask
                // has the 'javaTestOutputDirectory' @OutputDirectory and thus Gradle's output tracking always needs
                // a directory here.
                pbjSourceSet(sourceSet, project, sourceSet.getName() + "Test");
            }
        });
    }

    private static void pbjSourceSet(SourceSet sourceSet, Project project, String testFolderName) {
        final PbjExtension pbj = project.getExtensions().getByType(PbjExtension.class);
        final String outputDirectory = "generated/source/pbj-proto/";

        final Provider<Directory> outputDirectoryMain =
                project.getLayout().getBuildDirectory().dir(outputDirectory + sourceSet.getName() + "/java");
        final Provider<Directory> outputDirectoryTest =
                project.getLayout().getBuildDirectory().dir(outputDirectory + testFolderName + "/java");

        // for the given source set we:
        // 1) Add a new 'pbj' virtual directory mapping
        PbjSourceDirectorySet pbjSourceSet =
                createPbjSourceDirectorySet(((DefaultSourceSet) sourceSet).getDisplayName(), project.getObjects());
        sourceSet.getExtensions().add(PbjSourceDirectorySet.class, PbjSourceDirectorySet.NAME, pbjSourceSet);
        pbjSourceSet.getFilter().include("**/*.proto");
        pbjSourceSet.srcDir("src/" + sourceSet.getName() + "/proto");
        sourceSet.getAllSource().source(pbjSourceSet);

        // 2) create an PbjTask for this sourceSet following the gradle
        //    naming conventions via call to sourceSet.getTaskName()
        final String taskName = sourceSet.getTaskName("generate", "PbjSource");

        TaskProvider<PbjCompilerTask> pbjCompiler = project.getTasks()
                .register(taskName, PbjCompilerTask.class, pbjTask -> {
                    pbjTask.setDescription("Processes the " + sourceSet.getName() + " Pbj grammars.");
                    // 4) set up convention mapping for default sources (allows user
                    // to not have to specify)
                    pbjTask.setSource(pbjSourceSet);
                    pbjTask.getJavaMainOutputDirectory().set(outputDirectoryMain);
                    pbjTask.getJavaTestOutputDirectory().set(outputDirectoryTest);
                    pbjTask.getJavaPackageSuffix().set(pbj.getJavaPackageSuffix());
                });

        // 5) register fact that pbj should be run before compiling  by informing the 'java' part
        //    of the source set that it contains code produced by the pbj compiler
        sourceSet.getJava().srcDir(pbjCompiler.flatMap(PbjCompilerTask::getJavaMainOutputDirectory));
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
        PbjSourceDirectorySet pbjSourceSet = objectFactory.newInstance(
                DefaultPbjSourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName));
        pbjSourceSet.getFilter().include("**/*.proto");
        return pbjSourceSet;
    }
}
