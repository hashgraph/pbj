// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io.buffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("RandomAccessData.put*() methods must throw exceptions on read-only MemoryData")
    void putDoesNotWork() {
        final String str = "1234567890";
        final MemorySegment readOnlySegment =
                MemorySegment.ofArray(str.getBytes(StandardCharsets.UTF_8)).asReadOnly();
        final MemoryData data = MemoryData.wrap(readOnlySegment);

        assertThrows(IllegalArgumentException.class, () -> data.putByte(0, (byte) 0));
        assertEquals(str, data.asUtf8String());
        assertThrows(IllegalArgumentException.class, () -> data.putBytes(0, new byte[] {4, 5, 6}));
        assertEquals(str, data.asUtf8String());
        assertThrows(IllegalArgumentException.class, () -> data.putBytes(0, new byte[] {4, 5, 6}, 1, 1));
        assertEquals(str, data.asUtf8String());
        assertThrows(IllegalArgumentException.class, () -> data.putLong(0, 111L));
        assertEquals(str, data.asUtf8String());
        assertThrows(IllegalArgumentException.class, () -> data.putVarLong(0, 111L));
        assertEquals(str, data.asUtf8String());
    }
}
