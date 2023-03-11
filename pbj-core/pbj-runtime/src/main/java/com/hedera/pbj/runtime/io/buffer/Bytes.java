package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * Implementation of Bytes backed by a ByteBuffer
 */
public final class Bytes implements RandomAccessData {

    /** Single instance of an empty {@link RandomAccessData} we can use anywhere we need an empty instance */
    public static final Bytes EMPTY = new Bytes(new byte[0]);

    /** byte[] used as backing buffer for this {@link Bytes} */
    private final byte[] buffer;

    /** The start offset in {@code buffer} that we start at */
    private final int start;

    /** The number of bytes in this {@link Bytes} */
    private final int length;

    /**
     * Create a new ByteOverByteBuffer over given subsection of a ByteBuffer. This does not copy data it just wraps so
     * any changes to bytebuffer contents will be effected here. Changes to other state in ByteBuffer like position,
     * limit and mark have no effect. This is designed to be efficient at the risk of an unexpected data change.
     *
     * @param buffer The buffer to wrap
     * @param offset The offset within that buffer to start
     * @param length The length of bytes staring at offset to wrap
     */
    public Bytes(@NonNull final ByteBuffer buffer, final int offset, final int length) {
        this.buffer = buffer.array();
        this.start = offset;
        this.length = length;
    }

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps so
     * any changes to arrays contents will be effected here.
     *
     * @param data The data t
     */
    public Bytes(@NonNull final byte[] data) {
        this.buffer = data;
        this.start = 0;
        this.length = data.length;
    }

    /**
     * Convience method to create a new {@link Bytes} from a byte array.
     *
     * @param bytes The data to wrap
     * @return an instance of {@link Bytes}.
     */
    @NonNull
    public static Bytes wrap(@NonNull final byte[] bytes) {
        return new Bytes(bytes);
    }

    // ================================================================================================================
    // Object Methods

    /**
     * Create and return a new {@link ReadableSequentialData} that is backed by this {@link Bytes}.
     * It will not perform ANY copy operation.
     *
     * @return A {@link ReadableSequentialData}
     */
    @NonNull
    public ReadableSequentialData toReadableSequentialData() {
        return new ReadableSequentialData() {
            private long position = 0;
            private long limit = length;

            /**
             * {@inheritDoc}
             */
            @Override
            public byte readByte() {
                return getByte(Math.toIntExact(position++));
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public long position() {
                return position;
            }

            @Override
            public long limit() {
                return limit;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void limit(final long limit) {
                this.limit = (int) Math.min(limit, length);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public long skip(final long count) {
                final long skipped = Math.min(count, remaining());
                position += skipped;
                return skipped;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean hasRemaining() {
                return position < limit;
            }
        };
    }

    /**
     * Exposes this {@link Bytes} as an {@link InputStream}. This is a zero-copy operation.
     *
     * @return An {@link InputStream} that streams over the full set of data in this {@link Bytes}.
     */
    @NonNull
    public InputStream toInputStream() {
        return new InputStream() {
            private long pos = 0;
            @Override
            public int read() throws IOException {
                if (length - pos <= 0) {
                    return -1;
                }

                try {
                    return getUnsignedByte(pos++);
                } catch (DataAccessException e) {
                    // Catch and convert to IOException because the caller of the InputStream API
                    // will expect an IOException and NOT a DataAccessException.
                    throw new IOException(e);
                }
            }
        };
    }

    /**
     * Exposes this {@link Bytes} as an {@link DataInputStream}. This is a zero-copy operation.
     *
     * @return An {@link DataInputStream} that streams over the full set of data in this {@link Bytes}.
     */
    public @NonNull DataInputStream toDataInputStream() {
        return new DataInputStream(toInputStream());
    }

    /**
     * toString that outputs data in buffer in bytes.
     *
     * @return nice debug output of buffer contents
     */
    @Override
    @NonNull
    public String toString() {
        // build string
        StringBuilder sb = new StringBuilder();
        sb.append("Bytes[");
        for (int i = 0; i < length(); i++) {
            int v = getByte(i) & 0xFF;
            sb.append(v);
            if (i < (length()-1)) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Equals, important that it works for all subclasses of Bytes as well. As any 2 Bytes classes with same contents of
     * bytes are equal
     *
     * @param o the other Bytes object to compare to for equality
     * @return true if o instance of Bytes and contents match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bytes that)) return false;
        if (length != that.length()) {
            return false;
        }
        if (length == 0) return true;
        for (int i = 0; i < length; i++) {
            if (getByte(i) != that.getByte(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compute hash code for Bytes based on all bytes of content
     *
     * @return unique for any given content
     */
    @Override
    public int hashCode() {
        int h = 1;
        for (long i = length() - 1; i >= 0; i--) {
            h = 31 * h + getByte(i);
        }
        return h;
    }

    // ================================================================================================================
    // Bytes Methods

    /**
     * Package privet helper method for efficient copy of our data into another ByteBuffer with no effect on this
     * buffers state, so thread safe for this buffer. The destination buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     */
    void writeTo(ByteBuffer dstBuffer) {
        dstBuffer.put(dstBuffer.position(),buffer,start, length);
        dstBuffer.position(dstBuffer.position() + length);
    }

    /**
     * Get the number of bytes of data stored
     *
     * @return number of bytes of data stored
     */
    @Override
    public long length() {
        return length;
    }

    /**
     * Gets the byte at given {@code offset}.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset}
     */
    @Override
    public byte getByte(long offset) {
        return buffer[start + Math.toIntExact(offset)];
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset    The offset into data to get bytes at
     * @param dst       The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                  no larger than {@code dst.length}
     * @param length    The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                  no larger than {@code dst.length - offset}
     */
    @Override
    public void getBytes(long offset, @NonNull byte[] dst, int dstOffset, int length) {
        System.arraycopy(buffer, start + Math.toIntExact(offset), dst, dstOffset, length);
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code }dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst    The destination array
     */
    @Override
    public void getBytes(long offset, @NonNull byte[] dst) {
        System.arraycopy(buffer, start + Math.toIntExact(offset), dst, 0, dst.length);
    }
}
