package com.hedera.pbj.integration;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Faster non-synchronized ByteArrayOutputStream. This class is not thread safe, and does not require synchronization.
 * It was created to avoid the synchronization overhead of the standard Java ByteArrayOutputStream.
 */
public final class NonSynchronizedByteArrayOutputStream extends OutputStream {

    /** Lazy created ByteBuffer wrapper for array buffer. */
    private ByteBuffer byteBuffer = null;
    /** The byte array buffer we write to. */
    private byte[] buf;
    /** The number of bytes written. */
    private int count;

    /**
     * Creates a new byte array output stream, with a buffer capacity of 1MB.
     */
    public NonSynchronizedByteArrayOutputStream() {
        this(1024 * 1024);
    }

    /**
     * Creates a new byte array output stream, with a buffer capacity of the specified size, in bytes.
     *
     * @param size the initial size.
     */
    public NonSynchronizedByteArrayOutputStream(int size) {
        if (size < 0) throw new IllegalArgumentException();
        buf = new byte[size];
    }

    /**
     * Get a reused bytebuffer directly over the internal buffer. It will have position reset and limit set to
     * current data size.
     *
     * @return a ByteBuffer wrapping the internal buffer.
     */
    public ByteBuffer getByteBuffer() {
        if (byteBuffer == null || byteBuffer.array() != buf) {
            byteBuffer = ByteBuffer.wrap(buf);
            return byteBuffer.limit(count);
        } else {
            return byteBuffer.clear().limit(count);
        }
    }

    /**
     * Ensure the buffer has at least minCapacity bytes available.
     *
     * @param minCapacity the minimum capacity to ensure.
     */
    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            /* preferred growth */
            buf = Arrays.copyOf(buf, oldCapacity + Math.max(minGrowth, oldCapacity));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    /**
     * Reset the stream size to empty.
     */
    public void reset() {
        count = 0;
    }

    /**
     * Get the byte array buffer, this does a copy of the internal buffer to a new array of the correct size.
     *
     * @return a copy of the internal buffer contents.
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
    }
}
