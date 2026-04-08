// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.xdr;

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
 * Code generator that parses protobuf files and generates XDR codec stubs for each message type.
 */
public final class XdrCodecGenerator implements Generator {

    /**
     * {@inheritDoc}
     */
    public void generate(
            Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException {
        final String modelClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
        final String codecClassName = lookupHelper.getUnqualifiedClassForMessage(FileType.XDR_CODEC, msgDef);

        final List<Field> fields = new ArrayList<>();
        writer.addImport(lookupHelper.getPackage(FileType.MODEL, msgDef) + ".*");

        for (var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null || item.enumDef() != null || item.DOC_COMMENT() != null) {
                // sub-messages handled below
            } else if (item.oneof() != null) {
                final var field = new OneOfField(item.oneof(), modelClassName, lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            } else if (item.mapField() != null) {
                final MapField field = new MapField(item.mapField(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            } else if (item.field() != null && item.field().fieldName() != null) {
                final var field = new SingleField(item.field(), lookupHelper);
                fields.add(field);
                field.addAllNeededImports(writer::addImport, true, true, false);
            }
        }

        writer.addImport("com.hedera.pbj.runtime.*");
        writer.addImport("com.hedera.pbj.runtime.io.*");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport("java.io.IOException");
        writer.addImport("java.util.*");
        writer.addImport("com.hedera.pbj.runtime.PbjMap");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef));

        final String writeMethod = XdrCodecWriteMethodGenerator.generateWriteMethod(modelClassName, fields);
        final String measureRecordMethod =
                XdrCodecMeasureRecordMethodGenerator.generateMeasureRecordMethod(modelClassName, fields);
        final String parseMethod = XdrCodecParseMethodGenerator.generateParseMethod(modelClassName, fields);

        final String staticModifier = Generator.isInner(msgDef) ? " static" : "";

        // spotless:off
        writer.append("""
                /**
                 * XDR Codec for $modelClass model object. Generated based on protobuf schema.
                 */
                public final$staticModifier class $codecClass implements XdrCodec<$modelClass> {

                    /**
                     * Empty constructor
                     */
                    public $codecClass() {
                        // no-op
                    }

                    $parseMethod
                    $writeMethod
                    $measureRecordMethod
                    @Override
                    public int measure(@NonNull ReadableSequentialData input) throws ParseException {
                        final long start = input.position();
                        parse(input, false, false, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SIZE);
                        return (int)(input.position() - start);
                    }

                    @Override
                    public boolean fastEquals(@NonNull $modelClass item,
                            @NonNull ReadableSequentialData input) throws ParseException {
                        return item.equals(parse(input));
                    }

                    @Override
                    @NonNull
                    public $modelClass getDefaultInstance() {
                        return $modelClass.DEFAULT;
                    }

                """
                .replace("$modelClass", modelClassName)
                .replace("$staticModifier", staticModifier)
                .replace("$codecClass", codecClassName)
                .replace("$parseMethod", parseMethod)
                .replace("$writeMethod", writeMethod)
                .replace("$measureRecordMethod", measureRecordMethod)
        );
        // spotless:on

        for (final var item : msgDef.messageBody().messageElement()) {
            if (item.messageDef() != null) {
                generate(item.messageDef(), writer, lookupHelper);
            }
        }

        writer.append("}");
    }
}
