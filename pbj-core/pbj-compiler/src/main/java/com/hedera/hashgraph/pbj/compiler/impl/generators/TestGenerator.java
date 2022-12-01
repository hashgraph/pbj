package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;
import static com.hedera.hashgraph.pbj.compiler.impl.FileAndPackageNamesConfig.TEST_JAVA_FILE_SUFFIX;

/**
 * Code generator that parses protobuf files and generates unit tests for each message type.
 */
public final class TestGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(Protobuf3Parser.MessageDefContext msgDef, File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final var modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final var testClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.TEST, msgDef);
		final var writerClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.WRITER, msgDef);
		final var parserClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.PARSER, msgDef);
		final String testPackage = lookupHelper.getPackageForMessage(FileType.TEST, msgDef);
		final String protoCJavaFullQualifiedClass = lookupHelper.getFullyQualifiedMessageClassname(FileType.PROTOC,msgDef);
		final File javaFile = getJavaFile(destinationTestSrcDir, testPackage, testClassName);
		final List<Field> fields = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		imports.add(lookupHelper.getPackageForMessage(FileType.MODEL, msgDef));
		imports.add(lookupHelper.getPackageForMessage(FileType.WRITER, msgDef));
		imports.add(lookupHelper.getPackageForMessage(FileType.PARSER, msgDef));
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, true, true);
				for(var subField : field.fields()) {
					subField.addAllNeededImports(imports, true, false, true, true);
				}
			} else if (item.mapField() != null) { // process map fields
				throw new IllegalStateException("Encountered a mapField that was not handled in "+ modelClassName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final var field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				if (field.type() == Field.FieldType.MESSAGE || field.type() == Field.FieldType.ENUM) {
					field.addAllNeededImports(imports, true, false, true, true);
				}
			} else if (item.reserved() == null && item.optionStatement() == null) {
				System.err.println("TestGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}
		imports.add("java.util");
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			javaWriter.write("""
					package %s;
									
					import org.junit.jupiter.params.ParameterizedTest;
					import org.junit.jupiter.params.provider.Arguments;
					import org.junit.jupiter.params.provider.MethodSource;
					import static org.junit.jupiter.api.Assertions.assertArrayEquals;
					import static org.junit.jupiter.api.Assertions.assertEquals;
					import static com.hedera.hashgraph.pbj.runtime.Utils.*;
					import java.io.ByteArrayOutputStream;
					import java.util.stream.Collectors;
					import java.util.stream.IntStream;
					import java.util.stream.Stream;
					import java.nio.ByteBuffer;
					%s
										
					/**
					 * Unit Test for %s model object. Generate based on protobuf schema.
					 */
					public final class %s {
						%s
						%s
					}
					""".formatted(
							testPackage,
						imports.isEmpty() ? "" : imports.stream()
								.filter(input -> !input.equals(testPackage))
								.collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
						modelClassName,
						testClassName,
						generateTestMethod(modelClassName, writerClassName, parserClassName, protoCJavaFullQualifiedClass)
								.replaceAll("\n","\n"+FIELD_INDENT),
						generateModelTestArgumentsMethod(modelClassName, fields)
								.replaceAll("\n","\n"+FIELD_INDENT)
					)
					//  final ProtoOutputStream pout = new ProtoOutputStream(%s::valid, out);
			);
		}
	}

	private static String generateModelTestArgumentsMethod(final String modelClassName, final List<Field> fields) {
		return """	
				/**
				 * Create a stream of all test permutations of the %s class we are testing. This is reused by other tests
				 * as well that have model objects with fields of this type.
				 *
				 * @return stream of model objects for all test cases
				 */
				public static Stream<%s> createModelTestArguments() {
					%s
					// work out the longest of all the lists of args as that is how many test cases we need
					final int maxValues = IntStream.of(
						%s
					).max().getAsInt();
					// create new stream of model objects using lists above as constructor params
					return IntStream.range(0,maxValues)
							.mapToObj(i -> new %s(
								%s
							));
				}
				""".formatted(
					modelClassName,
					modelClassName,
					fields.stream()
							.map(f -> "final var "+f.nameCamelFirstLower()+"List = "+generateTestData(modelClassName, f, f.optional(), f.repeated())+";")
							.collect(Collectors.joining("\n"+FIELD_INDENT)),
					fields.stream()
							.map(f -> f.nameCamelFirstLower()+"List.size()")
							.collect(Collectors.joining(",\n"+FIELD_INDENT+FIELD_INDENT)),
					modelClassName,
					fields.stream().map(field -> "%sList.get(Math.min(i, %sList.size()-1))".formatted(
								field.nameCamelFirstLower(),
								field.nameCamelFirstLower()
						))
							.collect(Collectors.joining(",\n"+FIELD_INDENT+FIELD_INDENT+FIELD_INDENT+FIELD_INDENT))
		);
	}

	private static String generateTestData(String modelClassName, Field field, boolean optional, boolean repeated) {
		if (optional) {
			Field.FieldType convertedFieldType = getOptionalConvertedFieldType(field);
			return """
					makeListOptionals(%s)"""
					.formatted(
							getOptionsForFieldType(convertedFieldType, convertedFieldType.javaType))
					.replaceAll("\n","\n"+FIELD_INDENT+FIELD_INDENT);
		} else if (repeated) {
			final String optionsList = generateTestData(modelClassName, field, field.optional(), false);
			return """
					generateListArguments(%s)""".formatted(optionsList)
					.replaceAll("\n","\n"+FIELD_INDENT+FIELD_INDENT);
		} else if(field instanceof final OneOfField oneOf) {
			final List<String> options = new ArrayList<>();
			for (var subField: oneOf.fields()) {
				if(subField instanceof SingleField) {
					final String enumValueName = camelToUpperSnake(subField.name());
					// special cases to break cyclic dependencies
					if (!("THRESHOLD_KEY".equals(enumValueName) || "KEY_LIST".equals(enumValueName)
							|| "THRESHOLD_SIGNATURE".equals(enumValueName)|| "SIGNATURE_LIST".equals(enumValueName))) {
						final String listStr;
						if (subField.optional()) {
							Field.FieldType convertedSubFieldType = getOptionalConvertedFieldType(subField);
							listStr = "makeListOptionals("+getOptionsForFieldType(convertedSubFieldType, convertedSubFieldType.javaType)+")";
						} else {
							listStr = getOptionsForFieldType(subField.type(), ((SingleField) subField).javaFieldTypeForTest());
						}
						options.add(listStr + """
										.stream()
										.map(value -> new OneOf<>(%sOneOfType.%s, value))
										.toList()""".formatted(
										modelClassName + "." + field.nameCamelFirstUpper(),
										enumValueName
								).replaceAll("\n", "\n" + FIELD_INDENT + FIELD_INDENT)
						);
					}
				} else {
					System.err.println("Did not expect a OneOfField in a OneOfField. In "+
							"modelClassName="+modelClassName+" field="+field+" subField="+subField);
				}
			}
			// TODO
			return """
					Stream.of(
						List.of(new OneOf<>(%sOneOfType.UNSET, null)),
						%s
					).flatMap(List::stream).toList()""".formatted(
							modelClassName+"."+field.nameCamelFirstUpper(),
							options.stream().collect(Collectors.joining(",\n"+FIELD_INDENT))
					).replaceAll("\n","\n"+FIELD_INDENT+FIELD_INDENT);
		} else {
			return getOptionsForFieldType(field.type(), ((SingleField)field).javaFieldTypeForTest());
		}
	}

	private static Field.FieldType getOptionalConvertedFieldType(final Field field) {
		return switch (field.messageType()) {
			case "StringValue" -> Field.FieldType.STRING;
			case "Int32Value" -> Field.FieldType.INT32;
			case "UInt32Value" -> Field.FieldType.UINT32;
			case "SInt32Value" -> Field.FieldType.SINT32;
			case "Int64Value" -> Field.FieldType.INT64;
			case "UInt64Value" -> Field.FieldType.UINT64;
			case "SInt64Value" -> Field.FieldType.SINT64;
			case "FloatValue" -> Field.FieldType.FLOAT;
			case "DoubleValue" -> Field.FieldType.DOUBLE;
			case "BoolValue" -> Field.FieldType.BOOL;
			case "BytesValue" -> Field.FieldType.BYTES;
			case "EnumValue" -> Field.FieldType.ENUM;
			default -> Field.FieldType.MESSAGE;
		};
	}

	private static String getOptionsForFieldType(Field.FieldType fieldType, String javaFieldType) {
		return switch (fieldType) {
			case INT32, SINT32 -> "List.of(Integer.MIN_VALUE, -42, -21, 0, 21, 42, Integer.MAX_VALUE)";
			case UINT32 -> "List.of(0, 1, 2, Integer.MAX_VALUE)";
			case INT64, SINT64 -> "List.of(Long.MIN_VALUE, -42L, -21L, 0L, 21L, 42L, Long.MAX_VALUE)";
			case UINT64 -> "List.of(0L, 21L, 42L, Long.MAX_VALUE)";
			case FLOAT, SFIXED32 ->
					"List.of(Float.NEGATIVE_INFINITY, Float.MIN_VALUE, -102.7f, -5f, 1.7f, 0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NaN)";
			case FIXED32 -> "List.of(0f, 3f, 5.2f, 42.1f, Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NaN)";
			case DOUBLE, SFIXED64 ->
					"List.of(Double.NEGATIVE_INFINITY, Double.MIN_VALUE, -102.7, -5, 1.7, 0d, 3, 5.2, 42.1, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN)";
			case FIXED64 -> "List.of(0d, 3, 5.2, 42.1, Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NaN)";
			case BOOL -> "List.of(true, false)";
			case STRING -> "List.of(\"\", \"Dude\")";
			case BYTES -> "List.of(ByteBuffer.wrap(new byte[0]).asReadOnlyBuffer(), ByteBuffer.wrap(new byte[]{0b001}).asReadOnlyBuffer(), ByteBuffer.wrap(new byte[]{0b001, 0b010, 0b011}).asReadOnlyBuffer())";
			case ENUM -> "Arrays.asList(" + javaFieldType + ".values())";
			case ONE_OF -> "List.of(null)"; // TODO something more comprehensive
			case MESSAGE -> javaFieldType + TEST_JAVA_FILE_SUFFIX + ".createModelTestArguments().toList()"; // TODO something more comprehensive
		};
	}

	private static String generateTestMethod(final String modelClassName, final String writerClassName,
											 final String parserClassName,
											 final String protoCJavaFullQualifiedClass) {
		return """
				@ParameterizedTest
				@MethodSource("createModelTestArguments")
				public void test%sAgainstProtoC(final %s modelObj) throws Exception {
					// model to bytes
					final ByteArrayOutputStream bout = new ByteArrayOutputStream();
					%s.write(modelObj,bout);
					bout.flush();
					final byte[] modelWriterBytes = bout.toByteArray();
					// read proto bytes with new parser
					final %s modelObj2 = new %s().parse(modelWriterBytes);
					assertEquals(modelObj, modelObj2);
					// read model with proto and compare bytes
					final %s protoModelObj2 = %s.parseFrom(modelWriterBytes);
					final byte[] protoBytes = protoModelObj2.toByteArray();
					//assertArrayEquals(modelWriterBytes, protoBytes);
					// read proto bytes with new parser
					final %s modelObj3 = new %s().parse(protoBytes);
					assertEquals(modelObj, modelObj3);
				}
				""".formatted(
				modelClassName,
				modelClassName,
				writerClassName,
				modelClassName,
				parserClassName,
				protoCJavaFullQualifiedClass,
				protoCJavaFullQualifiedClass,
				modelClassName,
				parserClassName
		);
	}
}
