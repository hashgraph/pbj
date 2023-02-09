package com.hedera.pbj.runtime.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * <p>A {@code FilterInputStream} that makes it easy to convert any {@code InputStream} to a {@code DataInput}. This is
 * not the fastest way to read data but useful when you do not have a better option.</p>
 *
 * <p>It always has to read 1 byte ahead so it can detect end of stream and set limit correctly. This allows code to
 * read while hasRemaining() == true</p>
 *
 */
public class DataInputStream extends FilterInputStream implements DataInput {

    /** The current position, aka the number of bytes read */
    private long position = 0;

    /** The current limit for reading, defaults to Long.MAX_VALUE basically unlimited */
    private long limit = Long.MAX_VALUE;

    /** The next byte to be read from input stream, this is read ahead to detect end of stream */
    private byte nextByte;

    /**
     * Creates a {@code FilterInputStream} that implements {@code DataInput} API.
     *
     * @param in the underlying input stream, can not be null
     */
    public DataInputStream(InputStream in) {
        super(Objects.requireNonNull(in));
        try {
            readNextByte();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set a new InputStream, allows this object to be reused.
     *
     * @param newIn the new input stream to use
     * @throws IOException if there was a problem reading first byte
     */
    public void setInputStream(InputStream newIn) throws IOException {
        in = newIn;
        position = 0;
        limit = Long.MAX_VALUE;
        readNextByte();
    }

    private void readNextByte() throws IOException{
        // Have to read as int, so we can detect difference between byte -1 int for EOF and 255 valid byte
        final int nextByteAsInt = in.read();
        nextByte = (byte) nextByteAsInt;
        if (nextByteAsInt == -1) limit = position;
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
        // don't allow setting of limit beyond position when we are at end of stream
        this.limit = nextByte == -1 ? position : limit;
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
        byte readByte = nextByte;
        readNextByte();
        position ++;
        return readByte;
    }
}
