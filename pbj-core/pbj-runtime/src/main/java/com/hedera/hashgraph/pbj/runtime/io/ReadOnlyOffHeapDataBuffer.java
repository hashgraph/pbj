package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.ByteBuffer;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
public final class ReadOnlyOffHeapDataBuffer extends ReadOnlyDataBuffer {
    ReadOnlyOffHeapDataBuffer(ByteBuffer buffer) {
        super(buffer);
    }

    ReadOnlyOffHeapDataBuffer(int size) {
        super(ByteBuffer.allocateDirect(size));
    }

    // TODO add optimized methods using misc unsafe
}
