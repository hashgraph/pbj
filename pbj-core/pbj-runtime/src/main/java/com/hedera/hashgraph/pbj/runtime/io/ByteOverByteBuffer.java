package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Implementation of Bytes backed by a ByteBuffer
 */
public final class ByteOverByteBuffer extends Bytes {

    /** ByteBuffer used as backing buffer for this ByteOverByteBuffer */
    private final ByteBuffer buffer;

    /** The start offset in {@code buffer} that we start at */
    private final int start;

    /** The number of bytes in this Bytes */
    private final int length;

    /**
     * Create a new ByteOverByteBuffer over given subsection of a ByteBuffer. This does not copy data it just wraps so
     * any changes to bytebuffer contents will be effected here. Changes to other state in ByteBuffer like position,
     * limit and mark have no effect. This is designed to be efficient at the risk of an unexpected data change.
     *
     * @param buffer The buffer to wrap
     * @param offset The offset within that buffer to start
     * @param length The length of bytes staring at offset to wrap
     */
    public ByteOverByteBuffer(ByteBuffer buffer, int offset, int length) {
        this.buffer = Objects.requireNonNull(buffer);
        this.start = offset;
        this.length = length;
    }

    /**
     * Create a new ByteOverByteBuffer over given byte array. This does not copy data it just wraps so
     * any changes to arrays contents will be effected here.
     *
     * @param data The data t
     */
    public ByteOverByteBuffer(byte[] data) {
        this.buffer = ByteBuffer.wrap(Objects.requireNonNull(data));
        this.start = 0;
        this.length = data.length;
    }

    /**
     * Package protected direct access to buffer
     *
     * @return the internal buffer
     */
    ByteBuffer getBuffer() {
        return buffer;
    }

    /**
     * Package protected direct access to start offset
     *
     * @return the internal start offset
     */
    int getStart() {
        return start;
    }

    // ================================================================================================================
    // Bytes Methods

    /**
     * Package privet helper method for efficient copy of our data into another ByteBuffer with no effect on this
     * buffers state, so thread safe for this buffer. The destination buffers position is updated.
     *
     * @param dstBuffer the buffer to copy into
     */
    void writeTo(ByteBuffer dstBuffer) {
        dstBuffer.put(dstBuffer.position(),buffer,start, length);
        dstBuffer.position(dstBuffer.position() + length);
    }

    /**
     * Get the number of bytes of data stored
     *
     * @return number of bytes of data stored
     */
    @Override
    public int getLength() {
        return length;
    }

    /**
     * Gets the byte at given {@code offset}.
     *
     * @param offset The offset into data to get byte at
     * @return The byte at given {@code offset}
     */
    @Override
    public byte getByte(int offset) {
        return buffer.get(start + offset);
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code dst} array.
     *
     * @param offset    The offset into data to get bytes at
     * @param dst       The array into which bytes are to be written
     * @param dstOffset The offset within the {@code dst} array of the first byte to be written; must be non-negative and
     *                  no larger than {@code dst.length}
     * @param length    The maximum number of bytes to be written to the given {@code dst} array; must be non-negative and
     *                  no larger than {@code dst.length - offset}
     */
    @Override
    public void getBytes(int offset, byte[] dst, int dstOffset, int length) {
        buffer.get(start+offset, dst, dstOffset, length);
    }

    /**
     * Get bytes starting at given {@code offset} into dst array up to the size of {@code }dst} array.
     *
     * @param offset The offset into data to get bytes at
     * @param dst    The destination array
     */
    @Override
    public void getBytes(int offset, byte[] dst) {
        buffer.get(start+offset, dst);
    }
}
