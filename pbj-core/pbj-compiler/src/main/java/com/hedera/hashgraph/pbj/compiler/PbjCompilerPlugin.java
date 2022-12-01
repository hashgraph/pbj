package com.hedera.hashgraph.pbj.compiler;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.io.File;

/**
 * Gradle Plugin for generating java records model objects, parsers, writers and unit tests from .proto files.
 */
@SuppressWarnings("unused")
public class PbjCompilerPlugin implements Plugin<Project> {
    private final ObjectFactory objectFactory;

    @Inject
    public PbjCompilerPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @Override
    public void apply(Project project) {
        // get reference to java plugin
        final var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        // get java src sets
        final var javaMainSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final var javaTestSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        final File outputDirectory = new File(project.getBuildDir() + "/generated/source/pbj-proto/main");
        final File outputDirectoryMain = new File(outputDirectory,"java");
        final File outputDirectoryTest = new File(outputDirectory, "test");
        javaMainSrcSet.getJava().srcDir(outputDirectoryMain);
        javaTestSrcSet.getJava().srcDir(outputDirectoryTest);

        javaPlugin.getSourceSets().all(
                sourceSet -> {
                    // for each source set we will:
                    // 1) Add a new 'pbj' virtual directory mapping
                    PbjSourceDirectorySet pbjSourceSet = createPbjSourceDirectorySet(((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
                    sourceSet.getExtensions().add(PbjSourceDirectorySet.class, PbjSourceDirectorySet.NAME, pbjSourceSet);
                    pbjSourceSet.getFilter()
                            .include("**/*.proto");
                    final String srcDir = "src/" + sourceSet.getName() + "/proto";
                    pbjSourceSet.srcDir(srcDir);
                    sourceSet.getAllSource().source(pbjSourceSet);

                    // 2) create an PbjTask for this sourceSet following the gradle
                    //    naming conventions via call to sourceSet.getTaskName()
                    final String taskName = sourceSet.getTaskName("generate", "PbjSource");

                    project.getTasks().register(taskName, PbjCompilerTask.class, pbjTask -> {
                        pbjTask.setDescription("Processes the " + sourceSet.getName() + " Pbj grammars.");
                        // 4) set up convention mapping for default sources (allows user to not have to specify)
                        pbjTask.setSource(pbjSourceSet);
                        pbjTask.setJavaMainOutputDirectory(outputDirectoryMain);
                        pbjTask.setJavaTestOutputDirectory(outputDirectoryTest);
                    });

                    // 5) register fact that pbj should be run before compiling
                    // register fact that generateProtoSource should be run before compiling
                    project.getTasks().named(javaMainSrcSet.getCompileJavaTaskName(), task -> task.dependsOn(taskName));
                });
    }

    private static PbjSourceDirectorySet createPbjSourceDirectorySet(String parentDisplayName, ObjectFactory objectFactory) {
        String name = parentDisplayName + ".pbj";
        String displayName = parentDisplayName + " Pbj source";
        PbjSourceDirectorySet pbjSourceSet = objectFactory.newInstance(DefaultPbjSourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName));
        pbjSourceSet.getFilter().include("**/*.proto");
        return pbjSourceSet;
    }
}