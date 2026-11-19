// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

/**
 * Writes the elements of a packed repeated 32-bit integer or enum field one at a time, for generated message writers:
 * {@code add} each value, then {@code end()}, or let the next call on the parent end it. A field with no elements is not
 * written at all, as the model writes it.
 */
public final class PackedIntWriter extends MessageWriter {
    /** {@code int32} and enum values: varints, ten bytes for negative values. */
    public static final int INT32 = 0;
    /** {@code uint32} values: varints of the unsigned value. */
    public static final int UINT32 = 1;
    /** {@code sint32} values: zigzag varints. */
    public static final int SINT32 = 2;
    /** {@code fixed32} and {@code sfixed32} values: four bytes, little-endian. */
    public static final int FIXED32 = 3;

    private final int encoding;

    /**
     * Create a writer for one packed field.
     *
     * @param encoding {@link #INT32}, {@link #UINT32}, {@link #SINT32} or {@link #FIXED32}
     */
    public PackedIntWriter(final int encoding) {
        this.encoding = encoding;
    }

    /**
     * Write the next element.
     *
     * @param value the value
     * @return this writer
     * @throws IllegalStateException if the field has ended
     */
    public PackedIntWriter add(final int value) {
        checkOpen();
        out.ensure(10);
        switch (encoding) {
            case INT32 -> out.varintUnchecked(value);
            case UINT32 -> out.varintUnchecked(Integer.toUnsignedLong(value));
            case SINT32 -> out.varintUnchecked(Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
            default -> out.fixed32Unchecked(value);
        }
        return this;
    }
}
