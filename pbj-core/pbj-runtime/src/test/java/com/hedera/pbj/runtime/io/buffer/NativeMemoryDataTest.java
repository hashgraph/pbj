// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;

/// Test for native MemoryData
final class NativeMemoryDataTest extends BufferedDataTestBase<MemoryData> {

    @NonNull
    @Override
    protected MemoryData allocate(final int size) {
        return MemoryData.allocateOffHeap(size);
    }

    @NonNull
    @Override
    protected MemoryData wrap(final byte[] arr) {
        final MemoryData buf = MemoryData.allocateOffHeap(arr.length);
        buf.writeBytes(arr);
        buf.reset();
        return buf;
    }

    @NonNull
    @Override
    protected MemoryData wrap(byte[] arr, int offset, int len) {
        final MemoryData buf = MemoryData.allocateOffHeap(len);
        buf.writeBytes(arr, offset, len);
        buf.reset();
        return buf;
    }
}
