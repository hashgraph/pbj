package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * <p>A {@code WritableSequentialData} backed by an output stream. If the instance is closed,
 * the underlying {@link OutputStream} is closed too.
 */
public class WritableStreamingData implements WritableSequentialData, AutoCloseable {

    /** The underlying output stream */
    private final OutputStream out;
    /** The current position, aka the number of bytes written */
    private long position = 0;
    /** The current limit for writing, defaults to Long.MAX_VALUE, which is basically unlimited */
    private long limit = Long.MAX_VALUE;
    /** The maximum capacity. Normally this is unbounded ({@link Long#MAX_VALUE})*/
    private long capacity = Long.MAX_VALUE;

    /**
     * Creates a {@code WritableStreamingData} built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to be written to, can not be null
     */
    public WritableStreamingData(@NonNull final OutputStream out) {
        this.out = Objects.requireNonNull(out);
    }

    /**
     * Creates a {@code WritableStreamingData} built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to be written to, can not be null
     * @param capacity the maximum capacity of the stream
     */
    public WritableStreamingData(@NonNull final OutputStream out, final long capacity) {
        this.out = Objects.requireNonNull(out);
        this.capacity = capacity;
        this.limit = capacity;
    }

    // ================================================================================================================
    // AutoCloseable Methods

    @Override
    public void close() throws IOException {
        try {
            out.close();
        } catch (IOException ignored) {
            // We don't need to handle this. It is OK to silently ignore
            // (There is nothing we could have done anyway, except maybe log it)
        }
    }

    // ================================================================================================================
    // SequentialData Methods

    /** {@inheritDoc} */
    @Override
    public long capacity() {
        return capacity;
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
    public void limit(final long limit) {
        // Any attempt to set the limit must be clamped between position on the low end and capacity on the high end.
        this.limit = Math.min(capacity(), Math.max(position, limit));
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRemaining() {
        return position < limit;
    }

    /**
     * Move position forward by {@code count} bytes byte writing zeros to output stream.
     *
     * @param count number of bytes to skip
     * @return the actual number of bytes skipped.
     */
    @Override
    public long skip(long count) {
        try {
            // We can only skip UP TO count, but if there are fewer bytes remaining, then we can only skip that many.
            // And if the maximum bytes we can end up skipping is not positive, then we can't skip any bytes.
            count = Math.min(count, remaining());
            if (count <= 0) {
                return 0;
            }

            // Each byte skipped is a "zero" byte written to the output stream. To make this faster, we will support
            // writing in chunks instead of a single byte at a time. We will keep writing chunks until we're done.
            final byte[] zeros = new byte[1024];
            for (int i = 0; i < count;) {
                final var toWrite = (int) Math.min(zeros.length, count - i);
                out.write(zeros, 0, toWrite);
                i += toWrite;
            }

            // Update the position and return the number of bytes skipped.
            position += count;
            return count;
        } catch (IOException e) {
            // It is possible that we will encounter an IOException for some reason. If we do, then we turn
            // it into a DataAccessException.
            throw new DataAccessException(e);
        }
    }

    // ================================================================================================================
    // WritableSequentialData Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(final byte b) {
        if (position >= limit) {
            throw new BufferOverflowException();
        }

        try {
            out.write(b);
            position++;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }

        if (length == 0) {
            return;
        }

        if (length > remaining()) {
            throw new BufferOverflowException();
        }

        try {
            out.write(src, offset, length);
            position += length;
        } catch (final IOException e) {
            throw new DataAccessException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src) {
        if (src.length > remaining()) {
            throw new BufferOverflowException();
        }

        try {
            out.write(src);
            position += src.length;
        } catch (final IOException e) {
            throw new DataAccessException(e);
        }
    }

    @Override
    public void writeBytes(@NonNull final ByteBuffer src) {
        if (!src.hasArray()) {
            WritableSequentialData.super.writeBytes(src);
            return;
        }

        if (remaining() < src.remaining()) {
            throw new BufferOverflowException();
        }

        final long len = src.remaining();
        if (len == 0) {
            // Nothing to do
            return;
        }

        final byte[] srcArr = src.array();
        final int srcPos = src.position();
        try {
            out.write(srcArr, srcPos, Math.toIntExact(len));
            position += len;
            src.position(Math.toIntExact(srcPos + len));
        } catch (final IOException e) {
            throw new DataAccessException(e);
        }
    }
}
