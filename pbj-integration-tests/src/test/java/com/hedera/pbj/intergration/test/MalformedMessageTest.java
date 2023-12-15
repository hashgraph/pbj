package com.hedera.pbj.intergration.test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.test.proto.pbj.Everything;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import com.hedera.pbj.test.proto.pbj.codec.EverythingProtoCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.random.RandomGenerator;

class MalformedMessageTest {

    private EverythingProtoCodec codec;
    private RandomGenerator rng = RandomGenerator.getDefault();

    @BeforeEach
    public void setUp() {
        codec = new EverythingProtoCodec();
    }

    @Test
    void parseMalformedEverything_overflow() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(9);
        final BufferedData data = prepareTestData(buffer);
        buffer.array()[1] += 1; // artificially increase message size
        // parser fails because the message size is not expected
        assertThrows(BufferUnderflowException.class,() -> codec.parse(data));
    }

    @Test
    void parseMalformedEverything_parse_fail() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        final BufferedData data = prepareTestData(buffer);
        buffer.limit(10); // we trick the parser into thinking that there is more to process
        buffer.array()[9] = 0; // but the byte is not valid
        buffer.array()[1] += 1; // artificially increase message size
        assertThrows(IOException.class,() -> codec.parse(data)); // parser fails because of an unknown tag
    }

    @Test
    void parseMalformedEverything_underflow_for_nested() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(10);
        final BufferedData data = prepareTestData(buffer);
        buffer.limit(10); // we trick the parser into thinking that there is more to process
        buffer.array()[9] = 8; // the tag is valid but the data is not there
        buffer.array()[1] += 1; // artificially increase message size
        assertThrows(BufferUnderflowException.class,() -> codec.parse(data));
    }

    @Test
    void parseMalformedEverything_underflow() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(13);
        final BufferedData data = prepareTestData(buffer);
        buffer.array()[1] -= 1; // artificially decrease message size
        assertThrows(BufferUnderflowException.class,() -> codec.parse(data));
    }

    private BufferedData prepareTestData(final ByteBuffer byteBuffer) throws IOException {
        final BufferedData data = BufferedData.wrap(byteBuffer);
        byte[] bytes = new byte[8];
        rng.nextBytes(bytes);
        final TimestampTest bytesTest = TimestampTest.newBuilder()
                .seconds(System.currentTimeMillis())
                .build();
        final Everything obj = Everything.newBuilder()
                .subObject(bytesTest)
                .build();
        codec.write(obj, data);
        data.flip();
        return data;
    }
}
