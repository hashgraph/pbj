package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;


/**
 * Generates the getDefaultInstance method for the codec.
 */
public class CodecDefaultInstanceMethodGenerator {

    static String generateGetDefaultInstanceMethod() {
        return """
            /**
             * Get the default value for the model class.
             *
             * @return The default value for the model class
             */
            public $modelClass getDefaultInstance() {
                return $modelClass.getDefaultInstance();
            }
            """
            .indent(DEFAULT_INDENT);
    }
}
