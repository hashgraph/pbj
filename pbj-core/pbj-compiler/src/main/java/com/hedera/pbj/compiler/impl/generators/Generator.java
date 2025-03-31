// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.FileSetWriter;
import com.hedera.pbj.compiler.impl.JavaFileWriter;
import com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator;
import com.hedera.pbj.compiler.impl.generators.protobuf.CodecGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Interface for a code generator from protobuf message definition
 */
public interface Generator {

    /**
     * All generators.
     */
    Map<Class<? extends Generator>, Function<FileSetWriter, JavaFileWriter>> GENERATORS = Map.of(
            ModelGenerator.class, FileSetWriter::modelWriter,
            SchemaGenerator.class, FileSetWriter::schemaWriter,
            CodecGenerator.class, FileSetWriter::codecWriter,
            JsonCodecGenerator.class, FileSetWriter::jsonCodecWriter,
            TestGenerator.class, FileSetWriter::testWriter);

    /**
     * Generate a code from protobuf message type
     *
     * @param msgDef                the parsed message
     * @param lookupHelper          Lookup helper for global context lookups
     * @throws IOException if there was a problem writing generated code
     */
    void generate(
            final Protobuf3Parser.MessageDefContext msgDef,
            final JavaFileWriter writer,
            final ContextualLookupHelper lookupHelper)
            throws IOException;

    /**
     * A utility method to check if a type being generated is an inner message or not. The result returned by this
     * method should only affect minor details of the generated code, such as whether to add a `static` modifier
     * to the class definition and similar. The result MUST NOT be used to choose whether to actually inline
     * the type definition or go and create physical files on disk with an actual top-level type definition. This method
     * is NOT SUPPOSED to help make such decisions. A Generator implementation MUST NEVER create new files for top-level
     * types generation on its own. The Generator MUST emit all generated code into a given JavaFileWriter instance
     * and let it (and its owner) decide where the output should go.
     *
     * @param msgDef a MessageDefContext object
     * @return true if the given MessageDefContext is nested inside an outer MessageDefContext
     */
    static boolean isInner(final Protobuf3Parser.MessageDefContext msgDef) {
        ParserRuleContext parent = msgDef;
        while ((parent = parent.getParent()) != null) {
            if (parent instanceof Protobuf3Parser.MessageDefContext) {
                // We're inside an outer MessageDefContext, so the given msgDef must be an inner type
                return true;
            }
        }
        // We didn't find any outer MessageDefContext, so the given msgDef must be a top-level type
        return false;
    }
}
