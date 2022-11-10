package com.hedera.hashgraph.pbj.compiler;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Gradle Task that generates java src code from protobuf proto schema files.
 */
public abstract class PbjCompilerTask extends SourceTask {

	private Set<File> protoSrcDirectories;
	private File javaMainOutputDirectory;
	private File javaTestOutputDirectory;
	private String basePackage;

	public void setProtoSrcDirectories(Set<File> protoSrcDirectories) {
		this.protoSrcDirectories = protoSrcDirectories;
	}

	@OutputDirectory
	public File getJavaMainOutputDirectory() {
		return javaMainOutputDirectory;
	}

	public void setJavaMainOutputDirectory(File javaMainOutputDirectory) {
		this.javaMainOutputDirectory = javaMainOutputDirectory;
	}

	@OutputDirectory
	public File getJavaTestOutputDirectory() {
		return javaTestOutputDirectory;
	}

	public void setJavaTestOutputDirectory(File javaTestOutputDirectory) {
		this.javaTestOutputDirectory = javaTestOutputDirectory;
	}

	@Input
	public String getBasePackage() {
		return basePackage;
	}

	public void setBasePackage(String basePackage) {
		this.basePackage = basePackage;
	}

	@TaskAction
	public void perform() throws IOException {
		System.out.println("PbjCompilerTask.perform getBasePackage="+getBasePackage());
		try {
			// for each proto src directory generate code
			for (final File protoSrcDirectory : protoSrcDirectories) {

				System.out.println("protoSrcDirectory = " + protoSrcDirectory);
				System.out.println("protoSrcDirectory.exists() = " + protoSrcDirectory.exists());
				if (protoSrcDirectory.exists()) {
					final LookupHelper lookupHelper = new LookupHelper(protoSrcDirectory, basePackage);
					ModelGenerator.generateModel(protoSrcDirectory, javaMainOutputDirectory, lookupHelper);
					SchemaGenerator.generateSchemas(protoSrcDirectory, javaMainOutputDirectory, lookupHelper);
					ParserGenerator.generateParsers(protoSrcDirectory, javaMainOutputDirectory, lookupHelper);
					WriterGenerator.generateWriters(protoSrcDirectory, javaMainOutputDirectory, lookupHelper);
					TestGenerator.generateUnitTests(protoSrcDirectory, javaTestOutputDirectory, lookupHelper);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			throw e;
		}
	}
}