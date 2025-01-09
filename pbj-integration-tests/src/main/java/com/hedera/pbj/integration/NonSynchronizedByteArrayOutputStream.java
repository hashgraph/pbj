// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Faster non-synchronized ByteArrayOutputStream
 */
public final class NonSynchronizedByteArrayOutputStream extends OutputStream {

    private ByteBuffer byteBuffer = null;

    private byte[] buf;
    private int count;

    public NonSynchronizedByteArrayOutputStream() {
        this(1024 * 1024);
    }

    public NonSynchronizedByteArrayOutputStream(int size) {
        if (size < 0) throw new IllegalArgumentException();
        buf = new byte[size];
    }

    /**
     * get a reused bytebuffer directly over the internal buffer. It will have position reset and limit set to
     * current data size.
     */
    public ByteBuffer getByteBuffer() {
        if (byteBuffer == null || byteBuffer.array() != buf) {
            byteBuffer = ByteBuffer.wrap(buf);
            return byteBuffer.limit(count);
        } else {
            return byteBuffer.clear().limit(count);
        }
    }

    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            buf = Arrays.copyOf(buf, newLength(oldCapacity,
                    minGrowth, oldCapacity /* preferred growth */));
        }
    }

    public static int newLength(int oldLength, int minGrowth, int prefGrowth) {
        return oldLength + Math.max(minGrowth, prefGrowth);
    }

    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    public void write(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void reset() {
        count = 0;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public void close() {
    }
}
