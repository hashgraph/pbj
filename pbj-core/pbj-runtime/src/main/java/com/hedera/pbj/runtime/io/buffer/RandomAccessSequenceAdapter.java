package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A helper class used by {@link Bytes} (and some tests) to provide a {@link ReadableSequentialData} view of a
 * {@link RandomAccessData} instance. Since {@link RandomAccessData} has no position or limit, this class adds those,
 * and otherwise delegates to the underlying {@link RandomAccessData} instance.
 */
final class RandomAccessSequenceAdapter implements ReadableSequentialData {
    /** The delegate {@link RandomAccessData} instance */
    private final RandomAccessData delegate;

    /** The `Bytes readBytes(int length)` will replicate the return value if true. */
    private final boolean copyingBytes;

    /**
     * The capacity of this sequence will be the difference between the <b>initial</b> position and the
     * length of the delegate
     */
    private final long capacity;
    /** The starting index into the delegate */
    private final long start;
    /** The position. Will be a value between 0 and the {@link #capacity} */
    private long position;
    /** The limit. Will be a value between {@link #position} and the {@link #capacity} */
    private long limit;

    /**
     * Create a new instance where the position begins at 0 (the start of the random data buffer). The capacity of
     * this instance will be the length of the delegate.
     */
    RandomAccessSequenceAdapter(@NonNull final RandomAccessData delegate) {
        this.delegate = delegate;
        this.capacity = delegate.length();
        this.start = 0;
        this.limit = this.capacity;
        this.copyingBytes = false;
    }

    RandomAccessSequenceAdapter(@NonNull final RandomAccessData delegate, final boolean copyingBytes) {
        this.delegate = delegate;
        this.capacity = delegate.length();
        this.start = 0;
        this.limit = this.capacity;
        this.copyingBytes = copyingBytes;
    }

    /**
     * Create a new instance where the start begins at the given start, which must be less than the
     * length of the delegate. The capacity of this instance will be difference between the given {@code position}
     * and the length of the delegate.
     */
    RandomAccessSequenceAdapter(@NonNull final RandomAccessData delegate, final long start) {
        this.delegate = delegate;
        this.start = start;
        this.capacity = delegate.length() - start;
        this.limit = this.capacity;
        this.copyingBytes = false;

        if (this.start > delegate.length()) {
            throw new IllegalArgumentException("Start " + start + " is greater than the delegate length " + delegate.length());
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
        this.limit = Math.max(position, Math.min(limit, delegate.length()));
    }

    /** {@inheritDoc} */
    @Override
    public long skip(final long count) {
        if (count > remaining()) {
            throw new BufferUnderflowException();
        }
        if (count <= 0) {
            return 0;
        }

        position += count;
        return count;
    }

    // ================================================================================================================
    // ReadableSequentialData Methods

    /** {@inheritDoc} */
    @Override
    public byte readByte() {
        checkUnderflow(1);
        final var b = delegate.getByte(start + position);
        position += 1;
        return b;
    }

    /** {@inheritDoc} */
    @Override
    public int readUnsignedByte() {
        checkUnderflow(1);
        final var b = delegate.getUnsignedByte(start + position);
        position += 1;
        return b;
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
        if (offset < 0 || offset > dst.length) {
            throw new IndexOutOfBoundsException("Offset cannot be negative or larger than last index");
        }

        final var length = Math.min(maxLength, remaining());
        final var read = delegate.getBytes(start + position, dst, offset, Math.toIntExact(length));
        position += read;
        return read;
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        // False positive: duplicate code, yes, but two totally different data types that cannot reuse same code
        //noinspection DuplicatedCode
        final var dstPos = dst.position();
        final var length = Math.min(dst.remaining(), remaining());
        final var finalLimit = dstPos + Math.min(length, dst.remaining());
        dst.limit(Math.toIntExact(finalLimit));
        delegate.getBytes(start + position, dst);
        position += length;
        return length;
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final BufferedData dst) {
        // False positive: duplicate code, yes, but two totally different data types that cannot reuse same code
        //noinspection DuplicatedCode
        final var dstPos = dst.position();
        final var length = Math.min(dst.remaining(), remaining());
        final var finalLimit = dstPos + Math.min(length, dst.remaining());
        dst.limit(Math.toIntExact(finalLimit));
        delegate.getBytes(start + position, dst);
        position += length;
        return length;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes readBytes(final int length) {
        if (remaining() < length) {
            throw new BufferUnderflowException();
        }

        var bytes = delegate.getBytes(start + position, length);
        if (copyingBytes) {
            bytes = bytes.replicate();
        }
        position += bytes.length();
        return bytes;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ReadableSequentialData view(final int length) {
        checkUnderflow(length);

        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }

        final var view = new RandomAccessSequenceAdapter(delegate.slice(start + position, length));
        position += view.capacity();
        return view;
    }

    /** {@inheritDoc} */
    @Override
    public int readInt() {
        checkUnderflow(4);
        final var result = delegate.getInt(start + position);
        position += 4;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public int readInt(@NonNull final ByteOrder byteOrder) {
        checkUnderflow(4);
        final var result = delegate.getInt(start + position, byteOrder);
        position += 4;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public long readLong() {
        checkUnderflow(8);
        final var result = delegate.getLong(start + position);
        position += 8;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public long readLong(@NonNull final ByteOrder byteOrder) {
        checkUnderflow(8);
        final var result = delegate.getLong(start + position, byteOrder);
        position += 8;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public float readFloat() {
        checkUnderflow(4);
        final var result = delegate.getFloat(start + position);
        position += 4;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public float readFloat(@NonNull final ByteOrder byteOrder) {
        checkUnderflow(4);
        final var result = delegate.getFloat(start + position, byteOrder);
        position += 4;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public double readDouble() {
        checkUnderflow(8);
        final var result = delegate.getDouble(start + position);
        position += 8;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public double readDouble(@NonNull final ByteOrder byteOrder) {
        checkUnderflow(8);
        final var result = delegate.getDouble(start + position, byteOrder);
        position += 8;
        return result;
    }

    /** Utility method for checking if there is enough data to read */
    private void checkUnderflow(int remainingBytes) {
        if (remaining() - remainingBytes < 0) {
            throw new BufferUnderflowException();
        }
    }
}
