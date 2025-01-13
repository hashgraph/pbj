package com.hedera.pbj.integration.fuzz;

/**
 * A custom exception for the fuzz test.
 */
public class FuzzTestException extends RuntimeException {

    /**
     * Create a new exception with the given message and cause.
     *
     * @param message the message to log
     * @param cause the cause of exception
     */
    public FuzzTestException(String message, Throwable cause) {
        super(message, cause);
    }
}
