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
    /**
     * A {@code Comparable<UnknownField>} implementation that sorts UnknownField objects by their `field` numbers
     * in the increasing order. This comparator is used for maintaining a stable and deterministic order for any
     * unknown fields parsed from an input. When writing unknown fields, they're written in this same order as well.
     * The implementation should remain stable over time because this is a public API.
     *
     * @param o "other" UnknownField object to compare to
     */
    @Override
    public int compareTo(final UnknownField o) {
        return Integer.compare(field, o.field);
    }

    /**
     * An `Object.equals()` implementation that checks equality of all the members of the UnknownField record:
     * the `field`, the `wireType`, and the `bytes`.
     * The implementation should remain stable over time because this is a public API.
     *
     * @param o "other" object to check equality
     */
    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof UnknownField that)) return false;
        return field == that.field && wireType == that.wireType && Objects.equals(bytes, that.bytes);
    }

    /**
     * An `Object.hashCode()` implementation that computes a hash code using all the members of the UnknownField record:
     * the `field`, the `wireType`, and the `bytes`.
     * The implementation should remain stable over time because this is a public API.
     */
    @Override
    public int hashCode() {
        int hashCode = 1;

        hashCode = 257 * hashCode + Integer.hashCode(field);
        hashCode = 257 * hashCode + Integer.hashCode(wireType.ordinal());
        hashCode = 257 * hashCode + bytes.hashCode();

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
     * A Protobuf "{@code Comparable<UnknownField>}" implementation that sorts UnknownField objects by their `field`
     * numbers, then by their `wireType`, and finally by the payload `bytes`. This implementation is used to compare
     * unknown fields when a model containing the unknown fields implements the `Comparable` interface.
     * The implementation should remain stable over time because this is a public API.
     *
     * @param o "other" UnknownField object to compare to
     */
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

    /**
     * A Protobuf `model.toString()` implementation for this UnknownField object. For varint and fixed 32/64 bit
     * wireTypes, it uses a corresponding integer format for parsing the underlying bytes. For delimited wireType,
     * it prints the raw bytes of the field, including the size encoded in the first few bytes, as a byte array.
     * This method throws an exception for all other wireTypes.
     * The implementation should remain stable over time because this is a public API.
     *
     * @param sb a StringBuilder to append the toString() representation to
     * @throws IllegalStateException if the wireType is unsupported
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
