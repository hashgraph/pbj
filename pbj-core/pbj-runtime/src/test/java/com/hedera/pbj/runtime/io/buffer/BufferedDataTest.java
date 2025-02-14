// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

final class BufferedDataTest extends BufferedDataTestBase {

    @NonNull
    @Override
    protected BufferedData allocate(final int size) {
        return new BufferedData(ByteBuffer.allocate(size));
    }

    @NonNull
    @Override
    protected BufferedData wrap(final byte[] arr) {
        return new BufferedData(ByteBuffer.wrap(arr));
    }

    @NonNull
    @Override
    protected BufferedData wrap(final byte[] arr, final int offset, final int len) {
        return new BufferedData(ByteBuffer.wrap(arr, offset, len));
    }
}
