package com.hedera.pbj.compiler.impl;

/**
 * All constants used in the naming of class files and packages
 */
public final class FileAndPackageNamesConfig {

    /** Suffix for parser java classes */
    public static final String PARSER_JAVA_FILE_SUFFIX = "ProtoParser";

    /** Suffix for schema java classes */
    public static final String SCHEMA_JAVA_FILE_SUFFIX = "Schema";

    /** Suffix for schema java classes */
    public static final String TEST_JAVA_FILE_SUFFIX = "Test";

    /** Suffix for schema java classes */
    public static final String WRITER_JAVA_FILE_SUFFIX = "Writer";

    /** The sub package where all parser java classes should be placed */
    public static final String PARSERS_SUBPACKAGE = "parser";
    /** The sub package where all schema java classes should be placed */
    public static final String SCHEMAS_SUBPACKAGE = "schema";
    /** The sub package where all model java classes should be placed */
    public static final String WRITERS_SUBPACKAGE = "writer";
    /** The sub package where all unit test java classes should be placed */
    public static final String TESTS_SUBPACKAGE = "tests";
}
