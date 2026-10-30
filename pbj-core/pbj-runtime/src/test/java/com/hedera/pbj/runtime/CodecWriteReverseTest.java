// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.buffer.ReverseBuffer;
import org.junit.jupiter.api.Test;

/**
 * The default {@link Codec#writeReverse} reserves the measured size inside a buffer that already holds output, and
 * writes forward into it. A writer that disagrees with {@link Codec#measureRecord} must fail, not return output with
 * earlier bytes overwritten or stale bytes left in it.
 */
final class CodecWriteReverseTest {

    /** Writes the given bytes, and measures them as {@code measured} bytes. */
    private static Codec<byte[]> codec(final int measured) {
        return new CodecWrapper<>((data, out) -> out.writeBytes(data), data -> measured);
    }

    @Test
    void defaultArrayWriterReturnsTheBytesWrittenAtAnyOffset() {
        final byte[] output = new byte[10];
        assertEquals(3, codec(3).write(new byte[] {1, 2, 3}, output, 0));
        assertEquals(3, codec(3).write(new byte[] {1, 2, 3}, output, 5));
        assertEquals(Bytes.wrap(new byte[] {1, 2, 3, 0, 0, 1, 2, 3, 0, 0}), Bytes.wrap(output));
    }

    @Test
    void writesWhatWasMeasured() {
        final ReverseBuffer buffer = new ReverseBuffer(64);
        codec(3).writeReverse(new byte[] {4, 5, 6}, buffer);
        codec(3).writeReverse(new byte[] {1, 2, 3}, buffer);
        assertEquals(Bytes.wrap(new byte[] {1, 2, 3, 4, 5, 6}), buffer.bytes());
    }

    @Test
    void writingMoreThanWasMeasuredFails() {
        final ReverseBuffer buffer = new ReverseBuffer(64);
        codec(3).writeReverse(new byte[] {4, 5, 6}, buffer);
        // two bytes measured, three written: the third would land on the 4 already in the buffer
        assertThrows(IllegalStateException.class, () -> codec(2).writeReverse(new byte[] {1, 2, 3}, buffer));
    }

    @Test
    void writingLessThanWasMeasuredFails() {
        final ReverseBuffer buffer = new ReverseBuffer(64);
        // four bytes measured, three written: one reserved byte would hold whatever was there before
        assertThrows(IllegalStateException.class, () -> codec(4).writeReverse(new byte[] {1, 2, 3}, buffer));
    }
}
