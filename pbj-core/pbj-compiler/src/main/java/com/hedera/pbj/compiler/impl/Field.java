// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.Common.TYPE_FIXED32;
import static com.hedera.pbj.compiler.impl.Common.TYPE_FIXED64;
import static com.hedera.pbj.compiler.impl.Common.TYPE_LENGTH_DELIMITED;
import static com.hedera.pbj.compiler.impl.Common.TYPE_VARINT;
import static com.hedera.pbj.compiler.impl.Common.snakeToCamel;

import com.hedera.pbj.compiler.impl.generators.protobuf.CodecWriteByteArrayMethodGenerator;
import com.hedera.pbj.compiler.impl.generators.protobuf.CodecWriteMethodGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.MessageDefContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Interface for SingleFields and OneOfFields
 */
@SuppressWarnings("unused")
public interface Field {

    /** Annotation to add to fields that can't be set to null */
    String NON_NULL_ANNOTATION = "@NonNull";

    /**
     * Is this field a repeated field. Repeated fields are lists of values rather than a single value.
     *
     * @return true if this field is a list and false if it is a single value
     */
    boolean repeated();

    /**
     * Returns the field's max size relevant to repeated or length-encoded fields.
     * The returned value has no meaning for scalar fields (BOOL, INT, etc.).
     * A negative value means that the parser is free to enforce any generic limit it may be using for all fields.
     * A non-negative value would override the generic limit used by the parser for this particular field.
     * Note that PBJ currently doesn't support setting maxSize for individual fields,
     * so currently the method returns -1 and the parser always uses the generic limit
     * (see `Codec.parse(..., int maxSize)` for details.)
     */
    default long maxSize() {
        return -1;
    }

    /**
     * Get the field number, the number of the field in the parent message
     *
     * @return this fields number
     */
    int fieldNumber();

    /**
     * Get this fields name in original case and format
     *
     * @return this fields name
     */
    String name();

    /**
     * Get this fields name converted to camel case with the first letter upper case
     *
     * @return this fields name converted
     */
    default String nameCamelFirstUpper() {
        return snakeToCamel(name(), true);
    }

    /**
     * Get this fields name converted to camel case with the first letter lower case
     *
     * @return this fields name converted
     */
    @NonNull
    default String nameCamelFirstLower() {
        return snakeToCamel(name(), false);
    }

    /**
     * Get the field type for this field, the field type is independent of repeated
     *
     * @return this fields type
     */
    FieldType type();

    /**
     * Get the protobuf field type for this field
     *
     * @return this fields type in protobuf format
     */
    String protobufFieldType();

    /**
     * Get the Java field type for this field
     *
     * @return this fields type in Java format
     */
    String javaFieldType();

    /**
     * Get the Java storage field type for this field, this allows for fields to be stored in a different format than
     * they are presented to the user, for example String fields are stored as byte array. Normally this is the same as
     * {@link #javaFieldType()} except for a few special cases.
     *
     * @return this fields type in Java format
     */
    default String javaFieldStorageType() {
        return javaFieldType();
    }

    /**
     * Get the Java field type for this field.
     * Unlike {@link #javaFieldType()}, this method returns the base type for repeated and oneOf fields.
     *
     * @return this fields type in Java format
     */
    String javaFieldTypeBase();

    /**
     * Get the name for this type that is added to write/sizeof etc. methods.
     *
     * @return Name for type used in method names
     */
    String methodNameType();

    /**
     * Check if the storage type is different from the exposed type, this is true for String fields which are stored
     * as byte arrays.
     *
     * @return true if storage type is different from exposed type, otherwise false
     */
    default boolean hasDifferentStorageType() {
        return !javaFieldStorageType().equals(javaFieldType());
    }

    /**
     * Get the code for setting storage field, by default a no-op. This can be used for fields like String where we
     * want different internal storage types.
     *
     * @param inputVarName the name of the variable being passed in to the setter
     * @param msgDef the message definition
     * @param lookupHelper the lookup helper
     * @return code for setting storage field
     */
    default String storageFieldSetter(
            final String inputVarName, final MessageDefContext msgDef, final ContextualLookupHelper lookupHelper) {
        if (cannotBeNull()) {
            return inputVarName + " != null ? " + inputVarName + " : " + defaultValue(msgDef, lookupHelper);
        } else {
            return inputVarName;
        }
    }

    /**
     * Check if the field is a String, either native or boxed.
     * Useful because we use an array for storing strings in models, and arrays require very special handling
     * when it comes to equality checks, comparison, hash codes, etc.
     * @return true if this field is a string (and therefore, it's stored as a UTF-8 byte[] internally in models)
     */
    default boolean isString() {
        return type() == FieldType.STRING || (type() == FieldType.MESSAGE && "StringValue".equals(messageType()));
    }

