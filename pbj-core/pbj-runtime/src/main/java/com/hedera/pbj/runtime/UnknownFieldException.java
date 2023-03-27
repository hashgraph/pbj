package com.hedera.pbj.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * An exception thrown when an unknown field is encountered while parsing a message.
 */
public class UnknownFieldException extends IOException {
    /**
     * Constructs a new {@link UnknownFieldException} with the given field number.
     *
     * @param fieldNum the field number of the unknown field
     */
    public UnknownFieldException(final int fieldNum) {
        super("Encountered an unknown field with number " + fieldNum + " while parsing");
    }

    /**
     * Constructs a new {@link UnknownFieldException} with the given field name.
     *
     * @param fieldName the field name of the unknown field
     */
    public UnknownFieldException(@NonNull final String fieldName) {
        super("Encountered an unknown field with name " + fieldName + " while parsing");
    }
}
