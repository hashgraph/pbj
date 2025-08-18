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
    private static final String MIX_CODE = """
                        if(($xx_fieldCount & 1) == 0) {
                            $xx_acc += mixPair($xx_value, $xx_carry, $xx_fieldCount++ >> 1);
                        } else {
                            $xx_carry = $xx_value;
                            $xx_fieldCount ++;
                        }""";

    /**
     * Generates the hashCode method
     *
     * @param fields the fields to use for the code generation
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
                    int $xx_fieldCount = 0; // pairCount
                    long $xx_carry = 0L;
                    long $xx_value = 0L;
            """.indent(DEFAULT_INDENT);

        // Generate a call to private method that iterates through fields and calculates the hashcode
        bodyContent += fieldsContainMapsOrRepeatedListFields(fields) ?
                getFieldsHashCode(fields) : getFieldsHashCodeFixedLength(fields);
        bodyContent +=
            """
                    if ($unknownFields != null) {
                        for (int i = 0; i < $unknownFields.size(); i++) {
                            // For unknown fields, if they are default value they will not be on the wire
                            // there for they will not be in the $unknownFields list. So we can safely
                            // assume that if we are here, the field is not default value.
                            if (($xx_fieldCount & 1) == 0) {
                                $xx_acc += mixPair($xx_carry, $unknownFields.get(i).hashCode64(), $xx_fieldCount++ >> 1);
                            } else {
                                $xx_carry = $unknownFields.get(i).hashCode64();
                                $xx_fieldCount ++;
                            }
                        }
                    }
                    if (($xx_fieldCount & 1) == 0) {
                        // If we have an odd number of pairs, we need to mix the last carry value
                        $xx_acc += mixTail8($xx_carry, $xx_fieldCount >> 1);
                    }
                    $hashCode = finish($xx_acc, (long)$xx_fieldCount << 3); // $xx_fieldCount * 8
                }
                return $hashCode;
            }
            """.indent(DEFAULT_INDENT);
        // spotless:on
        return bodyContent;
    }

    /**
     * Checks if the fields contain maps or repeated list fields.
     *
     * @param fields The fields to check.
     * @return true if the fields contain maps or repeated list fields, false otherwise.
     */
    private static boolean fieldsContainMapsOrRepeatedListFields(final List<Field> fields) {
        for (final Field f : fields) {
            if (f.type() == FieldType.MAP || f.repeated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds code for each field, fast path for case where there are no repeating field types like map or list
     *
     * @param fields             The fields of this object.
     * @return The generated code for getting the hashCode value.
     */
    private static String getFieldsHashCodeFixedLength(final List<Field> fields) {
        String generatedCode = "";
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            final boolean isOddField = (i % 2) != 0;
            final String dstVarName =  isOddField ? "$xx_value":"$xx_carry";
            if (f instanceof OneOfField oneOfField) {
                final String fieldName = f.nameCamelFirstLower() + ".value()";
                String caseStatements = "";
                for (final Field childField : oneOfField.fields()) {
                    if (!caseStatements.isEmpty()) {
                        caseStatements += "\n    ";
                    }
                    caseStatements += "case " + Common.camelToUpperSnake(childField.name()) + " -> " +
                            getFieldGetLongCode(childField, fieldName, true) +";";
                }
                generatedCode +=
                        ("""
                                $dstVarName = switch($fieldName.kind()) {
                                    $caseStatements
                                    default -> throw new IllegalStateException("Unknown one-of kind: " + $fieldName.kind());
                                };$mixCode
                                """)
                                .replace("$caseStatements", caseStatements)
                                .replace("$mixCode", isOddField ? "\n$xx_acc += mixPair($xx_value, $xx_carry, "+(i/2)+");" : "")
                                .replace("$dstVarName", dstVarName)
                                .replace("$fieldName", f.nameCamelFirstLower());
            } else {
                generatedCode +=
                        ("""
                                $dstVarName = $varSetCode;$mixCode
                                """)
                                .replace("$varSetCode", getFieldGetLongCode(f, f.nameCamelFirstLower(), false))
                                .replace("$mixCode", isOddField ? "\n$xx_acc += mixPair($xx_value, $xx_carry, "+(i/2)+");" : "")
                                .replace("$dstVarName", dstVarName);
            }
        }
        generatedCode += "$xx_fieldCount = " + fields.size() + ";\n";
        return generatedCode.indent(DEFAULT_INDENT * 3);
    }

    /**
     * Recursively calculates the hashcode for a message fields.
     *
     * @param fields             The fields of this object.
     * @return The generated code for getting the hashCode value.
     */
    private static String getFieldsHashCode(final List<Field> fields) {
        String generatedCode = "";
        for (int i = 0; i < fields.size(); i++) {
            final Field f = fields.get(i);
            if (f instanceof OneOfField oneOfField) {
                final String fieldName = f.nameCamelFirstLower() + ".value()";
                String caseStatements = "";
                for (final Field childField : oneOfField.fields()) {
                    if (!caseStatements.isEmpty()) {
                        caseStatements += "\n    ";
                    }
                    caseStatements += "case " + Common.camelToUpperSnake(childField.name()) + " -> " +
                            getFieldGetLongCode(childField, fieldName, true) + ";";
                }
                generatedCode +=
                        ("""
                                $xx_value = switch($fieldName.kind()) {
                                    $caseStatements
                                    default -> throw new IllegalStateException("Unknown one-of kind: " + $fieldName.kind());
                                };
                                $mixCode
                                """)
                                .replace("$caseStatements", caseStatements)
                                .replace("$mixCode", MIX_CODE)
                                .replace("$fieldName", f.nameCamelFirstLower());
            } else if (f.repeated()) {
                generatedCode += ("""
                        for (var o : $fieldName) {
                            $xx_value = $addValueCode;
                        $mixCode
                        }
                        """)
                        .replace("$addValueCode", getFieldGetLongCode(f, "o", false))
                        .replace("$mixCode",MIX_CODE.indent(DEFAULT_INDENT*2))
                        .replace("$fieldType", f.javaFieldType())
                        .replace("$fieldName", f.nameCamelFirstLower());
            } else if (f.type() == FieldType.MAP) {
                final SingleField keyField = ((MapField) f).keyField();
                final SingleField valueField = ((MapField) f).valueField();
                String keyCode = getFieldGetLongCode(keyField, "$m_key", false);
                String valueCode = getFieldGetLongCode(valueField, "$m_value", false);
                generatedCode +=
                        ("""
                        for ($keyType $m_key : ((PbjMap<$keyType,$valueType>) $fieldName).getSortedKeys()) {
                            if ($m_key != null) {
                                $xx_value = $keyCode;
                        $mixCode
                            }
                            final $valueType $m_value = $fieldName.get($m_key);
                            if ($m_value != null) {
                                $xx_value = $valueCode;
                        $mixCode
                            }
                        }
                        """)
                                .replace("$keyType", keyField.javaFieldTypeBoxed())
                                .replace("$mixCode", MIX_CODE.indent(DEFAULT_INDENT * 2))
                                .replace("$valueType", valueField.javaFieldTypeBoxed())
                                .replace("$keyCode", keyCode)
                                .replace("$valueCode", valueCode)
                                .replace("$fieldName", ((MapField) f).nameCamelFirstLower());
            } else {
                generatedCode +=
                        ("""
                                $xx_value = $varSetCode;
                                $mixCode
                                """)
                                .replace("$varSetCode", getFieldGetLongCode(f, f.nameCamelFirstLower(), false))
                                .replace("$mixCode", MIX_CODE);
            }
        }
        return generatedCode.indent(DEFAULT_INDENT * 3);
    }

    /**
     * Get the hashcode codegen for an optional field.
     *
     * @param f The field for which to generate the hash code.
     *
     * @return Updated codegen string.
     */
    @NonNull
    private static String getFieldGetLongCode(final Field f, String fieldValueCode, final boolean needsCasting) {
        final String fieldValueCodeCasted = needsCasting ?  "((" + f.javaFieldType() + ")" + fieldValueCode + ")" : fieldValueCode;
        return switch (f.type()) {
            case FIXED32, INT32, SFIXED32, SINT32, UINT32, BOOL, FLOAT, DOUBLE, STRING -> "toLong("+fieldValueCodeCasted+")";
            case FIXED64, INT64, SFIXED64, SINT64, UINT64 -> fieldValueCodeCasted;
            case BYTES -> fieldValueCodeCasted+".hashCode64()";
            case ENUM -> "toLong("+fieldValueCodeCasted+".protoOrdinal())";
            case MESSAGE -> switch (f.messageType()) {
                case "StringValue" -> "toLong(" + fieldValueCodeCasted + ")";
                case "Int32Value", "UInt32Value" -> "toLong(" + fieldValueCodeCasted + ")";
                case "Int64Value", "UInt64Value" -> fieldValueCodeCasted ;
                case "FloatValue" -> "toLong(" + fieldValueCodeCasted + ")";
                case "DoubleValue" -> "toLong(" + fieldValueCodeCasted + ")";
                case "BytesValue" -> fieldValueCodeCasted + ".hashCode64()";
                case "BoolValue" -> "toLong(" + fieldValueCodeCasted + ")";
                default -> fieldValueCode +"==null?0:"+fieldValueCodeCasted+ ".hashCode64()";
            };
            default -> throw new UnsupportedOperationException("Unhandled optional message type:" + f.messageType());
        };
    }

}
