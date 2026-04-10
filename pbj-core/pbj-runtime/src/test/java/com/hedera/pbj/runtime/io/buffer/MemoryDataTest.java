// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;

/// Test for heap MemoryData
final class MemoryDataTest extends BufferedDataTestBase<MemoryData> {

    @NonNull
    @Override
    protected MemoryData allocate(final int size) {
        return MemoryData.allocate(size);
    }

    @NonNull
    @Override
    protected MemoryData wrap(final byte[] arr) {
        return MemoryData.wrap(arr);
    }

    @NonNull
    @Override
    protected MemoryData wrap(final byte[] arr, final int offset, final int len) {
        return MemoryData.wrap(arr, offset, len);
    }
}
