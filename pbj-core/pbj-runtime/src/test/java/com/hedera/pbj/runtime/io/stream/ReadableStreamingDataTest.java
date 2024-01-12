package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.ReadableSequentialTestBase;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ReadableStreamingDataTest extends ReadableSequentialTestBase {

    @NonNull
    @Override
    protected ReadableStreamingData emptySequence() {
        final var stream = new ReadableStreamingData(new byte[0]);
        stream.limit(0);
        return stream;
    }

    @NonNull
    private ReadableSequentialData throwingSequence() {
        return new ReadableStreamingData(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("testing here");
            }
        });
    }

    @NonNull
    private ReadableSequentialData oneByteSequence() {
        return new ReadableStreamingData(new InputStream() {
            private int pos = 0;
            @Override
            public int read() throws IOException {
                switch (pos) {
                    case 0: pos++; return 7;
                    case 1: pos++; return -1;
                    default: throw new IOException("EOF");
                }
            }

            @Override
            public int readNBytes(byte[] b, int off, int len) throws IOException {
                switch (pos) {
                    case 0: b[off] = (byte) read(); return 1;
                    default: return super.readNBytes(b, off, len);
                }
            }
        });
    }

    @Test
    @DisplayName("Stream with readByte() throwing an exception")
    void throwingSequenceTest() {
        final var stream = throwingSequence();
        assertThat(stream.hasRemaining()).isTrue();
        final var bytes = new byte[4];
        assertThrows(DataAccessException.class, () -> stream.readBytes(bytes), "testing here");
    }

    @Test
    @DisplayName("Stream with just one byte, to test EOF")
    void oneByteSequenceTest() {
        final var stream = oneByteSequence();
        assertThat(stream.hasRemaining()).isTrue();
        final var bb = ByteBuffer.allocate(4);
        assertThat(stream.readBytes(bb)).isEqualTo(1);
        assertThat(bb.array()[0]).isEqualTo((byte) 7);
        assertThat(stream.readBytes(bb)).isEqualTo(0);
    }

    @Test
    @DisplayName("Read into a ByteBuffer that has no a backing array")
    void noArrayByteBufferTest() {
        byte[] inputArr = {1, 2, 3};
        ReadableStreamingData sequence = sequence(inputArr);
        ByteBuffer bb = ByteBuffer.allocateDirect(3);
        if (bb.hasArray()) {
            // There's not an easy way to have a ByteBuffer w/o an array
            // on this particular platform, so we skip this test.
            // Note that on Mac, the direct buffer doesn't have an array,
            // so at the very least this test runs on Mac OS (as of 2023-12.)
            return;
        }
        final long bytesRead = sequence.readBytes(bb);
        assertThat(bytesRead).isEqualTo(3);
        byte[] outputArr = new byte[3];
        bb.rewind();
        bb.get(outputArr);
        assertArrayEquals(inputArr, outputArr);
    }

    @NonNull
    @Override
    protected ReadableStreamingData fullyUsedSequence() {
        final var s = "This is a test string!";
        final var stream = sequence(s.getBytes(StandardCharsets.UTF_8));
        stream.limit(s.length());
        stream.skip(s.getBytes(StandardCharsets.UTF_8).length);
        return stream;
    }

    @Override
    @NonNull
    protected ReadableStreamingData sequence(@NonNull byte [] arr) {
        final var stream = new ReadableStreamingData(arr);
        stream.limit(arr.length);
        return stream;
    }

    @Test
    @DisplayName("Closed stream has no bytes remaining")
    void closedStreamHasNoBytesRemaining() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            assertThat(stream.hasRemaining()).isTrue();
            stream.close();
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        }
    }

    @Test
    @DisplayName("Closed stream cannot be read")
    void closedStreamCannotBeRead() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            stream.close();
            assertThatThrownBy(stream::readByte)
                    .isInstanceOf(BufferUnderflowException.class);
        }
    }

    @Test
    @DisplayName("Streams can be closed twice")
    void closeTwice() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            stream.close();
            stream.close();
            assertThatThrownBy(stream::readByte)
                    .isInstanceOf(BufferUnderflowException.class);
        }
    }

    @Test
    @DisplayName("Bad InputStream will fail on skip")
    void inputStreamFailsDuringSkip() {
        final var byteStream = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5, 6, 7 });
        final var inputStream = new BufferedInputStream(byteStream) {
            @Override
            public synchronized long skip(long n) throws IOException {
                throw new IOException("Failed");
            }
        };

        final var stream = new ReadableStreamingData(inputStream);
        assertThatThrownBy(() -> stream.skip(5))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Bad InputStream will fail on read")
    void inputStreamFailsDuringRead() {
        final var throwNow = new AtomicBoolean(false);
        final var byteStream = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5, 6, 7 });
        final var inputStream = new BufferedInputStream(byteStream) {
            @Override
            public int read() throws IOException {
                if (throwNow.get()) {
                    throw new IOException("Failed");
                } else {
                    return super.read();
                }
            }
        };

        final var stream = new ReadableStreamingData(inputStream);
        assertThat(stream.skip(5)).isEqualTo(5);

        throwNow.set(true);
        assertThatThrownBy(stream::readByte)
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("Bad InputStream during close is ignored")
    void inputStreamFailsDuringClose() {
        final var inputStream = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                throw new IOException("Failed");
            }
        };

        final var stream = new ReadableStreamingData(inputStream);
        stream.close();
        assertThat(stream.hasRemaining()).isFalse();
    }

    @Test
    @DisplayName("Bad InputStream empty when read")
    void inputStreamEmptyReadBytes() {
        final var inputStream = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                throw new IOException("Failed");
            }
        };

        byte[] read = new byte[5];
        final var stream = new ReadableStreamingData(inputStream);
        assertThrows(EOFException.class, () -> {
            final var i = stream.readInt();
        });
        assertEquals(0, stream.readBytes(read));
    }

    @Test
    @DisplayName("Bad InputStream empty when read")
    void inputStreamEmptyReadVarLong() {
        final var inputStream = new ByteArrayInputStream(new byte[] {
                (byte) 128, (byte) 129, (byte) 130, (byte) 131});

        final var stream = new ReadableStreamingData(inputStream);

        assertThrows(EOFException.class, () -> stream.readVarLong(false));
    }

    @Test
    void incompleteStreamToByteBuffer() {
        final var inputStream = new ByteArrayInputStream(new byte[] {
                (byte) 128, (byte) 129, (byte) 130, (byte) 131});

        final var stream = new TestReadeableSequentialData(new ReadableStreamingData(inputStream));
        ByteBuffer buffer = ByteBuffer.allocate(8);

        assertEquals(4, stream.readBytes(buffer), "Unexpected number of bytes read");
    }

    @Test
    void incompleteStreamToBufferedData() {
        final var inputStream = new ByteArrayInputStream(new byte[] {
                (byte) 128, (byte) 129, (byte) 130, (byte) 131});

        final var stream = new TestReadeableSequentialData(new ReadableStreamingData(inputStream));
        stream.limit(8);
        BufferedData buffer = BufferedData.allocate(8);

        assertEquals(4, stream.readBytes(buffer), "Unexpected number of bytes read");
    }

    @Test
    @DisplayName("Reusing an input stream on two ReadableStreamingData does not lose any data")
    void reuseStream() {
        final var byteStream = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });

        final var bytes1 = new byte[5];
        final var stream1 = new ReadableStreamingData(byteStream);
        stream1.readBytes(bytes1);

        final var bytes2 = new byte[5];
        final var stream2 = new ReadableStreamingData(byteStream);
        stream2.readBytes(bytes2);

        assertThat(bytes1).containsExactly(1, 2, 3, 4, 5);
        assertThat(bytes2).containsExactly(6, 7, 8, 9, 10);
    }

    @Test
    @DisplayName("Limit is not changed when we get to the end of the stream")
    void limitNotChanged() {
        final var byteStream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        // The semantics are now that the stream doesn't know if it has reached the end until you
        // try to read past the end, at which point you get a BufferUnderflowException.
        try (final var stream = new ReadableStreamingData(byteStream)) {
            final var bytes = new byte[3];
            stream.readBytes(bytes);
            assertThat(stream.hasRemaining()).isTrue();
            assertThat(stream.limit()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    void zeroLimitReadEmptyFile() throws IOException {
        final File file = File.createTempFile(getClass().getSimpleName(), "zeroLimitReadEmptyFile");
        file.deleteOnExit();
        try (final var stream = new ReadableStreamingData(file.toPath())) {
            assertThat(stream.limit()).isEqualTo(0);
        }
    }

    @Test
    void nonZeroLimitReadFile() throws IOException {
        final File file = File.createTempFile(getClass().getSimpleName(), "nonZeroLimitReadFile");
        file.deleteOnExit();
        final byte[] bytes = new byte[] {0, 1, 2, 3, 4};
        Files.write(file.toPath(), bytes, StandardOpenOption.WRITE);
        try (final var stream = new ReadableStreamingData(file.toPath())) {
            assertThat(stream.limit()).isEqualTo(bytes.length);
        }
    }

    @Test
    void dataOnTopOfByteArrayLimits() {
        final byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
        try (final var stream = new ReadableStreamingData(bytes)) {
            assertThat(stream.limit()).isEqualTo(bytes.length);
        }
        try (final var stream = new ReadableStreamingData(new ByteArrayInputStream(bytes))) {
            assertThat(stream.limit()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    void readDirectoryFile() throws IOException {
        final Path dir = Files.createTempDirectory(getClass().getSimpleName() + "_readDirectoryFile");
        try {
            assertThrows(IOException.class, () -> new ReadableStreamingData(dir));
        } finally {
            Files.delete(dir);
        }
    }

    @Test
    void readFileThatDoesntExist() throws IOException {
        final Path file = Files.createTempFile(getClass().getSimpleName(), "readFileThatDoesntExist");
        Files.delete(file);
        assertThrows(IOException.class, () -> new ReadableStreamingData(file));
    }

    /**
     * The sole purpose of this class is to allow testing of
     * `{@link ReadableStreamingData#readBytes(ByteBuffer)}` and `{@link ReadableStreamingData#readBytes(BufferedData)}`.
     * This methods are overriddin in other implementation and not possible to test ortherwise.
     */
    private static class TestReadeableSequentialData implements ReadableSequentialData {
        private ReadableStreamingData readableStreamingData;

        public TestReadeableSequentialData(final ReadableStreamingData readableStreamingData) {
            this.readableStreamingData = readableStreamingData;
        }

        @Override
        public byte readByte() {
            return readableStreamingData.readByte();
        }

        @Override
        public long capacity() {
            return readableStreamingData.capacity();
        }

        @Override
        public long position() {
            return readableStreamingData.position();
        }

        @Override
        public long limit() {
            return readableStreamingData.limit();
        }

        @Override
        public void limit(long limit) {
            readableStreamingData.limit(limit);
        }

        @Override
        public long skip(long count) {
            return readableStreamingData.skip(count);
        }
    }
}
