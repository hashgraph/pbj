// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.PbjReader;
import com.hedera.pbj.runtime.io.PbjWriter;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

public class PbjReaderWriterTest {

    @Test
    void WriteConstructorBufferSizeConsistent() {
        final int expectedSize = new PbjWriter().internalArray().length;
        assertEquals(expectedSize, new PbjWriter((OutputStream) null).internalArray().length);
        assertEquals(expectedSize, new PbjWriter((WritableSequentialData) null).internalArray().length);
        assertEquals(expectedSize, new PbjWriter(128).internalArray().length);
        // Does not apply to ByteBuffer, byte[], or reserve when large
    }

    @Test
    void writerConstructorCanReserveLarge() {
        PbjWriter writer = new PbjWriter(2 << 20);
        assertEquals(2 << 20, writer.internalArray().length);
    }

    @Test
    void writeByteBufferConstructorHeap() {
        ByteBuffer bb = ByteBuffer.allocate(32);
        PbjWriter writer = new PbjWriter(bb);
        writer.writeByte3((byte) 11, (byte) 22, (byte) 33);
        assertEquals(11, bb.array()[bb.arrayOffset()]);
        assertEquals(22, bb.array()[bb.arrayOffset() + 1]);
        assertEquals(33, bb.array()[bb.arrayOffset() + 2]);
        assertEquals(3, writer.position());
    }

    @Test
    void writeByteBufferConstructorDirect() {
        ByteBuffer bb = ByteBuffer.allocateDirect(32);
        PbjWriter writer = new PbjWriter(bb);
        writer.writeByte3((byte) 11, (byte) 22, (byte) 33);
        writer.flush();
        bb.flip();
        assertEquals(11, bb.get());
        assertEquals(22, bb.get());
        assertEquals(33, bb.get());
    }

    @Test
    void writeBytesFromRandomAccessData() {
        Bytes src = Bytes.wrap(new byte[] {10, 20, 30, 40});
        PbjWriter writer = new PbjWriter();
        writer.writeBytes(src);
        assertArrayEquals(new byte[] {10, 20, 30, 40}, writer.toByteArray());
    }

    @Test
    void writeBytesFromBufferedData() {
        BufferedData src = BufferedData.wrap(new byte[] {10, 20, 30, 40});
        PbjWriter writer = new PbjWriter();
        writer.writeBytes(src);
        assertArrayEquals(new byte[] {10, 20, 30, 40}, writer.toByteArray());
    }

