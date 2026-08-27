// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

/**
 * Implemented by {@link ReadableSequentialData} types that are backed directly by a heap byte array.
 * Callers (e.g. {@link com.hedera.pbj.runtime.io.buffer.PbjReader}) can detect this and use the array directly
 * instead of copying data through an intermediate buffer.
 */
public interface ByteArraySequentialData {
    /** The raw backing byte array. */
    byte[] byteArrayUnsafe();

    /** Absolute index within {@link #byteArrayUnsafe()} where currently readable data begins. */
    int byteArrayUnsafeOffset();

    /** Absolute exclusive index within {@link #byteArrayUnsafe()} where currently readable data ends. */
    int byteArrayUnsafeEnd();
}
