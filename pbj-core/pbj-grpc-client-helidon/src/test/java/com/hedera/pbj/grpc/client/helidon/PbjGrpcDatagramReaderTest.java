// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.helidon.common.buffers.BufferData;
import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.List;
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
        PbjGrpcDatagramReader.Datagram datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        BufferData bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(0, bufferData.available());

        // 1 byte long datagram
        BufferData oneData = BufferData.create(new byte[] {0, 0, 0, 0, 1, 66});
        reader.add(oneData);
        datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        bufferData = datagram.data();
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
        datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        bufferData = datagram.data();
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

        PbjGrpcDatagramReader.Datagram datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        BufferData bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(data.getBytes().length, bufferData.available());
        assertEquals(data, bufferData.readString(data.getBytes().length));
    }

    @Test
    void testFlipCircularBuffer() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader();

        // The initial size is currently 1024, so fill it up almost completely:
        String dataString = "a".repeat(1000);

        reader.add(BufferData.create(new byte[] {0}));
        assertNull(reader.extractNextDatagram());

        BufferData lengthData = BufferData.create(4);
        lengthData.writeInt32(dataString.getBytes().length);
        reader.add(lengthData);
        assertNull(reader.extractNextDatagram());

        BufferData theData = BufferData.create(dataString.getBytes());
        reader.add(theData);

        // At this point we use 1005 bytes of the initial 1024 capacity.
        // Just in case we implement any future optimizations, let's start
        // sending the next datagram right away
        reader.add(BufferData.create(new byte[] {0}));

        // At this point the writePosition is at 1006 or something like that.
        // Let's read the first datagram and mark almost the entire buffer free
        // (except for that zero byte that we've just added above):
        PbjGrpcDatagramReader.Datagram datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        BufferData bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(dataString.getBytes().length, bufferData.available());
        assertEquals(dataString, bufferData.readString(dataString.getBytes().length));

        // The next datagram isn't ready yet:
        assertNull(reader.extractNextDatagram());

        // Now let's just finish adding the exact same 1000 bytes datagram,
        // which should force the reader to flip:
        String newDataString = "b".repeat(dataString.length());
        lengthData.rewind();
        reader.add(lengthData);
        assertNull(reader.extractNextDatagram());
        reader.add(BufferData.create(newDataString.getBytes()));

        // At this point, the circular buffer must've flipped over the tail to the head.
        // The buffer is private, so we cannot check it. But we should be able to
        // read back the last datagram. And more importantly, the flipping logic
        // is now covered by tests.
        PbjGrpcDatagramReader.Datagram newDatagram = reader.extractNextDatagram();
        assertEquals(0, newDatagram.compressedFlag());
        BufferData newBufferData = newDatagram.data();
        assertNotNull(newBufferData);
        assertEquals(newDataString.getBytes().length, newBufferData.available());
        assertEquals(newDataString, newBufferData.readString(newDataString.getBytes().length));
    }

    private void testDatagrams(final PbjGrpcDatagramReader reader, final List<String> datagrams) {
        datagrams.forEach(dataString -> {
            reader.add(BufferData.create(new byte[] {0}));
            BufferData lengthData = BufferData.create(4);
            lengthData.writeInt32(dataString.getBytes().length);
            reader.add(lengthData);
            BufferData theData = BufferData.create(dataString.getBytes());
            reader.add(theData);
        });

        // Read them back and check them:
        datagrams.forEach(dataString -> {
            PbjGrpcDatagramReader.Datagram datagram = reader.extractNextDatagram();
            assertEquals(0, datagram.compressedFlag());
            BufferData bufferData = datagram.data();
            assertNotNull(bufferData);
            assertEquals(dataString.getBytes().length, bufferData.available());
            assertEquals(dataString, bufferData.readString(dataString.getBytes().length));
        });

        // Ensure there's nothing else there:
        assertNull(reader.extractNextDatagram());
    }

    @Test
    void testEnlargePartiallyFilledBuffer() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader();

        // Add two datagrams of 1000 bytes, which will enlarge the initial 1024 bytes buffer
        testDatagrams(reader, List.of("a".repeat(1000), "b".repeat(1000)));

        // Now repeat this again, but this time 3 times. The writePosition of the buffer
        // is now near the tail. So writing 2 same datagrams would simply flip the pointer.
        // However, writing the 3rd one would enlarge the buffer again AND copy parts
        // from both the tail and the head of the buffer, hence fully covering the enlarging logic:
        testDatagrams(reader, List.of("1".repeat(1000), "2".repeat(1000), "3".repeat(1000)));
    }
}
