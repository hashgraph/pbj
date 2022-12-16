package com.hedera.hashgraph.pbj.compiler.impl;

/**
 * Exception thrown when compiler hits errors that are not recoverable
 */
public class PbjCompilerException extends RuntimeException {

    /**
     * Construct new compiler exception
     *
     * @param message exception explanation message
     */
    public PbjCompilerException(String message) {
        super(message);
    }
}
