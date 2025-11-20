// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Interface for enums that have a protobuf ordinal and name metdata
 */
public interface EnumWithProtoMetadata {
    /**
     * Get the Protobuf ordinal for this object
     *
     * @return integer ordinal
     */
    int protoOrdinal();

    /**
     * Get the original field name in protobuf for this type
     *
     * @return The original field name in protobuf for this type
     */
    String protoName();

    /**
     * Returns a protoOrdinal for an EnumWithProtoMetadata, or the value itself for an Integer,
     * or throws IllegalArgumentException otherwise.
     * @param obj either an EnumWithProtoMetadata or an Integer
     * @return a protoOrdinal of the given "enum value"
     * @throws IllegalArgumentException if the given object is not EnumWithProtoMetadata or Integer
     */
    static int protoOrdinal(final Object obj) {
        if (obj instanceof EnumWithProtoMetadata pbjEnum) {
            return pbjEnum.protoOrdinal();
        } else if (obj instanceof Integer intObj) {
            return intObj;
        } else {
            throw new IllegalArgumentException("EnumWithProtoMetadata or Integer are supported only, got "
                    + obj.getClass().getName());
        }
    }
}
