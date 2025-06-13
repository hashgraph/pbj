// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.helidon.common.buffers.BufferData;
import java.nio.BufferOverflowException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class PbjGrpcDatagramReaderTest {
    @Test
    void checkBufferOverflow() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader();

        // First, test the happy case, fill up the buffer to the current max limit (note that it's hard-coded here):
        BufferData goodData = BufferData.create("a".repeat(10 * 1024 * 1024));
        reader.add(goodData);

        // Now try to add some more, 1 byte should be enough:
        BufferData badData = BufferData.create("b");
        assertThrows(BufferOverflowException.class, () -> reader.add(badData));
    }

    @Test
    void testSingleCompleteDatagram() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader();

        // Trivial case of a zero-size datagram
        BufferData zeroData = BufferData.create(new byte[] {0, 0, 0, 0, 0});
        reader.add(zeroData);
        BufferData bufferData = reader.extractNextDatagram();
        assertNotNull(bufferData);
        assertEquals(0, bufferData.available());

        // 1 byte long datagram
        BufferData oneData = BufferData.create(new byte[] {0, 0, 0, 0, 1, 66});
        reader.add(oneData);
        bufferData = reader.extractNextDatagram();
        assertNotNull(bufferData);
        assertEquals(1, bufferData.available());
        assertEquals(66, bufferData.read());

        // Many bytes long datagram
        String data = "Some test data here...";
        BufferData manyData = BufferData.create(5 + data.getBytes().length);
        manyData.write(0);
        manyData.writeInt32(data.getBytes().length);
        manyData.write(data.getBytes());
        reader.add(manyData);
        bufferData = reader.extractNextDatagram();
        assertNotNull(bufferData);
        assertEquals(data.getBytes().length, bufferData.available());
        assertEquals(data, bufferData.readString(data.getBytes().length));
    }

    @Test
    void testSplitDatagram() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader();

        // This is very similar to the many bytes long datagram test above, but we feed the reader
        // little by little instead of adding the entire datagram at once:
        String data = "Some test data here...";

        reader.add(BufferData.create(new byte[] {0}));
        assertNull(reader.extractNextDatagram());

        BufferData someData = BufferData.create(4);
        someData.writeInt32(data.getBytes().length);
        reader.add(someData);
        assertNull(reader.extractNextDatagram());

        BufferData someMoreData = BufferData.create(Arrays.copyOf(data.getBytes(), 8));
        reader.add(someMoreData);
        assertNull(reader.extractNextDatagram());

        BufferData finalData = BufferData.create(Arrays.copyOfRange(data.getBytes(), 8, data.getBytes().length));
        reader.add(finalData);

        BufferData bufferData = reader.extractNextDatagram();
        assertNotNull(bufferData);
        assertEquals(data.getBytes().length, bufferData.available());
        assertEquals(data, bufferData.readString(data.getBytes().length));
    }
}
