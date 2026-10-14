// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

/**
 * Optional protobuf codec capability for shallow state diffs. Applications discover it on a registered
 * {@link Codec} without depending on generated model methods. A codec lacking this capability requires full values.
 * Nested messages, lists and maps are replaced as whole fields; masks are opaque and belong to this codec.
 * @param <T> model type
 */
public interface DiffCodec<T> extends Codec<T> {
    /**
     * Whether a value can participate in a partial update. Generated codecs reject null and top-level unknown fields.
     * Check both prior and next before diffing; emit a full value if either check fails.
     */
    boolean canDiff(T value);

    /** Compare values, using copy-builder candidates when available. Both must pass {@link #canDiff}. */
    Object diff(T prior, T next);

    /** Construct changed fields. The mask must come from this codec's {@link #diff}. */
    T partial(T next, Object mask);

    /** Sorted clears, including the old alternative when a oneof switches or becomes unset. */
    int[] clearedFields(T prior, T next, Object mask);

    /** Whether a partial saves at least minSaving bytes after including envelope overhead. */
    boolean worthPartial(T next, Object mask, int minSaving, int overhead);

    /** Apply a schema-aware partial. Unknown top-level fields are unsupported. */
    T applyPartial(T prior, T partial, int[] clearedFields);
}
