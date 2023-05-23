package com.hedera.pbj.compiler.impl.generators;

import com.hedera.pbj.compiler.impl.ContextualLookupHelper;
import com.hedera.pbj.compiler.impl.generators.json.JsonCodecGenerator;
import com.hedera.pbj.compiler.impl.generators.protobuf.CodecGenerator;
import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Interface for a code generator from protobuf message definition
 */
public interface Generator {

    /**
     * List of all generator classes
     */
    List<Class<? extends Generator>> GENERATORS = List.of(
            ModelGenerator.class,
            SchemaGenerator.class,
            CodecGenerator.class,
            JsonCodecGenerator.class,
            TestGenerator.class
    );

    /**
     * Generate a code from protobuf message type
     *
     * @param msgDef                the parsed message
     * @param destinationSrcDir     the destination source directory to generate into
     * @param destinationTestSrcDir the destination source directory to generate test files into
     * @param lookupHelper          Lookup helper for global context lookups
     * @throws IOException if there was a problem writing generated code
     */
    void generate(final Protobuf3Parser.MessageDefContext msgDef, final File destinationSrcDir,
                  File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException;

}
