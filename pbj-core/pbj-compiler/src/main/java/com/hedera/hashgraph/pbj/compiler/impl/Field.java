package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.Set;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;

/**
 * Interface for SingleFields and OneOfFields
 */
@SuppressWarnings("unused")
public interface Field {

	/**
	 * Is this field a repeated field. Repeated fields are lists of values rather than a single value.
	 *
	 * @return true if this field is a list and false if it is a single value
	 */
	boolean repeated();

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
		return snakeToCamel(name(),true);
	}

	/**
	 * Get this fields name converted to camel case with the first letter lower case
	 *
	 * @return this fields name converted
	 */
	default String nameCamelFirstLower() {
		return snakeToCamel(name(),false);
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
	 * Add all the needed imports for this field to the supplied set.
	 *
	 * @param imports set of imports to add to, this contains packages not classes. They are always imported as ".*".
	 * @param modelImports if imports for this field's generated model classes should be added
	 * @param parserImports if imports for this field's generated parser classes should be added
	 * @param writerImports if imports for this field's generated writer classes should be added
	 * @param testImports if imports for this field's generated test classes should be added
	 */
	void addAllNeededImports(Set<String> imports, boolean modelImports,boolean parserImports,
			final boolean writerImports, final boolean testImports);

	/**
	 * Get the java code to parse the value for this field from input
	 *
	 * @return java source code to parse
	 */
	String parseCode();

	/**
	 * Get the fully qualified parser class for message type for message fields
	 *
	 * @return fully qualified class name for parser class
	 */
	String parserClass();

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
	 * Get the java doc comment for this field
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
	default OneOfField parent() {
		return null;
	}

	/**
	 * Field type enum for use in field classes
	 */
	enum FieldType {
		MESSAGE("Object", "null", TYPE_LENGTH_DELIMITED),
		ENUM("int", "null", TYPE_VARINT),
		INT32("int", "0", TYPE_VARINT),
		UINT32("int", "0", TYPE_VARINT),
		SINT32("int", "0", TYPE_VARINT),
		INT64("long", "0", TYPE_VARINT),
		UINT64("long", "0", TYPE_VARINT),
		SINT64("long", "0", TYPE_VARINT),
		FLOAT("float", "0", TYPE_FIXED32),
		FIXED32("int", "0", TYPE_FIXED32),
		SFIXED32("int", "0", TYPE_FIXED32),
		DOUBLE("double", "0", TYPE_FIXED64),
		FIXED64("long", "0", TYPE_FIXED64),
		SFIXED64("long", "0", TYPE_FIXED64),
		STRING("String", "\"\"", TYPE_LENGTH_DELIMITED),
		BOOL("boolean", "false", TYPE_VARINT),
		BYTES("ReadOnlyDataBuffer", "ReadOnlyDataBuffer.EMPTY_BUFFER", TYPE_LENGTH_DELIMITED),
		ONE_OF("OneOf", "null", 0 );// BAD TYPE

		public final String javaType;
		public final String javaDefault;
		public final int wireType;

		FieldType(String javaType, final String javaDefault, int wireType) {
			this.javaType = javaType;
			this.javaDefault = javaDefault;
			this.wireType = wireType;
		}

		String fieldType() {
			return name();
		}

		public int wireType() {
			return wireType;
		}

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

		static FieldType of(Protobuf3Parser.Type_Context typeContext,  final ContextualLookupHelper lookupHelper) {
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
				throw new IllegalArgumentException("Unknown field type: "+typeContext);
			}
		}
	}
}
