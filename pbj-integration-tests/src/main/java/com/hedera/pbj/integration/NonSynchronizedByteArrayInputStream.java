// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Faster non-synchronized ByteArrayInputStream. This class is not thread safe, and does not require synchronization.
 * It was created to avoid the synchronization overhead of the standard Java ByteArrayInputStream.
 */
public final class NonSynchronizedByteArrayInputStream extends InputStream {
    /** The byte array buffer we read from. */
    private final byte[] buf;
    /** The current reading position in the buffer */
    private int pos;
    /** The mark position in the buffer */
    private int mark = 0;
    /** The number of bytes in the buffer */
    private final int count;

    /**
     * Creates a new byte array input stream, with the given buffer to read from
     *
     * @param buf the buffer to read from
     */
    public NonSynchronizedByteArrayInputStream(byte[] buf) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read() {
        return (pos < count) ? (buf[pos++] & 0xff) : -1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);

        if (pos >= count) {
            return -1;
        }

        int avail = count - pos;
        if (len > avail) {
            len = avail;
        }
        if (len <= 0) {
            return 0;
        }
        System.arraycopy(buf, pos, b, off, len);
        pos += len;
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull byte[] readAllBytes() {
        byte[] result = Arrays.copyOfRange(buf, pos, count);
        pos = count;
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readNBytes(byte[] b, int off, int len) {
        int n = read(b, off, len);
        return n == -1 ? 0 : n;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long transferTo(OutputStream out) throws IOException {
        int len = count - pos;
        out.write(buf, pos, len);
        pos = count;
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) {
        long k = count - pos;
        if (n < k) {
            k = n < 0 ? 0 : n;
        }

        pos += (int) k;
        return k;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() {
        return count - pos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mark(int readAheadLimit) {
        mark = pos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        pos = mark;
    }

    /**
     * Reset the position and mark to the beginning of the buffer.
     */
    public void resetPosition() {
        pos = 0;
        mark = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {}
}
