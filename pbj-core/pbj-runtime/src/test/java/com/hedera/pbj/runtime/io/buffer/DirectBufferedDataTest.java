package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

final class DirectBufferedDataTest extends BufferedTestBase {

    @NonNull
    @Override
    protected BufferedData allocate(final int size) {
        return new DirectBufferedData(ByteBuffer.allocateDirect(size));
    }

    @NonNull
    @Override
    protected BufferedData wrap(final byte[] arr) {
        final BufferedData buf = new DirectBufferedData(ByteBuffer.allocateDirect(arr.length));
        buf.writeBytes(arr);
        buf.reset();
        return buf;
    }

    @NonNull
    @Override
    protected BufferedData wrap(byte[] arr, int offset, int len) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(arr.length);
        buf.put(arr);
        buf.position(offset);
        buf.limit(offset + len);
        return new DirectBufferedData(buf);
    }
}
