// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

/**
 * Writes the elements of a packed repeated {@code bool} field one at a time, for generated message writers:
 * {@code add} each value, then {@code end()}, or let the next call on the parent end it. A field with no elements is not
 * written at all, as the model writes it.
 */
public final class PackedBooleanWriter extends MessageWriter {
    /**
     * Write the next element.
     *
     * @param value the value
     * @return this writer
     * @throws IllegalStateException if the field has ended
     */
    public PackedBooleanWriter add(final boolean value) {
        checkOpen();
        out.ensure(1);
        out.writeByteUnchecked(value ? 1 : 0);
        return this;
    }
}
