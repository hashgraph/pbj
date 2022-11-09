package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.Field.FieldType;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Lexer;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;

/**
 * Code generator that parses protobuf files and generates nice parsers for each message type.
 */
public class ParserGenerator {

	/** Suffix for parser java classes */
	public static final String PARSER_JAVA_FILE_SUFFIX = "ProtoParser";

	/** Record for a enum value tempory storage */
	private record EnumValue(String name, boolean deprecated, String javaDoc) {}
	/** Record for a field doc tempory storage */
	private record FieldDoc(String fieldName, String fieldComment) {}

	/**
	 * Main generate method that process a single protobuf file and writes records and enums into package directories
	 * inside the destinationSrcDir.
	 *
	 * @param protoDir The protobuf file to parse
	 * @param destinationSrcDir the generated source directory to write files into
	 * @param lookupHelper helper for global context
	 * @throws IOException if there was a problem writing files
	 */
	public static void generateParsers(File protoDir, File destinationSrcDir, final LookupHelper lookupHelper) throws IOException {
		generate(protoDir, destinationSrcDir,lookupHelper);
	}


	/**
	 * Process a directory of protobuf files or indervidual protobuf file. Generating Java record clasess for each
	 * message type and Java enums for each protobuf enum.
	 *
	 * @param protoDirOrFile directory of protobuf files or indervidual protobuf file
	 * @param destinationSrcDir The destination source directory to generate into
	 * @param lookupHelper helper for global context
	 * @throws IOException if there was a problem writing generated files
	 */
	private static void generate(File protoDirOrFile, File destinationSrcDir,
			final LookupHelper lookupHelper) throws IOException {
		if (protoDirOrFile.isDirectory()) {
			for (final File file : protoDirOrFile.listFiles()) {
				if (file.isDirectory() || file.getName().endsWith(".proto")) {
					generate(file, destinationSrcDir, lookupHelper);
				}
			}
		} else {
			final String dirName = protoDirOrFile.getParentFile().getName().toLowerCase();
			try (var input = new FileInputStream(protoDirOrFile)) {
				final var lexer = new Protobuf3Lexer(CharStreams.fromStream(input));
				final var parser = new Protobuf3Parser(new CommonTokenStream(lexer));
				final Protobuf3Parser.ProtoContext parsedDoc = parser.proto();
				final String javaPackage = computeJavaPackage(PARSERS_DEST_PACKAGE, dirName);
				final Path packageDir = destinationSrcDir.toPath().resolve(javaPackage.replace('.', '/'));
				Files.createDirectories(packageDir);
				for (var topLevelDef : parsedDoc.topLevelDef()) {
					final Protobuf3Parser.MessageDefContext msgDef = topLevelDef.messageDef();
					if (msgDef != null) {
						generateParserFile(msgDef, dirName, javaPackage, packageDir, lookupHelper);
					}
				}
			}
		}
	}

