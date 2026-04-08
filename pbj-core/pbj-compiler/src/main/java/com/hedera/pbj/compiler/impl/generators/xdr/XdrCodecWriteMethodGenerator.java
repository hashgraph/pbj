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
 * Code to generate the write method for XDR Codec classes.
 */
final class XdrCodecWriteMethodGenerator {

    /**
     * Generate the write() method for an XDR codec class.
     *
     * @param modelClassName the name of the model class being serialized
     * @param fields the list of fields in the message
     * @return the full write() method as a String
     */
    static String generateWriteMethod(final String modelClassName, final List<Field> fields) {
        final String fieldWriteLines = fields.stream()
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldWriteLines(field, modelClassName))
                .collect(Collectors.joining("\n"));

        // spotless:off
        return """
                /**
                 * Write out a $modelClass model to output stream in XDR format.
                 *
                 * @param item The input model data to write
                 * @param output The output stream to write to
                 * @throws IOException If there is a problem writing
                 */
                @Override
                public void write(@NonNull final $modelClass item, @NonNull final WritableSequentialData output) throws IOException {
                    $fieldWriteLines
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldWriteLines", fieldWriteLines)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    /**
     * Generate the write lines for a single field.
     */
    private static String generateFieldWriteLines(final Field field, final String modelClassName) {
        final String fieldName = field.nameCamelFirstLower();
        final String comment = "// [%d] - %s\n".formatted(field.fieldNumber(), field.name());

        if (field.type() == Field.FieldType.ONE_OF) {
            return comment + generateOneOfWriteLines((OneOfField) field, modelClassName);
        } else if (field.type() == Field.FieldType.MAP) {
            return comment + generateMapWriteLines((MapField) field);
        } else if (field.repeated()) {
            return comment + generateRepeatedWriteLines(field, fieldName);
        } else {
            return comment + generateSingularWriteLines(field, fieldName, modelClassName);
        }
    }

    /**
     * Generate write lines for a OneOf field (XDR discriminated union).
     */
    private static String generateOneOfWriteLines(final OneOfField oneOfField, final String modelClassName) {
        final String fieldName = oneOfField.nameCamelFirstLower();

        final String switchCases = oneOfField.fields().stream()
                .map(arm -> {
                    final String enumCase = Common.camelToUpperSnake(arm.name());
                    final String writeCode =
                            generateValueWriteCode(arm, "item.%s().as()".formatted(fieldName), modelClassName, true);
                    // spotless:off
                    return """
                            case %s -> {
                                %s
                            }"""
                            .formatted(enumCase, writeCode);
                    // spotless:on
                })
                .collect(Collectors.joining("\n        "));

        // spotless:off
        return """
                final int %s_discriminant_raw = ((EnumWithProtoMetadata)item.%s().kind()).protoOrdinal();
                final int %s_discriminant = %s_discriminant_raw < 0 ? 0 : %s_discriminant_raw;
                XdrWriterTools.writeInt(output, %s_discriminant);
                if (%s_discriminant != 0) {
                    switch (item.%s().kind()) {
                        case UNSET -> throw new IllegalStateException("UNSET oneof arm reached with non-zero discriminant");
                        %s
                    }
                }
                """
                .formatted(fieldName, fieldName, fieldName, fieldName, fieldName, fieldName, fieldName, fieldName, switchCases);
        // spotless:on
    }

    /**
     * Generate write lines for a Map field.
     */
    private static String generateMapWriteLines(final MapField mapField) {
        final String fieldName = mapField.nameCamelFirstLower();
        final Field keyField = mapField.keyField();
        final Field valueField = mapField.valueField();

        final String keyType = keyField.type() == Field.FieldType.MESSAGE
                ? ((SingleField) keyField).messageType()
                : keyField.type() == Field.FieldType.ENUM ? keyField.javaFieldTypeBase() : keyField.type().boxedType;
        final String valueType = valueField.type() == Field.FieldType.MESSAGE
                ? ((SingleField) valueField).messageType()
                : valueField.type() == Field.FieldType.ENUM
                        ? valueField.javaFieldTypeBase()
                        : valueField.type().boxedType;

        final String keyWrite = generateValueWriteCode(keyField, "k", null, false);
        final String valueWrite = generateValueWriteCode(valueField, "v", null, false);

        // spotless:off
        return """
                XdrWriterTools.writeInt(output, item.%s().size());
                if (!item.%s().isEmpty()) {
                    final PbjMap<%s, %s> pbjMap = (PbjMap<%s, %s>) item.%s();
                    for (%s k : pbjMap.getSortedKeys()) {
                        %s v = pbjMap.get(k);
                        %s
                        %s
                    }
                }
                """
                .formatted(
                        fieldName,
                        fieldName,
                        keyType, valueType, keyType, valueType, fieldName,
                        keyType,
                        valueType,
                        keyWrite,
                        valueWrite);
        // spotless:on
    }

    /**
     * Generate write lines for a repeated field (XDR variable-length array).
     */
    private static String generateRepeatedWriteLines(final Field field, final String fieldName) {
        final String elemType = field.javaFieldTypeBase();
        final String elemWrite = generateValueWriteCode(field, "elem", null, false);

        // spotless:off
        return """
                XdrWriterTools.writeInt(output, item.%s().size());
                for (final %s elem : item.%s()) {
                    %s
                }
                """
                .formatted(fieldName, elemType, fieldName, elemWrite);
        // spotless:on
    }

    /**
     * Generate write lines for a singular field (XDR optional: presence flag + value).
     */
    private static String generateSingularWriteLines(
            final Field field, final String fieldName, final String modelClassName) {
        final String presenceCheck = generatePresenceCheck(field, fieldName);
        final String varName = fieldName + "_present";
        final String writeCode = generateValueWriteCode(field, "item.%s()".formatted(fieldName), modelClassName, false);

        // spotless:off
        return """
                final boolean %s = %s;
                XdrWriterTools.writePresence(output, %s);
                if (%s) {
                    %s
                }
                """
                .formatted(varName, presenceCheck, varName, varName, writeCode);
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
            case INT32, UINT32, SINT32, FIXED32, SFIXED32, INT64, UINT64, SINT64, FIXED64, SFIXED64, FLOAT, DOUBLE ->
                "item.%s() != 0".formatted(fieldName);
            case BOOL -> "item.%s()".formatted(fieldName);
            case STRING -> "!item.%s().isEmpty()".formatted(fieldName);
            case BYTES -> "item.%s() != null && item.%s().length() > 0".formatted(fieldName, fieldName);
            case ENUM -> "item.%s() != null".formatted(fieldName);
            case MESSAGE -> "item.%s() != null".formatted(fieldName);
            default ->
                throw new UnsupportedOperationException("Unsupported field type for presence check: " + field.type());
        };
    }

    /**
     * Generate the code to write a field's value (without presence flag).
     *
     * @param field the field to write
     * @param valueExpr the Java expression that yields the value to write
     * @param modelClassName the model class name (used for enum type references)
     * @param isOneOfArm true if generating code inside a oneOf switch case (value needs cast)
     */
    private static String generateValueWriteCode(
            final Field field, final String valueExpr, final String modelClassName, final boolean isOneOfArm) {
        if (field.optionalValueType()) {
            // For optional wrapper types, the presence is already checked; write the inner value
            return generateOptionalValueWriteCode(field, valueExpr);
        }

        final String castExpr = isOneOfArm ? "(%s) %s".formatted(field.javaFieldType(), valueExpr) : valueExpr;

        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "XdrWriterTools.writeInt(output, %s);".formatted(castExpr);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 ->
                "XdrWriterTools.writeHyper(output, %s);".formatted(castExpr);
            case FLOAT -> "XdrWriterTools.writeFloat(output, %s);".formatted(castExpr);
            case DOUBLE -> "XdrWriterTools.writeDouble(output, %s);".formatted(castExpr);
            case BOOL -> "XdrWriterTools.writeBool(output, %s);".formatted(castExpr);
            case STRING -> "XdrWriterTools.writeString(output, %s);".formatted(castExpr);
            case BYTES -> "XdrWriterTools.writeOpaque(output, %s);".formatted(castExpr);
            case ENUM -> {
                if (isOneOfArm) {
                    yield "XdrWriterTools.writeEnum(output, ((%s) %s).protoOrdinal());"
                            .formatted(field.javaFieldType(), valueExpr);
                } else {
                    // For repeated enum, valueExpr is the element itself (already the enum object)
                    // For singular, valueExpr is item.fieldName() which returns the enum object
                    yield "XdrWriterTools.writeEnum(output, %s.protoOrdinal());".formatted(valueExpr);
                }
            }
            case MESSAGE -> {
                if (field instanceof SingleField sf) {
                    final String codecRef = sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
                    yield "%s.write(%s, output);".formatted(codecRef, castExpr);
                } else {
                    throw new UnsupportedOperationException("MESSAGE field is not a SingleField: " + field);
                }
            }
            default -> throw new UnsupportedOperationException("Unsupported field type for write: " + field.type());
        };
    }

    /**
     * Generate write code for optional wrapper types (StringValue, Int32Value, etc.).
     * Presence is already known to be true at the call site.
     */
    private static String generateOptionalValueWriteCode(final Field field, final String valueExpr) {
        if (!(field instanceof SingleField sf)) {
            throw new UnsupportedOperationException("Optional value type is not a SingleField: " + field);
        }
        return switch (sf.messageType()) {
            case "StringValue" -> "XdrWriterTools.writeString(output, %s);".formatted(valueExpr);
            case "BoolValue" -> "XdrWriterTools.writeBool(output, %s);".formatted(valueExpr);
            case "Int32Value", "UInt32Value" -> "XdrWriterTools.writeInt(output, %s);".formatted(valueExpr);
            case "Int64Value", "UInt64Value" -> "XdrWriterTools.writeHyper(output, %s);".formatted(valueExpr);
            case "FloatValue" -> "XdrWriterTools.writeFloat(output, %s);".formatted(valueExpr);
            case "DoubleValue" -> "XdrWriterTools.writeDouble(output, %s);".formatted(valueExpr);
            case "BytesValue" -> "XdrWriterTools.writeOpaque(output, %s);".formatted(valueExpr);
            default -> throw new UnsupportedOperationException("Unhandled optional message type: " + sf.messageType());
        };
    }
}
