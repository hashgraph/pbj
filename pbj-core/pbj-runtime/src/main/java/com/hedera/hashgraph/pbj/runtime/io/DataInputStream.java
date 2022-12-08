package com.hedera.hashgraph.pbj.runtime.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * A {@code FilterInputStream} that makes it easy to convert any {@code InputStream} to a {@code DataInput}
 */
public class DataInputStream extends FilterInputStream implements DataInput {

    /** The current position, aka the number of bytes read */
    private long position = 0;

    /** The current limit for reading, defaults to Long.MAX_VALUE basically unlimited */
    private long limit = Long.MAX_VALUE;

    /**
     * Creates a {@code FilterInputStream} that implements {@code DataInput} API.
     *
     * @param in the underlying input stream, can not be null
     */
    protected DataInputStream(InputStream in) {
        super(Objects.requireNonNull(in));
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
     * {@inheritDoc}
     */
    @Override
    public byte readByte() throws IOException {
        return (byte) in.read();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(byte[] dst, int offset, int length) throws IOException {
        in.read(dst, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(byte[] dst) throws IOException {
        in.read(dst);
    }
}
