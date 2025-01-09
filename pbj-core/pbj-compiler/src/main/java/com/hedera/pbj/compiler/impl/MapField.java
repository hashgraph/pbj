// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import static com.hedera.pbj.compiler.impl.SingleField.getDeprecatedOption;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.util.Set;

/**
 * A field of type map.
 *
 * <p>In protobuf, a map is essentially a repeated map entry message with two fields: key and value.
 * However, we don't model the map entry message explicitly for performance reasons. Instead, we
 * deal with the keys and values directly, and define synthetic Field objects for them here for
 * convenience, so that we can reuse the majority of the code generation code.
 *
 * <p>In model implementations we use a custom implementation of the Map interface named PbjMap
 * which is an immutable map that exposes a SortedKeys list which allows one to iterate the map
 * deterministically which is useful for serializing, computing the hash code, etc.
 */
public record MapField(
        /** A synthetic "key" field in a map entry. */
        Field keyField,
        /** A synthetic "value" field in a map entry. */
        Field valueField,
        // The rest of the fields below simply implement the Field interface:
        boolean repeated,
        int fieldNumber,
        String name,
        FieldType type,
        String protobufFieldType,
        String javaFieldTypeBase,
        String methodNameType,
        String parseCode,
        String javaDefault,
        String parserFieldsSetMethodCase,
        String comment,
        boolean deprecated)
        implements Field {

    /** Construct a MapField instance out of a MapFieldContext and a lookup helper. */
    public MapField(
            Protobuf3Parser.MapFieldContext mapContext, final ContextualLookupHelper lookupHelper) {
        this(
                new SingleField(
                        false,
                        FieldType.of(mapContext.keyType(), lookupHelper),
                        1,
                        "___" + mapContext.mapName().getText() + "__key",
                        null,
                        null,
                        null,
                        null,
                        "An internal, private map entry key for " + mapContext.mapName().getText(),
                        false,
                        null),
                new SingleField(
                        false,
                        FieldType.of(mapContext.type_(), lookupHelper),
                        2,
                        "___" + mapContext.mapName().getText() + "__value",
                        Field.extractMessageTypeName(mapContext.type_()),
                        Field.extractMessageTypePackage(
                                mapContext.type_(), FileType.MODEL, lookupHelper),
                        Field.extractMessageTypePackage(
                                mapContext.type_(), FileType.CODEC, lookupHelper),
                        Field.extractMessageTypePackage(
                                mapContext.type_(), FileType.TEST, lookupHelper),
                        "An internal, private map entry value for "
                                + mapContext.mapName().getText(),
                        false,
                        null),
                false, // maps cannot be repeated
                Integer.parseInt(mapContext.fieldNumber().getText()),
                mapContext.mapName().getText(),
                FieldType.MAP,
                "",
                "",
                "",
                null,
                "PbjMap.EMPTY",
                "",
                Common.buildCleanFieldJavaDoc(
                        Integer.parseInt(mapContext.fieldNumber().getText()),
                        mapContext.docComment()),
                getDeprecatedOption(mapContext.fieldOptions()));
    }

    /**
     * Composes the Java generic type of the map field, e.g. "&lt;Integer, String&gt;" for a
     * Map&lt;Integer, String&gt;.
     */
    public String javaGenericType() {
        return "<"
                + keyField.type().boxedType
                + ", "
                + (valueField().type() == FieldType.MESSAGE
                        ? ((SingleField) valueField()).messageType()
                        : valueField().type().boxedType)
                + ">";
    }

    /** {@inheritDoc} */
    @Override
    public String javaFieldType() {
        return "Map" + javaGenericType();
    }

    private void composeFieldDef(StringBuilder sb, Field field) {
        sb.append(
                """
                    /**
                     * $doc
                     */
                """
                        .replace("$doc", field.comment().replaceAll("\n", "\n     * ")));
        sb.append(
                "    public static final FieldDefinition %s = new FieldDefinition(\"%s\", FieldType.%s, %s, false, false, %d);\n"
                        .formatted(
                                Common.camelToUpperSnake(field.name()),
                                field.name(),
                                field.type().fieldType(),
                                field.repeated(),
                                field.fieldNumber()));
    }

    /** {@inheritDoc} */
    @Override
    public String schemaFieldsDef() {
        StringBuilder sb = new StringBuilder();
        composeFieldDef(sb, this);
        composeFieldDef(sb, keyField);
        composeFieldDef(sb, valueField);
        return sb.toString();
    }

    /** {@inheritDoc} */
    @Override
    public String schemaGetFieldsDefCase() {
        return "case %d -> %s;".formatted(fieldNumber, Common.camelToUpperSnake(name));
    }

    /** {@inheritDoc} */
    @Override
    public void addAllNeededImports(
            final Set<String> imports,
            final boolean modelImports,
            final boolean codecImports,
            final boolean testImports) {
        if (modelImports) {
            imports.add("java.util");
        }
        if (codecImports) {
            imports.add("java.util.stream");
            imports.add("com.hedera.pbj.runtime.test");
        }
    }
}
