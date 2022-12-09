package com.hedera.hashgraph.pbj.runtime.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.util.Objects;

/**
 * A {@code FilterOutputStream} that makes it easy to convert any {@code OutputStream} to a {@code DataOutput}
 */
public class DataOutputStream  extends FilterOutputStream implements DataOutput {

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
    public DataOutputStream(OutputStream out) {
        super(Objects.requireNonNull(out));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPosition() {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLimit() {
        return limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLimit(long limit) {
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
    public long skip(long count) throws IOException {
        count = Math.max(count, getRemaining());
        for (int i = 0; i < count; i++) {
            out.write(0);
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(byte b) throws IOException {
        out.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src, int offset, int length) throws IOException {
        out.write(src, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src) throws IOException {
        out.write(src);
    }
}
