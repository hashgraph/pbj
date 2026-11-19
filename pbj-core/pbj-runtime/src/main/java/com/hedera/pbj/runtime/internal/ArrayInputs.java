// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.internal;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Access to the backing array of array-backed inputs, for the runtime's own parsing code. This package is not exported,
 * so the access is not public API: {@code io.buffer} installs it when its input classes load.
 */
public final class ArrayInputs {
    /** Reads the backing array and current index of an input, if it has them. */
    public interface Access {
        /**
         * @param input an input
         * @return its backing array, or null if it has none
         */
        @Nullable
        byte[] array(@NonNull ReadableSequentialData input);

        /**
         * @param input an input with a backing array
         * @return the index in that array of the input's current position
         */
        int index(@NonNull ReadableSequentialData input);
    }

    private static volatile Access access;

    private ArrayInputs() {}

    /**
     * Install the access; called by {@code io.buffer}.
     *
     * @param newAccess the access
     */
    public static void install(@NonNull final Access newAccess) {
        access = newAccess;
    }

    /**
     * @param input an input
     * @return its backing array, or null if it has none
     */
    @Nullable
    public static byte[] array(@NonNull final ReadableSequentialData input) {
        final Access a = access;
        return a == null ? null : a.array(input);
    }

    /**
     * @param input an input with a backing array, as returned by {@link #array}
     * @return the index in that array of the input's current position
     */
    public static int index(@NonNull final ReadableSequentialData input) {
        return access.index(input);
    }
}
