package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.*;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code generator that parses protobuf files and generates schemas for each message type.
 */
public final class SchemaGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(final Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final String schemaClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.SCHEMA, msgDef);
		final String parserClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.PARSER, msgDef);
		final String schemaPackage = lookupHelper.getPackageForMessage(FileType.SCHEMA, msgDef);
		final File javaFile = Common.getJavaFile(destinationSrcDir, schemaPackage, schemaClassName);
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, false, false);
			} else if (item.mapField() != null) { // process map flattenedFields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ parserClassName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
			} else if (item.reserved() == null && item.optionStatement() == null) {
				// we can ignore reserved and option statements for now
				System.err.println("SchemaGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}

		final List<Field> flattenedFields = fields.stream()
				.flatMap(field -> field instanceof OneOfField ? ((OneOfField)field).fields().stream() :
						Stream.of(field))
				.collect(Collectors.toList());

		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write("""
					package $schemaPackage;
										
					import com.hedera.pbj.runtime.FieldDefinition;
					import com.hedera.pbj.runtime.FieldType;
					import com.hedera.pbj.runtime.Schema;
					$imports
										
					/**
					 * Schema for $modelClassName model object. Generate based on protobuf schema.
					 */
					public final class $schemaClassName implements Schema {
					
						// -- FIELD DEFINITIONS ---------------------------------------------
						
					$fields
										
						// -- OTHER METHODS -------------------------------------------------
						
						/**
						 * Check if a field definition belongs to this schema.
						 *
						 * @param f field def to check
						 * @return true if it belongs to this schema
						 */
						public static boolean valid(FieldDefinition f) {
							return f != null && getField(f.number()) == f;
						}
						
					$getMethods
					}
					"""
					.replace("$schemaPackage", schemaPackage)
					.replace("$imports", imports.isEmpty() ? "" : imports.stream()
							.filter(input -> !input.equals(schemaPackage))
							.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")))
					.replace("$modelClassName", modelClassName)
					.replace("$schemaClassName", schemaClassName)
					.replace("$fields", fields.stream().map(
							Field::schemaFieldsDef
					).collect(Collectors.joining("\n\n")))
					.replace("$getMethods", generateGetField(flattenedFields))
			);
		}
	}

	/**
	 * Generate getField method to get a field definition given a field number
	 *
	 * @param flattenedFields flattened list of all fields, with oneofs flattened
	 * @return source code string for getField method
	 */
	private static String generateGetField(final List<Field> flattenedFields) {
		return 	"""		
					/**
					 * Get a field definition given a field number
					 *
					 * @param fieldNumber the fields number to get def for
					 * @return field def or null if field number does not exist
					 */
					public static FieldDefinition getField(final int fieldNumber) {
						return switch(fieldNumber) {
						    %s
							default -> null;
						};
					}
				""".formatted(flattenedFields.stream()
											.map(Field::schemaGetFieldsDefCase)
											.collect(Collectors.joining("\n            ")));
	}

}
