// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

/**
 * Writes the elements of a packed repeated 64-bit integer field one at a time, for generated message writers:
 * {@code add} each value, then {@code end()}, or let the next call on the parent end it. A field with no elements is not
 * written at all, as the model writes it.
 */
public final class PackedLongWriter extends MessageWriter {
    /** {@code int64} and {@code uint64} values: varints. */
    public static final int INT64 = 0;
    /** {@code sint64} values: zigzag varints. */
    public static final int SINT64 = 1;
    /** {@code fixed64} and {@code sfixed64} values: eight bytes, little-endian. */
    public static final int FIXED64 = 2;

    private final int encoding;

    /**
     * Create a writer for one packed field.
     *
     * @param encoding {@link #INT64}, {@link #SINT64} or {@link #FIXED64}
     */
    public PackedLongWriter(final int encoding) {
        this.encoding = encoding;
    }

    /**
     * Write the next element.
     *
     * @param value the value
     * @return this writer
     * @throws IllegalStateException if the field has ended
     */
    public PackedLongWriter add(final long value) {
        checkOpen();
        out.ensure(10);
        switch (encoding) {
            case INT64 -> out.varintUnchecked(value);
            case SINT64 -> out.varintUnchecked((value << 1) ^ (value >> 63));
            default -> out.fixed64Unchecked(value);
        }
        return this;
    }
}