    /**
     * Get the code for getting storage field, by default a no-op. This can be used for fields like String where we
     * want different internal storage types.
     *
     * @param fieldName the name of the field being accessed
     * @return code for getting storage field
     */
    default String storageFieldGetter(String fieldName) {
        return fieldName;
    }

    default String storageFieldWriter(final String modelClassName, final String schemaClassName) {
        return CodecWriteMethodGenerator.generateFieldWriteLines(
                this, modelClassName, schemaClassName, nameCamelFirstLower(), true, true);
    }

    default String storageFieldByteArrayWriter(final String modelClassName, final String schemaClassName) {
        return CodecWriteByteArrayMethodGenerator.generateFieldWriteLines(
                this, modelClassName, schemaClassName, nameCamelFirstLower(), true, true);
    }

    /**
     * Determine if this field cannot be null. For example, string and bytes fields can never be null.
     * Repeated fields also cannot be null. They all are initialized with default values (e.g. empty collections)
     * if they're missing from the input, so that models always return non-null values to clients.
     * <p>
     * Note that if this method returns `false`, this does NOT mean that the field can be null. For example,
     * boolean fields cannot be null because we use the unboxed primitive `boolean` type to store them,
     * even though this method will return `false` for them.
     * <p>
     * In other words, the return value of this method is ONLY meaningful for fields represented by Java objects,
     * and it's only really meaningful if/when it's equal to `true`.
     *
     * @return true if this field can be null, otherwise false
     */
    default boolean cannotBeNull() {
        if (repeated()) return true;
        return switch (type()) {
            case BYTES, STRING -> true;
            default -> false;
        };
    }

    /**
     * Get a set of annotations for this field.
     *
     * @return an empty string, or a string with Java annotations ending with a space
     */
    default String annotations() {
        return cannotBeNull() ? NON_NULL_ANNOTATION + " " : "";
    }

    /**
     * Gets the default value for this field
     *
     * @param msgDef the message definition
     * @param lookupHelper the lookup helper
     * @return the generated code
     */
    default String defaultValue(final MessageDefContext msgDef, final ContextualLookupHelper lookupHelper) {
        if (type() == Field.FieldType.ONE_OF) {
            return lookupHelper.getFullyQualifiedMessageClassname(FileType.CODEC, msgDef) + "." + javaDefault();
        } else {
            return javaDefault();
        }
    }

    /**
     * Add all the needed imports for this field to the supplied set.
     *
     * @param imports      collector of imports
     * @param modelImports if imports for this field's generated model classes should be added
     * @param codecImports if imports for this field's generated codec classes should be added
     * @param testImports  if imports for this field's generated test classes should be added
     */
    void addAllNeededImports(
            Consumer<String> imports, boolean modelImports, boolean codecImports, final boolean testImports);

    /**
     * Get the java code to parse the value for this field from input
     *
     * @return java source code to parse
     */
    String parseCode();

    /**
     * Get the java code default value for this field, "null" for object types
     *
     * @return code for default value
     */
    String javaDefault();

    /**
     * Get the field definitions line of code for schema file for this field. One line for single fields and multiple
     * for oneofs.
     *
     * @return field definition lines of code
     */
    String schemaFieldsDef();

    /**
     * Get the schema case statement for getting the field definition by field number
     *
     * @return java source code for case statement to get field def for field number
     */
    String schemaGetFieldsDefCase();

    /**
     * Get the case statement for setting this method to go in parser set method code
     *
     * @return java source code for case statement setting this field
     */
    String parserFieldsSetMethodCase();

    /**
     * Get the java doc comment for this field, cleaned and ready to insert in output
     *
     * @return java doc comment
     */
    String comment();

    /**
     * Get if this field is deprecated or not
     *
     * @return true if field is deprecated, otherwise false
     */
    boolean deprecated();

    /**
     * Get the message type for this field if it is of type message otherwise null
     *
     * @return message type or null if not a message type field
     */
    default String messageType() {
        return null;
    }

    /**
     * Get if this field is an optional value type, optionals are handled in protobuf by value type objects for
     * primitives
     *
     * @return true if this field is option by use of a protobuf value type, otherwise false
     */
    default boolean optionalValueType() {
        return false;
    }

    /**
     * Get the parent field for this field, null if there is no parent like in the case of a single field.
     *
     * @return this fields parent field for oneof fields
     */
    default com.hedera.pbj.compiler.impl.OneOfField parent() {
        return null;
    }

    /**
     * Extract the name of the Java model class for a message type,
     * or null if the type is not a message.
     */
    static String extractMessageTypeName(final Protobuf3Parser.Type_Context typeContext) {
        return typeContext.messageType() == null
                ? null
                : typeContext.messageType().messageName().getText();
    }

