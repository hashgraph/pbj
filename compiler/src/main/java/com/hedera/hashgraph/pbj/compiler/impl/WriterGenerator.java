package com.hedera.hashgraph.pbj.compiler.impl;

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
import java.util.*;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;
import static com.hedera.hashgraph.pbj.compiler.impl.SchemaGenerator.SCHEMA_JAVA_FILE_SUFFIX;

/**
 * Code generator that parses protobuf files and generates writers for each message type.
 */
public class WriterGenerator {

	/** Suffix for schema java classes */
	public static final String WRITER_JAVA_FILE_SUFFIX = "Writer";

	/**
	 * Main generate method that process directory of protobuf files
	 *
	 * @param protoDir The protobuf file to parse
	 * @param destinationSrcDir the generated source directory to write files into
	 * @param lookupHelper helper for global context
	 * @throws IOException if there was a problem writing files
	 */
	public static void generateWriters(File protoDir, File destinationSrcDir, final LookupHelper lookupHelper) throws IOException {
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
			for (final File file : Objects.requireNonNull(protoDirOrFile.listFiles())) {
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
				final String javaPackage = computeJavaPackage(lookupHelper.getWriterPackage(), dirName);
				final Path packageDir = destinationSrcDir.toPath().resolve(javaPackage.replace('.', '/'));
				Files.createDirectories(packageDir);
				for (var topLevelDef : parsedDoc.topLevelDef()) {
					final Protobuf3Parser.MessageDefContext msgDef = topLevelDef.messageDef();
					if (msgDef != null) {
						generateWriterFile(msgDef, dirName, javaPackage, packageDir, lookupHelper);
					}
				}
			}
		}
	}

	/**
	 * Generate a Java writer class from protobuf message type
	 *
	 * @param msgDef the parsed message
	 * @param dirName the directory name of the dir containing the protobuf file
	 * @param javaPackage the java package the writer file should be generated in
	 * @param packageDir the output package directory
	 * @param lookupHelper helper for global context
	 * @throws IOException If there was a problem writing record file
	 */
	private static void generateWriterFile(Protobuf3Parser.MessageDefContext msgDef, String dirName, String javaPackage,
			Path packageDir, final LookupHelper lookupHelper) throws IOException {
		final var modelClassName = msgDef.messageName().getText();
		final var schemaClassName = modelClassName+ SCHEMA_JAVA_FILE_SUFFIX;
		final var writerClassName = modelClassName+ WRITER_JAVA_FILE_SUFFIX;
		final var javaFile = packageDir.resolve(writerClassName + ".java");
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		imports.add(computeJavaPackage(lookupHelper.getModelPackage(), dirName));
		imports.add(computeJavaPackage(lookupHelper.getSchemaPackage(), dirName));
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generateWriterFile(item.messageDef(), dirName, javaPackage,packageDir,lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, true, false);
			} else if (item.mapField() != null) { // process map fields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ writerClassName);
//			} else if (item.reserved() != null) { // process reserved - not needed
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				if (field.type() == Field.FieldType.MESSAGE) {
					field.addAllNeededImports(imports, true, false, true, false);
				}
//			} else if (item.optionStatement() != null){ // no needed for now
			} else {
				System.err.println("Unknown Element: "+item+" -- "+item.getText());
			}
		}
		final List<Field> sortedFields = fields.stream()
				.sorted((a,b) -> Integer.compare(a.fieldNumber(), b.fieldNumber()))
				.collect(Collectors.toList());
		final String fieldWriteLines = generateFieldWriteLines(sortedFields, schemaClassName, imports);
		try (FileWriter javaWriter = new FileWriter(javaFile.toFile())) {
			javaWriter.write("""
					package %s;
									
					import java.io.IOException;
					import java.io.OutputStream;
					import com.hedera.hashgraph.pbj.runtime.ProtoOutputStream;
					%s
					import static %s.*;
										
					/**
					 * Writer for %s model object. Generate based on protobuf schema.
					 */
					public final class %s {
						/**
						 * Write out a %s model to output stream in protobuf format.
						 *
						 * @param data The input model data to write
						 * @param out The output stream to write to
						 * @throws IOException If there is a problem writing
						 */
						public static void write(%s data, OutputStream out) throws IOException {
							final ProtoOutputStream pout = new ProtoOutputStream(%s::valid,out);
							%s
						}
					}
					""".formatted(
						javaPackage,
						imports.isEmpty() ? "" : imports.stream()
								.filter(input -> !input.equals(javaPackage))
								.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
						computeJavaPackage(lookupHelper.getSchemaPackage(), dirName)+"."+schemaClassName,
						modelClassName,
						writerClassName,
						modelClassName,
						modelClassName,
						schemaClassName,
						fieldWriteLines
					)
					//  final ProtoOutputStream pout = new ProtoOutputStream(%s::valid, out);
			);
		}
	}

	private static String generateFieldWriteLines(final List<Field> fields, String schemaClassName,final Set<String> imports) {
		return fields.stream()
				.map(field -> generateFieldWriteLines(field, schemaClassName, "data.%s()".formatted(field.nameCamelFirstLower()), imports))
				.collect(Collectors.joining("\n		"));
	}

	@SuppressWarnings("unused")
	private static String generateFieldWriteLines(final Field field, final String schemaClassName, final String getValueCode, final Set<String> imports) {
		final String fieldName = field.nameCamelFirstLower();
		final String fieldDef = camelToUpperSnake(field.name());
		if (field instanceof final OneOfField oneOfField) {
			final String oneOfName = field.name()+"OneOf";
			return """
					final var %s = data.%s();
					switch(%s.kind()) {
					%s
					}""".formatted(
					oneOfName,fieldName,oneOfName,
					oneOfField.fields().stream().map(f ->
							 			FIELD_INDENT+"case %s -> %s"
									.formatted(camelToUpperSnake(f.name()), generateFieldWriteLines(f,schemaClassName,"%s.as()".formatted(oneOfName), imports)))
							.collect(Collectors.joining("\n"))

			).replaceAll("\n","\n		");
		} else {
			final String writeMethodName = mapToWriteMethod(field);
			if(field.optional()) {
				return switch (field.messageType()) {
					case "EnumValue" -> "pout.writeOptionalEnum(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "StringValue" -> "pout.writeOptionalString(%s, %s);"
							.formatted(fieldDef,getValueCode);
					case "BoolValue" -> "pout.writeOptionalBoolean(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "Int32Value","UInt32Value","SInt32Value" -> "pout.writeOptionalInteger(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "Int64Value","UInt64Value","SInt64Value" -> "pout.writeOptionalLong(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "FloatValue" -> "pout.writeOptionalFloat(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "DoubleValue" -> "pout.writeOptionalDouble(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case "BytesValue" -> "pout.writeOptionalBytes(%s, %s);"
							.formatted(fieldDef, getValueCode);
					default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
				};
			} else if (field.repeated()) {
				return switch(field.type()) {
					case ENUM -> "pout.writeEnumList(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case MESSAGE -> "pout.writeMessageList(%s, %s, %s::write);"
							.formatted(fieldDef,getValueCode,
									capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
							);
					default -> "pout.write%sList(%s, %s);"
							.formatted(writeMethodName, fieldDef, getValueCode);
				};
			} else {
				return switch(field.type()) {
					case ENUM -> "pout.writeEnum(%s, %s);"
							.formatted(fieldDef, getValueCode);
					case STRING -> "pout.writeString(%s, %s);"
							.formatted(fieldDef,getValueCode);
					case MESSAGE -> "pout.writeMessage(%s, %s, %s::write);"
							.formatted(fieldDef,getValueCode,
									capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
							);
					case BOOL -> "pout.writeBoolean(%s, %s);"
							.formatted(fieldDef,getValueCode);
					default -> "pout.write%s(%s, %s);"
							.formatted(writeMethodName, fieldDef, getValueCode);
				};
			}
		}
	}

	private static String mapToWriteMethod(Field field) {
		return switch(field.type()) {
			case BOOL -> "Boolean";
			case INT32, UINT32, SINT32 -> "Integer";
			case INT64, SINT64, UINT64 -> "Long";
			case FLOAT -> "Float";
			case DOUBLE -> "Double";
			case MESSAGE -> "Message";
			case STRING -> "String";
			case ENUM -> "Enum";
			case BYTES -> "Bytes";
			default -> throw new UnsupportedOperationException("mapToWriteMethod can not handle "+field.type());
		};
	}
}
