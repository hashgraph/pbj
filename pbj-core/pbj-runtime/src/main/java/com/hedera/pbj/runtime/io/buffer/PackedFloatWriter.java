// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

/**
 * Writes the elements of a packed repeated {@code float} field one at a time, for generated message writers:
 * {@code add} each value, then {@code end()}, or let the next call on the parent end it. Values are written with their
 * raw bits, as the model writes them. A field with no elements is not written at all.
 */
public final class PackedFloatWriter extends MessageWriter {
    /**
     * Write the next element.
     *
     * @param value the value
     * @return this writer
     * @throws IllegalStateException if the field has ended
     */
    public PackedFloatWriter add(final float value) {
        checkOpen();
        out.ensure(Integer.BYTES);
        out.fixed32Unchecked(Float.floatToRawIntBits(value));
        return this;
    }
}