	/**
	 * Generate a Java parser from protobuf message type
	 *
	 * @param msgDef the parsed message
	 * @param dirName the directory name of the dir containing the protobuf file
	 * @param javaPackage the java package the record file should be generated in
	 * @param packageDir the output package directory
	 * @param lookupHelper helper for global context
	 * @throws IOException If there was a problem writing record file
	 */
	private static void generateParserFile(Protobuf3Parser.MessageDefContext msgDef, String dirName, String javaPackage,
			Path packageDir, final LookupHelper lookupHelper) throws IOException {
		final var modelClassName = msgDef.messageName().getText();
		final var parserClassName = modelClassName+ PARSER_JAVA_FILE_SUFFIX;
		final var javaFile = packageDir.resolve(parserClassName + ".java");
		String javaDocComment = (msgDef.docComment()== null) ? "" :
				msgDef.docComment().getText()
						.replaceAll("\n \\*\s*\n","\n * <p>\n");
		String deprectaed = "";
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		final List<String> oneOfUnsetConstants = new ArrayList<>();
		final String moidelJavaPackage = computeJavaPackage(MODELS_DEST_PACKAGE, dirName);
		imports.add(moidelJavaPackage);
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generateParserFile(item.messageDef(), dirName, javaPackage,packageDir,lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, true, false, false);
				oneOfUnsetConstants.add(
						"    public static final OneOf<%s> %s = new OneOf<>(%s.UNSET,null);"
						.formatted(field.getEnumClassRef(),camelToUpperSnake(field.name())+"_UNSET", field.getEnumClassRef()));
			} else if (item.mapField() != null) { // process map fields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ parserClassName);
			} else if (item.reserved() != null) { // process reserved
				// reserved are not needed
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, true, false, false);
			} else if (item.optionStatement() != null){
				// no needed for now
			} else {
				System.err.println("Unknown Element: "+item+" -- "+item.getText());
			}
		}

		try (FileWriter javaWriter = new FileWriter(javaFile.toFile())) {
			javaWriter.write("""
					package %s;
					
					import com.hedera.hashgraph.pbj.runtime.FieldDefinition;
					import com.hedera.hashgraph.pbj.runtime.MalformedProtobufException;
					import com.hedera.hashgraph.pbj.runtime.ProtoParser;
					%s
					import java.io.IOException;
					import java.io.InputStream;
					import java.nio.ByteBuffer;
					import static %s.*;

					/**
					 * Parser for %s model object from protobuf format
					 */
					public class %s extends ProtoParser {
					
					%s
					
						// -- REUSED TEMP STATE FIELDS --------------------------------------
						
					%s
					
						// -- PARSE METHODS -------------------------------------------------
						
					%s
					
						// -- OTHER METHODS -------------------------------------------------
						
					%s
					
						// -- FIELD SET METHODS ---------------------------------------------
						
					%s
					}
					""".formatted(
						javaPackage,
						imports.isEmpty() ? "" : imports.stream()
								.filter(input -> !input.equals(javaPackage))
								.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
						computeJavaPackage(SCHEMAS_DEST_PACKAGE, dirName) + "." + modelClassName+ SchemaGenerator.SCHEMA_JAVA_FILE_SUFFIX,
						modelClassName,
						parserClassName,
						oneOfUnsetConstants.stream().collect(Collectors.joining("\n")),
						fields.stream().map(field -> {
							return "    private %s %s = %s;".formatted(field.javaFieldType(), field.name(), field.javaDefault());
						}).collect(Collectors.joining("\n")),
						generateParseMethods(modelClassName, fields),
						generateGetFieldDefinition()+"\n"+generateResetMethod(fields),
						generateFieldSetMethods(fields)
					)
			);
		}
	}

	/**
	 * Generate get field definition method, it just deligates to the schema to get the answer
	 *
	 * @return source code for getFieldDefinition method
	 */
	private static String generateGetFieldDefinition() {
		return 	"""			   
						    /**
						     * get the FieldDefinition for given field number
						     *
						     * @param fieldNumber the field number to lookup definition for
						     * @return field definition for field with given index or null if no field exists.
						     */
							@Override
							protected FieldDefinition getFieldDefinition(final int fieldNumber) {
								return getField(fieldNumber);
							}
						""";
	}

	/**
	 * Generate source code for reset method
	 *
	 * @param fields list of all the fields
	 * @return string of source code for reset method
	 */
	private static String generateResetMethod(final List<Field> fields) {
		return 	"""
						    /**
						     * Reset all fields to default values, so we can start another parse job
						     */
							private void reset() {
								%s
							}
						""".formatted(
								fields.stream()
										.map(field -> {
											return "this.%s = %s;".formatted(field.name(), field.javaDefault());
										})
										.collect(Collectors.joining("\n        ")));
	}

	/** Array of input types to generate parse() methods for */
	private static final String[] PARSE_INPUT_TYPES = new String[]{"byte[]","ByteBuffer","InputStream"};

	/**
	 * Generate source code for parse methods
	 *
	 * @param modelClassName the class name for model class that parse methods will return
	 * @param fields list of all the fields
	 * @return string of source code for parse methods
	 */
	private static String generateParseMethods(final String modelClassName, final List<Field> fields) {
		final String resetFieldsCode = fields.stream()
				.map(field -> {
					return "this.%s = %s;".formatted(field.name(), field.javaDefault());
				})
				.collect(Collectors.joining("\n        "));
		return Arrays.stream(PARSE_INPUT_TYPES)
				.map(inputType ->
					"""
							public %s parse(%s protobuf) throws %sMalformedProtobufException {
								reset();
								super.start(protobuf);
								return new %s(%s);
							}
						""".formatted(
							modelClassName,
							inputType,
							inputType.equals("InputStream")? "IOException, " : "",
							modelClassName,
							fields.stream().map(Field::name).collect(Collectors.joining(", "))
					))
				.collect(Collectors.joining("\n"));
	}

	/**
	 * Enum for all the set field methods that can be in a parser
	 */
	private enum FieldMethodTypes{
		intField(false, FieldType.INT32, FieldType.UINT32, FieldType.SINT32),
		longField(false, FieldType.INT64, FieldType.UINT64, FieldType.SINT64),
		booleanField(false, FieldType.BOOL),
		floatField(false, FieldType.FLOAT, FieldType.FIXED32, FieldType.SFIXED32),
		doubleField(false, FieldType.DOUBLE, FieldType.FIXED64, FieldType.SFIXED64),
		enumField(false, FieldType.ENUM),
		stringField(false, FieldType.STRING),
		bytesField(false, FieldType.BYTES),
		objectField(false, FieldType.MESSAGE), // used for repeated and not repeated
		intList(true, FieldType.INT32, FieldType.UINT32, FieldType.SINT32),
		longList(true, FieldType.INT64, FieldType.UINT64, FieldType.SINT64),
		booleanList(true, FieldType.BOOL),
		enumList(true, FieldType.ENUM);

		private final boolean repeated;
		private final Set<FieldType> fieldTypes;
		FieldMethodTypes(boolean repeated, FieldType... fieldTypes) {
			this.repeated = repeated;
			this.fieldTypes = new HashSet<>(Arrays.asList(fieldTypes));
		}

		public String javaType() {
			return fieldTypes.stream().findAny().get().javaType(repeated);
		}

		public boolean matches(Field field) {
			if (field.type() == FieldType.MESSAGE) { // same method type for repeated and non-repeated
				return switch (field.messageType()) {
					case "StringValue" -> this == FieldMethodTypes.stringField;
					case "Int32Value" -> this == FieldMethodTypes.intField;
					case "UInt32Value" -> this == FieldMethodTypes.intField;
					case "SInt32Value" -> this == FieldMethodTypes.intField;
					case "Int64Value" -> this == FieldMethodTypes.longField;
					case "UInt64Value" -> this == FieldMethodTypes.longField;
					case "SInt64Value" -> this == FieldMethodTypes.longField;
					case "BoolValue" -> this == FieldMethodTypes.booleanField;
					case "BytesValue" -> this == FieldMethodTypes.bytesField;
					default -> fieldTypes.contains(field.type());
				};
			} if (field.repeated() && fieldTypes.contains(field.type()) &&
					(field.type() == FieldType.BYTES || field.type() == FieldType.STRING)) {
				return true;
			} else {
				return repeated == field.repeated() && fieldTypes.contains(field.type());
			}
		}
	}


	/**
	 * Generate the source code for field set methods that are callbacks from parser to build up local state inside
	 * parser class.
	 *
	 * @param fields list of all fields in record
	 * @return string of methods code
	 */
	private static String generateFieldSetMethods(final List<Field> fields) {
		// Flatten oneof fields
		final List<Field> flattenedFields = new ArrayList<>();
		for (var field: fields) {
			if (field instanceof OneOfField) {
				flattenedFields.addAll(((OneOfField)field).fields());
			} else {
				flattenedFields.add(field);
			}
		}
		return Arrays.stream(FieldMethodTypes.values())
				.filter(fieldMethodType -> flattenedFields.stream().filter(field -> fieldMethodType.matches(field)).count() > 0)
				.map(fieldMethodType ->
					"""	
						 	@Override
							public void %s(int fieldNum, %s input) %s{
								switch (fieldNum) {
									%s
									default -> throw new AssertionError("Not implemented in test code fieldNum='" + fieldNum + "'");
								}
							}
						""".formatted(
							fieldMethodType.toString(),
							switch (fieldMethodType) {
								case objectField -> "InputStream";
								default -> fieldMethodType.javaType();
							},
							fieldMethodType == FieldMethodTypes.objectField ? "throws IOException, MalformedProtobufException " : "",
							flattenedFields.stream()
									.filter(field -> fieldMethodType.matches(field))
									.map(field -> {
											return field.parserFieldsSetMethodCase();
									})
									.collect(Collectors.joining("\n            "))
							)
				)
				.collect(Collectors.joining("\n"));
	}
}
