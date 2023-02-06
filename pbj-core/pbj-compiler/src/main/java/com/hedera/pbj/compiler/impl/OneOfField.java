package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of Field for OneOf fields
 */
public record OneOfField(
		String parentMessageName,
		String name,
		String comment,
		List<Field> fields,
		boolean repeated,
		boolean deprecated
) implements Field {

	/**
	 * Create a OneOf field from parser context
	 *
	 * @param oneOfContext the parsed one of field
	 * @param parentMessageName the name of the parent message
	 * @param lookupHelper helper for accessing global context
	 */
	public OneOfField(final Protobuf3Parser.OneofContext oneOfContext, final String parentMessageName, final ContextualLookupHelper lookupHelper) {
		this(parentMessageName,
			oneOfContext.oneofName().getText(),
			Common.buildCleanFieldJavaDoc(
					oneOfContext.oneofField().stream().map(field -> Integer.parseInt(field.fieldNumber().getText())).toList(),
					oneOfContext.docComment()),
			new ArrayList<>(oneOfContext.oneofField().size()),
			false,
			getDeprecatedOption(oneOfContext.optionStatement())
		);
		for(var field: oneOfContext.oneofField()) {
			fields.add(new SingleField(field, this, lookupHelper));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FieldType type() {
		return FieldType.ONE_OF;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int fieldNumber() {
		return fields.get(0).fieldNumber();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String protobufFieldType() {
		return "oneof";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String javaFieldType() {
		return "OneOf<"+getEnumClassRef()+">";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String methodNameType() {
		throw new UnsupportedOperationException("mapToWriteMethod can not handle "+type());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addAllNeededImports(final Set<String> imports, boolean modelImports,
									boolean codecImports, final boolean testImports) {
		imports.add("com.hedera.pbj.runtime");
		for(var field:fields) {
			field.addAllNeededImports(imports, modelImports, codecImports, testImports);
		}
	}

	/**
	 * N/A for OneOfField
	 */
	@Override
	public String parseCode() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String javaDefault() {
		return Common.camelToUpperSnake(name)+"_UNSET";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String schemaFieldsDef() {
		return fields.stream().map(Field::schemaFieldsDef).collect(Collectors.joining("\n"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String schemaGetFieldsDefCase() {
		return fields.stream().map(Field::schemaGetFieldsDefCase).collect(Collectors.joining("\n            "));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String parserFieldsSetMethodCase() {
		return fields.stream().map(Field::parserFieldsSetMethodCase).collect(Collectors.joining("\n"));
	}

	/**
	 * Get reference to enum class in Java code
	 *
	 * @return enum class reference
	 */
	public String getEnumClassRef() {
		return parentMessageName+"."+ nameCamelFirstUpper()+"OneOfType";
	}

	/**
	 * Helpful debug toString
	 *
	 * @return debug toString
	 */
	@Override
	public String toString() {
		return "OneOfField{" +
				"parentMessageName='" + parentMessageName + '\'' +
				", name='" + name + '\'' +
				", comment='" + comment + '\'' +
				", fields.size=" + fields.size() +
				", repeated=" + repeated +
				", deprecated=" + deprecated +
				'}';
	}

	// ====== Static Utility Methods ============================

	/**
	 * Extract if a field is deprecated or not from the protobuf options on the field
	 *
	 * @param optionContext protobuf options from parser
	 * @return true if field has deprecated option, otherwise false
	 */
	private static boolean getDeprecatedOption(List<Protobuf3Parser.OptionStatementContext> optionContext) {
		boolean deprecated = false;
		if (optionContext != null) {
			for (var option : optionContext) {
				if ("deprecated".equals(option.optionName().getText())) {
					deprecated = true;
				} else {
					System.err.println("Unhandled Option on oneof: "+option.optionName().getText());
				}
			}
		}
		return deprecated;
	}
}
