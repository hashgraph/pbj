package com.hedera.pbj.runtime.io;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 *
 * TODO Make private and expose through Bytes.asDataInput()
 */
public final class BytesBuffer implements DataInput {

    /** Single instance of an empty buffer we can use anywhere we need an empty read only buffer */
    @SuppressWarnings("unused")
    public static final BytesBuffer EMPTY_BUFFER = new BytesBuffer(Bytes.wrap(new byte[0]));

    /** {@link Bytes} used as backing buffer for this {@link BytesBuffer} */
    private Bytes bytes;

    private int limit = 0;
    private int position = 0;

    /**
     * Wrap an existing allocated {@link Bytes} into a {@link BytesBuffer}. No copy is made.
     *
     * @param bytes the {@link Bytes} to wrap
     */
    private BytesBuffer(Bytes bytes) {
        this.bytes = bytes;
        this.limit = bytes.getLength();
    }

    // ================================================================================================================
    // Static Builder Methods

    /**
     * Wrap an existing allocated {@link Bytes} into a {@link BytesBuffer}. No copy is made.
     *
     * @param bytes the {@link Bytes} to wrap
     * @return new {@link BytesBuffer} using {@code bytes} as its data buffer
     */
    public static BytesBuffer wrap(Bytes bytes) {
        return new BytesBuffer(bytes);
    }

//    /**
//     * toString that outputs data in buffer in bytes.
//     *
//     * @return nice debug output of buffer contents
//     */
//    @Override
//    public String toString() {
//        // move read points back to beginning
//        buffer.position(0);
//        // build string
//        StringBuilder sb = new StringBuilder();
//        sb.append("DataBuffer[");
//        for (int i = 0; i < buffer.limit(); i++) {
//            int v = buffer.get(i) & 0xFF;
//            sb.append(v);
//            if (i < (buffer.limit()-1)) sb.append(',');
//        }
//        sb.append(']');
//        return sb.toString();
//    }
//
//    /**
//     * Equals that compares DataBuffer contents
//     *
//     * @param o another object or DataBuffer to compare to
//     * @return if {@code o} is an instance of {@code DataBuffer} and they contain the same bytes
//     */
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        BytesBuffer that = (BytesBuffer) o;
//        if (getCapacity() != that.getCapacity()) return false;
//        if (getLimit() != that.getLimit()) return false;
//        for (int i = 0; i < getLimit(); i++) {
//            if (buffer.get(i) != that.buffer.get(i)) return false;
//        }
//        return true;
//    }
//
//    /**
//     * Get hash based on contents of this buffer
//     *
//     * @return hash code
//     */
//    @Override
//    public int hashCode() {
//        return buffer.hashCode();
//    }

    // ================================================================================================================
    // PositionedData Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long count) {
        count = Math.max(count, getRemaining());
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
        this.limit = (int) Math.min(limit, bytes.getLength());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRemaining() {
        return position < limit;
    }

    // ================================================================================================================
    // DataInput Read Methods

    /**
     * {@inheritDoc}
     */
    @Override
    public byte readByte() {
        // TODO Does this throw the right exception if position is too large? Should we check before reading
        //      to prevent position from incrementing forever?
        return bytes.getByte(position++);
    }
}
