// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A record representing an unknown field as the field number, its wireType, and the raw bytes.
 * <p>
 * Bytes for `ProtoConstants.WIRE_TYPE_DELIMITED` wireType contain raw protobuf encoding of the field data
 * that includes a varInt prefix with the size of the data. For example, to read the actual bytes stored in the field
 * one could use the `ProtoParserTools.readBytes()` method which will read the length correctly and return the actual
 * bytes of the data w/o the length prefix.
 * <p>
 * The {@code Comparable<UnknownField>} interface implements sorting by the increasing field number.
 * For comparing the entire field, including its wire type and the payload, use the `protobufCompareTo()` method.
 *
 * @param field the protobuf field number
 * @param wireType the wire type of the field (e.g. varint, or delimited, etc.)
 * @param bytes a list of the raw bytes of each occurrence of the field (e.g. for repeated fields)
 */
public record UnknownField(int field, @NonNull ProtoConstants wireType, @NonNull Bytes bytes)
        implements Comparable<UnknownField> {
    /** {@inheritDoc} */
    @Override
    public int compareTo(final UnknownField o) {
        return Integer.compare(field, o.field);
    }

    /** Protobuf compareTo. */
    public int protobufCompareTo(final UnknownField o) {
        int result = compareTo(o);
        if (result != 0) {
            return result;
        }

        result = Integer.compare(wireType.ordinal(), o.wireType.ordinal());
        if (result != 0) {
            return result;
        }

        return bytes.compareTo(o.bytes);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UnknownField that)) return false;
        return field == that.field && Objects.equals(bytes, that.bytes) && wireType == that.wireType;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int hashCode = 1;

        hashCode = 31 * hashCode + Integer.hashCode(field);
        hashCode = 31 * hashCode + Integer.hashCode(wireType.ordinal());
        hashCode = 31 * hashCode + bytes.hashCode();

        // Shifts: 30, 27, 16, 20, 5, 18, 10, 24, 30
        hashCode += hashCode << 30;
        hashCode ^= hashCode >>> 27;
        hashCode += hashCode << 16;
        hashCode ^= hashCode >>> 20;
        hashCode += hashCode << 5;
        hashCode ^= hashCode >>> 18;
        hashCode += hashCode << 10;
        hashCode ^= hashCode >>> 24;
        hashCode += hashCode << 30;

        return hashCode;
    }

    /**
     * Prints a protobuf model.toString() representation for this unknown field into the given StringBuilder.
     */
    public void printToString(final StringBuilder sb) {
        sb.append(field).append("=");
        switch (wireType) {
            case WIRE_TYPE_VARINT_OR_ZIGZAG -> sb.append(bytes.getVarLong(0, false));
            case WIRE_TYPE_FIXED_32_BIT -> sb.append(bytes.getInt(0));
            case WIRE_TYPE_FIXED_64_BIT -> sb.append(bytes.getLong(0));
            case WIRE_TYPE_DELIMITED -> sb.append('[').append(bytes.toString()).append(']');
            default -> throw new IllegalStateException("Unsupported wire type: " + wireType);
        }
    }
}
