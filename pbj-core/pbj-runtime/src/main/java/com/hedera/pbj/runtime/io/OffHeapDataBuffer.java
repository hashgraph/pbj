package com.hedera.pbj.runtime.io;

import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
@SuppressWarnings("DuplicatedCode")
public final class OffHeapDataBuffer extends DataBuffer {

    /** Offset of the {@code java.nio.Buffer#address} field. */
    private static final long BYTE_BUFFER_ADDRESS_FIELD_OFFSET;

    /**
     * Offset of the {@code java.nio.ByteBuffer#hb} field.
     */
    public static final long BYTE_BUFFER_HB_FIELD_OFFSET;
    /** Offset of first data item in a byte array */
    private static final long BYTE_ARRAY_FIRST_DATA_OFFSET;
    /** Access to sun.misc.Unsafe required for atomic compareAndSwapLong on off-heap memory */
    private static final Unsafe UNSAFE;
    private static final int MAX_VARINT_SIZE = 10;

    private static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_BUFFER_ADDRESS_FIELD_OFFSET = UNSAFE.objectFieldOffset(Buffer.class.getDeclaredField("address"));
            BYTE_BUFFER_HB_FIELD_OFFSET = UNSAFE.objectFieldOffset(ByteBuffer.class.getDeclaredField("hb"));
            BYTE_ARRAY_FIRST_DATA_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    private int position = 0;
    private int limit = 0;
    private final int capacity;

    /** Pointer to beginning of off-heap bytes in buffer */
    private final long startOfBytesPointer;

