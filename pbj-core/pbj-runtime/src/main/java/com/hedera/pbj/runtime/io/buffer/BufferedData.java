package com.hedera.pbj.runtime.io.buffer;

import static java.nio.ByteOrder.BIG_ENDIAN;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * A buffer backed by a {@link ByteBuffer} that is a {@link BufferedSequentialData} (and therefore contains
 * a "position" cursor into the data), a {@link ReadableSequentialData} (and therefore can be read from),
 * a {@link WritableSequentialData} (and therefore can be written to), and a {@link RandomAccessData} (and therefore can
 * be accessed at any position).
 *
 * <p>This class is the most commonly used for buffered read/write data.
 */
public class BufferedData implements BufferedSequentialData, ReadableSequentialData, WritableSequentialData, RandomAccessData {

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
    private final ByteBuffer buffer;

    /** Locally cached value of {@link ByteBuffer#isDirect()}. This value is queried by the varInt methods. */
    private final boolean direct;

    /** Non parametric constructor used by the subclasses. */
    protected BufferedData() {
        buffer = null;
        direct = false;
    }

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     */
    protected BufferedData(@NonNull final ByteBuffer buffer) {
        this.buffer = buffer;
        this.direct = buffer.isDirect();
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
        if (buffer.isDirect() && ReadWriteDirectUnsafeByteBuffer.isSupported()) {
            return new ReadWriteDirectUnsafeByteBuffer(buffer);
        }
        return new BufferedData(buffer);
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made. The length of the {@link BufferedData} will be
     * the *ENTIRE* length of the byte array. DO NOT modify this array after having wrapped it.
     *
     * @param array the byte[] to wrap
     * @return new DataBuffer using {@code array} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final byte[] array) {
        return new BufferedData(ByteBuffer.wrap(array));
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made. DO NOT modify this array after having wrapped it.
     *
     * @param array the byte[] to wrap
     * @param offset the offset into the byte array which will form the origin of this {@link BufferedData}.
     * @param len the length of the {@link BufferedData} in bytes.
     * @return new DataBuffer using {@code array} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final byte[] array, final int offset, final int len) {
        return new BufferedData(ByteBuffer.wrap(array, offset, len));
    }

    /**
     * Wrap an already allocated {@link Bytes}. No copy is made. DO NOT modify this buffer after having wrapped
     * it.
     *
     * @param bytes the {@link Bytes} to wrap
     * @return new instance using {@code buffer} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final Bytes bytes) {
        return new ReadOnlyByteBuffer(bytes);
    }

    /**
     * Allocate a new DataBuffer with new memory, on the Java heap.
     *
     * @param size size of new buffer in bytes
     * @return a new allocated DataBuffer
     */
    @NonNull
    public static BufferedData allocate(final int size) {
        return new BufferedData(ByteBuffer.allocate(size));
    }

