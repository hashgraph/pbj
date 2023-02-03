package com.hedera.pbj.compiler;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.LookupHelper;
import com.hedera.pbj.compiler.impl.generators.EnumGenerator;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;

/**
 * Gradle Task that generates java src code from protobuf proto schema files.
 */
public abstract class PbjCompilerTask extends SourceTask {

	/** The java main directory that we write generated code into */
	private File javaMainOutputDirectory;

	/** The java test directory that we write generated code into */
	private File javaTestOutputDirectory;

	/**
	 * Get the java main directory that we write generated code into
	 *
	 * @return The java main directory that we write generated code into
	 */
	@OutputDirectory
	public File getJavaMainOutputDirectory() {
		return javaMainOutputDirectory;
	}

	/**
	 * Set the java main directory that we write generated code into
	 *
	 * @param javaMainOutputDirectory The new java main directory that we write generated code into
	 */
	public void setJavaMainOutputDirectory(File javaMainOutputDirectory) {
		this.javaMainOutputDirectory = javaMainOutputDirectory;
	}

	/**
	 * Get the java test directory that we write generated code into
	 *
	 * @return The java test directory that we write generated code into
	 */
	@OutputDirectory
	public File getJavaTestOutputDirectory() {
		return javaTestOutputDirectory;
	}

	/**
	 * Set the java test directory that we write generated code into
	 *
	 * @param javaTestOutputDirectory The new java test directory that we write generated code into
	 */
	public void setJavaTestOutputDirectory(File javaTestOutputDirectory) {
		this.javaTestOutputDirectory = javaTestOutputDirectory;
	}

	/**
	 * Perform task action - Generates all the PBJ java source files
	 *
	 * @throws Exception If there was a problem performing action
	 */
	@TaskAction
	public void perform() throws Exception {
		try {
			// first we do a scan of files to build lookup tables for imports, packages etc.
			final LookupHelper lookupHelper = new LookupHelper(getSource());
			// for each proto src directory generate code
			for (final File protoFile : getSource()) {
				if (protoFile.exists() && protoFile.isFile() && protoFile.getName().endsWith(".proto")) {
					final ContextualLookupHelper contextualLookupHelper = new ContextualLookupHelper(lookupHelper, protoFile);
					try (var input = new FileInputStream(protoFile)) {
						final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
						final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
						final Protobuf3Parser.ProtoContext parsedDoc = parser.proto();
						for (var topLevelDef : parsedDoc.topLevelDef()) {
							final Protobuf3Parser.MessageDefContext msgDef = topLevelDef.messageDef();
							if (msgDef != null) {
								// run all generators for message
								for(var generatorClass: Generator.GENERATORS) {
									final var generator = generatorClass.getDeclaredConstructor().newInstance();
									generator.generate(msgDef, javaMainOutputDirectory, javaTestOutputDirectory, contextualLookupHelper);
								}
							}
							final Protobuf3Parser.EnumDefContext enumDef = topLevelDef.enumDef();
							if (enumDef != null) {
								// run just enum generators for enum
								EnumGenerator.generateEnumFile(enumDef, javaMainOutputDirectory, contextualLookupHelper);
							}
						}
					} catch (Exception e) {
						System.err.println("Exception while processing file: "+protoFile);
						e.printStackTrace();
						throw e;
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			throw e;
		}
	}
}