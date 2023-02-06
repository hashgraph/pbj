package com.hedera.pbj.compiler.impl;

/**
 * Enum for the different types of files that are generated
 */
public enum FileType {
    /** Generated model record object */
    MODEL,
    /** Generated schema class */
    SCHEMA,
    /** Generated codec class */
    CODEC,
    /** Generated test class */
    TEST,
    /** Protoc generated model class */
    PROTOC
}
