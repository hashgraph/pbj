package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.stream.EOFException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import java.security.MessageDigest;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;

/**
 * An immutable representation of a byte array. This class is designed to be efficient and usable across threads.
 */
@SuppressWarnings("unused")
public final class Bytes implements BufferedSequentialData, ReadableSequentialData {

    /** An instance of an empty {@link Bytes} */
    public static final Bytes EMPTY = new Bytes(new byte[0]);

    /** Sorts {@link Bytes} according to their length, shorter first. */
    public static final Comparator<Bytes> SORT_BY_LENGTH = (Bytes o1, Bytes o2) ->
            Comparator.comparingLong(Bytes::length).compare(o1, o2);

    /** Sorts {@link Bytes} according to their byte values, lower valued bytes first.
      * Bytes are compared on a signed basis.
      */
    public static final Comparator<Bytes> SORT_BY_SIGNED_VALUE = valueSorter(Byte::compare);

    /** Sorts {@link Bytes} according to their byte values, lower valued bytes first.
      * Bytes are compared on an unsigned basis
      */
    public static final Comparator<Bytes> SORT_BY_UNSIGNED_VALUE = valueSorter(Byte::compareUnsigned);

    /** The limit for the offset of this {@link Bytes} object */
    private long limit;

    /** Current position in the buffer for this Bytes object */
    private int pos;

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
        limit = length;

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

    /**
     * Create a new Bytes with the contents of a UTF8 Base64 encoded String.
     *
     * @param string The base64 UFT8 encoded string to decode
     * @return new {@link Bytes} with string contents decoded from base64
     * @throws NullPointerException if string is null
     */
    @NonNull
    public static Bytes fromBase64(@NonNull final String string) {
        return new Bytes(Base64.getDecoder().decode(string));
    }

