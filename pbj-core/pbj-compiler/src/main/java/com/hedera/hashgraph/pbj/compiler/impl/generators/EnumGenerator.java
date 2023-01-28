package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.hashgraph.pbj.compiler.impl.FileType;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;

/**
 * Code for generating enum code
 */
@SuppressWarnings({"InconsistentTextBlockIndent", "EscapedSpace"})
public final class EnumGenerator {

	/** Record for an enum value temporary storage */
	record EnumValue(String name, boolean deprecated, String javaDoc) {}

	/**
	 * Generate a Java enum from protobuf enum
	 *
	 * @param enumDef the parsed enum def
	 * @param destinationSrcDir The destination source directory to generate into
	 * @param lookupHelper Lookup helper for package information
	 * @throws IOException if there was a problem writing generated code
	 */
	public static void generateEnumFile(Protobuf3Parser.EnumDefContext enumDef, File destinationSrcDir,
								 final ContextualLookupHelper lookupHelper) throws IOException {
		final String enumName = enumDef.enumName().getText();
		final String modelPackage = lookupHelper.getPackageForEnum(FileType.MODEL, enumDef);
		final String javaDocComment = (enumDef.docComment()== null) ? "" :
				cleanDocStr(enumDef.docComment().getText().replaceAll("\n \\*\s*\n","\n * <p>\n"));
		String deprecated = "";
		final Map<Integer, EnumValue> enumValues = new HashMap<>();
		int maxIndex = 0;
		for(var item: enumDef.enumBody().enumElement()) {
			if (item.enumField() != null && item.enumField().ident() != null) {
				final var enumValueName = item.enumField().ident().getText();
				final var enumNumber = Integer.parseInt(item.enumField().intLit().getText());
				final String enumValueJavaDoc = cleanDocStr(
						(item.enumField().docComment() == null || item.enumField().docComment().getText().isBlank()) ?
								enumValueName :
						item.enumField().docComment().getText()
								.replaceAll("[\t\s]*/\\*\\*","") // remove doc start indenting
								.replaceAll("\n[\t\s]+\\*","\n") // remove doc indenting
								.replaceAll("/\\*\\*","") //  remove doc start
								.replaceAll("\\*\\*/","") //  remove doc end
				);
				maxIndex = Math.max(maxIndex, enumNumber);
				enumValues.put(enumNumber, new EnumValue(enumValueName, false,enumValueJavaDoc));
			} else if (item.optionStatement() != null){
				if ("deprecated".equals(item.optionStatement().optionName().getText())) {
					deprecated = "@Deprecated ";
				} else {
					System.err.println("Unhandled Option: "+item.optionStatement().getText());
				}
			} else {
				System.err.println("EnumGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}
		try (FileWriter javaWriter = new FileWriter(getJavaFile(destinationSrcDir, modelPackage, enumName))) {
			javaWriter.write(
					"package "+modelPackage+";\n\n"+
							createEnum("", javaDocComment, deprecated, enumName, maxIndex, enumValues, false)
			);
		}
	}

	/**
	 * Generate code for a enum
	 *
	 * @param indent extra indent spaces beyond the default 4
	 * @param javaDocComment either enum javadoc comment or empty string
	 * @param deprecated either @deprecated string or empty string
	 * @param enumName the name for enum
	 * @param maxIndex the max ordinal for enum
	 * @param enumValues map of ordinal to enum value
	 * @param addUnknown when true we add an enum value for one of
	 * @return string code for enum
	 */
	static String createEnum(String indent, String javaDocComment, String deprecated, String enumName,
							 int maxIndex, Map<Integer, EnumValue> enumValues, boolean addUnknown) {
		final List<String> enumValuesCode = new ArrayList<>(maxIndex);
		if (addUnknown) {
			enumValuesCode.add(FIELD_INDENT+"""
					 /**
					  * Enum value for a unset OneOf, to avoid null OneOfs
					  */
					 UNSET(-1)"""
					.replaceAll("\n","\n"+FIELD_INDENT));
		}
		for (int i = 0; i <= maxIndex; i++) {
			final EnumValue enumValue = enumValues.get(i);
			if (enumValue != null) {
				System.out.println("======================================================================");
				System.out.println(enumValue.javaDoc);
				System.out.println("----------------------------------------------------------------------");
				final String cleanedEnumComment = FIELD_INDENT + "/** \n"
						+ FIELD_INDENT+" * "
						+ enumValue.javaDoc.replaceAll("\n[\t\s]*","\n"+FIELD_INDENT+" * ") // clean up doc indenting
						+ "\n"
						+ FIELD_INDENT + " */\n";

//						.replaceAll("[\t\s]*/\\*\\*",FIELD_INDENT+"/**") // clean up doc start indenting
//						.replaceAll("\n[\t\s]+\\*","\n"+FIELD_INDENT+" *") // clean up doc indenting
//						.replaceAll("/\\*\\*","/**\n"+FIELD_INDENT+" * <b>("+i+")</b>") // add field index

				System.out.println(cleanedEnumComment);
				System.out.println("======================================================================");
				final String deprecatedText = enumValue.deprecated ? FIELD_INDENT+"@Deprecated\n" : "";
				enumValuesCode.add(
						cleanedEnumComment
						+ deprecatedText+FIELD_INDENT+camelToUpperSnake(enumValue.name)+"("+i+")");
			}
		}
		return """
				$javaDocComment
				$deprecated public enum $enumName implements com.hedera.hashgraph.pbj.runtime.EnumWithProtoOrdinal{
				$enumValues;
					
					/** The oneof field ordinal in protobuf for this type */
					private final int protoOrdinal;
					
					/**
					 * OneOf Type Enum Constructor
					 *
					 * @param protoOrdinal The oneof field ordinal in protobuf for this type
					 */
					$enumName(final int protoOrdinal) {
						this.protoOrdinal = protoOrdinal;
					}
					
					/**
					 * Get the oneof field ordinal in protobuf for this type
					 *
					 * @return The oneof field ordinal in protobuf for this type
					 */
					public int protoOrdinal() {
						return protoOrdinal;
					}
					
					/**
					 * Get enum from protobuf ordinal
					 *
					 * @param ordinal the protobuf ordinal number
					 * @return enum for matching ordinal
					 * @throws IllegalArgumentException if ordinal doesn't exist
					 */
					public static $enumName fromProtobufOrdinal(int ordinal) {
						return switch(ordinal) {
				$caseStatements
							default -> throw new IllegalArgumentException("Unknown protobuf ordinal "+ordinal);
						};
					}
				}
				"""
				.replace("$javaDocComment", javaDocComment)
				.replace("$deprecated", deprecated)
				.replace("$enumName", enumName)
				.replace("$enumValues", String.join(",\n\n", enumValuesCode))
				.replace("$caseStatements", enumValues.entrySet().stream().map((entry) -> "			case " + entry.getKey() + " -> " +
										camelToUpperSnake(entry.getValue().name) + ";").collect(Collectors.joining("\n")))
				.replaceAll("\n", "\n" + indent);
	}
}
