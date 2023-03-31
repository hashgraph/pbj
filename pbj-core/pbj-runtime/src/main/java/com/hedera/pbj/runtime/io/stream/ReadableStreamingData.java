package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import static java.util.Objects.requireNonNull;

/**
 * <p>A {@code ReadableSequentialData} backed by an input stream. If the instance is closed,
 * the underlying {@link InputStream} is closed too.
 */
public class ReadableStreamingData implements ReadableSequentialData, AutoCloseable {

    /** The underlying input stream */
    private final InputStream in;
    /** The current position, aka the number of bytes read */
    private long position = 0;
    /** The current limit for reading, defaults to Long.MAX_VALUE basically unlimited */
    private long limit = Long.MAX_VALUE;
    /** Set to true when we encounter -1 from the underlying stream, or this instance is closed */
    private boolean eof = false;

    /**
     * Creates a {@code FilterInputStream} that implements {@code DataInput} API.
     *
     * @param in the underlying input stream, can not be null
     */
    public ReadableStreamingData(@NonNull final InputStream in) {
        this.in = requireNonNull(in);
    }

    // ================================================================================================================
    // AutoCloseable Methods

    /** {@inheritDoc} */
    @Override
    public void close() {
        try {
            eof = true;
            in.close();
        } catch (IOException ignored) {
            // We can ignore this.
        }
    }

    // ================================================================================================================
    // SequentialData Methods

    /** {@inheritDoc} */
    @Override
    public long capacity() {
        // This will always be true, for all streams, unless the stream had special knowledge of the maximum length.
        // Since we do not yet have a use case for that, we will always set the capacity to be Long.MAX_VALUE, since
        // that is the largest theoretical stream we will support. If we want to support streams with less capacity,
        // we will need to add a constructor taking the capacity as an argument.
        return Long.MAX_VALUE;
    }

    /** {@inheritDoc} */
    @Override
    public long position() {
        return position;
    }

    /** {@inheritDoc} */
    @Override
    public long limit() {
        return limit;
    }

    /** {@inheritDoc} */
    @Override
    public void limit(long limit) {
        // Any attempt to set the limit must be clamped between position on the low end and capacity on the high end.
        this.limit = Math.min(capacity(), Math.max(position, limit));
    }

    /** {@inheritDoc} */
    @Override
    public long remaining() {
        return eof ? 0 : limit - position;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRemaining() {
        return !eof && position < limit;
    }

    // ================================================================================================================
    // ReadableSequentialData Methods

    /** {@inheritDoc} */
    @Override
    public byte readByte() {
        // It should NEVER be possible for position to exceed limit, but being extra safe here
        // using >= instead of just ==
        if (eof || position >= limit) {
            throw new BufferUnderflowException();
        }

        // We know result is a byte, because we've already checked for EOF, and we know that
        // it will only ever be a byte, unless it is EOF, in which case it is a -1 int.
        try {
            final var result = in.read();
            if (result == -1) {
                eof = true;
                throw new BufferUnderflowException();
            }
            position++;
            return (byte) result;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long skip(final long n) {
        final long clamped = Math.min(n, limit);

        if (clamped <= 0) {
            return 0;
        }

        try {
            long numSkipped = in.skip(clamped);
            position += numSkipped;
            return numSkipped;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }
}
