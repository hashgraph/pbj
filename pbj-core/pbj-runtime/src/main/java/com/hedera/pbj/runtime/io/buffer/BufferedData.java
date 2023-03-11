package com.hedera.pbj.runtime.io.buffer;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.DataEncodingException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * A buffer backed by a {@link ByteBuffer} or byte array that is {@link BufferedSequentialData} (and therefore contains
 * a "position" cursor into the data), {@link ReadableSequentialData} (and therefore can be read from),
 * {@link WritableSequentialData} (and therefore can be written to), and {@link RandomAccessData} (and therefore can be
 * accessed at any position).
 *
 * <p>This class is the most commonly used for buffered read/write data.
 */
public class BufferedData implements BufferedSequentialData, ReadableSequentialData, WritableSequentialData, RandomAccessData {

    /** Single instance of an empty buffer we can use anywhere we need an empty read only buffer */
    @SuppressWarnings("unused")
    public static final BufferedData EMPTY_BUFFER = wrap(ByteBuffer.allocate(0));

    /** {@link ByteBuffer} used as backing buffer for this instance */
    private final ByteBuffer buffer;

    /**
     * Wrap an existing allocated {@link ByteBuffer}. No copy is made.
     *
     * @param buffer the {@link ByteBuffer} to wrap
     */
    BufferedData(@NonNull final ByteBuffer buffer) {
        this.buffer = buffer;
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
    // DataBuffer Methods

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
     * Exposes this {@link BufferedData} as a {@link DataInputStream}. This is a zero-copy operation.
     * The {@link #position()} and {@link #limit()} are **IGNORED**.
     *
     * @return A {@link DataInputStream} that streams over the full set of data in this {@link BufferedData}.
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
    public String toString() {
        // move read points back to beginning
        buffer.position(0);
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
        buffer.limit((int)limit);
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
     * Get the capacity in bytes that can be stored in this buffer
     *
     * @return capacity in bytes
     */
    @Override
    public int capacity() {
        return buffer.capacity();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public long length() {
        return buffer.limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte getByte(final long offset) {
        return buffer.get(Math.toIntExact(offset));
    }

    // FUTURE: Override the other methods from RandomAccessData to make them faster by using the ByteBuffer directly.

    // ================================================================================================================
    // ReadableSequentialData Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() {
        return buffer.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readUnsignedByte() {
        return Byte.toUnsignedInt(buffer.get());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(@NonNull final byte[] dst) {
        buffer.get(dst);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(@NonNull final byte[] dst, final int offset, final int length) {
        buffer.get(dst, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(@NonNull final ByteBuffer dst) {
        final int length = Math.toIntExact(Math.min(remaining(), dst.remaining()));
        final int dtsPos = dst.position();
        dst.put(dtsPos, buffer, Math.toIntExact(position()), length);
        dst.position(dtsPos + length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(@NonNull final BufferedData dst) {
        dst.buffer.put(buffer);
    }

    /**
     * Create a new Bytes over a subsection of this buffer. Data is shared and not copied, so any changes to
     * the contents of this buffer will be reflected in the Bytes. This position is incremented by
     * {@code length}.
     *
     * @param length The length in bytes of this buffer starting at current position to be in sub buffer
     * @return new read only data buffer representing a subsection of this buffers data
     * @throws BufferUnderflowException If length is more than remaining bytes
     */
    @NonNull
    @Override
    public ReadableSequentialData view(final int length) {
        return new BufferedData(buffer.slice(Math.toIntExact(position()), length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt(@NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return buffer.getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readUnsignedInt() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return Integer.toUnsignedLong(buffer.getInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readUnsignedInt(@NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return Integer.toUnsignedLong(buffer.getInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong(@NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getFloat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat(@NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return buffer.getFloat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble() {
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble(@NonNull final ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return buffer.getDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(final boolean zigZag) {
        int tempPos = buffer.position();
        if (!hasRemaining()) throw new DataEncodingException("Tried to read var int from 0 bytes remaining");
        int x;
        if ((x = buffer.get(tempPos++)) >= 0) {
            buffer.position(buffer.position() + 1);
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (buffer.remaining() < 10) {
            return ReadableSequentialData.super.readVarInt(zigZag);
        } else if ((x ^= (buffer.get(tempPos++) << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (buffer.get(tempPos++) << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (buffer.get(tempPos++) << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = buffer.get(tempPos++);
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && buffer.get(tempPos++) < 0
                    && buffer.get(tempPos++) < 0
                    && buffer.get(tempPos++) < 0
                    && buffer.get(tempPos++) < 0
                    && buffer.get(tempPos++) < 0) {
                throw new DataEncodingException("Malformed Varint");
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
        int tempPos = buffer.position();
        if (!buffer.hasRemaining()) throw new DataEncodingException("Tried to rad var int from 0 bytes remaining");
        long x;
        int y;
        if ((y = buffer.get(tempPos++)) >= 0) {
            buffer.position(buffer.position() + 1);
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (buffer.remaining() < 10) {
            return ReadableSequentialData.super.readVarLong(zigZag);
        } else if ((y ^= (buffer.get(tempPos++) << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (buffer.get(tempPos++) << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (buffer.get(tempPos++) << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) buffer.get(tempPos++) << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) buffer.get(tempPos++) << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) buffer.get(tempPos++) << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) buffer.get(tempPos++) << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) buffer.get(tempPos++) << 56);
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
                if (buffer.get(tempPos++) < 0L) {
                    throw new DataEncodingException("Malformed VarLong");
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
        buffer.put(src, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final ByteBuffer src) {
        if ((limit() - position()) < src.remaining()) {
            throw new BufferUnderflowException();
        }
        buffer.put(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(@NonNull final BufferedData src) {
        if ((limit() - position()) < src.remaining()) {
            System.err.println("Trying to write [" + src.remaining() + "] bytes but only [" +
                    (limit() - position()) + "] remaining of [" + capacity() + "]");
            throw new BufferUnderflowException();
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
                throw new BufferUnderflowException();
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
    public int writeBytes(@NonNull final InputStream src, final int len) {
        if (!buffer.hasArray()) {
            return WritableSequentialData.super.writeBytes(src, len);
        }

        // Check for a bad length or a null src
        Objects.requireNonNull(src);
        if (len < 0) {
            throw new IllegalArgumentException("The length must be >= 0");
        }

        // If the length is zero, then we have nothing to read
        if (len == 0) {
            return 0;
        }

        // Since we have an inner array, we can just read from the input stream into that
        // array over and over until either we read all the bytes we need to, or we hit
        // the end of the stream, or we have read all that we can.
        final var array = buffer.array();

        // We are going to read from the input stream up to either "len" or the number of bytes
        // remaining in this buffer, whichever is lesser.
        final long numBytesToRead = Math.min(len, remaining());
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
        buffer.order(ByteOrder.BIG_ENDIAN);
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
        buffer.order(ByteOrder.BIG_ENDIAN);
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
        buffer.order(ByteOrder.BIG_ENDIAN);
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
        buffer.order(ByteOrder.BIG_ENDIAN);
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
        buffer.order(ByteOrder.BIG_ENDIAN);
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
