package com.hedera.hashgraph.pbj.compiler.impl;

/**
 * Exception thrown when compiler hits errors that are not recoverable
 */
public class PbjCompilerException extends RuntimeException {
    public PbjCompilerException(String message) {
        super(message);
    }
}