    OffHeapDataBuffer(ByteBuffer buffer) {
        super(buffer);
        limit = capacity = buffer.capacity();
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("buffer.isDirect() must be true");
        }
        if(ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            throw new IllegalStateException("OffHeapDataBuffer assumes you are running on a little endian machine."+
                    " Which is both Intel and ARM so covers most cases.");
        }
        startOfBytesPointer = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
    }

    OffHeapDataBuffer(int size) {
        this(ByteBuffer.allocateDirect(size));
    }

    // ================================================================================================================
    // DataOutput Methods

    /**
     * Set the limit to current position and position to origin. This is useful when you have just finished writing
     * into a buffer and want to flip it ready to read back from.
     */
    public void flip() {
        limit = position;
        position = 0;
    }

    /**
     * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
     */
    public void reset() {
        position = 0;
        limit = capacity;
    }

    /**
     * Reset position to origin and leave limit alone, allowing this buffer to be read again with existing limit
     */
    public void resetPosition() {
        position = 0;
    }

    /**
     * Get the capacity in bytes that can be stored in this buffer
     *
     * @return capacity in bytes
     */
    public int getCapacity() {
        return capacity;
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
        sb.append("DataBuffer[");
        for (int i = 0; i < limit; i++) {
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
        if (capacity != that.getCapacity()) return false;
        if (limit != that.getLimit()) return false;
        for (int i = 0; i < limit; i++) {
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
        position += count;
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getPosition() {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLimit() {
        return limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLimit(long limit) {
        if (limit < 0 || limit > capacity) throw new IndexOutOfBoundsException();
        this.limit =(int)limit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRemaining() {
        return (limit - position) > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRemaining() {
        return Math.max(0,limit - position);
    }

    // ================================================================================================================
    // DataInput Read Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() {
        if (position < 0 || position > limit) throw new IndexOutOfBoundsException();
        return UNSAFE.getByte(startOfBytesPointer + position++);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readUnsignedByte() {
        if (position < 0 || position > limit) throw new IndexOutOfBoundsException();
        return Byte.toUnsignedInt(UNSAFE.getByte(startOfBytesPointer + position++));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(byte[] dst, int offset, int length) {
        if (position < 0 || position > limit - length || (offset+length) >= dst.length ) throw new IndexOutOfBoundsException();
        buffer.get(dst, offset, length);
        UNSAFE.copyMemory(null, startOfBytesPointer + position,
                dst, BYTE_ARRAY_FIRST_DATA_OFFSET+ offset, length);
        position += length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(ByteBuffer dst) throws IOException {
        final int length = dst.remaining();
        if (position < 0 || position > limit - length) throw new IndexOutOfBoundsException();
        if (dst.isDirect()) {
            final long dstPointer = UNSAFE.getLong(dst, BYTE_BUFFER_ADDRESS_FIELD_OFFSET);
            UNSAFE.copyMemory(startOfBytesPointer + position, dstPointer, length);
        } else {
            byte[] internalByteArray = (byte[])UNSAFE.getObject(dst, BYTE_BUFFER_HB_FIELD_OFFSET);
            UNSAFE.copyMemory(null, startOfBytesPointer + position,
                    internalByteArray, BYTE_ARRAY_FIRST_DATA_OFFSET, length);
        }
        position += length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(ByteBuffer dst, int offset, int length) throws IOException {
        if (position < 0 || position > limit - length || (offset+length) > dst.limit()) throw new IndexOutOfBoundsException();
        if (dst.isDirect()) {
            final long dstPointer = UNSAFE.getLong(dst, BYTE_BUFFER_ADDRESS_FIELD_OFFSET) + offset;
            UNSAFE.copyMemory(startOfBytesPointer + position, dstPointer, length);
        } else {
            byte[] internalByteArray = (byte[])UNSAFE.getObject(dst, BYTE_BUFFER_HB_FIELD_OFFSET);
            UNSAFE.copyMemory(null, startOfBytesPointer + position,
                    internalByteArray, BYTE_ARRAY_FIRST_DATA_OFFSET + offset, length);
        }
        position += length;
        dst.position(offset+length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBytes(byte[] dst) {
        final int remaining = limit - position;
        if (position < 0 || remaining <= 0) throw new IndexOutOfBoundsException();
        final int length = Math.min(remaining, dst.length);
        UNSAFE.copyMemory(null, startOfBytesPointer + position,
                dst, BYTE_ARRAY_FIRST_DATA_OFFSET, length);
        position += dst.length;
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
        if (position < 0 || position > limit - length) throw new IndexOutOfBoundsException();
        // move on position
        final Bytes bytes =  new ByteOverByteBuffer(buffer ,position , length);
        position += length;
        return bytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt() {
        return readInt(BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readInt(ByteOrder byteOrder) {
        if (position < 0 || position > limit - Integer.BYTES) throw new IndexOutOfBoundsException();
        final int value;
        if (byteOrder == NATIVE_BYTE_ORDER) {
            value = UNSAFE.getInt(startOfBytesPointer + position);
        } else {
            value = Integer.reverseBytes(UNSAFE.getInt(startOfBytesPointer + position));
        }
        position += Integer.BYTES;
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readUnsignedInt() {
        return readUnsignedInt(BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readUnsignedInt(ByteOrder byteOrder) {
        if (position < 0 || position > limit - Integer.BYTES) throw new IndexOutOfBoundsException();
        final int value;
        if (byteOrder == NATIVE_BYTE_ORDER) {
            value = UNSAFE.getInt(startOfBytesPointer + position);
        } else {
            value = Integer.reverseBytes(UNSAFE.getInt(startOfBytesPointer + position));
        }
        position += Integer.BYTES;
        return Integer.toUnsignedLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong() {
        return readLong(BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readLong(ByteOrder byteOrder) {
        if (position < 0 || position > limit - Long.BYTES) throw new IndexOutOfBoundsException();
        final long value;
        if (byteOrder == NATIVE_BYTE_ORDER) {
            value = UNSAFE.getLong(startOfBytesPointer + position);
        } else {
            value = Long.reverseBytes(UNSAFE.getLong(startOfBytesPointer + position));
        }
        position += Long.BYTES;
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat() {
        return readFloat(BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float readFloat(ByteOrder byteOrder) {
        if (position < 0 || position > limit - Float.BYTES) throw new IndexOutOfBoundsException();
        final float value;
        if (byteOrder == NATIVE_BYTE_ORDER) {
            value = UNSAFE.getFloat(startOfBytesPointer + position);
        } else {
            value = Float.intBitsToFloat(Integer.reverseBytes(UNSAFE.getInt(startOfBytesPointer + position)));
        }
        position += Float.BYTES;
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble() {
        return readDouble(BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double readDouble(ByteOrder byteOrder) {
        if (position < 0 || position > limit - Double.BYTES) throw new IndexOutOfBoundsException();
        final double value;
        if (byteOrder == NATIVE_BYTE_ORDER) {
            value = UNSAFE.getDouble(startOfBytesPointer + position);
        } else {
            value = Double.longBitsToDouble(Long.reverseBytes(UNSAFE.getLong(startOfBytesPointer + position)));
        }
        position += Double.BYTES;
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readVarInt(boolean zigZag) throws IOException {
        if (!hasRemaining()) throw new IOException("Tried to rad var int from 0 bytes remaining");
        int tempPos = position;
        int x;
        if ((x = UNSAFE.getByte(startOfBytesPointer + tempPos++)) >= 0) {
            position ++;
            return zigZag ? (x >>> 1) ^ -(x & 1) : x;
        } else if ((limit - position) < 10) { // Why is this if needed, it is much faster without?
            return (int)readVarLongSlow(zigZag);
        } else if ((x ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 7)) < 0) {
            x ^= (~0 << 7);
        } else if ((x ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 14)) >= 0) {
            x ^= (~0 << 7) ^ (~0 << 14);
        } else if ((x ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 21)) < 0) {
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
        } else {
            int y = UNSAFE.getByte(startOfBytesPointer + tempPos++);
            x ^= y << 28;
            x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
            if (y < 0
                    && UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0
                    && UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0
                    && UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0
                    && UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0
                    && UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0) {
                throw new IOException("Malformed Varint");
            }
        }
        position = tempPos;
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long readVarLong(boolean zigZag) throws IOException {
        if (!hasRemaining()) throw new IOException("Tried to rad var int from 0 bytes remaining");
        int tempPos = position;
        long x;
        int y;
        if ((y = UNSAFE.getByte(startOfBytesPointer + tempPos++)) >= 0) {
            position ++;
            return zigZag ? (y >>> 1) ^ -(y & 1) : y;
        } else if (buffer.remaining() < 10) { // Why is this if needed, it is much faster without?
            return readVarLongSlow(zigZag);
        } else if ((y ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 7)) < 0) {
            x = y ^ (~0 << 7);
        } else if ((y ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 14)) >= 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14));
        } else if ((y ^= (UNSAFE.getByte(startOfBytesPointer + tempPos++) << 21)) < 0) {
            x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
        } else if ((x = y ^ ((long) UNSAFE.getByte(startOfBytesPointer + tempPos++) << 28)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
        } else if ((x ^= ((long) UNSAFE.getByte(startOfBytesPointer + tempPos++) << 35)) < 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
        } else if ((x ^= ((long) UNSAFE.getByte(startOfBytesPointer + tempPos++) << 42)) >= 0L) {
            x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
        } else if ((x ^= ((long) UNSAFE.getByte(startOfBytesPointer + tempPos++) << 49)) < 0L) {
            x ^=
                    (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
        } else {
            x ^= ((long) UNSAFE.getByte(startOfBytesPointer + tempPos++) << 56);
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
                if (UNSAFE.getByte(startOfBytesPointer + tempPos++) < 0L) {
                    throw new IOException("Malformed Varint");
                }
            }
        }
        position = tempPos;
        return zigZag ? (x >>> 1) ^ -(x & 1) : x;
    }

    /**
     * Read a 64bit protobuf varint at current position. A long var int can be 1 to 10 bytes.
     *
     * @return long read in var int format
     * @param zigZag use protobuf zigZag varint encoding, optimized for negative numbers
     * @throws IOException if an I/O error occurs
     */
    private long readVarLongSlow(boolean zigZag) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return zigZag ? ((result >>> 1) ^ -(result & 1)) : result;
            }
        }
        throw new IOException("Malformed Varint");
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

//        java.nio.charset.CharsetDecoder
    }

    // ================================================================================================================
    // DataOutput Write Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(byte b) {
        if (position < 0 || position > limit) throw new IndexOutOfBoundsException();
        UNSAFE.putByte(startOfBytesPointer + position++, b);
    }

    /**
     * {@inheritDoc}
     */
    public void writeUnsignedByte(int b) {
        if (position < 0 || position > limit) throw new IndexOutOfBoundsException();
        UNSAFE.putByte(startOfBytesPointer + position++, (byte)b);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src, int offset, int length) {
        if (position < 0 || position > limit - length || (offset+length) < src.length ) throw new IndexOutOfBoundsException();
        UNSAFE.copyMemory(src, BYTE_ARRAY_FIRST_DATA_OFFSET + offset,null, startOfBytesPointer + position, length);
        position += length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(byte[] src) {
        if (position < 0 || position > limit - src.length) throw new IndexOutOfBoundsException();
        UNSAFE.copyMemory(src, BYTE_ARRAY_FIRST_DATA_OFFSET,null, startOfBytesPointer + position, src.length);
        position += src.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(DataBuffer src) throws IOException {
        final int length = (int)src.getRemaining();
        if (position < 0 || position > limit - length) throw new IndexOutOfBoundsException();
        final ByteBuffer srcBuffer = src.buffer;
        if (srcBuffer.isDirect()) {
            final long srcPointer = UNSAFE.getLong(srcBuffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET) + src.getPosition();
            UNSAFE.copyMemory(srcPointer, startOfBytesPointer + position, length);
        } else {
            byte[] internalByteArray = (byte[])UNSAFE.getObject(srcBuffer, BYTE_BUFFER_HB_FIELD_OFFSET);
            UNSAFE.copyMemory(internalByteArray, BYTE_ARRAY_FIRST_DATA_OFFSET + src.getPosition(),
                    null, startOfBytesPointer + position, length);
        }
        position += length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(ByteBuffer src) throws IOException {

        final int length = src.remaining();
        if (position < 0 || position > limit - length) throw new IndexOutOfBoundsException();
        if (src.isDirect()) {
            final long srcPointer = UNSAFE.getLong(src, BYTE_BUFFER_ADDRESS_FIELD_OFFSET) + src.position();
            UNSAFE.copyMemory(srcPointer, startOfBytesPointer + position, length);
        } else {
            byte[] internalByteArray = (byte[])UNSAFE.getObject(src, BYTE_BUFFER_HB_FIELD_OFFSET);
            UNSAFE.copyMemory(internalByteArray, BYTE_ARRAY_FIRST_DATA_OFFSET + src.position(),
                    null, startOfBytesPointer + position, length);
        }
        position += length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBytes(Bytes src) throws IOException {
        final int length = src.getLength();
        if (position < 0 || position > limit - length) throw new IndexOutOfBoundsException();
        if (src instanceof final ByteOverByteBuffer byteOverByteBuffer) {
            final ByteBuffer buffer = byteOverByteBuffer.getBuffer();
            if (buffer.isDirect()) {
                final long srcPointer = UNSAFE.getLong(buffer, BYTE_BUFFER_ADDRESS_FIELD_OFFSET) +  byteOverByteBuffer.getStart();
                UNSAFE.copyMemory(srcPointer, startOfBytesPointer + position, length);
            } else {
                byte[] internalByteArray = (byte[])UNSAFE.getObject(buffer, BYTE_BUFFER_HB_FIELD_OFFSET);
                UNSAFE.copyMemory(internalByteArray, BYTE_ARRAY_FIRST_DATA_OFFSET +  byteOverByteBuffer.getStart(),
                        null, startOfBytesPointer + position, length);
            }
            position += length;
        } else {
            super.writeBytes(src);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(int value) {
        writeInt(value, BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(int value, ByteOrder byteOrder) {
        if (position < 0 || position > limit - Integer.BYTES) throw new IndexOutOfBoundsException();
        if (byteOrder == NATIVE_BYTE_ORDER) {
            UNSAFE.putInt(startOfBytesPointer + position, value);
        } else {
            UNSAFE.putInt(startOfBytesPointer + position, Integer.reverseBytes(value));
        }
        position += Integer.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(long value) {
        writeUnsignedInt(value, BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeUnsignedInt(long value, ByteOrder byteOrder) {
        if (position < 0 || position > limit - Integer.BYTES) throw new IndexOutOfBoundsException();
        if (byteOrder == NATIVE_BYTE_ORDER) {
            UNSAFE.putInt(startOfBytesPointer + position, (int)value);
        } else {
            UNSAFE.putInt(startOfBytesPointer + position, Integer.reverseBytes((int)value));
        }
        position += Integer.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(long value) {
        writeLong(value, BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(long value, ByteOrder byteOrder) {
        if (position < 0 || position > limit - Long.BYTES) throw new IndexOutOfBoundsException();
        if (byteOrder == NATIVE_BYTE_ORDER) {
            UNSAFE.putLong(startOfBytesPointer + position, value);
        } else {
            UNSAFE.putLong(startOfBytesPointer + position, Long.reverseBytes(value));
        }
        position += Long.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(float value) {
        writeFloat(value, BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeFloat(float value, ByteOrder byteOrder) {
        if (position < 0 || position > limit - Float.BYTES) throw new IndexOutOfBoundsException();
        if (byteOrder == NATIVE_BYTE_ORDER) {
            UNSAFE.putFloat(startOfBytesPointer + position, value);
        } else {
            UNSAFE.putInt(startOfBytesPointer + position, Integer.reverseBytes(Float.floatToIntBits(value)));
        }
        position += Float.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(double value) {
        writeDouble(value, BIG_ENDIAN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeDouble(double value, ByteOrder byteOrder) {
        if (position < 0 || position > limit - Double.BYTES) throw new IndexOutOfBoundsException();
        if (byteOrder == NATIVE_BYTE_ORDER) {
            UNSAFE.putDouble(startOfBytesPointer + position, value);
        } else {
            UNSAFE.putLong(startOfBytesPointer + position, Long.reverseBytes(Double.doubleToLongBits(value)));
        }
        position += Double.BYTES;
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
        if (position < 0) {
            throw new IndexOutOfBoundsException();
        } else if (position < limit - MAX_VARINT_SIZE) { // check if enough room for any size varint, fast path
            while (true) {
                if ((longValue & ~0x7F) == 0) {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) longValue);
                    break;
                } else {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) ((longValue & 0x7F) | 0x80));
                    longValue >>>= 7;
                }
            }
        } else {
            final int limitMinusOne = limit - 1;
            while (position < limitMinusOne) {
                if ((longValue & ~0x7F) == 0) {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) longValue);
                    return;
                } else {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) ((longValue & 0x7F) | 0x80));
                    longValue >>>= 7;
                }
            }
            if (position > limitMinusOne) throw new IndexOutOfBoundsException();
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
        if (position < 0) {
            throw new IndexOutOfBoundsException();
        } else if (position < limit - MAX_VARINT_SIZE) { // check if enough room for any size varint, fast path
            while (true) {
                if ((value & ~0x7FL) == 0) {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) value);
                    break;
                } else {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
                if (position > limit - 1) throw new IndexOutOfBoundsException();
            }
        } else {
            final int limitMinusOne = limit - 1;
            while (position < limitMinusOne) {
                if ((value & ~0x7FL) == 0) {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) value);
                    return;
                } else {
                    UNSAFE.putByte(startOfBytesPointer + position++, (byte) (((int) value & 0x7F) | 0x80));
                    value >>>= 7;
                }
            }
            if (position > limitMinusOne) throw new IndexOutOfBoundsException();
        }
    }
}
