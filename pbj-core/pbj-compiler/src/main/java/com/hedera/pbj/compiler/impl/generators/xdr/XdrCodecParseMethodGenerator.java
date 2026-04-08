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
 * Code to generate the parse() method for XDR Codec classes.
 *
 * <p>XDR parsing is positional — fields are read sequentially in ascending field number order.
 * Singular fields are wrapped in XDR optional (presence flag + value). Repeated and map fields
 * use a 4-byte count prefix. OneOf fields use a 4-byte discriminant (the proto field number of
 * the active arm, or 0 for UNSET).
 */
@SuppressWarnings("StringConcatenationInsideStringBufferAppend")
final class XdrCodecParseMethodGenerator {

    private XdrCodecParseMethodGenerator() {}

    /**
     * Generate the parse() method for an XDR codec class.
     *
     * @param modelClassName the name of the model class being parsed
     * @param fields the list of fields in the message, in model-constructor order
     * @return the full parse() method as a String
     */
    static String generateParseMethod(final String modelClassName, final List<Field> fields) {
        // Temp variable declarations (model constructor order)
        final String fieldDefs = fields.stream()
                .map(field -> generateFieldDef(field, modelClassName))
                .collect(Collectors.joining("\n"));

        // Read lines sorted by field number (ascending) — XDR is positional
        final String fieldReadLines = fields.stream()
                .sorted(Comparator.comparingInt(Field::fieldNumber))
                .map(field -> generateFieldReadLines(field, modelClassName))
                .collect(Collectors.joining("\n"));

        // Make repeated lists read-only after parsing
        final String listMakeReadOnly = fields.stream()
                .filter(Field::repeated)
                .map(field -> "if (temp_" + field.name() + " instanceof UnmodifiableArrayList ual) ual.makeReadOnly();")
                .collect(Collectors.joining("\n"));

        // Constructor argument list — all temps + null for unknownFields
        final String fieldsList = fields.stream()
                        .map(field -> "temp_" + field.name())
                        .collect(Collectors.joining(", "))
                + (fields.isEmpty() ? "" : ", ") + "null";

        // spotless:off
        return """
                @Override
                @NonNull
                public $modelClass parse(
                        @NonNull final ReadableSequentialData input,
                        final boolean strictMode,
                        final boolean parseUnknownFields,
                        final int maxDepth,
                        final int maxSize) throws ParseException {
                    if (maxDepth < 0) {
                        throw new ParseException("Reached maximum allowed depth of nested messages");
                    }
                    try {
                        // -- TEMP STATE FIELDS --------------------------------------
                        $fieldDefs

                        // -- READ FIELDS IN ORDER (ascending field number) ----------
                        $fieldReadLines
                        // -- MAKE LISTS READ-ONLY -----------------------------------
                        $listMakeReadOnly
                        return new $modelClass($fieldsList);
                    } catch (final Exception anyException) {
                        if (anyException instanceof ParseException parseException) {
                            throw parseException;
                        }
                        throw new ParseException(anyException);
                    }
                }
                """
                .replace("$modelClass", modelClassName)
                .replace("$fieldDefs", fieldDefs)
                .replace("$fieldReadLines", fieldReadLines)
                .replace("$listMakeReadOnly", listMakeReadOnly)
                .replace("$fieldsList", fieldsList)
                .indent(DEFAULT_INDENT);
        // spotless:on
    }

    // =========================================================================
    // Field definition (temp variable declaration)
    // =========================================================================

