// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The entry of a map field whose values are messages, for generated message writers: the parent opens it, writes the
 * key into it, then opens the value's writer inside it. The entry ends with its value, when the parent is next written
 * to or ends.
 */
public final class MapEntryWriter extends MessageWriter {
    /**
     * Open the value of this entry, field 2, writing its tag.
     *
     * @param value the value's writer
     * @param perTypeReserve the per-type guess at the size of the value's length prefix, 1 or 2
     * @param <W> the value's writer type
     * @return {@code value}, now open
     */
    public <W extends MessageWriter> W openValue(@NonNull final W value, final int perTypeReserve) {
        openChild(value, perTypeReserve, 0x12);
        return value;
    }
}
