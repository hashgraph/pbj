package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

final class ByteArrayBufferedDataTest extends BufferedTestBase {

    @NonNull
    @Override
    protected BufferedData allocate(final int size) {
        return new ByteArrayBufferedData(ByteBuffer.allocate(size));
    }

    @NonNull
    @Override
    protected  BufferedData wrap(final byte[] arr) {
        return new ByteArrayBufferedData(ByteBuffer.wrap(arr));
    }

    @NonNull
    @Override
    protected BufferedData wrap(final byte[] arr, final int offset, final int len) {
        return new ByteArrayBufferedData(ByteBuffer.wrap(arr, offset, len));
    }
}
