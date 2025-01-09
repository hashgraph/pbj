// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

/**
 * A {@link RuntimeException} thrown when attempting to decode data from a {@link
 * ReadableSequentialData} but it cannot be decoded. See specifically {@link
 * ReadableSequentialData#readVarInt(boolean)} and {@link
 * ReadableSequentialData#readVarLong(boolean)}
 */
public class DataEncodingException extends RuntimeException {
    /**
     * Constructs a new {@code DataEncodingException} with the specified detail message.
     *
     * @param message the detail message
     */
    public DataEncodingException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code DataEncodingException} with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public DataEncodingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code DataEncodingException} with the specified cause.
     *
     * @param cause the cause
     */
    public DataEncodingException(Throwable cause) {
        super(cause);
    }
}
