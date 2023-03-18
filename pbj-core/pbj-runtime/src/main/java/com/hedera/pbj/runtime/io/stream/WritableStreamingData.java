package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * A {@code FilterOutputStream} that makes it easy to convert any {@code OutputStream} to a {@code DataOutput}
 */
public class WritableStreamingData extends FilterOutputStream implements WritableSequentialData {

    /** The current position, aka the number of bytes written */
    private long position = 0;

    /** The current limit for reading, defaults to Long.MAX_VALUE basically unlimited */
    private long limit = Long.MAX_VALUE;

    /**
     * Creates an {@code FilterOutputStream} built on top of the specified underlying output stream, that implements
     * {@code DataOutput}
     *
     * @param out the underlying output stream to be written to, can not be null
     */
    public WritableStreamingData(@NonNull final OutputStream out) {
        super(Objects.requireNonNull(out));
    }

    @Override
    public long capacity() {
        return Long.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long position() {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long limit() {
        return limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void limit(long limit) {
        this.limit = limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRemaining() {
        return (limit - position) > 0;
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
            count = Math.max(count, remaining());
            for (int i = 0; i < count; i++) {
                out.write(0);
            }
            position += count;
            return count;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(byte b) {
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
    public void writeBytes(@NonNull byte[] src, int offset, int length) {
        try {
            out.write(src, offset, length);
            position += length;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull byte[] src) {
        try {
            out.write(src);
            position += src.length;
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }
}
