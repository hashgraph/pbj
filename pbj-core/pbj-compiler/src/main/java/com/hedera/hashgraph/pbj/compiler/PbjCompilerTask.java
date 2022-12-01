package com.hedera.hashgraph.pbj.compiler;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.generators.*;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.FileInputStream;

/**
 * Gradle Task that generates java src code from protobuf proto schema files.
 */
public abstract class PbjCompilerTask extends SourceTask {

	private File javaMainOutputDirectory;
	private File javaTestOutputDirectory;

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