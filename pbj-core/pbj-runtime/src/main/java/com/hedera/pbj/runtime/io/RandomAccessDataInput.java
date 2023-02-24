package com.hedera.pbj.runtime.io;

public interface RandomAccessDataInput extends DataInput {
    /**
     * Set the limit to current position and position to origin. This is useful when you have just finished writing
     * into a buffer and want to flip it ready to read back from.
     */
    void flip();

    /**
     * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
     */
    void reset();

    /**
     * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
     */
    void resetPosition();

    /**
     * Get the capacity in bytes that can be stored in this buffer
     *
     * @return capacity in bytes
     */
    int getCapacity();
}
