// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Common;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.Field.FieldType;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Generates the hashCode() and hashCode64() methods for a model class.
 */
@SuppressWarnings("StringConcatenationInLoop")
public class ModelHashCodeGenerator {
    /** The mixing code for the hashCode generation. After each field */
    private static final String MIX_CODE = "    " + """
                            if($xx_haveCarry) $xx_acc = mixPair($xx_acc, $xx_carry, v, $xx_pairIndex++); else $xx_carry = v;
                            $xx_haveCarry = !$xx_haveCarry;
                            $xx_total += 8;
                        """.trim();
    /** The mixing code for the hashCode generation. After first field */
    private static final String MIX_CODE_FIRST_FIELD = "    " + """
                            $xx_carry = v;
                            $xx_haveCarry =  true;
                            $xx_total += 8;
                        """.trim();


    /**
     * Generates the hashCode method
     *
     * @param fields the fields to use for the code generation
     *
     * @return the generated code
     */
    @NonNull
    static String generateHashCode(final List<Field> fields) {
        // spotless:off
        String bodyContent =
            """
            /**
            * Override the default hashCode method for to make hashCode better distributed and follows protobuf rules
            * for default values. This is important for backward compatibility. This also lazy computes and caches the
            * hashCode for future calls. It is designed to be thread safe.
            */
            @Override
            public int hashCode() {
                return(int)hashCode64();
            }
            
            /**
            * Extended 64bit hashCode method for to make hashCode better distributed and follows protobuf rules
            * for default values. This is important for backward compatibility. This also lazy computes and caches the
            * hashCode for future calls. It is designed to be thread safe.
            */
            public long hashCode64() {
                // The $hashCode field is subject to a benign data race, making it crucial to ensure that any
                // observable result of the calculation in this method stays correct under any possible read of this
                // field. Necessary restrictions to allow this to be correct without explicit memory fences or similar
                // concurrency primitives is that we can ever only write to this field for a given Model object
                // instance, and that the computation is idempotent and derived from immutable state.
                // This is the same trick used in java.lang.String.hashCode() to avoid synchronization.
            
                if($hashCode == -1) {
                    long $xx_acc = 0L;
                    long $xx_total = 0L;
                    long $xx_carry = 0L;
                    boolean $xx_haveCarry = false;
                    int $xx_pairIndex = 0;
            """.indent(DEFAULT_INDENT);

        // Generate a call to private method that iterates through fields and calculates the hashcode
        bodyContent += getFieldsHashCode(fields);
        bodyContent +=
            """
                    if ($unknownFields != null) {
                        for (int i = 0; i < $unknownFields.size(); i++) {
                            // For unknown fields, if they are default value they will not be on the wire
                            // there for they will not be in the $unknownFields list. So we can safely
                            // assume that if we are here, the field is not default value.
                            final long v = $unknownFields.get(i).hashCode64();
                            $mixCode
                        }
                    }
                    if ($xx_haveCarry) {
                        $xx_acc = XXH3FieldHash.mixTail8($xx_acc, $xx_carry, $xx_pairIndex);
                    }
                    $hashCode = XXH3FieldHash.finish($xx_acc, $xx_total);
                }
                return $hashCode;
            }
            """.replace("$mixCode", MIX_CODE.indent(DEFAULT_INDENT+DEFAULT_INDENT))
                .indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Recursively calculates the hashcode for a message fields.
     *
     * @param fields             The fields of this object.
     * @return The generated code for getting the hashCode value.
     */
    public static String getFieldsHashCode(final List<Field> fields) {
        String generatedCode = "";
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            final boolean isFirstField = i == 0;
            if (f instanceof OneOfField oneOfField) {
                final String fieldName = f.nameCamelFirstLower() + ".value()";
                String caseStatements = "";
                for (final Field childField : oneOfField.fields()) {
                    if (!caseStatements.isEmpty()) {
                        caseStatements += "\n        ";
                    }
                    caseStatements += "case " + Common.camelToUpperSnake(childField.name()) + " -> ";
                    switch (childField.type()) {
                        case INT32, FIXED32, SINT32, SFIXED32, UINT32, STRING, DOUBLE, FLOAT, BOOL ->
                                caseStatements += "toLong((" + childField.javaFieldType() + ")" + fieldName + ");";
                        case INT64, FIXED64, SINT64, SFIXED64, UINT64 ->
                                caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ";";
                        case BYTES -> caseStatements +=
                                "((" + childField.javaFieldType() + ")" + fieldName + ").hashCode64();";
                        case ENUM -> caseStatements +=
                                "toLong(((" + childField.javaFieldType() + ")" + fieldName + ").protoOrdinal());";
                        case MESSAGE -> {
                            switch (childField.messageType()) {
                                case "StringValue" -> caseStatements += "toLong((String)" + fieldName + ");";
                                case "Int32Value", "UInt32Value" -> caseStatements += "toLong((int)" + fieldName + ");";
                                case "Int64Value", "UInt64Value" -> caseStatements += "(long)" + fieldName + ";";
                                case "FloatValue" -> caseStatements += "toLong((float)" + fieldName + ");";
                                case "DoubleValue" -> caseStatements += "toLong((double)" + fieldName + ");";
                                case "BytesValue" -> caseStatements += "((Bytes)" + fieldName + ").hashCode64();";
                                case "BoolValue" -> caseStatements += "toLong((boolean)" + fieldName + ");";
                                default -> caseStatements += "((" + childField.javaFieldType() + ")" + fieldName
                                        + ").hashCode64();";
                            }
                        }
                    }
                }
                generatedCode +=
                        ("""
                                if ($fieldName != DEFAULT.$fieldName) {
                                    final long v = switch($fieldName.kind()) {
                                        $caseStatements
                                        default -> throw new IllegalStateException("Unknown one-of kind: " + $fieldName.kind());
                                    };
                                    $mixCode
                                }
                                """)
                                .replace("$caseStatements", caseStatements)
                                .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                                .replace("$fieldName", f.nameCamelFirstLower());

            } else if (f.optionalValueType()) {
                generatedCode = getPrimitiveWrapperHashCodeGeneration(generatedCode, f, isFirstField);
            } else if (f.repeated()) {
                generatedCode = getRepeatedHashCodeGeneration(generatedCode, f, isFirstField);
            } else if (f.type() == FieldType.MAP) {
                generatedCode += getMapHashCodeGeneration(generatedCode, (MapField) f, isFirstField);
            } else {
                String start = switch (f.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32, FLOAT, DOUBLE -> """
                            if ($fieldName != 0) {
                                final long v = toLong($fieldName);
                            """;
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> """
                            if ($fieldName != 0) {
                                final long v = $fieldName;
                            """;
                    case BOOL -> """
                            if ($fieldName) {
                                final long v = 1; // 1 for boolean true, false is default so never hashed
                            """;
                    case BYTES -> """
                            if ($fieldName.length() > 0) {
                                final long v = $fieldName.hashCode64(); // compute xxh3 hash of bytes
                            """;
                    case ENUM -> """
                            if ($fieldName != null && $fieldName.protoOrdinal() != 0) {
                                final long v = toLong($fieldName.protoOrdinal()); // LE semantics if desired
                            """;
                    case STRING -> """
                            if ($fieldName.length() > 0) {
                                final long v = toLong($fieldName); // compute xxh3 hash of UFT8 bytes
                            """;
                    case MESSAGE ->  // process sub message
                            """
                                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                        final long v = $fieldName.hashCode64(); // get sub message hashCode64
                                    """;
                    default -> throw new RuntimeException("Unexpected field type for getting HashCode - "
                            + f.type().toString());
                };
                generatedCode +=
                        ("""
                                $start$mixCode
                                }
                                """)
                                .replace("$start", start)
                                .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                                .replace("$fieldName", f.nameCamelFirstLower());
            }
        }
        return generatedCode.indent(DEFAULT_INDENT * 3);
    }

