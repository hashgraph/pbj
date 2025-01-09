/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.pbj.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * Faster non-synchronized ByteArrayInputStream
 */
public final class NonSynchronizedByteArrayInputStream extends InputStream {
    private final byte[] buf;
    private int pos;
    private int mark = 0;
    private final int count;

    public NonSynchronizedByteArrayInputStream(byte[] buf) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }

    public int read() {
        return (pos < count) ? (buf[pos++] & 0xff) : -1;
    }

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

    public byte[] readAllBytes() {
        byte[] result = Arrays.copyOfRange(buf, pos, count);
        pos = count;
        return result;
    }

    public int readNBytes(byte[] b, int off, int len) {
        int n = read(b, off, len);
        return n == -1 ? 0 : n;
    }

    public long transferTo(OutputStream out) throws IOException {
        int len = count - pos;
        out.write(buf, pos, len);
        pos = count;
        return len;
    }

    public long skip(long n) {
        long k = count - pos;
        if (n < k) {
            k = n < 0 ? 0 : n;
        }

        pos += (int) k;
        return k;
    }

    public int available() {
        return count - pos;
    }

    public boolean markSupported() {
        return true;
    }

    public void mark(int readAheadLimit) {
        mark = pos;
    }

    public void reset() {
        pos = mark;
    }

    public void resetPosition() {
        pos = 0;
        mark = 0;
    }

    public void close() {}
}
