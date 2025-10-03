// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Code to generate the measure record method for Codec classes. This measures the number of bytes that would be
 * written if the record was serialized in protobuf format.
 */
public class LazyGetProtobufSizeMethodGenerator {

    public static String generateLazyGetProtobufSize(final List<Field> fields, final String schemaClassName) {
        final String fieldSizeOfLines =
                buildFieldSizeOfLines(null, schemaClassName, fields, Field::nameCamelFirstLower, true);
        return """
                /**
                 * Get number of bytes when serializing the object to protobuf binary.
                 *
                 * @return The length in bytes in protobuf encoding
                 */
                public int protobufSize() {
                    // The $protobufEncodedSize field is subject to a benign data race, making it crucial to ensure that any
                    // observable result of the calculation in this method stays correct under any possible read of this
                    // field. Necessary restrictions to allow this to be correct without explicit memory fences or similar
                    // concurrency primitives is that we can ever only write to this field for a given Model object
                    // instance, and that the computation is idempotent and derived from immutable state.
                    // This is the same trick used in java.lang.String.hashCode() to avoid synchronization.

                    if ($protobufEncodedSize == -1) {
                        int _size = 0;
                $fieldSizeOfLines
                $unknownFieldsSizeOfLines
                        $protobufEncodedSize = _size;
                    }
                    return $protobufEncodedSize;
                }
                """
                .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT))
                .replace(
                        "$unknownFieldsSizeOfLines",
                        formatUnknownFieldsSizeOfLines().indent(DEFAULT_INDENT))
                .indent(DEFAULT_INDENT);
    }

    static String buildFieldSizeOfLines(
            final String modelClassName,
            final String schemaClassName,
            final List<Field> fields,
            final Function<Field, String> getValueBuilder,
            boolean skipDefault) {
        // Flatten oneOf fields to subfields and sort all by field number (like protoc does)
        final List<Field> flattenedFields = fields.stream()
                .flatMap(field -> field.type() == Field.FieldType.ONE_OF
                        ? ((OneOfField) field).fields().stream()
                        : java.util.stream.Stream.of(field))
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .toList();

        // Track which oneOf parents we've already generated switches for (using identity, not equals/hashCode)
        final java.util.Set<OneOfField> processedOneOfs =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        // Now iterate through flattened fields
        final StringBuilder result = new StringBuilder();
        for (final Field field : flattenedFields) {
            if (field.parent() == null) {
                // Regular field -> generate normal size calculation
                result.append(generateFieldSizeOfLines(
                        field, modelClassName, schemaClassName, getValueBuilder.apply(field), skipDefault, false));
                result.append("\n");
            } else {
                // This is a oneOf subfield
                final OneOfField parentOneOf = field.parent();

                if (!processedOneOfs.contains(parentOneOf)) {
                    // First time seeing this oneOf parent -> generate switch for ALL its subfields
                    result.append(generateOneOfSwitchBlock(parentOneOf, modelClassName, schemaClassName));
                    result.append("\n");
                    processedOneOfs.add(parentOneOf);
                }
                // Else skip this field (already handled in the switch)
            }
        }

        return result.toString().indent(DEFAULT_INDENT);
    }

    /**
     * Generate a switch statement block for oneOf fields.
     *
     * @param oneOfField The oneOf field containing all cases
     * @param modelClassName The model class name (may be null)
     * @param schemaClassName The schema class name
     * @return java code for switch statement handling all oneOf cases
     */
    private static String generateOneOfSwitchBlock(
            final OneOfField oneOfField, final String modelClassName, final String schemaClassName) {
        final String switchVar = oneOfField.nameCamelFirstLower() + ".kind()";

        // Build switch cases for each field in the oneOf
        final String cases = oneOfField.fields().stream()
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> {
                    final String caseLabel = Common.camelToUpperSnake(field.name());
                    final String getValueCode = oneOfField.nameCamelFirstLower() + ".as()";
                    // Generate field size without prefix comment/if (we're in switch context)
                    final String fieldSize =
                            generateFieldSizeOfLines(field, modelClassName, schemaClassName, getValueCode, false, true);

                    return "case %s -> { // [%d] - %s\n%s\n}"
                            .formatted(caseLabel, field.fieldNumber(), field.name(), fieldSize.indent(DEFAULT_INDENT));
                })
                .collect(Collectors.joining("\n"));

        // spotless:off
        return """
                // OneOf field $oneOfName ($numCases cases)
                switch ($switchVar) {
                    $cases
                    case UNSET -> {
                        // oneOf not set, nothing to measure
                    }
                }
                """
                .replace("$oneOfName", oneOfField.name())
                .replace("$numCases", String.valueOf(oneOfField.fields().size()))
                .replace("$switchVar", switchVar)
                .replace("$cases", cases.indent(DEFAULT_INDENT));
        // spotless:on
    }

    static String formatUnknownFieldsSizeOfLines() {
        return """
                if ($unknownFields != null) {
                    for (int i = 0; i < $unknownFields.size(); i++) {
                        final UnknownField uf = $unknownFields.get(i);
                        _size += sizeOfVarInt32((uf.field() << ProtoParserTools.TAG_FIELD_OFFSET) | uf.wireType().ordinal());
                        _size += Math.toIntExact(uf.bytes().length());
                    }
                }
                """;
    }

    /**
     * Generate lines of code for measure method, that measure the size of each field and add to "size" variable.
     *
     * @param field The field to generate size of line
     * @param modelClassName The model class name for model class for message type we are generating writer for
     * @param getValueCode java code to get the value of field
     * @param skipDefault true if default value of the field should result in size zero
     * @param inSwitchContext true if generating code for use inside a switch statement (no prefix comment or if-check)
     * @return java code for adding fields size to "size" variable
     */
    private static String generateFieldSizeOfLines(
            final Field field,
            final String modelClassName,
            final String schemaClassName,
            String getValueCode,
            boolean skipDefault,
            boolean inSwitchContext) {
        final String fieldDef = schemaClassName + "." + Common.camelToUpperSnake(field.name());
        String prefix = "";

        // Only generate prefix comment and if-check when NOT in switch context
        if (!inSwitchContext) {
            prefix = "// [" + field.fieldNumber() + "] - " + field.name();
            prefix += "\n";

            if (field.parent() != null) {
                final OneOfField oneOfField = field.parent();
                final String oneOfType = modelClassName == null
                        ? oneOfField.nameCamelFirstUpper() + "OneOfType"
                        : modelClassName + "." + oneOfField.nameCamelFirstUpper() + "OneOfType";
                getValueCode = oneOfField.nameCamelFirstLower() + ".as()";
                prefix += "if (" + oneOfField.nameCamelFirstLower() + ".kind() == " + oneOfType + "."
                        + Common.camelToUpperSnake(field.name()) + ")";
                prefix += "\n";
            }
        }

        final String writeMethodName = field.methodNameType();
        if (field.optionalValueType()) {
            return prefix
                    + switch (field.messageType()) {
                        case "StringValue" ->
                            "_size += sizeOfOptionalString(%s, %s);".formatted(fieldDef, getValueCode);
                        case "BoolValue" -> "_size += sizeOfOptionalBoolean(%s, %s);".formatted(fieldDef, getValueCode);
                        case "Int32Value", "UInt32Value" ->
                            "_size += sizeOfOptionalInteger(%s, %s);".formatted(fieldDef, getValueCode);
                        case "Int64Value", "UInt64Value" ->
                            "_size += sizeOfOptionalLong(%s, %s);".formatted(fieldDef, getValueCode);
                        case "FloatValue" -> "_size += sizeOfOptionalFloat(%s, %s);".formatted(fieldDef, getValueCode);
                        case "DoubleValue" ->
                            "_size += sizeOfOptionalDouble(%s, %s);".formatted(fieldDef, getValueCode);
                        case "BytesValue" -> "_size += sizeOfOptionalBytes(%s, %s);".formatted(fieldDef, getValueCode);
                        default ->
                            throw new UnsupportedOperationException(
                                    "Unhandled optional message type:" + field.messageType());
                    };
        } else if (field.repeated()) {
            return prefix
                    + switch (field.type()) {
                        case ENUM -> "_size += sizeOfEnumList(%s, %s);".formatted(fieldDef, getValueCode);
                        case MESSAGE ->
                            "_size += sizeOfMessageList($fieldDef, $valueCode, $codec);"
                                    .replace("$fieldDef", fieldDef)
                                    .replace("$valueCode", getValueCode)
                                    .replace(
                                            "$codec",
                                            ((SingleField) field).messageTypeModelPackage() + "."
                                                    + ((SingleField) field).completeClassName() + ".PROTOBUF");
                        default -> "_size += sizeOf%sList(%s, %s);".formatted(writeMethodName, fieldDef, getValueCode);
                    };
        } else if (field.type() == Field.FieldType.MAP) {
            final MapField mapField = (MapField) field;
            final List<Field> mapEntryFields = List.of(mapField.keyField(), mapField.valueField());
            final Function<Field, String> getValueBuilder = mapEntryField ->
                    mapEntryField == mapField.keyField() ? "k" : (mapEntryField == mapField.valueField() ? "v" : null);
            final String fieldSizeOfLines = LazyGetProtobufSizeMethodGenerator.buildFieldSizeOfLines(
                    field.name(), schemaClassName, mapEntryFields, getValueBuilder, false);
            return prefix
                    + """
                        if (!$map.isEmpty()) {
                            final Pbj$javaFieldType pbjMap = (Pbj$javaFieldType) $map;
                            final int mapSize = pbjMap.size();
                            for (int i = 0; i < mapSize; i++) {
                                _size += sizeOfTag($fieldDef, WIRE_TYPE_DELIMITED);
                                final int sizePre = _size;
                                $K k = pbjMap.getSortedKeys().get(i);
                                $V v = pbjMap.get(k);
                                $fieldSizeOfLines
                                _size += sizeOfVarInt32(_size - sizePre);
                            }
                        }
                        """
                            .replace("$fieldDef", fieldDef)
                            .replace("$map", getValueCode)
                            .replace("$javaFieldType", mapField.javaFieldType())
                            .replace("$K", mapField.keyField().type().boxedType)
                            .replace(
                                    "$V",
                                    mapField.valueField().type() == Field.FieldType.MESSAGE
                                            ? mapField.valueField().messageType()
                                            : mapField.valueField().type().boxedType)
                            .replace("$fieldSizeOfLines", fieldSizeOfLines.indent(DEFAULT_INDENT));
        } else {
            return prefix
                    + switch (field.type()) {
                        case ENUM -> "_size += sizeOfEnum(%s, %s);".formatted(fieldDef, getValueCode);
                        case STRING ->
                            "_size += sizeOfString(%s, %s, %s);".formatted(fieldDef, getValueCode, skipDefault);
                        case MESSAGE ->
                            "_size += sizeOfMessage($fieldDef, $valueCode, $codec);"
                                    .replace("$fieldDef", fieldDef)
                                    .replace("$valueCode", getValueCode)
                                    .replace(
                                            "$codec",
                                            ((SingleField) field).messageTypeModelPackage() + "."
                                                    + ((SingleField) field).completeClassName() + ".PROTOBUF");
                        case BOOL ->
                            "_size += sizeOfBoolean(%s, %s, %s);".formatted(fieldDef, getValueCode, skipDefault);
                        case INT32,
                                UINT32,
                                SINT32,
                                FIXED32,
                                SFIXED32,
                                INT64,
                                SINT64,
                                UINT64,
                                FIXED64,
                                SFIXED64,
                                BYTES ->
                            "_size += sizeOf%s(%s, %s, %s);"
                                    .formatted(writeMethodName, fieldDef, getValueCode, skipDefault);
                        default -> "_size += sizeOf%s(%s, %s);".formatted(writeMethodName, fieldDef, getValueCode);
                    };
        }
    }
}