    /**
     * Create a new Bytes with the contents of a UTF8 hex encoded String.
     *
     * @param string The hex UFT8 encoded string to decode
     * @return new {@link Bytes} with string contents decoded from hex
     * @throws NullPointerException if string is null
     */
    @NonNull
    public static Bytes fromHex(@NonNull final String string) {
        return new Bytes(HexFormat.of().parseHex(string.toLowerCase()));
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
     * A helper method for efficient copy of our data into another ByteBuffer.
     * The destination buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     */
    public void writeTo(@NonNull final ByteBuffer dstBuffer) {
        dstBuffer.put(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into another ByteBuffer.
     * The destination buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final ByteBuffer dstBuffer, final int offset, final int length) {
        dstBuffer.put(buffer, offset, length);
        dstBuffer.position(dstBuffer.position() + length);
    }

    /**
     * A helper method for efficient copy of our data into an OutputStream without creating a defensive copy
     * of the data. The implementation relies on a well-behaved OutputStream that doesn't modify the buffer data.
     *
     * @param outStream the OutputStream to copy into
     */
    public void writeTo(@NonNull final OutputStream outStream) {
        try {
            outStream.write(buffer, start, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A helper method for efficient copy of our data into an OutputStream without creating a defensive copy
     * of the data. The implementation relies on a well-behaved OutputStream that doesn't modify the buffer data.
     *
     * @param outStream The OutputStream to copy into.
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final OutputStream outStream, final int offset, final int length) {
        try {
            outStream.write(buffer, offset, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A helper method for efficient copy of our data into an WritableSequentialData without creating a defensive copy
     * of the data. The implementation relies on a well-behaved WritableSequentialData that doesn't modify the buffer data.
     *
     * @param wsd the OutputStream to copy into
     */
    public void writeTo(@NonNull final WritableSequentialData wsd) {
        wsd.writeBytes(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into an WritableSequentialData without creating a defensive copy
     * of the data. The implementation relies on a well-behaved WritableSequentialData that doesn't modify the buffer data.
     *
     * @param wsd The OutputStream to copy into.
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final WritableSequentialData wsd, final int offset, final int length) {
        wsd.writeBytes(buffer, offset, length);
    }

    /**
     * A helper method for efficient copy of our data into an MessageDigest without creating a defensive copy
     * of the data. The implementation relies on a well-behaved MessageDigest that doesn't modify the buffer data.
     *
     * @param digest the MessageDigest to copy into
     */
    public void writeTo(@NonNull final MessageDigest digest) {
        digest.update(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into an MessageDigest without creating a defensive copy
     * of the data. The implementation relies on a well-behaved MessageDigest that doesn't modify the buffer data.
     *
     * @param digest the MessageDigest to copy into
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final MessageDigest digest, final int offset, final int length) {
        digest.update(buffer, offset, length);
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
     * Returns a Base64 encoded string of the bytes in this object.
     *
     * @return Base64 encoded string of the bytes in this object.
     */
    public String toBase64() {
        if (start == 0 && buffer.length == length) {
            return Base64.getEncoder().encodeToString(buffer);
        } else {
            byte[] bytes = new byte[length];
            getBytes(0,bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    /**
     * Returns a Hex encoded string of the bytes in this object.
     *
     * @return Hex encoded string of the bytes in this object.
     */
    public String toHex() {
        return HexFormat.of().formatHex(buffer,start,start+length);
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

    /** * Gets a byte[] of the bytes of this {@link Bytes} object..
     *
     * @return a clone of the bytes of this {@link Bytes} object or null.
     */
    @NonNull
    public byte[] toByteArray() {
        return toByteArray(0, length);
    }

    /** * Gets a byte[] of the bytes of this {@link Bytes} object..
     *
     * @param offset The start offset to get the bytes from.
     * @param length The number of bytes to get.
     * @return a clone of the bytes of this {@link Bytes} object or null.
     */
    @NonNull
    public byte[] toByteArray(final int offset, final int length) {
        byte[] ret = new byte[length];
        getBytes(offset, ret);
        return ret;
    }

    private void validateOffset(long offset) {
        if (offset < 0 || offset > this.length) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + this.length);
        }
    }

    /** Sorts {@link Bytes} according to their byte values, lower valued bytes first.
      * Bytes are compared using the passed in Byte Comparator
      */
    private static Comparator<Bytes> valueSorter(@NonNull final Comparator<Byte> byteComparator) {
        return (Bytes o1, Bytes o2) -> {
            final var val = Math.min(o1.length(), o2.length());
            for (long i = 0; i < val; i++) {
                final var byteComparison = byteComparator.compare(o1.getByte(i), o2.getByte(i));
                if (byteComparison != 0) {
                    return byteComparison;
                }
            }

            // In case one of the buffers is longer than the other and the first n bytes (where n in the length of the
            // shorter buffer) are equal, the buffer with the shorter length is first in the sort order.
            long len = o1.length() - o2.length();
            if (len == 0) {
                return 0;
            }
            return ((len > 0) ? 1 : -1);
        };
    }

    /**
     * Appends a {@link Bytes} object to this {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param bytes The {@link Bytes} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    @NonNull
    public Bytes append(@NonNull final Bytes bytes) {
        // The length field of Bytes is int. The length() returns always an int,
        // so safe to cast.
        long length = this.length();
        byte[] newBytes = new byte[(int)(length + (int)bytes.length())];
        this.getBytes(0, newBytes, 0, (int) length);
        bytes.getBytes(0, newBytes, (int) length, (int)bytes.length());
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends a {@link RandomAccessData} object to this {@link Bytes} object, producing a new immutable {link Bytes} object.
     * @param data The {@link RandomAccessData} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than Bytes.length()
     */
    @NonNull
    public Bytes append(@NonNull final RandomAccessData data) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int)(this.length() + (int)data.length())];
        int length1 = (int) this.length();
        this.getBytes(0, newBytes, 0, length1);
        data.getBytes(0, newBytes, length1, (int)data.length());
        return Bytes.wrap(newBytes);
    }

    /** {@inheritDoc} */
    @Override
    public int getVarInt(final long offset, final boolean zigZag) {
        int tempPos = (int)offset;
        if (length == tempPos) {
            return (int) getVarLongNonOptimized(offset, zigZag);
        }
        int x;
        if ((x = buffer[tempPos++]) >= 0) {
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (length - tempPos < 9) {
            return (int) getVarLongNonOptimized(offset, zigZag);
        } else if ((x ^= (buffer[tempPos++] << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (buffer[tempPos++] << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (buffer[tempPos++] << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = buffer[tempPos++];
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0) {
                return (int) getVarLongNonOptimized(offset, zigZag);
            }
        }
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /** {@inheritDoc} */
    @Override
    public long getVarLong(final long offset, final boolean zigZag) {
        int tempPos = (int)offset;
        if (tempPos == length) {
            return getVarLongNonOptimized(offset, zigZag);
        }
        long x;
        int y;
        if ((y = buffer[tempPos++]) >= 0) {
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (length - tempPos < 9) {
            return getVarLongNonOptimized(offset, zigZag);
        } else if ((y ^= (buffer[tempPos++] << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (buffer[tempPos++] << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (buffer[tempPos++] << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) buffer[tempPos++] << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) buffer[tempPos++] << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) buffer[tempPos++] << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) buffer[tempPos++] << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) buffer[tempPos++] << 56);
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56);
            if (x < 0L) {
                if (buffer[tempPos++] < 0L) {
                    return getVarLongNonOptimized(offset, zigZag);
                }
            }
        }
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }



    /** {@inheritDoc} */
    @Override
    public int readVarInt(final boolean zigZag) {
        int tempPos = pos;
        if (length == tempPos) {
            return (int) readVarIntLongNonOptimized(zigZag);
        }
        int x;
        if ((x = buffer[tempPos++]) >= 0) {
            pos++;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (length - tempPos < 9) {
            return (int) readVarIntLongNonOptimized(zigZag);
        } else if ((x ^= (buffer[tempPos++] << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (buffer[tempPos++] << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (buffer[tempPos++] << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = buffer[tempPos++];
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0
                    && buffer[tempPos++] < 0) {
                return (int) readVarIntLongNonOptimized(zigZag);
            }
        }
        pos = tempPos;
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /** {@inheritDoc} */
    @Override
    public long readVarLong(final boolean zigZag) {
        int tempPos = pos;
        if (tempPos == length) {
            return readVarIntLongNonOptimized(zigZag);
        }
        long x;
        int y;
        if ((y = buffer[tempPos++]) >= 0) {
            pos++;
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (length - tempPos < 9) {
            return readVarIntLongNonOptimized(zigZag);
        } else if ((y ^= (buffer[tempPos++] << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (buffer[tempPos++] << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (buffer[tempPos++] << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) buffer[tempPos++] << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) buffer[tempPos++] << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) buffer[tempPos++] << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) buffer[tempPos++] << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) buffer[tempPos++] << 56);
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49)
                            ^ (~0L << 56);
            if (x < 0L) {
                if (buffer[tempPos++] < 0L) {
                    return readVarIntLongNonOptimized(zigZag);
                }
            }
        }
        pos = tempPos;
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /** {@inheritDoc} */
    @Override
    public long capacity() {
        return length;
    }

    /** {@inheritDoc} */
    @Override
    public long position() {
        return pos;
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
        this.limit = Math.min(length, Math.max(pos, limit));
    }

    /** {@inheritDoc} */
    @Override
    public long remaining() {
        return limit - pos;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasRemaining() {
        return pos < limit;
    }




    /** {@inheritDoc} */
    @Override
    public byte readByte() {
        if (pos >= limit) {
            throw new BufferUnderflowException(); // throw new UncheckedIOException(/* new BufferUnderflowException(*/new IOException(pos + ":" + limit)); //);
        }
        return buffer[pos++];
    }

    /** {@inheritDoc} */
    @Override
    public int readUnsignedByte() {
        checkUnderflow(1);
        return Byte.toUnsignedInt(buffer[pos++]);
    }

    /** {@inheritDoc} */
    public long readBytes(@NonNull final byte[] dst) {
        return readBytes(dst, 0, dst.length);
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var length = Math.min(maxLength, (this.length - pos));
        final var maxIndex = offset + length;
        long bytesRead = 0;
        for (int i = offset; i < maxIndex; i++) {
            if (pos > this.limit) return (long) i - offset;
            try {
                dst[i] = readByte();
                bytesRead++;
            } catch (EOFException e) {
                return bytesRead;
            }
        }
        return length;
    }

    @Override
    /** {@inheritDoc} */
    public long readBytes(@NonNull final ByteBuffer dst) {
        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var len = dst.remaining();
        long bytesRead = 0;
        for (int i = 0; i < len; i++) {
            if (pos >= limit) return i;
            try {
                dst.put(buffer[pos++]);
                bytesRead++;
            } catch (EOFException e) {
                return bytesRead;
            }
        }
        return len;
    }

    /** {@inheritDoc}*/
    @Override
    public long readBytes(@NonNull final BufferedData dst) {
        // Read up to maxLength bytes into the dst array. Note the check for `hasRemaining()` is done in the loop
        // because, for streams, we cannot determine ahead of time the total number of available bytes, so we must
        // continue to check as we process each byte. This is not efficient for buffers.
        final var len = dst.remaining();
        long bytesRead = 0;
        for (int i = 0; i < len; i++) {
            if (pos >= limit) return i;
            try {
                dst.writeByte(readByte());
                bytesRead++;
            } catch (EOFException e) {
                return bytesRead;
            }
        }
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull Bytes readBytes(final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative length not allowed");
        }

        if ((this.limit - pos) < length) {
            throw new BufferUnderflowException();
        }

        final var bytes = new byte[length];
        readBytes(bytes, 0, length);
        return Bytes.wrap(bytes);
    }





    /** {@inheritDoc} */
    @NonNull
    @Override
    public ReadableSequentialData view(final int length) {
        checkUnderflow(length);

        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        final var view = new RandomAccessSequenceAdapter(Bytes.wrap(buffer, start + pos, length));
        pos += view.capacity();
        return view;
    }

    /** {@inheritDoc} */
    @Override
    public int readInt() {
        checkUnderflow(4);
        final byte b1 = buffer[pos++];
        final byte b2 = buffer[pos++];
        final byte b3 = buffer[pos++];
        final byte b4 = buffer[pos++];
        return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);

    }

    /** {@inheritDoc} */
    @Override
    public int readInt(@NonNull final ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            checkUnderflow(4);
            final byte b4 = buffer[pos++];
            final byte b3 = buffer[pos++];
            final byte b2 = buffer[pos++];
            final byte b1 = buffer[pos++];
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        } else {
            return readInt();
        }
    }

    @Override
    public long readLong() {
        // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
        //noinspection DuplicatedCode
        checkUnderflow(Long.BYTES);
        final byte b1 = buffer[pos++];
        final byte b2 = buffer[pos++];
        final byte b3 = buffer[pos++];
        final byte b4 = buffer[pos++];
        final byte b5 = buffer[pos++];
        final byte b6 = buffer[pos++];
        final byte b7 = buffer[pos++];
        final byte b8 = buffer[pos++];
        return (((long)b1 << 56) +
                ((long)(b2 & 255) << 48) +
                ((long)(b3 & 255) << 40) +
                ((long)(b4 & 255) << 32) +
                ((long)(b5 & 255) << 24) +
                ((b6 & 255) << 16) +
                ((b7 & 255) <<  8) +
                (b8 & 255));
    }

    /** {@inheritDoc} */
    public long readLong(@NonNull final ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            checkUnderflow(Long.BYTES);
            final byte b8 = buffer[pos++];
            final byte b7 = buffer[pos++];
            final byte b6 = buffer[pos++];
            final byte b5 = buffer[pos++];
            final byte b4 = buffer[pos++];
            final byte b3 = buffer[pos++];
            final byte b2 = buffer[pos++];
            final byte b1 = buffer[pos++];
            return (((long) b1 << 56) +
                    ((long) (b2 & 255) << 48) +
                    ((long) (b3 & 255) << 40) +
                    ((long) (b4 & 255) << 32) +
                    ((long) (b5 & 255) << 24) +
                    ((b6 & 255) << 16) +
                    ((b7 & 255) << 8) +
                    (b8 & 255));
        } else {
            return readLong();
        }
    }

    /** {@inheritDoc} */
    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    /** {@inheritDoc} */
    @Override
    public float readFloat(@NonNull final ByteOrder byteOrder) {
        return Float.intBitsToFloat(readInt(byteOrder));
    }

    /** {@inheritDoc} */
    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    /** {@inheritDoc} */
    @Override
    public double readDouble(@NonNull final ByteOrder byteOrder) {
        return Double.longBitsToDouble(readLong(byteOrder));
    }

    /** Utility method for checking if there is enough data to read */
    private void checkUnderflow(int remainingBytes) {
        if ((limit - pos) - remainingBytes < 0) {
            throw new BufferUnderflowException();
        }
    }

    /** {@inheritDoc} */
    @Override
    public long skip(long count) {
        final var c = Math.min(count, length);
        if (c <= 0) {
            return 0;
        }

        pos += c;
        return c;
    }

    // ================================================================================================================
    // BufferedSequentialData Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void position(final long position) {
        if (position > this.limit | position < 0) {
            throw new BufferUnderflowException();
        }

        this.pos = (int)position;
    }

    /** {@inheritDoc} */
    @Override
    public void flip() {
        this.limit = this.pos;
        this.pos = 0;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        pos = 0;
    }

    /** {@inheritDoc} */
    @Override
    public void resetPosition() {
        pos = 0;
    }

    /**
     * Get a 64bit protobuf varint at given {@code offset}. A long var int can be 1 to 10 bytes.
     *
     * @param offset The offset into data to get a varlong from.
     * @return long get in var long format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws BufferUnderflowException If the end of the buffer is encountered before the last segment of the varlong
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than {@link #length()}
     */
    private long getVarLongNonOptimized(final long offset, final boolean zigZag) {
        long result = 0;
        int index = (int) offset;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = buffer[index++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new RuntimeException("Malformed Varlong");
    }

    private long readVarIntLongNonOptimized(final boolean zigZag) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = buffer[pos++];
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? (result >>> 1) ^ -(result & 1) : result;
            }
        }
        throw new DataEncodingException("Malformed Varlong");
    }
}
