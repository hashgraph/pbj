package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.*;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.pbj.compiler.impl.FileAndPackageNamesConfig.WRITER_JAVA_FILE_SUFFIX;

/**
 * Code generator that parses protobuf files and generates writers for each message type.
 */
@SuppressWarnings("DuplicatedCode")
public final class WriterGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final String writerClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.WRITER, msgDef);
		final String writerPackage = lookupHelper.getPackageForMessage(FileType.WRITER, msgDef);
		final File javaFile = Common.getJavaFile(destinationSrcDir, writerPackage, writerClassName);
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
		final String fieldWriteLines = fields.stream()
				.flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
				.sorted(Comparator.comparingInt(Field::fieldNumber))
				.map(field -> generateFieldWriteLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
				.collect(Collectors.joining("\n		"));
		final String fieldSizeOfLines = fields.stream()
				.flatMap(field -> field.type() == Field.FieldType.ONE_OF ? ((OneOfField)field).fields().stream() : Stream.of(field))
				.sorted(Comparator.comparingInt(Field::fieldNumber))
				.map(field -> generateFieldSizeOfLines(field, modelClassName, "data.%s()".formatted(field.nameCamelFirstLower())))
				.collect(Collectors.joining("\n		"));
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write("""
					package $package;
									
					import com.hedera.pbj.runtime.io.DataOutput;
					import java.io.IOException;
					import java.io.OutputStream;
					$imports
					import static $schemaClass.*;
					import static com.hedera.pbj.runtime.ProtoWriterTools.*;
										
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
						
						/**
						 * Compute number of bytes that would be written when calling {@code write()} method.
						 *
						 * @param data The input model data to measure write bytes for
						 * @return size in bytes that would be written
						 */
						public static int sizeOf($modelClass data) {
							int size = 0;
							$fieldSizeOfLines
							return size;
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
					.replace("$fieldSizeOfLines", fieldSizeOfLines)
			);
		}
	}

	/**
	 * Generate lines of code for writing field
	 *
	 * @param field The field to generate writing line of code for
	 * @param modelClassName The model class name for model class for message type we are generating writer for
	 * @param getValueCode java code to get the value of field
	 * @return java code to write field to output
	 */
	private static String generateFieldWriteLines(final Field field, final String modelClassName, String getValueCode) {
		final String fieldDef = Common.camelToUpperSnake(field.name());
		String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
		prefix += "\n"+ Common.FIELD_INDENT.repeat(2);

		if (field.parent() != null) {
			final OneOfField oneOfField = field.parent();
			final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
			getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
			prefix += "if(data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
					Common.camelToUpperSnake(field.name())+")";
			prefix += "\n"+ Common.FIELD_INDENT.repeat(3);
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
				case MESSAGE -> "writeMessageList(out, %s, %s, %s::write, %s::sizeOf);"
						.formatted(fieldDef,getValueCode,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
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
				case MESSAGE -> "writeMessage(out, %s, %s, %s::write, %s::sizeOf);"
						.formatted(fieldDef,getValueCode,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
						);
				case BOOL -> "writeBoolean(out, %s, %s);"
						.formatted(fieldDef,getValueCode);
				default -> "write%s(out, %s, %s);"
						.formatted(writeMethodName, fieldDef, getValueCode);
			};
		}
	}

	/**
	 * Generate lines of code for size of method, that measure the size of each field and add to "size" variable.
	 *
	 * @param field The field to generate size of line
	 * @param modelClassName The model class name for model class for message type we are generating writer for
	 * @param getValueCode java code to get the value of field
	 * @return java code for adding fields size to "size" variable
	 */
	private static String generateFieldSizeOfLines(final Field field, final String modelClassName, String getValueCode) {
		final String fieldDef = Common.camelToUpperSnake(field.name());
		String prefix = "// ["+field.fieldNumber()+"] - "+field.name();
		prefix += "\n"+ Common.FIELD_INDENT.repeat(2);

		if (field.parent() != null) {
			final OneOfField oneOfField = field.parent();
			final String oneOfType = modelClassName+"."+oneOfField.nameCamelFirstUpper()+"OneOfType";
			getValueCode = "data."+oneOfField.nameCamelFirstLower()+"().as()";
			prefix += "if(data."+oneOfField.nameCamelFirstLower()+"().kind() == "+ oneOfType +"."+
					Common.camelToUpperSnake(field.name())+")";
			prefix += "\n"+ Common.FIELD_INDENT.repeat(3);
		}

		final String writeMethodName = mapToWriteMethod(field);
		if(field.optionalValueType()) {
			return prefix + switch (field.messageType()) {
				case "StringValue" -> "size += sizeOfOptionalString(%s, %s);"
						.formatted(fieldDef,getValueCode);
				case "BoolValue" -> "size += sizeOfOptionalBoolean(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case "Int32Value","UInt32Value" -> "size += sizeOfOptionalInteger(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case "Int64Value","UInt64Value" -> "size += sizeOfOptionalLong(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case "FloatValue" -> "size += sizeOfOptionalFloat(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case "DoubleValue" -> "size += sizeOfOptionalDouble(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case "BytesValue" -> "size += sizeOfOptionalBytes(%s, %s);"
						.formatted(fieldDef, getValueCode);
				default -> throw new UnsupportedOperationException("Unhandled optional message type:"+field.messageType());
			};
		} else if (field.repeated()) {
			return prefix + switch(field.type()) {
				case ENUM -> "size += sizeOfEnumList(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case MESSAGE -> "size += sizeOfMessageList(%s, %s, %s::sizeOf);"
						.formatted(fieldDef,getValueCode,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
						);
				default -> "size += sizeOf%sList(%s, %s);"
						.formatted(writeMethodName, fieldDef, getValueCode);
			};
		} else {
			return prefix + switch(field.type()) {
				case ENUM -> "size += sizeOfEnum(%s, %s);"
						.formatted(fieldDef, getValueCode);
				case STRING -> "size += sizeOfString(%s, %s);"
						.formatted(fieldDef,getValueCode);
				case MESSAGE -> "size += sizeOfMessage(%s, %s, %s::sizeOf);"
						.formatted(fieldDef,getValueCode,
								Common.capitalizeFirstLetter(field.messageType())+ WRITER_JAVA_FILE_SUFFIX
						);
				case BOOL -> "size += sizeOfBoolean(%s, %s);"
						.formatted(fieldDef,getValueCode);
				default -> "size += sizeOf%s(%s, %s);"
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
