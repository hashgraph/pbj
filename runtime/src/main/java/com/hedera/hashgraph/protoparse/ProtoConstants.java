package com.hedera.hashgraph.protoparse;

class ProtoConstants {

    // In protobuf, the "wire type" indicates the way the value was encoded on the wire.
    // Normally this isn't needed because when I know the field type, I know what the wire
    // type should be (although this should be validated). However, this is needed when
    // "skipping" bytes for unknown fields.
    static final int WIRE_TYPE_VARINT_OR_ZIGZAG = 0;
    static final int WIRE_TYPE_FIXED_64_BIT = 1;
    static final int WIRE_TYPE_DELIMITED = 2;
    static final int WIRE_TYPE_GROUP_START = 3;
    static final int WIRE_TYPE_GROUP_END = 4;
    static final int WIRE_TYPE_FIXED_32_BIT = 5;
    /**
     * The number of lower order bits from the "tag" byte that should be rotated out
     * to reveal the field number
     */
    static final int TAG_FIELD_OFFSET = 3;
    /**
     * Mask used to extract the wire type from the "tag" byte
     */
    static final int TAG_WRITE_TYPE_MASK = 0b0000_0111;
    /**
     * Mask used to read the continuation bit from a varint encoded byte
     */
    static final int VARINT_CONTINUATION_MASK = 0b1000_0000;
    /**
     * Mask used to read off the actual data bits from a varint encoded byte
     */
    static final int VARINT_DATA_MASK = 0b0111_1111;
    /**
     * The number of actual data bits in a varint byte
     */
    static final int NUM_BITS_PER_VARINT_BYTE = 7;

    private ProtoConstants() {

    }
}
