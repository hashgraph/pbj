package com.hedera.pbj.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import net.bytebuddy.utility.RandomString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

class ProtoParserToolsTest {

    private final RandomGenerator rng = RandomGenerator.getDefault();

    @Test
    void testReadInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readInt32,
                // in this case the size may up to 10 bytes in case of negative numbers,
                // because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadInt64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readInt64,
                // in this case the size may be 10 bytes, because we don't use zigzag encoding
                Long.BYTES + 2);
    }

    @Test
    void testReadUint32() {
        testRead(() ->
                rng.nextInt(0, Integer.MAX_VALUE),
                (d, v) -> d.writeVarInt(v, false),
                ProtoParserTools::readUint32,
                // the size may vary from 1 to 5 bytes
                Integer.BYTES + 1);
    }

    @Test
    void testReadUint64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, false),
                ProtoParserTools::readUint64,
                // the size may vary from 1 to 10 bytes
                Long.BYTES + 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void testReadBool(final int value) {
        testRead(() -> value != 0, (d, v) -> d.writeVarInt(value, false), input -> {
            try {
                return ProtoParserTools.readBool(input);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void testReadEnum(int value) {
        testRead(() -> value, (d, v) -> d.writeVarInt(value, false), ProtoParserTools::readEnum, 1);
    }


    @Test
    void testReadSignedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeVarInt(v, true),
                ProtoParserTools::readSignedInt32,
                Integer.BYTES + 1);
    }

    @Test
    void testReadSignedInt64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeVarLong(v, true),
                ProtoParserTools::readSignedInt64,
                Long.BYTES + 2);
    }

    @Test
    void testReadSignedFixedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadFixedInt32() {
        testRead(rng::nextInt,
                (d, v) -> d.writeInt(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed32,
                Integer.BYTES);
    }

    @Test
    void testReadSginedFixed64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readSignedFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFixed64() {
        testRead(rng::nextLong,
                (d, v) -> d.writeLong(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFixed64,
                Long.BYTES);
    }

    @Test
    void testReadFloat() {
        testRead(rng::nextFloat,
                (d, v) -> d.writeFloat(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readFloat,
                Long.BYTES);
    }

    @Test
    void testReadDouble() {
        testRead(rng::nextDouble,
                (d, v) -> d.writeDouble(v, ByteOrder.LITTLE_ENDIAN),
                ProtoParserTools::readDouble,
                Long.BYTES);
    }

    @Test
    void testReadString() {
        final int length = rng.nextInt(0, 100);
        final RandomString randomString = new RandomString(length);

        testRead(randomString::nextString,
                (d, v) -> {
                    d.writeVarInt(length, false); // write the size first
                    d.writeUTF8(v);
                },
                ProtoParserTools::readString,
                length + 1);
    }
    @Test
    void testReadString_incomplete() throws IOException {
        final int length = rng.nextInt(0, 100);
        final RandomString randomString = new RandomString(length);
        final BufferedData data = BufferedData.allocate(length + 1);
        data.writeVarInt(length, false); // write the size first
        final String expectedValue = randomString.nextString();
        data.writeUTF8(expectedValue);
        final byte[] bytes = data.toInputStream().readAllBytes();
        final byte[] incompleteCopy = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, incompleteCopy, 0, bytes.length - 1);
        final ReadableStreamingData streamingData = new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy));
        assertThrows(BufferUnderflowException.class, () -> ProtoParserTools.readString(streamingData));

    }

    @Test
    void testReadBytes() {
        final int length = rng.nextInt(0, 100);
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final Bytes bytes = Bytes.wrap(byteArray);

        testRead(() -> bytes,
                (d, v) -> {
                    d.writeVarInt(length, false); // write the size first
                    d.writeBytes(v);
                },
                ProtoParserTools::readBytes,
                length + 1);
    }

    @Test
    void testReadBytes_incomplete() throws IOException {
        final int length = rng.nextInt(0, 100);
        final byte[] byteArray = new byte[length];
        rng.nextBytes(byteArray);
        final BufferedData data = BufferedData.allocate(length + 1);
        data.writeVarInt(length, false); // write the size first
        data.writeBytes(byteArray);
        final byte[] bytes = data.toInputStream().readAllBytes();
        final byte[] incompleteCopy = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, incompleteCopy, 0, bytes.length - 1);
        final ReadableStreamingData streamingData = new ReadableStreamingData(new ByteArrayInputStream(incompleteCopy));
        assertThrows(BufferUnderflowException.class, () -> ProtoParserTools.readString(streamingData));
    }

    private static <T> void testRead(final Supplier<? extends T> valueSupplier,
                                     final BiConsumer<BufferedData, ? super T> valueWriter,
                                     final Function<? super BufferedData, T> reader,
                                     final int size) {
        final T value = valueSupplier.get();
        final BufferedData data = BufferedData.allocate(size);
        valueWriter.accept(data, value);
        data.flip();
        assertEquals(value, reader.apply(data));
    }

}