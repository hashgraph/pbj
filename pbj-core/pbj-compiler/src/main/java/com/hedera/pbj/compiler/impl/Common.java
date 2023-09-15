package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common functions and constants for code generation
 */
@SuppressWarnings({"DuplicatedCode", "EscapedSpace"})
public final class Common {
	/** The indent for fields, default 4 spaces */
	public static final String FIELD_INDENT = " ".repeat(4);

	/** Number of bits used to represent the tag type */
	static final int TAG_TYPE_BITS = 3;

	/** Wire format code for var int */
	public static final int TYPE_VARINT = 0;
	/** Wire format code for fixed 64bit number */
	public static final int TYPE_FIXED64 = 1;
	/** Wire format code for length delimited, all the complex types */
	public static final int TYPE_LENGTH_DELIMITED = 2;
	/** Wire format code for fixed 32bit number */
	public static final int TYPE_FIXED32 = 5;


	/**
	 * Makes a tag value given a field number and wire type.
	 *
	 * @param wireType the wire type part of tag
	 * @param fieldNumber the field number part of tag
	 * @return packed encoded tag
	 */
	public static int getTag(final int wireType, final int fieldNumber) {
		return (fieldNumber << TAG_TYPE_BITS) | wireType;
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
		return (firstUpper ? Character.toUpperCase(out.charAt(0)) : Character.toLowerCase(out.charAt(0)) )
				+ out.substring(1);
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
	 * Build a clean java doc comment for a field
	 *
	 * @param fieldNumber The field proto number
	 * @param docContext The parsed field comment contact
	 * @return clean comment
	 */
	public static String buildCleanFieldJavaDoc(int fieldNumber, Protobuf3Parser.DocCommentContext docContext) {
		final String cleanedComment = docContext == null ? "" : cleanJavaDocComment(docContext.getText());
		final String fieldNumComment = "<b>("+fieldNumber+")</b> ";
		return fieldNumComment + cleanedComment;
	}

	/**
	 * Build a clean java doc comment for an oneof field
	 *
	 * @param fieldNumbers The field proto numbers for all fields in oneof
	 * @param docContext The parsed field comment contact
	 * @return clean comment
	 */
	public static String buildCleanFieldJavaDoc(List<Integer> fieldNumbers, Protobuf3Parser.DocCommentContext docContext) {
		final String cleanedComment = docContext == null ? "" : cleanJavaDocComment(docContext.getText());
		final String fieldNumComment =
				"<b>("+fieldNumbers.stream().map(Objects::toString).collect(Collectors.joining(", "))+")</b> ";
		return fieldNumComment + cleanedComment;
	}

	/**
	 * Clean up a java doc style comment removing all the "*" etc.
	 *
	 * @param fieldComment raw Java doc style comment
	 * @return clean multi-line content of the comment
	 */
	public static String cleanJavaDocComment(String fieldComment) {
		return cleanDocStr(fieldComment
				.replaceAll("/\\*\\*[\n\r\s\t]*\\*[\t\s]*|[\n\r\s\t]*\\*/","") // remove java doc
				.replaceAll("\n\s+\\*\s+","\n") // remove indenting and *
				.replaceAll("/\\*\\*","") // remove indenting and /** at beginning of comment.
				.trim() // Remove leading and trailing spaces.
		);
	}

	/**
	 * Clean a string so that it can be included in JavaDoc. Does things like replace unsupported HTML tags.
	 *
	 * @param docStr The string to clean
	 * @return cleaned output
	 */
	public static String cleanDocStr(String docStr) {
		return docStr
				.replaceAll("<(/?)tt>", "<$1code>") // tt tags are not supported in javadoc
				.replaceAll(" < ", " &lt; ") // escape loose less than
				.replaceAll(" > ", " &gt; ") // escape loose less than
				.replaceAll(" & ", " &amp; ") // escape loose less than
				;
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

//	/** Lubo
//	 * Gets the message fields' values into a {@link List<Object>} collection recursively.
//	 *
//	 * @param msgDef The message object.
//	 * @param values The list where the objects which values needs to be appended.
//	 */
//	public static void getFieldsForHashCode(final Protobuf3Parser.MessageDefContext msgDef,
//										 final ContextualLookupHelper lookupHelper,
//										 final List<Field> values, final boolean topLevel) {
//
//		for(var item: msgDef.messageBody().messageElement()) {
//			if (item.messageDef() != null) { // process sub messages
//				getFieldsForHashCode(item.messageDef(), lookupHelper, values, false);
//			} else if (item.oneof() != null) { // process one ofs
//				final var javaRecordName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
//				final var field = new OneOfField(item.oneof(), javaRecordName, lookupHelper);
//				values.add(field);
//			} else if (item.field() != null && item.field().fieldName() != null) {
//				final var field = new SingleField(item.field(), lookupHelper);
//				values.add(field);
//			}
//		}
//
//		if (topLevel) {
//			Collections.sort(values, new Comparator<Field>() {
//				public int compare(Field f1, Field f2) {
//					return f1.name().compareTo(f2.name());
//				}
//			});
//		}
//	Lubo	return switch(primitiveFieldType){
//			case "boolean" -> "Boolean";
//			case "int" -> "Integer";
//			case "long" -> "Long";
//			case "float" -> "Float";
//			case "double" -> "Double";
//			default -> primitiveFieldType;
//		};
//	}

	/**
	 * Remove leading dot from a string so ".a.b.c" becomes "a.b.c"
	 *
	 * @param text text to remove leading dot from
	 * @return  text without a leading dot
	 */
	public static String removingLeadingDot(String text) {
		if (text.length() > 0 & text.charAt(0) == '.') {
			return text.substring(1);
		}
		return text;
	}

	/**
	 * Get the java file for a src directory, package and classname with optional suffix. All parent directories will
	 * also be created.
	 *
	 * @param srcDir The src dir root of all java src
	 * @param javaPackage the java package with '.' deliminators
	 * @param className the camel case class name
	 * @return File object for java file
	 */
	public static File getJavaFile(File srcDir, String javaPackage, String className) {
		File packagePath = new File(srcDir.getPath() + File.separatorChar + javaPackage.replaceAll("\\.","\\" + File.separator));
		//noinspection ResultOfMethodCallIgnored
		packagePath.mkdirs();
		return new File(packagePath,className+".java");
	}
}