    private static String generateFieldDef(final Field field, final String modelClassName) {
        if (field.type() == Field.FieldType.ONE_OF) {
            final OneOfField oneOfField = (OneOfField) field;
            return "%s<%s> temp_%s = new %s<>(%s.UNSET, null);".formatted(
                    oneOfField.className(),
                    oneOfField.getEnumClassRef(),
                    field.name(),
                    oneOfField.className(),
                    oneOfField.getEnumClassRef());
        } else if (field.type() == Field.FieldType.MAP) {
            return "Map temp_%s = PbjMap.EMPTY;".formatted(field.name());
        } else if (field.repeated()) {
            return "List temp_%s = Collections.emptyList();".formatted(field.name());
        } else if (field.optionalValueType()) {
            // Optional wrapper types (StringValue, Int32Value, etc.) — nullable Java type
            return "%s temp_%s = null;".formatted(field.javaFieldType(), field.name());
        } else if (field.type() == Field.FieldType.ENUM) {
            // ENUM fields: use Object to match the model's objectForEnum constructor variant
            return "Object temp_%s = null;".formatted(field.name());
        } else if (field.type() == Field.FieldType.MESSAGE) {
            return "%s temp_%s = null;".formatted(field.javaFieldType(), field.name());
        } else {
            // Scalar types: use the FieldType's default value
            return "%s temp_%s = %s;".formatted(
                    field.type().javaType, field.name(), field.type().javaDefault);
        }
    }

    // =========================================================================
    // Field read lines dispatch
    // =========================================================================

    private static String generateFieldReadLines(final Field field, final String modelClassName) {
        final String comment = "// [%d] - %s\n".formatted(field.fieldNumber(), field.name());
        if (field.type() == Field.FieldType.ONE_OF) {
            return comment + generateOneOfReadLines((OneOfField) field, modelClassName);
        } else if (field.type() == Field.FieldType.MAP) {
            return comment + generateMapReadLines((MapField) field);
        } else if (field.repeated()) {
            return comment + generateRepeatedReadLines(field, modelClassName);
        } else {
            return comment + generateSingularReadLines(field, modelClassName);
        }
    }

    // =========================================================================
    // Singular field (XDR optional: presence flag + value)
    // =========================================================================

