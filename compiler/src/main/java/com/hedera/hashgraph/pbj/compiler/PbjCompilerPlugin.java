package com.hedera.hashgraph.pbj.compiler;

import com.hedera.hashgraph.pbj.compiler.impl.DefaultPbjSourceDirectorySet;
import com.hedera.hashgraph.pbj.compiler.impl.PbjSourceDirectorySet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
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

    public void apply(Project project) {
        // apply java plugin as we depend on it
        project.getPluginManager().apply(JavaLibraryPlugin.class);
        // get reference to java plugin
        final var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        // get java src sets
        final var javaMainSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final var javaTestSrcSet = javaPlugin.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        System.out.println("1 javaMainSrcSet = " + javaMainSrcSet);

        // for each java source we add a /proto sub dir to our protoSourceSet
        final PbjSourceDirectorySet protoSourceSet = objectFactory.newInstance(DefaultPbjSourceDirectorySet.class,
                objectFactory.sourceDirectorySet("pbj", "pbj Protobuf Src"));
        protoSourceSet.getFilter().include("**/*.proto");
        javaPlugin.getSourceSets().all(
                sourceSet -> {
                    sourceSet.getExtensions().add(PbjSourceDirectorySet.class, PbjSourceDirectorySet.NAME, protoSourceSet);
                    final String srcDir = "src/" + sourceSet.getName() + "/proto";
                    protoSourceSet.srcDir(srcDir);
                    sourceSet.getAllSource().source(protoSourceSet);
                });


        // 3) Set up the Proto Compiler output directory (adding to javac inputs!)
        final File outputDirectory = new File(project.getBuildDir() + "/generated/source/pbj-proto/main");
        final File outputDirectoryMain = new File(outputDirectory,"java");
        final File outputDirectoryTest = new File(outputDirectory, "test");
        System.out.println("outputDirectoryMain = " + outputDirectoryMain);
        System.out.println("outputDirectoryTest = " + outputDirectoryTest);
        javaMainSrcSet.getJava().srcDir(outputDirectoryMain);
        javaTestSrcSet.getJava().srcDir(outputDirectoryTest);

        // register generateProtoSource task and configure it
        final String taskName = "generatePbjProtoSource";
        project.getTasks().register(taskName, PbjCompilerTask.class, pbjCompilerTask -> {
            pbjCompilerTask.setDescription("Generates java src from the src/main/proto protobuf proto schemas.");
            pbjCompilerTask.setProtoSrcDirectories(protoSourceSet.getSrcDirs());
            pbjCompilerTask.setSource(protoSourceSet);
            pbjCompilerTask.setJavaTestOutputDirectory(outputDirectoryTest);
            pbjCompilerTask.setJavaMainOutputDirectory(outputDirectoryMain);
        });
        // register fact that generateProtoSource should be run before compiling
        project.getTasks().named(javaMainSrcSet.getCompileJavaTaskName(), task -> task.dependsOn(taskName));
    }
}