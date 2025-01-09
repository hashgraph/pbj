// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.SequentialData;

/**
 * Represents buffered {@link SequentialData}. The buffer may be on-heap or off-heap, backed by a byte array or a
 * {@link java.nio.ByteBuffer} or memory-mapped file, or any other form of buffered data. {@link BufferedSequentialData}
 * has a maximum {@link #capacity()} (or size) supported by the buffer.
 */
public interface BufferedSequentialData extends SequentialData, RandomAccessData {
    /**
     * Sets the {@link #position()} to the given value and leaves the {@link #limit()} alone. The position must be
     * non-negative and no larger than the {@link #limit()}. If set to {@link #limit()}, then there will be no
     * remaining room for reading or writing from the buffer.
     *
     * @param position the new position
     * @throws IllegalArgumentException if the position is negative or greater than the limit
     */
    void position(long position);

    /**
     * Set the {@link #limit()} to the current {@link #position()} and the {@link #position()} to the origin. This is
     * useful when you have just finished writing into a buffer and want to flip it to be ready to read back from, or
     * vice versa.
     */
    void flip();

    /**
     * Reset the {@link #position()} to the origin and the {@link #limit()} to the {@link #capacity()}, allowing this
     * buffer to be read or written again, such that the entire buffer can be used.
     */
    void reset();

    /**
     * Reset the {@link #position()} to the origin and leave the {@link #limit()} alone, allowing this buffer to be
     * read again with the existing {@link #limit()}.
     */
    void resetPosition();
}