    @Test
    void closeFlushesDataToOutputStream() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PbjWriter writer = new PbjWriter(baos);
        writer.writeByte2((byte) 55, (byte) 66);
        assertArrayEquals(new byte[] {}, baos.toByteArray());
        writer.close();
        assertArrayEquals(new byte[] {55, 66}, baos.toByteArray());
    }

    @Test
    void closeIsNoopWithoutOutputStream() {
        PbjWriter writer = new PbjWriter();
        writer.writeByte((byte) 1);
        writer.close();
        assertEquals(1, writer.position());
    }

    @Test
    void toByteArrayWrappedErrorsOnStreamingWriter() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PbjWriter writer = new PbjWriter(baos);
        assertEquals(Bytes.EMPTY, writer.internalArrayWrapped());
        assertEquals(Bytes.EMPTY, writer.toByteArrayWrapped());
        assertEquals(PbjWriter.UsageError, writer.error());
        assertThrows(RuntimeException.class, () -> writer.throwOnError());
    }

    @Test
    void toByteArrayErrorsOnStreamingWriter() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PbjWriter writer = new PbjWriter(baos);
        assertEquals(null, writer.toByteArray());
        assertEquals(PbjWriter.UsageError, writer.error());
        assertThrows(RuntimeException.class, () -> writer.throwOnError());
    }

    @Test
    void takeBytesReturnsEmptyOnNonReuseable() {
        byte[] bytes = new byte[64];
        PbjWriter writer = new PbjWriter(bytes, 0, bytes.length);
        writer.writeByte((byte) 1);
        Bytes result = writer.takeBytes();
        assertEquals(Bytes.EMPTY, result);
        assertEquals(PbjWriter.UsageError, writer.error());
        assertThrows(RuntimeException.class, () -> writer.throwOnError());
    }

    @Test
    void takeBytesReturnsEmptyWhenStreaming() {
        PbjWriter writer = new PbjWriter(new ByteArrayOutputStream());
        writer.writeByte((byte) 1);
        Bytes result = writer.takeBytes();
        assertEquals(Bytes.EMPTY, result);
        assertEquals(PbjWriter.UsageError, writer.error());
        assertThrows(RuntimeException.class, () -> writer.throwOnError());
    }

    @Test
    void writerRelativeReserve() {
        PbjWriter writer = new PbjWriter();
        byte[] origArray = writer.internalArray();
        writer.reserveRel(origArray.length);
        byte[] arr2 = writer.internalArray();
        assertEquals(origArray, arr2); // same object

        writer.reserveRel(origArray.length + 1);
        byte[] arr3 = writer.internalArray();
        assertNotEquals(origArray, arr3); // diff object

        writer.skip(arr3.length - 1);
        writer.reserveRel(1);
        byte[] arr4 = writer.internalArray();
        assertEquals(arr3, arr4);

        writer.skip(1);
        writer.reserveRel(0);
        byte[] arr5 = writer.internalArray();
        assertEquals(arr4, arr5);

        writer.reserveRel(1);
        byte[] arr6 = writer.internalArray();
        assertNotEquals(arr4, arr6);
    }

    @Test
    void writerUsesByteArray() {
        byte[] bytes = new byte[128];
        PbjWriter writer = new PbjWriter(bytes, 0, bytes.length);
        writer.writeByte3((byte) 10, (byte) 20, (byte) 30);
        assertEquals(bytes[0], 10);
        assertEquals(bytes[1], 20);
        assertEquals(bytes[2], 30);
    }

    @Test
    void manyStringRoundtrip() {
        byte[] bytes = new byte[128];
        PbjWriter writer = new PbjWriter(bytes, 0, bytes.length);
        String strings[] = {
            "a",
            "\u0100",
            "\u2603",
            "\uD800\uDC00",
            "\uE000",
            "a\u0100\u2603\uE000\uD800\uDC00",
            "\uD800\uDC00\uE000\u2603\u0100a",
            "\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uE000\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100\uD800\uDC00\u2603\u0100a"
        };
        for (String str : strings) {
            writer.reset();
            writer.writeStringWithTag(str);
            String res = writer.toPbjReader().readString(128);
            assertEquals(str, res);
        }
    }

    @Test
    void toByteArrayDoesAClone() {
        byte[] bytes = new byte[128];
        PbjWriter writer = new PbjWriter(bytes, 0, bytes.length);
        for (int i = 0; i < 128; i++) {
            writer.writeVarIntNoZZ(i);
        }
        assertEquals(128, writer.position());
        assertEquals(bytes, writer.internalArray());
        assertEquals(bytes, writer.internalArrayWrapped().array());
        byte[] arr1 = writer.toByteArray();
        assertNotEquals(bytes, arr1);
        Bytes arr2 = writer.toByteArrayWrapped();
        assertNotEquals(bytes, arr1);
        assertNotEquals(bytes, arr2.array());
        for (int i = 0; i < 128; i++) {
            assertEquals(i, arr1[i]);
            assertEquals(i, arr2.array()[i]);
        }
    }

    @Test
    void takeBytesSetsInternalToNull() {
        PbjWriter writer = new PbjWriter();
        writer.writeByte((byte) 1);
        byte[] internalArray = writer.internalArray();
        Bytes internalArrayWrapped = writer.internalArrayWrapped();
        assertEquals(internalArray, internalArrayWrapped.array());
        Bytes arr = writer.takeBytes();
        assertEquals(internalArray, arr.array());
        assertEquals(internalArrayWrapped, arr);
        assertEquals(null, writer.internalArray());
        assertThrows(NullPointerException.class, () -> writer.internalArrayWrapped());
    }

    @Test
    void writeResetCheck() {
        PbjWriter writer = new PbjWriter();
        writer.writeByte((byte) 10);
        assertEquals(1, writer.position());
        writer.reset();
        assertEquals(0, writer.position());
        writer.writeByte2((byte) 99, (byte) 88);
        assertEquals(2, writer.position());
        assertEquals(99, writer.internalArray()[0]);
        assertEquals(88, writer.internalArray()[1]);
        assertArrayEquals(new byte[] {99, 88}, writer.toByteArray());
    }

    @Test
    void throwOnErrorThrows_NoPrevOverwrite() {
        PbjWriter writer = new PbjWriter();
        writer.writeStringWithTag("\uD800"); // lone surrogate sets MalformString
        assertEquals(PbjWriter.MalformString, writer.error());
        assertThrows(RuntimeException.class, writer::throwOnError);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PbjWriter writer2 = new PbjWriter(baos);
        writer2.writeStringWithTag("\uD800"); // lone surrogate sets MalformString
        assertEquals(PbjWriter.MalformString, writer2.error());
        assertThrows(RuntimeException.class, writer2::throwOnError);
        // Confirm error didn't change
        assertEquals(Bytes.EMPTY, writer2.takeBytes()); // if no error, this would set usage error
        assertEquals(PbjWriter.MalformString, writer2.error());

        baos = new ByteArrayOutputStream();
        PbjWriter writer3 = new PbjWriter(baos);
        assertEquals(Bytes.EMPTY, writer3.takeBytes());
        assertEquals(PbjWriter.UsageError, writer3.error());
    }

    @Test
    void encodeUtf8HighSurrogateFollowedByHighSurrogate() {
        PbjWriter writer = new PbjWriter();
        writer.writeStringNoTag("\uD800\uD801");
        assertEquals(0, writer.position());
    }

    @Test
    void doesntThrowOnNoError() {
        PbjReader reader = new PbjReader(new byte[] {1, 2, 3, 4});
        assertEquals(0x04030201, reader.readIntLE());
        assertEquals(PbjReader.EOF, reader.error());
        assertDoesNotThrow(() -> reader.throwOnError()); // must not throw

        PbjWriter writer = new PbjWriter();
        writer.writeByte((byte) 1);
        assertEquals(0, writer.error());
        writer.throwOnError(); // must not throw
    }

    @Test
    void writeBytesToClosedOutputStreamShouldThrow() {
        final var baos = new ByteArrayOutputStream();
        final var writer = new PbjWriter(baos);
        assertDoesNotThrow(baos::close);

        final byte[] bigData = new byte[32 * 1024];
        assertThrows(UncheckedIOException.class, () -> writer.writeBytes(bigData, 0, bigData.length));
    }

    @Test
    void manyRoundtrip() {
        PbjWriter w = new PbjWriter();

        w.writeInt(0x01020304);
        w.writeIntBE(0x05060708);
        w.writeIntLE(0x090A0B0C);

        w.writeLong(0x0102030405060708L);
        w.writeLongBE(0x090A0B0C0D0E0F10L);
        w.writeLongLE(0x1112131415161718L);

        w.writeFloat(1.5f);
        w.writeFloatBE(2.5f);
        w.writeFloatLE(3.5f);

        w.writeDouble(1.25);
        w.writeDoubleBE(2.25);
        w.writeDoubleLE(3.25);

        byte[] arr1 = {10, 20, 30, 40, 50};
        w.writeBytes(arr1);
        w.writeBytes(arr1, 1, 3);
        w.writeBytes(Bytes.wrap(new byte[] {60, 70}));
        BufferedData bd = BufferedData.allocate(2);
        bd.writeByte((byte) 80);
        bd.writeByte((byte) 90);
        bd.flip();
        w.writeBytes(bd);

        w.writeStringWithTag("hello");
        w.writeStringWithTag("Ā☃");

        PbjReader reader = w.toPbjReader();

        assertEquals(0x01020304, reader.readInt());
        assertEquals(0x05060708, reader.readIntBE());
        assertEquals(0x090A0B0C, reader.readIntLE());

        assertEquals(0x0102030405060708L, reader.readLong());
        assertEquals(0x090A0B0C0D0E0F10L, reader.readLongBE());
        assertEquals(0x1112131415161718L, reader.readLongLE());

        assertEquals(1.5f, reader.readFloat(), 0f);
        assertEquals(2.5f, reader.readFloat(), 0f);
        assertEquals(3.5f, reader.readFloatLE(), 0f);

        assertEquals(1.25, reader.readDouble(), 0.0);
        assertEquals(2.25, reader.readDouble(), 0.0);
        assertEquals(3.25, reader.readDoubleLE(), 0.0);

        byte[] dst1 = new byte[5];
        reader.readBytes(dst1);
        assertArrayEquals(arr1, dst1);

        byte[] dst2 = new byte[3];
        reader.readBytes(dst2);
        assertArrayEquals(new byte[] {20, 30, 40}, dst2);

        byte[] dst3 = new byte[2];
        reader.readBytes(dst3);
        assertArrayEquals(new byte[] {60, 70}, dst3);

        byte[] dst4 = new byte[2];
        reader.readBytes(dst4);
        assertArrayEquals(new byte[] {80, 90}, dst4);

        assertEquals("hello", reader.readString(100));
        assertEquals("Ā☃", reader.readString(100));

        assertFalse(reader.hasMore());
        assertEquals(0, w.error());
    }

    @Test
    void writeVarIntZigZagRoundtrip() {
        int[] values = {0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 100, -100};
        PbjWriter writer = new PbjWriter();
        for (int v : values) {
            writer.reset();
            writer.writeVarInt(v, true);
            assertEquals(v, writer.toPbjReader().readVarInt(true), "Failed for value " + v);

            writer.reset();
            writer.writeVarInt(v, false);
            assertEquals(v, writer.toPbjReader().readVarInt(false), "Failed for value " + v);

            writer.reset();
            writer.writeVarIntZZ(v);
            assertEquals(v, writer.toPbjReader().readVarInt(true), "Failed for value " + v);

            writer.reset();
            writer.writeVarIntNoZZ(v);
            assertEquals(v, writer.toPbjReader().readVarInt(false), "Failed for value " + v);
        }
    }

    @Test
    void writeVarLongZigZagRoundtrip() {
        long[] values = {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 1_000_000_000L, -1_000_000_000L};
        PbjWriter writer = new PbjWriter();
        for (long v : values) {
            writer.reset();
            writer.writeVarLong(v, true);
            assertEquals(v, writer.toPbjReader().readVarLong(true), "Failed for value " + v);

            writer.reset();
            writer.writeVarLong(v, false);
            assertEquals(v, writer.toPbjReader().readVarLong(false), "Failed for value " + v);

            writer.reset();
            writer.writeVarLongZZ(v);
            assertEquals(v, writer.toPbjReader().readVarLong(true), "Failed for value " + v);

            writer.reset();
            writer.writeVarLongNoZZ(v);
            int noZZLen = writer.position();
            assertEquals(v, writer.toPbjReader().readVarLong(false), "Failed for value " + v);

            // edge test
            writer.reset();
            int len = writer.internalArray().length;
            writer.skip(len);
            writer.writeVarLongNoZZ(v);
            byte[] buf = writer.internalArray();
            for (int i = 0; i < noZZLen; i++) {
                assertEquals(buf[i], buf[i + len]);
            }
        }
    }

    @Test
    void throwOnErrorThrowsWhenErrorIsSet() {
        PbjWriter writer = new PbjWriter();
        writer.writeStringNoTag("\uD800");
        assertEquals(PbjWriter.MalformString, writer.error());
        assertThrows(RuntimeException.class, writer::throwOnError);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer = new PbjWriter(baos);
        writer.writeStringNoTag("\uD800");
        assertEquals(PbjWriter.MalformString, writer.error());
        assertThrows(RuntimeException.class, writer::throwOnError);
        assertEquals(Bytes.EMPTY, writer.takeBytes());

        assertEquals(PbjWriter.MalformString, writer.error());

        baos = new ByteArrayOutputStream();
        writer = new PbjWriter(baos);
        assertEquals(Bytes.EMPTY, writer.takeBytes());
        assertEquals(PbjWriter.UsageError, writer.error());
    }

    @Test
    void testWriteByteAtEdge() {
        PbjWriter writer = new PbjWriter();
        int defaultLen = writer.internalArray().length;
        writer.skip(defaultLen - 1);
        writer.writeByte((byte) 1);
        byte[] internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 1]);
        writer.writeByte((byte) 1);
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 1]);
        assertEquals((byte) 1, internalArray[defaultLen]);

        writer = new PbjWriter();
        writer.skip(defaultLen - 2);
        writer.writeByte2((byte) 1, (byte) 2);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 2]);
        assertEquals((byte) 2, internalArray[defaultLen - 1]);
        writer.skip(-1);
        writer.writeByte2((byte) 1, (byte) 2);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 1]);
        assertEquals((byte) 2, internalArray[defaultLen]);

        writer = new PbjWriter();
        writer.skip(defaultLen - 3);
        writer.writeByte3((byte) 1, (byte) 2, (byte) 3);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 3]);
        assertEquals((byte) 2, internalArray[defaultLen - 2]);
        assertEquals((byte) 3, internalArray[defaultLen - 1]);
        writer.skip(-2);
        writer.writeByte3((byte) 1, (byte) 2, (byte) 3);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 2]);
        assertEquals((byte) 2, internalArray[defaultLen - 1]);
        assertEquals((byte) 3, internalArray[defaultLen]);

        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeByte4((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 4]);
        assertEquals((byte) 2, internalArray[defaultLen - 3]);
        assertEquals((byte) 3, internalArray[defaultLen - 2]);
        assertEquals((byte) 4, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeByte4((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 1, internalArray[defaultLen - 3]);
        assertEquals((byte) 2, internalArray[defaultLen - 2]);
        assertEquals((byte) 3, internalArray[defaultLen - 1]);
        assertEquals((byte) 4, internalArray[defaultLen]);

        // writeInt writes big-endian: 4 = {0, 0, 0, 4}
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeInt(4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0, internalArray[defaultLen - 4]);
        assertEquals((byte) 0, internalArray[defaultLen - 3]);
        assertEquals((byte) 0, internalArray[defaultLen - 2]);
        assertEquals((byte) 4, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeInt(4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0, internalArray[defaultLen - 3]);
        assertEquals((byte) 0, internalArray[defaultLen - 2]);
        assertEquals((byte) 0, internalArray[defaultLen - 1]);
        assertEquals((byte) 4, internalArray[defaultLen]);

        // writeIntLE writes little-endian: 4 = {4, 0, 0, 0}
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeIntLE(4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 4, internalArray[defaultLen - 4]);
        assertEquals((byte) 0, internalArray[defaultLen - 3]);
        assertEquals((byte) 0, internalArray[defaultLen - 2]);
        assertEquals((byte) 0, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeIntLE(4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 4, internalArray[defaultLen - 3]);
        assertEquals((byte) 0, internalArray[defaultLen - 2]);
        assertEquals((byte) 0, internalArray[defaultLen - 1]);
        assertEquals((byte) 0, internalArray[defaultLen]);

        // writeLong writes big-endian: 8 = {0, 0, 0, 0, 0, 0, 0, 8}
        writer = new PbjWriter();
        writer.skip(defaultLen - 8);
        writer.writeLong(8);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 8, internalArray[defaultLen - 1]);
        writer.skip(-7);
        writer.writeLong(8);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0, internalArray[defaultLen - 1]);
        assertEquals((byte) 8, internalArray[defaultLen]);

        // writeFloatLE writes little-endian: 4.0f = 0x40800000 = {0x00, 0x00, 0x80, 0x40}
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeFloatLE(4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0x00, internalArray[defaultLen - 4]);
        assertEquals((byte) 0x00, internalArray[defaultLen - 3]);
        assertEquals((byte) 0x80, internalArray[defaultLen - 2]);
        assertEquals((byte) 0x40, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeFloatLE(4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0x00, internalArray[defaultLen - 3]);
        assertEquals((byte) 0x00, internalArray[defaultLen - 2]);
        assertEquals((byte) 0x80, internalArray[defaultLen - 1]);
        assertEquals((byte) 0x40, internalArray[defaultLen]);

        // writeDoubleLE writes little-endian: 8.0 = 0x4020000000000000 = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20,
        // 0x40}
        writer = new PbjWriter();
        writer.skip(defaultLen - 8);
        writer.writeDoubleLE(8);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0x20, internalArray[defaultLen - 2]);
        assertEquals((byte) 0x40, internalArray[defaultLen - 1]);
        writer.skip(-7);
        writer.writeDoubleLE(8);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 0x20, internalArray[defaultLen - 1]);
        assertEquals((byte) 0x40, internalArray[defaultLen]);

        byte[] array4 = new byte[] {10, 20, 30, 40};
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeBytes(array4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 4]);
        assertEquals((byte) 20, internalArray[defaultLen - 3]);
        assertEquals((byte) 30, internalArray[defaultLen - 2]);
        assertEquals((byte) 40, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeBytes(array4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 3]);
        assertEquals((byte) 20, internalArray[defaultLen - 2]);
        assertEquals((byte) 30, internalArray[defaultLen - 1]);
        assertEquals((byte) 40, internalArray[defaultLen]);

        Bytes bytes4 = Bytes.wrap(new byte[] {10, 20, 30, 40});
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeBytes(bytes4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 4]);
        assertEquals((byte) 20, internalArray[defaultLen - 3]);
        assertEquals((byte) 30, internalArray[defaultLen - 2]);
        assertEquals((byte) 40, internalArray[defaultLen - 1]);
        writer.skip(-3);
        writer.writeBytes(bytes4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 3]);
        assertEquals((byte) 20, internalArray[defaultLen - 2]);
        assertEquals((byte) 30, internalArray[defaultLen - 1]);
        assertEquals((byte) 40, internalArray[defaultLen]);

        BufferedData bb4 = BufferedData.wrap(new byte[] {10, 20, 30, 40});
        writer = new PbjWriter();
        writer.skip(defaultLen - 4);
        writer.writeBytes(bb4);
        internalArray = writer.internalArray();
        assertEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 4]);
        assertEquals((byte) 20, internalArray[defaultLen - 3]);
        assertEquals((byte) 30, internalArray[defaultLen - 2]);
        assertEquals((byte) 40, internalArray[defaultLen - 1]);
        writer.skip(-3);
        bb4.resetPosition();
        writer.writeBytes(bb4);
        assertEquals(defaultLen + 1, writer.position());
        internalArray = writer.internalArray();
        assertNotEquals(defaultLen, internalArray.length);
        assertEquals((byte) 10, internalArray[defaultLen - 3]);
        assertEquals((byte) 20, internalArray[defaultLen - 2]);
        assertEquals((byte) 30, internalArray[defaultLen - 1]);
        assertEquals((byte) 40, internalArray[defaultLen]);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writer = new PbjWriter(baos);
        writer.skip(defaultLen - 4);
        bb4.resetPosition();
        writer.writeBytes(bb4);
        assertEquals(0, baos.size());

        writer.skip(-3);
        bb4.resetPosition();
        writer.writeBytes(bb4);
        assertEquals(defaultLen + 1, writer.position());

        assertEquals(defaultLen, baos.size());
        byte[] data = baos.toByteArray();
        assertEquals(defaultLen, data.length);
        assertEquals((byte) 10, data[defaultLen - 4]);

        assertEquals((byte) 10, data[defaultLen - 3]);
        assertEquals((byte) 20, data[defaultLen - 2]);
        assertEquals((byte) 30, data[defaultLen - 1]);
        // last byte inside internal buffer

        // Again but the Bytes path
        baos = new ByteArrayOutputStream();
        writer = new PbjWriter(baos);
        writer.skip(defaultLen - 4);
        writer.writeBytes(bytes4);
        assertEquals(0, baos.size());

        writer.skip(-3);
        writer.writeBytes(bytes4);
        assertEquals(defaultLen + 1, writer.position());

        assertEquals(defaultLen, baos.size());
        data = baos.toByteArray();
        assertEquals(defaultLen, data.length);
        assertEquals((byte) 10, data[defaultLen - 4]);

        assertEquals((byte) 10, data[defaultLen - 3]);
        assertEquals((byte) 20, data[defaultLen - 2]);
        assertEquals((byte) 30, data[defaultLen - 1]);
        // last byte inside internal buffer
    }

    @Test
    void utf8EncodingTest() {
        PbjWriter writer = new PbjWriter();
        char arr[] = new char[6 << 10];
        for (int i = 0; i < 127; i++) {
            arr[i] = 'a';
        }

        writer.writeStringWithTag(new String(arr, 0, 127));
        assertEquals(128, writer.position());

        arr[50] = 'Ā';
        writer.reset();
        writer.writeStringWithTag(new String(arr, 0, 127));
        assertEquals(130, writer.position());

        for (int i = 0; i < 127; i++) {
            arr[i] = 'Ā';
        }
        writer.reset();
        writer.writeStringWithTag(new String(arr, 0, 127));
        assertEquals(127 * 2 + 2, writer.position());

        for (int i = 0; i < 5460; i++) {
            arr[i] = 'b';
        }
        writer.reset();
        writer.writeStringWithTag(new String(arr, 0, 5460));
        assertEquals(5460 + 2, writer.position());

        for (int i = 0; i < 5460; i++) {
            arr[i] = 'ā';
        }

        writer.reset();
        writer.writeStringWithTag(new String(arr, 0, 5460));
        assertEquals(5460 * 2 + 2, writer.position());

        for (int i = 0; i < 6 << 10; i++) {
            arr[i] = (char) (32 + i);
        }

        writer.reset();
        String str = new String(arr);
        writer.writeStringWithTag(str);
        String res = writer.toPbjReader().readString(1 << 20);
        assertEquals(str, res);
    }

    @Test
    void readerLimit() {
        byte[] data = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        PbjReader reader = new PbjReader(data);

        assertTrue(reader.hasMore());
        assertEquals(0, reader.position());
        assertEquals(data.length, reader.limit());
        reader.limit(0);
        assertTrue(!reader.hasMore());
        assertEquals(0, reader.position());

        reader.limit(4);
        assertTrue(reader.hasMore());
        assertEquals(0x01020304, reader.readIntBE());
        assertFalse(reader.hasMore());
        assertEquals(4, reader.position());
        assertEquals(0, reader.readIntBE());
        assertEquals(PbjReader.BufferUnderflow, reader.error());
    }

    @Test
    void readerByteBufferConstructor() {
        PbjReader reader = new PbjReader(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}));
        assertEquals(0x01020304, reader.readIntBE());
        assertFalse(reader.hasMore());
    }

    @Test
    void readerBufferBytesConstructor() {
        PbjReader reader = new PbjReader(Bytes.wrap(new byte[] {0x0A, 0x0B, 0x0C, 0x0D}));
        assertEquals(0x0A0B0C0D, reader.readIntBE());
        assertFalse(reader.hasMore());
    }

    @Test
    void readerBufferInputStreamConstructor() {
        PbjReader reader = new PbjReader(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));
        assertEquals(0x01020304, reader.readIntBE());
        assertFalse(reader.hasMore());
    }

    @Test
    void readerSkipAdvancesPosition() {
        PbjReader reader = new PbjReader(new byte[] {1, 2, 3, 4, 5});
        reader.skip(3);
        assertEquals(3, reader.position());
        assertTrue(reader.hasMore());
    }

    @Test
    void readerSkipBeyondDataSetsBufferUnderflow() {
        PbjReader reader = new PbjReader(new byte[] {1, 2, 3, 4});
        reader.skip(5);
        assertEquals(PbjReader.BufferUnderflow, reader.error());
    }

    @Test
    void readerResetAllowsReRead() {
        byte[] data = {1, 2, 3, 4};
        PbjReader reader = new PbjReader(data);
        assertEquals(0x01020304, reader.readIntBE());
        assertFalse(reader.hasMore());
        reader.reset();
        assertEquals(0, reader.position());
        assertTrue(reader.hasMore());
        assertEquals(0x01020304, reader.readIntBE());
    }

    @Test
    void readerResetWithReplacesBuffer() {
        PbjReader reader = new PbjReader(new byte[] {1, 2, 3, 4});
        assertEquals(0x01020304, reader.readIntBE());
        reader.resetWith(Bytes.wrap(new byte[] {5, 6, 7, 8}));
        assertEquals(0x05060708, reader.readIntBE());
        assertFalse(reader.hasMore());
    }

    @Test
    void writeLargeBytesBypassInternalBuffer() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PbjWriter writer = new PbjWriter(baos);
        writer.writeByte((byte) 7);
        byte[] large = new byte[4096];
        large[0] = 11;
        large[4095] = 22;
        writer.writeBytes(large);

        byte[] result = baos.toByteArray();
        assertEquals(4097, result.length);
        assertEquals(7, result[0]);
        assertEquals(11, result[1]);
        assertEquals(22, result[4096]);

        byte[] internalArray = writer.internalArray();
        assertEquals(7, internalArray[0]);
        assertEquals(0, internalArray[1]);
    }

    @Test
    void writeBytesRandomAccessDataZeroLengthWritesNothing() {
        PbjWriter writer = new PbjWriter();
        writer.writeBytes(Bytes.wrap(new byte[0]));
        assertEquals(0, writer.position());
        writer.writeBytes(Bytes.EMPTY);
        assertEquals(0, writer.position());
    }

    @Test
    void writeBytesBufferedDataZeroRemainingWritesNothing() {
        PbjWriter writer = new PbjWriter();
        writer.writeBytes(BufferedData.wrap(new byte[0]));
        assertEquals(0, writer.position());

        BufferedData bd = BufferedData.wrap(new byte[] {1, 2, 3});
        bd.skip(3);
        writer.writeBytes(bd);
        assertEquals(0, writer.position());
    }

    @Test
    void writeBytesArrayZeroAndNegativeLengthWritesNothing() {
        byte[] src = new byte[] {1, 2, 3, 4};
        PbjWriter writer = new PbjWriter();
        writer.writeBytes(src, 0, 0);
        assertEquals(0, writer.position());
        writer.writeBytes(src, 0, -2);
        assertEquals(0, writer.position());
    }

    @Test
    void writeBytesArrayFastPathBoundaryWithOutputStream() {

        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PbjWriter writer = new PbjWriter(baos);
            byte[] src = new byte[2047];
            src[0] = 11;
            src[2046] = 22;
            writer.writeBytes(src, 0, 2047);
            assertEquals(11, writer.internalArray()[0]);
            assertEquals(0, baos.toByteArray().length);
            writer.reset();
            baos.reset();
            int internalLen = writer.internalArray().length;
            writer.skip(internalLen - 1);
            writer.writeBytes(src, 0, 2047);
            assertEquals(internalLen - 1, baos.toByteArray().length);
        }
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PbjWriter writer = new PbjWriter(baos);
            byte[] src = new byte[2048];
            src[0] = 11;
            src[2047] = 22;
            writer.writeBytes(src, 0, 2048);
            writer.flush();
            assertEquals(0, writer.internalArray()[0]); // this length bypasses buffer
            byte[] result = baos.toByteArray();
            assertEquals(2048, result.length);
            assertEquals((byte) 11, result[0]);
            assertEquals((byte) 22, result[2047]);
            byte[] internalArray = writer.internalArray();
            assertEquals(0, internalArray[0]);
            assertEquals(0, internalArray[2047]);
        }
    }
}
