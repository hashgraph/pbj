package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
public sealed class ReadOnlyDataBuffer implements DataInput permits DataBuffer, ReadOnlyOffHeapDataBuffer {

    /** ByteBuffer used as backing buffer for this DataBuffer */
    protected ByteBuffer buffer;

    /**
     * Wrap an existing allocated ByteBuffer into a DataBuffer. No copy is made.
     *
     * @param buffer the ByteBuffer to wrap
     */
    protected ReadOnlyDataBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Allocate new on Java heap Data buffer
     *
     * @param size size of new buffer in bytes
     */
    protected ReadOnlyDataBuffer(int size) {
        this.buffer = ByteBuffer.allocate(size);
    }

    // ================================================================================================================
    // Static Builder Methods

    /**
     * Wrap an existing allocated ByteBuffer into a ReadOnlyDataBuffer. No copy is made.
     *
     * @param buffer the ByteBuffer to wrap
     * @return new DataBuffer using {@code buffer} as its data buffer
     */
    public static ReadOnlyDataBuffer wrap(ByteBuffer buffer) {
        return buffer.isDirect() ? new ReadOnlyOffHeapDataBuffer(buffer) : new ReadOnlyDataBuffer(buffer);
    }

    // ================================================================================================================
    // DataBuffer Methods

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
     * Create a new sub data buffer over a subsection of this buffer. Data is shared and not copied, so any changes to
     * the contents of this buffer will be reflected in the sub-buffer. The sub buffer is always read only.
     *
     * @param length The length in bytes of this buffer starting at current position to be in sub buffer
     * @return new read only data buffer representing a subsection of this buffers data
     * @throws BufferUnderflowException If length is more than remaining bytes
     */
    public ReadOnlyDataBuffer readDataBuffer(int length) {
        if (length > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        final int start = buffer.position();
        buffer.position(start+length);
        return ReadOnlyDataBuffer.wrap(buffer.slice(start, length));
    }

    // ================================================================================================================
    // DataInput Methods

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
}