    /**
     * Allocate a new DataBuffer with new memory, off the Java heap. Off heap has higher cost of allocation and garbage
     * collection but is much faster to read and write to. It should be used for long-lived buffers where performance is
     * critical. On heap is slower for read and writes but cheaper to allocate and garbage collect. Off-heap comes from
     * different memory allocation that needs to be manually managed so make sure we have space for it before using.
     *
     * @param size size of new buffer in bytes
     * @return a new allocated DataBuffer
     */
    @NonNull
    public static BufferedData allocateOffHeap(final int size) {
        if (ReadWriteDirectUnsafeByteBuffer.isSupported()) {
            return new ReadWriteDirectUnsafeByteBuffer(ByteBuffer.allocateDirect(size));
        }
        return new BufferedData(ByteBuffer.allocateDirect(size));
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
        sb.append("BufferedData[");
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BufferedData that = (BufferedData) o;
        if (this.capacity() != that.capacity()) return false;
        if (this.limit() != that.limit()) return false;
        for (int i = 0; i < this.limit(); i++) {
            if (buffer.get(i) != that.buffer.get(i)) return false;
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
    public long skip(long count) {
        count = Math.min(count, buffer.remaining());
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

    @NonNull
    @Override
    public Bytes getBytes(long offset, long length) {
        final var len = Math.toIntExact(length);
        if (direct) {
            final var copy = new byte[len];
            buffer.get(Math.toIntExact(offset), copy, 0, len);
            return Bytes.wrap(copy);
        } else {
            return Bytes.wrap(buffer.array(), Math.toIntExact(buffer.arrayOffset() + offset), len);
        }
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public BufferedData slice(final long offset, final long length) {
        return new BufferedData(buffer.slice(Math.toIntExact(offset), Math.toIntExact(length)));
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
            throw new IllegalArgumentException("Length cannot be negative");
        }

        if (length > remaining()) {
            throw new BufferUnderflowException();
        }

        final var pos = Math.toIntExact(position());
        final var buf = new BufferedData(buffer.slice(pos, length));
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(final boolean zigZag) {
        if (direct) return ReadableSequentialData.super.readVarInt(zigZag);

        final int arrayOffset = buffer.arrayOffset();
        int tempPos = buffer.position() + arrayOffset;
        final byte[] buff = buffer.array();
        int lastPos = buffer.limit() + arrayOffset;

        if (lastPos == tempPos) {
            throw new BufferUnderflowException();
        }

        int x;
        if ((x = buff[tempPos++]) >= 0) {
            buffer.position(tempPos - arrayOffset);
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (lastPos - tempPos < 9) {
            return (int) readVarIntLongSlow(zigZag);
        } else if ((x ^= (buff[tempPos++] << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (buff[tempPos++] << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (buff[tempPos++] << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = buff[tempPos++];
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && buff[tempPos++] < 0
                    && buff[tempPos++] < 0
                    && buff[tempPos++] < 0
                    && buff[tempPos++] < 0
                    && buff[tempPos++] < 0) {
                return (int) readVarIntLongSlow(zigZag);
            }
        }
        buffer.position(tempPos - arrayOffset);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(boolean zigZag) {
        if (direct) return ReadableSequentialData.super.readVarLong(zigZag);

        final int arrayOffset = buffer.arrayOffset();
        int tempPos = buffer.position() + arrayOffset;
        final byte[] buff = buffer.array();
        int lastPos = buffer.limit() + arrayOffset;

        if (lastPos == tempPos) {
            throw new BufferUnderflowException();
        }

        long x;
        int y;
        if ((y = buff[tempPos++]) >= 0) {
            buffer.position(tempPos - arrayOffset);
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (lastPos - tempPos < 9) {
            return readVarIntLongSlow(zigZag);
        } else if ((y ^= (buff[tempPos++] << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (buff[tempPos++] << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (buff[tempPos++] << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) buff[tempPos++] << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) buff[tempPos++] << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) buff[tempPos++] << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) buff[tempPos++] << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) buff[tempPos++] << 56);
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
                if (buff[tempPos++] < 0L) {
                    return readVarIntLongSlow(zigZag);
                }
            }
        }
        buffer.position(tempPos - arrayOffset);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
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
    public int writeBytes(@NonNull final InputStream src, final int maxLength) {
        if (!buffer.hasArray()) {
            return WritableSequentialData.super.writeBytes(src, maxLength);
        }

        // Check for a bad length or a null src
        Objects.requireNonNull(src);
        if (maxLength < 0) {
            throw new IllegalArgumentException("The length must be >= 0");
        }

        // If the length is zero, then we have nothing to read
        if (maxLength == 0) {
            return 0;
        }

        // Since we have an inner array, we can just read from the input stream into that
        // array over and over until either we read all the bytes we need to, or we hit
        // the end of the stream, or we have read all that we can.
        final var array = buffer.array();

        // We are going to read from the input stream up to either "len" or the number of bytes
        // remaining in this buffer, whichever is lesser.
        final long numBytesToRead = Math.min(maxLength, remaining());
        if (numBytesToRead == 0) {
            return 0;
        }

        try {
            int totalBytesRead = 0;
            while (totalBytesRead < numBytesToRead) {
                int numBytesRead = src.read(array, buffer.position() + buffer.arrayOffset(), (int) numBytesToRead - totalBytesRead);
                if (numBytesRead == -1) {
                    return totalBytesRead;
                }

                buffer.position(buffer.position() + numBytesRead);
                totalBytesRead += numBytesRead;
            }

            return totalBytesRead;
        } catch (IOException e) {
            throw new DataAccessException(e);
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
    public void writeVarInt(final int value, final boolean zigZag) {
        long longValue = value;
        if (zigZag) {
            longValue = (longValue << 1) ^ (longValue >> 63);
        }
        while (true) {
            if ((longValue & ~0x7F) == 0) {
                buffer.put((byte) longValue);
                break;
            } else {
                buffer.put((byte) ((longValue & 0x7F) | 0x80));
                longValue >>>= 7;
            }
        }
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

    /** BufferedData subclass that encapsulates immutable {@link Bytes} object */
    static public final class ReadOnlyByteBuffer extends BufferedData {
        final Bytes bytes;
        private final byte[] buffer;
        private int limit;
        private int pos;
        private int start;

        private ReadOnlyByteBuffer(@NonNull final Bytes bytesIn) {
            bytes = bytesIn;
            buffer = bytes.getBytes();
            limit = Math.toIntExact(bytes.length());
            start = bytes.start();
            pos = start;
        }

        private ReadOnlyByteBuffer(@NonNull final Bytes bytesIn, final int startIn, final int limitIn) {
            bytes = bytesIn;
            buffer = bytes.getBytes();
            limit = limitIn;
            start = startIn;
            pos = start;
        }

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
                private final long length = limit;

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
                    final var remaining = limit - pos;
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
            sb.append("BufferedData[");
            for (int i = start; i < limit; i++) {
                int v = buffer[i] & 0xFF;
                sb.append(v);
                if (i < (limit-1)) sb.append(',');
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReadOnlyByteBuffer that = (ReadOnlyByteBuffer) o;
            if (this.capacity() != that.capacity()) return false;
            if (this.limit() != that.limit()) return false;
            for (int i = start; i < this.limit(); i++) {
                if (buffer[i] != that.buffer[i]) return false;
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
            return limit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long position() {
            return pos;
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
        public void limit(final long limit) {
            throw new UncheckedIOException(new IOException("limit - Read-only buffer."));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasRemaining() {
            return pos < limit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long remaining() {
            return limit - pos;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long skip(long count) {
            count = Math.min(count, remaining());
            if (count <= 0) {
                return 0;
            }

            pos = pos + (int) count;
            return count;
        }

        // ================================================================================================================
        // BufferedSequentialData Methods

        /**
         * {@inheritDoc}
         */
        @Override
        public void position(final long position) {
            if (position > limit)
                throw new BufferOverflowException();

            pos = Math.toIntExact(position);
        }

        /**
         * Set the limit to current position and position to origin. This is useful when you have just finished writing
         * into a buffer and want to flip it ready to read back from.
         */
        @Override
        public void flip() {
            limit = pos;
            pos = start;
        }

        /**
         * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
         */
        @Override
        public void reset() {
            limit = Math.toIntExact(bytes.length());
            pos = start;
        }

        /**
         * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
         */
        @Override
        public void resetPosition() {
            pos = start;
        }

        // ================================================================================================================
        // RandomAccessData Methods

        /** {@inheritDoc} */
        @Override
        public long length() {
            return limit - start;
        }

        /** {@inheritDoc} */
        @Override
        public byte getByte(final long offset) {
            return buffer[Math.toIntExact(start + offset)];
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
            if (maxLength < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            final var len = Math.min(maxLength, length() - (start + offset));
            System.arraycopy(buffer, Math.toIntExact(start + offset), dst, dstOffset, Math.toIntExact(len));
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
            final var len = Math.min(dst.remaining(), length() - (start + offset));
            dst.put(dst.position(), buffer, Math.toIntExact(start + offset), Math.toIntExact(len));
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final BufferedData dst) {
            final var len = Math.min(dst.remaining(), length() - (start + offset));
            dst.buffer.put(dst.buffer.position(), buffer, Math.toIntExact(start + offset), Math.toIntExact(len));
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public int getInt(final long offset) {
            if ((length() - (start + offset)) < Integer.BYTES) {
                throw new BufferUnderflowException();
            }
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            final byte b1 = buffer[Math.toIntExact(start + offset)];
            final byte b2 = buffer[Math.toIntExact(start + offset) + 1];
            final byte b3 = buffer[Math.toIntExact(start + offset) + 2];
            final byte b4 = buffer[Math.toIntExact(start + offset) + 3];
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        }

        /** {@inheritDoc} */
        @Override
        public int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
            if ((length() - (start + offset)) < Integer.BYTES) {
                throw new BufferUnderflowException();
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
                //noinspection DuplicatedCode
                final byte b4 = buffer[Math.toIntExact(start + offset)];
                final byte b3 = buffer[Math.toIntExact(start + offset) + 1];
                final byte b2 = buffer[Math.toIntExact(start + offset) + 2];
                final byte b1 = buffer[Math.toIntExact(start + offset) + 3];
                return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
            } else {
                return getInt(offset);
            }
        }

        /** {@inheritDoc} */
        @Override
        public long getLong(final long offset) {
            if ((length() - (start + offset)) < Long.BYTES) {
                throw new BufferUnderflowException();
            }
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            final byte b1 = buffer[Math.toIntExact(start + offset)];
            final byte b2 = buffer[Math.toIntExact(start + offset) + 1];
            final byte b3 = buffer[Math.toIntExact(start + offset) + 2];
            final byte b4 = buffer[Math.toIntExact(start + offset) + 3];
            final byte b5 = buffer[Math.toIntExact(start + offset) + 4];
            final byte b6 = buffer[Math.toIntExact(start + offset) + 5];
            final byte b7 = buffer[Math.toIntExact(start + offset) + 6];
            final byte b8 = buffer[Math.toIntExact(start + offset) + 7];
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
        @Override
        public long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
            if ((length() - (start + offset)) < Long.BYTES) {
                throw new BufferUnderflowException();
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
                //noinspection DuplicatedCode
                final byte b8 = buffer[Math.toIntExact(start + offset)];
                final byte b7 = buffer[Math.toIntExact(start + offset) + 1];
                final byte b6 = buffer[Math.toIntExact(start + offset) + 2];
                final byte b5 = buffer[Math.toIntExact(start + offset) + 3];
                final byte b4 = buffer[Math.toIntExact(start + offset) + 4];
                final byte b3 = buffer[Math.toIntExact(start + offset) + 5];
                final byte b2 = buffer[Math.toIntExact(start + offset) + 6];
                final byte b1 = buffer[Math.toIntExact(start + offset) + 7];
                return (((long) b1 << 56) +
                        ((long) (b2 & 255) << 48) +
                        ((long) (b3 & 255) << 40) +
                        ((long) (b4 & 255) << 32) +
                        ((long) (b5 & 255) << 24) +
                        ((b6 & 255) << 16) +
                        ((b7 & 255) << 8) +
                        (b8 & 255));
            } else {
                return getLong(offset);
            }
        }

        /** {@inheritDoc} */
        @Override
        public float getFloat(final long offset) {
            return Float.intBitsToFloat(getInt(offset));
        }

        /** {@inheritDoc} */
        @Override
        public float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
            return Float.intBitsToFloat(getInt(offset, byteOrder));
        }

        /** {@inheritDoc} */
        @Override
        public double getDouble(final long offset) {
            return Double.longBitsToDouble(getLong(offset));
        }

        /** {@inheritDoc} */
        @Override
        public double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
            return Double.longBitsToDouble(getLong(offset, byteOrder));
        }

        // ================================================================================================================
        // ReadableSequentialData Methods

        /** {@inheritDoc} */
        @Override
        public byte readByte() {
            return buffer[pos++];
        }

        /** {@inheritDoc} */
        @Override
        public int readUnsignedByte() {
            return Byte.toUnsignedInt(buffer[pos++]);
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
            if (maxLength < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            final var len = Math.toIntExact(Math.min(maxLength, remaining()));
            if (len == 0) return 0;

            System.arraycopy(buffer, Math.toIntExact(pos), dst, offset, Math.toIntExact(maxLength));
            pos += len;
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final ByteBuffer dst) {
            final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
            if (len == 0) return 0;

            final int dtsPos = dst.position();
            dst.put(dtsPos, buffer, Math.toIntExact(pos), len);
            dst.position(dtsPos + len);
            pos += len;
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final BufferedData dst) {
            final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
            if (len == 0) {
                dst.buffer.limit(limit);
                return 0;
            }

            final var lim = limit;
            limit = pos + len;

            try {
                dst.buffer.put(buffer, pos, len);
                dst.buffer.limit(limit);
                return len;
            } finally {
                limit = lim;
            }
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public Bytes readBytes(final int length) {
            if (length < 0) throw new IllegalArgumentException("Length cannot be negative");
            if (length == 0) return Bytes.EMPTY;
            if (remaining() < length) throw new BufferUnderflowException();

            final var bytes = getBytes(pos, length);
            pos += length;
            return bytes;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public ReadOnlyByteBuffer view(final int length) {
            if (length < 0) {
                throw new IllegalArgumentException("Length cannot be negative");
            }

            if (length > remaining()) {
                throw new BufferUnderflowException();
            }

            final var buf = new ReadOnlyByteBuffer(bytes, pos, Math.toIntExact(length));
            pos += length;
            return buf;
        }

        /** {@inheritDoc} */
        @Override
        public int readInt() {
            final int ret = getInt(pos);
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int readInt(@NonNull final ByteOrder byteOrder) {
            final int ret = getInt(pos, byteOrder);
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readUnsignedInt() {
            final long ret = Integer.toUnsignedLong(getInt(pos));
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readLong() {
            final long ret = getLong(pos);
            pos += Long.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readLong(@NonNull final ByteOrder byteOrder) {
            final long ret = getLong(pos, byteOrder);
            pos += Long.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float readFloat() {
            final float ret = getFloat(pos);
            pos += Float.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float readFloat(@NonNull final ByteOrder byteOrder) {
            final float ret = getFloat(pos, byteOrder);
            pos += Float.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double readDouble() {
            final double d = getDouble(pos);
            pos += Double.BYTES;
            return d;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double readDouble(@NonNull final ByteOrder byteOrder) {
            final double d = getDouble(pos, byteOrder);
            pos += Double.BYTES;
            return d;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int readVarInt(final boolean zigZag) {
            int tempPos = pos;
            int lastPos = limit;

            if (lastPos == tempPos) {
                throw new BufferUnderflowException();
            }

            int x;
            if ((x = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return zigZag ? (x >>> 1) ^ -(x & 1) : x;
            } else if (lastPos - tempPos < 9) {
                return (int)readVarIntLongSlow(zigZag);
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
                    return (int)readVarIntLongSlow(zigZag);
                }
            }
            pos = tempPos;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readVarLong(boolean zigZag) {
            int tempPos = pos;
            int lastPos = limit;

            if (lastPos == tempPos) {
                throw new BufferUnderflowException();
            }

            long x;
            int y;
            if ((y = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return zigZag ? (y >>> 1) ^ -(y & 1) : y;
            } else if (lastPos - tempPos < 9) {
                return readVarIntLongSlow(zigZag);
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
                        return readVarIntLongSlow(zigZag);
                    }
                }
            }
            pos = tempPos;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        }

        // ================================================================================================================
        // DataOutput Write Methods

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeByte(final byte b) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        public void writeUnsignedByte(final int b) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final byte[] src) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final byte[] src, final int offset, final int length) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final ByteBuffer src) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final BufferedData src) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final RandomAccessData src) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int writeBytes(@NonNull final InputStream src, final int maxLength) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeInt(final int value) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeInt(final int value, @NonNull final ByteOrder byteOrder) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeUnsignedInt(final long value) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeUnsignedInt(final long value, @NonNull final ByteOrder byteOrder) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeLong(final long value) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeLong(final long value, @NonNull final ByteOrder byteOrder) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeFloat(final float value) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeFloat(final float value, @NonNull final ByteOrder byteOrder) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeDouble(final double value) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeDouble(final double value, @NonNull final ByteOrder byteOrder) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeVarInt(final int value, final boolean zigZag) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeVarLong(long value, final boolean zigZag) {
            throw new UncheckedIOException(new IOException("Bytes backed writes are not allowed"));
        }

        /**
         * {@inheritDoc}
         */
        public int getUnsignedByte(final long offset) {
            return Byte.toUnsignedInt(buffer[pos]);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getBytes(final long offset, @NonNull final byte[] dst) {
            return getBytes(offset, dst, 0, dst.length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public Bytes getBytes(final long offset, final long length) {
            if ((start + offset) < 0 || (start + offset) >= length()) {
                throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + length());
            }

            if (length < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            if (length > length() - (start + offset)) {
                throw new BufferUnderflowException();
            }

            // If we find the result is empty, we can take a shortcut
            if (length == 0) {
                return Bytes.EMPTY;
            }

            return Bytes.wrap(buffer, Math.toIntExact(start + offset), Math.toIntExact(length));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public ReadOnlyByteBuffer slice(final long offset, final long length) {
            return new ReadOnlyByteBuffer(getBytes(offset, length));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getUnsignedInt(final long offset) {
            return (getInt(offset)) & 0xFFFFFFFFL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getUnsignedInt(final long offset, @NonNull final ByteOrder byteOrder) {
            return (getInt(offset, byteOrder)) & 0xFFFFFFFFL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public String asUtf8String() {
            return asUtf8String(0, length());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public String asUtf8String(final long offset, final long len) {
            if (len > length() - (start + offset)) {
                throw new BufferUnderflowException();
            }

            if ((start + offset) < 0 || (start + offset + len) > length()) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return "";
            }

            byte[] offsetBytes = new byte[Math.toIntExact(length() - (start + offset))];
            getBytes(offset, offsetBytes);
            return new String(offsetBytes, StandardCharsets.UTF_8);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matchesPrefix(@NonNull final byte[] prefix) {
            return contains(start, prefix);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean contains(final long offset, @NonNull final byte[] bytes) {
            if ((start + offset) < 0 || (start + offset) >= length()) {
                throw new IndexOutOfBoundsException();
            }

            // If the number of bytes between offset and length is shorter than the bytes we're matching, then there
            // is NO WAY we could have a match, so we need to return false.
            if (length() - (start + offset) < bytes.length) {
                return false;
            }

            // Check each byte one at a time until we find a mismatch or, we get to the end, and all bytes match.
            for (int i = Math.toIntExact(start + offset); i < bytes.length; i++) {
                if (bytes[Math.toIntExact(i - start - offset)] != buffer[i]) {
                    return false;
                }
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matchesPrefix(@NonNull final RandomAccessData prefix) {
            return contains(0, prefix);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean contains(final long offset, @NonNull final RandomAccessData data) {
            // If this data is EMPTY, return true if only the incoming data is EMPTY too.
            if (length() == 0) {
                return data.length() == 0;
            }

            if ((start + offset) < 0 || (start + offset) >= length()) {
                throw new IndexOutOfBoundsException();
            }

            if (length() - (start + offset) < data.length()) {
                return false;
            }

            for (int i = Math.toIntExact(start + offset); i < data.length(); i++) {
                if (data.getByte(i - start - offset) != buffer[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** BufferedData subclass that encapsulates a direct {@link ByteBuffer} object
     * and takes advantage of the unsafe fast operations on the data.
     */
    static public final class ReadWriteDirectUnsafeByteBuffer extends BufferedData {
        /** The direct buffer that is backing this stream. */
        private final ByteBuffer buffer;
        /** The unsafe address of the content of {@link #buffer}. */
        private final long address;
        /** The unsafe address of the current read limit of the buffer. */
        private long limit;
        /** The unsafe address of the current read position of the buffer. */
        private long pos;
        /** The unsafe address of the starting read position. */
        private long start;
        /** The amount of available data in the buffer beyond {@link #limit}. */

        static boolean isSupported() {
            return UnsafeUtils.hasUnsafeByteBufferOperations();
        }

        private ReadWriteDirectUnsafeByteBuffer(@NonNull final ByteBuffer bufferIn) {
            buffer = bufferIn;
            address = UnsafeUtils.addressOffset(buffer);
            limit = address + buffer.limit();
            pos = address + buffer.position();
            start = pos;
        }

        private ReadWriteDirectUnsafeByteBuffer(@NonNull final ByteBuffer bufferIn, final long startIn, final long limitIn) {
            buffer = bufferIn;
            address = UnsafeUtils.addressOffset(buffer);
            limit = limitIn;
            start = startIn;
            pos = start;
        }

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
                private final long length = limit;

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
                    final var remaining = limit - pos;
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
            sb.append("ReadWriteDirectUnsafeByteBuffer[");
            for (long i = start; i < limit; i++) {
                int v = UnsafeUtils.getByte(i) & 0xFF;
                sb.append(v);
                if (i < (limit-1)) sb.append(',');
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
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ReadWriteDirectUnsafeByteBuffer that = (ReadWriteDirectUnsafeByteBuffer) o;
            if (this.capacity() != that.capacity()) return false;
            if (this.limit() != that.limit()) return false;
            for (long i = start; i < this.limit(); i++) {
                if (UnsafeUtils.getByte(pos) != UnsafeUtils.getByte(that.pos)) return false;
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
            return limit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long position() {
            return pos;
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
        public void limit(final long limit) {
            final var lim = Math.min(buffer.capacity(), Math.max(limit, position()) - address);
            buffer.limit((int)lim);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasRemaining() {
            return pos < limit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long remaining() {
            return limit - pos;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long skip(long count) {
            count = Math.min(count, remaining());
            if (count <= 0) {
                return 0;
            }

            pos = pos + (int) count;
            return count;
        }

        // ================================================================================================================
        // BufferedSequentialData Methods

        /**
         * {@inheritDoc}
         */
        @Override
        public void position(final long position) {
            if (position + address > limit)
                throw new BufferOverflowException();

            pos = position + address;
        }

        /**
         * Set the limit to current position and position to origin. This is useful when you have just finished writing
         * into a buffer and want to flip it ready to read back from.
         */
        @Override
        public void flip() {
            limit = pos;
            pos = start;
        }

        /**
         * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
         */
        @Override
        public void reset() {
            limit = buffer.capacity();
            pos = start;
        }

        /**
         * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
         */
        @Override
        public void resetPosition() {
            pos = start;
        }

        // ================================================================================================================
        // RandomAccessData Methods

        /** {@inheritDoc} */
        @Override
        public long length() {
            return limit - start;
        }

        /** {@inheritDoc} */
        @Override
        public byte getByte(final long offset) {
            return UnsafeUtils.getByte(address + offset);
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final byte[] dst, final int dstOffset, final int maxLength) {
            if (maxLength < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            final var len = Math.min(maxLength, length() - ((start - address) + offset));
            UnsafeUtils.copyMemory(start + offset, dst, dstOffset, len);
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final ByteBuffer dst) {
            final var len = Math.min(dst.remaining(), length() - ((start - address) + offset));
            byte[] bytes = new byte[Math.toIntExact(len)];
            UnsafeUtils.copyMemory(start + offset, bytes, 0, len);
            dst.put(dst.position(), bytes);
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long getBytes(final long offset, @NonNull final BufferedData dst) {
            final var len = Math.min(dst.remaining(), length() - ((start - offset) + offset));
            byte[] bytes = new byte[Math.toIntExact(len)];
            UnsafeUtils.copyMemory(start + offset, bytes, 0, len);
            dst.buffer.put(dst.buffer.position(), bytes);
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public int getInt(final long offset) {
            if ((length() - ((start - address) + offset)) < Integer.BYTES) {
                throw new BufferUnderflowException();
            }
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            final byte b1 = UnsafeUtils.getByte(start + offset);
            final byte b2 = UnsafeUtils.getByte(start + offset + 1);
            final byte b3 = UnsafeUtils.getByte(start + offset + 2);
            final byte b4 = UnsafeUtils.getByte(start + offset + 3);
            return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
        }

        /** {@inheritDoc} */
        @Override
        public int getInt(final long offset, @NonNull final ByteOrder byteOrder) {
            if ((length() - ((start - address) + offset)) < Integer.BYTES) {
                throw new BufferUnderflowException();
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
                //noinspection DuplicatedCode
                final byte b4 = UnsafeUtils.getByte(start + offset);
                final byte b3 = UnsafeUtils.getByte(start + offset + 1);
                final byte b2 = UnsafeUtils.getByte(start + offset + 2);
                final byte b1 = UnsafeUtils.getByte(start + offset + 3);
                return ((b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
            } else {
                return getInt(offset);
            }
        }

        /** {@inheritDoc} */
        @Override
        public long getLong(final long offset) {
            if ((length() - ((start - address) + offset)) < Long.BYTES) {
                throw new BufferUnderflowException();
            }
            // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
            //noinspection DuplicatedCode
            final byte b1 = UnsafeUtils.getByte(start + offset);
            final byte b2 = UnsafeUtils.getByte(start + offset + 1);
            final byte b3 = UnsafeUtils.getByte(start + offset + 2);
            final byte b4 = UnsafeUtils.getByte(start + offset + 3);
            final byte b5 = UnsafeUtils.getByte(start + offset + 4);
            final byte b6 = UnsafeUtils.getByte(start + offset + 5);
            final byte b7 = UnsafeUtils.getByte(start + offset + 6);
            final byte b8 = UnsafeUtils.getByte(start + offset + 7);
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
        @Override
        public long getLong(final long offset, @NonNull final ByteOrder byteOrder) {
            if ((length() - ((start - address) + offset)) < Long.BYTES) {
                throw new BufferUnderflowException();
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                // False positive: bytes in "duplicated" fragments are read in opposite order for big vs. little endian
                //noinspection DuplicatedCode
                final byte b8 = UnsafeUtils.getByte(start + offset);
                final byte b7 = UnsafeUtils.getByte(start + offset + 1);
                final byte b6 = UnsafeUtils.getByte(start + offset + 2);
                final byte b5 = UnsafeUtils.getByte(start + offset + 3);
                final byte b4 = UnsafeUtils.getByte(start + offset + 4);
                final byte b3 = UnsafeUtils.getByte(start + offset + 5);
                final byte b2 = UnsafeUtils.getByte(start + offset + 6);
                final byte b1 = UnsafeUtils.getByte(start + offset + 7);
                return (((long) b1 << 56) +
                        ((long) (b2 & 255) << 48) +
                        ((long) (b3 & 255) << 40) +
                        ((long) (b4 & 255) << 32) +
                        ((long) (b5 & 255) << 24) +
                        ((b6 & 255) << 16) +
                        ((b7 & 255) << 8) +
                        (b8 & 255));
            } else {
                return getLong(offset);
            }
        }

        /** {@inheritDoc} */
        @Override
        public float getFloat(final long offset) {
            return Float.intBitsToFloat(getInt(offset));
        }

        /** {@inheritDoc} */
        @Override
        public float getFloat(final long offset, @NonNull final ByteOrder byteOrder) {
            return Float.intBitsToFloat(getInt(offset, byteOrder));
        }

        /** {@inheritDoc} */
        @Override
        public double getDouble(final long offset) {
            return Double.longBitsToDouble(getLong(offset));
        }

        /** {@inheritDoc} */
        @Override
        public double getDouble(final long offset, @NonNull final ByteOrder byteOrder) {
            return Double.longBitsToDouble(getLong(offset, byteOrder));
        }

        // ================================================================================================================
        // ReadableSequentialData Methods

        /** {@inheritDoc} */
        @Override
        public byte readByte() {
            return UnsafeUtils.getByte(pos++);
        }

        /** {@inheritDoc} */
        @Override
        public int readUnsignedByte() {
            return Byte.toUnsignedInt(UnsafeUtils.getByte(pos++));
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final byte[] dst, final int offset, final int maxLength) {
            if (maxLength < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            final var len = Math.toIntExact(Math.min(maxLength, remaining()));
            if (len == 0) return 0;
            UnsafeUtils.copyMemory(pos, dst, offset, len);
            pos += len;
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final ByteBuffer dst) {
            final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
            if (len == 0) return 0;

            getBytes(pos - address, dst);
            final int dtsPos = dst.position();
            dst.position(dtsPos + len);
            pos += len;
            return len;
        }

        /** {@inheritDoc} */
        @Override
        public long readBytes(@NonNull final BufferedData dst) {
            final var len = Math.toIntExact(Math.min(dst.remaining(), remaining()));
            if (len == 0) {
                dst.buffer.limit(Math.toIntExact(limit - address));
                return 0;
            }

            final var lim = limit;
            limit = pos + len;

            try {
                getBytes(pos, dst);
                dst.buffer.limit(Math.toIntExact(limit - address));
                pos += len;
                return len;
            } finally {
                limit = lim;
            }
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public Bytes readBytes(final int length) {
            if (length < 0) throw new IllegalArgumentException("Length cannot be negative");
            if (length == 0) return Bytes.EMPTY;
            if (remaining() < length) throw new BufferUnderflowException();

            final var bytes = getBytes(pos, length);
            pos += length;
            return bytes;
        }

        /** {@inheritDoc} */
        @NonNull
        @Override
        public ReadWriteDirectUnsafeByteBuffer view(final int length) {
            if (length < 0) {
                throw new IllegalArgumentException("Length cannot be negative");
            }

            if (length > remaining()) {
                throw new BufferUnderflowException();
            }

            final var buf = new ReadWriteDirectUnsafeByteBuffer(buffer, pos, length + address);
            pos += length;
            return buf;
        }

        /** {@inheritDoc} */
        @Override
        public int readInt() {
            final int ret = getInt(pos);
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int readInt(@NonNull final ByteOrder byteOrder) {
            final int ret = getInt(pos, byteOrder);
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readUnsignedInt() {
            final long ret = Integer.toUnsignedLong(getInt(pos));
            pos += Integer.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readLong() {
            final long ret = getLong(pos);
            pos += Long.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readLong(@NonNull final ByteOrder byteOrder) {
            final long ret = getLong(pos, byteOrder);
            pos += Long.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float readFloat() {
            final float ret = getFloat(pos);
            pos += Float.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public float readFloat(@NonNull final ByteOrder byteOrder) {
            final float ret = getFloat(pos, byteOrder);
            pos += Float.BYTES;
            return ret;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double readDouble() {
            final double d = getDouble(pos);
            pos += Double.BYTES;
            return d;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public double readDouble(@NonNull final ByteOrder byteOrder) {
            final double d = getDouble(pos, byteOrder);
            pos += Double.BYTES;
            return d;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int readVarInt(final boolean zigZag) {
            long tempPos = pos;
            long lastPos = limit;

            if (lastPos == tempPos) {
                throw new BufferUnderflowException();
            }

            int x;
            if ((x = UnsafeUtils.getByte(tempPos++)) >= 0) {
                pos = tempPos;
                return zigZag ? (x >>> 1) ^ -(x & 1) : x;
            } else if (lastPos - tempPos < 9) {
                return (int)readVarIntLongSlow(zigZag);
            } else if ((x ^= (UnsafeUtils.getByte(tempPos++) << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (UnsafeUtils.getByte(tempPos++) << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (UnsafeUtils.getByte(tempPos++) << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = UnsafeUtils.getByte(tempPos++);
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && UnsafeUtils.getByte(tempPos++) < 0
                        && UnsafeUtils.getByte(tempPos++) < 0
                        && UnsafeUtils.getByte(tempPos++) < 0
                        && UnsafeUtils.getByte(tempPos++) < 0
                        && UnsafeUtils.getByte(tempPos++) < 0) {
                    return (int)readVarIntLongSlow(zigZag);
                }
            }
            pos = tempPos;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long readVarLong(boolean zigZag) {
            long tempPos = pos;
            long lastPos = limit;

            if (lastPos == tempPos) {
                throw new BufferUnderflowException();
            }

            long x;
            int y;
            if ((y = UnsafeUtils.getByte(tempPos++)) >= 0) {
                pos = tempPos;
                return zigZag ? (y >>> 1) ^ -(y & 1) : y;
            } else if (lastPos - tempPos < 9) {
                return readVarIntLongSlow(zigZag);
            } else if ((y ^= (UnsafeUtils.getByte(tempPos++) << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (UnsafeUtils.getByte(tempPos++) << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (UnsafeUtils.getByte(tempPos++) << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) UnsafeUtils.getByte(tempPos++) << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) UnsafeUtils.getByte(tempPos++) << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) UnsafeUtils.getByte(tempPos++) << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) UnsafeUtils.getByte(tempPos++) << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) UnsafeUtils.getByte(tempPos++) << 56);
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
                    if (UnsafeUtils.getByte(tempPos++) < 0L) {
                        return readVarIntLongSlow(zigZag);
                    }
                }
            }
            pos = tempPos;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        }

        // ================================================================================================================
        // DataOutput Write Methods
        /**
         * {@inheritDoc}
         */
        @Override
        public void writeByte(final byte b) {
            UnsafeUtils.putByte(pos++, b);
        }

        /**
         * {@inheritDoc}
         */
        public void writeUnsignedByte(final int b) {
            UnsafeUtils.putByte(pos++, (byte)b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final byte[] src) {
            writeBytes(src, 0, src.length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final byte[] src, final int offset, final int length) {
            if (src == null
                    || offset < 0
                    || length < 0
                    || (src.length - length) < offset
                    || (limit - length) < pos) {
                throw new BufferOverflowException();
            }
            UnsafeUtils.copyMemory(src, offset, pos, length);
            pos += length;
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final ByteBuffer src) {
            if (remaining() < src.remaining()) {
                throw new BufferOverflowException();
            }

            while(src.hasRemaining()) {
                writeByte(src.get());
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final BufferedData src) {
            if (remaining() < src.remaining()) {
                throw new BufferOverflowException();
            }

            while(src.hasRemaining()) {
                writeByte(src.readByte());
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBytes(@NonNull final RandomAccessData src) {
            if (remaining() < src.length()) {
                throw new BufferOverflowException();
            }

            for (int i = 0; i < src.length(); i++) {
                writeByte(src.getByte(i));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int writeBytes(@NonNull final InputStream src, final int maxLength) {
// Check for a bad length or a null src
            Objects.requireNonNull(src);
            if (maxLength < 0) {
                throw new IllegalArgumentException("The length must be >= 0");
            }

            // If the length is zero, then we have nothing to read
            if (maxLength == 0) {
                return 0;
            }

            // We are going to read from the input stream up to either "len" or the number of bytes
            // remaining in this DataOutput, whichever is lesser.
            final long numBytesToRead = Math.min(maxLength, remaining());
            if (numBytesToRead == 0) {
                return 0;
            }

            // In this default implementation, we use a small buffer for reading from the stream.
            try {
                final var buf = new byte[8192];
                int totalBytesRead = 0;
                while (totalBytesRead < numBytesToRead) {
                    final var maxBytesToRead = Math.toIntExact(Math.min(numBytesToRead - totalBytesRead, buf.length));
                    final var numBytesRead = src.read(buf, 0, maxBytesToRead);
                    if (numBytesRead == -1) {
                        return totalBytesRead;
                    }

                    totalBytesRead += numBytesRead;
                    writeBytes(buf, 0, numBytesRead);
                }
                return totalBytesRead;
            } catch (IOException ex) {
                throw new DataAccessException("Failed to read from InputStream", ex);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeInt(final int value) {
            if (remaining() < Integer.BYTES) {
                throw new BufferOverflowException();
            }
            writeByte((byte)(value >>> 24));
            writeByte((byte)(value >>> 16));
            writeByte((byte)(value >>>  8));
            writeByte((byte)(value));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeInt(final int value, @NonNull final ByteOrder byteOrder) {
            if (byteOrder == ByteOrder.BIG_ENDIAN) {
                writeInt(value);
            } else {
                if (remaining() < Integer.BYTES) {
                    throw new BufferOverflowException();
                }
                writeByte((byte) (value));
                writeByte((byte) (value >>> 8));
                writeByte((byte) (value >>> 16));
                writeByte((byte) (value >>> 24));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeUnsignedInt(final long value) {
            if (remaining() < Integer.BYTES) {
                throw new BufferOverflowException();
            }
            writeByte((byte)(value >>> 24));
            writeByte((byte)(value >>> 16));
            writeByte((byte)(value >>>  8));
            writeByte((byte)(value));        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeUnsignedInt(final long value, @NonNull final ByteOrder byteOrder) {
            if (byteOrder == ByteOrder.BIG_ENDIAN) {
                writeUnsignedInt(value);
            } else {
                if (remaining() < Integer.BYTES) {
                    throw new BufferOverflowException();
                }
                writeByte((byte) (value));
                writeByte((byte) (value >>> 8));
                writeByte((byte) (value >>> 16));
                writeByte((byte) (value >>> 24));
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeLong(final long value) {
            if (remaining() < Long.BYTES) {
                throw new BufferOverflowException();
            }
            writeByte((byte)(value >>> 56));
            writeByte((byte)(value >>> 48));
            writeByte((byte)(value >>> 40));
            writeByte((byte)(value >>> 32));
            writeByte((byte)(value >>> 24));
            writeByte((byte)(value >>> 16));
            writeByte((byte)(value >>>  8));
            writeByte((byte)(value));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeLong(final long value, @NonNull final ByteOrder byteOrder) {
            if (byteOrder == ByteOrder.BIG_ENDIAN) {
                writeLong(value);
            } else {
                if (remaining() < Long.BYTES) {
                    throw new BufferOverflowException();
                }
                writeByte((byte) (value));
                writeByte((byte) (value >>> 8));
                writeByte((byte) (value >>> 16));
                writeByte((byte) (value >>> 24));
                writeByte((byte) (value >>> 32));
                writeByte((byte) (value >>> 40));
                writeByte((byte) (value >>> 48));
                writeByte((byte) (value >>> 56));
            }        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeFloat(final float value) {
            writeInt(Float.floatToIntBits(value));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeFloat(final float value, @NonNull final ByteOrder byteOrder) {
            writeInt(Float.floatToIntBits(value), byteOrder);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeDouble(final double value) {
            writeLong(Double.doubleToLongBits(value));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeDouble(final double value, @NonNull final ByteOrder byteOrder) {
            writeLong(Double.doubleToLongBits(value), byteOrder);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeVarInt(final int value, final boolean zigZag) {
            writeVarLong(value, zigZag);
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
                    writeByte((byte) value);
                    return;
                } else {
                    writeByte((byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        public int getUnsignedByte(final long offset) {
            return Byte.toUnsignedInt(UnsafeUtils.getByte(offset + address));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getBytes(final long offset, @NonNull final byte[] dst) {
            return getBytes(offset, dst, 0, dst.length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public Bytes getBytes(final long offset, final long length) {
            if ((start + offset) < 0 || ((start - address) + offset) >= length()) {
                throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + length());
            }

            if (length < 0) {
                throw new IllegalArgumentException("Negative maxLength not allowed");
            }

            if (length > length() - ((start - address) + offset)) {
                throw new BufferUnderflowException();
            }

            // If we find the result is empty, we can take a shortcut
            if (length == 0) {
                return Bytes.EMPTY;
            }

            byte[] bytes = new byte[Math.toIntExact(length)];
            UnsafeUtils.copyMemory(address + offset, bytes, 0, length);
            return Bytes.wrap(bytes, 0, Math.toIntExact(length));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public ReadWriteDirectUnsafeByteBuffer slice(final long offset, final long length) {
            return new ReadWriteDirectUnsafeByteBuffer(buffer, address + offset, address + offset + length);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getUnsignedInt(final long offset) {
            return (getInt(offset)) & 0xFFFFFFFFL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getUnsignedInt(final long offset, @NonNull final ByteOrder byteOrder) {
            return (getInt(offset, byteOrder)) & 0xFFFFFFFFL;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public String asUtf8String() {
            return asUtf8String(0, length());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public String asUtf8String(final long offset, final long len) {
            if (len > length() - (start + offset)) {
                throw new BufferUnderflowException();
            }

            if ((start + offset) < 0 || (start + offset + len) > length()) {
                throw new IndexOutOfBoundsException();
            }

            if (len == 0) {
                return "";
            }

            byte[] offsetBytes = new byte[Math.toIntExact(length() - (start + offset))];
            getBytes(offset, offsetBytes);
            return new String(offsetBytes, StandardCharsets.UTF_8);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matchesPrefix(@NonNull final byte[] prefix) {
            return contains(start, prefix);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean contains(final long offset, @NonNull final byte[] bytes) {
            if ((start + offset) < 0 || (start + offset) >= length()) {
                throw new IndexOutOfBoundsException();
            }

            // If the number of bytes between offset and length is shorter than the bytes we're matching, then there
            // is NO WAY we could have a match, so we need to return false.
            if (length() - (start + offset) < bytes.length) {
                return false;
            }

            // Check each byte one at a time until we find a mismatch or, we get to the end, and all bytes match.
            for (int i = Math.toIntExact(start + offset); i < bytes.length; i++) {
                if (bytes[Math.toIntExact(i - start - offset)] != UnsafeUtils.getByte(i)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matchesPrefix(@NonNull final RandomAccessData prefix) {
            return contains(0, prefix);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean contains(final long offset, @NonNull final RandomAccessData data) {
            // If this data is EMPTY, return true if only the incoming data is EMPTY too.
            if (length() == 0) {
                return data.length() == 0;
            }

            if ((start + offset) < 0 || (start + offset) >= length()) {
                throw new IndexOutOfBoundsException();
            }

            if (length() - (start + offset) < data.length()) {
                return false;
            }

            for (int i = Math.toIntExact(start + offset); i < data.length(); i++) {
                if (data.getByte(i - start - offset) != UnsafeUtils.getByte(i)) {
                    return false;
                }
            }
            return true;
        }
    }
}
