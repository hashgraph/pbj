// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.xdr;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileType;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.generators.Generator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;

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

        writer.addImport("com.hedera.pbj.runtime.*");
        writer.addImport("com.hedera.pbj.runtime.io.*");
        writer.addImport("com.hedera.pbj.runtime.io.buffer.*");
        writer.addImport("java.io.IOException");
        writer.addImport("edu.umd.cs.findbugs.annotations.NonNull");
        writer.addImport(lookupHelper.getFullyQualifiedMessageClassname(FileType.MODEL, msgDef));

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

                    @Override
                    @NonNull
                    public $modelClass parse(@NonNull ReadableSequentialData input,
                            boolean strictMode, boolean parseUnknownFields,
                            int maxDepth, int maxSize) throws ParseException {
                        return $modelClass.DEFAULT;
                    }

                    @Override
                    public void write(@NonNull $modelClass item,
                            @NonNull WritableSequentialData output) throws IOException {
                        // TODO: implement in task 3.3
                    }

                    @Override
                    public int measureRecord($modelClass item) {
                        return 0;
                    }

                    @Override
                    public int measure(@NonNull ReadableSequentialData input) throws ParseException {
                        return measureRecord(parse(input, false, false, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SIZE));
                    }

                    @Override
                    public boolean fastEquals(@NonNull $modelClass item,
                            @NonNull ReadableSequentialData input) throws ParseException {
                        return item.equals(parse(input, false, false, DEFAULT_MAX_DEPTH, DEFAULT_MAX_SIZE));
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
