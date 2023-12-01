package com.hedera.pbj.runtime.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public abstract class ReadableSequentialTestBase extends ReadableTestBase {

    @NonNull
    @Override
    protected abstract ReadableSequentialData emptySequence();

    @NonNull
    @Override
    protected abstract ReadableSequentialData fullyUsedSequence();

    @Override
    @NonNull
    protected abstract ReadableSequentialData sequence(@NonNull byte [] arr);

    @Test
    @DisplayName("Stream with no data")
    void noDataTest() throws Exception {
        final var stream = emptySequence();
        try {
            assertThat(stream.remaining()).isZero();
            assertThat(stream.hasRemaining()).isFalse();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Stream with data")
    void someDataTest() throws Exception {
        final var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8));
        try {
            assertThat(stream.hasRemaining()).isTrue();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Read some bytes")
    void readSomeBytesTest() throws Exception {
        final var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8));
        try {
            final var bytes = new byte[4];
            final long bytesRead = stream.readBytes(bytes);
            assertThat(bytesRead).isEqualTo(bytes.length);
            assertThat(stream.position()).isEqualTo(4);
            assertThat(bytes).containsExactly('W', 'h', 'a', 't');
            assertThat(stream.hasRemaining()).isTrue();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Read some bytes, skip some bytes, read some more bytes")
    void readSomeSkipSomeTest() throws Exception {
        final var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8));
        try {
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
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Skip some bytes, read some bytes")
    void skipSomeReadSomeTest() throws Exception {
        final var stream = sequence("What a dream!!".getBytes(StandardCharsets.UTF_8));
        try {
            assertThat(stream.skip(7)).isEqualTo(7);
            final var bytes = new byte[5];
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(12);
            assertThat(bytes).containsExactly('d', 'r', 'e', 'a', 'm');
            assertThat(stream.hasRemaining()).isTrue();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Read up to some limit on the stream which is less than its total length")
    void readToLimit() throws Exception {
        final var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8));
        try {
            stream.limit(6);
            final var bytes = new byte[6];
            stream.readBytes(bytes);
            assertThat(stream.position()).isEqualTo(6);
            assertThat(bytes).containsExactly('0', '1', '2', '3', '4', '5');
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Read past the data - EOF")
    void readPastEnd() throws Exception {
        final var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8));
        try {
            stream.limit(12);
            final var bytes = new byte[12];
            final var read = stream.readBytes(bytes);
            assertEquals(10, read);
            assertThat(stream.position()).isEqualTo(10);
            assertThat(bytes).containsExactly('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 0, 0);
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Read up to some limit and then extend the limit")
    void readToLimitAndExtend() throws Exception {
        final var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8));
        try {
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
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Limit snaps to position if set to less than position")
    void limitNotBeforePosition() throws Exception {
        final var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8));
        try {
            final var bytes = new byte[5];
            stream.readBytes(bytes);
            assertThat(stream.hasRemaining()).isTrue();
            assertThat(stream.limit()).isEqualTo(10);
            assertThat(stream.position()).isEqualTo(5);
            stream.limit(2);
            assertThat(stream.position()).isEqualTo(5);
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    @Test
    @DisplayName("Skipping more bytes than are available")
    void skipMoreThanAvailable() throws Exception {
        final var stream = sequence("0123456789".getBytes(StandardCharsets.UTF_8));
        try {
            assertThat(stream.skip(20)).isEqualTo(10);
            assertThat(stream.hasRemaining()).isFalse();
            assertThat(stream.remaining()).isZero();
        } finally {
            if (stream instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

}
