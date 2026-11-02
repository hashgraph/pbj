// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import com.hedera.pbj.runtime.io.buffer.ArrayCursor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Every generated view ({@code Xxx.View}, from {@code pbj.views}) can be wrapped over part of an array: so a view's
 * bytes field holding an encoded message, such as a signed transaction's body, can be read through that message's
 * view without copying it, with {@code xxxAs(view)}.
 */
public interface MessageView {
    /**
     * Wrap {@code in[offset, offset + length)}, part of {@code source} if it is not null.
     *
     * @param source the {@link Bytes} holding the bytes, or null if they are a plain array
     * @param in the bytes
     * @param offset where the message starts
     * @param length the message's length
     * @param strictMode whether to reject unknown fields, as parsing in strict mode does
     * @param maxDepth the deepest nesting allowed
     * @param maxSize the largest length allowed for a field
     * @return this view
     * @throws ParseException if the message is malformed
     */
    MessageView wrap(
            @Nullable Bytes source,
            @NonNull byte[] in,
            int offset,
            int length,
            boolean strictMode,
            int maxDepth,
            int maxSize)
            throws ParseException;

    /**
     * Wrap {@code length} bytes from {@code offset} within what {@code outer} reads, as a containing view does for the
     * messages in it: cheaper than {@link #wrap(Bytes, byte[], int, int, boolean, int, int)}, as the outer cursor's
     * bytes were checked already.
     *
     * @param outer the cursor reading the bytes around the message
     * @param offset where the message starts
     * @param length the message's length
     * @param strictMode whether to reject unknown fields, as parsing in strict mode does
     * @param maxDepth the deepest nesting allowed
     * @param maxSize the largest length allowed for a field
     * @return this view
     * @throws ParseException if the message is malformed
     * @throws IndexOutOfBoundsException if the message is not inside what {@code outer} reads
     */
    MessageView wrap(@NonNull ArrayCursor outer, int offset, int length, boolean strictMode, int maxDepth, int maxSize)
            throws ParseException;
}
