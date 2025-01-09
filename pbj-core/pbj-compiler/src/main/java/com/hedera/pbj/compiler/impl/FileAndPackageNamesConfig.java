// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

/**
 * All constants used in the naming of class files and packages
 */
public final class FileAndPackageNamesConfig {

    /** Suffix for schema java classes */
    public static final String SCHEMA_JAVA_FILE_SUFFIX = "Schema";

    /** Suffix for test java classes */
    public static final String TEST_JAVA_FILE_SUFFIX = "Test";

    /** Suffix for codec java classes */
    public static final String CODEC_JAVA_FILE_SUFFIX = "ProtoCodec";

    /** Suffix for JSON codec java classes */
    public static final String JSON_CODEC_JAVA_FILE_SUFFIX = "JsonCodec";

    /** The sub package where all schema java classes should be placed */
    public static final String SCHEMAS_SUBPACKAGE = "schema";

    /** The sub package where all codec java classes should be placed */
    public static final String CODECS_SUBPACKAGE = "codec";

    /** The sub package where all unit test java classes should be placed */
    public static final String TESTS_SUBPACKAGE = "tests";
}
