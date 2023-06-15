package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.DataEncodingException;
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
import java.util.Objects;
import static java.nio.ByteOrder.BIG_ENDIAN;

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

    /** {@link ByteBuffer} used as backing buffer for this instance */
    private final ByteBuffer buffer;

    private final boolean direct;

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     * @param direct If this is a dirrect buffer allocation.
     */
    private BufferedData(@NonNull final ByteBuffer buffer) {
        this.buffer = buffer;
        this.direct = buffer.isDirect();
        // I switch the buffer to BIG_ENDIAN so that all our normal "get/read" methods can assume they are in
        // BIG_ENDIAN mode, reducing the boilerplate around those methods. This necessarily means the LITTLE_ENDIAN
        // methods will be slower. I'm assuming BIG_ENDIAN is what we want to optimize for.
        this.buffer.order(BIG_ENDIAN);
    }

    // ================================================================================================================
    // Static Builder Methods

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     * @return new instance using {@code buffer} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final ByteBuffer buffer) {
        return new BufferedData(buffer);
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made. The length of the {@link BufferedData} will be
     * the *ENTIRE* length of the byte array.
     *
     * @param array the byte[] to wrap
     * @return new DataBuffer using {@code array} as its data buffer
     */
    @NonNull
    public static BufferedData wrap(@NonNull final byte[] array) {
        return new BufferedData(ByteBuffer.wrap(array));
    }

    /**
     * Wrap an existing allocated byte[]. No copy is made.
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
            if (i < (buffer.limit()-1)) sb.append(',');
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
        buffer.limit((int)lim);
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
     * into a buffer and want to flip it ready to read back from.
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
        buffer.get(Math.toIntExact(offset), dst.array(), dst.position(), Math.toIntExact(len));
        return len;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytes(final long offset, @NonNull final BufferedData dst) {
        final var len = Math.min(dst.remaining(), length() - offset);
        buffer.get(Math.toIntExact(offset), dst.buffer.array(), Math.toIntExact(dst.position()), Math.toIntExact(len));
        return len;
    }

    @NonNull
    @Override
    public Bytes getBytes(long offset, long length) {
        return Bytes.wrap(buffer.array(), buffer.arrayOffset() + Math.toIntExact(offset), Math.toIntExact(length));
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

        final int dtsPos = dst.position();
        dst.put(dtsPos, buffer, Math.toIntExact(position()), len);
        dst.position(dtsPos + len);
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

        final var bytes = Bytes.wrap(buffer.array(),buffer.arrayOffset() + buffer.position(), length);
        buffer.position(buffer.position() + length);
        return bytes;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public ReadableSequentialData view(final int length) {
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

        int tempPos = buffer.position();
        final byte[] buff = buffer.array();
        int len = buffer.limit();
        if (len == tempPos) {
            return (int)readVarIntLongSlow(zigZag);
        }

        int x;
        if ((x = buff[tempPos++]) >= 0) {
            buffer.position(tempPos);
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (len - tempPos < 9) {
            return (int)readVarIntLongSlow(zigZag);
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
                return (int)readVarIntLongSlow(zigZag);
            }
        }
        buffer.position(tempPos);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(boolean zigZag) {
        if (direct) return ReadableSequentialData.super.readVarLong(zigZag);

        int tempPos = (int)position();
        byte[] buff = buffer.array();
        int len = buffer.limit();
        if (len == tempPos) {
            return readVarIntLongSlow(zigZag);
        }

        long x;
        int y;
        if ((y = buff[tempPos++]) >= 0) {
            buffer.position(tempPos);
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (len - tempPos < 9) {
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
        buffer.position(tempPos);
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
        buffer.put((byte)b);
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
                int numBytesRead = src.read(array, buffer.position(), (int) numBytesToRead - totalBytesRead);
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
        buffer.putInt((int)value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(final long value, @NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putInt((int)value);
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
}
