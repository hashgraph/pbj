package com.hedera.hashgraph.pbj.runtime;

/**
 * Common constants used by parsers, writers and tests.
 */
public class ProtoConstants {
    /** On wire encoded type for varint */
    static final int WIRE_TYPE_VARINT_OR_ZIGZAG = 0;
    /** On wire encoded type for fixed 64bit */
    static final int WIRE_TYPE_FIXED_64_BIT = 1;
    /** On wire encoded type for length delimited */
    static final int WIRE_TYPE_DELIMITED = 2;
    /** On wire encoded type for group start, deprecated */
    static final int WIRE_TYPE_GROUP_START = 3;
    /** On wire encoded type for group end, deprecated */
    static final int WIRE_TYPE_GROUP_END = 4;
    /** On wire encoded type for fixed 32bit */
    static final int WIRE_TYPE_FIXED_32_BIT = 5;

    /** Never create an instance */
    private ProtoConstants() {}
}
