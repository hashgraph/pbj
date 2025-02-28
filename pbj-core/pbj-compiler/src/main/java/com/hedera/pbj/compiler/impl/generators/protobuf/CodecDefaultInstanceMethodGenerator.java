// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

/**
 * Generates the getDefaultInstance method for the codec.
 */
public class CodecDefaultInstanceMethodGenerator {

    static String generateGetDefaultInstanceMethod(String modelClassName) {
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
                .replace("$modelClass", modelClassName)
                .indent(DEFAULT_INDENT);
    }
}
