// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.RandomAccessData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * <p>A {@code WritableSequentialData} backed by an output stream. If the instance is closed,
 * the underlying {@link OutputStream} is closed too.
 */
public class WritableStreamingData implements WritableSequentialData, Closeable, Flushable {

    /** The underlying output stream */
    private final OutputStream out;
    /** The current position, aka the number of bytes written */
    private long position = 0;
    /** The current limit for writing, defaults to Long.MAX_VALUE, which is basically unlimited */
    private long limit = Long.MAX_VALUE;
    /** The maximum capacity. Normally this is unbounded ({@link Long#MAX_VALUE})*/
    private final long capacity;

    /**
     * Creates a {@code WritableStreamingData} built on top of the specified underlying output stream.
     *
     * @param out the underlying output stream to be written to, can not be null
     */
    public WritableStreamingData(@NonNull final OutputStream out) {
        this.out = Objects.requireNonNull(out);
        this.capacity = Long.MAX_VALUE;
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
    // Closeable Methods

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
     * @param count number of bytes to skip. If 0 or negative, then no bytes are skipped.
     * @throws BufferOverflowException if {@code count} would move the position past the {@link #limit()}.
     * @throws UncheckedIOException if an I/O error occurs
     */
    @Override
    public void skip(final long count) {
        try {
            // We can only skip UP TO count.
            // And if the maximum bytes we can end up skipping is not positive, then we can't skip any bytes.
            if (count > remaining()) {
                throw new BufferOverflowException();
            }
            if (count <= 0) {
                return;
            }

            // Each byte skipped is a "zero" byte written to the output stream. To make this faster, we will support
            // writing in chunks instead of a single byte at a time. We will keep writing chunks until we're done.
            final byte[] zeros = new byte[1024];
            for (int i = 0; i < count; ) {
                final var toWrite = (int) Math.min(zeros.length, count - i);
                out.write(zeros, 0, toWrite);
                i += toWrite;
            }

            // Update the position and return the number of bytes skipped.
            position += count;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            throw new UncheckedIOException(e);
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
            throw new UncheckedIOException(e);
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
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
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
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull BufferedData src) throws BufferOverflowException, UncheckedIOException {
        if (remaining() < src.remaining()) {
            throw new BufferOverflowException();
        }
        final int pos = Math.toIntExact(src.position());
        final int len = Math.toIntExact(src.remaining());
        src.writeTo(out, pos, len);
        position += len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final RandomAccessData src) {
        final long len = src.length();
        if (remaining() < len) {
            throw new BufferOverflowException();
        }
        src.writeTo(out);
        position += len;
    }

    // ================================================================================================================
    // Flushable Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
        out.flush();
    }
}
