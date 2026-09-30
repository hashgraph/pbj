// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Optional, transient provenance generated for immutable PBJ models. Tracking is only a comparison
 * accelerator: a consumer may use the candidate fields only when {@link #$copyBuilderOrigin()} is
 * its exact prior instance, and must still compare every candidate by value. Otherwise it must
 * compare all fields. Metadata is excluded from equality, hashing, text, and all codecs.
 *
 * <p>As with PBJ models generally, callers must not mutate collections retained by a built model.
 * Dollar-prefixed methods cannot collide with protobuf field names.
 *
 * @param <T> the generated model type
 */
public interface CopyBuilderTracked<T> {
    /** Returns the untracked instance at the start of the builder chain, or null if untracked. */
    @Nullable
    T $copyBuilderOrigin();

    /**
     * Returns whether a setter may have changed this protobuf field number in the builder chain.
     * All alternatives of a changed oneof are candidates. This is not a value-difference test.
     */
    boolean $copyBuilderFieldChanged(int fieldNumber);

    /** Returns this instance if untracked, otherwise a shallow copy without provenance. */
    T $untracked();

    /** Removes provenance from a model; leaves other values (including null) unchanged. */
    @SuppressWarnings("unchecked")
    static <T> T untracked(T value) {
        return value instanceof CopyBuilderTracked<?> tracked ? (T) tracked.$untracked() : value;
    }
}
