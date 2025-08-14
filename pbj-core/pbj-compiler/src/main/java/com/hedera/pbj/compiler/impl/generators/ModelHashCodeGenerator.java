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
public class ModelHashCodeGenerator {
    /**
     * Generates the hashCode method
     *
     * @param fields the fields to use for the code generation
     *
     * @return the generated code
     */
    @NonNull
    static String generateHashCode(final List<Field> fields) {
        // Generate a call to private method that iterates through fields and calculates the hashcode
        final String statements = getFieldsHashCode(fields, "");
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
                    long $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash0;
            """.indent(DEFAULT_INDENT);

        bodyContent += statements;

        // TODO there should be a way to get a 64 bit hash from the unknown fields, but we don't have that yet.
        bodyContent +=
            """
                    if ($unknownFields != null) {
                        for (int i = 0; i < $unknownFields.size(); i++) {
                            $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$unknownFields.get(i).hashCode());
                        }
                    }
                    $hashCode = $intermediaryHash;
                }
                return $hashCode;
            }
            """.indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Recursively calculates the hashcode for a message fields.
     *
     * @param fields             The fields of this object.
     * @param generatedCodeSoFar The accumulated hash code so far.
     * @return The generated code for getting the hashCode value.
     */
    public static String getFieldsHashCode(final List<Field> fields, String generatedCodeSoFar) {
        for (Field f : fields) {
            if (f instanceof OneOfField oneOfField) {
                final String fieldName = f.nameCamelFirstLower() + ".value()";
                String caseStatements = "";
                for (final Field childField : oneOfField.fields()) {
                    if (!caseStatements.isEmpty()) caseStatements += "\n        ";
                    caseStatements += "case " + Common.camelToUpperSnake(childField.name()) + " -> XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,";
                    switch (childField.type()) {
                        case INT32, FIXED32, SINT32, SFIXED32, UINT32 -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case INT64, FIXED64, SINT64, SFIXED64, UINT64 -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case BOOL -> {
                            caseStatements +=
                                    "(" + childField.javaFieldType() + ")" + fieldName + "? (byte)1 : 0);";
                        }
                        case FLOAT -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case DOUBLE -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case BYTES -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case ENUM -> {
                            caseStatements +=
                                    "((" + childField.javaFieldType() + ")" + fieldName + ").protoOrdinal());";
                        }
                        case STRING -> {
                            caseStatements += "(" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case MESSAGE -> {
                            switch (childField.messageType()) {
                                case "StringValue" -> caseStatements += "(String)" + fieldName + ");";
                                case "Int32Value", "UInt32Value" -> caseStatements += "(int)" + fieldName + ");";
                                case "Int64Value", "UInt64Value" -> caseStatements += "(long)" + fieldName + ");";
                                case "FloatValue" -> caseStatements += "(float)" + fieldName + ");";
                                case "DoubleValue" -> caseStatements += "(double)" + fieldName + ");";
                                case "BytesValue" -> caseStatements += "(Bytes)" + fieldName + ");";
                                case "BoolValue" -> caseStatements += "(boolean)" + fieldName + "? (byte)1 : 0);";
                                default ->
                                    caseStatements += "((" + childField.javaFieldType() + ")" + fieldName
                                            + ").hashCode64());";
                            }
                        }
                    }
                }
                generatedCodeSoFar +=
                        ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                switch($fieldName.kind()) {
                                    $caseStatements
                                    default -> throw new IllegalStateException("Unknown one-of kind: " + $fieldName.kind());
                                }
                            }
                            """)
                                .replace("$caseStatements", caseStatements)
                                .replace("$fieldName", f.nameCamelFirstLower());

            } else if (f.optionalValueType()) {
                generatedCodeSoFar = getPrimitiveWrapperHashCodeGeneration(generatedCodeSoFar, f);
            } else if (f.repeated()) {
                generatedCodeSoFar = getRepeatedHashCodeGeneration(generatedCodeSoFar, f);
            } else {
                if (f.type() == FieldType.FIXED32
                        || f.type() == FieldType.INT32
                        || f.type() == FieldType.SFIXED32
                        || f.type() == FieldType.SINT32
                        || f.type() == FieldType.UINT32) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.FIXED64
                        || f.type() == FieldType.INT64
                        || f.type() == FieldType.SFIXED64
                        || f.type() == FieldType.SINT64
                        || f.type() == FieldType.UINT64) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.BOOL) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName? (byte)1 : 0);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.BYTES) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.ENUM) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName.protoOrdinal());
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.MAP) {
                    generatedCodeSoFar += getMapHashCodeGeneration(generatedCodeSoFar, (MapField) f);
                } else if (f.type() == FieldType.STRING) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.MESSAGE) { // process sub message
                    // If this is not an oneof field, we can use the hashCode64() method directly.
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName.hashCode64());
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else {
                    throw new RuntimeException("Unexpected field type for getting HashCode - "
                            + f.type().toString());
                }
            }
        }
        return generatedCodeSoFar.indent(DEFAULT_INDENT * 3);
    }

    /**
     * Get the hashcode codegen for an optional field.
     *
     * @param generatedCodeSoFar The string that the codegen is generated into.
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getPrimitiveWrapperHashCodeGeneration(String generatedCodeSoFar, Field f) {
        switch (f.messageType()) {
            case "StringValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "BoolValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName ? (byte)1 : 0);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "Int32Value", "UInt32Value" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "Int64Value", "UInt64Value" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "FloatValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "DoubleValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "BytesValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        $intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,$fieldName);
                    }
                    """)
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
    private static String getRepeatedHashCodeGeneration(String generatedCodeSoFar, Field f) {
        String addToHashLine =
                switch (f.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case BOOL -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o? (byte)1 : 0);";
                    case FLOAT -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case DOUBLE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case BYTES -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case ENUM -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o.protoOrdinal());";
                    case STRING -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o);";
                    case MESSAGE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,o.hashCode64());";
                    default ->
                        throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
                };

        generatedCodeSoFar +=
                ("""
                $fieldType list$$fieldName = $fieldName;
                if (list$$fieldName != null) {
                    for (var o : list$$fieldName) {
                        if (o != null) {
                            $addToHashLine
                        }
                   }
                }
                """)
                        .replace("$addToHashLine", addToHashLine)
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
    private static String getMapHashCodeGeneration(String generatedCodeSoFar, final MapField f) {
        final SingleField keyField = f.keyField();
        final SingleField valueField = f.valueField();
        String keyCode =
                switch (keyField.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case BOOL -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k ? (byte)1 : 0);";
                    case FLOAT -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case DOUBLE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case BYTES -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case ENUM -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k.protoOrdinal());";
                    case STRING -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k);";
                    case MESSAGE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,k.hashCode64());";
                    default ->
                        throw new UnsupportedOperationException("Unhandled key message type:" + keyField.messageType());
                };
        String valueCode =
                switch (valueField.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case BOOL -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v ? (byte)1 : 0);";
                    case FLOAT -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case DOUBLE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case BYTES -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case ENUM -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v.protoOrdinal());";
                    case STRING -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v);";
                    case MESSAGE -> "$intermediaryHash = XXH3_64.DEFAULT_INSTANCE.hash($intermediaryHash,v.hashCode64());";
                    default ->
                        throw new UnsupportedOperationException(
                                "Unhandled value message type:" + valueField.messageType());
                };
        generatedCodeSoFar +=
                ("""
                for ($keyType k : ((PbjMap<$keyType,$valueType>) $fieldName).getSortedKeys()) {
                    if (k != null) $keyCode
                    final $valueType v = $fieldName.get(k);
                    if (v != null) $valueCode
                }
                """)
                        .replace("$keyType", keyField.javaFieldTypeBoxed())
                        .replace("$valueType", valueField.javaFieldTypeBoxed())
                        .replace("$keyCode", keyCode)
                        .replace("$valueCode", valueCode)
                        .replace("$fieldName", f.nameCamelFirstLower());
        return generatedCodeSoFar;
    }
}
