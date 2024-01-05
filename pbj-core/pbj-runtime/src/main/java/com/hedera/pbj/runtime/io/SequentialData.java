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
     * Get the maximum number of bytes that may be in this {@link SequentialData}. For a buffer, this is the
     * size of the buffer. For a stream, this is {@code Long.MAX_VALUE}, since the maximum potential capacity
     * of the stream is unbounded (unless, by some other mechanism, you know ahead of time how many possible bytes
     * there are, such as with an HTTP request with a known Content-Length header).
     *
     * <p>The capacity will never change.
     *
     * @return capacity in bytes of this sequence
     */
    long capacity();

    /**
     * Current read (or if applicable, write) position relative to origin, which is position 0. The position will
     * never be greater than {@link #capacity()}. It will always be non-negative.
     *
     * @return The current read position.
     */
    long position();

    /**
     * The byte position that can be read up to, relative to the origin. The limit will always be greater than or equal
     * to the {@link #position()}, and less than or equal to the {@link #capacity()}. It will therefore always be
     * non-negative. If the limit is equal to the {@link #position()}, then there are no bytes left to
     * ready, or no room left to write. Any attempt to read or write at the limit will throw an exception.
     *
     * @return maximum position that can be read from origin
     */
    long limit();

    /**
     * Set the limit that can be read up to, relative to origin. If less than {@link #position()} then clamp to
     * {@link #position()}, meaning there are no bytes left to read. If greater than {@link #limit()} then clamp to
     * the {@link #capacity()}, meaning the end of the sequence.
     *
     * @param limit The new limit relative to origin.
     */
    void limit(long limit);

    /**
     * Returns true if there are bytes remaining between the current {@link #position()} and {@link #limit()}. If this
     * method returns true, then there will be at least one byte available to read or write.
     *
     * @return true if ({@link #limit()} - {@link #position()}) > 0
     */
    default boolean hasRemaining() {
        return limit() - position() > 0;
    }

    /**
     * Gets the number of bytes remaining between the current {@link #position()} and {@link #limit()}. This
     * value will always be non-negative.
     *
     * @return number of bytes remaining to be read
     */
    default long remaining() {
        return limit() - position();
    }

    /**
     * Move {@link #position()} forward by {@code count} bytes. If the {@code count} would move the position past the
     * {@link #limit()}, then a buffer overflow or underflow exception is thrown.
     *
     * @param count number of bytes to skip. If 0 or negative, then no bytes are skipped.
     * @return the actual number of bytes skipped.
     * @throws DataAccessException if an I/O error occurs
     */
    long skip(long count);
}
