package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.util.Objects.requireNonNull;

/**
 * <p>A {@code ReadableSequentialData} backed by an input stream. If the instance is closed,
 * the underlying {@link InputStream} is closed too.
 */
public class ReadableStreamingData implements ReadableSequentialData, Closeable {

    /** The underlying input stream */
    private final InputStream in;

    /** The capacity of this stream if known, or Long.MAX_VALUE otherwise. */
    private final long capacity;

    /** The current position, aka the number of bytes read */
    private long position = 0;

    /** The current limit for reading, defaults to Long.MAX_VALUE basically unlimited */
    private long limit = Long.MAX_VALUE;

    /** Set to true when we encounter -1 from the underlying stream, or this instance is closed */
    private boolean eof = false;

    /**
     * Creates a new streaming data object on top of a given input stream.
     *
     * @param in the underlying input stream, can not be null
     */
    public ReadableStreamingData(@NonNull final InputStream in) {
        this.in = requireNonNull(in);
        this.capacity = Long.MAX_VALUE;
    }

    /**
     * Opens a new input stream to read a given file and creates a new streaming data object on top
     * of this stream.
     *
     * @param file the file, can not be null
     * @throws IOException if an I/O error occurs
     */
    public ReadableStreamingData(@NonNull final Path file) throws IOException {
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            throw new IOException("Cannot read file: " + file);
        }
        this.in = Files.newInputStream(file, StandardOpenOption.READ);
        this.capacity = this.limit = Files.size(file);
    }

    /**
     * Creates a new streaming data object on top of a given byte array.
     *
     * @param bytes the byte array, can not be null
     */
    public ReadableStreamingData(@NonNull final byte[] bytes) {
        this.in = new ByteArrayInputStream(bytes);
        this.capacity = this.limit = bytes.length;
    }

    // ================================================================================================================
    // Closeable Methods

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
        if (!hasRemaining()) {
            throw new BufferUnderflowException();
        }
        // We know result is a byte, because we've already checked for EOF, and we know that
        // it will only ever be a byte, unless it is EOF, in which case it is a -1 int.
        try {
            final var result = in.read();
            if (result == -1) {
                eof = true;
                throw new EOFException();
            }
            position++;
            return (byte) result;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long skip(final long n) {
        if (position + n > limit) {
            throw new BufferUnderflowException();
        }

        if (n <= 0) {
            return 0;
        }

        try {
            long numSkipped = in.skip(n);
            position += numSkipped;
            return numSkipped;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }
        if ((offset < 0) || (maxLength > dst.length - offset)) {
            throw new IndexOutOfBoundsException("Illegal read offset / maxLength");
        }
        final int len = Math.min(dst.length - offset, maxLength);
        if ((len == 0) || !hasRemaining()) {
            // Nothing to do
            return 0;
        }
        try {
            int bytesRead = in.readNBytes(dst, offset, len);
            position += bytesRead;
            if (bytesRead < len) {
                eof = true;
            }
            return bytesRead;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        if (!dst.hasArray()) {
            return ReadableSequentialData.super.readBytes(dst);
        }
        if (!hasRemaining()) {
            return 0;
        }
        final byte[] dstArr = dst.array();
        final int dstArrOffset = dst.arrayOffset();
        final int dstPos = dst.position();
        final long len = Math.min(remaining(), dst.remaining());
        try {
            int bytesRead = in.readNBytes(dstArr, dstPos + dstArrOffset, Math.toIntExact(len));
            position += bytesRead;
            if (bytesRead < len) {
                eof = true;
            }
            return bytesRead;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public long readBytes(@NonNull final BufferedData dst) {
        final long len = Math.min(remaining(), dst.remaining());
        int bytesRead = dst.writeBytes(in, Math.toIntExact(len));
        position += bytesRead;
        if (bytesRead < len) {
            eof = true;
        }
        return bytesRead;
    }


    @Override
    public long readVarLong(final boolean zigZag) {
        if (!hasRemaining()) {
            throw new BufferUnderflowException();
        }

        long value = 0;

        try {
            int i = 0;
            for (; i < 10; i++) {
                final int b = in.read();
                if (b < 0) {
                    eof = true;
                    throw new EOFException();
                }
                value |= (long) (b & 0x7F) << (i * 7);
                if ((b & 0x80) == 0) {
                    position += i + 1;
                    return zigZag ? (value >>> 1) ^ -(value & 1) : value;
                }
            }
            assert i == 10;
            throw new DataEncodingException("Malformed var int");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
