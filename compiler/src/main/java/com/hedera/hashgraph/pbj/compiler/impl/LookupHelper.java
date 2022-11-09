package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;

/**
 * Class that manages packages and enum names that are used more than one place in code generation
 */
public class LookupHelper {
	private final Map<String,String> packageSuffixMap = new HashMap<>();
	private final Set<String> enumNames = new HashSet<>();

	/**
	 * Build a new lookup helper, root directory of protobuf files. This scans directory reading protobuf files
	 * extracting what is needed.
	 *
	 * @param protoDir base directory for protobuf sources, all relative paths are taken from here
	 */
	public LookupHelper(File protoDir) {
		build(protoDir);
	}

	/**
	 * Get the Java package a model class should generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put model class in
	 */
	public String getModelPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return MODELS_DEST_PACKAGE + suffix;
	}

	/**
	 * Get the Java package a parser class should generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put parser class in
	 */
	public String getParserPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return PARSERS_DEST_PACKAGE + packageSuffixMap.get(messageName);
	}
	/**
	 * Get the Java package a writer class should generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put writer class in
	 */
	public String getWriterPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return WRITERS_DEST_PACKAGE + packageSuffixMap.get(messageName);
	}

	/**
	 * Get the Java package a schema class should generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put schema class in
	 */
	public String getSchemaPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return SCHEMAS_DEST_PACKAGE + packageSuffixMap.get(messageName);
	}

	/**
	 * Get the Java package a test class should generated into for a given message. The directory structure for where
	 * the message is relative to protobuf root is used as sub packages.
	 *
	 * @param messageName The name of the messgae
	 * @return java package to put test class in
	 */
	public String getTestPackage(String messageName) {
		final String suffix = packageSuffixMap.get(messageName);
		if (suffix == null) return null;
		return UNIT_TESTS_DEST_PACKAGE + packageSuffixMap.get(messageName);
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
	 * Builds onto map from protobuf message to java package. This is used to produce imports for messages in other packages.
	 *
	 * @param protosFileOrDir a directory containing protobuf files or the protobuf file to parse and look for messages types in
	 * @param packageMap map of message name to java package name to add to
	 */
	private void build(File protosFileOrDir) {
		if (protosFileOrDir.isDirectory()) {
			for (final File file : protosFileOrDir.listFiles()) {
				build(file);
			}
		} else if (protosFileOrDir.getName().endsWith(".proto")){
			final String dirName = protosFileOrDir.getParentFile().getName().toLowerCase();
			try (var input = new FileInputStream(protosFileOrDir)) {
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
