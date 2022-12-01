package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.Field.FieldType;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;

/**
 * Code generator that parses protobuf files and generates nice parsers for each message type.
 */
@SuppressWarnings("DuplicatedCode")
public final class ParserGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(final Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final var modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final var parserClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.PARSER, msgDef);
		final String modelPackage = lookupHelper.getPackageForMessage(FileType.MODEL, msgDef);
		final String parserPackage = lookupHelper.getPackageForMessage(FileType.PARSER, msgDef);
		final File javaFile = getJavaFile(destinationSrcDir, parserPackage, parserClassName);
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		final List<String> oneOfUnsetConstants = new ArrayList<>();
		imports.add(modelPackage);
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, true, false, false);
				oneOfUnsetConstants.add(
						"    public static final OneOf<%s> %s = new OneOf<>(%s.UNSET,null);"
						.formatted(field.getEnumClassRef(),camelToUpperSnake(field.name())+"_UNSET", field.getEnumClassRef()));
			} else if (item.mapField() != null) { // process map fields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ parserClassName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, true, false, false);
			} else if (item.reserved() == null && item.optionStatement() == null) {
				System.err.println("ParserGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}

		try (FileWriter javaWriter = new FileWriter(javaFile)) {
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
						parserPackage,
						imports.isEmpty() ? "" : imports.stream()
								.filter(input -> !input.equals(parserPackage))
								.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
						lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef),
						modelClassName,
						parserClassName,
						String.join("\n", oneOfUnsetConstants),
						fields.stream().map(field -> "    private %s %s = %s;".formatted(field.javaFieldType(),
								field.name(), field.javaDefault())).collect(Collectors.joining("\n")),
						generateParseMethods(modelClassName, fields),
						generateGetFieldDefinition()+"\n"+generateResetMethod(fields),
						generateFieldSetMethods(fields)
					)
			);
		}
	}

	/**
	 * Generate get field definition method, it just delegates to the schema to get the answer
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
										.map(field -> "this.%s = %s;".formatted(field.name(), field.javaDefault()))
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
			final var fieldType = fieldTypes.stream().findAny();
			return fieldType.isEmpty() ? null : fieldType.get().javaType(repeated);
		}

		public boolean matches(Field field) {
			if (field.type() == FieldType.MESSAGE) { // same method type for repeated and non-repeated
				return switch (field.messageType()) {
					case "StringValue" -> this == FieldMethodTypes.stringField;
					case "Int32Value", "UInt32Value", "SInt32Value" -> this == FieldMethodTypes.intField;
					case "Int64Value", "UInt64Value", "SInt64Value" -> this == FieldMethodTypes.longField;
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
				.filter(fieldMethodType -> flattenedFields.stream().anyMatch(fieldMethodType::matches))
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
							(fieldMethodType == FieldMethodTypes.objectField) ? "InputStream" : fieldMethodType.javaType(),
							fieldMethodType == FieldMethodTypes.objectField ? "throws IOException, MalformedProtobufException " : "",
							flattenedFields.stream()
									.filter(fieldMethodType::matches)
									.map(Field::parserFieldsSetMethodCase)
									.collect(Collectors.joining("\n            "))
							)
				)
				.collect(Collectors.joining("\n"));
	}
}
