// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileAndPackageNamesConfig;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final var modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final var testClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.TEST, msgDef);
        final String protoCJavaFullQualifiedClass =
                lookupHelper.getFullyQualifiedMessageClassname(FileType.PROTOC, msgDef);
        final List<Field> fields = new ArrayList<>();

        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport(lookupHelper.getPackage(FileType.MODEL, msgDef) + ".*");

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null
                    || item.enumDef() != null
                    || item.DOC_COMMENT() != null) { // process sub messages down below
            } else if (item.oneof() != null) { // process one ofs
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, false, true);
                for (var subField : field.fields()) {
                    subField.addAllNeededImports(writer::addImport, true, false, true);
                }
            } else if (item.mapField() != null) { // process map fields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, false, true);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
                if (field.type() == Field.FieldType.MESSAGE || field.type() == Field.FieldType.ENUM) {
                    field.addAllNeededImports(writer::addImport, true, false, true);
                }
            } else if (item.reserved() == null && item.optionStatement() == null) {
                System.err.printf("TestGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }

        final String testClassAnnotations;
        if (Generator.isInner(msgDef)) {
            // NOTE: nested Test classes shouldn't be static, otherwise @Nested doesn't work.
            writer.addImport("org.junit.jupiter.api.Nested");
            testClassAnnotations = "\n@Nested";
        } else {
            testClassAnnotations = "";
        }

        writer.addImport("java.util.*");
        writer.addImport("com.google.protobuf.util.JsonFormat");
        writer.addImport("com.google.protobuf.CodedOutputStream");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.BufferedData");
        writer.addImport("com.hedera.pbj.runtime.JsonTools");
        writer.addImport("org.junit.jupiter.api.Test");
        writer.addImport("org.junit.jupiter.params.ParameterizedTest");
        writer.addImport("org.junit.jupiter.params.provider.MethodSource");
        writer.addImport("com.hedera.pbj.runtime.test.*");
        writer.addImport("java.util.stream.IntStream");
        writer.addImport("java.util.stream.Stream");
        writer.addImport("java.nio.ByteBuffer");
        writer.addImport("java.nio.CharBuffer");
        writer.addImport("com.google.protobuf.CodedInputStream");
        writer.addImport("com.google.protobuf.WireFormat");
        writer.addImport("java.io.IOException");
        writer.addImport("java.nio.charset.StandardCharsets");
        writer.addImport("static com.hedera.pbj.runtime.ProtoTestTools.*");
        writer.addImport("static org.junit.jupiter.api.Assertions.*");

        // spotless:off
        writer.append(
                """
                /**
                 * Unit Test for $modelClass model object. Generate based on protobuf schema.
                 */$testClassAnnotations
                public final class $testClassName {
                $testMethod
                $nonCanonicalOrderTest
                $viewTest
                $writerTest
                $testArguments
                $diffTests
                """
                .replace("$testClassAnnotations", testClassAnnotations)
                .replace("$modelClass", modelClassName)
                .replace("$testClassName", testClassName)
                .replace("$testMethod", generateTestMethod(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), protoCJavaFullQualifiedClass, lookupHelper.getCompleteClass(msgDef)).indent(DEFAULT_INDENT))
                .replace("$viewTest", lookupHelper.getLookupHelper().hasView(msgDef) ? generateViewTest(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), lookupHelper.getCompleteClass(msgDef), fields, unusedFieldNumber(fields)).indent(DEFAULT_INDENT) : "")
                .replace("$writerTest", lookupHelper.getLookupHelper().hasWriter(msgDef) ? generateWriterTest(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), lookupHelper.getCompleteClass(msgDef)).indent(DEFAULT_INDENT) : "")
                .replace("$nonCanonicalOrderTest", generateNonCanonicalOrderTest(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), protoCJavaFullQualifiedClass, lookupHelper.getCompleteClass(msgDef), unusedFieldNumber(fields)).indent(DEFAULT_INDENT))
                .replace("$testArguments", generateModelTestArgumentsMethod(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), fields, lookupHelper.getCompleteClass(msgDef)).indent(DEFAULT_INDENT))
                .replace("$diffTests", lookupHelper.hasDiffApis(msgDef) ? generateDiffTests(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef), protoCJavaFullQualifiedClass, fields, lookupHelper.generateCopyBuilderTracking(msgDef)) : "")
        );
        // spotless:on

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), writer, lookupHelper);
            }
        }

        writer.append("}");
    }

    /**
     * Find a field number the message does not define, preferring 99, for the unknown field the non-canonical-order
     * test appends.
     *
     * @param fields the message's fields
     * @return an unused field number
     */
    static int unusedFieldNumber(final List<Field> fields) {
        final Set<Integer> used = new HashSet<>();
        for (final Field field : fields) {
            if (field instanceof final OneOfField oneOf) {
                oneOf.fields().forEach(subField -> used.add(subField.fieldNumber()));
            } else {
                used.add(field.fieldNumber());
            }
        }
        int fieldNumber = 99;
        while (used.contains(fieldNumber)) {
            fieldNumber++;
        }
        return fieldNumber;
    }

    /**
     * Generate a test that parses each test object from protoc's bytes with the top-level fields in reverse order and
     * an unknown field appended. Writers always emit fields in ascending order, so this is the test that exercises a
     * parser's handling of out-of-order fields and unknown fields after known ones.
     *
     * @param modelClassName the fully qualified model class
     * @param protoCJavaFullQualifiedClass the fully qualified protoc class
     * @param testName the complete class name, used to name the test method
     * @param unknownFieldNumber a field number the message does not define
     * @return code for the test method
     */
    private static String generateNonCanonicalOrderTest(
            final String modelClassName,
            final String protoCJavaFullQualifiedClass,
            final String testName,
            final int unknownFieldNumber) {
        // spotless:off
        return """
                @ParameterizedTest
                @MethodSource("createModelTestArguments")
                public void test$simpleModelClassNameNonCanonicalOrder(final NoToStringWrapper<$modelClassName> modelObjWrapper) throws Exception {
                    final $modelClassName modelObj = modelObjWrapper.getValue();
                    // protoc's canonical bytes, then the same fields in reverse order followed by an unknown field
                    final Bytes canonical = Bytes.wrap($protocModelClass
                            .parseFrom($modelClassName.PROTOBUF.toBytes(modelObj).toByteArray()).toByteArray());
                    final Bytes reordered = appendVarIntField(reverseTopLevelFieldOrder(canonical), $unknownField);
                    // field order does not matter, and the unknown field is skipped by default
                    assertEquals(modelObj, $modelClassName.PROTOBUF.parse(reordered));
                    // strict mode rejects the unknown field
                    assertThrows(com.hedera.pbj.runtime.ParseException.class, () -> $modelClassName.PROTOBUF.parseStrict(reordered));
                    // a kept unknown field is written after the known fields, which are back in canonical order
                    final $modelClassName withUnknown = $modelClassName.PROTOBUF.parse(reordered.toReadableSequentialData(),
                            false, true, com.hedera.pbj.runtime.Codec.DEFAULT_MAX_DEPTH);
                    assertEquals(appendVarIntField(canonical, $unknownField), $modelClassName.PROTOBUF.toBytes(withUnknown));
                }
                """
                .replace("$simpleModelClassName", testName.replace(".", ""))
                .replace("$modelClassName", modelClassName)
                .replace("$protocModelClass", protoCJavaFullQualifiedClass)
                .replace("$unknownField", Integer.toString(unknownFieldNumber));
        // spotless:on
    }

    /**
     * Generate a test that reads every test object through its zero-copy view, from canonical bytes and from bytes in
     * reverse field order with an unknown field, and checks each field and {@code toModel()} against the model.
     */
    private static String generateViewTest(
            final String modelClassName,
            final String testName,
            final List<Field> fields,
            final int unknownFieldNumber) {
        final StringBuilder checks = new StringBuilder();
        for (final Field field : fields) {
            final String name = field.nameCamelFirstLower();
            if (field instanceof OneOfField oneOf) {
                checks.append("assertEquals(modelObj.%s().kind(), view.%sKind());%n".formatted(name, name));
                for (final Field alternative : oneOf.fields()) {
                    final String alt = alternative.nameCamelFirstLower();
                    final String check =
                            alternative.type() == Field.FieldType.MESSAGE && !alternative.optionalValueType()
                                    ? "assertEquals(modelObj.%s(), view.%s().toModel());".formatted(alt, alt)
                                    : "assertEquals(modelObj.%s(), view.%s());".formatted(alt, alt);
                    checks.append("if (modelObj.%s().kind() == %s.%s) {%n    %s%n}%n"
                            .formatted(
                                    name,
                                    oneOf.getEnumClassRef(),
                                    Common.camelToUpperSnake(alternative.name()),
                                    check));
                }
                continue;
            }
            if (field.repeated()) {
                checks.append(
                        """
                        {
                            final List<Object> elements = new ArrayList<>();
                            for (var it = view.$name(); it.next(); ) {
                                elements.add($element);
                            }
                            assertEquals(modelObj.$getter(), elements);
                            assertEquals(elements.size(), view.$nameCount());
                        }
                        """.replace("$getter", field.type() == Field.FieldType.ENUM ? name + "ProtoOrdinals" : name)
                                .replace(
                                        "$element",
                                        field.type() == Field.FieldType.MESSAGE ? "it.value().toModel()" : "it.value()")
                                .replace("$name", name));
                continue;
            }
            checks.append(
                    switch (field.type()) {
                        case ENUM ->
                            "assertEquals(modelObj.%sProtoOrdinal(), view.%sProtoOrdinal());%n".formatted(name, name);
                        case MESSAGE ->
                            field.optionalValueType()
                                    ? "assertEquals(modelObj.%s(), view.%s());%n".formatted(name, name)
                                    : "assertEquals(modelObj.%s(), view.%s() == null ? null : view.%s().toModel());%n"
                                            .formatted(name, name, name);
                        default -> "assertEquals(modelObj.%s(), view.%s());%n".formatted(name, name);
                    });
        }
        // each string field holding the single byte 0xFF, which is not UTF-8
        final String malformedStrings = fields.stream()
                .filter(field -> field.type() == Field.FieldType.STRING)
                .map(field -> java.util.Arrays.stream(
                                Common.tagBytes(field.fieldNumber(), Common.TYPE_LENGTH_DELIMITED))
                        .mapToObj(b -> "(byte) 0x%02X, ".formatted(b))
                        .collect(Collectors.joining("", "{", "1, (byte) 0xFF}")))
                .collect(Collectors.joining(", "));
        // spotless:off
        final String malformedTest = malformedStrings.isEmpty() ? "" : """

                @Test
                public void test$simpleModelClassNameViewRejectsMalformedUtf8() {
                    for (final byte[] malformed : new byte[][] {$malformedStrings}) {
                        final Bytes bytes = Bytes.wrap(malformed);
                        assertThrows(com.hedera.pbj.runtime.ParseException.class, () -> $modelClassName.PROTOBUF.parse(bytes));
                        assertThrows(com.hedera.pbj.runtime.ParseException.class, () -> new $modelClassName.View().wrap(bytes));
                    }
                }
                """.replace("$malformedStrings", malformedStrings);
        return """
                @ParameterizedTest
                @MethodSource("createModelTestArguments")
                public void test$simpleModelClassNameView(final NoToStringWrapper<$modelClassName> modelObjWrapper) throws Exception {
                    final $modelClassName modelObj = modelObjWrapper.getValue();
                    final Bytes bytes = $modelClassName.PROTOBUF.toBytes(modelObj);
                    final $modelClassName.View view = new $modelClassName.View().wrap(bytes);
                $checks
                    assertEquals(modelObj, view.toModel());
                    // the same view, re-wrapped over the fields in reverse order followed by an unknown field
                    view.wrap(appendVarIntField(reverseTopLevelFieldOrder(bytes), $unknownField));
                $checks
                }
                $malformedTest"""
                .replace("$malformedTest", malformedTest)
                .replace("$checks", checks.toString().indent(DEFAULT_INDENT))
                .replace("$simpleModelClassName", testName.replace(".", ""))
                .replace("$modelClassName", modelClassName)
                .replace("$unknownField", Integer.toString(unknownFieldNumber));
        // spotless:on
    }

    /**
     * Generate a test that writes every test object through the message's writer with {@code writeFrom}, into a buffer
     * that starts small enough to grow mid-message, with each way of reserving length prefixes, and checks the bytes
     * are exactly the model's encoding and parse back to the object.
     */
    private static String generateWriterTest(final String modelClassName, final String testName) {
        // spotless:off
        return """
                @ParameterizedTest
                @MethodSource("createModelTestArguments")
                public void test$simpleModelClassNameWriter(final NoToStringWrapper<$modelClassName> modelObjWrapper) throws Exception {
                    final $modelClassName modelObj = modelObjWrapper.getValue();
                    final Bytes expected = $modelClassName.PROTOBUF.toBytes(modelObj);
                    final $modelClassName.Writer writer = new $modelClassName.Writer();
                    for (final ProtoWriteBuffer.LengthReservation reservation : ProtoWriteBuffer.LengthReservation.values()) {
                        final ProtoWriteBuffer buffer = new ProtoWriteBuffer(1, reservation);
                        assertEquals(expected.length(), writer.start(buffer).writeFrom(modelObj).end());
                        assertEquals(expected, buffer.bytes());
                        assertEquals(0, writer.offset());
                        assertEquals(modelObj, $modelClassName.PROTOBUF.parse(buffer.bytes()));
                        // the same writer and buffer again, after a reset
                        buffer.reset();
                        writer.start(buffer).writeFrom(modelObj).end();
                        assertEquals(expected, buffer.bytes());
                    }
                }
                """
                .replace("$simpleModelClassName", testName.replace(".", ""))
                .replace("$modelClassName", modelClassName);
        // spotless:on
    }

    private static String generateDiffTests(String model, String protoc, List<Field> fields, boolean tracking) {
        String roundTrips = """
                @Test
                public void testPartialRoundTrip() throws Exception {
                    assertInstanceOf(com.hedera.pbj.runtime.DiffCodec.class, $model.PROTOBUF);
                    for (int i = 0; i < ARGUMENTS.size(); i++) {
                        final $model value = ARGUMENTS.get(i);
                        checkPartialRoundTrip(value, ARGUMENTS.get((i + 1) % ARGUMENTS.size()));
                        checkPartialRoundTrip($model.DEFAULT, value);
                        checkPartialRoundTrip(value, $model.DEFAULT);
                    }
                }

                private void checkPartialRoundTrip($model prior, $model next) throws Exception {
                    final $maskType mask = $model.diff(prior, next);
                    final $model partial = $model.partial(next, mask);
                    final com.hedera.pbj.runtime.io.buffer.Bytes priorBytes = $model.PROTOBUF.toBytes(prior);
                    final com.hedera.pbj.runtime.io.buffer.Bytes nextBytes = $model.PROTOBUF.toBytes(next);
                    final com.hedera.pbj.runtime.io.buffer.Bytes partialBytes = $model.PROTOBUF.toBytes(partial);
                    final int[] precise = $model.clearedFields(prior, next, mask);
                    final int[] broad = $model.clearedFields(next, mask);
                    for (int[] cleared : new int[][] { precise, broad }) {
                        assertEquals(nextBytes, com.hedera.pbj.runtime.PartialApplier.splice(priorBytes, partialBytes, cleared));
                        final $model applied = $model.applyPartial(prior, partial, cleared);
                        assertTrue(next.$equalsForDiff(applied));
                        assertEquals(nextBytes, $model.PROTOBUF.toBytes(applied));
                    }
                    assertArrayEquals(partialBytes.toByteArray(), $protoc.parseFrom(partialBytes.toByteArray()).toByteArray());
                    final long saving = nextBytes.length() - partialBytes.length();
                    for (int threshold : new int[] { 0, 1, 32, 128 }) {
                        for (int overhead : new int[] { 0, 17 }) {
                            assertEquals(saving >= (long) threshold + overhead,
                                    $model.worthPartial(next, mask, threshold, overhead));
                        }
                    }
                }
                """.replace("$maskType", CopyBuilderGenerator.maskType(fields))
                .replace("$model", model)
                .replace("$protoc", protoc);
        if (!tracking) return roundTrips.indent(DEFAULT_INDENT);
        final StringBuilder setters = new StringBuilder();
        for (Field field : fields) {
            String name = field.nameCamelFirstLower();
            String set;
            if (field instanceof OneOfField group) {
                set = "switch (donor." + name + "().kind()) {\n";
                for (Field alternative : group.fields()) {
                    set += "case " + com.hedera.pbj.compiler.impl.Common.camelToUpperSnake(alternative.name())
                            + " -> builder." + alternative.nameCamelFirstLower() + "(donor."
                            + alternative.nameCamelFirstLower() + "());\n";
                }
                set += "default -> builder.clear" + field.nameCamelFirstUpper() + "();\n}\n";
            } else {
                set = "builder." + name + "(donor." + name + "());\n";
            }
            int number = field instanceof OneOfField group
                    ? group.fields().getFirst().fieldNumber()
                    : field.fieldNumber();
            setters.append("""
                    {
                        final $model.Builder builder = prior.copyBuilder();
                    $set
                        final $model next = builder.build();
                        assertTrue(next.$copyBuilderFieldChanged($number));
                        assertSame(prior, next.$copyBuilderOrigin());
                        final $model detached = next.$untracked();
                        $maskAssertion
                        if (donor == prior) {
                            $emptyMaskAssertion
                        }
                        checkPartialRoundTrip(prior, next);
                        assertTrue(next.$equalsForDiff(detached));
                        assertEquals(next.hashCode(), detached.hashCode());
                        assertEquals($model.PROTOBUF.toBytes(next), $model.PROTOBUF.toBytes(detached));
                        final $model chained = next.copyBuilder().build();
                        assertSame(prior, chained.$copyBuilderOrigin());
                        assertEquals($model.PROTOBUF.toBytes(next), $model.PROTOBUF.toBytes(chained));
                    }
                    """.replace("$set\n", set.indent(DEFAULT_INDENT))
                    .replace("$number", Integer.toString(number))
                    .replace(
                            "$maskAssertion",
                            fields.size() > Long.SIZE
                                    ? "assertArrayEquals(" + model + ".diff(prior, detached), " + model
                                            + ".diff(prior, next));"
                                    : "assertEquals(" + model + ".diff(prior, detached), " + model
                                            + ".diff(prior, next));")
                    .replace(
                            "$emptyMaskAssertion",
                            fields.size() > Long.SIZE
                                    ? "assertArrayEquals(new long[" + ((fields.size() + Long.SIZE - 1) / Long.SIZE)
                                            + "], " + model + ".diff(prior, next));"
                                    : "assertEquals(0L, " + model + ".diff(prior, next));")
                    .replace("$model", model));
        }
        // Re-run every generated setter with the same prior as donor: candidates are marked, but the diff is empty.
        String tracked = """
                @Test
                public void testCopyBuilderTracking() throws Exception {
                    final $model prior = ARGUMENTS.getFirst();
                    for ($model donor : List.of(ARGUMENTS.get(Math.min(1, ARGUMENTS.size() - 1)), prior)) {
                $setters
                    }
                }
                """.replace("$model", model)
                .replace("$setters\n", setters.toString().indent(DEFAULT_INDENT * 2));
        return (roundTrips + tracked).indent(DEFAULT_INDENT);
    }

    private static String generateModelTestArgumentsMethod(
            final String modelClassName, final List<Field> fields, final String selfType) {
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
                            .filter(field -> !field.javaFieldType().equals(selfType))
                            .map(f -> "final var %sList = %s;".formatted(f.nameCamelFirstLower(),
                                    generateTestData(modelClassName, f, f.optionalValueType(), f.repeated())))
                            .collect(Collectors.joining("\n")).indent(DEFAULT_INDENT),
                    fields.stream().filter(field -> !field.javaFieldType().equals(selfType))
                            .map(f -> f.nameCamelFirstLower()+"List.size()")
                            .collect(Collectors.collectingAndThen(Collectors.toList(),
                                    list -> list.isEmpty() ? Stream.of("0") : list.stream()))
                            .collect(Collectors.joining(",\n")).indent(DEFAULT_INDENT * 2),
                    modelClassName,
                    fields.stream().map(field -> field.javaFieldType().equals(selfType)
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
            // Skip the special UNRECOGNIZED constant in tests because it cannot(shouldn't) be set by client code
            case ENUM ->
                "java.util.Arrays.asList(%s.values()).stream().filter(e -> !\"UNRECOGNIZED\".equals(e.name())).toList()"
                        .formatted(javaFieldType);
            case ONE_OF ->
                throw new RuntimeException("Should never happen, should have been caught in generateTestData()");
            case MESSAGE -> "%s%s.ARGUMENTS".formatted(javaFieldType, FileAndPackageNamesConfig.TEST_JAVA_FILE_SUFFIX);
            case MAP ->
                throw new RuntimeException("Should never happen, should have been caught in generateTestData()");
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
    private static String generateTestMethod(
            final String modelClassName, final String protoCJavaFullQualifiedClass, final String testName) {
        // spotless:off
        return """
                /** A reference to the protoc generated object class. */
                public static final Class<$protocModelClass> PROTOC_MODEL_CLASS
                        = $protocModelClass.class;

                @ParameterizedTest
                @MethodSource("createModelTestArguments")
                public void test$simpleModelClassNameAgainstProtoC(final NoToStringWrapper<$modelClassName> modelObjWrapper) throws Exception {
                    final $modelClassName modelObj = modelObjWrapper.getValue();
                    // get reusable thread buffers
                    final var dataBuffer = getThreadLocalDataBuffer();
                    final var dataBuffer2 = getThreadLocalDataBuffer2();
                    final var byteBuffer = getThreadLocalByteBuffer();
                    final var charBuffer = getThreadLocalCharBuffer();
                    final var charBuffer2 = getThreadLocalCharBuffer2();
                    final byte[] bytesArray = getThreadLocalByteArray();
                
                    // model to bytes with PBJ
                    $modelClassName.PROTOBUF.write(modelObj, dataBuffer);
                    // clamp limit to bytes written
                    dataBuffer.limit(dataBuffer.position());
                
                    java.util.Arrays.fill(bytesArray, (byte) 0);
                    $modelClassName.PROTOBUF.write(modelObj, bytesArray, 0);

                    // copy bytes to ByteBuffer
                    dataBuffer.resetPosition();
                    final int protoBufByteCount = (int)dataBuffer.remaining();
                    dataBuffer.readBytes(byteBuffer);
                    byteBuffer.flip();
                
                    // Check the write(byte[]) output against write(WritableSequentialData):
                    dataBuffer.resetPosition();
                    final int mismatchIndex = java.util.Arrays.mismatch(byteBuffer.array(), 0, protoBufByteCount, bytesArray, 0, protoBufByteCount);
                    if (mismatchIndex != -1) {
                        fail("Mismatch at index " + mismatchIndex +
                                ": in buffer got " + byteBuffer.array()[mismatchIndex] + ", but in array got " + bytesArray[mismatchIndex] +
                                ".\\nBuffer:\\n" + dataBuffer.toString() +
                                "\\nArray:\\n" + java.util.Arrays.toString(java.util.Arrays.copyOf(bytesArray, protoBufByteCount)) +
                                "\\nModel:\\n" + modelObj +
                                "\\nProtobuf size:\\n" + protoBufByteCount
                        );
                    }
                    // This check is not precise. An IOOB exception would be best. But we don't want to create new arrays
                    // for each test so as not to overwhelm the Java GC. So we do this instead:
                    for (int i = protoBufByteCount; i < bytesArray.length; i++) {
                        if (bytesArray[i] != 0) {
                            fail("write(byte[]) wrote more bytes into array than write(WritableSequentialData) into buffer," +
                                    " e.g. at index " + i + " got " + bytesArray[i] + " but expected zero." +
                                    "\\nBuffer:\\n" + dataBuffer.toString() +
                                    "\\nArray:\\n" + java.util.Arrays.toString(java.util.Arrays.copyOf(bytesArray, protoBufByteCount)) +
                                    "\\nModel:\\n" + modelObj +
                                    "\\nProtobuf size:\\n" + protoBufByteCount
                            );
                        }
                    }

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
                
                    // check measureRecord()
                    dataBuffer2.resetPosition();
                    assertEquals(protoBufByteCount, $modelClassName.PROTOBUF.measureRecord(modelObj));
                                
                    // Test toBytes()
                    Bytes bytes = $modelClassName.PROTOBUF.toBytes(modelObj);
                    final var dataBuffer3 = getThreadLocalDataBuffer();
                    bytes.toReadableSequentialData().readBytes(dataBuffer3);
                    byte[] readBytes = new byte[(int)dataBuffer3.length()];
                    dataBuffer3.getBytes(0, readBytes);
                    assertArrayEquals(bytes.toByteArray(), readBytes);

                    // Test writeReverse(), which must write the same bytes as toBytes(), also into a reused buffer
                    final ReverseBuffer reverseBuffer = new ReverseBuffer(16);
                    $modelClassName.PROTOBUF.writeReverse(modelObj, reverseBuffer);
                    assertEquals(bytes, reverseBuffer.toBytes());
                    reverseBuffer.reset();
                    $modelClassName.PROTOBUF.writeReverse(modelObj, reverseBuffer);
                    assertEquals(bytes, reverseBuffer.bytes());
                
                    // Test JSON Writing
                    final CharBufferToWritableSequentialData charBufferToWritableSequentialData = new CharBufferToWritableSequentialData(charBuffer);
                    $modelClassName.JSON.write(modelObj,charBufferToWritableSequentialData);
                    charBuffer.flip();
                    JsonFormat.printer().appendTo(protoCModelObj, charBuffer2);
                    charBuffer2.flip();
                    assertEquals(charBuffer2, charBuffer);
                
                    // Test JSON Reading
                    final $modelClassName jsonReadPbj = $modelClassName.JSON.parse(JsonTools.parseJson(charBuffer), false, Integer.MAX_VALUE, Integer.MAX_VALUE);
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

                @Test
                public void testCodecGetDefaultValueMethod(){
                    final var codec = $modelClassName.PROTOBUF;
                    final var codecDefaultInstance = codec.getDefaultInstance();
                    assertEquals($modelClassName.DEFAULT, codecDefaultInstance);
                }
                """
                .replace("$simpleModelClassName", testName.replace(".", ""))
                .replace("$modelClassName",modelClassName)
                .replace("$protocModelClass",protoCJavaFullQualifiedClass);
        // spotless:on
    }
}