    /** Check if the given Type_Context is a comparable message. */
    static boolean isMessageComparable(
            final Protobuf3Parser.Type_Context typeContext,
            final com.hedera.pbj.compiler.impl.ContextualLookupHelper lookupHelper) {
        return typeContext.messageType() == null ? false : lookupHelper.isComparable(typeContext.messageType());
    }

    /**
     * Extract the complete name of the Java model class for a message/enum type, including outer classes names,
     * or null if the type is not a message/enum.
     */
    static String extractCompleteClassName(
            final Protobuf3Parser.Type_Context typeContext,
            final com.hedera.pbj.compiler.impl.FileType fileType,
            final com.hedera.pbj.compiler.impl.ContextualLookupHelper lookupHelper) {
        if (typeContext.messageType() != null) return lookupHelper.getCompleteClass(typeContext.messageType());
        else if (typeContext.enumType() != null) return lookupHelper.getCompleteClass(typeContext.enumType());
        else return null;
    }

    /**
     * Extract the name of the Java package for a given FileType for a message/enum type,
     * or null if the type is not a message/enum.
     */
    static String extractTypePackage(
            final Protobuf3Parser.Type_Context typeContext,
            final com.hedera.pbj.compiler.impl.FileType fileType,
            final com.hedera.pbj.compiler.impl.ContextualLookupHelper lookupHelper) {
        if ((typeContext.messageType() != null
                        && typeContext.messageType().messageName().getText() != null)
                || (typeContext.enumType() != null
                        && typeContext.enumType().enumName().getText() != null)) {
            return lookupHelper.getPackageFieldType(fileType, typeContext);
        } else {
            return null;
        }
    }

    /**
     * Field type enum for use in field classes
     */
    enum FieldType {
        /** Protobuf message field type */
        MESSAGE("Object", "Object", "null", TYPE_LENGTH_DELIMITED),
        /** Protobuf enum(unsigned varint encoded int of ordinal) field type */
        ENUM("int", "Integer", "null", TYPE_VARINT),
        /** Protobuf int32(signed varint encoded int) field type */
        INT32("int", "Integer", "0", TYPE_VARINT),
        /** Protobuf uint32(unsigned varint encoded int) field type */
        UINT32("int", "Integer", "0", TYPE_VARINT),
        /** Protobuf sint32(signed zigzag varint encoded int) field type */
        SINT32("int", "Integer", "0", TYPE_VARINT),
        /** Protobuf int64(signed varint encoded long) field type */
        INT64("long", "Long", "0", TYPE_VARINT),
        /** Protobuf uint64(unsigned varint encoded long)  field type */
        UINT64("long", "Long", "0", TYPE_VARINT),
        /** Protobuf sint64(signed zigzag varint encoded long) field type */
        SINT64("long", "Long", "0", TYPE_VARINT),
        /** Protobuf float field type */
        FLOAT("float", "Float", "0", TYPE_FIXED32),
        /** Protobuf fixed int32(fixed encoding int) field type */
        FIXED32("int", "Integer", "0", TYPE_FIXED32),
        /** Protobuf sfixed int32(signed fixed encoding int) field type */
        SFIXED32("int", "Integer", "0", TYPE_FIXED32),
        /** Protobuf double field type */
        DOUBLE("double", "Double", "0", TYPE_FIXED64),
        /** Protobuf sfixed64(fixed encoding long) field type */
        FIXED64("long", "Long", "0", TYPE_FIXED64),
        /** Protobuf sfixed64(signed fixed encoding long) field type */
        SFIXED64("long", "Long", "0", TYPE_FIXED64),
        /** Protobuf string field type */
        STRING("String", "String", "PbjConstants.EMPTY_BYTES", TYPE_LENGTH_DELIMITED),
        /** Protobuf bool(boolean) field type */
        BOOL("boolean", "Boolean", "false", TYPE_VARINT),
        /** Protobuf bytes field type */
        BYTES("Bytes", "Bytes", "Bytes.EMPTY", TYPE_LENGTH_DELIMITED),
        /** Protobuf oneof field type, this is not a true field type in protobuf. Needed here for a few edge cases */
        ONE_OF("OneOf", "OneOf", "null", 0), // BAD TYPE
        // On the wire, a map is a repeated Message {key, value}, sorted in the natural order of keys for determinism.
        MAP("Map", "Map", "Collections.EMPTY_MAP", TYPE_LENGTH_DELIMITED);

        /** The type of field type in Java code */
        public final String javaType;
        /** The type of boxed field type in Java code */
        public final String boxedType;
        /** The field type default value in Java code */
        public final String javaDefault;
        /** The protobuf wire type for field type */
        public final int wireType;

