package com.hedera.pbj.compiler.impl.generators.protobuf;

import static com.hedera.pbj.compiler.impl.Common.DEFAULT_INDENT;

import com.hedera.pbj.compiler.impl.Field;

import java.util.List;

/**
 * Code to generate the fast equals method for Codec classes. The idea of fast equals is to parse and compare at same
 * time and fail fast as soon as parsed bytes do not match.
 */
@SuppressWarnings("unused")
class CodecFastEqualsMethodGenerator {

    static String generateFastEqualsMethod(final String modelClassName, final List<Field> fields) {
        // Placeholder implementation, replace faster implementation than full parse if there is one
        return """
                /**
                 * Compares the given item with the bytes in the input, and returns false if it determines that
                 * the bytes in the input could not be equal to the given item. Sometimes we need to compare an
                 * item in memory with serialized bytes and don't want to incur the cost of deserializing the
                 * entire object, when we could have determined the bytes do not represent the same object very
                 * cheaply and quickly.
                 *
                 * @param item The item to compare. Cannot be null.
                 * @param input The input with the bytes to compare
                 * @return true if the bytes represent the item, false otherwise.
                 * @throws ParseException If parsing fails
                 */
                public boolean fastEquals(@NonNull $modelClass item, @NonNull final ReadableSequentialData input) throws ParseException {
                    return item.equals(parse(input));
                }
                """
                .replace("$modelClass", modelClassName)
                .indent(DEFAULT_INDENT);
    }
}
