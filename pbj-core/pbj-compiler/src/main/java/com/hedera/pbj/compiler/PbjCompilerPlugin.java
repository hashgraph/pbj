// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler;

import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
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

        // get reference to java plugin
        final var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);

        // register transform to extract 'proto' files from 'jar' files where needed
        final LibraryElements jarElements = project.getObjects().named(LibraryElements.class, LibraryElements.JAR);
        final LibraryElements protobufsElements = project.getObjects().named(LibraryElements.class, "protobufs");
        project.getDependencies().registerTransform(PbjProtobufExtractTransform.class, a -> {
            a.getFrom().attribute(LIBRARY_ELEMENTS_ATTRIBUTE, jarElements);
            a.getTo().attribute(LIBRARY_ELEMENTS_ATTRIBUTE, protobufsElements);
        });

        javaPlugin.getSourceSets().configureEach(sourceSet -> {
            // 'test' is special as it will contain tests generated from the sources in the 'main' source set.
            if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                final var generatePbjSource = project.getTasks().named("generatePbjSource", PbjCompilerTask.class);
                generatePbjSource.configure(t -> t.getGenerateTestClasses().set(pbj.getGenerateTestClasses()));
                sourceSet.getJava().srcDir(generatePbjSource.flatMap(PbjCompilerTask::getJavaTestOutputDirectory));
            } else {
                pbjSourceSet(sourceSet, protobufsElements, project);
            }
        });
    }

    private static void pbjSourceSet(SourceSet sourceSet, LibraryElements protobufsElements, Project project) {
        final PbjExtension pbj = project.getExtensions().getByType(PbjExtension.class);
        final ConfigurationContainer configurations = project.getConfigurations();
        final String outputDirectory = "generated/source/pbj-proto/";

        // If not main source set, this is an unused empty folder
        final String testFolderName =
                SourceSet.MAIN_SOURCE_SET_NAME.equals(sourceSet.getName()) ? "test" : sourceSet.getName() + "Test";

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

        // Create a view on the compile classpath the only contains the extracted protobuf files
        final FileCollection extractedCompileClasspath = configurations
                .getByName(sourceSet.getCompileClasspathConfigurationName())
                .getIncoming()
                .artifactView(v -> v.getAttributes().attribute(LIBRARY_ELEMENTS_ATTRIBUTE, protobufsElements))
                .getFiles();

        TaskProvider<PbjCompilerTask> pbjCompiler = project.getTasks()
                .register(taskName, PbjCompilerTask.class, pbjTask -> {
                    pbjTask.setDescription("Processes the " + sourceSet.getName() + " Pbj grammars.");
                    // 4) set up convention mapping for default sources (allows user
                    // to not have to specify)
                    pbjTask.setSource(pbjSourceSet);
                    pbjTask.getClasspath().from(extractedCompileClasspath);
                    pbjTask.getJavaMainOutputDirectory().set(outputDirectoryMain);
                    pbjTask.getJavaTestOutputDirectory().set(outputDirectoryTest);
                    pbjTask.getJavaPackageSuffix().set(pbj.getJavaPackageSuffix());
                    pbjTask.getGenerateTestClasses().set(false);
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
