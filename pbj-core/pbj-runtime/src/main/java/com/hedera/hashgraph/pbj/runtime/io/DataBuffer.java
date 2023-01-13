package com.hedera.hashgraph.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
public sealed class DataBuffer implements DataInput, DataOutput permits OffHeapDataBuffer {

    /** Single instance of an empty buffer we can use anywhere we need an empty read only buffer */
    @SuppressWarnings("unused")
    public static final DataBuffer EMPTY_BUFFER = wrap(ByteBuffer.allocate(0));

    /** ByteBuffer used as backing buffer for this DataBuffer */
    protected ByteBuffer buffer;

    /**
     * Wrap an existing allocated ByteBuffer into a DataBuffer. No copy is made.
     *
     * @param buffer the ByteBuffer to wrap
     */
    protected DataBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Allocate new on Java heap Data buffer
     *
     * @param size size of new buffer in bytes
     */
    protected DataBuffer(int size) {
        this.buffer = ByteBuffer.allocate(size);
    }

    // ================================================================================================================
    // Static Builder Methods

    /**
     * Wrap an existing allocated ByteBuffer into a DataBuffer. No copy is made.
     *
     * @param buffer the ByteBuffer to wrap
     * @return new DataBuffer using {@code buffer} as its data buffer
     */
    public static DataBuffer wrap(ByteBuffer buffer) {
        return buffer.isDirect() ? new OffHeapDataBuffer(buffer) : new DataBuffer(buffer);
    }

    /**
     * Wrap an existing allocated byte[] into a DataBuffer. No copy is made.
     *
     * @param array the byte[] to wrap
     * @return new DataBuffer using {@code array} as its data buffer
     */
    public static DataBuffer wrap(byte[] array) {
        return new DataBuffer(ByteBuffer.wrap(array));
    }

    /**
     * Allocate a new DataBuffer with new memory, either on or off the Java heap. Off heap has higher cost of
     * allocation and garbage collection but is much faster to read and write to. It should be used for long-lived
     * buffers where performance is critical. On heap is slower for read and writes but cheaper to allocate and garbage
     * collect. Off-heap comes from different memory allocation that needs to be manually managed so make sure we have
     * space for it before using.
     *
     * @param size size of new buffer in bytes
     * @param offHeap if the new buffer should be allocated from off-heap memory or the standard Java heap memory
     * @return a new allocated DataBuffer
     */
    public static DataBuffer allocate(int size, boolean offHeap) {
        return offHeap ? new OffHeapDataBuffer(size) : new DataBuffer(size);
    }

    // ================================================================================================================
    // DataOutput Methods


    static String bufToString(ByteBuffer buf) {
        byte[] b = new byte[Math.min(30,buf.remaining())];
        buf.get(0, b);
        return "ByteBuffer{direct="+buf.isDirect()+",pos="+buf.position()+",remaining="+buf.remaining()+" ,d="+ Arrays.toString(b)+"}";
    }

    /**
     * Set the limit to current position and position to origin. This is useful when you have just finished writing
     * into a buffer and want to flip it ready to read back from.
     */
    public void flip() {
       buffer.flip();
    }

    /**
     * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
     */
    public void reset() {
        buffer.clear();
    }

    /**
     * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
     */
    public void resetPosition() {
        buffer.position(0);
    }