    /**
     * Get the hashcode codegen for an optional field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     * @param isFirstField Whether this is the first field in the hashCode generation.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getPrimitiveWrapperHashCodeGeneration(String generatedCodeSoFar, Field f, boolean isFirstField) {
        switch (f.messageType()) {
            case "Int32Value", "UInt32Value", "BoolValue", "FloatValue", "DoubleValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        final long v = toLong($fieldName);
                        $mixCode
                    }
                    """)
                    .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                    .replace("$fieldName", f.nameCamelFirstLower());
            case "StringValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName.length() > 0) {
                        final long v = toLong($fieldName);
                        $mixCode
                    }
                    """)
                    .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                    .replace("$fieldName", f.nameCamelFirstLower());
            case "Int64Value", "UInt64Value" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        final long v = $fieldName;
                        $mixCode
                    }
                    """)
                    .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                    .replace("$fieldName", f.nameCamelFirstLower());
            case "BytesValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName.length() > 0) {
                        final long v = $fieldName.hashCode64();
                        $mixCode
                    }
                    """)
                    .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                    .replace("$fieldName", f.nameCamelFirstLower());
            default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
        }
        return generatedCodeSoFar;
    }

    /**
     * Get the hashcode codegen for a repeated field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getRepeatedHashCodeGeneration(String generatedCodeSoFar, Field f, boolean isFirstField) {
        String addValueCode =
                switch (f.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "toLong(o)";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "o";
                    case BOOL -> "toLong(o)";
                    case FLOAT -> "toLong(o)";
                    case DOUBLE -> "toLong(o)";
                    case BYTES -> "o.hashCode64()";
                    case ENUM -> "toLong(o.protoOrdinal())";
                    case STRING -> "toLong(o)";
                    case MESSAGE -> "o.hashCode64()";
                    default ->
                        throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
                };

        generatedCodeSoFar +=
                ("""
                $fieldType list$$fieldName = $fieldName;
                if (list$$fieldName != null) {
                    for (var o : list$$fieldName) {
                        if (o != null) {
                            final long v = $addValueCode;
                            $mixCode
                        }
                   }
                }
                """)
                        .replace("$addValueCode", addValueCode)
                        .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                        .replace("$fieldType", f.javaFieldType())
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }

    /**
     * Get the hashcode codegen for a map field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getMapHashCodeGeneration(String generatedCodeSoFar, final MapField f, boolean isFirstField) {
        final SingleField keyField = f.keyField();
        final SingleField valueField = f.valueField();
        String keyCode =
                switch (keyField.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "toLong($m_key)";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "$m_key";
                    case BOOL -> "toLong($m_key)";
                    case FLOAT -> "toLong($m_key)";
                    case DOUBLE -> "toLong($m_key)";
                    case BYTES -> "$m_key.hashCode64()";
                    case ENUM -> "toLong($m_key.protoOrdinal())";
                    case STRING -> "toLong($m_key)";
                    case MESSAGE -> "$m_key.hashCode64()";
                    default ->
                        throw new UnsupportedOperationException("Unhandled key message type:" + keyField.messageType());
                };
        String valueCode =
                switch (valueField.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "toLong($m_value)";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "$m_value";
                    case BOOL -> "toLong($m_value)";
                    case FLOAT -> "toLong($m_value)";
                    case DOUBLE -> "toLong($m_value)";
                    case BYTES -> "$m_value.hashCode64()";
                    case ENUM -> "toLong($m_value.protoOrdinal())";
                    case STRING -> "toLong($m_value)";
                    case MESSAGE -> "$m_value.hashCode64()";
                    default ->
                        throw new UnsupportedOperationException(
                                "Unhandled value message type:" + valueField.messageType());
                };
        generatedCodeSoFar +=
                ("""
                for ($keyType $m_key : ((PbjMap<$keyType,$valueType>) $fieldName).getSortedKeys()) {
                    if ($m_key != null) {
                        final long v = $keyCode;
                        $mixCode
                    }
                    final $valueType $m_value = $fieldName.get($m_key);
                    if ($m_value != null) {
                        final long v = $valueCode;
                        $mixCode
                    }
                }
                """)
                        .replace("$keyType", keyField.javaFieldTypeBoxed())
                        .replace("$mixCode", isFirstField ? MIX_CODE_FIRST_FIELD : MIX_CODE)
                        .replace("$valueType", valueField.javaFieldTypeBoxed())
                        .replace("$keyCode", keyCode)
                        .replace("$valueCode", valueCode)
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }
}
