package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
public sealed class DataBuffer implements DataInput, DataOutput permits OffHeapDataBuffer {

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

    /**
     * Reset position to origin and limit to capacity, allowing this buffer to be read or written again
     */
    public void reset() {
        buffer.clear();
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
    public byte readByte() {
        return buffer.get();
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
    public void readBytes(byte[] dst) {
        buffer.get(dst);
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
    public void writeByte(byte b) {
        buffer.put(b);
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
}