    private static String generateSingularReadLines(final Field field, final String modelClassName) {
        final String name = field.name();

        if (field.optionalValueType()) {
            return generateOptionalValueReadLines(field);
        }

        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readInt(input);
                    if (temp_$name == 0) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readHyper(input);
                    if (temp_$name == 0) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case FLOAT ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readFloat(input);
                    if (temp_$name == 0) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case DOUBLE ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readDouble(input);
                    if (temp_$name == 0) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case BOOL ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readBool(input);
                    if (!temp_$name) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case STRING ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readString(input, maxSize);
                    if (temp_$name.isEmpty()) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case BYTES ->
                """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = XdrParserTools.readOpaque(input, maxSize);
                    if (temp_$name.length() == 0) throw new ParseException(
                            "Canonical encoding violation: presence=1 with default value for $name");
                }
                """.replace("$name", name);
            case ENUM -> generateSingularEnumReadLines(field);
            case MESSAGE -> generateSingularMessageReadLines(field);
            default -> throw new UnsupportedOperationException(
                    "Unsupported field type for singular XDR parse: " + field.type());
        };
    }

    private static String generateSingularEnumReadLines(final Field field) {
        final String name = field.name();
        final String enumClass = Common.snakeToCamel(field.messageType(), true);
        // spotless:off
        return """
                if (XdrParserTools.readPresence(input)) {
                    final int $name_ordinal = XdrParserTools.readEnum(input);
                    final $enumClass $name_val = $enumClass.fromProtobufOrdinal($name_ordinal);
                    if ($name_val == $enumClass.UNRECOGNIZED) {
                        throw new ParseException("Unrecognized enum value " + $name_ordinal + " for $name");
                    }
                    temp_$name = $name_val;
                }
                """
                .replace("$enumClass", enumClass)
                .replace("$name", name);
        // spotless:on
    }

    private static String generateSingularMessageReadLines(final Field field) {
        final String name = field.name();
        if (!(field instanceof SingleField sf)) {
            throw new UnsupportedOperationException("MESSAGE field is not a SingleField: " + field);
        }
        final String codecRef = sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
        // spotless:off
        return """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = $codecRef.parse(input, false, false, maxDepth - 1, maxSize);
                }
                """
                .replace("$name", name)
                .replace("$codecRef", codecRef);
        // spotless:on
    }

    private static String generateOptionalValueReadLines(final Field field) {
        final String name = field.name();
        if (!(field instanceof SingleField sf)) {
            throw new UnsupportedOperationException("Optional value type is not a SingleField: " + field);
        }
        final String readExpr = switch (sf.messageType()) {
            case "StringValue" -> "XdrParserTools.readString(input, maxSize)";
            case "BoolValue" -> "XdrParserTools.readBool(input)";
            case "Int32Value", "UInt32Value" -> "XdrParserTools.readInt(input)";
            case "Int64Value", "UInt64Value" -> "XdrParserTools.readHyper(input)";
            case "FloatValue" -> "XdrParserTools.readFloat(input)";
            case "DoubleValue" -> "XdrParserTools.readDouble(input)";
            case "BytesValue" -> "XdrParserTools.readOpaque(input, maxSize)";
            default -> throw new UnsupportedOperationException(
                    "Unhandled optional message type: " + sf.messageType());
        };
        // spotless:off
        return """
                if (XdrParserTools.readPresence(input)) {
                    temp_$name = $readExpr;
                }
                """
                .replace("$name", name)
                .replace("$readExpr", readExpr);
        // spotless:on
    }

    // =========================================================================
    // Repeated field (XDR variable-length array: 4-byte count + N elements)
    // =========================================================================

    private static String generateRepeatedReadLines(final Field field, final String modelClassName) {
        final String name = field.name();
        final String loopBody;

        if (field.type() == Field.FieldType.ENUM) {
            final String enumClass = Common.snakeToCamel(field.messageType(), true);
            // spotless:off
            loopBody = """
                    final int $name_elem_ordinal = XdrParserTools.readEnum(input);
                    final $enumClass $name_elem_val = $enumClass.fromProtobufOrdinal($name_elem_ordinal);
                    if ($name_elem_val == $enumClass.UNRECOGNIZED) {
                        throw new ParseException("Unrecognized enum value " + $name_elem_ordinal + " for $name element");
                    }
                    temp_$name = ProtoParserTools.addToList(temp_$name, $name_elem_val);"""
                    .replace("$enumClass", enumClass)
                    .replace("$name", name);
            // spotless:on
        } else {
            final String elemReadExpr = generateElementReadExpr(field, modelClassName);
            loopBody = "temp_$name = ProtoParserTools.addToList(temp_$name, $elemReadExpr);"
                    .replace("$name", name)
                    .replace("$elemReadExpr", elemReadExpr);
        }

        // spotless:off
        return """
                final int $name_count = input.readInt();
                if ($name_count < 0) throw new ParseException("Negative count for $name: " + $name_count);
                if ($name_count > 65536) throw new ParseException("Count " + $name_count + " exceeds max 65536 for $name");
                for (int $name_i = 0; $name_i < $name_count; $name_i++) {
                    $loopBody
                }
                """
                .replace("$name", name)
                .replace("$loopBody", loopBody);
        // spotless:on
    }

    /**
     * Generate the read expression for a single element of a repeated field (no presence flag).
     * Must not be called for ENUM type fields — those need multi-statement handling.
     */
    private static String generateElementReadExpr(final Field field, final String modelClassName) {
        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "XdrParserTools.readInt(input)";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "XdrParserTools.readHyper(input)";
            case FLOAT -> "XdrParserTools.readFloat(input)";
            case DOUBLE -> "XdrParserTools.readDouble(input)";
            case BOOL -> "XdrParserTools.readBool(input)";
            case STRING -> "XdrParserTools.readString(input, maxSize)";
            case BYTES -> "XdrParserTools.readOpaque(input, maxSize)";
            case MESSAGE -> {
                if (field instanceof SingleField sf) {
                    final String codecRef =
                            sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
                    yield "%s.parse(input, false, false, maxDepth - 1, maxSize)".formatted(codecRef);
                }
                throw new UnsupportedOperationException("MESSAGE field is not a SingleField: " + field);
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported element type for repeated XDR parse: " + field.type());
        };
    }

    // =========================================================================
    // Map field (XDR variable-length array of key-value pairs)
    // =========================================================================

    private static String generateMapReadLines(final MapField mapField) {
        final String name = mapField.name();
        final Field keyField = mapField.keyField();
        final Field valueField = mapField.valueField();
        final String keyReadExpr = generateMapScalarReadExpr(keyField);

        final String loopBody;
        if (valueField.type() == Field.FieldType.ENUM) {
            final String enumClass = Common.snakeToCamel(valueField.messageType(), true);
            // spotless:off
            loopBody = """
                    final var $name_k = $keyReadExpr;
                    final int $name_v_ordinal = XdrParserTools.readEnum(input);
                    final $enumClass $name_v = $enumClass.fromProtobufOrdinal($name_v_ordinal);
                    if ($name_v == $enumClass.UNRECOGNIZED) {
                        throw new ParseException("Unrecognized enum value " + $name_v_ordinal + " for $name value");
                    }
                    temp_$name = ProtoParserTools.addToMap(temp_$name, $name_k, $name_v);"""
                    .replace("$enumClass", enumClass)
                    .replace("$keyReadExpr", keyReadExpr)
                    .replace("$name", name);
            // spotless:on
        } else {
            final String valueReadExpr = generateMapValueReadExpr(valueField);
            // spotless:off
            loopBody = """
                    final var $name_k = $keyReadExpr;
                    final var $name_v = $valueReadExpr;
                    temp_$name = ProtoParserTools.addToMap(temp_$name, $name_k, $name_v);"""
                    .replace("$keyReadExpr", keyReadExpr)
                    .replace("$valueReadExpr", valueReadExpr)
                    .replace("$name", name);
            // spotless:on
        }

        // spotless:off
        return """
                final int $name_count = input.readInt();
                if ($name_count < 0) throw new ParseException("Negative count for $name: " + $name_count);
                if ($name_count > 65536) throw new ParseException("Count " + $name_count + " exceeds max 65536 for $name");
                for (int $name_i = 0; $name_i < $name_count; $name_i++) {
                    $loopBody
                }
                """
                .replace("$name", name)
                .replace("$loopBody", loopBody);
        // spotless:on
    }

    /**
     * Generate a simple read expression for a map key (scalar types only per protobuf spec).
     */
    private static String generateMapScalarReadExpr(final Field field) {
        return switch (field.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "XdrParserTools.readInt(input)";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "XdrParserTools.readHyper(input)";
            case FLOAT -> "XdrParserTools.readFloat(input)";
            case DOUBLE -> "XdrParserTools.readDouble(input)";
            case BOOL -> "XdrParserTools.readBool(input)";
            case STRING -> "XdrParserTools.readString(input, maxSize)";
            case BYTES -> "XdrParserTools.readOpaque(input, maxSize)";
            default -> throw new UnsupportedOperationException(
                    "Unsupported map key/value type for XDR parse: " + field.type());
        };
    }

    /**
     * Generate a read expression for a map value (scalars or messages).
     * ENUM values must be handled in {@link #generateMapReadLines} for UNRECOGNIZED validation.
     */
    private static String generateMapValueReadExpr(final Field field) {
        if (field.type() == Field.FieldType.MESSAGE) {
            if (field instanceof SingleField sf) {
                final String codecRef = sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
                return "%s.parse(input, false, false, maxDepth - 1, maxSize)".formatted(codecRef);
            }
            throw new UnsupportedOperationException("MESSAGE map value is not a SingleField: " + field);
        } else {
            return generateMapScalarReadExpr(field);
        }
    }

    // =========================================================================
    // OneOf field (XDR discriminated union: 4-byte discriminant + arm value)
    // =========================================================================

    private static String generateOneOfReadLines(final OneOfField oneOfField, final String modelClassName) {
        final String name = oneOfField.name();
        final String enumClassRef = oneOfField.getEnumClassRef();
        final String className = oneOfField.className();

        final String switchCases = oneOfField.fields().stream()
                .map(arm -> generateOneOfCaseBody(arm, name, enumClassRef, className))
                .collect(Collectors.joining("\n"));

        // spotless:off
        return """
                final int $name_discriminant = input.readInt();
                switch ($name_discriminant) {
                    case 0 -> {} // UNSET
                    $switchCases
                    default -> throw new ParseException("Unknown $name discriminant: " + $name_discriminant);
                }
                """
                .replace("$name", name)
                .replace("$switchCases", switchCases);
        // spotless:on
    }

    private static String generateOneOfCaseBody(
            final Field arm,
            final String oneOfName,
            final String enumClassRef,
            final String className) {
        final int armFieldNumber = arm.fieldNumber();
        final String enumCase = Common.camelToUpperSnake(arm.name());

        if (arm.type() == Field.FieldType.ENUM) {
            final String enumClass = Common.snakeToCamel(arm.messageType(), true);
            // spotless:off
            return """
                    case $armFieldNumber -> {
                        final int $armName_ordinal = XdrParserTools.readEnum(input);
                        final $enumClass $armName_val = $enumClass.fromProtobufOrdinal($armName_ordinal);
                        if ($armName_val == $enumClass.UNRECOGNIZED) {
                            throw new ParseException("Unrecognized enum value " + $armName_ordinal + " for $oneOfName arm");
                        }
                        temp_$oneOfName = new $className<>($enumClassRef.$enumCase, $armName_val);
                    }"""
                    .replace("$armFieldNumber", Integer.toString(armFieldNumber))
                    .replace("$enumClassRef", enumClassRef)
                    .replace("$enumClass", enumClass)
                    .replace("$armName", arm.name())
                    .replace("$className", className)
                    .replace("$enumCase", enumCase)
                    .replace("$oneOfName", oneOfName);
            // spotless:on
        } else {
            final String valueReadExpr = generateArmValueReadExpr(arm);
            // spotless:off
            return """
                    case $armFieldNumber -> {
                        temp_$oneOfName = new $className<>($enumClassRef.$enumCase, $valueReadExpr);
                    }"""
                    .replace("$armFieldNumber", Integer.toString(armFieldNumber))
                    .replace("$className", className)
                    .replace("$enumClassRef", enumClassRef)
                    .replace("$enumCase", enumCase)
                    .replace("$valueReadExpr", valueReadExpr)
                    .replace("$oneOfName", oneOfName);
            // spotless:on
        }
    }

    /**
     * Generate the value-read expression for a non-enum arm of a oneOf switch.
     */
    private static String generateArmValueReadExpr(final Field arm) {
        // Optional wrapper types (Int64Value, StringValue, etc.) have no codec; read directly
        if (arm.optionalValueType() && arm instanceof SingleField sf) {
            return switch (sf.messageType()) {
                case "StringValue" -> "XdrParserTools.readString(input, maxSize)";
                case "BoolValue" -> "XdrParserTools.readBool(input)";
                case "Int32Value", "UInt32Value" -> "XdrParserTools.readInt(input)";
                case "Int64Value", "UInt64Value" -> "XdrParserTools.readHyper(input)";
                case "FloatValue" -> "XdrParserTools.readFloat(input)";
                case "DoubleValue" -> "XdrParserTools.readDouble(input)";
                case "BytesValue" -> "XdrParserTools.readOpaque(input, maxSize)";
                default -> throw new UnsupportedOperationException(
                        "Unhandled optional value type in oneOf arm: " + sf.messageType());
            };
        }
        return switch (arm.type()) {
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "XdrParserTools.readInt(input)";
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> "XdrParserTools.readHyper(input)";
            case FLOAT -> "XdrParserTools.readFloat(input)";
            case DOUBLE -> "XdrParserTools.readDouble(input)";
            case BOOL -> "XdrParserTools.readBool(input)";
            case STRING -> "XdrParserTools.readString(input, maxSize)";
            case BYTES -> "XdrParserTools.readOpaque(input, maxSize)";
            case MESSAGE -> {
                if (arm instanceof SingleField sf) {
                    final String codecRef =
                            sf.messageTypeModelPackage() + "." + sf.completeClassName() + ".XDR";
                    yield "%s.parse(input, false, false, maxDepth - 1, maxSize)".formatted(codecRef);
                }
                throw new UnsupportedOperationException("MESSAGE oneOf arm is not a SingleField: " + arm);
            }
            default -> throw new UnsupportedOperationException(
                    "Unsupported oneOf arm type for XDR parse: " + arm.type());
        };
    }
}
