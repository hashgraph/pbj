package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.Set;

import static com.hedera.pbj.compiler.impl.Common.*;

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
	default OneOfField parent() {
		return null;
	}

	/**
	 * Field type enum for use in field classes
	 */
	enum FieldType {
		/** Protobuf message field type */
		MESSAGE("Object", "null", TYPE_LENGTH_DELIMITED),
		/** Protobuf enum(unsigned varint encoded int of ordinal) field type */
		ENUM("int", "null", TYPE_VARINT),
		/** Protobuf int32(signed varint encoded int) field type */
		INT32("int", "0", TYPE_VARINT),
		/** Protobuf uint32(unsigned varint encoded int) field type */
		UINT32("int", "0", TYPE_VARINT),
		/** Protobuf sint32(signed zigzag varint encoded int) field type */
		SINT32("int", "0", TYPE_VARINT),
		/** Protobuf int64(signed varint encoded long) field type */
		INT64("long", "0", TYPE_VARINT),
		/** Protobuf uint64(unsigned varint encoded long)  field type */
		UINT64("long", "0", TYPE_VARINT),
		/** Protobuf sint64(signed zigzag varint encoded long) field type */
		SINT64("long", "0", TYPE_VARINT),
		/** Protobuf float field type */
		FLOAT("float", "0", TYPE_FIXED32),
		/** Protobuf fixed int32(fixed encoding int) field type */
		FIXED32("int", "0", TYPE_FIXED32),
		/** Protobuf sfixed int32(signed fixed encoding int) field type */
		SFIXED32("int", "0", TYPE_FIXED32),
		/** Protobuf double field type */
		DOUBLE("double", "0", TYPE_FIXED64),
		/** Protobuf sfixed64(fixed encoding long) field type */
		FIXED64("long", "0", TYPE_FIXED64),
		/** Protobuf sfixed64(signed fixed encoding long) field type */
		SFIXED64("long", "0", TYPE_FIXED64),
		/** Protobuf string field type */
		STRING("String", "\"\"", TYPE_LENGTH_DELIMITED),
		/** Protobuf bool(boolean) field type */
		BOOL("boolean", "false", TYPE_VARINT),
		/** Protobuf bytes field type */
		BYTES("Bytes", "Bytes.EMPTY_BYTES", TYPE_LENGTH_DELIMITED),
		/** Protobuf oneof field type, this is not a true field type in protobuf. Needed here for a few edge cases */
		ONE_OF("OneOf", "null", 0 );// BAD TYPE

		/** The type of field type in Java code */
		public final String javaType;
		/** The field type default value in Java code */
		public final String javaDefault;
		/** The protobuf wire type for field type */
		public final int wireType;

		/**
		 * Construct a new FieldType enum
		 *
		 * @param javaType The type of field type in Java code
		 * @param javaDefault The field type default value in Java code
		 * @param wireType The protobuf wire type for field type
		 */
		FieldType(String javaType, final String javaDefault, int wireType) {
			this.javaType = javaType;
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
