// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.client.helidon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import io.helidon.common.buffers.BufferData;
import java.nio.BufferOverflowException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PbjGrpcDatagramReaderTest {
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;

    @Test
    void checkBufferOverflow() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(MAX_BUFFER_SIZE);

        // First, test the happy case, fill up the buffer to the current max limit (note that it's hard-coded here):
        BufferData goodData = BufferData.create(MAX_BUFFER_SIZE);
        goodData.write(0);
        goodData.writeInt32(MAX_BUFFER_SIZE - 5);
        goodData.write(new byte[MAX_BUFFER_SIZE - 5]);
        reader.add(goodData);

        // Now try to add some more, 1 byte should be enough:
        BufferData badData = BufferData.create("b");
        assertThrows(BufferOverflowException.class, () -> reader.add(badData));
    }

    @Test
    void testSingleCompleteDatagram() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(MAX_BUFFER_SIZE);

        // Trivial case of a zero-size datagram
        BufferData zeroData = BufferData.create(new byte[] {0, 0, 0, 0, 0});
        reader.add(zeroData);
        PbjGrpcDatagramReader.Datagram datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        Bytes bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(0, bufferData.length());

        // 1 byte long datagram
        BufferData oneData = BufferData.create(new byte[] {0, 0, 0, 0, 1, 66});
        reader.add(oneData);
        datagram = reader.extractNextDatagram();
        assertEquals(0, datagram.compressedFlag());
        bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(1, bufferData.length());
        assertEquals(66, bufferData.getByte(0));

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
        assertEquals(data.getBytes().length, bufferData.length());
        assertEquals(data, bufferData.asUtf8String());
    }

    @Test
    void testSplitDatagram() {
        PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(MAX_BUFFER_SIZE);

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
        Bytes bufferData = datagram.data();
        assertNotNull(bufferData);
        assertEquals(data.getBytes().length, bufferData.length());
        assertEquals(data, bufferData.asUtf8String());
    }

    @Test
    void testDrainCompleteMessageWhileNextMessageIsPartial() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(MAX_BUFFER_SIZE);
        final String first = "a".repeat(1000);
        final String second = "b".repeat(1000);
        final BufferData firstFrame = BufferData.create(first.length() + 6);
        firstFrame.write(0);
        firstFrame.writeInt32(first.length());
        firstFrame.write(first.getBytes());
        firstFrame.write(0); // First byte of the second message's prefix.
        reader.add(firstFrame);

        assertEquals(first, reader.extractNextDatagram().data().asUtf8String());
        assertNull(reader.extractNextDatagram());
        final BufferData remaining = BufferData.create(second.length() + 4);
        remaining.writeInt32(second.length());
        remaining.write(second.getBytes());
        reader.add(remaining);
        assertEquals(second, reader.extractNextDatagram().data().asUtf8String());
        assertNull(reader.extractNextDatagram());
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
            Bytes bufferData = datagram.data();
            assertNotNull(bufferData);
            assertEquals(dataString.getBytes().length, bufferData.length());
            assertEquals(dataString, bufferData.asUtf8String());
        });

        // Ensure there's nothing else there:
        assertNull(reader.extractNextDatagram());
    }

    @Test
    void testRepeatedBatchesRemainOrdered() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(MAX_BUFFER_SIZE);
        testDatagrams(reader, List.of("a".repeat(1000), "b".repeat(1000)));
        testDatagrams(reader, List.of("1".repeat(1000), "2".repeat(1000), "3".repeat(1000)));
    }

    @Test
    void preservesCompressionFlagAndWirePayload() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(100);
        reader.add(BufferData.create(new byte[] {1, 0, 0, 0, 3, 10, 20, 30}));
        final PbjGrpcDatagramReader.Datagram message = reader.extractNextDatagram();
        assertEquals(1, message.compressedFlag());
        assertEquals(Bytes.wrap(new byte[] {10, 20, 30}), message.data());
    }

    @Test
    void queuedAndPartialMessagesShareTheBufferLimit() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(11);
        reader.add(BufferData.create(new byte[] {0, 0, 0, 0, 1, 42, 0, 0, 0, 0, 1}));
        assertThrows(BufferOverflowException.class, () -> reader.add(BufferData.create(new byte[] {43})));
        assertEquals(Bytes.wrap(new byte[] {42}), reader.extractNextDatagram().data());
        reader.add(BufferData.create(new byte[] {43}));
        assertEquals(Bytes.wrap(new byte[] {43}), reader.extractNextDatagram().data());
    }

    @Test
    void rejectsAnOversizedAnnouncedMessageBeforeAllocatingPayload() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(10);
        assertThrows(BufferOverflowException.class, () -> reader.add(BufferData.create(new byte[] {0, 0, 0, 0, 6})));
    }

    @Test
    void completedMessagesOwnTheirBytes() {
        final PbjGrpcDatagramReader reader = new PbjGrpcDatagramReader(20);
        final byte[] frame = {0, 0, 0, 0, 1, 42};
        reader.add(BufferData.create(frame));
        frame[5] = 99;
        assertEquals(Bytes.wrap(new byte[] {42}), reader.extractNextDatagram().data());
    }
}
