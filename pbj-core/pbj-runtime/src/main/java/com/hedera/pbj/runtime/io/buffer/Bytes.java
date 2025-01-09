// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.UnsafeUtils;
import com.hedera.pbj.runtime.io.WritableSequentialData;
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
import java.security.MessageDigest;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;

/**
 * An immutable representation of a byte array. This class is designed to be efficient and usable
 * across threads.
 */
@SuppressWarnings("unused")
public final class Bytes implements RandomAccessData, Comparable<Bytes> {

    /** An instance of an empty {@link Bytes} */
    public static final Bytes EMPTY = new Bytes(new byte[0]);

    /** Sorts {@link Bytes} according to their length, shorter first. */
    public static final Comparator<Bytes> SORT_BY_LENGTH =
            (Bytes o1, Bytes o2) -> Comparator.comparingLong(Bytes::length).compare(o1, o2);

    /**
     * Sorts {@link Bytes} according to their byte values, lower valued bytes first. Bytes are
     * compared on a signed basis.
     */
    public static final Comparator<Bytes> SORT_BY_SIGNED_VALUE = valueSorter(Byte::compare);

    /**
     * Sorts {@link Bytes} according to their byte values, lower valued bytes first. Bytes are
     * compared on an unsigned basis
     */
    public static final Comparator<Bytes> SORT_BY_UNSIGNED_VALUE =
            valueSorter(Byte::compareUnsigned);

    /** byte[] used as backing buffer */
    private final byte[] buffer;

    /**
     * The offset within the backing buffer where this {@link Bytes} starts. To prevent array
     * copies, we sometimes want to have a "view" or "slice" of another buffer, where we begin at
     * some offset and have a length.
     */
    private final int start;