    /**
     * Get the capacity in bytes that can be stored in this buffer
     *
     * @return capacity in bytes
     */
    public int getCapacity() {
        return buffer.capacity();
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
        sb.append("DataBuffer[");
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataBuffer that = (DataBuffer) o;
        if (getCapacity() != that.getCapacity()) return false;
        if (getLimit() != that.getLimit()) return false;
        for (int i = 0; i < getLimit(); i++) {
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
    // DataOutput Position Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long count) {
        count = Math.max(count, buffer.remaining());
        buffer.position(buffer.position() + (int)count);
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPosition() {
        return buffer.position();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLimit() {
        return buffer.limit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLimit(long limit) {
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
    public long getRemaining() {
        return buffer.remaining();
    }

    // ================================================================================================================
    // DataInput Read Methods

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
    public void readBytes(byte[] dst, int offset, int length) {
        buffer.get(dst, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(ByteBuffer dst) throws IOException {
        final int length = dst.remaining();
        final int dtsPos = dst.position();
        dst.put(dtsPos,buffer,0,length);
        dst.position(dtsPos+length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(ByteBuffer dst, int offset, int length) throws IOException {
        final int dtsPos = dst.position();
        dst.put(dtsPos,buffer,offset,length);
        dst.position(dtsPos+length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(byte[] dst) {
        buffer.get(dst);
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
    @Override
    public Bytes readBytes(int length) {
        if (length > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int startPos = buffer.position();
        // move on position
        buffer.position(startPos + length);
        return new ByteOverByteBuffer(buffer ,startPos , length);
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
    public int readInt(ByteOrder byteOrder) {
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
    public long readUnsignedInt(ByteOrder byteOrder) {
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
    public long readLong(ByteOrder byteOrder) {
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
    public float readFloat(ByteOrder byteOrder) {
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
    public double readDouble(ByteOrder byteOrder) {
        buffer.order(byteOrder);
        return buffer.getDouble();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(boolean zigZag) throws IOException {
        int tempPos = buffer.position();
        if (!hasRemaining()) throw new IOException("Tried to rad var int from 0 bytes remaining");
        int x;
        if ((x = buffer.get(tempPos++)) >= 0) {
            buffer.position(buffer.position() + 1);
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if (buffer.remaining() < 10) {
            return DataInput.super.readVarInt(zigZag);
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
                throw new IOException("Malformed Varint");
            }
        }
        buffer.position(tempPos);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(boolean zigZag) throws IOException {
        int tempPos = buffer.position();
        if (!buffer.hasRemaining()) throw new IOException("Tried to rad var int from 0 bytes remaining");
        long x;
        int y;
        if ((y = buffer.get(tempPos++)) >= 0) {
            buffer.position(buffer.position() + 1);
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (buffer.remaining() < 10) {
            return DataInput.super.readVarLong(zigZag);
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
                    throw new IOException("Malformed Varint");
                }
            }
        }
        buffer.position(tempPos);
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String readUtf8String(int lengthInBytes) throws IOException {
//        if (buffer.remaining() < lengthInBytes) throw new IOException("Not enough bytes to remaining [" +
//                buffer.remaining() + "] to read string of [" + lengthInBytes + "] bytes");
//        int oldLimit = buffer.limit();
//        buffer.limit(buffer.position()+lengthInBytes);
//        final String readStr = StandardCharsets.UTF_8.decode(buffer).toString();
//        buffer.limit(oldLimit);
//        return readStr;
        final String readStr = new String(buffer.array(), buffer.position(), lengthInBytes, StandardCharsets.UTF_8);
        buffer.position(buffer.position() + lengthInBytes);
        return readStr;
    }

    // ================================================================================================================
    // DataOutput Write Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(byte b) {
        buffer.put(b);
    }

    /**
     * {@inheritDoc}
     */
    public void writeUnsignedByte(int b) {
        buffer.put((byte)b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src, int offset, int length) {
        buffer.put(src, offset, length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src) {
        buffer.put(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(DataBuffer src) throws IOException {
        if ((getLimit() - getPosition()) < src.getRemaining()) {
            throw new BufferUnderflowException();
        }
        buffer.put(src.buffer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(ByteBuffer src) throws IOException {
        if ((getLimit() - getPosition()) < src.remaining()) {
            throw new BufferUnderflowException();
        }
        buffer.put(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(Bytes src) throws IOException {
        if (src instanceof ByteOverByteBuffer) {
            if ((getLimit() - getPosition()) < src.getLength()) {
                throw new BufferUnderflowException();
            }
            ((ByteOverByteBuffer)src).writeTo(buffer);
        } else {
            DataOutput.super.writeBytes(src);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(int value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(int value, ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putInt(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(long value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt((int)value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(long value, ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putInt((int)value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(long value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(long value, ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(float value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putFloat(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(float value, ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putFloat(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(double value) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putDouble(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(double value, ByteOrder byteOrder) {
        buffer.order(byteOrder);
        buffer.putDouble(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeVarInt(int value, boolean zigZag) {
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
    public void writeVarLong(long value, boolean zigZag) {
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
