package com.hedera.pbj.runtime.io.buffer;

import static java.nio.ByteOrder.BIG_ENDIAN;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * A buffer backed by a {@link ByteBuffer} that is a {@link BufferedSequentialData} (and therefore contains
 * a "position" cursor into the data), a {@link ReadableSequentialData} (and therefore can be read from),
 * a {@link WritableSequentialData} (and therefore can be written to), and a {@link RandomAccessData} (and therefore can
 * be accessed at any position).
 *
 * <p>This class is the most commonly used for buffered read/write data.
 */
public sealed class BufferedData
        implements BufferedSequentialData, ReadableSequentialData, WritableSequentialData, RandomAccessData
        permits ByteArrayBufferedData, DirectBufferedData {

    /** Single instance of an empty buffer we can use anywhere we need an empty read only buffer */
    @SuppressWarnings("unused")
    public static final BufferedData EMPTY_BUFFER = wrap(ByteBuffer.allocate(0));

    /**
     * {@link ByteBuffer} used as backing buffer for this instance.
     *
     * <p>The buffer may be direct, or may be on the heap. It may also be a "view" of another buffer. The ByteBuffer has
     * an inner array, which can be accessed directly. If it is, you MUST BE VERY CAREFUL to take the array offset into
     * account, otherwise you will read out of bounds of the view.
     */
    protected final ByteBuffer buffer;

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     */
    protected BufferedData(@NonNull final ByteBuffer buffer) {
        this.buffer = buffer;
        // We switch the buffer to BIG_ENDIAN so that all our normal "get/read" methods can assume they are in
        // BIG_ENDIAN mode, reducing the boilerplate around those methods. This necessarily means the LITTLE_ENDIAN
        // methods will be slower. We're assuming BIG_ENDIAN is what we want to optimize for.
        this.buffer.order(BIG_ENDIAN);
    }

    // ================================================================================================================
    // Static Builder Methods

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made. DO NOT modify this buffer after having wrapped
     * it.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     * @return new instance using {@code buffer} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final ByteBuffer buffer) {
        if (buffer.hasArray()) {
            return new ByteArrayBufferedData(buffer);
        } else if (buffer.isDirect()) {
            return new DirectBufferedData(buffer);
        } else {
            // It must be read-only heap byte buffer
            return new BufferedData(buffer);
        }
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made. DO NOT modify this array after having wrapped it.
     *
     * <p>The current position of the created {@link BufferedData} will be 0, the length and capacity will
     * be the length of the wrapped byte array.
     *
     * @param array the byte[] to wrap
     * @return new BufferedData using {@code array} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final byte[] array) {
        return new ByteArrayBufferedData(ByteBuffer.wrap(array));
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made. DO NOT modify this array after having wrapped it.
     *
     * <p>The current position of the created {@link BufferedData} will be {@code offset}, the length will be
     * set to {@code offset} + {@code len}, and capacity will be the length of the wrapped byte array.
     *
     * @param array the byte[] to wrap
     * @param offset the offset into the byte array which will form the origin of this {@link BufferedData}.
     * @param len the length of the {@link BufferedData} in bytes.
     * @return new BufferedData using {@code array} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final byte[] array, final int offset, final int len) {
        return new ByteArrayBufferedData(ByteBuffer.wrap(array, offset, len));
    }

    /**
     * Allocate a new buffered data object with new memory, on the Java heap.
     *
     * @param size size of new buffer in bytes
     * @return a new allocated BufferedData
     */
    @NonNull
    public static BufferedData allocate(final int size) {
        return new ByteArrayBufferedData(ByteBuffer.allocate(size));
    }

    /**
     * Allocate a new buffered data object with new memory, off the Java heap. Off heap has higher cost of allocation
     * and garbage collection but is much faster to read and write to. It should be used for long-lived buffers where
     * performance is critical. On heap is slower for read and writes but cheaper to allocate and garbage collect.
     * Off-heap comes from different memory allocation that needs to be manually managed so make sure we have space
     * for it before using.
     *
     * @param size size of new buffer in bytes
     * @return a new allocated BufferedData
     */
    @NonNull
    public static BufferedData allocateOffHeap(final int size) {
        return new DirectBufferedData(ByteBuffer.allocateDirect(size));
    }

    // ================================================================================================================
    // BufferedData Methods

    /**
     * Exposes this {@link BufferedData} as an {@link InputStream}. This is a zero-copy operation.
     * The {@link #position()} and {@link #limit()} are **IGNORED**.
     *
     * @return An {@link InputStream} that streams over the full set of data in this {@link BufferedData}.
     */
    @NonNull
    public InputStream toInputStream() {
        return new InputStream() {
            private long pos = 0;
            private final long length = capacity();

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

            @Override
            public int read(@NonNull final byte[] b, final int off, final int len) throws IOException {
                final var remaining = length - pos;
                if (remaining <= 0) {
                    return -1;
                }

                try {
                    // We know for certain int is big enough because the min of an int and long will be an int
                    final int toRead = (int) Math.min(len, remaining);
                    getBytes(pos, b, off, toRead);
                    pos += toRead;
                    return toRead;
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
    public String toString() {
        // build string
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append("[");
        for (int i = 0; i < buffer.limit(); i++) {
            int v = buffer.get(i) & 0xFF;
            sb.append(v);
            if (i < (buffer.limit() - 1)) sb.append(',');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Equals that compares DataBuffer contents
     *
     * @param o another object or DataBuffer to compare to
     * @return if {@code o} is an instance of {@code DataBuffer} and they contain the same bytes
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BufferedData that)) {
            return false;
        }
        if (this.capacity() != that.capacity()) {
            return false;
        }
        if (this.limit() != that.limit()) {
            return false;
        }
        for (int i = 0; i < this.limit(); i++) {
            if (buffer.get(i) != that.buffer.get(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get hash based on contents of this buffer
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return buffer.hashCode();
    }

    // ================================================================================================================
    // SequentialData Methods

    /**
     * Get the capacity in bytes that can be stored in this buffer
     *
     * @return capacity in bytes
     */
    @Override
    public long capacity() {
        return buffer.capacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long position() {
        return buffer.position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long limit() {
        return buffer.limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void limit(final long limit) {
        final var lim = Math.min(capacity(), Math.max(limit, position()));
        buffer.limit((int) lim);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long remaining() {
        return buffer.remaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long count) {
        if (count > Integer.MAX_VALUE || (int) count > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        if (count <= 0) {
            return 0;
        }

        buffer.position(buffer.position() + (int) count);
        return count;
    }

    // ================================================================================================================
    // BufferedSequentialData Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void position(final long position) {
        buffer.position(Math.toIntExact(position));
    }

    /**
     * Set the limit to current position and position to origin. This is useful when you have just finished writing
     * into a buffer and want to flip it ready to read back from, or vice versa.
     */
    @Override
    public void flip() {
        buffer.flip();
    }

    /**
     * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
     */
    @Override
    public void reset() {
        buffer.clear();
    }

    /**
     * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
     */
    @Override
    public void resetPosition() {
        buffer.position(0);
    }

    // ================================================================================================================
    // RandomAccessData Methods

    /** {@inheritDoc} */
    @Override
    public long length() {
        return buffer.limit();
    }

    /** {@inheritDoc} */
    @Override
    public byte getByte(final long offset) {
        checkUnderflow(offset, 1);
        return buffer.get(Math.toIntExact(offset));
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        // FUTURE: why offset + length is checked for this object, but not for dst?
        final var len = Math.min(maxLength, length() - offset);
        buffer.get(Math.toIntExact(offset), dst, dstOffset, Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        dst.put(dst.position(), buffer, Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final BufferedData dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        dst.buffer.put(dst.buffer.position(), buffer, Math.toIntExact(offset), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes getBytes(final long offset, final long length) {
        final var len = Math.toIntExact(length);
        if (len < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        if (length() - offset < length) {
            throw new BufferUnderflowException();
        }
        // It is vital that we always copy here, we can never assume ownership of the underlying buffer
        final var copy = new byte[len];
        buffer.get(Math.toIntExact(offset), copy, 0, len);
        return Bytes.wrap(copy);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public BufferedData slice(final long offset, final long length) {
        return BufferedData.wrap(buffer.slice(Math.toIntExact(offset), Math.toIntExact(length)));
    }

    /** {@inheritDoc} */
    @Override
    public int getInt(final long offset) {
        checkUnderflow(offset, 4);
        return buffer.getInt(Math.toIntExact(offset));
    }

    /** {@inheritDoc} */
    @Override
    public int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
        checkUnderflow(offset, 4);
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getInt(Math.toIntExact(offset));
        } finally {
            buffer.order(order);
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getLong(final long offset) {
        checkUnderflow(offset, 8);
        return buffer.getLong(Math.toIntExact(offset));
    }

    /** {@inheritDoc} */
    @Override
    public long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
        checkUnderflow(offset, 8);
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getLong(Math.toIntExact(offset));
        } finally {
            buffer.order(order);
        }
    }

    /** {@inheritDoc} */
    @Override
    public float getFloat(final long offset) {
        checkUnderflow(offset, 4);
        return buffer.getFloat(Math.toIntExact(offset));
    }

    /** {@inheritDoc} */
    @Override
    public float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
        checkUnderflow(offset, 4);
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getFloat(Math.toIntExact(offset));
        } finally {
            buffer.order(order);
        }
    }

    /** {@inheritDoc} */
    @Override
    public double getDouble(final long offset) {
        checkUnderflow(offset, 8);
        return buffer.getDouble(Math.toIntExact(offset));
    }

    /** {@inheritDoc} */
    @Override
    public double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
        checkUnderflow(offset, 8);
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getDouble(Math.toIntExact(offset));
        } finally {
            buffer.order(order);
        }
    }

    /** Utility method for checking if there is enough data to read */
    private void checkUnderflow(long offset, int remainingBytes) {
        if ((length() - offset) - remainingBytes < 0) {
            throw new BufferUnderflowException();
        }
    }

    // ================================================================================================================
    // ReadableSequentialData Methods

    /** {@inheritDoc} */
    @Override
    public byte readByte() {
        return buffer.get();
    }

    /** {@inheritDoc} */
    @Override
    public int readUnsignedByte() {
        return Byte.toUnsignedInt(buffer.get());
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
        if (maxLength < 0) {
            throw new IllegalArgumentException("Negative maxLength not allowed");
        }

        final var len = Math.toIntExact(Math.min(maxLength, remaining()));
        if (len == 0) return 0;

        buffer.get(dst, offset, len);
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final ByteBuffer dst) {
        final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
        if (len == 0) return 0;

        final int dstPos = dst.position();
        dst.put(dstPos, buffer, Math.toIntExact(position()), len);
        dst.position(dstPos + len);
        position(position() + len);
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long readBytes(@NonNull final BufferedData dst) {
        final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
        if (len == 0) return 0;

        final var lim = buffer.limit();
        buffer.limit(buffer.position() + len);

        try {
            dst.buffer.put(buffer);
            return len;
        } finally {
            buffer.limit(lim);
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bytes readBytes(final int length) {
        if (length < 0) throw new IllegalArgumentException("Length cannot be negative");
        if (length == 0) return Bytes.EMPTY;
        if (remaining() < length) throw new BufferUnderflowException();

        final var bytes = getBytes(position(), length);
        buffer.position(buffer.position() + length);
        return bytes;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public BufferedData view(final int length) {
        if (length < 0) {
            // FUTURE: change to AIOOBE
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (length > remaining()) {
            throw new BufferUnderflowException();
        }

        final var pos = Math.toIntExact(position());
        final var buf = BufferedData.wrap(buffer.slice(pos, length));
        position((long) pos + length);
        return buf;
    }

    /** {@inheritDoc} */
    @Override
    public int readInt() {
        return buffer.getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt(@NonNull final ByteOrder byteOrder) {
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getInt();
        } finally {
            buffer.order(order);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readUnsignedInt() {
        return Integer.toUnsignedLong(buffer.getInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong() {
        return buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong(@NonNull final ByteOrder byteOrder) {
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getLong();
        } finally {
            buffer.order(order);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat() {
        return buffer.getFloat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat(@NonNull final ByteOrder byteOrder) {
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getFloat();
        } finally {
            buffer.order(order);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble() {
        return buffer.getDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble(@NonNull final ByteOrder byteOrder) {
        final var order = buffer.order();
        try {
            buffer.order(byteOrder);
            return buffer.getDouble();
        } finally {
            buffer.order(order);
        }
    }

    // ================================================================================================================
    // DataOutput Write Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(final byte b) {
        buffer.put(b);
    }

    /**
     * {@inheritDoc}
     */
    public void writeUnsignedByte(final int b) {
        buffer.put((byte) b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src) {
        buffer.put(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final byte[] src, final int offset, final int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0");
        }
        buffer.put(src, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final ByteBuffer src) {
        if ((limit() - position()) < src.remaining()) {
            throw new BufferOverflowException();
        }
        buffer.put(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final BufferedData src) {
        if ((limit() - position()) < src.remaining()) {
            throw new BufferOverflowException();
        }
        buffer.put(src.buffer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final RandomAccessData src) {
        if (src instanceof Bytes buf) {
            if ((limit() - position()) < src.length()) {
                throw new BufferOverflowException();
            }
            buf.writeTo(buffer);
        } else {
            WritableSequentialData.super.writeBytes(src);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(final int value) {
        buffer.order(BIG_ENDIAN);
        buffer.putInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(final int value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(final long value) {
        buffer.order(BIG_ENDIAN);
        buffer.putInt((int) value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(final long value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putInt((int) value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(final long value) {
        buffer.order(BIG_ENDIAN);
        buffer.putLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(final long value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(final float value) {
        buffer.order(BIG_ENDIAN);
        buffer.putFloat(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(final float value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putFloat(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(final double value) {
        buffer.order(BIG_ENDIAN);
        buffer.putDouble(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(final double value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putDouble(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeVarLong(long value, final boolean zigZag) {
        if (zigZag) {
            value = (value << 1) ^ (value >> 63);
        }
        while (true) {
            if ((value & ~0x7FL) == 0) {
                buffer.put((byte) value);
                break;
            } else {
                buffer.put((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    /**
     * @InheritDoc
     */
    @Override
    public void writeTo(@NonNull OutputStream outStream) {
        try {
            final WritableByteChannel channel = Channels.newChannel(outStream);
            channel.write(buffer.duplicate().position(0).limit(buffer.limit()));
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    /**
     * @InheritDoc
     */
    @Override
    public void writeTo(@NonNull OutputStream outStream, int offset, int length) {
        try {
            final WritableByteChannel channel = Channels.newChannel(outStream);
            channel.write(buffer.duplicate().position(offset).limit(offset + length));
        } catch (IOException e) {
            throw new DataAccessException(e);
        }
    }

    // Helper methods

    protected void validateLen(final long len) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative length not allowed");
        }
    }

    protected void validateCanRead(final long offset, final long len) {
        if (offset < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (offset > length() - len) {
            // FUTURE: change to AIOOBE, too
            throw new BufferUnderflowException();
        }
    }

    protected void validateCanWrite(final long len) {
        if (remaining() < len) {
            throw new BufferOverflowException();
        }
    }
}
