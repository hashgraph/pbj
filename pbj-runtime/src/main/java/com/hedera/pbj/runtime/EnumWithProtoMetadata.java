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
}
