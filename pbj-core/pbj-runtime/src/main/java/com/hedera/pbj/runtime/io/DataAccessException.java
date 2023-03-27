package com.hedera.pbj.runtime.io;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A {@link RuntimeException} thrown when a {@link SequentialData} is unable to read or write data.
 */
public class DataAccessException extends UncheckedIOException {
    /**
     * Constructs a new {@code DataAccessException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public DataAccessException(String message, IOException cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DataAccessException} with the specified cause.
     *
     * @param cause the cause
     */
    public DataAccessException(IOException cause) {
        super(cause);
    }
}
