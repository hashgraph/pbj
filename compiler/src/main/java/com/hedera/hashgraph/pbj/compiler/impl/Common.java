package com.hedera.hashgraph.pbj.compiler.impl;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Common functions and constants for code generation
 */
public class Common {
	/** The indent for fields, default 4 spaces */
	public static final String FIELD_INDENT = " ".repeat(4);

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
	 * Make sure first character of a string is upper case
	 *
	 * @param name string input who's first character can be upper or lower case
	 * @return name with first character converted to upper case
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
	 * Convert names like "hello_world" to "HelloWorld" or "helloWorld" depending on firstUpper. Also handles special case
	 * like "HELLO_WORLD" to same output as "hello_world", while "HelloWorld_Two" still becomes "helloWorldTwo".
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
		final StringBuilder buf = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			if (Character.isUpperCase(c) && i > 0) {
				buf.append("_");
				buf.append(c);
			} else {
				buf.append(Character.toUpperCase(c));
			}
		}
		// fix special case for capital ID
		return buf.toString().replaceAll("_I_D", "_ID");
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
	 * Convert a field type like "long" to the Java object wrapper type "Long", or pass though if not java primitive
	 *
	 * @param primitiveFieldType java field type like "int" etc
	 * @return java object wrapper type like "Integer" or pass though
	 */
	public static String javaPrimitiveToObjectType(String primitiveFieldType) {
		return switch(primitiveFieldType){
			case "boolean" -> "Boolean";
			case "int" -> "Integer";
			case "long" -> "Long";
			case "float" -> "Float";
			case "double" -> "Double";
			default -> primitiveFieldType;
		};
	}
}
