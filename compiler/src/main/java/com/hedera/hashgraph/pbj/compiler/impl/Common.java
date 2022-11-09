package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Common functions and constants for code generation
 */
public class Common {
	/** The indent for fields, default 4 spaces */
	public static final String FIELD_INDENT = " ".repeat(4);
	/** The base package where all parser java classes should be placed */
	public static final String PARSERS_DEST_PACKAGE = "com.hedera.hashgraph.pbj.runtime.parser";
	/** The base package where all schema java classes should be placed */
	public static final String SCHEMAS_DEST_PACKAGE = "com.hedera.hashgraph.pbj.runtime.schema";
	/** The base package where all model java classes should be placed */
	public static final String MODELS_DEST_PACKAGE = "com.hedera.hashgraph.pbj.runtime.model";
	/** The base package where all writer java classes should be placed */
	public static final String WRITERS_DEST_PACKAGE = "com.hedera.hashgraph.pbj.runtime.writer";
	/** The base package where all unit test java classes should be placed */
	public static final String UNIT_TESTS_DEST_PACKAGE = "com.hedera.hashgraph.pbj.runtime";

	/**
	 * Compute a destination Java package based on parent directory of the protobuf file
	 *
	 * @param destPackage the base package to start from
	 * @param dirName The name of the parent protobuf directory
	 * @return complete java package
	 */
	@NotNull
	public static String computeJavaPackage(final String destPackage, final String dirName) {
		return destPackage + computeJavaPackageSuffix(dirName);
	}

	/**
	 * Compute a destination Java package suffix based on parent directory of the protobuf file
	 *
	 * @param dirName The name of the parent protobuf directory
	 * @return complete java package
	 */
	@NotNull
	public static String computeJavaPackageSuffix(final String dirName) {
		return (dirName.equals("services") ? "" : "." + dirName);
	}

	/**
	 * Extract Java package option from parsed protobuf document
	 *
	 * @param parsedDoc parseed protobuf source
	 * @return the java package option if set or empty string
	 */
	public static String getJavaPackage(Protobuf3Parser.ProtoContext parsedDoc) {
		String packageName = "";
		for(var option: parsedDoc.optionStatement()){
			if ("java_package".equals(option.optionName().getText())) {
				packageName = option.constant().getText().replace("\"","");
			}
		}
		return packageName;
	}

	/**
	 * Make sure first charachter of a string is upper case
	 *
	 * @param name string input who's first charachter can be upper or lower case
	 * @return name with first charachter converted to upper case
	 */
	public static String capitalizeFirstLetter(String name) {
		if (name.length() > 0) {
			if (name.chars().allMatch(Character::isUpperCase)) {
				return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
			} else {
				return Character.toUpperCase(name.charAt(0)) + name.substring(1);
			}
		}
		return name;
	}

	/**
	 * Convert names like "hello_world" to "HelloWorld" or "helloWorld" depening on firstUpper. Also handles special case
	 * like "HELLO_WORLD" to same output as "hello_world, while "HelloWorld_Two" still becomes "helloWorldTwo".
	 *
	 * @param name input name in snake case
	 * @param firstUpper if true then first char is upper case otherwise it is lower
	 * @return out name in camel case
	 */
	public static String snakeToCamel(String name, boolean firstUpper) {
		final String out =  Arrays.stream(name.split("_")).map(Common::capitalizeFirstLetter).collect(
				Collectors.joining(""));
		return firstUpper ? out : Character.toLowerCase(out.charAt(0)) + out.substring(1);
	}

	/**
	 * Convert a camel case name to upper case snake case
	 *
	 * @param name the input name in camel case
	 * @return output name in upper snake case
	 */
	public static String camelToUpperSnake(String name) {
		// check if already camel upper
		if (name.chars().allMatch(c -> Character.isUpperCase(c) || Character.isDigit(c) || c == '_')) return name;
		// check if already has underscores, then just capitalize
		if (name.chars().anyMatch(c -> c == '_')) return name.toUpperCase();
		// else convert
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			if (Character.isUpperCase(c) && i > 0) {
				buf.append("_");
				buf.append(c);
			} else {
				buf.append(Character.toUpperCase(c));
			}
		}
		// fix special case for captial ID
		final String converted = buf.toString().replaceAll("_I_D", "_ID");
		return converted;
	}

	/**
	 * Clean up a java doc style comment removing all the "*" etc.
	 *
	 * @param fieldComment raw Java doc style comment
	 * @return clean multi-line content of the comment
	 */
	public static String cleanJavaDocComment(String fieldComment) {
		return fieldComment
				.replaceAll("/\\*\\*[\n\r\s\t]*\\*[\t\s]*|[\n\r\s\t]*\\*/","") // remove java doc
				.replaceAll("\n\s+\\*\s+","\n"); // remove indenting and *
	}

	/**
	 * Convert a field type like "long" to the Java object wrapper type "Long", or pass though if not java primative
	 *
	 * @param primativeFieldType java field type like "int" etc
	 * @return java object wrapper type like "Integer" or pass though
	 */
	public static String javaPrimativeToObjectType(String primativeFieldType) {
		return switch(primativeFieldType){
			case "boolean" -> "Boolean";
			case "int" -> "Integer";
			case "long" -> "Long";
			case "float" -> "Float";
			case "double" -> "Double";
			default -> primativeFieldType;
		};
	}
}
