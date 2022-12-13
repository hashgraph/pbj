package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		final Stream<Field> sortedFieldsStream = fields.stream()
				.flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
				.sorted(Comparator.comparingInt(Field::fieldNumber));
		final String fieldWriteLines = sortedFieldsStream
				.map(field -> generateFieldWriteLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower()), imports))
				.collect(Collectors.joining("\n		"));
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write("""
					package $package;
									
					import com.hedera.hashgraph.pbj.runtime.io.DataOutput;
					import java.io.IOException;
					import java.io.OutputStream;
					$imports
					import static $schemaClass.*;
					import static com.hedera.hashgraph.pbj.runtime.ProtoWriterTools.*;
										
					/**
					 * Writer for $modelClass model object. Generate based on protobuf schema.
					 */
					public final class $writerClass {
						/**
						 * Write out a $modelClass model to output stream in protobuf format.
						 *
						 * @param data The input model data to write
						 * @param out The output stream to write to
						 * @throws IOException If there is a problem writing
						 */
						public static void write($modelClass data, DataOutput out) throws IOException {
							$fieldWriteLines
						}
					}
					"""
					.replace("$package", writerPackage)
					.replace("$imports", imports.isEmpty() ? "" : imports.stream()
							.filter(input -> !input.equals(writerPackage))
							.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")))
					.replace("$schemaClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef))
					.replace("$modelClass", modelClassName)
					.replace("$writerClass", writerClassName)
					.replace("$fieldWriteLines", fieldWriteLines)
			);
		}

		// TODO add checks back in before each field line if wanted
		//  assert $schemaClass.valid(field) : "Field " + field + " doesn't belong to the expected schema".
	}

	@SuppressWarnings("unused")
	private static String generateFieldWriteLines(final Field field, final String modelClassName, String getValueCode, final Set<String> imports) {
		final String fieldName = field.nameCamelFirstLower();
		final String fieldDef = camelToUpperSnake(field.name());
		String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
		prefix += "\n"+FIELD_INDENT.repeat(2);

		if (field.parent() != null) {
			final OneOfField oneOfField = field.parent();
			final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
			getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
			prefix += "if(data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
					camelToUpperSnake(field.name())+")";
			prefix += "\n"+FIELD_INDENT.repeat(3);
		}

		final String writeMethodName = mapToWriteMethod(field);
		if(field.optionalValueType()) {
			return prefix + switch (field.messageType()) {
				case "StringValue" -> "writeOptionalString(out, %s, %s);"
						.formatted(fieldDef,getValueCode);
				case "BoolValue" -> "writeOptionalBoolean(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case "Int32Value","UInt32Value" -> "writeOptionalInteger(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case "Int64Value","UInt64Value" -> "writeOptionalLong(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case "FloatValue" -> "writeOptionalFloat(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case "DoubleValue" -> "writeOptionalDouble(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case "BytesValue" -> "writeOptionalBytes(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
			};
		} else if (field.repeated()) {
			return prefix + switch(field.type()) {
				case ENUM -> "writeEnumList(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case MESSAGE -> "writeMessageList(out, %s, %s, %s::write);"
						.formatted(fieldDef,getValueCode,
								capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
						);
				default -> "write%sList(out, %s, %s);"
						.formatted(writeMethodName, fieldDef, getValueCode);
			};
		} else {
			return prefix + switch(field.type()) {
				case ENUM -> "writeEnum(out, %s, %s);"
						.formatted(fieldDef, getValueCode);
				case STRING -> "writeString(out, %s, %s);"
						.formatted(fieldDef,getValueCode);
				case MESSAGE -> "writeMessage(out, %s, %s, %s::write);"
						.formatted(fieldDef,getValueCode,
								capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
						);
				case BOOL -> "writeBoolean(out, %s, %s);"
						.formatted(fieldDef,getValueCode);
				default -> "write%s(out, %s, %s);"
						.formatted(writeMethodName, fieldDef, getValueCode);
			};
		}
	}

	private static String mapToWriteMethod(Field field) {
		return switch(field.type()) {
			case BOOL -> "Boolean";
			case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "Integer";
			case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> "Long";
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
