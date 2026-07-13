// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/**
 * Verifies that direct {@link ByteBuffer} writes and writes via a {@link BufferedData} wrapping
 * the same buffer share the underlying position — mirroring the pattern used in
 * {@code PcesFileChannelWriter}, which writes an int header directly to a {@link ByteBuffer} and
 * then writes a protobuf message via the {@link com.hedera.pbj.runtime.io.WritableSequentialData}
 * that wraps it.
 */
class MixedByteBufferWriteTest {

    /**
     * Replicates the PcesFileChannelWriter write sequence:
     * 1. measure serialized size
     * 2. write size as int directly to ByteBuffer
     * 3. write protobuf message via the BufferedData that wraps the same ByteBuffer
     * 4. flip and read back both fields, asserting round-trip correctness.
     */
    @Test
    void writeIntHeaderThenProtobufMessageRoundTrips() throws IOException, ParseException {
        final TimestampTest message = new TimestampTest(5155135L, 44513);
        final int size = TimestampTest.PROTOBUF.measureRecord(message);

        final ByteBuffer buffer = ByteBuffer.allocateDirect(Integer.BYTES + size);
        final BufferedData writableSequentialData = BufferedData.wrap(buffer);

        // Direct write to ByteBuffer — advances buffer.position() by 4
        buffer.putInt(size);

        // Write protobuf message via the wrapping BufferedData — continues from position 4
        TimestampTest.PROTOBUF.write(message, writableSequentialData);

        buffer.flip();

        final int readSize = buffer.getInt();
        assertEquals(size, readSize, "size header mismatch");

        final byte[] messageBytes = new byte[readSize];
        buffer.get(messageBytes);
        final TimestampTest parsed = TimestampTest.PROTOBUF.parse(BufferedData.wrap(messageBytes));

        assertEquals(message, parsed, "round-tripped message mismatch");
    }

    /**
     * Explicitly verifies that position advances made directly on the ByteBuffer are immediately
     * visible through the wrapping BufferedData and vice versa.
     */
    @Test
    void positionIsSharedBetweenByteBufferAndWrappedBufferedData() throws IOException {
        final TimestampTest message = new TimestampTest(999L, 1);
        final int size = TimestampTest.PROTOBUF.measureRecord(message);

        final ByteBuffer buffer = ByteBuffer.allocateDirect(Integer.BYTES + size);
        final BufferedData wsd = BufferedData.wrap(buffer);

        assertEquals(0, buffer.position(), "initial ByteBuffer position");
        assertEquals(0, wsd.position(), "initial BufferedData position");

        // Direct write advances both views
        buffer.putInt(size);
        assertEquals(Integer.BYTES, buffer.position(), "ByteBuffer position after putInt");
        assertEquals(Integer.BYTES, wsd.position(), "BufferedData position after putInt");

        // Write via BufferedData advances both views
        TimestampTest.PROTOBUF.write(message, wsd);
        assertEquals(Integer.BYTES + size, buffer.position(), "ByteBuffer position after protobuf write");
        assertEquals(Integer.BYTES + size, wsd.position(), "BufferedData position after protobuf write");
    }

    /**
     * Verifies the buffer-expansion path: when the serialized size exceeds the current buffer
     * capacity a new ByteBuffer is allocated and re-wrapped, and the data still round-trips.
     * This mirrors the expandBuffer branch in PcesFileChannelWriter.writeEvent().
     */
    @Test
    void bufferExpansionAndRewrapRoundTrips() throws IOException, ParseException {
        final TimestampTest message = new TimestampTest(123456789L, 999999999);
        final int size = TimestampTest.PROTOBUF.measureRecord(message);

        // Start with a buffer that is intentionally too small
        ByteBuffer buffer = ByteBuffer.allocateDirect(1);

        final boolean needsExpansion = (size + Integer.BYTES) > buffer.capacity();
        if (needsExpansion) {
            buffer = ByteBuffer.allocateDirect(size + Integer.BYTES);
        }

        final BufferedData writableSequentialData = BufferedData.wrap(buffer);

        buffer.putInt(size);
        TimestampTest.PROTOBUF.write(message, writableSequentialData);

        buffer.flip();

        final int readSize = buffer.getInt();
        assertEquals(size, readSize, "size header mismatch after expansion");

        final byte[] messageBytes = new byte[readSize];
        buffer.get(messageBytes);
        final TimestampTest parsed = TimestampTest.PROTOBUF.parse(BufferedData.wrap(messageBytes));

        assertEquals(message, parsed, "round-tripped message mismatch after expansion");
    }
}
