package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static java.util.Objects.requireNonNull;

/**
 * An immutable representation of a byte array. This class is designed to be efficient and usable across threads.
 */
public final class Bytes implements RandomAccessData {

    /** An instance of an empty {@link Bytes} */
    public static final Bytes EMPTY = new Bytes(new byte[0]);

    /** byte[] used as backing buffer */
    private final byte[] buffer;

    /**
     * The offset within the backing buffer where this {@link Bytes} starts. To prevent array copies, we sometimes
     * want to have a "view" or "slice" of another buffer, where we begin at some offset and have a length.
     */
    private final int start;

    /**
     * The number of bytes in this {@link Bytes}. To prevent array copies, we sometimes want to have a "view" or
     * "slice" of another buffer, where we begin at some offset and have a length.
     */
    private final int length;

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps so
     * any changes to arrays contents will be effected here.
     *
     * @param data The data t
     */
    private Bytes(@NonNull final byte[] data) {
        this(data, 0, data.length);
    }

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps so
     * any changes to arrays contents will be effected here.
     *
     * @param data The data t
     * @param offset The offset within that buffer to start
     * @param length The length of bytes staring at offset to wrap
     */
    private Bytes(@NonNull final byte[] data, final int offset, final int length) {
        this.buffer = requireNonNull(data);
        this.start = offset;
        this.length = length;

        if (offset < 0 || offset > data.length) {
            throw new IndexOutOfBoundsException("Offset " + offset + " is out of bounds for buffer of length "
                    + data.length);
        }

        if (length < 0) {
            throw new IllegalArgumentException("Length " + length + " is negative");
        }

        if (offset + length > data.length) {
            throw new IllegalArgumentException("Length " + length + " is too large buffer of length "
                    + data.length + " starting at offset " + offset);
        }
    }

    // ================================================================================================================
    // Static Methods

    /**
     * Create a new {@link Bytes} over the contents of the given byte array. This does not copy data it just
     * wraps so any changes to array's contents will be visible in the returned result.
     *
     * @param byteArray The byte array to wrap
     * @return new {@link Bytes} with same contents as byte array
     * @throws NullPointerException if byteArray is null
     */
    @NonNull
    public static Bytes wrap(@NonNull final byte[] byteArray) {
        return new Bytes(byteArray);
    }

    /**
     * Create a new {@link Bytes} over the contents of the given byte array. This does not copy data it just
     * wraps so any changes to arrays contents will be visible in the returned result.
     *
     * @param byteArray The byte array to wrap
     * @param offset The offset within that buffer to start. Must be &gt;= 0 and &lt; byteArray.length
     * @param length The length of bytes staring at offset to wrap. Must be &gt;= 0 and &lt; byteArray.length - offset
     * @return new {@link Bytes} with same contents as byte array
     * @throws NullPointerException if byteArray is null
     * @throws IndexOutOfBoundsException if offset or length are out of bounds
     * @throws IllegalArgumentException if length is negative
     */
    @NonNull
    public static Bytes wrap(@NonNull final byte[] byteArray, final int offset, final int length) {
        return new Bytes(byteArray, offset, length);
    }

    /**
     * Create a new Bytes with the contents of a UTF8 encoded String.
     *
     * @param string The UFT8 encoded string to wrap
     * @return new {@link Bytes} with string contents UTF8 encoded
     * @throws NullPointerException if string is null
     */
    @NonNull
    public static Bytes wrap(@NonNull final String string) {
        return new Bytes(string.getBytes(StandardCharsets.UTF_8));
    }

    // ================================================================================================================
    // Object Methods

    /**
     * Duplicate this {@link Bytes} by making a copy of the underlying byte array and returning a new {@link Bytes}
     * over the copied data. Use this method when you need to wrap a copy of a byte array:
     *
     * <pre>
     *     final var arr = new byte[] { 1, 2, 3 };
     *     final var bytes = Bytes.wrap(arr).replicate();
     *     arr[0] = 4; // this modification will NOT be visible in the "bytes" instance
     * </pre>
     *
     * <p>Implementation note: since we will be making an array copy, if the source array had an offset and length,
     * the newly copied array will only contain the bytes between the offset and length of the original array.
     *
     * @return A new {@link Bytes} instance with a copy of the underlying byte array data.
     */
    @NonNull
    public Bytes replicate() {
        final var newLength = length - start;
        final var bytes = new byte[newLength];
        System.arraycopy(buffer, start, bytes, 0, newLength);
        return new Bytes(bytes, 0, newLength);
    }

    /**
     * Package privet helper method for efficient copy of our data into another ByteBuffer with no effect on this
     * buffers state, so thread safe for this buffer. The destination buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     */
    void writeTo(@NonNull final ByteBuffer dstBuffer) {
        dstBuffer.put(dstBuffer.position(), buffer, start, length);
        dstBuffer.position(dstBuffer.position() + length);
    }

    /**
     * Create and return a new {@link ReadableSequentialData} that is backed by this {@link Bytes}.
     *
     * @return A {@link ReadableSequentialData} backed by this {@link Bytes}.
     */
    @NonNull
    public ReadableSequentialData toReadableSequentialData() {
        return new RandomAccessSequenceAdapter(this);
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
        for (long i = 0; i < length(); i++) {
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
    public boolean equals(@Nullable final Object o) {
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
    // RandomAccessData Methods

    /** {@inheritDoc} */
    @Override
    public long length() {
        return length;
    }

    /** {@inheritDoc} */
    @Override
    public byte getByte(final long offset) {
        if (length == 0) {
            throw new BufferUnderflowException();
        }

        validateOffset(offset);
        return buffer[start + Math.toIntExact(offset)];
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        validateOffset(offset);

        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        // Maybe this instance is empty and there is nothing to get
        if (maxLength == 0) {
            return 0;
        }

        // This is a faster implementation than the default, since it has access to the entire byte array
        // and can do a system array copy instead of a loop.
        final var len = Math.min(maxLength, length - offset);
        System.arraycopy(buffer, start + Math.toIntExact(offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        validateOffset(offset);

        // Maybe this instance is empty and there is nothing to get
        if (length == 0) {
            return 0;
        }

        // This is a faster implementation than the default, since it has access to the entire byte array
        // and can do a system array copy instead of a loop.
        final var len = Math.min(dst.remaining(), length - offset);
        dst.put(buffer, start + Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final BufferedData dst) {
        validateOffset(offset);

        // Maybe this instance is empty and there is nothing to get
        if (length == 0) {
            return 0;
        }

        // This is a faster implementation than the default, since it has access to the entire byte array
        // and can do a system array copy instead of a loop.
        final var len = Math.min(dst.remaining(), length - offset);
        dst.writeBytes(buffer, start + Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes getBytes(long offset, long length) {
        validateOffset(offset);

        if (length > this.length - offset) {
            throw new BufferUnderflowException();
        }

        // Maybe this instance is empty and there is nothing to get
        if (length == 0) {
            return Bytes.EMPTY;
        }

        return new Bytes(buffer, Math.toIntExact(start + offset), Math.toIntExact(length));
    }


    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes slice(long offset, long length) {
        return getBytes(offset, length);
    }

    private void validateOffset(long offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + this.length);
        }
    }
}
