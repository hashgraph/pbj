// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

/**
 * Writes the elements of a packed repeated {@code double} field one at a time, for generated message writers:
 * {@code add} each value, then {@code end()}, or let the next call on the parent end it. NaNs are written canonical, as
 * the model writes them. A field with no elements is not written at all.
 */
public final class PackedDoubleWriter extends MessageWriter {
    /**
     * Write the next element.
     *
     * @param value the value
     * @return this writer
     * @throws IllegalStateException if the field has ended
     */
    public PackedDoubleWriter add(final double value) {
        checkOpen();
        out.ensure(Long.BYTES);
        out.fixed64Unchecked(Double.doubleToLongBits(value));
        return this;
    }
}
