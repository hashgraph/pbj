package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.Set;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.snakeToCamel;

/**
 * Interface for SingleFields and OneOfFields
 */
public interface Field {

	/**
	 * Is this field a repeated field. Repeated fields are lists of values rather than a single value.
	 *
	 * @return true if this field is a list and false if it is a single value
	 */
	public boolean repeated();

	/**
	 * Get the field number, the number of the field in the parent message
	 *
	 * @return this fields number
	 */
	public int fieldNumber();

	/**
	 * Get this fields name in orginal case and format
	 *
	 * @return this fields name
	 */
	public String name();

	/**
	 * Get this fields name converted to camel case with the first leter upper case
	 *
	 * @return this fields name converted
	 */
	public default String nameCamelFirstUpper() {
		return snakeToCamel(name(),true);
	}

	/**
	 * Get this fields name converted to camel case with the first leter lower case
	 *
	 * @return this fields name converted
	 */
	public default String nameCamelFirstLower() {
		return snakeToCamel(name(),false);
	}

	/**
	 * Get the field type for this field, the field type is independent of repeated
	 *
	 * @return this fields type
	 */
	public FieldType type();

	/**
	 * Get the protobuf field type for this field
	 *
	 * @return this fields type in protobuf format
	 */
	public String protobufFieldType();

	/**
	 * Get the Java field type for this field
	 *
	 * @return this fields type in Java format
	 */
	public String javaFieldType();

	/**
	 * Add all the needed imports for this field to the supplied set.
	 *
	 * @param imports set of imports to add to, this contains packages not classes. They are always imported as ".*".
	 * @param modelImports if imports for this fields generated model classes should be added
	 * @param parserImports if imports for this fields generated parser classes should be added
	 * @param writerImports if imports for this fields generated writer classes should be added
	 * @param testImports if imports for this fields generated test classes should be added
	 */
	public void addAllNeededImports(Set<String> imports, boolean modelImports,boolean parserImports,
			final boolean writerImports, final boolean testImports);

	/**
	 * Get the java code to parse the value for this field from input
	 *
	 * @return java source code to parse
	 */
	public String parseCode();

	/**
	 * Get the java code default value for this field, "null" for object types
	 *
	 * @return code for default value
	 */
	public String javaDefault();

	/**
	 * Get the field definiations line of code for schema file for this field. One line for single fields and multiple
	 * for oneofs.
	 *
	 * @return field definition lines of code
	 */
	public String schemaFieldsDef();

	/**
	 * Get the schema case statement for getting the field definition by field number
	 *
	 * @return java source code for case statement to get field def for field number
	 */
	public String schemaGetFieldsDefCase();

	/**
	 * Get the case statement for seting this method to go in parser set method code
	 *
	 * @return java source code for case statement setting this field
	 */
	public String parserFieldsSetMethodCase();

	/**
	 * Get the java doc comment for this field
	 *
	 * @return java doc comment
	 */
	public String comment();

	/**
	 * Get if this field is depericated or not
	 *
	 * @return true if field is depericated, otherwise false
	 */
	public boolean depricated();

	/**
	 * Get the message type for this field if it is of type message otherwise null
	 *
	 * @return message type or null if not a message type field
	 */
	public default String messageType() {
		return null;
	};

	/**
	 * Get if this field is optional, optionals are handled in protobuf by value type objects for primatives
	 *
	 * @return true if this field is option by use of a protobuf value type, otherwise false
	 */
	public default boolean optional() {
		return false;
	}

	/**
	 * Get the parent field for this field, null if there is no parent like in the case of a single field.
	 *
	 * @return this fields parent field for oneof fields
	 */
	public default OneOfField parent() {
		return null;
	}

	/**
	 * Field type enum for use in field classes
	 */
	public enum FieldType {
		MESSAGE("Object", "null"),
		ENUM("int", "null"),
		INT32("int", "0"),
		UINT32("int", "0"),
		SINT32("int", "0"),
		INT64("long", "0"),
		UINT64("long", "0"),
		SINT64("long", "0"),
		FLOAT("long", "0"),
		FIXED32("long", "0"),
		SFIXED32("long", "0"),
		DOUBLE("double", "0"),
		FIXED64("double", "0"),
		SFIXED64("double", "0"),
		STRING("String", "\"\""),
		BOOL("boolean", "false"),
		BYTES("ByteBuffer", "ByteBuffer.allocate(0).asReadOnlyBuffer()"),
		ONE_OF("OneOf", "null");

		public final String javaType;
		public final String javaDefault;

		FieldType(String javaType, final String javaDefault) {
			this.javaType = javaType;
			this.javaDefault = javaDefault;
		}

		public String fieldType() {
			String name = toString();
			if (Character.isDigit(name.charAt(name.length()-2))) {
				return name.substring(0,name.length()-2) + "_" + name.substring(name.length()-2);
			} else {
				return name;
			}
		}

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

		public static FieldType of(Protobuf3Parser.Type_Context typeContext,  final LookupHelper lookupHelper) {
			if (typeContext.enumType() != null) {
				return FieldType.ENUM;
			} else if (typeContext.messageType() != null) {
				if (lookupHelper.isEnum(typeContext.messageType().getText())) return FieldType.ENUM;
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
