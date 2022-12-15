package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.ByteBuffer;

/**
 * A Buffer backed by a ByteBuffer that implements {@code DataInput} and {@code DataOutput}.
 */
public final class OffHeapDataBuffer extends DataBuffer {
    OffHeapDataBuffer(ByteBuffer buffer) {
        super(buffer);
    }

    OffHeapDataBuffer(int size) {
        super(ByteBuffer.allocateDirect(size));
    }
}
