package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;

/**
 * All the data position and limit methods shared by DataInput and DataOutput
 */
public interface PositionedData {

    /**
     * Current read/write position relative to origin
     *
     * @return number of bytes that have been read/written since the origin
     */
    long getPosition();

    /**
     * Limit that we can read/write up to, relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     *
     * @return maximum position that can be read/written from origin
     */
    long getLimit();

    /**
     * Set limit that can be read/written up to, relative to origin. If less than position then set to position,
     * meaning there are no bytes left available to be read or write.
     *
     * @param limit The new limit relative to origin, can be {@code Long.MAX_VALUE} to indicate unlimited
     */
    void setLimit(long limit);

    /**
     * If there are bytes remaining between current position and limit. There will be at least one byte
     * available to read.
     *
     * @return true if (limit - position) > 0
     */
    boolean hasRemaining();

    /**
     * Get the number of byte remaining
     *
     * @return number of bytes remaining between current position and limit
     */
    default long getRemaining() {
        return Math.max(0, getLimit() - getPosition());
    }

    /**
     * Move position forward by {@code count} bytes.
     *
     * @param count number of bytes to skip
     * @return the actual number of bytes skipped.
     */
    long skip(long count) throws IOException;
}
