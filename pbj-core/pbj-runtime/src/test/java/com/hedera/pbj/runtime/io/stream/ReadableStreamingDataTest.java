package com.hedera.pbj.runtime.io.stream;

import com.hedera.pbj.runtime.io.DataAccessException;
import com.hedera.pbj.runtime.io.ReadableTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

final class ReadableStreamingDataTest extends ReadableTestBase {

    // TODO Verify capacity is Long.MAX_VALUE always.

    @NonNull
    @Override
    protected ReadableStreamingData emptySequence() {
        return new ReadableStreamingData(new ByteArrayInputStream(new byte[0]));
    }

    @NonNull
    @Override
    protected ReadableStreamingData fullyUsedSequence() {
        final var s = "This is a test string!";
        final var stream = sequence(s.getBytes(StandardCharsets.UTF_8));
        //noinspection ResultOfMethodCallIgnored
        stream.skip(s.getBytes(StandardCharsets.UTF_8).length);
        return stream;
    }

    @Override
    @NonNull
    protected ReadableStreamingData sequence(@NonNull byte [] arr) {
        return new ReadableStreamingData(new ByteArrayInputStream(arr));
    }

    @Test
    @DisplayName("Stream with no data")
    void noDataTest() {
        try (var stream = emptySequence()) {
            assertThat(stream.remaining()).isZero();
            assertThat(stream.hasRemaining()).isFalse();
        }
    }

    @Test
    @DisplayName("Stream with data")
    void someDataTest() {
        try (var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8))) {
            assertThat(stream.hasRemaining()).isTrue();
        }
    }

    @Test
    @DisplayName("Read some bytes")
    void readSomeBytesTest() {
        try (var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8))) {
            final var bytes = new byte[4];
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(4);
            assertThat(bytes).containsExactly('W', 'h', 'a', 't');
            assertThat(stream.hasRemaining()).isTrue();
        }
    }

    @Test
    @DisplayName("Read some bytes, skip some bytes, read some more bytes")
    void readSomeSkipSomeTest() {
        try (var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8))) {
            final var bytes = new byte[5];
            stream.readBytes(bytes, 0, 4);
            assertThat(stream.position()).isEqualTo(4);
            assertThat(bytes).containsExactly('W', 'h', 'a', 't', 0);
            assertThat(stream.skip(3)).isEqualTo(3);
            assertThat(stream.position()).isEqualTo(7);
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(12);
            assertThat(bytes).containsExactly('d', 'r', 'e', 'a', 'm');
            assertThat(stream.hasRemaining()).isTrue();
        }
    }

    @Test
    @DisplayName("Skip some bytes, read some bytes")
    void skipSomeReadSomeTest() {
        try (var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8))) {
            assertThat(stream.skip(7)).isEqualTo(7);
            final var bytes = new byte[5];
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(12);
            assertThat(bytes).containsExactly('d', 'r', 'e', 'a', 'm');
            assertThat(stream.hasRemaining()).isTrue();
        }
    }

    @Test
    @DisplayName("Read up to some limit on the stream which is less than its total length")
    void readToLimit() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            stream.limit(6);
            final var bytes = new byte[6];
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(6);
            assertThat(bytes).containsExactly('0', '1', '2', '3', '4', '5');
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        }
    }

    @Test
    @DisplayName("Read up to some limit and then extend the limit")
    void readToLimitAndExtend() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            final var bytes = new byte[6];
            stream.limit(6);
            stream.readBytes(bytes);
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();

            stream.limit(10);
            stream.readBytes(bytes, 0, 4);
            assertThat(stream.position()).isEqualTo(10);
            assertThat(bytes).containsExactly('6', '7', '8', '9', '4', '5');
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        }
    }

    @Test
    @DisplayName("Limit is not changed when we get to the end of the stream")
    void limitNotChanged() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            final var bytes = new byte[10];
            stream.readBytes(bytes);
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
            assertThat(stream.limit()).isEqualTo(Long.MAX_VALUE);
        }
    }

    @Test
    @DisplayName("Limit snaps to position if set to less than position")
    void limitNotBeforePosition() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            final var bytes = new byte[5];
            stream.readBytes(bytes);
            assertThat(stream.hasRemaining()).isTrue();
            assertThat(stream.limit()).isEqualTo(Long.MAX_VALUE);
            assertThat(stream.position()).isEqualTo(5);
            stream.limit(2);
            assertThat(stream.position()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Skipping more bytes than are available")
    void skipMoreThanAvailable() {
        try (var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8))) {
            assertThat(stream.skip(20)).isEqualTo(10);
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        }
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
    @DisplayName("InputStream fails immediately if bad in constructor")
    void inputStreamFailsImmediately() throws IOException {
        try (final var inputStream = mock(InputStream.class)) {
            given(inputStream.read()).willThrow(new IOException("Failed"));

            assertThatThrownBy(() -> new ReadableStreamingData(inputStream))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    @DisplayName("Bad InputStream will fail on skip")
    void inputStreamFailsDuringSkip() throws IOException {
        try (final var inputStream = mock(InputStream.class)) {
            given(inputStream.skip(4)).willThrow(new IOException("Failed"));

            final var stream = new ReadableStreamingData(inputStream);
            assertThatThrownBy(() -> stream.skip(5))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    @DisplayName("Bad InputStream will fail on read")
    void inputStreamFailsDuringRead() throws IOException {
        try (final var inputStream = mock(InputStream.class)) {
            given(inputStream.skip(4)).willReturn(4L);
            given(inputStream.read())
                    .willReturn(0) // Returned when first byte is preloaded in constructor
                    .willReturn(5) // Returned when next + 1 byte is preloaded in skip
                    .willThrow(new IOException("Failed")); // Thrown when attempt to read sequence after skip

            final var stream = new ReadableStreamingData(inputStream);
            assertThat(stream.skip(5)).isEqualTo(5);
            assertThatThrownBy(stream::readByte)
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    @DisplayName("Bad InputStream during close is ignored")
    void inputStreamFailsDuringClose() throws IOException {
        final var inputStream = mock(InputStream.class);
        given(inputStream.read()).willReturn(0); // Returned when first byte is preloaded in constructor
        Mockito.doThrow(new IOException("Failed")).when(inputStream).close();

        final var stream = new ReadableStreamingData(inputStream);
        stream.close();
        assertThat(stream.hasRemaining()).isFalse();
    }
}
