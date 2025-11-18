// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Code generator that parses protobuf files and generates schemas for each message type.
 */
public final class SchemaGenerator implements Generator {

    /**
     * {@inheritDoc}
     */
    public void generate(
            final Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final String schemaClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.SCHEMA, msgDef);
        final List<Field> fields = new ArrayList<>();
        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null
                    || item.enumDef() != null
                    || item.DOC_COMMENT() != null) { // process sub messages down below
            } else if (item.oneof() != null) { // process one ofs
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, false, false);
            } else if (item.mapField() != null) { // process map flattenedFields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, false, false);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
            } else if (item.reserved() == null && item.optionStatement() == null) {
                // we can ignore reserved and option statements for now
                System.err.printf("SchemaGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }

        final List<Field> flattenedFields = fields.stream()
                .flatMap(field ->
                        field instanceof OneOfField ? ((OneOfField) field).fields().stream() : Stream.of(field))
                .collect(Collectors.toList());

        final String staticModifier = Generator.isInner(msgDef) ? " static" : "";

        writer.addImport("com.hedera.pbj.runtime.FieldDefinition");
        writer.addImport("com.hedera.pbj.runtime.FieldType");
        writer.addImport("com.hedera.pbj.runtime.Schema");

        // spotless:off
        writer.append("""
                /**
                 * Schema for $modelClassName model object. Generate based on protobuf schema.
                 */
                public final$staticModifier class $schemaClassName implements Schema {

                    /**
                     * Private constructor to prevent instantiation.
                     */
                     private $schemaClassName() {
                         // no-op
                     }

                    // -- FIELD DEFINITIONS ---------------------------------------------

                $fields

                    // -- OTHER METHODS -------------------------------------------------

                    /**
                     * Check if a field definition belongs to this schema.
                     *
                     * @param f field def to check
                     * @return true if it belongs to this schema
                     */
                    public static boolean valid(FieldDefinition f) {
                        return f != null && getField(f.number()) == f;
                    }

                $getMethods

                """
                .replace("$modelClassName", modelClassName)
                .replace("$staticModifier", staticModifier)
                .replace("$schemaClassName", schemaClassName)
                .replace("$fields", fields.stream().map(Field::schemaFieldsDef)
                        .collect(Collectors.joining("\n\n")))
                .replace("$getMethods", generateGetField(flattenedFields))
        );
        // spotless:on

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), writer, lookupHelper);
            }
        }

        writer.append("}");
    }

    /**
     * Generate getField method to get a field definition given a field number
     *
     * @param flattenedFields flattened list of all fields, with oneofs flattened
     * @return source code string for getField method
     */
    private static String generateGetField(final List<Field> flattenedFields) {
        // spotless:off
        return
                """        
                /**
                 * Get a field definition given a field number
                 *
                 * @param fieldNumber the fields number to get def for
                 * @return field def or null if field number does not exist
                 */
                public static FieldDefinition getField(final int fieldNumber) {
                    return switch(fieldNumber) {
                        %s
                        default -> null;
                    };
                }
                """.formatted(flattenedFields.stream()
                        .map(Field::schemaGetFieldsDefCase)
                        .collect(Collectors.joining("\n            ")));
        // spotless:on
    }
}
