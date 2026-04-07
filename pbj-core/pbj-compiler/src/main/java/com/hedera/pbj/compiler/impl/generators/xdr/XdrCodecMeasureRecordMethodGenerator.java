// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.xdr;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Code to generate the measureRecord method for XDR Codec classes.
 */
final class XdrCodecMeasureRecordMethodGenerator {

    /**
     * Generate the measureRecord() method for an XDR codec class.
     *
     * @param modelClassName the name of the model class being measured
     * @param fields the list of fields in the message
     * @return the full measureRecord() method as a String
     */
    static String generateMeasureRecordMethod(final String modelClassName, final List<Field> fields) {
        final String fieldSizeLines = fields.stream()
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldSizeLines(field, modelClassName))
                .collect(Collectors.joining("\n"));

        // spotless:off
        return """
                /**
                 * Compute the number of bytes that would be written when calling write() method.
                 *
                 * @param item The input model data to measure
                 * @return The length in bytes that would be written
                 */
                @Override
                public int measureRecord(final $modelClass item) {
                    int size = 0;
                    $fieldSizeLines
                    return size;
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldSizeLines", fieldSizeLines)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Generate the size lines for a single field.
     */
    private static String generateFieldSizeLines(final Field field, final String modelClassName) {
        final String fieldName = field.nameCamelFirstLower();
        final String comment = "// [%d] - %s\n".formatted(field.fieldNumber(), field.name());

        if (field.type() == Field.FieldType.ONE_OF) {
            return comment + generateOneOfSizeLines((OneOfField) field, modelClassName);
        } else if (field.type() == Field.FieldType.MAP) {
            return comment + generateMapSizeLines((MapField) field);
        } else if (field.repeated()) {
            return comment + generateRepeatedSizeLines(field, fieldName);
        } else {
            return comment + generateSingularSizeLines(field, fieldName, modelClassName);
        }
    }

    /**
     * Generate size lines for a OneOf field (XDR discriminated union).
     */
    private static String generateOneOfSizeLines(final OneOfField oneOfField, final String modelClassName) {
        final String fieldName = oneOfField.nameCamelFirstLower();

        final String switchCases = oneOfField.fields().stream()
                .map(arm -> {
                    final String enumCase = Common.camelToUpperSnake(arm.name());
                    final String sizeCode = generateValueSizeCode(
                            arm, "item.%s().as()".formatted(fieldName), modelClassName, true);
                    // spotless:off
                    return """
                            case %s -> size += %s;"""
                            .formatted(enumCase, sizeCode);
                    // spotless:on
                })
                .collect(Collectors.joining("\n        "));

        // spotless:off
        return """
                size += 4; // discriminant
                if (item.%s().hasValue()) {
                    switch (item.%s().kind()) {
                        %s
                    }
                }
                """
                .formatted(fieldName, fieldName, switchCases);
        // spotless:on
    }

    /**
     * Generate size lines for a Map field.
     */
    private static String generateMapSizeLines(final MapField mapField) {
        final String fieldName = mapField.nameCamelFirstLower();
        final Field keyField = mapField.keyField();
        final Field valueField = mapField.valueField();

        final String keyType = keyField.type() == Field.FieldType.MESSAGE
                ? ((SingleField) keyField).messageType()
                : keyField.type() == Field.FieldType.ENUM
                        ? keyField.javaFieldTypeBase()
                        : keyField.type().boxedType;
        final String valueType = valueField.type() == Field.FieldType.MESSAGE
                ? ((SingleField) valueField).messageType()
                : valueField.type() == Field.FieldType.ENUM
                        ? valueField.javaFieldTypeBase()
                        : valueField.type().boxedType;

        final String keySize = generateValueSizeCode(keyField, "k", null, false);
        final String valueSize = generateValueSizeCode(valueField, "v", null, false);

        // spotless:off
        return """
                size += 4; // count
                if (!item.%s().isEmpty()) {
                    final PbjMap<%s, %s> pbjMap = (PbjMap<%s, %s>) item.%s();
                    for (%s k : pbjMap.getSortedKeys()) {
                        %s v = pbjMap.get(k);
                        size += %s;
                        size += %s;
                    }
                }
                """
                .formatted(
                        fieldName,
                        keyType, valueType, keyType, valueType, fieldName,
                        keyType,
                        valueType,
                        keySize,
                        valueSize);
        // spotless:on
    }

    /**
     * Generate size lines for a repeated field (XDR variable-length array).
     */
    private static String generateRepeatedSizeLines(final Field field, final String fieldName) {
        final String elemType = field.javaFieldTypeBase();
        final String elemSize = generateValueSizeCode(field, "elem", null, false);

        // spotless:off
        return """
                size += 4; // count
                for (final %s elem : item.%s()) {
                    size += %s;
                }
                """
                .formatted(elemType, fieldName, elemSize);
        // spotless:on
    }

    /**
     * Generate size lines for a singular field (XDR optional: presence flag + value).
     */
    private static String generateSingularSizeLines(
            final Field field, final String fieldName, final String modelClassName) {
        final String presenceCheck = generatePresenceCheck(field, fieldName);
        final String varName = fieldName + "_present";
        final String sizeCode = generateValueSizeCode(field, "item.%s()".formatted(fieldName), modelClassName, false);

        // spotless:off
        return """
                final boolean %s = %s;
                size += 4; // presence flag
                if (%s) {
                    size += %s;
                }
                """
                .formatted(varName, presenceCheck, varName, sizeCode);
        // spotless:on
    }

    /**
     * Generate the boolean presence check expression for a singular field.
     */
    private static String generatePresenceCheck(final Field field, final String fieldName) {
        if (field.optionalValueType()) {
            return "item.%s() != null".formatted(fieldName);
        }
        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32,
                 INT64, UINT64, SINT64, FIXED64, SFIXED64,
                 FLOAT, DOUBLE -> "item.%s() != 0".formatted(fieldName);
            case BOOL -> "item.%s()".formatted(fieldName);
            case STRING -> "!item.%s().isEmpty()".formatted(fieldName);
            case BYTES -> "item.%s() != null && item.%s() != Bytes.EMPTY".formatted(fieldName, fieldName);
            case ENUM -> "item.%sProtoOrdinal() != 0".formatted(fieldName);
            case MESSAGE -> "item.%s() != null".formatted(fieldName);
            default -> throw new UnsupportedOperationException(
                    "Unsupported field type for presence check: " + field.type());
        };
    }

    /**
     * Generate the expression for the size of a field's value (without presence flag overhead).
     *
     * @param field the field to size
     * @param valueExpr the Java expression that yields the value
     * @param modelClassName the model class name (used for enum type references)
     * @param isOneOfArm true if generating code inside a oneOf switch case (value needs cast)
     * @return a Java expression evaluating to the number of bytes for this value
     */
    private static String generateValueSizeCode(
            final Field field, final String valueExpr, final String modelClassName, final boolean isOneOfArm) {
        if (field.optionalValueType()) {
            return generateOptionalValueSizeCode(field, valueExpr);
        }

        final String castExpr = isOneOfArm
                ? "(%s) %s".formatted(field.javaFieldType(), valueExpr)
                : valueExpr;

        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32, BOOL, ENUM, FLOAT -> "4";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64, DOUBLE -> "8";
            case STRING -> "XdrWriterTools.sizeOfString(%s)".formatted(castExpr);
            case BYTES -> "XdrWriterTools.sizeOfOpaque(%s)".formatted(castExpr);
            case MESSAGE -> {
                if (field instanceof SingleField sf) {
                    final String codecRef = sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
                    yield "%s.measureRecord(%s)".formatted(codecRef, castExpr);
                } else {
                    throw new UnsupportedOperationException("MESSAGE field is not a SingleField: " + field);
                }
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported field type for size: " + field.type());
        };
    }

    /**
     * Generate size code for optional wrapper types (StringValue, Int32Value, etc.).
     * Presence is already known to be true at the call site.
     */
    private static String generateOptionalValueSizeCode(final Field field, final String valueExpr) {
        if (!(field instanceof SingleField sf)) {
            throw new UnsupportedOperationException("Optional value type is not a SingleField: " + field);
        }
        return switch (sf.messageType()) {
            case "StringValue" -> "XdrWriterTools.sizeOfString(%s)".formatted(valueExpr);
            case "BoolValue", "Int32Value", "UInt32Value", "FloatValue" -> "4";
            case "Int64Value", "UInt64Value", "DoubleValue" -> "8";
            case "BytesValue" -> "XdrWriterTools.sizeOfOpaque(%s)".formatted(valueExpr);
            default -> throw new UnsupportedOperationException(
                    "Unhandled optional message type: " + sf.messageType());
        };
    }
}
