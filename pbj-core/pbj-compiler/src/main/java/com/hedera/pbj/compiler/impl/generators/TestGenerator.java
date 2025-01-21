// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileAndPackageNamesConfig;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
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
 * Code generator that parses protobuf files and generates unit tests for each message type.
 */
public final class TestGenerator implements Generator {

    private static final List<String> CIRCULAR_DEPENDENCIES_FIELDS =
            List.of("THRESHOLD_KEY", "KEY_LIST", "THRESHOLD_SIGNATURE", "SIGNATURE_LIST", "ATOMIC_BATCH");

    /**
     * {@inheritDoc}
     */
    public void generate(
            Protobuf3Parser.MessageDefContext msgDef,
            File destinationSrcDir,
            File destinationTestSrcDir,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final var modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final var testClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.TEST, msgDef);
        final String testPackage = lookupHelper.getPackageForMessage(FileType.TEST, msgDef);
        final String protoCJavaFullQualifiedClass =
                lookupHelper.getFullyQualifiedMessageClassname(FileType.PROTOC, msgDef);
        final File javaFile = Common.getJavaFile(destinationTestSrcDir, testPackage, testClassName);
        final List<Field> fields = new ArrayList<>();
        final Set<String> imports = new TreeSet<>();
        imports.add("com.hedera.pbj.runtime.io.buffer");
        imports.add(lookupHelper.getPackageForMessage(FileType.MODEL, msgDef));
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
            } else if (item.oneof() != null) { // process one ofs
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(imports, true, false, true);
                for (var subField : field.fields()) {
                    subField.addAllNeededImports(imports, true, false, true);
                }
            } else if (item.mapField() != null) { // process map fields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(imports, true, false, true);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
                if (field.type() == Field.FieldType.MESSAGE || field.type() == Field.FieldType.ENUM) {
                    field.addAllNeededImports(imports, true, false, true);
                }
            } else if (item.reserved() == null && item.optionStatement() == null) {
                System.err.printf("TestGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }
        imports.add("java.util");
        // spotless:off
        try (FileWriter javaWriter = new FileWriter(javaFile)) {
            javaWriter.write(
                    """
                    package %s;
                    
                    import com.google.protobuf.util.JsonFormat;
                    import com.google.protobuf.CodedOutputStream;
                    import com.hedera.pbj.runtime.io.buffer.BufferedData;
                    import com.hedera.pbj.runtime.JsonTools;
                    import org.junit.jupiter.api.Test;
                    import org.junit.jupiter.params.ParameterizedTest;
                    import org.junit.jupiter.params.provider.MethodSource;
                    import com.hedera.pbj.runtime.test.*;
                    import java.util.stream.IntStream;
                    import java.util.stream.Stream;
                    import java.nio.ByteBuffer;
                    import java.nio.CharBuffer;
                    %s
                    
                    import com.google.protobuf.CodedInputStream;
                    import com.google.protobuf.WireFormat;
                    import java.io.IOException;
                    import java.nio.charset.StandardCharsets;
                    
                    import static com.hedera.pbj.runtime.ProtoTestTools.*;
                    import static org.junit.jupiter.api.Assertions.*;
                    
                    /**
                     * Unit Test for %s model object. Generate based on protobuf schema.
                     */
                    public final class %s {
                    %s
                    %s
                    }
                    """.formatted(testPackage, imports.isEmpty() ? "" : imports.stream()
                                    .filter(input -> !input.equals(testPackage))
                                    .collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
                            modelClassName, testClassName,
                            generateTestMethod(modelClassName, protoCJavaFullQualifiedClass).indent(DEFAULT_INDENT),
                            generateModelTestArgumentsMethod(modelClassName, fields).indent(DEFAULT_INDENT)
                    )
            );
        }
        // spotless:on
    }

    private static String generateModelTestArgumentsMethod(final String modelClassName, final List<Field> fields) {
        // spotless:off
        return
                """
                /**
                 * List of all valid arguments for testing, built as a static list, so we can reuse it.
                 */
                public static final List<%s> ARGUMENTS;
                
                static {
                %s
                    // work out the longest of all the lists of args as that is how many test cases we need
                    final int maxValues = IntStream.of(
                %s
                    ).max().getAsInt();
                    // create new stream of model objects using lists above as constructor params
                    ARGUMENTS = (maxValues > 0 ? IntStream.range(0, maxValues) : IntStream.of(0))
                            .mapToObj(i -> new %s(
                %s
                            )).toList();
                }
                
                /**
                 * Create a stream of all test permutations of the %s class we are testing. This is reused by other tests
                 * as well that have model objects with fields of this type.
                 *
                 * @return stream of model objects for all test cases
                 */
                public static Stream<NoToStringWrapper<%s>> createModelTestArguments() {
                    return ARGUMENTS.stream().map(NoToStringWrapper::new);
                }
                """.formatted(modelClassName,fields.stream()
                                .filter(field -> !field.javaFieldType().equals(modelClassName))
                                .map(f -> "final var %sList = %s;".formatted(f.nameCamelFirstLower(),
                                        generateTestData(modelClassName, f, f.optionalValueType(), f.repeated())))
                                .collect(Collectors.joining("\n")).indent(DEFAULT_INDENT),
                        fields.stream().filter(field -> !field.javaFieldType().equals(modelClassName))
                                .map(f -> f.nameCamelFirstLower()+"List.size()")
                                .collect(Collectors.collectingAndThen(Collectors.toList(),
                                        list -> list.isEmpty() ? Stream.of("0") : list.stream()))
                                .collect(Collectors.joining(",\n")).indent(DEFAULT_INDENT * 2),
                        modelClassName,
                        fields.stream().map(field -> field.javaFieldType().equals(modelClassName)
                                        ? field.javaFieldType() + ".newBuilder().build()"
                                        : "$nameList.get(Math.min(i, $nameList.size()-1))"
                                        .replace("$name", field.nameCamelFirstLower()))
                                .collect(Collectors.joining(",\n")).indent(DEFAULT_INDENT * 4),
                        modelClassName, modelClassName
                );
        // spotless:on
    }

    private static String generateTestData(String modelClassName, Field field, boolean optional, boolean repeated) {
        if (optional) {
            Field.FieldType convertedFieldType = getOptionalConvertedFieldType(field);
            return "addNull(%s)".formatted(getOptionsForFieldType(convertedFieldType, convertedFieldType.javaType));
        } else if (repeated) {
            final String optionsList = generateTestData(modelClassName, field, field.optionalValueType(), false);
            return "generateListArguments(%s)".formatted(optionsList);
        } else if (field instanceof final OneOfField oneOf) {
            // spotless:off
            final List<String> options = new ArrayList<>();
            final String classDotField = "%s.%s".formatted(modelClassName, field.nameCamelFirstUpper());
            for (var subField : oneOf.fields()) {
                if (subField instanceof SingleField) {
                    final String enumValueName = Common.camelToUpperSnake(subField.name());
                    // special cases to break cyclic dependencies
                    if (!(CIRCULAR_DEPENDENCIES_FIELDS.contains(enumValueName))) {
                        final String listStr;
                        if (subField.optionalValueType()) {
                            Field.FieldType convertedSubFieldType = getOptionalConvertedFieldType(subField);
                            listStr = getOptionsForFieldType(convertedSubFieldType, convertedSubFieldType.javaType);
                        } else {
                            listStr = getOptionsForFieldType(subField.type(), ((SingleField) subField).javaFieldTypeForTest());
                        }
                        options.add("%s%s".formatted(listStr,
                                """
                                
                                .stream()
                                .map(value -> new %s<>(%sOneOfType.%s, value))
                                .toList()"""
                                        .formatted(((OneOfField)field).className(), classDotField, enumValueName)
                                        .indent(DEFAULT_INDENT)));
                    }
                } else {
                    System.err.printf(
                            "Did not expect a OneOfField in a OneOfField. In modelClassName=%s field=%s subField=%s%n",
                            modelClassName, field, subField);
                }
            }
            return """
                    Stream.of(
                        List.of(new %s<>(%sOneOfType.UNSET, null)),
                        %s
                    ).flatMap(List::stream).toList()"""
                    .formatted(((OneOfField) field).className(), classDotField,
                            String.join(",\n", options).indent(DEFAULT_INDENT)).indent(DEFAULT_INDENT * 2);
            // spotless:on
        } else if (field instanceof final MapField mapField) {
            // e.g. INTEGER_TESTS_LIST
            final String keyOptions = getOptionsForFieldType(
                    mapField.keyField().type(), mapField.keyField().javaFieldType());
            // e.g. STRING_TESTS_LIST, or, say, CustomMessageTest.ARGUMENTS
            final String valueOptions = getOptionsForFieldType(
                    mapField.valueField().type(), mapField.valueField().javaFieldType());

            // A cartesian product is nice to use, but it doesn't seem reasonable from the performance perspective.
            // Instead, we want to test three cases:
            // 1. Empty map
            // 2. Map with a single entry
            // 3. Map with multiple (e.g. two) entries
            // Note that keys and value options lists may be pretty small. E.g. Boolean would only have 2 elements. So
            // we use mod.
            // Also note that we assume there's at least one element in each list.
            // spotless:off
            return """
                    List.of(
                        Map.$javaGenericTypeof(),
                        Map.$javaGenericTypeof($keyOptions.get(0), $valueOptions.get(0)),
                        Map.$javaGenericTypeof(
                            $keyOptions.get(1 % $keyOptions.size()), $valueOptions.get(1 % $valueOptions.size()),
                            $keyOptions.get(2 % $keyOptions.size()), $valueOptions.get(2 % $valueOptions.size())
                        )
                    )"""
                    .replace("$javaGenericType", mapField.javaGenericType())
                    .replace("$keyOptions", keyOptions)
                    .replace("$valueOptions", valueOptions);
            // spotless:on
        } else {
            return getOptionsForFieldType(field.type(), ((SingleField) field).javaFieldTypeForTest());
        }
    }

    private static Field.FieldType getOptionalConvertedFieldType(final Field field) {
        return switch (field.messageType()) {
            case "StringValue" -> Field.FieldType.STRING;
            case "Int32Value" -> Field.FieldType.INT32;
            case "UInt32Value" -> Field.FieldType.UINT32;
            case "Int64Value" -> Field.FieldType.INT64;
            case "UInt64Value" -> Field.FieldType.UINT64;
            case "FloatValue" -> Field.FieldType.FLOAT;
            case "DoubleValue" -> Field.FieldType.DOUBLE;
            case "BoolValue" -> Field.FieldType.BOOL;
            case "BytesValue" -> Field.FieldType.BYTES;
            default -> Field.FieldType.MESSAGE;
        };
    }

    private static String getOptionsForFieldType(Field.FieldType fieldType, String javaFieldType) {
        return switch (fieldType) {
            case INT32, SINT32, SFIXED32 -> "INTEGER_TESTS_LIST";
            case UINT32, FIXED32 -> "UNSIGNED_INTEGER_TESTS_LIST";
            case INT64, SINT64, SFIXED64 -> "LONG_TESTS_LIST";
            case UINT64, FIXED64 -> "UNSIGNED_LONG_TESTS_LIST";
            case FLOAT -> "FLOAT_TESTS_LIST";
            case DOUBLE -> "DOUBLE_TESTS_LIST";
            case BOOL -> "BOOLEAN_TESTS_LIST";
            case STRING -> "STRING_TESTS_LIST";
            case BYTES -> "BYTES_TESTS_LIST";
            case ENUM -> "Arrays.asList(%s.values())".formatted(javaFieldType);
            case ONE_OF -> throw new RuntimeException(
                    "Should never happen, should have been caught in generateTestData()");
            case MESSAGE -> "%s%s.ARGUMENTS".formatted(javaFieldType, FileAndPackageNamesConfig.TEST_JAVA_FILE_SUFFIX);
            case MAP -> throw new RuntimeException(
                    "Should never happen, should have been caught in generateTestData()");
        };
    }

    /**
     * Generate code for test method. The test method is designed to reuse thread local buffers. This is
     * very important for performance as without this the tests quickly overwhelm the garbage collector.
     *
     * This method also adds a public static final reference to the ProtoC class for this model object.
     *
     * @param modelClassName The class name of the model object we are creating a test for
     * @param protoCJavaFullQualifiedClass The qualified class name of the protoc generated object class
     * @return Code for test method
     */
    private static String generateTestMethod(final String modelClassName, final String protoCJavaFullQualifiedClass) {
        // spotless:off
        return """
                /** A reference to the protoc generated object class. */
                public static final Class<$protocModelClass> PROTOC_MODEL_CLASS
                        = $protocModelClass.class;

                @ParameterizedTest
                @MethodSource("createModelTestArguments")
                public void test$modelClassNameAgainstProtoC(final NoToStringWrapper<$modelClassName> modelObjWrapper) throws Exception {
                    final $modelClassName modelObj = modelObjWrapper.getValue();
                    // get reusable thread buffers
                    final var dataBuffer = getThreadLocalDataBuffer();
                    final var dataBuffer2 = getThreadLocalDataBuffer2();
                    final var byteBuffer = getThreadLocalByteBuffer();
                    final var charBuffer = getThreadLocalCharBuffer();
                    final var charBuffer2 = getThreadLocalCharBuffer2();
                
                    // model to bytes with PBJ
                    $modelClassName.PROTOBUF.write(modelObj, dataBuffer);
                    // clamp limit to bytes written
                    dataBuffer.limit(dataBuffer.position());
                
                    // copy bytes to ByteBuffer
                    dataBuffer.resetPosition();
                    final int protoBufByteCount = (int)dataBuffer.remaining();
                    dataBuffer.readBytes(byteBuffer);
                    byteBuffer.flip();
                
                    // read proto bytes with ProtoC to make sure it is readable and no parse exceptions are thrown
                    final $protocModelClass protoCModelObj = $protocModelClass.parseFrom(byteBuffer);
                
                    // read proto bytes with PBJ parser
                    dataBuffer.resetPosition();
                    final $modelClassName modelObj2 = $modelClassName.PROTOBUF.parse(dataBuffer);
                
                    // check the read back object is equal to written original one
                    //assertEquals(modelObj.toString(), modelObj2.toString());
                    assertEquals(modelObj, modelObj2);
                
                    // model to bytes with ProtoC writer
                    byteBuffer.clear();
                    final CodedOutputStream codedOutput = CodedOutputStream.newInstance(byteBuffer);
                    protoCModelObj.writeTo(codedOutput);
                    codedOutput.flush();
                    byteBuffer.flip();
                    // copy to a data buffer
                    dataBuffer2.writeBytes(byteBuffer);
                    dataBuffer2.flip();
                
                    // compare written bytes
                    assertEquals(dataBuffer, dataBuffer2);
                
                    // parse those bytes again with PBJ
                    dataBuffer2.resetPosition();
                    final $modelClassName modelObj3 = $modelClassName.PROTOBUF.parse(dataBuffer2);
                    assertEquals(modelObj, modelObj3);
                
                    // check measure methods
                    dataBuffer2.resetPosition();
                    assertEquals(protoBufByteCount, $modelClassName.PROTOBUF.measure(dataBuffer2));
                    assertEquals(protoBufByteCount, $modelClassName.PROTOBUF.measureRecord(modelObj));
                
                    // check fast equals
                    dataBuffer2.resetPosition();
                    assertTrue($modelClassName.PROTOBUF.fastEquals(modelObj, dataBuffer2));
                
                    // Test toBytes()
                    Bytes bytes = $modelClassName.PROTOBUF.toBytes(modelObj);
                    final var dataBuffer3 = getThreadLocalDataBuffer();
                    bytes.toReadableSequentialData().readBytes(dataBuffer3);
                    byte[] readBytes = new byte[(int)dataBuffer3.length()];
                    dataBuffer3.getBytes(0, readBytes);
                    assertArrayEquals(bytes.toByteArray(), readBytes);
                
                    // Test JSON Writing
                    final CharBufferToWritableSequentialData charBufferToWritableSequentialData = new CharBufferToWritableSequentialData(charBuffer);
                    $modelClassName.JSON.write(modelObj,charBufferToWritableSequentialData);
                    charBuffer.flip();
                    JsonFormat.printer().appendTo(protoCModelObj, charBuffer2);
                    charBuffer2.flip();
                    assertEquals(charBuffer2, charBuffer);
                
                    // Test JSON Reading
                    final $modelClassName jsonReadPbj = $modelClassName.JSON.parse(JsonTools.parseJson(charBuffer), false, Integer.MAX_VALUE);
                    assertEquals(modelObj, jsonReadPbj);
                }
                
                @SuppressWarnings("EqualsWithItself")
                @Test
                public void testTestEqualsAndHashCode() throws Exception {
                    if (ARGUMENTS.size() >= 3) {
                        final var item1 = ARGUMENTS.get(0);
                        final var item2 = ARGUMENTS.get(1);
                        final var item3 = ARGUMENTS.get(2);
                        assertEquals(item1, item1);
                        assertEquals(item2, item2);
                        assertEquals(item3, item3);
                        assertNotEquals(item1, item2);
                        assertNotEquals(item2, item3);
                        final var item1HashCode = item1.hashCode();
                        final var item2HashCode = item2.hashCode();
                        final var item3HashCode = item3.hashCode();
                        assertNotEquals(item1HashCode, item2HashCode);
                        assertNotEquals(item2HashCode, item3HashCode);
                    }
                }
                """
                .replace("$modelClassName",modelClassName)
                .replace("$protocModelClass",protoCJavaFullQualifiedClass)
                .replace("$modelClassName",modelClassName);
        // spotless:on
    }
}