    /**
     * The number of bytes in this {@link Bytes}. To prevent array copies, we sometimes want to have
     * a "view" or "slice" of another buffer, where we begin at some offset and have a length.
     */
    private final int length;

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps
     * so any changes to arrays contents will be effected here.
     *
     * @param data The data t
     */
    private Bytes(@NonNull final byte[] data) {
        this(data, 0, data.length);
    }

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps
     * so any changes to arrays contents will be effected here.
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
            throw new IndexOutOfBoundsException(
                    "Offset " + offset + " is out of bounds for buffer of length " + data.length);
        }

        if (length < 0) {
            throw new IllegalArgumentException("Length " + length + " is negative");
        }

        if (offset + length > data.length) {
            throw new IllegalArgumentException(
                    "Length "
                            + length
                            + " is too large buffer of length "
                            + data.length
                            + " starting at offset "
                            + offset);
        }
    }

    // ================================================================================================================
    // Static Methods

    /**
     * Create a new {@link Bytes} over the contents of the given byte array. This does not copy data
     * it just wraps so any changes to array's contents will be visible in the returned result.
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
     * Create a new {@link Bytes} over the contents of the given byte array. This does not copy data
     * it just wraps so any changes to arrays contents will be visible in the returned result.
     *
     * @param byteArray The byte array to wrap
     * @param offset The offset within that buffer to start. Must be &gt;= 0 and &lt;
     *     byteArray.length
     * @param length The length of bytes staring at offset to wrap. Must be &gt;= 0 and &lt;
     *     byteArray.length - offset
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

    @Override
    public int getInt(final long offset) {
        return UnsafeUtils.getInt(buffer, Math.toIntExact(start + offset));
    }

    @Override
    public int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.BIG_ENDIAN
                ? getInt(offset)
                : Integer.reverseBytes(getInt(offset));
    }

    @Override
    public long getLong(final long offset) {
        return UnsafeUtils.getLong(buffer, Math.toIntExact(start + offset));
    }

    @Override
    public long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.BIG_ENDIAN
                ? getLong(offset)
                : Long.reverseBytes(getLong(offset));
    }

    /**
     * Duplicate this {@link Bytes} by making a copy of the underlying byte array and returning a
     * new {@link Bytes} over the copied data. Use this method when you need to wrap a copy of a
     * byte array:
     *
     * <pre>
     *     final var arr = new byte[] { 1, 2, 3 };
     *     final var bytes = Bytes.wrap(arr).replicate();
     *     arr[0] = 4; // this modification will NOT be visible in the "bytes" instance
     * </pre>
     *
     * <p>Implementation note: since we will be making an array copy, if the source array had an
     * offset and length, the newly copied array will only contain the bytes between the offset and
     * length of the original array.
     *
     * @return A new {@link Bytes} instance with a copy of the underlying byte array data.
     */
    @NonNull
    public Bytes replicate() {
        final var bytes = new byte[length];
        System.arraycopy(buffer, start, bytes, 0, length);
        return new Bytes(bytes, 0, length);
    }

    /**
     * A helper method for efficient copy of our data into another ByteBuffer. The destination
     * buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     */
    public void writeTo(@NonNull final ByteBuffer dstBuffer) {
        dstBuffer.put(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into another ByteBuffer. The destination
     * buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final ByteBuffer dstBuffer, final int offset, final int length) {
        dstBuffer.put(buffer, Math.toIntExact(start + offset), length);
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(@NonNull final OutputStream outStream) {
        try {
            outStream.write(buffer, start, length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeTo(@NonNull final OutputStream outStream, final int offset, final int length) {
        try {
            outStream.write(buffer, Math.toIntExact(start + offset), length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A helper method for efficient copy of our data into an WritableSequentialData without
     * creating a defensive copy of the data. The implementation relies on a well-behaved
     * WritableSequentialData that doesn't modify the buffer data.
     *
     * @param wsd the OutputStream to copy into
     */
    public void writeTo(@NonNull final WritableSequentialData wsd) {
        wsd.writeBytes(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into an WritableSequentialData without
     * creating a defensive copy of the data. The implementation relies on a well-behaved
     * WritableSequentialData that doesn't modify the buffer data.
     *
     * @param wsd The OutputStream to copy into.
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(
            @NonNull final WritableSequentialData wsd, final int offset, final int length) {
        wsd.writeBytes(buffer, Math.toIntExact(start + offset), length);
    }

    /**
     * A helper method for efficient copy of our data into an MessageDigest without creating a
     * defensive copy of the data. The implementation relies on a well-behaved MessageDigest that
     * doesn't modify the buffer data.
     *
     * @param digest the MessageDigest to copy into
     */
    public void writeTo(@NonNull final MessageDigest digest) {
        digest.update(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into an MessageDigest without creating a
     * defensive copy of the data. The implementation relies on a well-behaved MessageDigest that
     * doesn't modify the buffer data.
     *
     * @param digest the MessageDigest to copy into
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from.
     * @param length The number of bytes to extract.
     */
    public void writeTo(@NonNull final MessageDigest digest, final int offset, final int length) {
        digest.update(buffer, Math.toIntExact(start + offset), length);
    }

    /**
     * Same as {@link #updateSignature(Signature, int, int)} with offset 0 and length equal to the
     * length of this {@link Bytes} object.
     */
    public void updateSignature(@NonNull final Signature signature) throws SignatureException {
        signature.update(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into a Signature without creating a defensive
     * copy of the data. The implementation relies on a well-behaved Signature that doesn't modify
     * the buffer data. Calls the {@link Signature#update(byte[], int, int)} method with all the
     * data in this {@link Bytes} object. This method should be used when the data in the buffer
     * should be validated or signed.
     *
     * @param signature The Signature to update
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from
     * @param length The number of bytes to extract
     * @throws SignatureException If the Signature instance throws this exception
     */
    public void updateSignature(
            @NonNull final Signature signature, final int offset, final int length)
            throws SignatureException {
        validateOffsetLength(offset, length);
        signature.update(buffer, calculateOffset(offset), length);
    }

    /**
     * Same as {@link #verifySignature(Signature, int, int)} with offset 0 and length equal to the
     * length of this {@link Bytes} object.
     */
    public boolean verifySignature(@NonNull final Signature signature) throws SignatureException {
        return signature.verify(buffer, start, length);
    }

    /**
     * A helper method for efficient copy of our data into a Signature without creating a defensive
     * copy of the data. The implementation relies on a well-behaved Signature that doesn't modify
     * the buffer data. Calls the {@link Signature#verify(byte[], int, int)} method with all the
     * data in this {@link Bytes} object. This method should be used when the data in the buffer is
     * a signature that should be verified.
     *
     * @param signature the Signature to use to verify
     * @param offset The offset from the start of this {@link Bytes} object to get the bytes from
     * @param length The number of bytes to extract
     * @return true if the signature is valid, false otherwise
     * @throws SignatureException If the Signature instance throws this exception
     */
    public boolean verifySignature(
            @NonNull final Signature signature, final int offset, final int length)
            throws SignatureException {
        validateOffsetLength(offset, length);
        return signature.verify(buffer, calculateOffset(offset), length);
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
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            }
        };
    }

    /**
     * Compare this {@link Bytes} object to another {@link Bytes} object. The comparison is done on
     * a byte-by-byte
     *
     * @param otherData the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     *     to, or greater than
     */
    @Override
    public int compareTo(Bytes otherData) {
        // Compare the lengths first
        final int minLength = Math.min(length, otherData.length);
        for (int i = 0; i < minLength; i++) {
            final int compare = Byte.compareUnsigned(getByte(i), otherData.getByte(i));
            if (compare != 0) {
                return compare;
            }
        }
        // If all compared elements are equal, the shorter array is considered less
        return Integer.compare(length, otherData.length);
    }

    /**
     * toString that outputs data in buffer in bytes.
     *
     * @return nice debug output of buffer contents
     */
    @Override
    @NonNull
    public String toString() {
        return HexFormat.of().formatHex(buffer, start, start + length);
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
            getBytes(0, bytes);
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    /**
     * Returns a Hex encoded string of the bytes in this object.
     *
     * @return Hex encoded string of the bytes in this object.
     */
    public String toHex() {
        return HexFormat.of().formatHex(buffer, start, start + length);
    }

    /**
     * Equals, important that it works for all subclasses of Bytes as well. As any 2 Bytes classes
     * with same contents of bytes are equal
     *
     * @param o the other Bytes object to compare to for equality
     * @return true if o instance of Bytes and contents match
     */
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (!(o instanceof Bytes that)) return false;
        return Arrays.equals(
                buffer, start, start + length, that.buffer, that.start, that.start + that.length);
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
        return buffer[Math.toIntExact(start + offset)];
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(
            final long offset,
            @NonNull final byte[] dst,
            final int dstOffset,
            final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }
        final var len = Math.min(maxLength, length - offset);
        // Maybe this instance is empty and there is nothing to get
        if (len == 0) {
            return 0;
        }
        validateOffset(offset);
        // This is a faster implementation than the default, since it has access to the entire byte
        // array
        // and can do a system array copy instead of a loop.
        System.arraycopy(
                buffer, Math.toIntExact(start + offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        // Maybe this instance is empty and there is nothing to get
        if (len == 0) {
            return 0;
        }
        validateOffset(offset);
        // This is a faster implementation than the default, since it has access to the entire byte
        // array
        // and can do a system array copy instead of a loop.
        dst.put(buffer, Math.toIntExact(start + offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final BufferedData dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        // Maybe this instance is empty and there is nothing to get
        if (len == 0) {
            return 0;
        }
        validateOffset(offset);
        // This is a faster implementation than the default, since it has access to the entire byte
        // array
        // and can do a system array copy instead of a loop.
        dst.writeBytes(buffer, Math.toIntExact(start + offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes getBytes(final long offset, final long len) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative len not allowed");
        }
        if (length() - offset < len) {
            throw new BufferUnderflowException();
        }
        // Maybe this instance is empty and there is nothing to get
        if (len == 0) {
            return Bytes.EMPTY;
        }
        validateOffset(offset);
        // Our buffer is assumed to be immutable, so we can just return a new Bytes object that
        // wraps the same buffer
        return new Bytes(buffer, Math.toIntExact(start + offset), Math.toIntExact(len));
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String asUtf8String(final long offset, final long len) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative len not allowed");
        }
        if (length() - offset < len) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return "";
        }
        validateOffset(offset);
        return new String(buffer, Math.toIntExact(start + offset), length, StandardCharsets.UTF_8);
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(final long offset, @NonNull final byte[] bytes) {
        final int len = bytes.length;
        if (length() - offset < len) {
            return false;
        }
        validateOffset(offset);
        return Arrays.equals(
                buffer,
                Math.toIntExact(start + offset),
                Math.toIntExact(start + offset + len),
                bytes,
                0,
                len);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes slice(final long offset, final long length) {
        return getBytes(offset, length);
    }

    /**
     * * Gets a byte[] of the bytes of this {@link Bytes} object..
     *
     * @return a clone of the bytes of this {@link Bytes} object or null.
     */
    @NonNull
    public byte[] toByteArray() {
        return toByteArray(0, length);
    }

    /**
     * * Gets a byte[] of the bytes of this {@link Bytes} object.
     *
     * @param offset The start offset to get the bytes from.
     * @param len The number of bytes to get.
     * @return a clone of the bytes of this {@link Bytes} object.
     */
    @NonNull
    public byte[] toByteArray(final int offset, final int len) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative len not allowed");
        }
        byte[] ret = new byte[len];
        getBytes(offset, ret);
        return ret;
    }

    private void validateOffset(final long offset) {
        if ((offset < 0) || (offset >= this.length)) {
            throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + this.length);
        }
    }

    /**
     * Validates whether the offset and length supplied to a method are within the bounds of the
     * Bytes object.
     *
     * @param suppliedOffset the offset supplied
     * @param suppliedLength the length supplied
     */
    private void validateOffsetLength(final long suppliedOffset, final long suppliedLength) {
        if (suppliedOffset < 0 || suppliedLength < 0) {
            throw new IllegalArgumentException("Negative length or offset not allowed");
        }
        if (suppliedOffset + suppliedLength > length) {
            throw new IndexOutOfBoundsException(
                    "The offset(%d) and length(%d) provided are out of bounds for this Bytes object, which has a length of %d"
                            .formatted(suppliedOffset, suppliedLength, length));
        }
    }

    /**
     * Calculates the offset from the start for the given supplied offset.
     *
     * @param suppliedOffset the offset supplied
     * @return the calculated offset
     */
    private int calculateOffset(final long suppliedOffset) {
        return Math.toIntExact(start + suppliedOffset);
    }

    /**
     * Sorts {@link Bytes} according to their byte values, lower valued bytes first. Bytes are
     * compared using the passed in Byte Comparator
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

            // In case one of the buffers is longer than the other and the first n bytes (where n in
            // the length of the
            // shorter buffer) are equal, the buffer with the shorter length is first in the sort
            // order.
            long len = o1.length() - o2.length();
            if (len == 0) {
                return 0;
            }
            return ((len > 0) ? 1 : -1);
        };
    }

    /**
     * Appends a {@link Bytes} object to this {@link Bytes} object, producing a new immutable {link
     * Bytes} object.
     *
     * @param bytes The {@link Bytes} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than
     *     Bytes.length()
     */
    @NonNull
    public Bytes append(@NonNull final Bytes bytes) {
        // The length field of Bytes is int. The length() returns always an int,
        // so safe to cast.
        long length = this.length();
        byte[] newBytes = new byte[(int) (length + (int) bytes.length())];
        this.getBytes(0, newBytes, 0, (int) length);
        bytes.getBytes(0, newBytes, (int) length, (int) bytes.length());
        return Bytes.wrap(newBytes);
    }

    /**
     * Appends a {@link RandomAccessData} object to this {@link Bytes} object, producing a new
     * immutable {link Bytes} object.
     *
     * @param data The {@link RandomAccessData} object to append.
     * @return A new {link Bytes} object containing the concatenated bytes and b.
     * @throws BufferUnderflowException if the buffer is empty
     * @throws IndexOutOfBoundsException If the given {@code offset} is negative or not less than
     *     Bytes.length()
     */
    @NonNull
    public Bytes append(@NonNull final RandomAccessData data) {
        // The length field of Bytes is int. The length(0 returns always an int,
        // so safe to cast.
        byte[] newBytes = new byte[(int) (this.length() + (int) data.length())];
        int length1 = (int) this.length();
        this.getBytes(0, newBytes, 0, length1);
        data.getBytes(0, newBytes, length1, (int) data.length());
        return Bytes.wrap(newBytes);
    }

    /** {@inheritDoc} */
    @Override
    public int getVarInt(final long offset, final boolean zigZag) {
        return (int) getVar(Math.toIntExact(offset), zigZag);
    }

    /** {@inheritDoc} */
    @Override
    public long getVarLong(final long offset, final boolean zigZag) {
        return getVar(Math.toIntExact(offset), zigZag);
    }

    private long getVar(int offset, final boolean zigZag) {
        if ((offset < 0) || (offset >= length)) {
            throw new IndexOutOfBoundsException();
        }
        offset += start;

        int rem = (start + length) - offset;
        if (rem > 10) {
            rem = 10;
        }

        long value = 0;

        for (int i = 0; i != rem; i++) {
            final byte b = UnsafeUtils.getArrayByteNoChecks(buffer, offset + i);
            value |= (long) (b & 0x7F) << (i * 7);
            if (b >= 0) {
                return zigZag ? (value >>> 1) ^ -(value & 1) : value;
            }
        }
        throw new DataEncodingException("Malformed var int");
    }
}
