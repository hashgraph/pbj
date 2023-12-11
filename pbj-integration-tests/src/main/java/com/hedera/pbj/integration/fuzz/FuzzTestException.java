package com.hedera.pbj.integration.fuzz;

public class FuzzTestException extends RuntimeException {
    public FuzzTestException(String message, Throwable cause) {
        super(message, cause);
    }
}
