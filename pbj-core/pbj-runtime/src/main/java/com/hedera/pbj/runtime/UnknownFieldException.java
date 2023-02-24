package com.hedera.pbj.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class UnknownFieldException extends IOException {
    // Used by PROTOBUF parsing, for example
    public UnknownFieldException(final int fieldNum) {
        super("Encountered an unknown field with number " + fieldNum + " while parsing");
    }

    // Used by JSON parsing, for example
    public UnknownFieldException(@NonNull final String fieldName) {
        super("Encountered an unknown field with name " + fieldName + " while parsing");
    }
}
