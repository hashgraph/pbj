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
                    final HashingWritableSequentialData hashingStream = XXH3_64.DEFAULT_INSTANCE.hashingWritableSequentialData();
            """.indent(DEFAULT_INDENT);

        bodyContent += statements;

        bodyContent +=
            """
                    if ($unknownFields != null) {
                        for (int i = 0; i < $unknownFields.size(); i++) {
                            hashingStream.writeInt($unknownFields.get(i).hashCode());
                        }
                    }
                    $hashCode = hashingStream.computeHash();
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
                    caseStatements += "case " + Common.camelToUpperSnake(childField.name()) + " -> hashingStream.write";
                    switch (childField.type()) {
                        case INT32, FIXED32, SINT32, SFIXED32, UINT32 -> {
                            caseStatements += "Int((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case INT64, FIXED64, SINT64, SFIXED64, UINT64 -> {
                            caseStatements += "Long((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case BOOL -> {
                            caseStatements +=
                                    "Byte((" + childField.javaFieldType() + ")" + fieldName + "? (byte)1 : 0);";
                        }
                        case FLOAT -> {
                            caseStatements += "Float((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case DOUBLE -> {
                            caseStatements += "Double((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case BYTES -> {
                            caseStatements += "Bytes((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case ENUM -> {
                            caseStatements +=
                                    "Int(((" + childField.javaFieldType() + ")" + fieldName + ").protoOrdinal());";
                        }
                        case STRING -> {
                            caseStatements += "UTF8((" + childField.javaFieldType() + ")" + fieldName + ");";
                        }
                        case MESSAGE -> {
                            switch (childField.messageType()) {
                                case "StringValue" -> caseStatements += "UTF8((String)" + fieldName + ");";
                                case "Int32Value", "UInt32Value" -> caseStatements += "Int((int)" + fieldName + ");";
                                case "Int64Value", "UInt64Value" -> caseStatements += "Long((long)" + fieldName + ");";
                                case "FloatValue" -> caseStatements += "Float((float)" + fieldName + ");";
                                case "DoubleValue" -> caseStatements += "Double((double)" + fieldName + ");";
                                case "BytesValue" -> caseStatements += "Bytes((Bytes)" + fieldName + ");";
                                case "BoolValue" -> caseStatements += "Byte((boolean)" + fieldName + "? (byte)1 : 0);";
                                default ->
                                    caseStatements += "Long(((" + childField.javaFieldType() + ")" + fieldName
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
                                hashingStream.writeInt($fieldName);
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
                                hashingStream.writeLong($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.BOOL) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                hashingStream.writeByte($fieldName? (byte)1 : 0);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.FLOAT) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                hashingStream.writeFloat($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.DOUBLE) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != DEFAULT.$fieldName) {
                                hashingStream.writeDouble($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.BYTES) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                hashingStream.writeBytes($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.ENUM) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                hashingStream.writeInt($fieldName.protoOrdinal());
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.MAP) {
                    generatedCodeSoFar += getMapHashCodeGeneration(generatedCodeSoFar, (MapField) f);
                } else if (f.type() == FieldType.STRING) {
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                hashingStream.writeUTF8($fieldName);
                            }
                            """)
                                    .replace("$fieldName", f.nameCamelFirstLower());
                } else if (f.type() == FieldType.MESSAGE) { // process sub message
                    // If this is not an oneof field, we can use the hashCode64() method directly.
                    generatedCodeSoFar +=
                            ("""
                            if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                                hashingStream.writeLong($fieldName.hashCode64());
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
                        hashingStream.writeUTF8($fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "BoolValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeByte($fieldName ? (byte)1 : 0);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "Int32Value", "UInt32Value" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeInt($fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "Int64Value", "UInt64Value" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeLong($fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "FloatValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeFloat($fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "DoubleValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeDouble($fieldName);
                    }
                    """)
                                .replace("$fieldName", f.nameCamelFirstLower());
            case "BytesValue" ->
                generatedCodeSoFar +=
                        ("""
                    if ($fieldName != null && !$fieldName.equals(DEFAULT.$fieldName)) {
                        hashingStream.writeBytes($fieldName);
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
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "hashingStream.writeInt(o);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "hashingStream.writeLong(o);";
                    case BOOL -> "hashingStream.writeByte(o? (byte)1 : 0);";
                    case FLOAT -> "hashingStream.writeFloat(o);";
                    case DOUBLE -> "hashingStream.writeDouble(o);";
                    case BYTES -> "hashingStream.writeBytes(o);";
                    case ENUM -> "hashingStream.writeInt(o.protoOrdinal());";
                    case STRING -> "hashingStream.writeUTF8(o);";
                    case MESSAGE -> "hashingStream.writeLong(o.hashCode64());";
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
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "hashingStream.writeInt(k);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "hashingStream.writeLong(k);";
                    case BOOL -> "hashingStream.writeByte(k ? (byte)1 : 0);";
                    case FLOAT -> "hashingStream.writeFloat(k);";
                    case DOUBLE -> "hashingStream.writeDouble(k);";
                    case BYTES -> "hashingStream.writeBytes(k);";
                    case ENUM -> "hashingStream.writeInt(k.protoOrdinal());";
                    case STRING -> "hashingStream.writeUTF8(k);";
                    case MESSAGE -> "hashingStream.writeLong(k.hashCode64());";
                    default ->
                        throw new UnsupportedOperationException("Unhandled key message type:" + keyField.messageType());
                };
        String valueCode =
                switch (valueField.type()) {
                    case FIXED32, INT32, SFIXED32, SINT32, UINT32 -> "hashingStream.writeInt(v);";
                    case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> "hashingStream.writeLong(v);";
                    case BOOL -> "hashingStream.writeByte(v ? (byte)1 : 0);";
                    case FLOAT -> "hashingStream.writeFloat(v);";
                    case DOUBLE -> "hashingStream.writeDouble(v);";
                    case BYTES -> "hashingStream.writeBytes(v);";
                    case ENUM -> "hashingStream.writeInt(v.protoOrdinal());";
                    case STRING -> "hashingStream.writeUTF8(v);";
                    case MESSAGE -> "hashingStream.writeLong(v.hashCode64());";
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
