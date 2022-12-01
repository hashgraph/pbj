package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;
import static com.hedera.hashgraph.pbj.compiler.impl.FileAndPackageNamesConfig.WRITER_JAVA_FILE_SUFFIX;

/**
 * Code generator that parses protobuf files and generates writers for each message type.
 */
public final class WriterGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final String schemaClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.SCHEMA, msgDef);
		final String writerClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.WRITER, msgDef);
		final String writerPackage = lookupHelper.getPackageForMessage(FileType.WRITER, msgDef);
		final File javaFile = getJavaFile(destinationSrcDir, writerPackage, writerClassName);
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		imports.add(lookupHelper.getPackageForMessage(FileType.MODEL, msgDef));
		imports.add(lookupHelper.getPackageForMessage(FileType.SCHEMA, msgDef));
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, true, false);
			} else if (item.mapField() != null) { // process map fields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ writerClassName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				if (field.type() == Field.FieldType.MESSAGE) {
					field.addAllNeededImports(imports, true, false, true, false);
				}
			} else if (item.reserved() == null && item.optionStatement() == null) {
				System.err.println("WriterGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}
		final List<Field> sortedFields = fields.stream()
				.sorted(Comparator.comparingInt(Field::fieldNumber))
				.collect(Collectors.toList());
		final String fieldWriteLines = generateFieldWriteLines(sortedFields, schemaClassName, imports);
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
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
						writerPackage,
						imports.isEmpty() ? "" : imports.stream()
								.filter(input -> !input.equals(writerPackage))
								.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
						lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef),
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
