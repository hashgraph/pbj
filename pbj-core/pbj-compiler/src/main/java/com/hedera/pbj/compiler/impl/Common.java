package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Common functions and constants for code generation
 */
@SuppressWarnings({"DuplicatedCode", "EscapedSpace"})
public final class Common {
	/** The indent for fields, default 4 spaces */
	public static final String FIELD_INDENT = " ".repeat(4);

	public static final int DEFAULT_INDENT = 4;
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
		if (!name.isEmpty()) {
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
	@NonNull
	public static String snakeToCamel(@NonNull String name, boolean firstUpper) {
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
		if (name.contains("_")) return name.toUpperCase();
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

	/**
	 * Recursively calculates the hashcode for a message fields.
	 *
	 * @param fields The fields of this object.
	 * @param generatedCodeSoFar The accumulated hash code so far.
	 * @return The generated code for getting the hashCode value.
	 */
	public static String getFieldsHashCode(final List<Field> fields, String generatedCodeSoFar) {
		for(Field f : fields) {
			if (f.parent() != null) {
				final OneOfField oneOfField = f.parent();
				generatedCodeSoFar += getFieldsHashCode(oneOfField.fields(), generatedCodeSoFar);
			} else if (f.optionalValueType()) {
				generatedCodeSoFar = getPrimitiveWrapperHashCodeGeneration(generatedCodeSoFar, f);
			} else if (f.repeated()) {
				generatedCodeSoFar = getRepeatedHashCodeGeneration(generatedCodeSoFar, f);
			} else {
                if (f.type() == Field.FieldType.FIXED32 ||
                        f.type() == Field.FieldType.INT32 ||
                        f.type() == Field.FieldType.SFIXED32 ||
                        f.type() == Field.FieldType.SINT32 ||
                        f.type() == Field.FieldType.UINT32) {
                    generatedCodeSoFar += (
                            """
                             if ($fieldName != DEFAULT.$fieldName) {
                                 result = 31 * result + Integer.hashCode($fieldName);
                             }
                             """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FIXED64 ||
                        f.type() == Field.FieldType.INT64 ||
                        f.type() == Field.FieldType.SFIXED64 ||
                        f.type() == Field.FieldType.SINT64 ||
                        f.type() == Field.FieldType.UINT64) {
                    generatedCodeSoFar += (
                            """
                             if ($fieldName != DEFAULT.$fieldName) {
                                 result = 31 * result + Long.hashCode($fieldName);
                             }
                             """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BOOL) {
                    generatedCodeSoFar += (
                            """
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Boolean.hashCode($fieldName);
                            }
                            """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FLOAT) {
                    generatedCodeSoFar += (
                            """
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Float.hashCode($fieldName);
                            }
                            """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.DOUBLE) {
                    generatedCodeSoFar += (
							"""
                            if ($fieldName != DEFAULT.$fieldName) {
                               result = 31 * result + Double.hashCode($fieldName);
                            }
                            """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BYTES) {
                    generatedCodeSoFar += (
                            """
                             if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                result = 31 * result + $fieldName.hashCode();
                             }
                             """).replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.ENUM ||
						f.type() == Field.FieldType.STRING ||
						f.parent() == null) { // process sub message
                    generatedCodeSoFar += (
                            """
                             if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                result = 31 * result + $fieldName.hashCode();
                             }
                             """).replace("$fieldName", f.nameCamelFirstLower());
                } else {
                    throw new RuntimeException("Unexpected field type for getting HashCode - " + f.type().toString());
                }
            }
		}
		return generatedCodeSoFar.indent(DEFAULT_INDENT * 2);
	}

	/**
	 * Get the hashcode codegen for a optional field.
	 * @param generatedCodeSoFar The string that the codegen is generated into.
	 * @param f The field for which to generate the hash code.
	 * @return Updated codegen string.
	 */
	@NonNull
	private static String getPrimitiveWrapperHashCodeGeneration(String generatedCodeSoFar, Field f) {
		switch (f.messageType()) {
			case "StringValue" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + $fieldName.hashCode();
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "BoolValue" -> generatedCodeSoFar += (
				"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + Boolean.hashCode($fieldName);
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "Int32Value", "UInt32Value" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + Integer.hashCode($fieldName);
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "Int64Value", "UInt64Value" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + Long.hashCode($fieldName);
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "FloatValue" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + Float.hashCode($fieldName);
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "DoubleValue" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + Double.hashCode($fieldName);
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "BytesValue" -> generatedCodeSoFar += (
     			"""
				if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
				    result = 31 * result + ($fieldName == null ? 0 : $fieldName.hashCode());
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
		}
		return generatedCodeSoFar;
	}

	/**
	 * Get the hashcode codegen for a repeated field.
	 * @param generatedCodeSoFar The string that the codegen is generated into.
	 * @param f The field for which to generate the hash code.
	 * @return Updated codegen string.
	 */
	@NonNull
	private static String getRepeatedHashCodeGeneration(String generatedCodeSoFar, Field f) {
		generatedCodeSoFar += (
           """
			java.util.List list$$fieldName = $fieldName;
			if(list$$fieldName != null) {
			    for (Object o : list$$fieldName) {
			        if (o != null) {
			            result = 31 * result + o.hashCode();
			        } else {
			            result = 31 * result;
			        }
			    }    
			}
			""").replace("$fieldName", f.nameCamelFirstLower());
		return generatedCodeSoFar;
	}

	/**
	 * Recursively calculates the hashcode for a message fields.
	 *
	 * @param fields The fields of this object.
	 * @param generatedCodeSoFar The accumulated hash code so far.
	 * @return The generated code for getting the hashCode value.
	 */
	public static String getFieldsEqualsStatements(final List<Field> fields, String generatedCodeSoFar) {
		for(Field f : fields) {
			if (f.parent() != null) {
				final OneOfField oneOfField = f.parent();
				generatedCodeSoFar += getFieldsEqualsStatements(oneOfField.fields(), generatedCodeSoFar);
			}

			if (f.optionalValueType()) {
				generatedCodeSoFar = getPrimitiveWrapperEqualsGeneration(generatedCodeSoFar, f);
			}
			else if (f.repeated()) {
				generatedCodeSoFar = getRepeatedEqualsGeneration(generatedCodeSoFar, f);
			} else {
                f.nameCamelFirstLower();
                if (f.type() == Field.FieldType.FIXED32 ||
                        f.type() == Field.FieldType.INT32 ||
                        f.type() == Field.FieldType.SFIXED32 ||
                        f.type() == Field.FieldType.SINT32 ||
                        f.type() == Field.FieldType.UINT32) {
                    generatedCodeSoFar +=
                             """
                             if ($fieldName != thatObj.$fieldName) {
                                 return false;
                             }
                             """.replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FIXED64 ||
                        f.type() == Field.FieldType.INT64 ||
                        f.type() == Field.FieldType.SFIXED64 ||
                        f.type() == Field.FieldType.SINT64 ||
                        f.type() == Field.FieldType.UINT64) {
                    generatedCodeSoFar +=
                            """
                            if ($fieldName != thatObj.$fieldName) {
                                return false;
                            }
                            """.replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.BOOL) {
                    generatedCodeSoFar +=
                            """
                             if ($fieldName != thatObj.$fieldName) {
                                 return false;
                             }
                             """.replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            """
                             if ($fieldName != thatObj.$fieldName) {
                                 return false;
                             }
                             """.replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            """
                             if ($fieldName != thatObj.$fieldName) {
                                 return false;
                             }
                             """.replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == Field.FieldType.STRING ||
                        f.type() == Field.FieldType.BYTES ||
                        f.type() == Field.FieldType.ENUM ||
                        f.parent() == null /* Process a sub-message */) {
                    generatedCodeSoFar += (
                            """
                             if ($fieldName == null && thatObj.$fieldName != null) {
                                 return false;
                             }
                             if ($fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
                                 return false;
                             }
                             """).replace("$fieldName", f.nameCamelFirstLower());
                } else {
                    throw new IllegalArgumentException("Unexpected field type for getting HashCode - " + f.type().toString());
                }
            }
		}
		return generatedCodeSoFar.indent(DEFAULT_INDENT);
	}

	/**
	 * Get the equals codegen for a optional field.
	 * @param generatedCodeSoFar The string that the codegen is generated into.
	 * @param f The field for which to generate the equals code.
	 * @return Updated codegen string.
	 */
	@NonNull
	private static String getPrimitiveWrapperEqualsGeneration(String generatedCodeSoFar, Field f) {
		switch (f.messageType()) {
			case "StringValue" ->
				generatedCodeSoFar += (
                """
                if (this.$fieldName == null && thatObj.$fieldName != null) {
                    return false;
                }
                if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
				    return false;
                }
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "BoolValue" ->

				generatedCodeSoFar += (
                """
				if (this.$fieldName == null && thatObj.$fieldName != null) {
				    return false;
				}
				if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
				    return false;
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			case "Int32Value", "UInt32Value", "Int64Value", "UInt64Value", "FloatValue", "DoubleValue" ->
				generatedCodeSoFar += (
    			"""
				if (this.$fieldName == null && thatObj.$fieldName != null) {
				    return false;
				}
				if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
				    return false;
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
            case "BytesValue" ->
				generatedCodeSoFar += (
                """
                if (this.$fieldName == null && thatObj.$fieldName != null) {
                    return false;
                }
				if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
				    return false;
				}
				""").replace("$fieldName", f.nameCamelFirstLower());
			default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
		}
		;
		return generatedCodeSoFar;
	}

	/**
	 * Get the equals codegen for a repeated field.
	 * @param generatedCodeSoFar The string that the codegen is generated into.
	 * @param f The field for which to generate the equals code.
	 * @return Updated codegen string.
	 */
	@NonNull
	private static String getRepeatedEqualsGeneration(String generatedCodeSoFar, Field f) {
		generatedCodeSoFar += (
    	"""
		if (this.$fieldName == null && thatObj.$fieldName != null) {
		    return false;
		}

		if (this.$fieldName != null && !$fieldName.equals(thatObj.$fieldName)) {
		    return false;
		}
		""").replace("$fieldName", f.nameCamelFirstLower());
		return generatedCodeSoFar;
	}

	/**
	 * Remove leading dot from a string so ".a.b.c" becomes "a.b.c"
	 *
	 * @param text text to remove leading dot from
	 * @return  text without a leading dot
	 */
	public static String removingLeadingDot(String text) {
		if (!text.isEmpty() & text.charAt(0) == '.') {
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
