// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Common constants used by parsers, writers and tests.
 */
public enum ProtoConstants {

    /** On wire encoded type for varint */
    WIRE_TYPE_VARINT_OR_ZIGZAG,
    /** On wire encoded type for fixed 64bit */
    WIRE_TYPE_FIXED_64_BIT,
    /** On wire encoded type for length delimited */
    WIRE_TYPE_DELIMITED,
    /** On wire encoded type for group start, deprecated */
    WIRE_TYPE_GROUP_START,
    /** On wire encoded type for group end, deprecated */
    WIRE_TYPE_GROUP_END,
    /** On wire encoded type for fixed 32bit */
    WIRE_TYPE_FIXED_32_BIT;

    // values() seems to allocate a new array on each call, so let's cache it here
    private static final ProtoConstants[] values = values();

    /**
     * Mask used to extract the wire type from the "tag" byte
     */
    public static final int TAG_WIRE_TYPE_MASK = 0b0000_0111;

    public static ProtoConstants get(int ordinal) {
        return values[ordinal];
    }
}
