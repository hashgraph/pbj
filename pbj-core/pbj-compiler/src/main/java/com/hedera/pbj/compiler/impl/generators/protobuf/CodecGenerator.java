// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.generators.protobuf.CodecDefaultInstanceMethodGenerator.generateGetDefaultInstanceMethod;

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
public final class CodecGenerator implements Generator {

    /**
     * {@inheritDoc}
     */
    public void generate(
            Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final String schemaClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.SCHEMA, msgDef);
        final String codecClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.CODEC, msgDef);
        final String codecPackage = lookupHelper.getPackage(FileType.CODEC, msgDef);

        final List<Field> fields = new ArrayList<>();
        writer.addImport(lookupHelper.getPackage(FileType.MODEL, msgDef) + ".*");
        writer.addImport(lookupHelper.getPackage(FileType.SCHEMA, msgDef) + ".*");

        for (var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null
                    || item.enumDef() != null
                    || item.DOC_COMMENT() != null) { // process sub messages down below
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
        final String writeMethod =
                CodecWriteMethodGenerator.generateWriteMethod(modelClassName, schemaClassName, fields);
        final String writeByteArrayMethod =
                CodecWriteByteArrayMethodGenerator.generateWriteMethod(modelClassName, schemaClassName, fields);

        final String staticModifier = Generator.isInner(msgDef) ? " static" : "";

        writer.addImport("com.hedera.pbj.runtime.*");
        writer.addImport("com.hedera.pbj.runtime.io.*");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport("com.hedera.pbj.runtime.io.stream.EOFException");
        writer.addImport("com.hedera.pbj.runtime.io.stream.WritableStreamingData");
        writer.addImport("com.hedera.pbj.runtime.ProtoArrayWriterTools");
        writer.addImport("java.io.IOException");
        writer.addImport("java.nio.*");
        writer.addImport("java.nio.charset.*");
        writer.addImport("java.util.Comparator");
        writer.addImport("java.util.stream.Stream");
        writer.addImport("java.util.*");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef));
        writer.addImport("static " + lookupHelper.getFullyQualifiedMessageClassname(FileType.SCHEMA, msgDef) + ".*");
        writer.addImport("static com.hedera.pbj.runtime.ProtoWriterTools.*");
        writer.addImport("static com.hedera.pbj.runtime.ProtoParserTools.*");
        writer.addImport("static com.hedera.pbj.runtime.ProtoConstants.*");
        writer.addImport("static com.hedera.pbj.runtime.Utf8Tools.*");

        // spotless:off
        writer.append("""
                /**
                 * Protobuf Codec for $modelClass model object. Generated based on protobuf schema.
                 */
                public final$staticModifier class $codecClass implements Codec<$modelClass> {
                    /**
                     * An initial capacity for the ArrayList where unknown fields are collected.
                     * To optimize parsing unknown fields, we store the max value we've seen so far.
                     * The variable is prone to a slight thread-race which isn't super-critical for this value
                     * because it doesn't have to be precise for correctness. It will eventually have the correct value,
                     * and that's sufficient.
                     */
                    private static int $initialSizeOfUnknownFieldsArray = 1;
                
                    /**
                     * Empty constructor
                     */
                     public $codecClass() {
                         // no-op
                     }

                $unsetOneOfConstants
                $parseMethod
                $writeMethod
                $writeByteArrayMethod
                $measureDataMethod
                $measureRecordMethod
                $fastEqualsMethod
                $getDefaultInstanceMethod

                """
                .replace("$modelClass", modelClassName)
                .replace("$staticModifier", staticModifier)
                .replace("$codecClass", codecClassName)
                .replace("$unsetOneOfConstants", CodecParseMethodGenerator.generateUnsetOneOfConstants(fields))
                .replace("$parseMethod", CodecParseMethodGenerator.generateParseMethod(modelClassName, schemaClassName, fields))
                .replace("$writeMethod", writeMethod)
                .replace("$writeByteArrayMethod", writeByteArrayMethod)
                .replace("$measureDataMethod", CodecMeasureDataMethodGenerator.generateMeasureMethod(modelClassName, fields))
                .replace("$measureRecordMethod", CodecMeasureRecordMethodGenerator.generateMeasureMethod(modelClassName, fields))
                .replace("$fastEqualsMethod", CodecFastEqualsMethodGenerator.generateFastEqualsMethod(modelClassName, fields))
                .replace("$getDefaultInstanceMethod", generateGetDefaultInstanceMethod(modelClassName))
        );
        // spotless:on

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) { // process sub messages
                generate(item.messageDef(), writer, lookupHelper);
            }
        }

        writer.append("}");
    }
}
