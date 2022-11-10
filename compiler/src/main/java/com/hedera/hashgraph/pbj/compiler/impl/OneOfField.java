package com.hedera.hashgraph.pbj.compiler.impl;

import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.camelToUpperSnake;

/**
 * A implementation of Field for OneOf fields 
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
	public OneOfField(final Protobuf3Parser.OneofContext oneOfContext, final String parentMessageName, final LookupHelper lookupHelper) {
		this(parentMessageName,
			oneOfContext.oneofName().getText(),
			oneOfContext.docComment().getText(),
			new ArrayList<>(oneOfContext.oneofField().size()),
			false,
			getDepricatedOption(oneOfContext.optionStatement())
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
	public void addAllNeededImports(final Set<String> imports, boolean modelImports,boolean parserImports,
			final boolean writerImports, final boolean testImports) {
		imports.add("com.hedera.hashgraph.pbj.runtime");
		for(var field:fields) {
			field.addAllNeededImports(imports, modelImports, parserImports, writerImports, testImports);
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
		return camelToUpperSnake(name)+"_UNSET";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String schemaFieldsDef() {
		return fields.stream().map(field -> field.schemaFieldsDef()).collect(Collectors.joining("\n"));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String schemaGetFieldsDefCase() {
		return fields.stream().map(field -> field.schemaGetFieldsDefCase()).collect(Collectors.joining("\n            "));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String parserFieldsSetMethodCase() {
		return fields.stream().map(field -> field.parserFieldsSetMethodCase()).collect(Collectors.joining("\n"));
	}

	public String getEnumClassRef() {
		return parentMessageName+"."+ nameCamelFirstUpper()+"OneOfType";
	}

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

	// ====== Staic Utility Methods ============================

	/**
	 * Extract if a field is deprecated or not from the protobuf options on the field
	 *
	 * @param optionContext protobuf options from parser
	 * @return true if field has deprecated option, otherwise false
	 */
	private static boolean getDepricatedOption(List<Protobuf3Parser.OptionStatementContext> optionContext) {
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
