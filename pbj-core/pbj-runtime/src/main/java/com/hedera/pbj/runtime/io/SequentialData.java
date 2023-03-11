package com.hedera.pbj.runtime.io;

/**
 * Represents sequential data which may either be buffered or streamed. Conceptually, streamed data is
 * a sequential stream of bytes, while a buffer is a sequential array of bytes. A stream <b>necessarily</b>
 * has the concept of "position" within the stream where data is being read or written to. A buffer that implements
 * {@link SequentialData} also contains a "position" within the buffer from which data is being read or written.
 * This makes it convenient to parse objects from a buffer without having to keep track of the read position
 * manually or to write data into a buffer.
 *
 * <p>This interface does not itself define any methods by which data may be read or written, because a stream,
 * for example, may be readable or writable but not both. See sub-interfaces {@link ReadableSequentialData} and
 * {@link WritableSequentialData} for API for reading or writing.
 */
public interface SequentialData {

    /**
     * Current read (or if applicable, write) position relative to origin, which is position 0.
     *
     * @return The current read position.
     */
    long position();

    /**
     * Limit that we can read up to, relative to origin. Use a value of {@code Long.MAX_VALUE} to indicate
     * unlimited read/write, or an unknown limit (as in the case of unbounded streams).
     *
     * @return maximum position that can be read from origin
     */
    long limit();

    /**
     * Set the limit that can be read up to, relative to origin. If less than {@link #position()} then set to
     * {@link #position()}, meaning there are no bytes left to read.
     *
     * @param limit The new limit relative to origin, and can be {@code Long.MAX_VALUE} to indicate it is unlimited
     */
    void limit(long limit);

    /**
     * If there are bytes remaining between current {@link #position()} and {@link #limit()}. There will be at
     * least one byte available to read.
     *
     * @return true if ({@link #limit()} - {@link #position()}) > 0
     */
    default boolean hasRemaining() {
        return limit() - position() > 0;
    }

    /**
     * Get the number of bytes remaining between the current {@link #position()} and {@link #limit()}.
     *
     * @return number of bytes remaining to be read
     */
    default long remaining() {
        return Math.max(0, limit() - position());
    }

    /**
     * Move {@link #position()} forward by {@code count} bytes.
     *
     * @param count number of bytes to skip
     * @return the actual number of bytes skipped.
     * @throws DataAccessException if an I/O error occurs
     */
    long skip(long count);
}
