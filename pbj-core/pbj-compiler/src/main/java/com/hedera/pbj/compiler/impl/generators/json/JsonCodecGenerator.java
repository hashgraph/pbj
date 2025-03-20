// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.json;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.Field;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.MapField;
import com.hedera.pbj.compiler.impl.OneOfField;
import com.hedera.pbj.compiler.impl.SingleField;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Code generator that parses protobuf files and generates writers for each message type.
 */
@SuppressWarnings("DuplicatedCode")
public final class JsonCodecGenerator implements Generator {

    /**
     * {@inheritDoc}
     */
    public void generate(
            Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final String codecClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.JSON_CODEC, msgDef);

        final List<Field> fields = new ArrayList<>();
        writer.addImport(lookupHelper.getPackageForMessage(FileType.MODEL, msgDef) + ".*");
        writer.addImport(lookupHelper.getPackageForMessage(FileType.SCHEMA, msgDef) + ".*");

        for (var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages down below
            } else if (item.oneof() != null) { // process one ofs
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            } else if (item.mapField() != null) { // process map fields
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            } else if (item.reserved() == null && item.optionStatement() == null) {
                System.err.printf("WriterGenerator Warning - Unknown element: %s -- %s%n", item, item.getText());
            }
        }
        final String writeMethod = JsonCodecWriteMethodGenerator.generateWriteMethod(modelClassName, fields);

        final String staticModifier = Generator.isInner(msgDef) ? " static" : "";

        writer.addImport("com.hedera.pbj.runtime.*");
        writer.addImport("com.hedera.pbj.runtime.io.*");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport("java.io.IOException");
        writer.addImport("java.nio.*");
        writer.addImport("java.nio.charset.*");
        writer.addImport("java.util.*");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport("edu.umd.cs.findbugs.annotations.Nullable");
        writer.addImport(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef));
        writer.addImport("com.hedera.pbj.runtime.jsonparser.*");
        writer.addImport("static " + lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef) + ".*");
        writer.addImport("static com.hedera.pbj.runtime.JsonTools.*");

        // spotless:off
        writer.append("""
                /**
                 * JSON Codec for $modelClass model object. Generated based on protobuf schema.
                 */
                public final$staticModifier class $codecClass implements JsonCodec<$modelClass> {

                    /**
                     * Empty constructor
                     */
                     public $codecClass() {
                         // no-op
                     }

                    $unsetOneOfConstants
                    $parseObject
                    $writeMethod

                """
                .replace("$modelClass", modelClassName)
                .replace("$staticModifier", staticModifier)
                .replace("$codecClass", codecClassName)
                .replace("$unsetOneOfConstants", JsonCodecParseMethodGenerator.generateUnsetOneOfConstants(fields))
                .replace("$writeMethod", writeMethod)
                .replace("$parseObject", JsonCodecParseMethodGenerator.generateParseObjectMethod(modelClassName, fields))
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
     * Converts a field name to a JSON field name.
     *
     * @param fieldName the field name
     * @return the JSON field name
     */
    static String toJsonFieldName(String fieldName) {
        // based directly on protoc so output matches
        final int length = fieldName.length();
        StringBuilder result = new StringBuilder(length);
        boolean isNextUpperCase = false;
        for (int i = 0; i < length; i++) {
            char ch = fieldName.charAt(i);
            if (ch == '_') {
                isNextUpperCase = true;
            } else if (isNextUpperCase) {
                // This closely matches the logic for ASCII characters in:
                // http://google3/google/protobuf/descriptor.cc?l=249-251&rcl=228891689
                if ('a' <= ch && ch <= 'z') {
                    ch = (char) (ch - 'a' + 'A');
                }
                result.append(ch);
                isNextUpperCase = false;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }
}
