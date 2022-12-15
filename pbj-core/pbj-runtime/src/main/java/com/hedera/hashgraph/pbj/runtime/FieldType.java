package com.hedera.hashgraph.pbj.runtime;

/**
 * Enumeration of possible types of fields.
 */
public enum FieldType {
	/** Protobuf 64bit Double Type */
	DOUBLE,
	/** Protobuf 32bit Float Type */
	FLOAT,
	/** Protobuf 32bit Signed Integer Type */
	INT32,
	/** Protobuf 64bit Signed Long Type */
	INT64,
	/** Protobuf 32bit Unsigned Long Type */
	UINT32,
	/** Protobuf 64bit Unsigned Long Type */
	UINT64,
	/** Protobuf 32bit Signed Integer Type, ZigZag encoded */
	SINT32,
	/** Protobuf 64bit Signed Long Type, ZigZag encoded */
	SINT64,
	/** Protobuf 32bit Unsigned Integer Type, not varint encoded, just little endian */
	FIXED32,
	/** Protobuf 64bit Unsigned Long Type, not varint encoded, just little endian */
	FIXED64,
	/** Protobuf 32bit Signed Integer Type, not varint encoded, just little endian */
	SFIXED32,
	/** Protobuf 64bit Signed Long Type, not varint encoded, just little endian */
	SFIXED64,
	/** Protobuf 1 byte boolean type */
	BOOL,
	/** Protobuf UTF8 String type */
	STRING,
	/** Protobuf bytes type */
	BYTES,
	/** Protobuf enum type */
	ENUM,
	/** Protobuf sub-message type */
	MESSAGE;

	/**
	 * Optional values have an inner field, with a standard definition for every FieldType. We create singleton
	 * instances here for them to avoid them having to be created on every use. Placing them on the enum avoid a switch.
	 */
	final FieldDefinition optionalFieldDefinition;

	/**
	 * Constructor, creates optionalFieldDefinition automatically
	 */
	FieldType() {
		optionalFieldDefinition = new FieldDefinition("value",this,false,1);
	}
}
