package com.hedera.hashgraph.pbj.runtime.io;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

    // TODO add optimized methods using misc unsafe
}
