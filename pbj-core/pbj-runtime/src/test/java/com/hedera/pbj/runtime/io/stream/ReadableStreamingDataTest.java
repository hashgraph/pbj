package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableSequentialTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ReadableStreamingDataTest extends ReadableSequentialTestBase {

    @NonNull
    @Override
    protected ReadableStreamingData emptySequence() {
        final var stream = new ReadableStreamingData(new ByteArrayInputStream(new byte[0]));
        stream.limit(0);
        return stream;
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
        final var stream = new ReadableStreamingData(new ByteArrayInputStream(arr));
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
    void inputStreamFailsDuringRead() throws IOException {
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
    void inputStreamEmptyRead() {
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

}
