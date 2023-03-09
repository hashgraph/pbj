package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.Set;

/**
 * Record for Field in Protobuf file. Contains all logic and special cases for fields
 *
 * @param repeated                If this is a repeated field, ie protobuf equivalent of array
 * @param type                    The type of this single field
 * @param fieldNumber             The protobuf field number
 * @param name                    The name of this filed
 * @param messageType             The message type of this field is of type message
 * @param messageTypeCodecPackage
 */
@SuppressWarnings("DuplicatedCode")
public record SingleField(boolean repeated, FieldType type, int fieldNumber, String name, String messageType,
						  String messageTypeModelPackage,
						  String messageTypeCodecPackage, String messageTypeTestPackage,
						  String comment, boolean deprecated, OneOfField parent) implements Field {


	/**
	 * Construct a SingleField from a parsed field context
	 *
	 * @param fieldContext the field context to extra field data from
	 * @param lookupHelper lookup helper for finding packages and other global context data
	 */
	public SingleField(Protobuf3Parser.FieldContext fieldContext, final ContextualLookupHelper lookupHelper) {
		this(fieldContext.REPEATED() != null,
				FieldType.of(fieldContext.type_(), lookupHelper),
				Integer.parseInt(fieldContext.fieldNumber().getText()), fieldContext.fieldName().getText(),
				(fieldContext.type_().messageType() == null) ? null :
						fieldContext.type_().messageType().messageName().getText(),
				(fieldContext.type_().messageType() == null || fieldContext.type_().messageType().messageName().getText() == null) ? null :
						lookupHelper.getPackageFieldMessageType(FileType.MODEL, fieldContext),
				(fieldContext.type_().messageType() == null || fieldContext.type_().messageType().messageName().getText() == null) ? null :
						lookupHelper.getPackageFieldMessageType(FileType.CODEC, fieldContext), (fieldContext.type_().messageType() == null || fieldContext.type_().messageType().messageName().getText() == null) ? null :
						lookupHelper.getPackageFieldMessageType(FileType.TEST, fieldContext),
				Common.buildCleanFieldJavaDoc(Integer.parseInt(fieldContext.fieldNumber().getText()), fieldContext.docComment()),
				getDeprecatedOption(fieldContext.fieldOptions()),
				null
		);
	}

	/**
	 * Construct a SingleField from a parsed oneof subfield context
	 *
	 * @param fieldContext the field context to extra field data from
	 * @param lookupHelper lookup helper for finding packages and other global context data
	 */
	public SingleField(Protobuf3Parser.OneofFieldContext fieldContext, final OneOfField parent,  final ContextualLookupHelper lookupHelper) {
		this(false,
				FieldType.of(fieldContext.type_(), lookupHelper),
				Integer.parseInt(fieldContext.fieldNumber().getText()), fieldContext.fieldName().getText(),
				(fieldContext.type_().messageType() == null) ? null :
						fieldContext.type_().messageType().messageName().getText(),
				(fieldContext.type_().messageType() == null) ? null :
						lookupHelper.getPackageOneofFieldMessageType(FileType.MODEL, fieldContext),
				(fieldContext.type_().messageType() == null) ? null :
						lookupHelper.getPackageOneofFieldMessageType(FileType.CODEC, fieldContext), (fieldContext.type_().messageType() == null) ? null :
						lookupHelper.getPackageOneofFieldMessageType(FileType.TEST, fieldContext),
				Common.buildCleanFieldJavaDoc(Integer.parseInt(fieldContext.fieldNumber().getText()), fieldContext.docComment()),
				getDeprecatedOption(fieldContext.fieldOptions()),
				parent
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean optionalValueType() { // Move logic for checking built in types to common
		return type == SingleField.FieldType.MESSAGE && (
				messageType.equals("StringValue") ||
				messageType.equals("Int32Value") ||
				messageType.equals("UInt32Value") ||
				messageType.equals("Int64Value") ||
				messageType.equals("UInt64Value") ||
				messageType.equals("FloatValue") ||
				messageType.equals("DoubleValue") ||
				messageType.equals("BoolValue") ||
				messageType.equals("BytesValue")
		);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String protobufFieldType() {
		return type == SingleField.FieldType.MESSAGE ? messageType : type.javaType;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String javaFieldType() {
		String fieldType = switch(type) {
			case MESSAGE -> messageType;
			case ENUM -> Common.snakeToCamel(messageType, true);
			default -> type.javaType;
		};
		fieldType = switch (fieldType) {
			case "StringValue" -> "String";
			case "Int32Value", "UInt32Value" -> "Integer";
			case "Int64Value", "UInt64Value" -> "Long";
			case "FloatValue" -> "Float";
			case "DoubleValue" -> "Double";
			case "BoolValue" -> "Boolean";
			case "BytesValue" -> "Bytes";
			default -> fieldType;
		};
		if (repeated) {
			fieldType = switch (fieldType) {
				case "int" -> "List<Integer>";
				case "long" -> "List<Long>";
				case "float" -> "List<Float>";
				case "double" -> "List<Double>";
				case "boolean" -> "List<Boolean>";
				default -> "List<" + fieldType + ">";
			};
		}
		return fieldType;
	}
	public String javaFieldTypeForTest() {
		return switch(type) {
			case MESSAGE -> messageType;
			case ENUM -> Common.snakeToCamel(messageType, true);
			default -> type.javaType;
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String methodNameType() {
		return switch(type()) {
			case BOOL -> "Boolean";
			case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> "Integer";
			case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> "Long";
			case FLOAT -> "Float";
			case DOUBLE -> "Double";
			case MESSAGE -> "Message";
			case STRING -> "String";
			case ENUM -> "Enum";
			case BYTES -> "Bytes";
			default -> throw new UnsupportedOperationException("mapToWriteMethod can not handle "+type());
		};
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addAllNeededImports(Set<String> imports, boolean modelImports, boolean codecImports, final boolean testImports) {
		if (repeated || optionalValueType()) imports.add("java.util");
		if (type == FieldType.BYTES) imports.add("com.hedera.pbj.runtime.io");
		if (messageTypeModelPackage != null && modelImports) imports.add(messageTypeModelPackage);
		if (messageTypeCodecPackage != null && codecImports) imports.add(messageTypeCodecPackage);
		if (messageTypeTestPackage != null && testImports) imports.add(messageTypeTestPackage);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String parseCode() {
		if (type == FieldType.MESSAGE) {
			return "strictMode ? %s.PROTOBUF.parseStrict(input) : %s.PROTOBUF.parse(input)"
					.formatted(messageType, messageType);
		} else {
			return "input";
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String javaDefault() {
		if (optionalValueType()) {
			return "null";
		} else if (repeated) {
			return "Collections.emptyList()";
		} else if (type == FieldType.ENUM) {
			return messageType+".values()[0]";
		} else {
			return type.javaDefault;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String schemaFieldsDef() {
		final String javaDocComment =
				"""
                    /**
                     * $doc
                     */
                """
				.replace("$doc", comment().replaceAll("\n","\n     * "));
		boolean isPartOfOneOf = parent != null;
		if (optionalValueType()) {
			final String optionalBaseFieldType = switch (messageType) {
				case "StringValue" -> "STRING";
				case "Int32Value" -> "INT32";
				case "UInt32Value" -> "UINT32";
				case "Int64Value" -> "INT64";
				case "UInt64Value" -> "UINT64";
				case "FloatValue" -> "FLOAT";
				case "DoubleValue" -> "DOUBLE";
				case "BoolValue" -> "BOOL";
				case "BytesValue" -> "BYTES";
				default -> throw new UnsupportedOperationException("Unsupported optional field type found: "+type.javaType+" in "+this);
			};
			return javaDocComment + "    public static final FieldDefinition %s = new FieldDefinition(\"%s\", FieldType.%s, %s, true, %s, %d);\n"
					.formatted(Common.camelToUpperSnake(name), name, optionalBaseFieldType, repeated, isPartOfOneOf, fieldNumber);
		} else {
			return javaDocComment + "    public static final FieldDefinition %s = new FieldDefinition(\"%s\", FieldType.%s, %s, false, %s, %d);\n"
					.formatted(Common.camelToUpperSnake(name), name, type.fieldType(), repeated, isPartOfOneOf, fieldNumber);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String schemaGetFieldsDefCase() {
		return "case %d -> %s;".formatted(fieldNumber, Common.camelToUpperSnake(name));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String parserFieldsSetMethodCase() {
		final String fieldNameToSet = parent != null ? parent.name() : name;
		if (optionalValueType()) {
			if (parent != null) { // one of
				return "case %d -> this.%s = new OneOf<>(%s.%sOneOfType.%s, input);"
						.formatted(fieldNumber, fieldNameToSet, parent.parentMessageName(), Common.snakeToCamel(parent.name(), true), Common.camelToUpperSnake(name));
			} else {
				return "case %d -> this.%s = input;".formatted(fieldNumber, fieldNameToSet);
			}
		} else if (type == FieldType.MESSAGE) {
			final String valueToSet = parent != null ?
					"new OneOf<>($parentMessageName.$parentNameOneOfType.$parentName, %modelClass.PROTOBUF.parse(input))"
							.replace("$parentMessageName", parent.parentMessageName())
							.replace("$parentName", Common.snakeToCamel(parent.name(), true))
							.replace("$parseCode", parseCode())
							: parseCode();
			if (repeated) {
				return """
					case %d -> {
									if (this.%s.equals(Collections.emptyList())) {
										this.%s = new ArrayList<>();
									}
									this.%s.add(%s);
								}"""
						.formatted(fieldNumber, fieldNameToSet, fieldNameToSet, fieldNameToSet, valueToSet);

			} else {
				return "case %d -> this.%s = %s;".formatted(fieldNumber, fieldNameToSet,valueToSet);
			}
		} else if (type == FieldType.ENUM) {
			if (repeated) {
				return "case %d -> this.%s = input.stream().map(%s::fromProtobufOrdinal).toList();".formatted(fieldNumber, fieldNameToSet,
						Common.snakeToCamel(messageType, true));
			} else {
				return "case %d -> this.%s = %s.fromProtobufOrdinal(input);".formatted(fieldNumber, fieldNameToSet,
						Common.snakeToCamel(messageType, true));
			}
		} else if (repeated && (type == FieldType.STRING || type == FieldType.BYTES)) {
			final String valueToSet = parent != null ?
					"new OneOf<>(%s.%sOneOfType.%s,input)".formatted(parent.parentMessageName(), Common.snakeToCamel(parent.name(), true), Common.camelToUpperSnake(name)) :
					"input";
			return """
				case %d -> {
								if (this.%s.equals(Collections.emptyList())) {
									this.%s = new ArrayList<>();
								}
								this.%s.add(%s);
							}"""
					.formatted(fieldNumber, fieldNameToSet, fieldNameToSet, fieldNameToSet, valueToSet);

		} else {
			final String valueToSet = parent != null ?
					"new OneOf<>(%s.%sOneOfType.%s,input)".formatted(parent.parentMessageName(), Common.snakeToCamel(parent.name(), true), Common.camelToUpperSnake(name)) :
					"input";
			return "case %d -> this.%s = %s;".formatted(fieldNumber, fieldNameToSet,valueToSet);
		}
	}

	// ====== Static Utility Methods ============================

	/**
	 * Extract if a field is deprecated or not from the protobuf options on the field
	 *
	 * @param optionContext protobuf options from parser
	 * @return true if field has deprecated option, otherwise false
	 */
	private static boolean getDeprecatedOption(Protobuf3Parser.FieldOptionsContext optionContext) {
		if (optionContext != null) {
			for (var option : optionContext.fieldOption()) {
				if ("deprecated".equals(option.optionName().getText())) {
					return true;
				} else {
					System.err.println("Unhandled Option on enum: "+optionContext.getText());
				}
			}
		}
		return false;
	}
}
