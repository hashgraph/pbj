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
@SuppressWarnings({"DuplicatedCode", "StringConcatenationInsideStringBufferAppend"})
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
					package $parserPackage;
					
					import com.hedera.hashgraph.pbj.runtime.io.*;
					$extraImports
					
					import java.io.IOException;
					import java.nio.ByteOrder;
					import java.nio.charset.StandardCharsets;
					import java.util.Arrays;
					
					import $modelClass;
					import static $schemaClass.getField;
					import static com.hedera.hashgraph.pbj.runtime.ProtoParserTools.*;

					/**
					 * Parser for $modelClassName model object from protobuf format
					 */
					public class $parserClassName {
					$unsetConstants
					$parseMethod
					}
					"""
					.replace("$parserPackage", parserPackage)
					.replace("$extraImports", imports.isEmpty() ? "" : imports.stream()
							.filter(input -> !input.equals(parserPackage))
							.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")))
					.replace("$modelClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef))
					.replace("$schemaClass", lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef))
					.replace("$modelClassName", modelClassName)
					.replace("$parserClassName", parserClassName)
					.replace("$unsetConstants", String.join("\n", oneOfUnsetConstants))
					.replace("$parseMethod", generateParseMethod(modelClassName, fields))
			);
		}
	}

	/**
	 * Generate source code for parse methods
	 *
	 * @param modelClassName the class name for model class that parse methods will return
	 * @param fields list of all the fields
	 * @return string of source code for parse methods
	 */
	private static String generateParseMethod(final String modelClassName, final List<Field> fields) {
		return FIELD_INDENT + """
						/**
						 * Parses a $modelClassName object from ProtoBuf bytes in a DataInput
						 *
						 * @param input The data input to parse data from, it is assumed to be in a state ready to read with position at start
						 *              of data to read and limit set at the end of data to read. The data inputs limit will be changed by this
						 *              method. If null, the method returns immediately. If there are no bytes remaining in the data input,
						 *              then the method also returns immediately.
						 * @return Parsed $modelClassName model object or null if data input was null or empty
						 * @throws IOException If the protobuf stream is not empty and has malformed
						 * 									  protobuf bytes (i.e. isn't valid protobuf).
						 */
						public static $modelClassName parse(DataInput input) throws IOException {
							// If protobuf stream is null, then return null (valid protobuf encoding can be 0+ tag/value pairs)
							if (input == null) {
								return null;
							}
					
							// -- TEMP STATE FIELDS --------------------------------------
						$fieldDefs

							// -- PARSE LOOP ---------------------------------------------
							// Continue to parse bytes out of the input stream until we get to the end.
							while (input.hasRemaining()) {
								// Read the "tag" byte which gives us the field number for the next field to read
								// and the wire type (way it is encoded on the wire).
								final int tag = input.readVarInt(false);
					
								// The field is the top 5 bits of the byte. Read this off
								final int field = tag >>> TAG_FIELD_OFFSET;
					
								// Ask the Schema to inform us what field this represents.
								final var f = getField(field);
								
								// Given the wire type and the field type, parse the field
								switch (tag) {
									$caseStatements
									default -> {
										// The wire type is the bottom 3 bits of the byte. Read that off
										final int wireType = tag & TAG_WRITE_TYPE_MASK;
										// handle error cases here, so we do not do if statements in normal loop
										// Validate the field number is valid (must be > 0)
										if (field == 0) {
											throw new IOException("Bad protobuf encoding. We read a field value of " + field);
										}
										// Validate the wire type is valid (must be >=0 && <= 5). Otherwise we cannot parse this.
										// Note: it is always >= 0 at this point (see code above where it is defined).
										if (wireType > 5) {
											throw new IOException("Cannot understand wire_type of " + wireType);
										}
										// It may be that the parser subclass doesn't know about this field. In that case, we
										// just need to read off the bytes for this field to skip it and move on to the next one.
										if (f == null) {
											skipField(input, wireType);
										} else {
											throw new IOException("Bad tag ["+tag+"], field [" + field + "] wireType [" + wireType + "]");
										}
									}
								}
							}
							return new $modelClassName($fieldsList);
						}"""
				.replace("$modelClassName",modelClassName)
				.replace("$fieldDefs",fields.stream().map(field -> "    %s temp_%s = %s;".formatted(field.javaFieldType(),
						field.name(), field.javaDefault())).collect(Collectors.joining("\n")))
				.replace("$fieldsList",fields.stream().map(field -> "temp_"+field.name()).collect(Collectors.joining(", ")))
				.replace("$caseStatements",generateCaseStatements(fields))
				.replaceAll("\n", "\n" + FIELD_INDENT);
	}

	/**
	 * Generate switch case statements for each tag (field & wire type pair). For repeated numeric value types we
	 * generate 2 case statements for packed and unpacked encoding.
	 *
	 * @param fields list of all fields in record
	 * @return string of case statement code
	 */
	private static String generateCaseStatements(final List<Field> fields) {
		StringBuilder sb = new StringBuilder();
		for(Field field: fields) {
			if (field instanceof final OneOfField oneOfField) {
				for(final Field subField: oneOfField.fields()) {
					generateFieldCaseStatement(sb,subField);
				}
			} else if (field.repeated() && field.type().wireType() != TYPE_LENGTH_DELIMITED) {
				// for repeated fields that are not length encoded there are 2 forms they can be stored in file.
				// "packed" and repeated primitive fields
				generateFieldCaseStatement(sb, field);
				generateFieldCaseStatementPacked(sb, field);
			} else {
				generateFieldCaseStatement(sb, field);
			}
		}
		return sb.toString().replaceAll("\n","\n" + FIELD_INDENT.repeat(3));
	}

	/**
	 * Generate switch case statement for a repeated numeric value type in packed encoding.
	 *
	 * @param field field to generate case statement for
	 * @param sb StringBuilder to append code to
	 */
	private static void generateFieldCaseStatementPacked(final StringBuilder sb, final Field field) {
		final int wireType = TYPE_LENGTH_DELIMITED;
		final int fieldNum = field.fieldNumber();
		final int tag = getTag(wireType, fieldNum);
		sb.append("case " + tag +" /* type=" + wireType + " [" + field.type() + "] packed-repeated " +
				"field=" + fieldNum + " [" + field.name() + "] */ -> {\n");
		sb.append(FIELD_INDENT + """
				// Read the length of packed repeated field data
				final int length = input.readVarInt(false);
				final long beforeLimit = input.getLimit();
				input.setLimit(input.getPosition() + length);
				while (input.hasRemaining()) {
					$tempFieldName = addToList($tempFieldName,$readMethod);
				}
				input.setLimit(beforeLimit);"""
				.replace("$tempFieldName", "temp_" + field.name())
				.replace("$readMethod", readMethod(field))
				.replaceAll("\n","\n" + FIELD_INDENT)
		);
		sb.append("\n}\n");
	}

	/**
	 * Generate switch case statement for a field.
	 *
	 * @param field field to generate case statement for
	 * @param sb StringBuilder to append code to
	 */
	private static void generateFieldCaseStatement(final StringBuilder sb, final Field field) {
		final int wireType = field.optionalValueType() ? TYPE_LENGTH_DELIMITED : field.type().wireType();
		final int fieldNum = field.fieldNumber();
		final int tag = getTag(wireType, fieldNum);
		sb.append("case " + tag +" /* type=" + wireType + " [" + field.type() + "] " +
				"field=" + fieldNum + " [" + field.name() + "] */ -> {\n");
		if (field.optionalValueType()) {
			sb.append(FIELD_INDENT + """
							// Read the message size, it is not needed
							final int valueTypeMessageSize = input.readVarInt(false);
							final $fieldType value;
							if (valueTypeMessageSize > 0) {
								final long beforeLimit = input.getLimit();
								input.setLimit(input.getPosition() + valueTypeMessageSize);
								// read inner tag
								final int valueFieldTag = input.readVarInt(false);
								// assert tag is as expected
								assert (valueFieldTag >>> TAG_FIELD_OFFSET) == 1;
								assert (valueFieldTag & TAG_WRITE_TYPE_MASK) == $valueTypeWireType;
								// read value
								value = Optional.of($readMethod);
								input.setLimit(beforeLimit);
							} else {
								// means optional is default value
								value = $defaultValue;
							}"""
					.replace("$fieldType", field.javaFieldType())
					.replace("$readMethod", readMethod(field))
					.replace("$defaultValue",
							switch (field.messageType()) {
								case "Int32Value", "UInt32Value" -> "Optional.of(0)";
								case "Int64Value", "UInt64Value" -> "Optional.of(0l)";
								case "FloatValue" -> "Optional.of(0f)";
								case "DoubleValue" -> "Optional.of(0d)";
								case "BoolValue" -> "Optional.of(false)";
								case "BytesValue" -> "Optional.of(Bytes.EMPTY_BYTES)";
								case "StringValue" -> "Optional.of(\"\")";
								default -> throw new PbjCompilerException("Unexpected and unknown field type " + field.type() + " cannot be parsed");
							})
					.replace("$valueTypeWireType", Integer.toString(
							switch (field.messageType()) {
								case "StringValue", "BytesValue" -> TYPE_LENGTH_DELIMITED;
								case "Int32Value", "UInt32Value", "Int64Value", "UInt64Value", "BoolValue" -> TYPE_VARINT;
								case "FloatValue" -> TYPE_FIXED32;
								case "DoubleValue" -> TYPE_FIXED64;
								default -> throw new PbjCompilerException("Unexpected and unknown field type " + field.type() + " cannot be parsed");
							}))
					.replaceAll("\n","\n" + FIELD_INDENT)
			);
			sb.append('\n');
		} else if (field.type() == FieldType.MESSAGE){
			sb.append(FIELD_INDENT + """
						final int messageLength = input.readVarInt(false);
						final long limitBefore = input.getLimit();
						input.setLimit(input.getPosition() + messageLength);
						final var value = $readMethod;
						input.setLimit(limitBefore);"""
					.replace("$readMethod", readMethod(field))
					.replaceAll("\n", "\n" + FIELD_INDENT)
			);
		} else {
			sb.append(FIELD_INDENT + "final var value = " + readMethod(field) + ";\n");
		}
		// set value to temp var
		sb.append(FIELD_INDENT);
		if (field.parent() != null && field.repeated()) {
			throw new PbjCompilerException("Fields can not be oneof and repeated ["+field+"]");
		} else if (field.parent() != null) {
			final var oneOfField = field.parent();
			sb.append("temp_" + oneOfField.name() + " =  new OneOf<>(" +
					oneOfField.getEnumClassRef() + '.' + camelToUpperSnake(field.name()) + ", value);\n");
		} else if (field.repeated()) {
			sb.append("temp_" + field.name() + " = addToList(temp_" + field.name() + ",value);\n");
		} else {
			sb.append("temp_" + field.name() + " = value;\n");
		}
		sb.append("}\n");
	}

	private static String readMethod(Field field) {
		if (field.optionalValueType()) {
			return switch (field.messageType()) {
				case "StringValue" -> "readString(input)";
				case "Int32Value" -> "readInt32(input)";
				case "UInt32Value" -> "readUint32(input)";
				case "Int64Value" -> "readInt64(input)";
				case "UInt64Value" -> "readUint64(input)";
				case "FloatValue" -> "readFloat(input)";
				case "DoubleValue" -> "readDouble(input)";
				case "BoolValue" -> "readBool(input)";
				case "BytesValue" -> "readBytes(input)";
				default -> throw new PbjCompilerException("Optional message type [" + field.messageType() + "] not supported");
			};
		}
		return switch(field.type()) {
			case ENUM ->  snakeToCamel(field.messageType(), true) + ".fromProtobufOrdinal(readEnum(input))";
			case INT32 -> "readInt32(input)";
			case UINT32 -> "readUint32(input)";
			case SINT32 -> "readSignedInt32(input)";
			case INT64 -> "readInt64(input)";
			case UINT64 -> "readUint64(input)";
			case SINT64 -> "readSignedInt64(input)";
			case FLOAT -> "readFloat(input)";
			case FIXED32 -> "readFixed32(input)";
			case SFIXED32 -> "readSignedFixed32(input)";
			case DOUBLE -> "readDouble(input)";
			case FIXED64 -> "readFixed64(input)";
			case SFIXED64 -> "readSignedFixed64(input)";
			case STRING -> "readString(input)";
			case BOOL -> "readBool(input)";
			case BYTES -> "readBytes(input)";
			case MESSAGE -> field.parserClass() + ".parse(input)";
			case ONE_OF -> throw new PbjCompilerException("Should never happen, oneof handled else where");
		};
	}
}
