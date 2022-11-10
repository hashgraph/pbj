package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.computeJavaPackageSuffix;

/**
 * Class that manages packages and enum names that are used more than one place in code generation
 */
@SuppressWarnings("unused")
public final class LookupHelper {
	/** The sub package where all parser java classes should be placed */
	private static final String PARSERS_SUBPACKAGE = "parser";
	/** The sub package where all schema java classes should be placed */
	private static final String SCHEMAS_SUBPACKAGE = "schema";
	/** The sub package where all model java classes should be placed */
	private static final String MODELS_SUBPACKAGE = "model";
	/** The sub package where all writer java classes should be placed */
	private static final String WRITERS_SUBPACKAGE = "writer";
	/** The sub package where all unit test java classes should be placed */
	private static final String TESTS_SUBPACKAGE = "tests";

	private final Map<String,String> packageSuffixMap = new HashMap<>();
	private final Set<String> enumNames = new HashSet<>();
	private final String basePackagePlusDot;

	/**
	 * Build a new lookup helper, root directory of protobuf files. This scans directory reading protobuf files
	 * extracting what is needed.
	 *
	 * @param protoDir base directory for protobuf sources, all relative paths are taken from here
	 * @param basePackage the base package name under which generated code will be placed in sub packages like "model"
	 */
	public LookupHelper(File protoDir, String basePackage) {
		build(protoDir);
		this.basePackagePlusDot = basePackage + '.';
	}

	/**
	 * Get the base Java package a model class should be generated into.
	 *
	 * @return base java package to put model class in
	 */
	public String getModelPackage() {
		return basePackagePlusDot + MODELS_SUBPACKAGE;
	}

	/**
	 * Get the Java package a model class should be generated into for a given message. The directory structure for
	 * where the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the message
	 * @return java package to put model class in
	 */
	public String getModelPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return getModelPackage() + suffix;
	}

	/**
	 * Get the base Java package a parser class should be generated into.
	 *
	 * @return base java package to put parser class in
	 */
	public String getParserPackage() {
		return basePackagePlusDot + PARSERS_SUBPACKAGE;
	}

	/**
	 * Get the Java package a parser class should be generated into for a given message. The directory structure for
	 * where the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put parser class in
	 */
	public String getParserPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return getParserPackage() + packageSuffixMap.get(messageName);
	}

	/**
	 * Get the base Java package a writer class should be generated into.
	 *
	 * @return base java package to put writer class in
	 */
	public String getWriterPackage() {
		return basePackagePlusDot + WRITERS_SUBPACKAGE;
	}

	/**
	 * Get the Java package a writer class should be generated into for a given message. The directory structure for
	 * where the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the message
	 * @return java package to put writer class in
	 */
	public String getWriterPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return getWriterPackage() + packageSuffixMap.get(messageName);
	}

	/**
	 * Get the base Java package a schema class should be generated into.
	 *
	 * @return base java package to put schema class in
	 */
	public String getSchemaPackage() {
		return basePackagePlusDot + SCHEMAS_SUBPACKAGE;
	}

	/**
	 * Get the Java package a schema class should be generated into for a given message. The directory structure for
	 * where the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the message
	 * @return java package to put schema class in
	 */
	public String getSchemaPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return getSchemaPackage() + packageSuffixMap.get(messageName);
	}

	/**
	 * Get the base Java package a test class should be generated into.
	 *
	 * @return base java package to put test class in
	 */
	public String getTestPackage() {
		return basePackagePlusDot + TESTS_SUBPACKAGE;
	}

	/**
	 * Get the Java package a test class should be generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the message
	 * @return java package to put test class in
	 */
	public String getTestPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return getTestPackage() + packageSuffixMap.get(messageName);
	}

	/**
	 * Check if the given messageType is a known enum
	 *
	 * @param messageType to check if enum
	 * @return true if known as a enum, recorded by addEnum()
	 */
	public boolean isEnum(String messageType) {
		return enumNames.contains(messageType);
	}

	/**
	 * Builds onto map from protobuf message to java package. This is used to produce imports for messages in other
	 * packages.
	 *
	 * @param protobufSrcFilesDir a directory containing protobuf files or the protobuf file to parse and look for messages
	 *                           types in
	 */
	private void build(File protobufSrcFilesDir) {
		if (protobufSrcFilesDir.isDirectory()) {
			for (final File file : Objects.requireNonNull(protobufSrcFilesDir.listFiles())) {
				build(file);
			}
		} else if (protobufSrcFilesDir.getName().endsWith(".proto")){
			final String dirName = protobufSrcFilesDir.getParentFile().getName().toLowerCase();
			try (var input = new FileInputStream(protobufSrcFilesDir)) {
				final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
				final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
				final String javaPackageSuffix = computeJavaPackageSuffix(dirName);
				var parsedDoc = parser.proto();
				for (var item : parsedDoc.topLevelDef()) {
					if (item.messageDef() != null) build(item.messageDef(), javaPackageSuffix);
					if (item.enumDef() != null) build(item.enumDef(), javaPackageSuffix);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Walk a message def and build packages and enums lists
	 * *
	 * @param msgDef protobuf message def
	 * @param javaPackageSuffix the java package relative to root package that the message should be in
	 */
	private void build(Protobuf3Parser.MessageDefContext msgDef, String javaPackageSuffix) {
		var msgName = msgDef.messageName().getText();
		packageSuffixMap.put(msgName, javaPackageSuffix);
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) build(item.messageDef(), javaPackageSuffix);
			if (item.enumDef() != null) build(item.enumDef(), javaPackageSuffix);
		}
	}

	/**
	 * Walk a enum def and build packages and enums lists
	 * *
	 * @param enumDef protobuf enum def
	 * @param javaPackageSuffix the java package relative to root package that the enum should be in
	 */
	private void build(Protobuf3Parser.EnumDefContext enumDef, String javaPackageSuffix) {
		final var enumName = enumDef.enumName().getText();
		packageSuffixMap.put(enumName, javaPackageSuffix);
		enumNames.add(enumName);
	}
}