        /**
         * Construct a new FieldType enum
         *
         * @param javaType The type of field type in Java code
         * @param boxedType The boxed type of the field type, e.g. Integer for an int field.
         * @param javaDefault The field type default value in Java code
         * @param wireType The protobuf wire type for field type
         */
        FieldType(String javaType, final String boxedType, final String javaDefault, int wireType) {
            this.javaType = javaType;
            this.boxedType = boxedType;
            this.javaDefault = javaDefault;
            this.wireType = wireType;
        }

        /**
         * Get the field type string = the enum name
         *
         * @return Field type string
         */
        String fieldType() {
            return name();
        }

        /**
         * Get the protobuf wire type for field type
         *
         * @return protobuf wire type for field type
         */
        public int wireType() {
            return wireType;
        }

        /**
         * Get the type of field type in Java code
         *
         * @param repeated if the field is repeated or not, java types are different for repeated field
         * @return The type of field type in Java code
         */
        @SuppressWarnings("DuplicatedCode")
        public String javaType(boolean repeated) {
            if (repeated) {
                return switch (javaType) {
                    case "int" -> "List<Integer>";
                    case "long" -> "List<Long>";
                    case "float" -> "List<Float>";
                    case "double" -> "List<Double>";
                    case "boolean" -> "List<Boolean>";
                    default -> "List<" + javaType + ">";
                };
            } else {
                return javaType;
            }
        }

        /**
         * Get the field type for a given parser context
         *
         * @param typeContext The parser context to get field type for
         * @param lookupHelper Lookup helper with global context
         * @return The field type enum for parser context
         */
        static FieldType of(
                Protobuf3Parser.Type_Context typeContext,
                final com.hedera.pbj.compiler.impl.ContextualLookupHelper lookupHelper) {
            if (typeContext.enumType() != null) {
                return FieldType.ENUM;
            } else if (typeContext.messageType() != null) {
                if (lookupHelper.isEnum(typeContext.messageType())) return FieldType.ENUM;
                return FieldType.MESSAGE;
            } else if (typeContext.INT32() != null) {
                return FieldType.INT32;
            } else if (typeContext.UINT32() != null) {
                return FieldType.UINT32;
            } else if (typeContext.SINT32() != null) {
                return FieldType.SINT32;
            } else if (typeContext.INT64() != null) {
                return FieldType.INT64;
            } else if (typeContext.UINT64() != null) {
                return FieldType.UINT64;
            } else if (typeContext.SINT64() != null) {
                return FieldType.SINT64;
            } else if (typeContext.FLOAT() != null) {
                return FieldType.FLOAT;
            } else if (typeContext.FIXED32() != null) {
                return FieldType.FIXED32;
            } else if (typeContext.SFIXED32() != null) {
                return FieldType.SFIXED32;
            } else if (typeContext.DOUBLE() != null) {
                return FieldType.DOUBLE;
            } else if (typeContext.FIXED64() != null) {
                return FieldType.FIXED64;
            } else if (typeContext.SFIXED64() != null) {
                return FieldType.SFIXED64;
            } else if (typeContext.STRING() != null) {
                return FieldType.STRING;
            } else if (typeContext.BOOL() != null) {
                return FieldType.BOOL;
            } else if (typeContext.BYTES() != null) {
                return FieldType.BYTES;
            } else {
                throw new IllegalArgumentException("Unknown field type: " + typeContext);
            }
        }

        /**
         * Get the field type for a given map key type parser context
         *
         * @param typeContext The parser context to get field type for
         * @param lookupHelper Lookup helper with global context
         * @return The field type enum for parser context
         */
        static FieldType of(
                Protobuf3Parser.KeyTypeContext typeContext,
                final com.hedera.pbj.compiler.impl.ContextualLookupHelper lookupHelper) {
            if (typeContext.INT32() != null) {
                return FieldType.INT32;
            } else if (typeContext.UINT32() != null) {
                return FieldType.UINT32;
            } else if (typeContext.SINT32() != null) {
                return FieldType.SINT32;
            } else if (typeContext.INT64() != null) {
                return FieldType.INT64;
            } else if (typeContext.UINT64() != null) {
                return FieldType.UINT64;
            } else if (typeContext.SINT64() != null) {
                return FieldType.SINT64;
            } else if (typeContext.FIXED32() != null) {
                return FieldType.FIXED32;
            } else if (typeContext.SFIXED32() != null) {
                return FieldType.SFIXED32;
            } else if (typeContext.FIXED64() != null) {
                return FieldType.FIXED64;
            } else if (typeContext.SFIXED64() != null) {
                return FieldType.SFIXED64;
            } else if (typeContext.STRING() != null) {
                return FieldType.STRING;
            } else if (typeContext.BOOL() != null) {
                return FieldType.BOOL;
            } else {
                throw new IllegalArgumentException("Unknown map key type: " + typeContext);
            }
        }
    }
}
