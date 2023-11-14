package com.hedera.pbj.runtime.io.buffer;

import java.nio.ByteBuffer;

final class DirectBufferedData extends BufferedData {

    DirectBufferedData(final ByteBuffer buffer) {
        super(buffer);
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Cannot create a DirectBufferedData over a heap byte buffer");
        }
    }
}
